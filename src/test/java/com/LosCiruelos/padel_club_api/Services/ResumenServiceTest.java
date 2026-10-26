package com.LosCiruelos.padel_club_api.Services;

import static com.LosCiruelos.padel_club_api.TestData.AHORA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.LosCiruelos.padel_club_api.TestData;
import com.LosCiruelos.padel_club_api.DTOs.Resumen.CobrosDelPeriodo;
import com.LosCiruelos.padel_club_api.DTOs.Resumen.EntradasDeCaja;
import com.LosCiruelos.padel_club_api.DTOs.Resumen.ProductoVendido;
import com.LosCiruelos.padel_club_api.DTOs.Resumen.TurnosDelPeriodo;
import com.LosCiruelos.padel_club_api.DTOs.Responses.ResumenResponse;
import com.LosCiruelos.padel_club_api.reservas.entity.Reserva;
import com.LosCiruelos.padel_club_api.pagos.entity.enums.EstadoPago;
import com.LosCiruelos.padel_club_api.reservas.entity.enums.EstadoReserva;
import com.LosCiruelos.padel_club_api.pagos.payment.PaymentProvider;
import com.LosCiruelos.padel_club_api.Entities.Enum.TipoProducto;
import com.LosCiruelos.padel_club_api.Exceptions.PermisoDenegadoException;
import com.LosCiruelos.padel_club_api.Exceptions.ReservaException;
import com.LosCiruelos.padel_club_api.Repository.ResumenRepository;

/** Cómo arma el servicio la respuesta. Las consultas en sí se prueban en ResumenRepositoryTest. */
class ResumenServiceTest {

    private static final LocalDate DESDE = LocalDate.of(2026, 10, 1);
    private static final LocalDate HASTA = LocalDate.of(2026, 10, 31);

    private ResumenRepository repo;
    private ResumenService resumen;

