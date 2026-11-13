package com.LosCiruelos.padel_club_api.reservas.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import com.LosCiruelos.padel_club_api.TestData;
import com.LosCiruelos.padel_club_api.club.dto.response.CanchaResponse;
import com.LosCiruelos.padel_club_api.club.entity.Cancha;
import com.LosCiruelos.padel_club_api.club.service.CanchaService;
import com.LosCiruelos.padel_club_api.club.service.ConfiguracionService;
import com.LosCiruelos.padel_club_api.club.service.ProductoService;
import com.LosCiruelos.padel_club_api.common.exception.PermisoDenegadoException;
import com.LosCiruelos.padel_club_api.mapper.VentaMapper;
import com.LosCiruelos.padel_club_api.reservas.dto.response.MetricasResponse;
import com.LosCiruelos.padel_club_api.reservas.entity.Reserva;
import com.LosCiruelos.padel_club_api.reservas.entity.enums.EstadoReserva;
import com.LosCiruelos.padel_club_api.reservas.service.MetricasService;
import com.LosCiruelos.padel_club_api.usuarios.entity.Usuario;
import com.LosCiruelos.padel_club_api.usuarios.entity.enums.Role;
import com.LosCiruelos.padel_club_api.usuarios.service.UsuarioService;
import com.LosCiruelos.padel_club_api.ventas.repository.VentaRepository;
import com.LosCiruelos.padel_club_api.ventas.service.VentaConsultaService;

/** Las métricas contra la base (H2): las consultas y el período anterior. Hoy es el martes 13/10/2026. */
@DataJpaTest(properties = { "spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect" })
class MetricasRepositoryTest {

    private static final LocalDate HOY = TestData.HOY;

    @Autowired
    private TestEntityManager em;

    @Autowired
    private ReservaRepository reservas;

    @Autowired
    private VentaRepository ventas;

    private Usuario admin;
    private Usuario ana;
    private Cancha cancha;
    private MetricasService metricas;

    @BeforeEach
    void datos() {
        admin = TestData.usuario(null, Role.ADMIN);
        admin.setEmail("admin@test.com");
        admin = em.persist(admin);
        ana = em.persist(TestData.usuario(null, Role.CLIENTE));
        cancha = em.persist(Cancha.builder().numero(1).descripcion("Cancha 1").build());

        // período: lunes 5/10 a domingo 11/10; el anterior, del 28/9 al 4/10
        reserva(HOY.minusDays(8), EstadoReserva.COMPLETADA);                 // lunes 5/10
        reserva(HOY.minusDays(7), EstadoReserva.COMPLETADA);                 // martes 6/10
        Reserva cancelada = reserva(HOY.minusDays(6), EstadoReserva.CANCELADA);
        cancelada.setCanceladaAt(HOY.minusDays(7).atTime(10, 0));
        cancelada.setCanceladaPor(ana);
        reserva(HOY.minusDays(14), EstadoReserva.COMPLETADA);                // del período anterior
        reserva(HOY.plusDays(2), EstadoReserva.SEÑADA);                      // lo que viene
        em.flush();

        UsuarioService usuarioService = mock(UsuarioService.class);
        when(usuarioService.autenticado(admin.getEmail())).thenReturn(admin);
        when(usuarioService.autenticado("empleado@test.com")).thenReturn(TestData.usuario(9L, Role.EMPLEADO));
        CanchaService canchas = mock(CanchaService.class);
        CanchaResponse c = new CanchaResponse();
        c.setId(cancha.getId());
        c.setNumero(1);
        when(canchas.listarCanchas()).thenReturn(List.of(c));
        ConfiguracionService config = mock(ConfiguracionService.class);
        when(config.obtener()).thenReturn(TestData.configuracion());
        var reloj = TestData.reloj(TestData.AHORA);
        metricas = new MetricasService(reservas,
                new VentaConsultaService(ventas, usuarioService, new VentaMapper(), reloj, null, TestData.jornadas(reloj)),
                mock(ProductoService.class), canchas, config, TestData.jornadas(reloj), usuarioService, reloj);
    }

    private Reserva reserva(LocalDate fecha, EstadoReserva estado) {
        return em.persist(Reserva.builder().cancha(cancha).cliente(ana).createdBy(ana).fechaReserva(fecha)
                .horaInicio(LocalTime.of(18, 0)).horaFin(LocalTime.of(19, 30)).montoTotal(new BigDecimal("15000"))
                .estado(estado).createdAt(fecha.minusDays(1).atTime(12, 0)).build());
    }

    @Test
    void comparaElPeriodoConElAnteriorDeIgualDuracion() {
        MetricasResponse m = metricas.metricas(HOY.minusDays(8), HOY.minusDays(2), admin.getEmail());

        assertThat(m.anterior()).isEqualTo(new MetricasResponse.Periodo(HOY.minusDays(15), HOY.minusDays(9)));
        assertThat(m.ingresos().cancha().actual()).isEqualByComparingTo("30000");
        assertThat(m.ingresos().cancha().anterior()).isEqualByComparingTo("15000");
        assertThat(m.ingresos().cancha().variacion()).isEqualByComparingTo("100.0");
        // 3 h ocupadas de 5 días hábiles × 15 h + sábado 13 h + domingo 11 h, con 1 cancha
        assertThat(m.ocupacion().horasAbiertas().actual()).isEqualByComparingTo("99.0");
        assertThat(m.cancelaciones().canceladas().actual()).isEqualByComparingTo("1");
        assertThat(m.cancelaciones().porCliente()).isEqualTo(1);
        assertThat(m.clientes().activos().actual()).isEqualByComparingTo("1");
        assertThat(m.clientes().recurrentes().actual()).isEqualByComparingTo("1"); // su primera es del anterior
        assertThat(m.futuro().turnos14Dias()).isEqualTo(1);
    }

    @Test
    void sinFechasSonLosUltimos30DiasYSoloElAdmin() {
        MetricasResponse m = metricas.metricas(null, null, admin.getEmail());
        assertThat(m.desde()).isEqualTo(HOY.minusDays(29));
        assertThat(m.hasta()).isEqualTo(HOY);

        assertThatThrownBy(() -> metricas.metricas(null, null, "empleado@test.com"))
                .isInstanceOf(PermisoDenegadoException.class);
    }
}
