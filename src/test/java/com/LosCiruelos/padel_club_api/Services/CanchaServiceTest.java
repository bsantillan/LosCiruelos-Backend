package com.LosCiruelos.padel_club_api.Services;

import static com.LosCiruelos.padel_club_api.TestData.MANIANA;
import static com.LosCiruelos.padel_club_api.TestData.reserva;
import static com.LosCiruelos.padel_club_api.TestData.usuario;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.LosCiruelos.padel_club_api.TestData;
import com.LosCiruelos.padel_club_api.DTOs.Responses.DisponibilidadResponse;
import com.LosCiruelos.padel_club_api.DTOs.Responses.DisponibilidadResponse.Turno;
import com.LosCiruelos.padel_club_api.Entities.Reserva;
import com.LosCiruelos.padel_club_api.Entities.Enum.EstadoReserva;
import com.LosCiruelos.padel_club_api.Entities.Enum.Role;
import com.LosCiruelos.padel_club_api.Exceptions.CanchaNotFoundException;
import com.LosCiruelos.padel_club_api.Exceptions.ReservaException;
import com.LosCiruelos.padel_club_api.Repository.CanchaRepository;
import com.LosCiruelos.padel_club_api.Repository.ConfiguracionRepository;

class CanchaServiceTest {

    private final ReservaService reservaService = mock(ReservaService.class);
    private final CanchaRepository canchaRepository = mock(CanchaRepository.class);

    private CanchaService servicio(LocalDateTime ahora) {
        ConfiguracionRepository configuracionRepository = mock(ConfiguracionRepository.class);
        when(configuracionRepository.obtener()).thenReturn(TestData.configuracion());
        when(canchaRepository.findById(1L)).thenReturn(Optional.of(TestData.cancha()));
        return new CanchaService(canchaRepository, reservaService, new ConfiguracionService(configuracionRepository),
                TestData.reloj(ahora));
    }

    private void ocupadas(Reserva... reservas) {
        when(reservaService.buscarQueOcupan(anyLong(), any(), any(), any(), any())).thenReturn(List.of(reservas));
    }

    @Test
    void devuelveLosTurnosLibresDeLaDuracionPedida() {
        Reserva r = reserva(1L, usuario(1L, Role.CLIENTE), EstadoReserva.PAGADA, "15000"); // 18:00-19:30
        ocupadas(r);

        DisponibilidadResponse d = servicio(TestData.AHORA).getDisponibilidad(1L, MANIANA, 90);

        assertThat(d.getHorarioMinimoInicio()).isEqualTo(LocalTime.of(8, 0));
        assertThat(d.getSlotsOcupados()).containsExactly(new Turno(LocalTime.of(18, 0), LocalTime.of(19, 30)));
        assertThat(d.getTurnosDisponibles())
                .contains(new Turno(LocalTime.of(8, 0), LocalTime.of(9, 30)))
                .contains(new Turno(LocalTime.of(16, 30), LocalTime.of(18, 0)))
                .contains(new Turno(LocalTime.of(21, 30), LocalTime.of(23, 0)))
                .doesNotContain(new Turno(LocalTime.of(17, 0), LocalTime.of(18, 30)))
                .doesNotContain(new Turno(LocalTime.of(19, 0), LocalTime.of(20, 30)));
        // el último turno termina justo al cierre
        assertThat(d.getTurnosDisponibles().getLast().getHoraFin()).isEqualTo(LocalTime.of(23, 0));
    }

    @Test
    void hoyEmpiezaEnElProximoTurnoAlineadoConLaApertura() {
        ocupadas();
        LocalDate hoy = TestData.HOY;

        DisponibilidadResponse d = servicio(LocalDateTime.of(hoy, LocalTime.of(14, 10))).getDisponibilidad(1L, hoy, 90);

        assertThat(d.getHorarioMinimoInicio()).isEqualTo(LocalTime.of(14, 30));
        assertThat(d.getTurnosDisponibles().getFirst().getHoraInicio()).isEqualTo(LocalTime.of(14, 30));
    }

    @Test
    void despuesDeLas2330NoFallaYNoHayTurnos() {
        // antes LocalTime.of(24, 0) lanzaba una excepción y respondía 500
        ocupadas();
        LocalDate hoy = TestData.HOY;

        DisponibilidadResponse d = servicio(LocalDateTime.of(hoy, LocalTime.of(23, 40))).getDisponibilidad(1L, hoy, 90);

        assertThat(d.getTurnosDisponibles()).isEmpty();
        assertThat(d.getHorarioMinimoInicio()).isNull();
    }

    @Test
    void sinDuracionUsaLaMinima() {
        ocupadas();
        assertThat(servicio(TestData.AHORA).getDisponibilidad(1L, MANIANA, null).getDuracion()).isEqualTo(90);
    }

    @Test
    void informaElPrecioDeLaCanchaParaLaDuracion() {
        ocupadas();
        CanchaService servicio = servicio(TestData.AHORA);
        // montoReserva 0 + 5000 por cada 30 min
        assertThat(servicio.getDisponibilidad(1L, MANIANA, 90).getMontoCancha()).isEqualByComparingTo("15000");
        assertThat(servicio.getDisponibilidad(1L, MANIANA, 120).getMontoCancha()).isEqualByComparingTo("20000");
        assertThat(servicio.getDisponibilidad(1L, MANIANA, null).getMontoCancha()).isEqualByComparingTo("15000");
    }

    @Test
    void rechazaFechasPasadasYDuracionesInvalidas() {
        ocupadas();
        CanchaService servicio = servicio(TestData.AHORA);
        assertThatThrownBy(() -> servicio.getDisponibilidad(1L, TestData.HOY.minusDays(1), 90))
                .isInstanceOf(ReservaException.class);
        assertThatThrownBy(() -> servicio.getDisponibilidad(1L, MANIANA, 100))
                .isInstanceOf(ReservaException.class);
    }

    @Test
    void canchaInexistente() {
        CanchaService servicio = servicio(TestData.AHORA);
        when(canchaRepository.findById(9L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> servicio.getDisponibilidad(9L, MANIANA, 90))
                .isInstanceOf(CanchaNotFoundException.class);
    }

    @Test
    void listaLasCanchas() {
        CanchaService servicio = servicio(TestData.AHORA);
        when(canchaRepository.findAll()).thenReturn(List.of(TestData.cancha()));
        assertThat(servicio.listarCanchas()).hasSize(1);
        assertThat(servicio.listarCanchas().getFirst().getDescripcion()).isEqualTo("Cancha 1");
    }
}
