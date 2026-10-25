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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.LosCiruelos.padel_club_api.TestData;
import com.LosCiruelos.padel_club_api.DTOs.Resumen.CobrosPorEstado;
import com.LosCiruelos.padel_club_api.DTOs.Resumen.ProductoVendido;
import com.LosCiruelos.padel_club_api.DTOs.Resumen.ReservasPorEstado;
import com.LosCiruelos.padel_club_api.DTOs.Responses.ResumenResponse;
import com.LosCiruelos.padel_club_api.Entities.Reserva;
import com.LosCiruelos.padel_club_api.Entities.Enum.EstadoPago;
import com.LosCiruelos.padel_club_api.Entities.Enum.EstadoReserva;
import com.LosCiruelos.padel_club_api.Entities.Enum.PaymentProvider;
import com.LosCiruelos.padel_club_api.Entities.Enum.TipoProducto;
import com.LosCiruelos.padel_club_api.Exceptions.ReservaException;
import com.LosCiruelos.padel_club_api.Repository.ResumenRepository;

class ResumenServiceTest {

    private static final LocalDate DESDE = LocalDate.of(2026, 10, 1);
    private static final LocalDate HASTA = LocalDate.of(2026, 10, 31);

    private ResumenRepository repo;
    private ResumenService resumen;

    @BeforeEach
    void setUp() {
        repo = mock(ResumenRepository.class);
        resumen = new ResumenService(repo, TestData.reloj(AHORA));
    }

    private static BigDecimal $(String monto) {
        return new BigDecimal(monto);
    }

    @Test
    void calculaElResumenDelPeriodo() {
        when(repo.reservasPorEstado(DESDE, HASTA, AHORA.toLocalDate(), AHORA.toLocalTime())).thenReturn(List.of(
                new ReservasPorEstado(EstadoReserva.COMPLETADA, 20L, $("300000"), 20L),
                new ReservasPorEstado(EstadoReserva.SEÑADA, 6L, $("90000"), 5L),       // 5 jugadas con saldo
                new ReservasPorEstado(EstadoReserva.PAGADA, 4L, $("60000"), 3L),
                new ReservasPorEstado(EstadoReserva.CANCELADA, 3L, $("45000"), 1L),
                new ReservasPorEstado(EstadoReserva.EXPIRADA, 2L, $("30000"), 2L),     // no cuentan
                new ReservasPorEstado(EstadoReserva.PENDIENTE, 1L, $("15000"), 0L)));
        // en el club: 150.000, de los que 120.000 son efectivo y 30.000 tarjeta/QR (Mercado Pago presencial)
        // bonificado por el ADMIN: 10.000 en reservas que se juegan y 3.000 en una cancelada (no cuentan como cobrado)
        when(repo.cobrosPorEstado(eq(DESDE), eq(HASTA), eq(EstadoPago.APROBADO), any(), eq(PaymentProvider.MANUAL),
                eq(PaymentProvider.BONIFICACION)))
                .thenReturn(List.of(
                        new CobrosPorEstado(EstadoReserva.COMPLETADA, $("200000"), $("100000"), $("80000"), $("10000")),
                        new CobrosPorEstado(EstadoReserva.SEÑADA, $("40000"), $("5000"), $("5000"), $("0")),
                        new CobrosPorEstado(EstadoReserva.PAGADA, $("0"), $("45000"), $("35000"), $("0")),
                        new CobrosPorEstado(EstadoReserva.CANCELADA, $("7500"), $("0"), $("0"), $("3000"))));
        ProductoVendido pelotas = new ProductoVendido(1L, "Pelotas", TipoProducto.VENTA, 40L, $("200000"));
        when(repo.productos(DESDE, HASTA, Reserva.ESTADOS_QUE_SE_JUEGAN)).thenReturn(List.of(pelotas));

        ResumenResponse r = resumen.resumen(DESDE, HASTA);

        assertThat(r.desde()).isEqualTo(DESDE);
        assertThat(r.hasta()).isEqualTo(HASTA);
        assertThat(r.montoEsperado()).isEqualTo($("450000.00"));
        assertThat(r.cobradoOnline()).isEqualTo($("240000.00"));
        assertThat(r.cobradoEnClub()).isEqualTo($("150000.00"));
        assertThat(r.montoCobrado()).isEqualTo($("390000.00"));
        assertThat(r.cobradoEfectivo()).isEqualTo($("120000.00"));
        assertThat(r.cobradoMercadoPago()).isEqualTo($("270000.00"));
        assertThat(r.montoBonificado()).isEqualTo($("10000.00"));
        // lo que falta = esperado - cobrado - bonificado
        assertThat(r.montoPendiente()).isEqualTo($("50000.00"));
        assertThat(r.retenidoCancelaciones()).isEqualTo($("7500.00"));
        assertThat(r.partidosJugados()).isEqualTo(28);
        assertThat(r.partidosCancelados()).isEqualTo(3);
        assertThat(r.productos()).containsExactly(pelotas);
    }

