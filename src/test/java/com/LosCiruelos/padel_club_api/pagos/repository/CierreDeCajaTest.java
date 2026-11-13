package com.LosCiruelos.padel_club_api.pagos.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
import com.LosCiruelos.padel_club_api.club.entity.Producto;
import com.LosCiruelos.padel_club_api.club.entity.enums.TipoProducto;
import com.LosCiruelos.padel_club_api.common.exception.PermisoDenegadoException;
import com.LosCiruelos.padel_club_api.mapper.VentaMapper;
import com.LosCiruelos.padel_club_api.pagos.dto.response.CajaResponse;
import com.LosCiruelos.padel_club_api.pagos.entity.Devolucion;
import com.LosCiruelos.padel_club_api.pagos.entity.Pago;
import com.LosCiruelos.padel_club_api.pagos.entity.enums.EstadoDevolucion;
import com.LosCiruelos.padel_club_api.pagos.entity.enums.EstadoPago;
import com.LosCiruelos.padel_club_api.pagos.entity.enums.TipoMovimientoCaja;
import com.LosCiruelos.padel_club_api.pagos.payment.PaymentProvider;
import com.LosCiruelos.padel_club_api.pagos.service.CajaService;
import com.LosCiruelos.padel_club_api.reservas.entity.Reserva;
import com.LosCiruelos.padel_club_api.reservas.entity.enums.EstadoReserva;
import com.LosCiruelos.padel_club_api.usuarios.entity.Usuario;
import com.LosCiruelos.padel_club_api.usuarios.entity.enums.Role;
import com.LosCiruelos.padel_club_api.usuarios.service.UsuarioService;
import com.LosCiruelos.padel_club_api.ventas.entity.Venta;
import com.LosCiruelos.padel_club_api.ventas.entity.enums.MedioDePago;
import com.LosCiruelos.padel_club_api.ventas.repository.VentaRepository;
import com.LosCiruelos.padel_club_api.ventas.service.VentaConsultaService;

/**
 * El cierre de caja de una jornada contra la base (H2). Hoy es el martes 13/10, que abre a las 8:00:
 * la jornada va de las 8:00 del martes a las 8:00 del miércoles.
 */
@DataJpaTest(properties = { "spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect" })
class CierreDeCajaTest {

    private static final LocalDate HOY = TestData.HOY;

    @Autowired
    private TestEntityManager em;

    @Autowired
    private PagoRepository pagos;

    @Autowired
    private VentaRepository ventas;

    private Usuario admin;
    private Usuario empleado;
    private UsuarioService usuarioService;
    private Reserva reserva;

    @BeforeEach
    void laJornada() {
        Usuario cliente = TestData.usuario(null, Role.CLIENTE);
        cliente.setNombre("Ana");
        cliente.setApellido("Pérez");
        cliente = em.persist(cliente);
        admin = TestData.usuario(null, Role.ADMIN);
        admin.setEmail("admin@test.com");
        admin = em.persist(admin);
        empleado = TestData.usuario(null, Role.EMPLEADO);
        empleado.setEmail("empleado@test.com");
        empleado.setNombre("Carla");
        empleado.setApellido("Méndez");
        empleado = em.persist(empleado);
        Cancha cancha = em.persist(Cancha.builder().numero(1).descripcion("Cancha 1").build());
        reserva = em.persist(Reserva.builder().cancha(cancha).cliente(cliente).createdBy(cliente).fechaReserva(HOY)
                .horaInicio(LocalTime.of(18, 0)).horaFin(LocalTime.of(19, 30)).montoTotal(new BigDecimal("30000"))
                .estado(EstadoReserva.PAGADA).createdAt(TestData.AHORA.minusDays(3)).build());

        Pago efectivo = pago(PaymentProvider.MANUAL, "5000", HOY.atTime(9, 0));
        pago(PaymentProvider.TRANSFERENCIA_MP, "3000", HOY.atTime(9, 30));
        Pago anulado = pago(PaymentProvider.MANUAL, "2000", HOY.atTime(9, 10));
        anulado.anular(empleado, "Lo cargué dos veces", HOY.atTime(9, 20));
        pago(PaymentProvider.BONIFICACION, "1000", HOY.atTime(9, 40));
        pago(PaymentProvider.CUENTA_DNI, "4000", HOY.plusDays(1).atTime(0, 10)); // 00:10: sigue la jornada del martes
        pago(PaymentProvider.MANUAL, "7000", HOY.atTime(7, 50));                // 7:50: de la jornada del lunes

        Devolucion entregada = Devolucion.builder().pago(efectivo).monto(new BigDecimal("1500"))
                .estado(EstadoDevolucion.A_REEMBOLSAR).createdAt(HOY.atTime(9, 45)).registradaPor(admin).build();
        entregada.marcarDevuelta(empleado, HOY.atTime(9, 45));
        em.persist(entregada);
        em.persist(Devolucion.builder().pago(efectivo).monto(new BigDecimal("500")).estado(EstadoDevolucion.A_REEMBOLSAR)
                .createdAt(HOY.atTime(9, 50)).registradaPor(admin).build());

        Producto agua = em.persist(Producto.builder().nombre("Agua").tipo(TipoProducto.VENTA)
                .precio(new BigDecimal("600")).stock(10).build());
        venta(agua, MedioDePago.EFECTIVO, HOY.atTime(9, 55), false);
        venta(agua, MedioDePago.MERCADO_PAGO, HOY.atTime(10, 5), true);
        em.flush();

        usuarioService = mock(UsuarioService.class);
        when(usuarioService.autenticado(admin.getEmail())).thenReturn(admin);
        when(usuarioService.autenticado(empleado.getEmail())).thenReturn(empleado);
        when(usuarioService.findByIdOrThrow(empleado.getId())).thenReturn(empleado);
    }

