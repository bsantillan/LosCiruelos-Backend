package com.LosCiruelos.padel_club_api.club.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.mock.web.MockMultipartFile;

import com.LosCiruelos.padel_club_api.TestData;
import com.LosCiruelos.padel_club_api.club.dto.request.NovedadRequest;
import com.LosCiruelos.padel_club_api.club.dto.response.NovedadPublicaResponse;
import com.LosCiruelos.padel_club_api.club.dto.response.NovedadResponse;
import com.LosCiruelos.padel_club_api.club.entity.Novedad;
import com.LosCiruelos.padel_club_api.club.entity.enums.EstadoNovedad;
import com.LosCiruelos.padel_club_api.club.service.NovedadService;
import com.LosCiruelos.padel_club_api.common.AlmacenDeImagenesTest;
import com.LosCiruelos.padel_club_api.common.archivos.AlmacenDeImagenes;
import com.LosCiruelos.padel_club_api.common.exception.ReservaException;
import com.LosCiruelos.padel_club_api.mapper.NovedadMapper;
import com.LosCiruelos.padel_club_api.usuarios.entity.Usuario;
import com.LosCiruelos.padel_club_api.usuarios.entity.enums.Role;
import com.LosCiruelos.padel_club_api.usuarios.service.UsuarioService;

/** Las novedades contra la base (H2). Hoy es el martes 13/10/2026. */
@DataJpaTest(properties = { "spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect" })
class NovedadRepositoryTest {

    private static final LocalDate HOY = TestData.HOY;

    @TempDir
    Path carpeta;

    @Autowired
    private TestEntityManager em;

    @Autowired
    private NovedadRepository repo;

    private Usuario admin;
    private NovedadService servicio;
    private AlmacenDeImagenes almacen;

    @BeforeEach
    void setUp() {
        admin = TestData.usuario(null, Role.ADMIN);
        admin.setEmail("admin@test.com");
        admin = em.persist(admin);
        UsuarioService usuarios = mock(UsuarioService.class);
        when(usuarios.autenticado("admin@test.com")).thenReturn(admin);
        almacen = new AlmacenDeImagenes(carpeta.toString());
        servicio = new NovedadService(repo, new NovedadMapper(), usuarios, almacen, TestData.reloj(TestData.AHORA));
    }

    private Novedad novedad(String titulo, boolean publicada, LocalDateTime publicadaAt, LocalDate visibleHasta) {
        return em.persist(Novedad.builder().titulo(titulo).descripcion("…").publicada(publicada).publicadaAt(publicadaAt)
                .visibleHasta(visibleHasta).creadoPor(admin).createdAt(TestData.AHORA.minusDays(10))
                .updatedAt(TestData.AHORA.minusDays(10)).build());
    }

    private static NovedadRequest pedido(String titulo) {
        NovedadRequest req = new NovedadRequest();
        req.setTitulo("  " + titulo + " ");
        req.setDescripcion("Inscripción abierta hasta el viernes.");
        req.setEtiqueta("Torneo");
        return req;
    }

    @Test
    void laListaPublicaTraeLasPublicadasYVigentesDeLaMasNuevaALaMasVieja() {
        novedad("Vieja", true, TestData.AHORA.minusDays(5), null);
        novedad("Nueva", true, TestData.AHORA.minusDays(1), HOY); // vence hoy: todavía se ve
        novedad("Vencida", true, TestData.AHORA.minusDays(2), HOY.minusDays(1));
        novedad("Borrador", false, null, null);
        em.flush();

        assertThat(servicio.vigentes(null)).extracting(NovedadPublicaResponse::titulo).containsExactly("Nueva", "Vieja");
        assertThat(servicio.vigentes(1)).extracting(NovedadPublicaResponse::titulo).containsExactly("Nueva");
        assertThatThrownBy(() -> servicio.vigentes(0)).isInstanceOf(ReservaException.class);

        assertThat(servicio.listar(0, null).contenido()).extracting(NovedadResponse::estado)
                .containsExactlyInAnyOrder(EstadoNovedad.PUBLICADA, EstadoNovedad.PUBLICADA, EstadoNovedad.VENCIDA,
                        EstadoNovedad.BORRADOR);
    }

    @Test
    void naceComoBorradorSePublicaYSeDespublica() {
        NovedadResponse creada = servicio.crear(pedido("Torneo de primavera"), "admin@test.com");
        assertThat(creada.titulo()).isEqualTo("Torneo de primavera");
        assertThat(creada.estado()).isEqualTo(EstadoNovedad.BORRADOR);
        assertThat(creada.publicadaAt()).isNull();
        assertThat(creada.creadoPor()).isEqualTo((admin.getNombre() + " " + admin.getApellido()).trim());

        NovedadResponse publicada = servicio.publicar(creada.id(), true);
        assertThat(publicada.estado()).isEqualTo(EstadoNovedad.PUBLICADA);
        assertThat(publicada.publicadaAt()).isEqualTo(TestData.AHORA);
        assertThat(servicio.vigentes(null)).hasSize(1);

        assertThat(servicio.publicar(creada.id(), false).estado()).isEqualTo(EstadoNovedad.BORRADOR);
        assertThat(servicio.vigentes(null)).isEmpty();
    }

    @Test
    void laImagenSeReemplazaYSeBorraConLaNovedad() {
        NovedadResponse creada = servicio.crear(pedido("Clases"), "admin@test.com");

        String primera = servicio.ponerImagen(creada.id(),
                new MockMultipartFile("archivo", "a.png", "image/png", AlmacenDeImagenesTest.PNG)).imagenUrl();
        String segunda = servicio.ponerImagen(creada.id(),
                new MockMultipartFile("archivo", "b.jpg", "image/jpeg", AlmacenDeImagenesTest.JPG)).imagenUrl();
        assertThat(segunda).endsWith(".jpg").isNotEqualTo(primera);
        // sin transacción que confirmar (el test es transaccional): la anterior se borra al confirmar
        assertThat(almacen.buscar(segunda.substring("/archivos/".length()))).isPresent();

        assertThat(servicio.sacarImagen(creada.id()).imagenUrl()).isNull();
        servicio.eliminar(creada.id());
        assertThat(repo.findById(creada.id())).isEmpty();
    }
}
