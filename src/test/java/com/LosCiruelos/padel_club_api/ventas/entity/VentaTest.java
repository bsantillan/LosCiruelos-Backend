package com.LosCiruelos.padel_club_api.ventas.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.LosCiruelos.padel_club_api.club.entity.Producto;
import com.LosCiruelos.padel_club_api.club.entity.enums.TipoProducto;

class VentaTest {

    private final Producto agua = Producto.builder().id(5L).nombre("Agua").tipo(TipoProducto.VENTA)
            .precio(new BigDecimal("1500")).build();
    private final Producto pelotas = Producto.builder().id(1L).nombre("Pelotas").tipo(TipoProducto.VENTA)
            .precio(new BigDecimal("8000")).build();

    @Test
    void elTotalEsLaSumaDeLosItemsAlPrecioDeHoy() {
        Venta venta = new Venta();
        venta.agregar(agua, 2);
        venta.agregar(pelotas, 1);

        assertThat(venta.getTotal()).isEqualByComparingTo("11000");
        assertThat(venta.getItems()).allSatisfy(i -> assertThat(i.getVenta()).isSameAs(venta));
    }

    @Test
    void unProductoRepetidoSumaUnidadesAlMismoItem() {
        Venta venta = new Venta();
        venta.agregar(agua, 2);
        agua.setPrecio(new BigDecimal("2000"));
        venta.agregar(agua, 1);

        assertThat(venta.getItems()).singleElement().satisfies(i -> {
            assertThat(i.getCantidad()).isEqualTo(3);
            assertThat(i.getPrecioUnitario()).isEqualByComparingTo("1500"); // el del primer ítem
        });
        assertThat(venta.getTotal()).isEqualByComparingTo("4500");
    }
}
