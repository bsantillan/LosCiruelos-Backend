package com.LosCiruelos.padel_club_api.club.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.LosCiruelos.padel_club_api.TestData;
import com.LosCiruelos.padel_club_api.club.dto.request.CierreRequest;
import com.LosCiruelos.padel_club_api.club.dto.request.ConfiguracionRequest;
import com.LosCiruelos.padel_club_api.club.dto.response.CancelacionesResponse;
import com.LosCiruelos.padel_club_api.club.dto.response.FueraDeHorarioResponse;
import com.LosCiruelos.padel_club_api.club.entity.Configuracion;
import com.LosCiruelos.padel_club_api.club.exception.HorariosConReservasException;
import com.LosCiruelos.padel_club_api.club.repository.ConfiguracionRepository;
import com.LosCiruelos.padel_club_api.common.exception.ReservaException;
import com.LosCiruelos.padel_club_api.mapper.ConfiguracionMapper;
import com.LosCiruelos.padel_club_api.usuarios.entity.enums.Role;
import com.LosCiruelos.padel_club_api.usuarios.service.UsuarioService;

/** La configuración general: cambios, días cerrados y la cancelación de lo que queda afuera al confirmar. */
class ConfiguracionGeneralServiceTest {

    private static final String EMAIL = "admin@test.com";
    private static final LocalDate NAVIDAD = LocalDate.of(2026, 12, 25);
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
        when(horariosOcupados.delDia(any())).thenReturn(NADA);
        when(horariosOcupados.cancelar(anyList(), anyString())).thenAnswer(
                inv -> new CancelacionesResponse(inv.getArgument(0), List.of()));
        configuracionService = mock(ConfiguracionService.class);
        when(configuracionService.obtener()).thenReturn(config);
        UsuarioService usuarioService = mock(UsuarioService.class);
        when(usuarioService.autenticado(EMAIL)).thenReturn(TestData.usuario(1L, Role.ADMIN));
        ConfiguracionMapper mapper = new ConfiguracionMapper();
        CambiosDeConfiguracion cambios = new CambiosDeConfiguracion(configuracionRepository, mapper, horariosOcupados,
                usuarioService, TestData.reloj(TestData.AHORA));
        servicio = new ConfiguracionGeneralService(configuracionRepository, configuracionService, mapper, cambios,
                horariosOcupados, TestData.reloj(TestData.AHORA));
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

    /** Antes del cambio no hay nada afuera; con el horario nuevo, lo que se indica. */
    private void dejaAfuera(FueraDeHorarioResponse despues) {
        when(horariosOcupados.fueraDeHorario(any())).thenReturn(NADA, despues);
    }

    @Nested
    class Consultas {

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
            assertThat(r.cancelaciones()).isNull();
        }

