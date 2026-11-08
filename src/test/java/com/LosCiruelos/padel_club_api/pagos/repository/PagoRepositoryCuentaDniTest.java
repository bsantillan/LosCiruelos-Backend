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
import com.LosCiruelos.padel_club_api.club.entity.Producto;
import com.LosCiruelos.padel_club_api.club.entity.enums.TipoProducto;
import com.LosCiruelos.padel_club_api.mapper.CuentaDniMapper;
import com.LosCiruelos.padel_club_api.mapper.VentaMapper;
import com.LosCiruelos.padel_club_api.pagos.dto.response.CobroCuentaDniResponse;
import com.LosCiruelos.padel_club_api.pagos.dto.response.CobroCuentaDniResponse.Origen;
import com.LosCiruelos.padel_club_api.pagos.service.PagoConsultaService;
import com.LosCiruelos.padel_club_api.ventas.entity.Venta;
import com.LosCiruelos.padel_club_api.ventas.entity.enums.MedioDePago;
import com.LosCiruelos.padel_club_api.ventas.repository.VentaRepository;
import com.LosCiruelos.padel_club_api.ventas.service.VentaConsultaService;

/** El listado de lo cobrado con Cuenta DNI (reservas y ventas) que el ADMIN controla contra la cuenta (H2). */
@DataJpaTest(properties = { "spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect" })
class PagoRepositoryCuentaDniTest {

    private static final LocalDate HOY = TestData.HOY;

    @Autowired
    private TestEntityManager em;

    @Autowired
    private PagoRepository repo;

    @Autowired
    private VentaRepository ventaRepo;

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
    void elServicioJuntaLosPagosYLasVentasPorFecha() {
        Producto agua = em.persist(Producto.builder().nombre("Agua").tipo(TipoProducto.VENTA)
                .precio(new BigDecimal("1500")).stock(10).build());
        Venta venta = Venta.builder().createdAt(HOY.atTime(8, 0)).medio(MedioDePago.CUENTA_DNI).registradoPor(admin)
                .build();
        venta.agregar(agua, 2);
        em.persist(venta);
        Venta enEfectivo = Venta.builder().createdAt(HOY.atTime(8, 30)).medio(MedioDePago.EFECTIVO)
                .registradoPor(admin).build();
        enEfectivo.agregar(agua, 1);
        em.persist(enEfectivo);
        em.flush();
        PagoConsultaService servicio = new PagoConsultaService(repo,
                new VentaConsultaService(ventaRepo, null, new VentaMapper(), TestData.reloj(TestData.AHORA), null),
                new CuentaDniMapper());

        var cobros = servicio.cuentaDni(HOY.minusDays(2), HOY, false);

        // la venta de hoy a las 8:00 y el pago de hoy a las 9:00 (el verificado de ayer no)
        assertThat(cobros).extracting(CobroCuentaDniResponse::origen, CobroCuentaDniResponse::id)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(Origen.VENTA, venta.getId()),
                        org.assertj.core.groups.Tuple.tuple(Origen.RESERVA, sinVerificar.getId()));
        assertThat(cobros.get(0).monto()).isEqualByComparingTo("3000");
        assertThat(cobros).extracting(CobroCuentaDniResponse::anulada).containsExactly(false, false);
        assertThat(servicio.cuentaDni(HOY.minusDays(2), HOY, null)).hasSize(3);

        // una venta anulada sigue en el listado, marcada, para conciliar la cuenta
        venta.anular(admin, "El cliente no pagó", HOY.atTime(8, 5));
        em.flush();
        assertThat(servicio.cuentaDni(HOY.minusDays(2), HOY, false).get(0).anulada()).isTrue();
    }

    @Test
    void filtraPorVerificado() {
        assertThat(cuentaDni(false)).containsExactly(sinVerificar);
        assertThat(cuentaDni(true)).containsExactly(verificado);
    }
}