    @BeforeEach
    void setUp() {
        repo = mock(ResumenRepository.class);
        resumen = new ResumenService(repo, TestData.reloj(AHORA));
        // sin reservas ni pagos: las cantidades vienen en null y los montos en 0
        when(repo.turnos(any(), any(), any(), any(), any(), any()))
                .thenReturn(new TurnosDelPeriodo(null, $("0"), null, $("0"), null));
        when(repo.cobros(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new CobrosDelPeriodo($("0"), $("0"), $("0"), $("0"), $("0")));
        when(repo.entradasDeCaja(any(), any(), any(), any(), any(), any()))
                .thenReturn(new EntradasDeCaja($("0"), $("0"), $("0")));
        when(repo.devoluciones(any(), any(), any(), any())).thenReturn($("0"));
    }

    private static BigDecimal $(String monto) {
        return new BigDecimal(monto);
    }

    @Test
    void armaElResumenDelPeriodo() {
        when(repo.turnos(DESDE, HASTA, AHORA.toLocalDate(), AHORA.toLocalTime(), Reserva.ESTADOS_QUE_SE_JUEGAN,
                EstadoReserva.CANCELADA)).thenReturn(new TurnosDelPeriodo(28L, $("420000"), 6L, $("90000"), 3L));
        when(repo.cobros(eq(DESDE), eq(HASTA), eq(AHORA.toLocalDate()), eq(AHORA.toLocalTime()),
                eq(Reserva.ESTADOS_QUE_SE_JUEGAN), eq(EstadoReserva.CANCELADA), eq(EstadoPago.APROBADO), any(),
                eq(PaymentProvider.BONIFICACION)))
                .thenReturn(new CobrosDelPeriodo($("380000"), $("15000"), $("45000"), $("0"), $("7500")));
        when(repo.entradasDeCaja(DESDE.atStartOfDay(), HASTA.plusDays(1).atStartOfDay(),
                Set.of(EstadoPago.APROBADO, EstadoPago.DEVUELTO, EstadoPago.A_REEMBOLSAR,
                        EstadoPago.REEMBOLSO_PENDIENTE),
                PaymentProvider.MANUAL, PaymentProvider.MERCADO_PAGO, PaymentProvider.BONIFICACION))
                .thenReturn(new EntradasDeCaja($("90000"), $("100000"), $("40000")));
        when(repo.devoluciones(DESDE.atStartOfDay(), HASTA.plusDays(1).atStartOfDay(), EstadoPago.DEVUELTO,
                PaymentProvider.BONIFICACION)).thenReturn($("7500"));
        ProductoVendido pelotas = new ProductoVendido(1L, "Pelotas", TipoProducto.VENTA, 40L, $("200000"));
        when(repo.productos(DESDE, HASTA, Reserva.ESTADOS_QUE_SE_JUEGAN)).thenReturn(List.of(pelotas));

        ResumenResponse r = resumen.resumen(DESDE, HASTA);

        assertThat(r.desde()).isEqualTo(DESDE);
        assertThat(r.hasta()).isEqualTo(HASTA);
        // deuda = total - cobrado - bonificado
        assertThat(r.jugados()).isEqualTo(new ResumenResponse.Jugados(28, $("420000.00"), $("380000.00"),
                $("15000.00"), $("25000.00")));
        assertThat(r.porJugar()).isEqualTo(new ResumenResponse.PorJugar(6, $("90000.00"), $("45000.00"),
                $("0.00"), $("45000.00")));
        assertThat(r.caja()).isEqualTo(new ResumenResponse.Caja($("230000.00"), $("90000.00"), $("100000.00"),
                $("40000.00"), $("7500.00"), $("222500.00")));
        assertThat(r.cancelaciones()).isEqualTo(new ResumenResponse.Cancelaciones(3, $("7500.00")));
        assertThat(r.productos()).containsExactly(pelotas);
    }

    @Test
    void elRetenidoCuentaLoQueNoSeDevolvioDeLosPagosDevueltosOPorDevolver() {
        resumen.resumen(DESDE, HASTA);

        verify(repo).cobros(DESDE, HASTA, AHORA.toLocalDate(), AHORA.toLocalTime(), Reserva.ESTADOS_QUE_SE_JUEGAN,
                EstadoReserva.CANCELADA, EstadoPago.APROBADO,
                Set.of(EstadoPago.DEVUELTO, EstadoPago.A_REEMBOLSAR, EstadoPago.REEMBOLSO_PENDIENTE),
                PaymentProvider.BONIFICACION);
    }

    @Test
    void unPeriodoSinReservasDaTodoEnCero() {
        ResumenResponse r = resumen.resumen(DESDE, DESDE);

        assertThat(r.jugados()).isEqualTo(new ResumenResponse.Jugados(0, $("0.00"), $("0.00"), $("0.00"),
                $("0.00")));
        assertThat(r.porJugar().turnos()).isZero();
        assertThat(r.caja().neto()).isEqualTo($("0.00"));
        assertThat(r.cancelaciones().turnos()).isZero();
        assertThat(r.productos()).isEmpty();
    }

    @Test
    void sinFechasEsElDiaDeHoyYLaCajaVaDeLas0A_Las24() {
        LocalDate hoy = AHORA.toLocalDate();
        ResumenResponse r = resumen.resumen(null, null, true);

        assertThat(r.desde()).isEqualTo(hoy);
        assertThat(r.hasta()).isEqualTo(hoy);
        verify(repo).turnos(hoy, hoy, hoy, AHORA.toLocalTime(), Reserva.ESTADOS_QUE_SE_JUEGAN,
                EstadoReserva.CANCELADA);
        verify(repo).devoluciones(hoy.atStartOfDay(), hoy.plusDays(1).atStartOfDay(), EstadoPago.DEVUELTO,
                PaymentProvider.BONIFICACION);
    }

    @Test
    void elEmpleadoSoloVeElDiaDeHoy() {
        LocalDate hoy = AHORA.toLocalDate();
        assertThat(resumen.resumen(hoy, hoy, true).desde()).isEqualTo(hoy);

        assertThatThrownBy(() -> resumen.resumen(DESDE, HASTA, true))
                .isInstanceOf(PermisoDenegadoException.class)
                .hasMessage("Solo podés ver el resumen de hoy.");
        assertThatThrownBy(() -> resumen.resumen(hoy.minusDays(1), hoy, true))
                .isInstanceOf(PermisoDenegadoException.class);
        assertThatThrownBy(() -> resumen.resumen(hoy, hoy.plusDays(1), true))
                .isInstanceOf(PermisoDenegadoException.class);
    }

    @Test
    void elAdminVeCualquierPeriodo() {
        assertThat(resumen.resumen(DESDE, HASTA, false).desde()).isEqualTo(DESDE);
    }

    @Test
    void conUnaSolaFechaEsUnError() {
        assertThatThrownBy(() -> resumen.resumen(DESDE, null, false))
                .isInstanceOf(ReservaException.class)
                .hasMessageContaining("las dos fechas");
        assertThatThrownBy(() -> resumen.resumen(null, HASTA, true)).isInstanceOf(ReservaException.class);
    }

    @Test
    void validaElPeriodo() {
        ResumenRepository sinUso = mock(ResumenRepository.class);
        ResumenService servicio = new ResumenService(sinUso, TestData.reloj(AHORA));
        assertThatThrownBy(() -> servicio.resumen(HASTA, DESDE))
                .isInstanceOf(ReservaException.class)
                .hasMessage("La fecha desde no puede ser posterior a hasta.");
        assertThatThrownBy(() -> servicio.resumen(DESDE, DESDE.plusDays(366)))
                .isInstanceOf(ReservaException.class)
                .hasMessageContaining("366 días");
        verifyNoInteractions(sinUso);

        // un año bisiesto completo (366 días) se acepta
        resumen.resumen(LocalDate.of(2028, 1, 1), LocalDate.of(2028, 12, 31));
    }
}
