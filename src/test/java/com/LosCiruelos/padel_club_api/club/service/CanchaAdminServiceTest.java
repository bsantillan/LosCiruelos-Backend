package com.LosCiruelos.padel_club_api.club.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import com.LosCiruelos.padel_club_api.club.dto.request.CanchaRequest;
import com.LosCiruelos.padel_club_api.club.dto.response.BajaDeCanchaResponse;
import com.LosCiruelos.padel_club_api.club.dto.response.CancelacionesResponse;
import com.LosCiruelos.padel_club_api.club.dto.response.CanchaResponse;
import com.LosCiruelos.padel_club_api.club.dto.response.FueraDeHorarioResponse;
import com.LosCiruelos.padel_club_api.club.entity.Cancha;
import com.LosCiruelos.padel_club_api.club.entity.CanchaFoto;
import com.LosCiruelos.padel_club_api.club.entity.enums.TipoCancha;
import com.LosCiruelos.padel_club_api.club.exception.HorariosConReservasException;
import com.LosCiruelos.padel_club_api.club.repository.CanchaRepository;
import com.LosCiruelos.padel_club_api.common.AlmacenDeImagenesTest;
import com.LosCiruelos.padel_club_api.common.archivos.AlmacenDeImagenes;
import com.LosCiruelos.padel_club_api.common.exception.EstadoInvalidoException;
import com.LosCiruelos.padel_club_api.common.exception.ReservaException;
import com.LosCiruelos.padel_club_api.mapper.CanchaMapper;

/** La gestión de canchas del ADMIN. */
class CanchaAdminServiceTest {

    @TempDir
    Path carpeta;

    private CanchaRepository repo;
    private HorariosOcupados ocupados;
    private AlmacenDeImagenes almacen;
    private CanchaAdminService servicio;
    private Cancha cancha;

    @BeforeEach
    void setUp() {
        repo = mock(CanchaRepository.class);
        ocupados = mock(HorariosOcupados.class);
        almacen = new AlmacenDeImagenes(carpeta.toString());
        servicio = new CanchaAdminService(repo, new CanchaMapper(), ocupados, almacen);
        cancha = Cancha.builder().id(5L).numero(5).tipo(TipoCancha.TECHADA).build();
        when(repo.findById(5L)).thenReturn(Optional.of(cancha));
        when(repo.findByIdForUpdate(5L)).thenReturn(Optional.of(cancha));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(repo.saveAndFlush(any())).thenAnswer(inv -> {
            Cancha c = inv.getArgument(0);
            long id = 1;
            for (CanchaFoto f : c.getFotos()) {
                if (f.getId() == null) {
                    f.setId(100 + id);
                }
                id++;
            }
            return c;
        });
    }

    private static CanchaRequest pedido(int numero) {
        CanchaRequest req = new CanchaRequest();
        req.setNumero(numero);
        req.setNombre("  Cancha central ");
        req.setTipo(TipoCancha.AIRE_LIBRE);
        req.setSuperficie("Césped sintético");
        req.setIluminacion(true);
        return req;
    }

    @Test
    void creaUnaCanchaActivaYNoRepiteElNumero() {
        CanchaResponse r = servicio.crear(pedido(6));
        assertThat(r.getNombre()).isEqualTo("Cancha central");
        assertThat(r.getActiva()).isTrue();
        assertThat(r.getEliminable()).isTrue();

        when(repo.existsByNumero(1)).thenReturn(true);
        assertThatThrownBy(() -> servicio.crear(pedido(1)))
                .isInstanceOf(EstadoInvalidoException.class)
                .hasMessage("Ya existe la cancha 1.");
    }

    @Test
    void unaCanchaUsadaNoSeEliminaYUnaNuevaSiConSusFotos() throws Exception {
        when(ocupados.canchaUsada(5L)).thenReturn(true);
        assertThatThrownBy(() -> servicio.eliminar(5L))
                .isInstanceOf(EstadoInvalidoException.class)
                .hasMessage("La cancha tiene reservas o turnos fijos: desactivala en lugar de eliminarla.");
        verify(repo, never()).delete(any());

        when(ocupados.canchaUsada(5L)).thenReturn(false);
        servicio.agregarFoto(5L, new MockMultipartFile("archivo", "a.png", "image/png", AlmacenDeImagenesTest.PNG));
        String archivo = cancha.getFotos().getFirst().getArchivo();
        servicio.eliminar(5L);
        verify(repo).delete(cancha);
        assertThat(almacen.buscar(archivo)).isEmpty(); // la foto se borró del disco
    }

