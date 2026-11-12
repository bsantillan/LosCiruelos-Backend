package com.LosCiruelos.padel_club_api.ventas.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import com.LosCiruelos.padel_club_api.TestData;
import com.LosCiruelos.padel_club_api.club.entity.Producto;
import com.LosCiruelos.padel_club_api.club.entity.enums.TipoProducto;
import com.LosCiruelos.padel_club_api.usuarios.entity.Usuario;
import com.LosCiruelos.padel_club_api.usuarios.entity.enums.Role;
import com.LosCiruelos.padel_club_api.ventas.entity.Venta;
import com.LosCiruelos.padel_club_api.ventas.entity.VentaDevolucion;
import com.LosCiruelos.padel_club_api.ventas.entity.enums.EstadoVenta;
import com.LosCiruelos.padel_club_api.ventas.entity.enums.MedioDePago;

/** Los filtros y los totales del listado de ventas del personal (H2). */
@DataJpaTest(properties = { "spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect" })
class VentaListadoRepositoryTest {

    private static final LocalDate HOY = TestData.HOY;

    @Autowired
    private TestEntityManager em;

    @Autowired
    private VentaRepository repo;

    private Usuario empleado;
    private Usuario jose;
    private Producto agua;
    private Producto pelotas;
    private Venta efectivoDeJose;
    private Venta dni;
    private Venta anulada;
    private Venta ayer;

    @BeforeEach
    void datos() {
        empleado = em.persist(TestData.usuario(null, Role.EMPLEADO));
        jose = TestData.usuario(null, Role.CLIENTE);
        jose.setEmail("jose@test.com");
        jose.setNombre("José");
        jose.setApellido("Pérez");
        jose = em.persist(jose);
        agua = em.persist(Producto.builder().nombre("Agua").tipo(TipoProducto.VENTA).precio(new BigDecimal("1500"))
                .stock(50).activo(true).ofrecerAlReservar(false).controlaStock(true).build());
        pelotas = em.persist(Producto.builder().nombre("Pelotas").tipo(TipoProducto.VENTA).precio(new BigDecimal("5000"))
                .stock(50).activo(true).ofrecerAlReservar(true).controlaStock(true).build());

        efectivoDeJose = venta(HOY.atTime(9, 0), MedioDePago.EFECTIVO, jose, agua, 3); // 4.500
        dni = venta(HOY.atTime(9, 30), MedioDePago.CUENTA_DNI, null, pelotas, 2); // 10.000
        anulada = venta(HOY.atTime(9, 45), MedioDePago.EFECTIVO, null, agua, 1);
        anulada.anular(empleado, "Error de carga", HOY.atTime(9, 50));
        ayer = venta(HOY.minusDays(1).atTime(20, 0), MedioDePago.EFECTIVO, jose, pelotas, 1); // 5.000
        em.flush();
        // devuelve un agua de la venta de José: $1.500
        efectivoDeJose.devolver(List.of(new VentaDevolucion.Pedido(agua.getId(), 1, true)), "Vencida", empleado,
                HOY.atTime(9, 55));
        em.flush();
        em.clear();
    }

    private Venta venta(LocalDateTime cuando, MedioDePago medio, Usuario cliente, Producto producto, int cantidad) {
        Venta venta = Venta.builder().createdAt(cuando).medio(medio).cliente(cliente).registradoPor(empleado).build();
        venta.agregar(producto, cantidad);
        return em.persist(venta);
    }

    private List<Long> ids(FiltroVentas filtro) {
        return repo.findAll(VentaSpecification.delListado(filtro),
                PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt"))).map(Venta::getId).getContent();
    }

    private static FiltroVentas deHoy() {
        return new FiltroVentas(HOY.atStartOfDay(), HOY.plusDays(1).atStartOfDay(), null, null, null, null, null);
    }

    @Test
    void filtraPorFechaProductoClienteMedioYEstado() {
        assertThat(ids(deHoy())).containsExactly(anulada.getId(), dni.getId(), efectivoDeJose.getId());
        assertThat(ids(new FiltroVentas(null, null, null, null, null, null, null))).hasSize(4);
        assertThat(ids(new FiltroVentas(HOY.minusDays(1).atStartOfDay(), HOY.plusDays(1).atStartOfDay(), pelotas.getId(), null, null, null, null)))
                .containsExactly(dni.getId(), ayer.getId());
        // cliente: contiene, sin tildes
        assertThat(ids(new FiltroVentas(null, null, null, "jose per", null, null, null)))
                .containsExactly(efectivoDeJose.getId(), ayer.getId());
        assertThat(ids(new FiltroVentas(null, null, null, null, jose.getId(), null, null))).hasSize(2);
        assertThat(ids(new FiltroVentas(HOY.atStartOfDay(), HOY.plusDays(1).atStartOfDay(), null, null, null, Set.of(MedioDePago.CUENTA_DNI), null)))
                .containsExactly(dni.getId());
        assertThat(ids(new FiltroVentas(HOY.atStartOfDay(), HOY.plusDays(1).atStartOfDay(), null, null, null, null, Set.of(EstadoVenta.ANULADA))))
                .containsExactly(anulada.getId());
        assertThat(ids(new FiltroVentas(HOY.atStartOfDay(), HOY.plusDays(1).atStartOfDay(), null, null, null, Set.of(), null))).isEmpty();
    }

    @Test
    void losTotalesSonDeTodoElFiltro() {
        TotalesDelListado t = repo.totalesDelListado(deHoy());

        assertThat(t.cobradas()).isEqualTo(2);
        assertThat(t.anuladas()).isEqualTo(1);
        // la anulada no suma; lo devuelto se resta en el neto
        assertThat(t.cobradoPorMedio().get(MedioDePago.EFECTIVO)).isEqualByComparingTo("4500");
        assertThat(t.cobradoPorMedio().get(MedioDePago.CUENTA_DNI)).isEqualByComparingTo("10000");
        assertThat(t.devueltoPorMedio().get(MedioDePago.EFECTIVO)).isEqualByComparingTo("1500");
        assertThat(t.devueltoPorMedio()).doesNotContainKey(MedioDePago.CUENTA_DNI);

        TotalesDelListado deJose = repo.totalesDelListado(new FiltroVentas(null, null, null, "perez", null, null, null));
        assertThat(deJose.cobradas()).isEqualTo(2);
        assertThat(deJose.cobradoPorMedio().get(MedioDePago.EFECTIVO)).isEqualByComparingTo("9500");
    }
}
