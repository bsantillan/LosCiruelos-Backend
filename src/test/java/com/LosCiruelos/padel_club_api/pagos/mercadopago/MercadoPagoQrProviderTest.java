package com.LosCiruelos.padel_club_api.pagos.mercadopago;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.LosCiruelos.padel_club_api.TestData;
import com.LosCiruelos.padel_club_api.pagos.entity.Pago;
import com.LosCiruelos.padel_club_api.pagos.entity.enums.EstadoPago;
import com.LosCiruelos.padel_club_api.pagos.exception.PagoProveedorException;
import com.LosCiruelos.padel_club_api.pagos.exception.ReembolsoFallidoException;
import com.LosCiruelos.padel_club_api.pagos.payment.PaymentProvider;
import com.LosCiruelos.padel_club_api.pagos.payment.ProveedorCobroQr.EstadoOrden;
import com.LosCiruelos.padel_club_api.pagos.payment.ProveedorCobroQr.OrdenNoCancelableException;
import com.LosCiruelos.padel_club_api.reservas.entity.Reserva;
import com.LosCiruelos.padel_club_api.reservas.entity.enums.EstadoReserva;
import com.LosCiruelos.padel_club_api.usuarios.entity.enums.Role;

/** Lo que se le manda a la API de Orders y cómo se lee lo que responde. */
class MercadoPagoQrProviderTest {

    private static final String API = "https://api.mercadopago.com";
    private static final UUID CLAVE = UUID.fromString("7f3c2b1a-0000-4000-8000-000000000001");