    private Pago pago(PaymentProvider provider, String monto, LocalDateTime cuando) {
        return em.persist(Pago.builder().reserva(reserva).provider(provider).estado(EstadoPago.APROBADO)
                .monto(new BigDecimal(monto)).createdAt(cuando).paidAt(cuando).registradoPorId(empleado.getId())
                .build());
    }

    private void venta(Producto producto, MedioDePago medio, LocalDateTime cuando, boolean anulada) {
        Venta venta = Venta.builder().createdAt(cuando).medio(medio).registradoPor(empleado).build();
        venta.agregar(producto, 2);
        if (anulada) {
            venta.anular(empleado, "Error de carga", cuando.plusMinutes(5));
        }
        em.persist(venta);
    }

    private CajaService caja(LocalDateTime ahora) {
        var reloj = TestData.reloj(ahora);
        VentaConsultaService ventaConsultas = new VentaConsultaService(ventas, usuarioService, new VentaMapper(), reloj,
                null, TestData.jornadas(reloj));
        return new CajaService(pagos, ventaConsultas, usuarioService, TestData.jornadas(reloj));
    }

    @Test
    void elCierreDeLaJornadaSumaPorMedioSinLoAnuladoYConLoDeDespuesDeMedianoche() {
        CajaResponse r = caja(TestData.AHORA).cierre(null, empleado.getEmail());

        assertThat(r.fecha()).isEqualTo(HOY);
        assertThat(r.inicio()).isEqualTo(HOY.atTime(8, 0));
        assertThat(r.fin()).isEqualTo(HOY.plusDays(1).atTime(8, 0));
        // efectivo 5.000 + venta 1.200 (el de 2.000 anulado no cuenta; el de las 7:50 es de ayer)
        assertThat(r.cobrado()).isEqualTo(new CajaResponse.PorMedio(new BigDecimal("6200.00"),
                new BigDecimal("3000.00"), new BigDecimal("4000.00"), new BigDecimal("13200.00")));
        assertThat(r.devuelto()).isEqualTo(new CajaResponse.PorMedio(new BigDecimal("1500.00"),
                new BigDecimal("0.00"), new BigDecimal("0.00"), new BigDecimal("1500.00")));
        assertThat(r.efectivoEnCaja()).isEqualTo(new BigDecimal("4700.00"));
        assertThat(r.bonificado()).isEqualTo(new BigDecimal("1000.00"));
        assertThat(r.aEntregar()).isEqualTo(new CajaResponse.AEntregar(1, new BigDecimal("500.00")));

        assertThat(r.movimientos()).extracting(CajaResponse.Movimiento::tipo).containsExactly(
                TipoMovimientoCaja.COBRO, TipoMovimientoCaja.COBRO, TipoMovimientoCaja.ANULACION,
                TipoMovimientoCaja.COBRO, TipoMovimientoCaja.DEVOLUCION, TipoMovimientoCaja.VENTA,
                TipoMovimientoCaja.VENTA, TipoMovimientoCaja.ANULACION, TipoMovimientoCaja.COBRO);
        CajaResponse.Movimiento primero = r.movimientos().getFirst();
        assertThat(primero.detalle()).isEqualTo("Reserva #" + reserva.getId() + " · Ana Pérez");
        assertThat(primero.registradoPor()).isEqualTo("Carla Méndez");
        assertThat(primero.reservaId()).isEqualTo(reserva.getId());
        assertThat(r.movimientos().get(4).monto()).isEqualByComparingTo("-1500");
        // la suma de los movimientos cierra con lo cobrado menos lo devuelto
        assertThat(com.LosCiruelos.padel_club_api.common.Dinero.sumar(r.movimientos().stream()
                .map(CajaResponse.Movimiento::monto))).isEqualByComparingTo("11700");
    }

    @Test
    void alas0030ElEmpleadoSigueViendoLaCajaDeLaJornadaDelMartes() {
        CajaResponse r = caja(HOY.plusDays(1).atTime(0, 30)).cierre(null, empleado.getEmail());

        assertThat(r.fecha()).isEqualTo(HOY);
        assertThat(r.cobrado().cuentaDni()).isEqualTo(new BigDecimal("4000.00"));
    }

    @Test
    void elEmpleadoSoloVeLaJornadaEnCursoYElAdminCualquiera() {
        assertThatThrownBy(() -> caja(TestData.AHORA).cierre(HOY.minusDays(1), empleado.getEmail()))
                .isInstanceOf(PermisoDenegadoException.class)
                .hasMessage("Solo podés ver la caja de la jornada en curso.");

        CajaResponse lunes = caja(TestData.AHORA).cierre(HOY.minusDays(1), admin.getEmail());
        assertThat(lunes.cobrado().efectivo()).isEqualTo(new BigDecimal("7000.00"));
        assertThat(lunes.fin()).isEqualTo(HOY.atTime(8, 0));
    }
}
