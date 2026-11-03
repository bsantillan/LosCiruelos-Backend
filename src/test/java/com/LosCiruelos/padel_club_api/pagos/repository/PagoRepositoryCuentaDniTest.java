package com.LosCiruelos.padel_club_api.pagos.repository;

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
import com.LosCiruelos.padel_club_api.club.entity.Cancha;
import com.LosCiruelos.padel_club_api.pagos.entity.Pago;
import com.LosCiruelos.padel_club_api.pagos.entity.enums.EstadoPago;
import com.LosCiruelos.padel_club_api.pagos.payment.PaymentProvider;
import com.LosCiruelos.padel_club_api.reservas.entity.Reserva;
import com.LosCiruelos.padel_club_api.reservas.entity.enums.EstadoReserva;
import com.LosCiruelos.padel_club_api.usuarios.entity.Usuario;
import com.LosCiruelos.padel_club_api.usuarios.entity.enums.Role;

/** El listado de pagos con Cuenta DNI que el ADMIN controla contra la cuenta (H2). */
@DataJpaTest(properties = { "spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect" })
class PagoRepositoryCuentaDniTest {

    private static final LocalDate HOY = TestData.HOY;

    @Autowired
    private TestEntityManager em;

    @Autowired
    private PagoRepository repo;

    private Usuario admin;
    private Pago verificado;
    private Pago sinVerificar;

    @BeforeEach
    void datos() {
        Usuario cliente = em.persist(TestData.usuario(null, Role.CLIENTE));
        admin = TestData.usuario(null, Role.ADMIN);
        admin.setEmail("admin@test.com");
        em.persist(admin);
        Cancha cancha = em.persist(Cancha.builder().numero(1).descripcion("Cancha 1").build());
        Reserva reserva = em.persist(Reserva.builder().cancha(cancha).cliente(cliente).createdBy(cliente)
                .fechaReserva(HOY).horaInicio(LocalTime.of(18, 0)).horaFin(LocalTime.of(19, 30))
                .montoTotal(new BigDecimal("15000")).estado(EstadoReserva.PAGADA)
                .createdAt(TestData.AHORA.minusDays(3)).build());

        verificado = pago(reserva, PaymentProvider.CUENTA_DNI, HOY.minusDays(1).atTime(18, 0));
        verificado.verificar(true, admin.getId(), TestData.AHORA);
        sinVerificar = pago(reserva, PaymentProvider.CUENTA_DNI, HOY.atTime(9, 0));
        pago(reserva, PaymentProvider.MANUAL, HOY.atTime(9, 30));                    // otro medio
        pago(reserva, PaymentProvider.CUENTA_DNI, HOY.minusDays(10).atTime(9, 0));  // fuera del período
        em.flush();
    }

    private Pago pago(Reserva reserva, PaymentProvider provider, LocalDateTime paidAt) {
        return em.persist(Pago.builder().reserva(reserva).provider(provider).estado(EstadoPago.APROBADO)
                .monto(new BigDecimal("5000")).createdAt(paidAt).paidAt(paidAt).build());
    }

    private java.util.List<Pago> cuentaDni(Boolean verificadoFiltro) {
        return repo.cuentaDni(PaymentProvider.CUENTA_DNI, HOY.minusDays(2).atStartOfDay(),
                HOY.plusDays(1).atStartOfDay(), verificadoFiltro);
    }

    @Test
    void traeLosDeCuentaDniDelPeriodoDelMasViejoAlMasNuevo() {
        assertThat(cuentaDni(null)).containsExactly(verificado, sinVerificar);
    }

    @Test
    void filtraPorVerificado() {
        assertThat(cuentaDni(false)).containsExactly(sinVerificar);
        assertThat(cuentaDni(true)).containsExactly(verificado);
    }
}