    private MockRestServiceServer mp;
    private MercadoPagoQrProvider proveedor;
    private Pago pago;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        mp = MockRestServiceServer.bindTo(builder).build();
        proveedor = new MercadoPagoQrProvider(builder, API, "TEST-token", "CAJA1");
        Reserva reserva = TestData.reserva(10L, TestData.usuario(1L, Role.CLIENTE), EstadoReserva.SEÑADA, "15000");
        pago = Pago.builder().id(55L).reserva(reserva).monto(new BigDecimal("7500")).estado(EstadoPago.EN_PROCESO)
                .provider(PaymentProvider.MERCADO_PAGO).claveIdempotencia(CLAVE).orderId("ORD1").build();
    }

    @Test
    void leAsignaALaCajaUnaOrderEnModoEstaticoPorElMontoExacto() {
        mp.expect(requestTo(API + "/v1/orders"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer TEST-token"))
                .andExpect(header("X-Idempotency-Key", "losciruelos-qr-" + CLAVE))
                .andExpect(content().json("""
                        {"type":"qr","external_reference":"reserva-10-pago-55","total_amount":"7500.00",
                         "description":"Turno","expiration_time":"PT10M",
                         "config":{"qr":{"external_pos_id":"CAJA1","mode":"static"}},
                         "transactions":{"payments":[{"amount":"7500.00"}]}}
                        """, true))
                .andRespond(withSuccess("""
                        {"id":"ORD1","status":"created","config":{"qr":{"external_pos_id":"CAJA1","mode":"static"}}}
                        """, MediaType.APPLICATION_JSON));

        assertThat(proveedor.crear(pago, "Turno", 10)).isEqualTo("ORD1");
        mp.verify();
    }

    @Test
    void unErrorDeMercadoPagoAlCrearEs502() {
        mp.expect(requestTo(API + "/v1/orders")).andRespond(withStatus(HttpStatus.BAD_REQUEST));

        assertThatThrownBy(() -> proveedor.crear(pago, "Turno", 10)).isInstanceOf(PagoProveedorException.class);
    }

    @Test
    void leeElEstadoYElMontoCobradoDeLaOrder() {
        mp.expect(requestTo(API + "/v1/orders/ORD1")).andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"id":"ORD1","status":"processed","status_detail":"processed",
                         "transactions":{"payments":[{"id":"PAY1","amount":"7500.00","paid_amount":"7500.00",
                           "status":"processed","status_detail":"accredited"}]}}
                        """, MediaType.APPLICATION_JSON));

        var estado = proveedor.consultar("ORD1");

        assertThat(estado.estado()).isEqualTo(EstadoOrden.PROCESADA);
        assertThat(estado.montoPagado()).isEqualByComparingTo("7500");
        assertThat(estado.pagoId()).isEqualTo("PAY1");
    }

    @Test
    void cadaStatusDeLaOrderTieneSuEstado() {
        assertThat(MercadoPagoQrProvider.estado(java.util.Map.of("status", "created")).estado())
                .isEqualTo(EstadoOrden.CREADA);
        assertThat(MercadoPagoQrProvider.estado(java.util.Map.of("status", "expired")).estado())
                .isEqualTo(EstadoOrden.VENCIDA);
        assertThat(MercadoPagoQrProvider.estado(java.util.Map.of("status", "canceled")).estado())
                .isEqualTo(EstadoOrden.CANCELADA);
        assertThat(MercadoPagoQrProvider.estado(java.util.Map.of("status", "refunded")).estado())
                .isEqualTo(EstadoOrden.DEVUELTA);
        // un estado que no conocemos: sigue esperando (el job lo vuelve a mirar)
        assertThat(MercadoPagoQrProvider.estado(java.util.Map.of("status", "action_required")).estado())
                .isEqualTo(EstadoOrden.CREADA);
    }

    @Test
    void cancelarUnaOrderQueYaNoEstaCreadaAvisa() {
        mp.expect(requestTo(API + "/v1/orders/ORD1/cancel"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Idempotency-Key", "losciruelos-qrcancel-" + CLAVE))
                .andRespond(withStatus(HttpStatus.CONFLICT).body("{\"errors\":[{\"code\":\"order_already_canceled\"}]}"));

        assertThatThrownBy(() -> proveedor.cancelar(pago)).isInstanceOf(OrdenNoCancelableException.class);
    }

    @Test
    void elReembolsoTotalVaSinBody() {
        mp.expect(requestTo(API + "/v1/orders/ORD1/refund"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Idempotency-Key", "losciruelos-qrrefund-" + CLAVE))
                .andExpect(content().string(""))
                .andRespond(withStatus(HttpStatus.CREATED));

        proveedor.reembolsar(pago, new BigDecimal("7500"));
        mp.verify();
    }

    @Test
    void elReembolsoParcialIndicaElPagoYElMonto() {
        mp.expect(requestTo(API + "/v1/orders/ORD1")).andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"id":"ORD1","status":"processed","transactions":{"payments":[{"id":"PAY1","amount":"7500.00"}]}}
                        """, MediaType.APPLICATION_JSON));
        mp.expect(requestTo(API + "/v1/orders/ORD1/refund"))
                .andExpect(content().json("{\"transactions\":[{\"id\":\"PAY1\",\"amount\":\"2500.00\"}]}", true))
                .andRespond(withStatus(HttpStatus.CREATED));

        proveedor.reembolsar(pago, new BigDecimal("2500"));
        mp.verify();
    }

    @Test
    void unRechazoDelReembolsoEsDefinitivoSalvoQueTodaviaNoSePueda() {
        mp.expect(requestTo(API + "/v1/orders/ORD1/refund")).andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY));
        assertThatThrownBy(() -> proveedor.reembolsar(pago, new BigDecimal("7500")))
                .isInstanceOfSatisfying(ReembolsoFallidoException.class, e -> assertThat(e.isDefinitivo()).isTrue());

        mp.reset();
        mp.expect(requestTo(API + "/v1/orders/ORD1/refund")).andRespond(withStatus(HttpStatus.valueOf(425)));
        assertThatThrownBy(() -> proveedor.reembolsar(pago, new BigDecimal("7500")))
                .isInstanceOfSatisfying(ReembolsoFallidoException.class, e -> assertThat(e.isDefinitivo()).isFalse());
    }

    @Test
    void sinCajaNoEstaConfigurado() {
        assertThat(new MercadoPagoQrProvider(RestClient.builder(), API, "TEST-token", "").estaConfigurado()).isFalse();
        assertThat(proveedor.estaConfigurado()).isTrue();
    }
}
