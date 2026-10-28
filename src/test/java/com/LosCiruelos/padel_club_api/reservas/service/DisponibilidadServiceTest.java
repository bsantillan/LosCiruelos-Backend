package com.LosCiruelos.padel_club_api.reservas.service;

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

import com.LosCiruelos.padel_club_api.mapper.ConfiguracionMapper;
import com.LosCiruelos.padel_club_api.mapper.CanchaMapper;
import com.LosCiruelos.padel_club_api.club.service.CanchaService;
import com.LosCiruelos.padel_club_api.club.service.ConfiguracionService;
import com.LosCiruelos.padel_club_api.TestData;
import com.LosCiruelos.padel_club_api.reservas.dto.response.DisponibilidadResponse;
import com.LosCiruelos.padel_club_api.reservas.dto.response.DisponibilidadResponse.Turno;
import com.LosCiruelos.padel_club_api.reservas.entity.Reserva;
import com.LosCiruelos.padel_club_api.reservas.entity.enums.EstadoReserva;
import com.LosCiruelos.padel_club_api.usuarios.entity.enums.Role;
import com.LosCiruelos.padel_club_api.Exceptions.CanchaNotFoundException;
import com.LosCiruelos.padel_club_api.Exceptions.ReservaException;
import com.LosCiruelos.padel_club_api.club.repository.CanchaRepository;
import com.LosCiruelos.padel_club_api.club.repository.ConfiguracionRepository;
import com.LosCiruelos.padel_club_api.reservas.repository.ReservaRepository;
import com.LosCiruelos.padel_club_api.reservas.repository.TurnoFijoRepository;

/** Disponibilidad de una cancha: las mismas reglas que al reservar. */
class DisponibilidadServiceTest {

    private final ReservaRepository reservaRepository = mock(ReservaRepository.class);
    private final CanchaRepository canchaRepository = mock(CanchaRepository.class);

    private DisponibilidadService servicio(LocalDateTime ahora) {
        ConfiguracionRepository configuracionRepository = mock(ConfiguracionRepository.class);
        when(configuracionRepository.obtener()).thenReturn(TestData.configuracion());
        when(canchaRepository.findById(1L)).thenReturn(Optional.of(TestData.cancha()));
        return new DisponibilidadService(new ConfiguracionService(configuracionRepository, new ConfiguracionMapper()),
                new CanchaService(canchaRepository, new CanchaMapper()), reservaRepository, mock(TurnoFijoRepository.class),
                TestData.reloj(ahora));
    }

    private void ocupadas(Reserva... reservas) {
        when(reservaRepository.findQueOcupan(anyLong(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of(reservas));
    }

    @Test
    void devuelveLosTurnosLibresDeLaDuracionPedida() {
        Reserva r = reserva(1L, usuario(1L, Role.CLIENTE), EstadoReserva.PAGADA, "15000"); // 18:00-19:30
        ocupadas(r);

        DisponibilidadResponse d = servicio(TestData.AHORA).disponibilidad(1L, MANIANA, 90);

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

        DisponibilidadResponse d = servicio(LocalDateTime.of(hoy, LocalTime.of(14, 10))).disponibilidad(1L, hoy, 90);

        assertThat(d.getHorarioMinimoInicio()).isEqualTo(LocalTime.of(14, 30));
        assertThat(d.getTurnosDisponibles().getFirst().getHoraInicio()).isEqualTo(LocalTime.of(14, 30));
    }

    @Test
    void despuesDeLas2330NoFallaYNoHayTurnos() {
        // antes LocalTime.of(24, 0) lanzaba una excepción y respondía 500
        ocupadas();
        LocalDate hoy = TestData.HOY;

        DisponibilidadResponse d = servicio(LocalDateTime.of(hoy, LocalTime.of(23, 40))).disponibilidad(1L, hoy, 90);

        assertThat(d.getTurnosDisponibles()).isEmpty();
        assertThat(d.getHorarioMinimoInicio()).isNull();
    }

    @Test
    void sinDuracionUsaLaMinima() {
        ocupadas();
        assertThat(servicio(TestData.AHORA).disponibilidad(1L, MANIANA, null).getDuracion()).isEqualTo(90);
    }

    @Test
    void informaElPrecioDeLaCanchaParaLaDuracion() {
        ocupadas();
        DisponibilidadService servicio = servicio(TestData.AHORA);
        // montoReserva 0 + 5000 por cada 30 min
        assertThat(servicio.disponibilidad(1L, MANIANA, 90).getMontoCancha()).isEqualByComparingTo("15000");
        assertThat(servicio.disponibilidad(1L, MANIANA, 120).getMontoCancha()).isEqualByComparingTo("20000");
        assertThat(servicio.disponibilidad(1L, MANIANA, null).getMontoCancha()).isEqualByComparingTo("15000");
    }

    @Test
    void rechazaFechasPasadasYDuracionesInvalidas() {
        ocupadas();
        DisponibilidadService servicio = servicio(TestData.AHORA);
        assertThatThrownBy(() -> servicio.disponibilidad(1L, TestData.HOY.minusDays(1), 90))
                .isInstanceOf(ReservaException.class);
        assertThatThrownBy(() -> servicio.disponibilidad(1L, MANIANA, 100))
                .isInstanceOf(ReservaException.class);
    }

    @Test
    void canchaInexistente() {
        DisponibilidadService servicio = servicio(TestData.AHORA);
        when(canchaRepository.findById(9L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> servicio.disponibilidad(9L, MANIANA, 90))
                .isInstanceOf(CanchaNotFoundException.class);
    }
}
