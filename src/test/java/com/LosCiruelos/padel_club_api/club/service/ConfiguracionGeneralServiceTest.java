package com.LosCiruelos.padel_club_api.club.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.LosCiruelos.padel_club_api.TestData;
import com.LosCiruelos.padel_club_api.club.dto.request.ConfiguracionRequest;
import com.LosCiruelos.padel_club_api.club.dto.response.FueraDeHorarioResponse;
import com.LosCiruelos.padel_club_api.club.entity.Configuracion;
import com.LosCiruelos.padel_club_api.club.exception.HorariosConReservasException;
import com.LosCiruelos.padel_club_api.club.repository.ConfiguracionRepository;
import com.LosCiruelos.padel_club_api.common.exception.ReservaException;
import com.LosCiruelos.padel_club_api.mapper.ConfiguracionMapper;

class ConfiguracionGeneralServiceTest {

    private static final FueraDeHorarioResponse NADA = new FueraDeHorarioResponse(List.of(), List.of());
    private static final FueraDeHorarioResponse.Reserva RESERVA = new FueraDeHorarioResponse.Reserva(31L,
            LocalDate.of(2026, 10, 19), LocalTime.of(8, 0), LocalTime.of(9, 30), 2, "Juan Gómez", "NORMAL");
    private static final FueraDeHorarioResponse.TurnoFijo TURNO_FIJO = new FueraDeHorarioResponse.TurnoFijo(4L,
            DayOfWeek.MONDAY, LocalTime.of(8, 0), LocalTime.of(9, 30), 1);

    private ConfiguracionRepository configuracionRepository;
    private HorariosOcupados horariosOcupados;
    private ConfiguracionService configuracionService;
    private ConfiguracionGeneralService servicio;
    private Configuracion config;

    @BeforeEach
    void setUp() {
        config = TestData.configuracion();
        configuracionRepository = mock(ConfiguracionRepository.class);
        when(configuracionRepository.obtener()).thenReturn(config);
        horariosOcupados = mock(HorariosOcupados.class);
        when(horariosOcupados.fueraDeHorario(any())).thenReturn(NADA);
        configuracionService = mock(ConfiguracionService.class);
        when(configuracionService.obtener()).thenReturn(config);
        servicio = new ConfiguracionGeneralService(configuracionRepository, configuracionService,
                new ConfiguracionMapper(), horariosOcupados);
    }

    private static ConfiguracionRequest.Horario horario(String dia, int apertura, int cierre) {
        ConfiguracionRequest.Horario h = new ConfiguracionRequest.Horario();
        h.setDia(dia);
        h.setApertura(LocalTime.of(apertura, 0));
        h.setCierre(LocalTime.of(cierre, 0));
        return h;
    }

    private static ConfiguracionRequest pedido() {
        ConfiguracionRequest req = new ConfiguracionRequest();
        req.setMontoReserva(new BigDecimal("1000"));
        req.setMontoXMediaHora(new BigDecimal("6000.5"));
        req.setPorcentajeSenia(new BigDecimal("30"));
        req.setDuracionMinimaTurno(60);
        req.setDuracionMaximaTurno(120);
        req.setMinutosReembolsoTotal(2880);
        req.setMinutosReembolsoSenia(720);
        req.setRecordatorioActivo(false);
        req.setMinutosRecordatorio(60);
        req.setDiasAnticipacion(15);
        req.setHorarios(List.of(horario("sabado", 10, 21), horario("Lunes", 8, 23)));
        return req;
    }

    /** Antes del cambio no hay nada afuera; con el horario nuevo, una reserva y un turno fijo. */
    private void dejaAfuera(FueraDeHorarioResponse despues) {
        when(horariosOcupados.fueraDeHorario(any())).thenReturn(NADA, despues);
    }

    @Test
    void devuelveTodaLaConfiguracionConLosDiasDeLunesADomingo() {
        var r = servicio.general();

        assertThat(r.montoXMediaHora()).isEqualTo(new BigDecimal("5000.00"));
        assertThat(r.porcentajeSenia()).isEqualTo(new BigDecimal("50.00"));
        assertThat(r.duracionMinimaTurno()).isEqualTo(90);
        assertThat(r.minutosReembolsoTotal()).isEqualTo(1440);
        assertThat(r.diasAnticipacion()).isEqualTo(30);
        assertThat(r.horarios()).extracting(h -> h.dia()).containsExactly("Lunes", "Martes", "Miércoles", "Jueves",
                "Viernes", "Sábado", "Domingo");
        assertThat(r.horarios().get(5).apertura()).isEqualTo(LocalTime.of(9, 0));
    }