    @Test
    void lasFotosSonHastaSeisYSeOrdenan() {
        for (int i = 0; i < 6; i++) {
            servicio.agregarFoto(5L, new MockMultipartFile("archivo", "a.jpg", "image/jpeg", AlmacenDeImagenesTest.JPG));
        }
        assertThat(cancha.getFotos()).extracting(CanchaFoto::getOrden).containsExactly(1, 2, 3, 4, 5, 6);
        assertThatThrownBy(() -> servicio.agregarFoto(5L,
                new MockMultipartFile("archivo", "a.jpg", "image/jpeg", AlmacenDeImagenesTest.JPG)))
                .isInstanceOf(EstadoInvalidoException.class)
                .hasMessage("La cancha ya tiene 6 fotos: sacá una antes de subir otra.");

        List<Long> ids = cancha.getFotos().stream().map(CanchaFoto::getId).toList();
        CanchaResponse r = servicio.ordenarFotos(5L, ids.reversed());
        assertThat(r.getFotos()).extracting(CanchaResponse.Foto::id).containsExactlyElementsOf(ids.reversed());
        assertThat(r.getFotos()).extracting(CanchaResponse.Foto::orden).containsExactly(1, 2, 3, 4, 5, 6);
        assertThatThrownBy(() -> servicio.ordenarFotos(5L, ids.subList(0, 2)))
                .isInstanceOf(ReservaException.class);

        r = servicio.sacarFoto(5L, ids.getLast());
        assertThat(r.getFotos()).hasSize(5).extracting(CanchaResponse.Foto::orden).containsExactly(1, 2, 3, 4, 5);
    }

    @Test
    void laBajaConReservasPideConfirmarYDespuesLasCancelaYDaDeBajaLosTurnosFijos() {
        BajaDeCanchas baja = new BajaDeCanchas(servicio, ocupados);
        FueraDeHorarioResponse ocupado = new FueraDeHorarioResponse(
                List.of(new FueraDeHorarioResponse.Reserva(30L, java.time.LocalDate.of(2026, 10, 20),
                        java.time.LocalTime.of(18, 0), java.time.LocalTime.of(19, 30), 5, "Ana Gómez", "NORMAL")),
                List.of(new FueraDeHorarioResponse.TurnoFijo(8L, java.time.DayOfWeek.MONDAY, java.time.LocalTime.of(20, 0),
                        java.time.LocalTime.of(21, 30), 5)));
        when(ocupados.deLaCancha(5L)).thenReturn(ocupado);

        assertThatThrownBy(() -> baja.darDeBaja(5L, false, "admin@test.com"))
                .isInstanceOf(HorariosConReservasException.class)
                .hasMessageContaining("La cancha tiene 1 reserva y 1 turno fijo de hoy en adelante")
                .satisfies(e -> assertThat(((HorariosConReservasException) e).getFuera()).isEqualTo(ocupado));
        assertThat(cancha.getActiva()).isTrue();

        when(ocupados.liberarCancha(5L, "admin@test.com")).thenReturn(new HorariosOcupados.LiberacionDeCancha(
                new CancelacionesResponse(List.of(30L), List.of()), List.of(8L)));
        BajaDeCanchaResponse r = baja.darDeBaja(5L, true, "admin@test.com");
        assertThat(cancha.getActiva()).isFalse();
        assertThat(r.cancelaciones().canceladas()).containsExactly(30L);
        assertThat(r.turnosFijosDadosDeBaja()).containsExactly(8L);

        servicio.reactivar(5L);
        assertThat(cancha.getActiva()).isTrue();
    }

    @Test
    void sinNadaQueCancelarSeDaDeBajaDirecto() {
        when(ocupados.deLaCancha(5L)).thenReturn(new FueraDeHorarioResponse(List.of(), List.of()));

        BajaDeCanchaResponse r = new BajaDeCanchas(servicio, ocupados).darDeBaja(5L, false, "admin@test.com");

        assertThat(r.cancha().getActiva()).isFalse();
        assertThat(r.cancelaciones()).isEqualTo(CancelacionesResponse.NINGUNA);
        verify(ocupados, never()).liberarCancha(any(), any());
    }
}
