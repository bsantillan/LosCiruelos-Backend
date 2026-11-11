package com.LosCiruelos.padel_club_api.pagos.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.LosCiruelos.padel_club_api.TestData;
import com.LosCiruelos.padel_club_api.common.exception.EstadoInvalidoException;
import com.LosCiruelos.padel_club_api.pagos.entity.enums.CanalDePago;
import com.LosCiruelos.padel_club_api.pagos.entity.enums.EstadoPago;
import com.LosCiruelos.padel_club_api.pagos.payment.PaymentProvider;

/** Por dónde entró un pago y cuáles verifica el ADMIN. */
class PagoCanalTest {

    private static Pago pago(PaymentProvider provider, String orderId) {
        return Pago.builder().id(1L).monto(new BigDecimal("7500")).estado(EstadoPago.APROBADO).provider(provider)
                .orderId(orderId).build();
    }

    @Test
    void losDeMercadoPagoSeSeparanEnOnlineQrYTransferencia() {
        assertThat(pago(PaymentProvider.MERCADO_PAGO, null).canal()).isEqualTo(CanalDePago.MERCADO_PAGO_ONLINE);
        assertThat(pago(PaymentProvider.MERCADO_PAGO, "ORD1").canal()).isEqualTo(CanalDePago.MERCADO_PAGO_QR);
        assertThat(pago(PaymentProvider.TRANSFERENCIA_MP, null).canal()).isEqualTo(CanalDePago.TRANSFERENCIA_MP);
        assertThat(pago(PaymentProvider.MANUAL, null).canal()).isEqualTo(CanalDePago.EFECTIVO);
        assertThat(pago(PaymentProvider.CUENTA_DNI, null).canal()).isEqualTo(CanalDePago.CUENTA_DNI);
        assertThat(pago(PaymentProvider.BONIFICACION, null).canal()).isEqualTo(CanalDePago.BONIFICACION);
    }

    @Test
    void elAdminVerificaLasTransferenciasYCuentaDniNoElResto() {
        Pago transferencia = pago(PaymentProvider.TRANSFERENCIA_MP, null);
        transferencia.verificar(true, 1L, TestData.AHORA);
        assertThat(transferencia.estaVerificado()).isTrue();
        // la transferencia no se devuelve por la API: si se cancela, queda para devolver a mano
        assertThat(transferencia.seDevuelvePorApi()).isFalse();

        assertThatThrownBy(() -> pago(PaymentProvider.MERCADO_PAGO, "ORD1").verificar(true, 1L, TestData.AHORA))
                .isInstanceOf(EstadoInvalidoException.class)
                .hasMessage("Solo se verifican los pagos con Cuenta DNI o por transferencia.");
    }
}