    @Test
    void losPagosDevueltosOPorDevolverCuentanComoDevueltos() {
        resumen.resumen(DESDE, HASTA);

        verify(repo).cobrosPorEstado(DESDE, HASTA, EstadoPago.APROBADO,
                java.util.Set.of(EstadoPago.DEVUELTO, EstadoPago.A_REEMBOLSAR, EstadoPago.REEMBOLSO_PENDIENTE),
                PaymentProvider.MANUAL, PaymentProvider.BONIFICACION);
    }

    @Test
    void unPeriodoSinReservasDaTodoEnCero() {
        ResumenResponse r = resumen.resumen(DESDE, DESDE);

        assertThat(r.montoEsperado()).isEqualTo($("0.00"));
        assertThat(r.montoCobrado()).isEqualTo($("0.00"));
        assertThat(r.montoPendiente()).isEqualTo($("0.00"));
        assertThat(r.partidosJugados()).isZero();
        assertThat(r.productos()).isEmpty();
    }

    @Test
    void sinFechasEsElDiaDeHoy() {
        ResumenResponse r = resumen.resumen(null, null, true);

        assertThat(r.desde()).isEqualTo(AHORA.toLocalDate());
        assertThat(r.hasta()).isEqualTo(AHORA.toLocalDate());
        verify(repo).reservasPorEstado(AHORA.toLocalDate(), AHORA.toLocalDate(), AHORA.toLocalDate(),
                AHORA.toLocalTime());
    }

    @Test
    void elEmpleadoSoloVeElDiaDeHoy() {
        LocalDate hoy = AHORA.toLocalDate();
        assertThat(resumen.resumen(hoy, hoy, true).desde()).isEqualTo(hoy);

        assertThatThrownBy(() -> resumen.resumen(DESDE, HASTA, true))
                .isInstanceOf(com.LosCiruelos.padel_club_api.Exceptions.PermisoDenegadoException.class)
                .hasMessage("Solo podés ver el resumen de hoy.");
        assertThatThrownBy(() -> resumen.resumen(hoy.minusDays(1), hoy, true))
                .isInstanceOf(com.LosCiruelos.padel_club_api.Exceptions.PermisoDenegadoException.class);
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
        assertThatThrownBy(() -> resumen.resumen(HASTA, DESDE))
                .isInstanceOf(ReservaException.class)
                .hasMessage("La fecha desde no puede ser posterior a hasta.");
        assertThatThrownBy(() -> resumen.resumen(DESDE, DESDE.plusDays(366)))
                .isInstanceOf(ReservaException.class)
                .hasMessageContaining("366 días");
        verifyNoInteractions(repo);

        // un año bisiesto completo (366 días) se acepta
        resumen.resumen(LocalDate.of(2028, 1, 1), LocalDate.of(2028, 12, 31));
    }
}
