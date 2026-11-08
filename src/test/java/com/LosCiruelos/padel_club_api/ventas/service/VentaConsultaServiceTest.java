package com.LosCiruelos.padel_club_api.ventas.service;

import static com.LosCiruelos.padel_club_api.TestData.AHORA;
import static com.LosCiruelos.padel_club_api.TestData.HOY;
import static com.LosCiruelos.padel_club_api.TestData.usuario;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.LosCiruelos.padel_club_api.TestData;
import com.LosCiruelos.padel_club_api.club.entity.Producto;
import com.LosCiruelos.padel_club_api.club.entity.enums.TipoProducto;
import com.LosCiruelos.padel_club_api.common.exception.PermisoDenegadoException;
import com.LosCiruelos.padel_club_api.common.exception.ReservaException;
import com.LosCiruelos.padel_club_api.mapper.VentaMapper;
import com.LosCiruelos.padel_club_api.usuarios.entity.Usuario;
import com.LosCiruelos.padel_club_api.usuarios.entity.enums.Role;
import com.LosCiruelos.padel_club_api.usuarios.service.UsuarioService;
import com.LosCiruelos.padel_club_api.ventas.entity.Venta;
import com.LosCiruelos.padel_club_api.ventas.entity.enums.MedioDePago;
import com.LosCiruelos.padel_club_api.ventas.repository.VentaRepository;

class VentaConsultaServiceTest {

    private VentaRepository ventaRepository;
    private VentaConsultaService servicio;
    private CobroQrDeVentas cobroQr;
    private Usuario admin;
    private Usuario empleado;

    @BeforeEach
    void setUp() {
        ventaRepository = mock(VentaRepository.class);
        UsuarioService usuarioService = mock(UsuarioService.class);
        cobroQr = mock(CobroQrDeVentas.class);
        servicio = new VentaConsultaService(ventaRepository, usuarioService, new VentaMapper(), TestData.reloj(AHORA), cobroQr);
        admin = usuario(1L, Role.ADMIN);
        empleado = usuario(2L, Role.EMPLEADO);
        when(usuarioService.autenticado(admin.getEmail())).thenReturn(admin);
        when(usuarioService.autenticado(empleado.getEmail())).thenReturn(empleado);
    }

    @Test
    void sinFechasListaLasDeHoy() {
        Venta venta = Venta.builder().id(3L).createdAt(AHORA).medio(MedioDePago.EFECTIVO).registradoPor(empleado)
                .build();
        venta.agregar(Producto.builder().id(5L).nombre("Agua").tipo(TipoProducto.VENTA)
                .precio(new BigDecimal("1500")).build(), 2);
        when(ventaRepository.findByCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDesc(
                HOY.atStartOfDay(), HOY.plusDays(1).atStartOfDay())).thenReturn(List.of(venta));

        assertThat(servicio.listar(null, null, empleado.getEmail()))
                .singleElement()
                .satisfies(v -> {
                    assertThat(v.total()).isEqualTo(new BigDecimal("3000.00"));
                    assertThat(v.registradaPor()).isEqualTo("Nombre2 Apellido2");
                });
    }

    @Test
    void elDetalleDeUnaVentaElEmpleadoSoloLoVeHoy() {
        Venta hoy = Venta.builder().id(3L).createdAt(AHORA).medio(MedioDePago.EFECTIVO).registradoPor(empleado)
                .total(BigDecimal.ZERO).build();
        Venta ayer = Venta.builder().id(4L).createdAt(AHORA.minusDays(1)).medio(MedioDePago.EFECTIVO)
                .registradoPor(empleado).total(BigDecimal.ZERO).build();
        when(ventaRepository.findConDetalleById(3L)).thenReturn(java.util.Optional.of(hoy));
        when(ventaRepository.findConDetalleById(4L)).thenReturn(java.util.Optional.of(ayer));

        assertThat(servicio.obtener(3L, empleado.getEmail()).id()).isEqualTo(3L);
        assertThatThrownBy(() -> servicio.obtener(4L, empleado.getEmail()))
                .isInstanceOf(com.LosCiruelos.padel_club_api.ventas.exception.VentaNoEncontradaException.class);
        assertThat(servicio.obtener(4L, admin.getEmail()).id()).isEqualTo(4L);
        assertThatThrownBy(() -> servicio.obtener(9L, admin.getEmail()))
                .isInstanceOf(com.LosCiruelos.padel_club_api.ventas.exception.VentaNoEncontradaException.class);
    }

    @Test
    void losMediosDeCobroEnOrdenYMercadoPagoSoloConElQrConfigurado() {
        when(cobroQr.estaConfigurado()).thenReturn(true);
        assertThat(servicio.medios()).containsExactly(
                new com.LosCiruelos.padel_club_api.ventas.dto.response.MedioDeVentaResponse(MedioDePago.EFECTIVO,
                        "Efectivo", true, false, null),
                new com.LosCiruelos.padel_club_api.ventas.dto.response.MedioDeVentaResponse(MedioDePago.MERCADO_PAGO,
                        "Mercado Pago (QR de la caja)", true, true, null),
                new com.LosCiruelos.padel_club_api.ventas.dto.response.MedioDeVentaResponse(MedioDePago.CUENTA_DNI,
                        "Cuenta DNI", true, false, null));

        when(cobroQr.estaConfigurado()).thenReturn(false);
        assertThat(servicio.medios().get(1)).satisfies(m -> {
            assertThat(m.disponible()).isFalse();
            assertThat(m.detalle()).isEqualTo("El QR de la caja de Mercado Pago no está configurado.");
        });
        assertThat(servicio.medios()).filteredOn(m -> !m.conQr()).allMatch(m -> m.disponible() && m.detalle() == null);
    }

    @Test
    void elAdminVeCualquierPeriodo() {
        LocalDate desde = LocalDate.of(2026, 10, 1);
        LocalDate hasta = LocalDate.of(2026, 10, 31);

        servicio.listar(desde, hasta, admin.getEmail());

        verify(ventaRepository).findByCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDesc(
                desde.atStartOfDay(), LocalDate.of(2026, 11, 1).atStartOfDay());
    }

    @Test
    void elEmpleadoSoloVeLasDeHoy() {
        assertThat(servicio.listar(HOY, HOY, empleado.getEmail())).isEmpty();
        assertThatThrownBy(() -> servicio.listar(HOY.minusDays(1), HOY, empleado.getEmail()))
                .isInstanceOf(PermisoDenegadoException.class)
                .hasMessage("Solo podés ver las ventas de hoy.");
    }

    @Test
    void validaElPeriodo() {
        assertThatThrownBy(() -> servicio.listar(HOY, null, admin.getEmail()))
                .isInstanceOf(ReservaException.class)
                .hasMessageContaining("las dos fechas");
        assertThatThrownBy(() -> servicio.listar(HOY, HOY.minusDays(1), admin.getEmail()))
                .isInstanceOf(ReservaException.class)
                .hasMessage("La fecha desde no puede ser posterior a hasta.");
    }
}
