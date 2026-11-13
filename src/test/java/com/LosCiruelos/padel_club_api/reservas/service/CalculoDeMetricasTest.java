package com.LosCiruelos.padel_club_api.reservas.service;

import static com.LosCiruelos.padel_club_api.TestData.AHORA;
import static com.LosCiruelos.padel_club_api.TestData.HOY;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.LosCiruelos.padel_club_api.TestData;
import com.LosCiruelos.padel_club_api.club.entity.Cancha;
import com.LosCiruelos.padel_club_api.club.entity.Producto;
import com.LosCiruelos.padel_club_api.club.entity.enums.TipoProducto;
import com.LosCiruelos.padel_club_api.pagos.entity.Pago;
import com.LosCiruelos.padel_club_api.pagos.entity.enums.EstadoPago;
import com.LosCiruelos.padel_club_api.pagos.payment.PaymentProvider;
import com.LosCiruelos.padel_club_api.reservas.dto.response.MetricasResponse;
import com.LosCiruelos.padel_club_api.reservas.entity.Reserva;
import com.LosCiruelos.padel_club_api.reservas.entity.ReservaProducto;
import com.LosCiruelos.padel_club_api.reservas.entity.enums.EstadoReserva;
import com.LosCiruelos.padel_club_api.reservas.service.CalculoDeMetricas.CanchaDeMetricas;
import com.LosCiruelos.padel_club_api.reservas.service.CalculoDeMetricas.Periodo;
import com.LosCiruelos.padel_club_api.usuarios.entity.Usuario;
import com.LosCiruelos.padel_club_api.usuarios.entity.enums.Role;

/**
 * Las cuentas de las métricas, sin Spring. Hoy es el martes 13/10/2026 a las 10:00; el club abre de
 * lunes a viernes de 8 a 23 (15 h) y tiene 2 canchas.
 */
class CalculoDeMetricasTest {

    private final CalculoDeMetricas calculo = new CalculoDeMetricas(TestData.configuracion(), 2, AHORA);
    private final Usuario ana = cliente(1L, "Ana");
    private final Usuario beto = cliente(2L, "Beto");
    private final Periodo lunes12 = new Periodo(HOY.minusDays(1), HOY.minusDays(1));

    private static Usuario cliente(Long id, String nombre) {
        Usuario u = TestData.usuario(id, Role.CLIENTE);
        u.setNombre(nombre);
        u.setApellido("");
        return u;
    }

    private Reserva reserva(Long id, Usuario cliente, LocalDate fecha, String inicio, String fin, EstadoReserva estado,
            String total) {
        Reserva r = TestData.reserva(id, cliente, estado, total);
        r.setCancha(Cancha.builder().id(id % 2 == 0 ? 2L : 1L).numero(id % 2 == 0 ? 2 : 1).build());
        r.setFechaReserva(fecha);
        r.setHoraInicio(LocalTime.parse(inicio));
        r.setHoraFin(LocalTime.parse(fin));
        return r;
    }

    @Test
    void laVariacionEsContraElPeriodoAnteriorYSinAnteriorEsNull() {
        assertThat(CalculoDeMetricas.comparar(new BigDecimal("112"), new BigDecimal("100")).variacion())
                .isEqualByComparingTo("12.0");
        assertThat(CalculoDeMetricas.comparar(5, 0).variacion()).isNull();
        assertThat(lunes12.anterior()).isEqualTo(new Periodo(HOY.minusDays(2), HOY.minusDays(2)));
    }

