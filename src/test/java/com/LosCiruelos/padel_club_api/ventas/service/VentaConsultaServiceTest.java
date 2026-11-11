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

    private static final com.LosCiruelos.padel_club_api.ventas.repository.TotalesDelListado SIN_TOTALES =
            new com.LosCiruelos.padel_club_api.ventas.repository.TotalesDelListado(0, 0, java.util.Map.of(),
                    java.util.Map.of());

    /** Lista con el repository mockeado y devuelve el filtro que se usó. */
    private com.LosCiruelos.padel_club_api.ventas.repository.FiltroVentas listar(LocalDate desde, LocalDate hasta,
            String cliente, List<MedioDePago> medios, String email) {
        when(ventaRepository.findAll(org.mockito.ArgumentMatchers.<org.springframework.data.jpa.domain.Specification<Venta>>any(),
                org.mockito.ArgumentMatchers.any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(org.springframework.data.domain.Page.empty());
        when(ventaRepository.totalesDelListado(org.mockito.ArgumentMatchers.any())).thenReturn(SIN_TOTALES);
        servicio.listar(desde, hasta, null, cliente, null, medios, null, 0, null, null, email);
        org.mockito.ArgumentCaptor<com.LosCiruelos.padel_club_api.ventas.repository.FiltroVentas> filtro =
                org.mockito.ArgumentCaptor.forClass(com.LosCiruelos.padel_club_api.ventas.repository.FiltroVentas.class);
        verify(ventaRepository, org.mockito.Mockito.atLeastOnce()).totalesDelListado(filtro.capture());
        return filtro.getValue();
    }

    @Test
    void sinFechasListaLasDeHoyDeLaMasNuevaALaMasVieja() {
        var filtro = listar(null, null, null, null, empleado.getEmail());

        assertThat(filtro.desde()).isEqualTo(HOY);
        assertThat(filtro.hasta()).isEqualTo(HOY);
        org.mockito.ArgumentCaptor<org.springframework.data.domain.Pageable> pagina =
                org.mockito.ArgumentCaptor.forClass(org.springframework.data.domain.Pageable.class);
        verify(ventaRepository).findAll(org.mockito.ArgumentMatchers.<org.springframework.data.jpa.domain.Specification<Venta>>any(),
                pagina.capture());
        assertThat(pagina.getValue().getPageSize()).isEqualTo(20);
        assertThat(pagina.getValue().getSort().getOrderFor("createdAt").isDescending()).isTrue();
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
                new com.LosCiruelos.padel_club_api.ventas.dto.response.MedioDeVentaResponse(MedioDePago.TRANSFERENCIA_MP,
                        "Mercado Pago (transferencia)", true, false, null),
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
    void elAdminVeCualquierPeriodoYCombinaLosFiltros() {
        var filtro = listar(LocalDate.of(2026, 10, 1), null, "  Pérez ", List.of(MedioDePago.CUENTA_DNI),
                admin.getEmail());

        assertThat(filtro.desde()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(filtro.hasta()).isNull();
        assertThat(filtro.textoCliente()).isEqualTo("perez");
        assertThat(filtro.medios()).containsExactly(MedioDePago.CUENTA_DNI);
    }

    @Test
    void elEmpleadoSoloVeLasDeHoy() {
        listar(HOY, HOY, null, null, empleado.getEmail());
        assertThatThrownBy(() -> servicio.listar(HOY.minusDays(1), HOY, null, null, null, null, null, 0, null, null,
                empleado.getEmail()))
                .isInstanceOf(PermisoDenegadoException.class)
                .hasMessage("Solo podés ver las ventas de hoy.");
        assertThatThrownBy(() -> servicio.listar(HOY, null, null, null, null, null, null, 0, null, null,
                empleado.getEmail()))
                .isInstanceOf(PermisoDenegadoException.class);
    }

    @Test
    void validaElPeriodoElClienteLaPaginaYElOrden() {
        assertThatThrownBy(() -> servicio.listar(HOY, HOY.minusDays(1), null, null, null, null, null, 0, null, null,
                admin.getEmail()))
                .isInstanceOf(ReservaException.class)
                .hasMessage("La fecha desde no puede ser posterior a hasta.");
        assertThatThrownBy(() -> servicio.listar(HOY, HOY, null, "pé", null, null, null, 0, null, null, admin.getEmail()))
                .isInstanceOf(ReservaException.class)
                .hasMessage("Escribí al menos 3 letras para buscar un cliente.");
        assertThatThrownBy(() -> servicio.listar(HOY, HOY, null, null, null, null, null, 0, 101, null, admin.getEmail()))
                .isInstanceOf(ReservaException.class)
                .hasMessage("El tamaño de página tiene que estar entre 1 y 100.");
        assertThatThrownBy(() -> servicio.listar(HOY, HOY, null, null, null, null, null, 0, null, "x", admin.getEmail()))
                .isInstanceOf(ReservaException.class)
                .hasMessage("El orden tiene que ser 'asc' o 'desc'.");
    }
}