    @Test
    void losHorariosPublicosSalenDeLaConfiguracionEnCache() {
        var r = servicio.horarios();

        assertThat(r.horarios()).hasSize(7);
        assertThat(r.horarios().get(0).cierre()).isEqualTo(LocalTime.of(23, 0));
        assertThat(r.duracionMinimaTurno()).isEqualTo(90);
        assertThat(r.duracionMaximaTurno()).isEqualTo(180);
        verify(configuracionService).obtener();
        verify(configuracionRepository, never()).obtener();
    }

    @Test
    void reemplazaTodaLaConfiguracion() {
        var r = servicio.actualizar(pedido());

        assertThat(config.getMontoReserva()).isEqualTo(new BigDecimal("1000.00"));
        assertThat(config.getMontoXMediaHora()).isEqualTo(new BigDecimal("6000.50"));
        assertThat(config.getPorcentajeSenia()).isEqualTo(new BigDecimal("30.00"));
        assertThat(config.getDuracionMinimaTurno()).isEqualTo(60);
        assertThat(config.getDuracionMaximaTurno()).isEqualTo(120);
        assertThat(config.getMinutosReembolsoTotal()).isEqualTo(2880);
        assertThat(config.getMinutosReembolsoSenia()).isEqualTo(720);
        assertThat(config.getRecordatorioActivo()).isFalse();
        assertThat(config.getMinutosRecordatorio()).isEqualTo(60);
        assertThat(config.getDiasAnticipacionReserva()).isEqualTo(15);
        assertThat(r.horarios()).extracting(h -> h.dia()).containsExactly("Lunes", "Sábado");
        // el precio de un turno de 90 minutos con la configuración nueva
        assertThat(config.montoTurno(90)).isEqualTo(new BigDecimal("19001.50"));
    }

    @Test
    void lasReglasDeLaConfiguracionSeValidan() {
        ConfiguracionRequest req = pedido();
        req.setMinutosReembolsoTotal(60);

        assertThatThrownBy(() -> servicio.actualizar(req))
                .isInstanceOf(ReservaException.class)
                .hasMessage("La anticipación para devolver todo no puede ser menor que la de devolver la seña.");
    }

    @Test
    void siElHorarioNuevoDejaReservasAfueraEs409ConLaLista() {
        dejaAfuera(new FueraDeHorarioResponse(List.of(RESERVA), List.of(TURNO_FIJO)));

        assertThatThrownBy(() -> servicio.actualizar(pedido()))
                .isInstanceOfSatisfying(HorariosConReservasException.class, e -> {
                    assertThat(e.getFuera().reservasFueraDeHorario()).containsExactly(RESERVA);
                    assertThat(e.getFuera().turnosFijosFueraDeHorario()).containsExactly(TURNO_FIJO);
                    assertThat(e.getMessage()).startsWith("El horario nuevo deja afuera 1 reserva y 1 turno fijo.");
                });
        // se revisa con el horario nuevo (la configuración en memoria, que la transacción revierte)
        verify(horariosOcupados, org.mockito.Mockito.times(2)).fueraDeHorario(config);
    }

    @Test
    void conConfirmarSeGuardaIgual() {
        dejaAfuera(new FueraDeHorarioResponse(List.of(RESERVA), List.of()));
        ConfiguracionRequest req = pedido();
        req.setConfirmar(true);

        var r = servicio.actualizar(req);

        assertThat(r.horarios()).extracting(h -> h.dia()).containsExactly("Lunes", "Sábado");
        assertThat(config.getDiasAnticipacionReserva()).isEqualTo(15);
    }

    @Test
    void loQueYaEstabaAfueraNoSeVuelveAAvisar() {
        // ya confirmado antes: la misma reserva sigue afuera y se cambia otra cosa
        FueraDeHorarioResponse yaAfuera = new FueraDeHorarioResponse(List.of(RESERVA), List.of(TURNO_FIJO));
        when(horariosOcupados.fueraDeHorario(any())).thenReturn(yaAfuera, yaAfuera);

        servicio.actualizar(pedido());

        assertThat(config.getMontoReserva()).isEqualTo(new BigDecimal("1000.00"));
    }

    @Test
    void soloSeAvisaLoQueQuedaAfueraNuevo() {
        FueraDeHorarioResponse.Reserva otra = new FueraDeHorarioResponse.Reserva(32L, LocalDate.of(2026, 10, 20),
                LocalTime.of(22, 0), LocalTime.of(23, 0), 1, "Ana Ruiz", "PARTIDO");
        when(horariosOcupados.fueraDeHorario(any())).thenReturn(
                new FueraDeHorarioResponse(List.of(RESERVA), List.of()),
                new FueraDeHorarioResponse(List.of(RESERVA, otra), List.of()));

        assertThatThrownBy(() -> servicio.actualizar(pedido()))
                .isInstanceOfSatisfying(HorariosConReservasException.class, e -> {
                    assertThat(e.getFuera().reservasFueraDeHorario()).containsExactly(otra);
                    assertThat(e.getMessage()).startsWith("El horario nuevo deja afuera 1 reserva.");
                });
    }
}