    @Test
    void laOcupacionEsLoOcupadoSobreLoAbiertoYElMapaMarcaLasHoras() {
        // lunes 12/10: 30 h abiertas (15 h × 2 canchas); se juegan 3 h
        List<Reserva> confirmadas = List.of(
                reserva(1L, ana, HOY.minusDays(1), "18:00", "19:30", EstadoReserva.COMPLETADA, "15000"),
                reserva(2L, beto, HOY.minusDays(1), "09:00", "10:30", EstadoReserva.COMPLETADA, "12000"));

        MetricasResponse.Ocupacion o = calculo.ocupacion(lunes12, confirmadas, List.of(), List.of(),
                List.of(new CanchaDeMetricas(1L, 1), new CanchaDeMetricas(2L, 2)));

        assertThat(o.porcentaje().actual()).isEqualByComparingTo("10.0");
        assertThat(o.horasOcupadas().actual()).isEqualByComparingTo("3.0");
        assertThat(o.horasAbiertas().actual()).isEqualByComparingTo("30.0");
        assertThat(o.porCancha()).extracting(MetricasResponse.OcupacionCancha::porcentaje)
                .containsExactly(new BigDecimal("10.0"), new BigDecimal("10.0"));
        // a las 18 del lunes: 60 min de 120 abiertos (2 canchas) = 50 %; a las 19, 30 de 120 = 25 %
        MetricasResponse.DiaDelMapa lunes = o.mapa().stream().filter(d -> d.dia() == DayOfWeek.MONDAY).findFirst()
                .orElseThrow();
        assertThat(lunes.franjas()).filteredOn(f -> f.hora().equals("18:00")).singleElement()
                .satisfies(f -> assertThat(f.porcentaje()).isEqualByComparingTo("50.0"));
        assertThat(lunes.franjas()).filteredOn(f -> f.hora().equals("19:00")).singleElement()
                .satisfies(f -> assertThat(f.porcentaje()).isEqualByComparingTo("25.0"));
        // el martes no está en el período: abre pero no hay datos de ese día
        assertThat(o.mapa().stream().filter(d -> d.dia() == DayOfWeek.TUESDAY).findFirst().orElseThrow().franjas())
                .allMatch(f -> f.porcentaje() == null);
        // la noche (18 a 23): 1,5 h ocupadas de 10 abiertas; la cancha de $15.000 son $10.000 por hora
        assertThat(o.porFranja()).filteredOn(f -> f.franja().equals("NOCHE")).singleElement().satisfies(f -> {
            assertThat(f.porcentaje()).isEqualByComparingTo("15.0");
            assertThat(f.ingresoPorHora()).isEqualByComparingTo("10000");
        });
        assertThat(o.proximos7Dias()).hasSize(7);
    }

    @Test
    void losIngresosSeparanCanchaProductosYOnlineDeLoCobradoEnElClub() {
        Reserva r = reserva(1L, ana, HOY.minusDays(1), "18:00", "19:30", EstadoReserva.COMPLETADA, "18000");
        r.getProductos().add(ReservaProducto.builder().reserva(r).cantidad(1).precioUnitario(new BigDecimal("3000"))
                .producto(Producto.builder().id(5L).nombre("Pelotas").tipo(TipoProducto.VENTA).build()).build());
        Pago online = TestData.pago(1L, r, EstadoPago.APROBADO, "9000");
        online.setExternalPaymentId("mp-1");
        Pago efectivo = TestData.pago(2L, r, EstadoPago.APROBADO, "9000");
        efectivo.setProvider(PaymentProvider.MANUAL);

        MetricasResponse.Ingresos i = calculo.ingresos(List.of(r), List.of(), new BigDecimal("2000"), BigDecimal.ZERO);

        assertThat(i.cancha().actual()).isEqualByComparingTo("15000");
        assertThat(i.productosDeReservas().actual()).isEqualByComparingTo("3000");
        assertThat(i.ventasSueltas().actual()).isEqualByComparingTo("2000");
        assertThat(i.total().actual()).isEqualByComparingTo("20000");
        assertThat(i.online().actual()).isEqualByComparingTo("9000");
        assertThat(i.enElClub().actual()).isEqualByComparingTo("9000");
        assertThat(i.ticketPromedio().actual()).isEqualByComparingTo("18000");
        assertThat(i.porHoraOcupada().actual()).isEqualByComparingTo("10000");
    }

    @Test
    void losClientesNuevosRecurrentesYLosQueDejaronDeVenir() {
        List<Reserva> confirmadas = List.of(
                reserva(1L, ana, HOY.minusDays(1), "18:00", "19:30", EstadoReserva.COMPLETADA, "15000"),
                reserva(3L, ana, HOY.minusDays(1), "20:00", "21:30", EstadoReserva.COMPLETADA, "15000"),
                reserva(2L, beto, HOY.minusDays(1), "09:00", "10:30", EstadoReserva.COMPLETADA, "15000"));
        // Ana es nueva (su primera reserva es del período); Beto ya venía
        Map<Long, LocalDate> primeras = Map.of(1L, HOY.minusDays(1), 2L, HOY.minusDays(60));
        Usuario carla = cliente(3L, "Carla");
        List<Reserva> ultimos120 = List.of(
                reserva(11L, carla, HOY.minusDays(40), "18:00", "19:30", EstadoReserva.COMPLETADA, "15000"),
                reserva(12L, carla, HOY.minusDays(50), "18:00", "19:30", EstadoReserva.COMPLETADA, "15000"),
                reserva(13L, carla, HOY.minusDays(60), "18:00", "19:30", EstadoReserva.COMPLETADA, "15000"),
                reserva(14L, beto, HOY.minusDays(45), "18:00", "19:30", EstadoReserva.COMPLETADA, "15000"));

        MetricasResponse.Clientes c = calculo.clientes(lunes12, confirmadas, List.of(), primeras, ultimos120);

        assertThat(c.activos().actual()).isEqualByComparingTo("2");
        assertThat(c.nuevos().actual()).isEqualByComparingTo("1");
        assertThat(c.recurrentes().actual()).isEqualByComparingTo("1");
        assertThat(c.frecuencia().actual()).isEqualByComparingTo("1.50");
        assertThat(c.topPorReservas().getFirst().nombre()).isEqualTo("Ana");
        assertThat(c.dejaronDeVenir()).singleElement().satisfies(d -> {
            assertThat(d.nombre()).isEqualTo("Carla");
            assertThat(d.reservasAntes()).isEqualTo(3);
            assertThat(d.ultimaReserva()).isEqualTo(HOY.minusDays(40));
        });
    }