        @Test
        void losHorariosPublicosSalenDeLaCacheConLosProximosCierres() {
            config.cerrar(NAVIDAD, "Navidad", 1L, TestData.AHORA);

            var r = servicio.horarios();

            assertThat(r.horarios()).hasSize(7);
            assertThat(r.horarios().get(0).cierre()).isEqualTo(LocalTime.of(23, 0));
            assertThat(r.cierres()).extracting(c -> c.fecha()).containsExactly(NAVIDAD);
            assertThat(r.duracionMinimaTurno()).isEqualTo(90);
            assertThat(r.duracionMaximaTurno()).isEqualTo(180);
            verify(configuracionService).obtener();
            verify(configuracionRepository, never()).obtener();
        }
    }

    @Nested
    class CambioDeConfiguracion {

        @Test
        void reemplazaTodaLaConfiguracion() {
            var r = servicio.actualizar(pedido(), EMAIL);

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
            // el turno mínimo (60 minutos) es $1.000 y cada media hora de más, $6.000,50
            assertThat(config.montoTurno(60)).isEqualTo(new BigDecimal("1000.00"));
            assertThat(config.montoTurno(90)).isEqualTo(new BigDecimal("7000.50"));
            // no había nada afuera: no se cancela nada
            assertThat(r.cancelaciones()).isEqualTo(CancelacionesResponse.NINGUNA);
            verify(horariosOcupados, never()).cancelar(anyList(), anyString());
        }

        @Test
        void lasReglasDeLaConfiguracionSeValidan() {
            ConfiguracionRequest req = pedido();
            req.setMinutosReembolsoTotal(60);

            assertThatThrownBy(() -> servicio.actualizar(req, EMAIL))
                    .isInstanceOf(ReservaException.class)
                    .hasMessage("La anticipación para devolver todo no puede ser menor que la de devolver la seña.");
        }

        @Test
        void siElHorarioNuevoDejaReservasAfueraEs409ConLaListaYNoSeCancelaNada() {
            dejaAfuera(new FueraDeHorarioResponse(List.of(RESERVA), List.of(TURNO_FIJO)));

            assertThatThrownBy(() -> servicio.actualizar(pedido(), EMAIL))
                    .isInstanceOfSatisfying(HorariosConReservasException.class, e -> {
                        assertThat(e.getFuera().reservasFueraDeHorario()).containsExactly(RESERVA);
                        assertThat(e.getFuera().turnosFijosFueraDeHorario()).containsExactly(TURNO_FIJO);
                        assertThat(e.getMessage()).startsWith("El horario nuevo deja afuera 1 reserva y 1 turno fijo.");
                    });
            // se revisa con el horario nuevo (la configuración en memoria, que la transacción revierte)
            verify(horariosOcupados, times(2)).fueraDeHorario(config);
            verify(horariosOcupados, never()).cancelar(anyList(), anyString());
        }

        @Test
        void conConfirmarSeGuardaYDespuesSeCancelanLasReservasAfuera() {
            dejaAfuera(new FueraDeHorarioResponse(List.of(RESERVA), List.of(TURNO_FIJO)));
            when(horariosOcupados.cancelar(List.of(31L), EMAIL)).thenReturn(new CancelacionesResponse(List.of(),
                    List.of(new CancelacionesResponse.SinCancelar(31L, "La reserva tiene un pago en proceso."))));
            ConfiguracionRequest req = pedido();
            req.setConfirmar(true);

            var r = servicio.actualizar(req, EMAIL);

            assertThat(r.horarios()).extracting(h -> h.dia()).containsExactly("Lunes", "Sábado");
            assertThat(config.getDiasAnticipacionReserva()).isEqualTo(15);
            // la que no se pudo cancelar queda informada (el cambio ya está guardado)
            assertThat(r.cancelaciones().sinCancelar()).extracting(CancelacionesResponse.SinCancelar::reservaId)
                    .containsExactly(31L);
            var orden = inOrder(horariosOcupados);
            orden.verify(horariosOcupados, times(2)).fueraDeHorario(config);
            orden.verify(horariosOcupados).cancelar(List.of(31L), EMAIL);
        }

        @Test
        void loQueYaEstabaAfueraNoSeVuelveAAvisarNiSeCancela() {
            FueraDeHorarioResponse yaAfuera = new FueraDeHorarioResponse(List.of(RESERVA), List.of(TURNO_FIJO));
            when(horariosOcupados.fueraDeHorario(any())).thenReturn(yaAfuera, yaAfuera);

            var r = servicio.actualizar(pedido(), EMAIL);

            assertThat(config.getMontoReserva()).isEqualTo(new BigDecimal("1000.00"));
            assertThat(r.cancelaciones()).isEqualTo(CancelacionesResponse.NINGUNA);
            verify(horariosOcupados, never()).cancelar(anyList(), anyString());
        }

        @Test
        void soloSeAvisaYCancelaLoQueQuedaAfueraNuevo() {
            FueraDeHorarioResponse.Reserva otra = new FueraDeHorarioResponse.Reserva(32L, LocalDate.of(2026, 10, 20),
                    LocalTime.of(22, 0), LocalTime.of(23, 0), 1, "Ana Ruiz", "PARTIDO");
            when(horariosOcupados.fueraDeHorario(any())).thenReturn(
                    new FueraDeHorarioResponse(List.of(RESERVA), List.of()),
                    new FueraDeHorarioResponse(List.of(RESERVA, otra), List.of()),
                    new FueraDeHorarioResponse(List.of(RESERVA), List.of()),
                    new FueraDeHorarioResponse(List.of(RESERVA, otra), List.of()));

            assertThatThrownBy(() -> servicio.actualizar(pedido(), EMAIL))
                    .isInstanceOfSatisfying(HorariosConReservasException.class, e -> {
                        assertThat(e.getFuera().reservasFueraDeHorario()).containsExactly(otra);
                        assertThat(e.getMessage()).startsWith("El horario nuevo deja afuera 1 reserva.");
                    });
            ConfiguracionRequest req = pedido();
            req.setConfirmar(true);
            assertThat(servicio.actualizar(req, EMAIL).cancelaciones().canceladas()).containsExactly(32L);
        }
    }

    @Nested
    class DiasCerrados {

        private CierreRequest cierre(Boolean confirmar) {
            CierreRequest req = new CierreRequest();
            req.setFecha(NAVIDAD);
            req.setMotivo(" Navidad ");
            req.setConfirmar(confirmar);
            return req;
        }

        @Test
        void sinReservasEseDiaSeCierraDirecto() {
            var r = servicio.cerrar(cierre(null), EMAIL);

            assertThat(r.fecha()).isEqualTo(NAVIDAD);
            assertThat(r.motivo()).isEqualTo("Navidad");
            assertThat(r.cerradoAt()).isEqualTo(TestData.AHORA);
            assertThat(r.cancelaciones()).isEqualTo(CancelacionesResponse.NINGUNA);
            assertThat(config.cierreDel(NAVIDAD)).get().satisfies(c -> assertThat(c.getCreatedById()).isEqualTo(1L));
            assertThat(servicio.cierres()).extracting(c -> c.fecha()).containsExactly(NAVIDAD);
        }

        @Test
        void conReservasEseDiaEs409ConLaLista() {
            FueraDeHorarioResponse delDia = new FueraDeHorarioResponse(List.of(RESERVA), List.of(TURNO_FIJO));
            when(horariosOcupados.delDia(NAVIDAD)).thenReturn(delDia);

            assertThatThrownBy(() -> servicio.cerrar(cierre(false), EMAIL))
                    .isInstanceOfSatisfying(HorariosConReservasException.class, e -> {
                        assertThat(e.getFuera()).isEqualTo(delDia);
                        assertThat(e.getMessage()).isEqualTo("El viernes 25/12 hay 1 reserva. Para cerrar igual, mandá "
                                + "\"confirmar\": true: se cancelan con reembolso total y se avisa a cada cliente.");
                    });
            verify(horariosOcupados, never()).cancelar(anyList(), anyString());
        }

        @Test
        void conConfirmarSeCierraYSeCancelanLasReservasDeEseDia() {
            when(horariosOcupados.delDia(NAVIDAD)).thenReturn(new FueraDeHorarioResponse(List.of(RESERVA), List.of()));

            var r = servicio.cerrar(cierre(true), EMAIL);

            assertThat(r.cancelaciones().canceladas()).containsExactly(31L);
            verify(horariosOcupados).cancelar(List.of(31L), EMAIL);
        }

        @Test
        void soloLosTurnosFijosDeEseDiaNoFrenanElCierre() {
            // sin partido creado: ese día simplemente no se crea
            when(horariosOcupados.delDia(NAVIDAD)).thenReturn(new FueraDeHorarioResponse(List.of(), List.of(TURNO_FIJO)));

            assertThat(servicio.cerrar(cierre(null), EMAIL).cancelaciones()).isEqualTo(CancelacionesResponse.NINGUNA);
        }

        @Test
        void seReabre() {
            servicio.cerrar(cierre(null), EMAIL);

            servicio.reabrir(NAVIDAD);

            assertThat(config.cierreDel(NAVIDAD)).isEmpty();
            assertThat(servicio.cierres()).isEmpty();
        }
    }
}
