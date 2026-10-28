package com.LosCiruelos.padel_club_api.reportes.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import com.LosCiruelos.padel_club_api.TestData;
import com.LosCiruelos.padel_club_api.reportes.dto.response.ResumenResponse;
import com.LosCiruelos.padel_club_api.club.entity.Cancha;
import com.LosCiruelos.padel_club_api.pagos.entity.Pago;
import com.LosCiruelos.padel_club_api.reservas.entity.Reserva;
import com.LosCiruelos.padel_club_api.Entities.Usuario;
import com.LosCiruelos.padel_club_api.pagos.entity.enums.EstadoPago;
import com.LosCiruelos.padel_club_api.reservas.entity.enums.EstadoReserva;
import com.LosCiruelos.padel_club_api.pagos.payment.PaymentProvider;
import com.LosCiruelos.padel_club_api.Entities.Enum.Role;
import com.LosCiruelos.padel_club_api.reportes.service.ResumenService;
import com.LosCiruelos.padel_club_api.common.Reloj;

/**
 * Las consultas del resumen contra una base real (H2, esquema generado por Hibernate), a través
 * del servicio. Hoy es el martes 13/10/2026 a las 10:00.
 */
@DataJpaTest(properties = { "spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=create-drop", 
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect" })
class ResumenRepositoryTest {

    private static final LocalDate HOY = TestData.HOY; // 13/10/2026
    private static final LocalDate OCT_1 = LocalDate.of(2026, 10, 1);
    private static final LocalDate OCT_31 = LocalDate.of(2026, 10, 31);

    @Autowired
    private TestEntityManager em;

    @Autowired
    private ResumenRepository repo;

    private Usuario cliente;
    private Cancha cancha;

    @BeforeEach
    void datos() {
        cliente = TestData.usuario(null, Role.CLIENTE);
        em.persist(cliente);
        cancha = em.persist(Cancha.builder().numero(1).descripcion("Cancha 1").build());

        // jugados
        Reserva completada = reserva(LocalDate.of(2026, 10, 10), "18:00", "19:30", EstadoReserva.COMPLETADA, "15000");
        pago(completada, PaymentProvider.MANUAL, null, EstadoPago.APROBADO, "15000", LocalDate.of(2026, 10, 10).atTime(20, 0));
        Reserva conDeuda = reserva(LocalDate.of(2026, 10, 12), "18:00", "19:30", EstadoReserva.SEÑADA, "20000");
        pago(conDeuda, PaymentProvider.MERCADO_PAGO, "mp-1", EstadoPago.APROBADO, "10000", OCT_1.atTime(12, 0));
        // hoy, ya terminado: se pagó en el mostrador con tarjeta
        Reserva hoyTerminada = reserva(HOY, "08:00", "09:30", EstadoReserva.PAGADA, "12000");
        pago(hoyTerminada, PaymentProvider.MERCADO_PAGO, null, EstadoPago.APROBADO, "12000", HOY.atTime(9, 45));

        // por jugar
        // hoy, todavía no terminó: señó hoy online
        Reserva hoyPorJugar = reserva(HOY, "11:00", "12:30", EstadoReserva.SEÑADA, "18000");
        pago(hoyPorJugar, PaymentProvider.MERCADO_PAGO, "mp-2", EstadoPago.APROBADO, "6000", HOY.atTime(9, 0));
        // el ADMIN la bonificó entera: no es plata y no entra en caja
        Reserva bonificada = reserva(LocalDate.of(2026, 10, 15), "18:00", "19:30", EstadoReserva.PAGADA, "10000");
        pago(bonificada, PaymentProvider.BONIFICACION, null, EstadoPago.APROBADO, "10000", HOY.atTime(9, 10));
        // turno del mes que viene, señado hoy: entra en la caja de hoy
        Reserva futura = reserva(LocalDate.of(2026, 11, 20), "18:00", "19:30", EstadoReserva.SEÑADA, "15000");
        pago(futura, PaymentProvider.MERCADO_PAGO, "mp-3", EstadoPago.APROBADO, "7500", HOY.atTime(9, 30));

        // cancelada: pagó 15.000 el 5/10 y se le devolvieron 7.500 el 9/10
        Reserva cancelada = reserva(LocalDate.of(2026, 10, 11), "18:00", "19:30", EstadoReserva.CANCELADA, "15000");
        Pago devuelto = pago(cancelada, PaymentProvider.MERCADO_PAGO, "mp-4", EstadoPago.DEVUELTO, "15000",
                LocalDate.of(2026, 10, 5).atTime(10, 0));
        devuelto.setMontoReembolsado(new BigDecimal("7500"));
        devuelto.setReembolsadoAt(LocalDate.of(2026, 10, 9).atTime(11, 0));

        // no cuentan: expirada con un pago rechazado y pendiente sin pagar
        Reserva expirada = reserva(LocalDate.of(2026, 10, 9), "18:00", "19:30", EstadoReserva.EXPIRADA, "15000");
        pago(expirada, PaymentProvider.MERCADO_PAGO, "mp-5", EstadoPago.RECHAZADO, "7500", null);
        reserva(LocalDate.of(2026, 10, 20), "18:00", "19:30", EstadoReserva.PENDIENTE, "15000");
        em.flush();
    }

    private Reserva reserva(LocalDate fecha, String inicio, String fin, EstadoReserva estado, String total) {
        return em.persist(Reserva.builder()
                .cancha(cancha)
                .cliente(cliente)
                .createdBy(cliente)
                .fechaReserva(fecha)
                .horaInicio(LocalTime.parse(inicio))
                .horaFin(LocalTime.parse(fin))
                .montoTotal(new BigDecimal(total))
                .estado(estado)
                .createdAt(TestData.AHORA.minusDays(20))
                .build());
    }

    private Pago pago(Reserva reserva, PaymentProvider provider, String externalId, EstadoPago estado, String monto,
            LocalDateTime paidAt) {
        return em.persist(Pago.builder()
                .reserva(reserva)
                .provider(provider)
                .externalPaymentId(externalId)
                .estado(estado)
                .monto(new BigDecimal(monto))
                .createdAt(paidAt != null ? paidAt : TestData.AHORA.minusDays(4))
                .paidAt(paidAt)
                .build());
    }

    private ResumenResponse resumen(LocalDate desde, LocalDate hasta, LocalDateTime ahora) {
        Reloj reloj = TestData.reloj(ahora);
        return new ResumenService(repo, reloj).resumen(desde, hasta);
    }

    private static BigDecimal $(String monto) {
        return new BigDecimal(monto);
    }

    @Test
    void unPeriodoQueCruzaHoySeparaJugadosYPorJugar() {
        ResumenResponse r = resumen(OCT_1, OCT_31, TestData.AHORA);

        // completada, la señada del 12/10 y la de hoy a las 08:00
        assertThat(r.jugados()).isEqualTo(new ResumenResponse.Jugados(3, $("47000.00"), $("37000.00"),
                $("0.00"), $("10000.00")));
        // la de hoy a las 11:00 y la bonificada del 15/10 (la de noviembre queda afuera del período)
        assertThat(r.porJugar()).isEqualTo(new ResumenResponse.PorJugar(2, $("28000.00"), $("6000.00"),
                $("10000.00"), $("12000.00")));
        assertThat(r.cancelaciones()).isEqualTo(new ResumenResponse.Cancelaciones(1, $("7500.00")));
        // caja de octubre: efectivo 15.000; online 10.000 + 6.000 + 7.500 (seña de noviembre) + 15.000
        // (el pago de la cancelada, que entró completo); mostrador 12.000; devueltos 7.500 el 9/10
        assertThat(r.caja()).isEqualTo(new ResumenResponse.Caja($("65500.00"), $("15000.00"), $("38500.00"),
                $("12000.00"), $("7500.00"), $("58000.00")));
    }

    @Test
    void unPeriodoSoloPasadoNoTieneTurnosPorJugar() {
        ResumenResponse r = resumen(OCT_1, HOY.minusDays(1), TestData.AHORA);

        assertThat(r.jugados().turnos()).isEqualTo(2);
        assertThat(r.jugados().montoTotal()).isEqualTo($("35000.00"));
        assertThat(r.jugados().deuda()).isEqualTo($("10000.00"));
        assertThat(r.porJugar()).isEqualTo(new ResumenResponse.PorJugar(0, $("0.00"), $("0.00"), $("0.00"),
                $("0.00")));
        // las señas de hoy no entran en la caja de días anteriores
        assertThat(r.caja().total()).isEqualTo($("40000.00"));
        assertThat(r.caja().devoluciones()).isEqualTo($("7500.00"));
    }

    @Test
    void unPeriodoSoloFuturoNoTieneTurnosJugadosNiCaja() {
        ResumenResponse r = resumen(HOY.plusDays(1), LocalDate.of(2026, 11, 30), TestData.AHORA);

        assertThat(r.jugados()).isEqualTo(new ResumenResponse.Jugados(0, $("0.00"), $("0.00"), $("0.00"),
                $("0.00")));
        // la bonificada del 15/10 y la de noviembre, señada hoy
        assertThat(r.porJugar()).isEqualTo(new ResumenResponse.PorJugar(2, $("25000.00"), $("7500.00"),
                $("10000.00"), $("7500.00")));
        assertThat(r.caja().total()).isEqualTo($("0.00"));
        assertThat(r.cancelaciones().turnos()).isZero();
    }

    @Test
    void hoySeparaLosTurnosTerminadosYLaCajaEsLoCobradoHoy() {
        ResumenResponse r = resumen(HOY, HOY, TestData.AHORA);

        assertThat(r.jugados().turnos()).isEqualTo(1);           // 08:00 a 09:30
        assertThat(r.jugados().montoCobrado()).isEqualTo($("12000.00"));
        assertThat(r.porJugar().turnos()).isEqualTo(1);          // 11:00 a 12:30
        assertThat(r.porJugar().aCobrar()).isEqualTo($("12000.00"));
        // seña de hoy (6.000) + seña de hoy para noviembre (7.500) + mostrador (12.000); la bonificación no
        assertThat(r.caja()).isEqualTo(new ResumenResponse.Caja($("25500.00"), $("0.00"), $("13500.00"),
                $("12000.00"), $("0.00"), $("25500.00")));
    }

    @Test
    void unTurnoPasaAJugadoCuandoLlegaSuHoraDeFin() {
        assertThat(resumen(HOY, HOY, HOY.atTime(12, 29)).jugados().turnos()).isEqualTo(1);
        ResumenResponse r = resumen(HOY, HOY, HOY.atTime(12, 30));

        assertThat(r.jugados().turnos()).isEqualTo(2);
        assertThat(r.porJugar().turnos()).isZero();
        assertThat(r.jugados().deuda()).isEqualTo($("12000.00"));
    }

    @Test
    void laSeniaDeUnTurnoFuturoEntraEnLaCajaDeHoyYEnPorJugarDelPeriodoDelTurno() {
        assertThat(resumen(HOY, HOY, TestData.AHORA).caja().mercadoPagoOnline()).isEqualTo($("13500.00"));

        ResumenResponse noviembre = resumen(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30), TestData.AHORA);
        assertThat(noviembre.porJugar().cobradoAnticipado()).isEqualTo($("7500.00"));
        assertThat(noviembre.caja().total()).isEqualTo($("0.00"));
    }

    @Test
    void lasDevolucionesCuentanElDiaDelReembolso() {
        ResumenResponse delReembolso = resumen(LocalDate.of(2026, 10, 9), LocalDate.of(2026, 10, 9), TestData.AHORA);
        assertThat(delReembolso.caja().total()).isEqualTo($("0.00"));
        assertThat(delReembolso.caja().devoluciones()).isEqualTo($("7500.00"));
        assertThat(delReembolso.caja().neto()).isEqualTo($("-7500.00"));

        ResumenResponse delCobro = resumen(LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 5), TestData.AHORA);
        assertThat(delCobro.caja().mercadoPagoOnline()).isEqualTo($("15000.00"));
        assertThat(delCobro.caja().devoluciones()).isEqualTo($("0.00"));
    }

    @Test
    void unPeriodoSinNadaDaTodoEnCero() {
        ResumenResponse r = resumen(LocalDate.of(2027, 1, 1), LocalDate.of(2027, 1, 31), TestData.AHORA);

        assertThat(r.jugados().turnos()).isZero();
        assertThat(r.porJugar().montoTotal()).isEqualTo($("0.00"));
        assertThat(r.caja().neto()).isEqualTo($("0.00"));
        assertThat(r.cancelaciones()).isEqualTo(new ResumenResponse.Cancelaciones(0, $("0.00")));
        assertThat(r.productos()).isEmpty();
    }
}