    @Test
    void laAnticipacionDeLasReservasYQuienLasCrea() {
        Reserva mismoDia = reserva(1L, ana, HOY, "18:00", "19:30", EstadoReserva.PAGADA, "15000");
        mismoDia.setCreatedAt(HOY.atTime(9, 0));
        Reserva conUnaSemana = reserva(2L, beto, HOY.plusDays(10), "18:00", "19:30", EstadoReserva.SEÑADA, "15000");
        conUnaSemana.setCreatedAt(HOY.atTime(9, 0));
        conUnaSemana.setCreatedBy(TestData.usuario(9L, Role.EMPLEADO));

        MetricasResponse.Reservas r = calculo.reservas(List.of(mismoDia, conUnaSemana), List.of(),
                List.of(mismoDia, conUnaSemana));

        assertThat(r.creadas().actual()).isEqualByComparingTo("2");
        assertThat(r.anticipacion()).isEqualTo(new MetricasResponse.Anticipacion(1, 0, 0, 1));
        assertThat(r.porOrigen()).isEqualTo(new MetricasResponse.PorOrigen(1, 1));
        assertThat(r.duraciones()).containsExactly(new MetricasResponse.Duracion(90, 2));
        assertThat(r.porTipo()).isEqualTo(new MetricasResponse.PorTipo(2, 0, 0));
    }

    @Test
    void lasCancelacionesPorQuienYConCuantaAnticipacion() {
        Reserva porCliente = reserva(1L, ana, HOY.minusDays(1), "18:00", "19:30", EstadoReserva.CANCELADA, "15000");
        porCliente.setCanceladaAt(HOY.minusDays(1).atTime(17, 0)); // 1 h antes
        porCliente.setCanceladaPor(ana);
        Reserva porElClub = reserva(2L, beto, HOY.minusDays(1), "18:00", "19:30", EstadoReserva.CANCELADA, "15000");
        porElClub.setCanceladaAt(HOY.minusDays(5).atTime(18, 0)); // 4 días antes
        porElClub.setCanceladaPor(TestData.usuario(9L, Role.ADMIN));
        Pago retenido = TestData.pago(1L, porElClub, EstadoPago.DEVUELTO, "15000");
        retenido.setMontoReembolsado(new BigDecimal("7500"));
        Reserva vieja = reserva(3L, ana, HOY.minusDays(1), "20:00", "21:30", EstadoReserva.CANCELADA, "15000");
        Reserva jugada = reserva(4L, beto, HOY.minusDays(1), "09:00", "10:30", EstadoReserva.COMPLETADA, "15000");
        Reserva vencida = reserva(5L, beto, HOY.minusDays(1), "11:00", "12:30", EstadoReserva.EXPIRADA, "15000");

        MetricasResponse.Cancelaciones c = calculo.cancelaciones(List.of(porCliente, porElClub, vieja, jugada, vencida),
                List.of());

        assertThat(c.canceladas().actual()).isEqualByComparingTo("3");
        assertThat(c.tasa().actual()).isEqualByComparingTo("75.0");
        assertThat(c.porCliente()).isEqualTo(1);
        assertThat(c.porClub()).isEqualTo(1);
        assertThat(c.sinDato()).isEqualTo(1);
        assertThat(c.anticipacion()).isEqualTo(new MetricasResponse.AnticipacionCancelacion(1, 0, 0, 1));
        assertThat(c.retenido().actual()).isEqualByComparingTo("7500");
        assertThat(c.vencidasSinPagar().actual()).isEqualByComparingTo("1");
    }

