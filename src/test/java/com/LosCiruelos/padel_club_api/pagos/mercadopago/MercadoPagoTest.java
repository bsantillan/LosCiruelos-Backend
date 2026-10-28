package com.LosCiruelos.padel_club_api.pagos.mercadopago;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.Test;

import com.LosCiruelos.padel_club_api.TestData;
import com.LosCiruelos.padel_club_api.pagos.entity.Pago;
import com.LosCiruelos.padel_club_api.reservas.entity.Reserva;
import com.LosCiruelos.padel_club_api.pagos.entity.enums.EstadoPago;
import com.LosCiruelos.padel_club_api.pagos.payment.EstadoPayment;
import com.LosCiruelos.padel_club_api.reservas.entity.enums.EstadoReserva;
import com.LosCiruelos.padel_club_api.usuarios.entity.enums.Role;
import com.mercadopago.client.preference.PreferenceRequest;

class MercadoPagoTest {

    @Test
    void mapeaLosEstadosDeMercadoPago() {
        assertThat(MercadoPagoPaymentService.mapearEstado("approved")).isEqualTo(EstadoPayment.APROBADO);
        assertThat(MercadoPagoPaymentService.mapearEstado("rejected")).isEqualTo(EstadoPayment.RECHAZADO);
        assertThat(MercadoPagoPaymentService.mapearEstado("cancelled")).isEqualTo(EstadoPayment.RECHAZADO);
        assertThat(MercadoPagoPaymentService.mapearEstado("refunded")).isEqualTo(EstadoPayment.DEVUELTO);
        assertThat(MercadoPagoPaymentService.mapearEstado("charged_back")).isEqualTo(EstadoPayment.DEVUELTO);
        assertThat(MercadoPagoPaymentService.mapearEstado("in_process")).isEqualTo(EstadoPayment.EN_PROCESO);
        assertThat(MercadoPagoPaymentService.mapearEstado("in_mediation")).isEqualTo(EstadoPayment.EN_PROCESO);
        assertThat(MercadoPagoPaymentService.mapearEstado(null)).isEqualTo(EstadoPayment.EN_PROCESO);
    }

    @Test
    void leeElIdDePagoDeLaMetadataEnCualquierFormato() {
        assertThat(MercadoPagoPaymentService.pagoIdDesdeMetadata(Map.of("pago_id", "12"))).isEqualTo(12L);
        assertThat(MercadoPagoPaymentService.pagoIdDesdeMetadata(Map.of("pago_id", 12.0))).isEqualTo(12L);
        assertThat(MercadoPagoPaymentService.pagoIdDesdeMetadata(Map.of("pago_id", "abc"))).isNull();
        assertThat(MercadoPagoPaymentService.pagoIdDesdeMetadata(Map.of())).isNull();
        assertThat(MercadoPagoPaymentService.pagoIdDesdeMetadata(null)).isNull();
    }

    private static Pago pagoDeReserva(EstadoReserva estado) {
        Reserva reserva = TestData.reserva(10L, TestData.usuario(1L, Role.CLIENTE), estado, "15000");
        return TestData.pago(7L, reserva, EstadoPago.PENDIENTE, "7500");
    }

    @Test
    void laPreferenciaDevuelveAlFrontYNoQuedaPendiente() {
        MercadoPagoPaymentService mp = new MercadoPagoPaymentService("https://api/pagos/notificacion",
                "http://localhost:5173/");

        PreferenceRequest pref = mp.armarPreferencia(pagoDeReserva(EstadoReserva.PENDIENTE));

        assertThat(pref.getBackUrls().getSuccess())
                .isEqualTo("http://localhost:5173/pago/resultado?reservaId=10&resultado=aprobado");
        assertThat(pref.getBackUrls().getFailure()).endsWith("resultado=rechazado");
        assertThat(pref.getBackUrls().getPending()).endsWith("resultado=pendiente");
        assertThat(pref.getBinaryMode()).isTrue();
        assertThat(pref.getAutoReturn()).isNull(); // MP no acepta auto_return con URLs locales
        assertThat(pref.getExternalReference()).isEqualTo("10");
        assertThat(pref.getMetadata()).containsEntry("pago_id", "7");
        assertThat(pref.getItems().getFirst().getUnitPrice()).isEqualByComparingTo("7500");
    }