    @Test
    void laDeudaPorAntiguedadYLoQueViene() {
        Reserva reciente = reserva(1L, ana, HOY.minusDays(3), "18:00", "19:30", EstadoReserva.SEÑADA, "15000");
        TestData.pago(1L, reciente, EstadoPago.APROBADO, "7500");
        Reserva vieja = reserva(2L, ana, HOY.minusDays(40), "18:00", "19:30", EstadoReserva.SEÑADA, "15000");
        TestData.pago(2L, vieja, EstadoPago.APROBADO, "7500");

        MetricasResponse.Deuda d = calculo.deuda(List.of(reciente, vieja));
        assertThat(d.total()).isEqualByComparingTo("15000");
        assertThat(d.hasta7Dias()).isEqualByComparingTo("7500");
        assertThat(d.masDe30Dias()).isEqualByComparingTo("7500");
        assertThat(d.deudores()).singleElement().satisfies(x -> {
            assertThat(x.reservas()).isEqualTo(2);
            assertThat(x.masVieja()).isEqualTo(HOY.minusDays(40));
        });

        Reserva semana = reserva(3L, beto, HOY.plusDays(5), "18:00", "19:30", EstadoReserva.SEÑADA, "15000");
        TestData.pago(3L, semana, EstadoPago.APROBADO, "7500");
        Reserva lejos = reserva(4L, beto, HOY.plusDays(20), "18:00", "19:30", EstadoReserva.PAGADA, "15000");
        TestData.pago(4L, lejos, EstadoPago.APROBADO, "15000");
        MetricasResponse.Futuro f = calculo.futuro(List.of(semana, lejos));
        assertThat(f.asegurado()).isEqualByComparingTo("22500");
        assertThat(f.porCobrar14Dias()).isEqualByComparingTo("7500");
        assertThat(f.turnos14Dias()).isEqualTo(1);
    }

    @Test
    void losProductosMasVendidosLasPaletasQueNoVolvieronYElStockBajo() {
        Reserva r = reserva(1L, ana, HOY.minusDays(1), "18:00", "19:30", EstadoReserva.COMPLETADA, "19000");
        Producto paletas = Producto.builder().id(2L).nombre("Paletas").tipo(TipoProducto.ALQUILER).build();
        r.getProductos().add(ReservaProducto.builder().reserva(r).producto(paletas).cantidad(4)
                .precioUnitario(new BigDecimal("1000")).cantidadDevuelta(3).build());
        Producto agua = Producto.builder().id(3L).nombre("Agua").tipo(TipoProducto.VENTA).stock(2).activo(true).build();
        Producto pelotas = Producto.builder().id(1L).nombre("Pelotas").tipo(TipoProducto.VENTA).stock(40).activo(true)
                .build();

        MetricasResponse.Productos p = calculo.productos(
                List.of(new com.LosCiruelos.padel_club_api.reservas.repository.ProductoVendido(1L, "Pelotas",
                        TipoProducto.VENTA, 2L, new BigDecimal("16000")),
                        new com.LosCiruelos.padel_club_api.reservas.repository.ProductoVendido(2L, "Paletas",
                                TipoProducto.ALQUILER, 4L, new BigDecimal("4000"))),
                List.of(new com.LosCiruelos.padel_club_api.ventas.repository.VentaPorProducto(3L, "Agua",
                        TipoProducto.VENTA, 5L, new BigDecimal("7500")),
                        new com.LosCiruelos.padel_club_api.ventas.repository.VentaPorProducto(1L, "Pelotas",
                                TipoProducto.VENTA, 1L, new BigDecimal("8000"))),
                List.of(r), List.of(agua, pelotas));

        assertThat(p.masVendidos()).extracting(MetricasResponse.ProductoMovido::nombre,
                MetricasResponse.ProductoMovido::cantidad)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("Agua", 5L),
                        org.assertj.core.groups.Tuple.tuple("Pelotas", 3L));
        assertThat(p.alquilados()).singleElement().satisfies(x -> assertThat(x.cantidad()).isEqualTo(4));
        assertThat(p.noDevueltos()).isEqualTo(new MetricasResponse.NoDevueltos(1, new BigDecimal("1000.00"), 1, 0));
        assertThat(p.stockBajo()).singleElement().satisfies(x -> assertThat(x.nombre()).isEqualTo("Agua"));
    }
}