    @Test
    void conHttpsVuelveSoloAlAprobarse() {
        MercadoPagoPaymentService mp = new MercadoPagoPaymentService("https://api/pagos/notificacion",
                "https://reservas.losciruelos.com");

        assertThat(mp.armarPreferencia(pagoDeReserva(EstadoReserva.PENDIENTE)).getAutoReturn()).isEqualTo("approved");
    }

    @Test
    void laPreferenciaVenceConLaReservaPendiente() {
        MercadoPagoPaymentService mp = new MercadoPagoPaymentService("https://api", "http://localhost:5173");
        Pago pago = pagoDeReserva(EstadoReserva.PENDIENTE);

        PreferenceRequest pref = mp.armarPreferencia(pago);

        assertThat(pref.getExpires()).isTrue();
        assertThat(pref.getExpirationDateTo().toLocalDateTime()).isEqualTo(pago.getReserva().getExpiresAt());

        // el saldo de una reserva ya señada no tiene ese plazo
        assertThat(mp.armarPreferencia(pagoDeReserva(EstadoReserva.SEÑADA)).getExpirationDateTo()).isNull();
    }

    @Test
    void validaLaFirmaDelWebhook() throws Exception {
        String secret = "clave-secreta";
        MercadoPagoWebhookValidator validador = new MercadoPagoWebhookValidator(secret);
        String manifiesto = "id:123456;request-id:req-1;ts:1700000000;";
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String v1 = HexFormat.of().formatHex(mac.doFinal(manifiesto.getBytes(StandardCharsets.UTF_8)));

        assertThat(validador.esValida("ts=1700000000,v1=" + v1, "req-1", "123456")).isTrue();
        assertThat(validador.esValida("ts=1700000000,v1=" + v1, "req-2", "123456")).isFalse();
        assertThat(validador.esValida("ts=1700000001,v1=" + v1, "req-1", "123456")).isFalse();
        assertThat(validador.esValida("v1=" + v1, "req-1", "123456")).isFalse();
        assertThat(validador.esValida(null, "req-1", "123456")).isFalse();
    }

    @Test
    void laClaveDeIdempotenciaUsaElUuidDelPagoYNoSuId() {
        Pago pago = pagoDeReserva(EstadoReserva.PENDIENTE);
        Pago otro = pagoDeReserva(EstadoReserva.PENDIENTE); // mismo id 7 (ej. base recreada)

        String clave = MercadoPagoPaymentService.claveIdempotencia(pago, "reembolso");

        assertThat(clave).isEqualTo("losciruelos-reembolso-" + pago.getClaveIdempotencia());
        assertThat(clave).isNotEqualTo(MercadoPagoPaymentService.claveIdempotencia(otro, "reembolso"));
        // la misma en cada reintento
        assertThat(MercadoPagoPaymentService.claveIdempotencia(pago, "reembolso")).isEqualTo(clave);
    }

    @Test
    void elVencimientoDeLaPreferenciaVaConElOffsetDeArgentina() {
        MercadoPagoPaymentService mp = new MercadoPagoPaymentService("https://api", "http://localhost:5173");

        PreferenceRequest pref = mp.armarPreferencia(pagoDeReserva(EstadoReserva.PENDIENTE));

        assertThat(pref.getExpirationDateTo().getOffset()).hasToString("-03:00");
    }

    @Test
    void sinClaveConfiguradaNoSeValidaLaFirma() {
        MercadoPagoWebhookValidator validador = new MercadoPagoWebhookValidator("");
        assertThat(validador.estaConfigurado()).isFalse();
        assertThat(validador.esValida("cualquier cosa", null, "1")).isTrue();
    }
}
