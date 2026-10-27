package com.LosCiruelos.padel_club_api.reservas.service;

import static com.LosCiruelos.padel_club_api.TestData.AHORA;
import static com.LosCiruelos.padel_club_api.TestData.HOY;
import static com.LosCiruelos.padel_club_api.TestData.MANIANA;
import static com.LosCiruelos.padel_club_api.TestData.pago;
import static com.LosCiruelos.padel_club_api.TestData.reserva;
import static com.LosCiruelos.padel_club_api.TestData.usuario;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.dao.DataIntegrityViolationException;

import com.LosCiruelos.padel_club_api.Services.CanchaService;
import com.LosCiruelos.padel_club_api.Services.ClienteProfileService;
import com.LosCiruelos.padel_club_api.Services.ConfiguracionService;
import com.LosCiruelos.padel_club_api.Services.UsuarioService;
import com.LosCiruelos.padel_club_api.TestData;
import com.LosCiruelos.padel_club_api.mapper.PagoMapper;
import com.LosCiruelos.padel_club_api.reservas.dto.request.CrearReservaRequest;
import com.LosCiruelos.padel_club_api.DTOs.Responses.PaginaResponse;
import com.LosCiruelos.padel_club_api.reservas.dto.response.ReservaResponse;
import com.LosCiruelos.padel_club_api.Entities.ClienteProfile;
import com.LosCiruelos.padel_club_api.Entities.Configuracion;
import com.LosCiruelos.padel_club_api.pagos.entity.Pago;
import com.LosCiruelos.padel_club_api.Entities.Producto;
import com.LosCiruelos.padel_club_api.Entities.Usuario;
import com.LosCiruelos.padel_club_api.pagos.entity.enums.EstadoPago;
import com.LosCiruelos.padel_club_api.Entities.Enum.Role;
import com.LosCiruelos.padel_club_api.Entities.Enum.TipoProducto;
import com.LosCiruelos.padel_club_api.Exceptions.DiaSinAperturaException;
import com.LosCiruelos.padel_club_api.Exceptions.EstadoInvalidoException;
import com.LosCiruelos.padel_club_api.Exceptions.HorarioOcupadoException;
import com.LosCiruelos.padel_club_api.Exceptions.PerfilIncompletoException;
import com.LosCiruelos.padel_club_api.Exceptions.ReservaException;
import com.LosCiruelos.padel_club_api.Exceptions.ReservaNoEncontradaException;
import com.LosCiruelos.padel_club_api.Repository.CanchaRepository;
import com.LosCiruelos.padel_club_api.Repository.ConfiguracionRepository;
import com.LosCiruelos.padel_club_api.pagos.repository.PagoRepository;
import com.LosCiruelos.padel_club_api.reservas.entity.Reserva;
import com.LosCiruelos.padel_club_api.reservas.entity.ReservaProducto;
import com.LosCiruelos.padel_club_api.reservas.entity.enums.EstadoReserva;
import com.LosCiruelos.padel_club_api.mapper.ReservaMapper;
import com.LosCiruelos.padel_club_api.reservas.repository.ReservaRepository;
import com.LosCiruelos.padel_club_api.reservas.repository.TurnoFijoRepository;

class ReservaServiceTest {

    private UsuarioService usuarioService;
    private ClienteProfileService clienteProfileService;
    private ReservaRepository reservaRepository;
    private TurnoFijoRepository turnoFijoRepository;
    private CanchaRepository canchaRepository;
    private StockService stockService;
    private ReservaService reservaService;
    private ReservaMapper mapper;
    private DisponibilidadService disponibilidad;
    private Configuracion config;

    private Usuario cliente;
    private Usuario otroCliente;
    private Usuario admin;

    @BeforeEach
    void setUp() {
        config = TestData.configuracion();
        ConfiguracionRepository configuracionRepository = mock(ConfiguracionRepository.class);
        when(configuracionRepository.obtener()).thenReturn(config);

        usuarioService = mock(UsuarioService.class);
        clienteProfileService = mock(ClienteProfileService.class);
        reservaRepository = mock(ReservaRepository.class);
        turnoFijoRepository = mock(TurnoFijoRepository.class);
        canchaRepository = mock(CanchaRepository.class);
        PagoRepository pagoRepository = mock(PagoRepository.class);

        stockService = mock(StockService.class);
        ConfiguracionService configuracionService = new ConfiguracionService(configuracionRepository);
        mapper = new ReservaMapper(configuracionService, new PoliticaCancelacion(configuracionService), TestData.reloj(AHORA), new PagoMapper());
        disponibilidad = new DisponibilidadService(configuracionService, new CanchaService(canchaRepository), reservaRepository,
                turnoFijoRepository, TestData.reloj(AHORA));
        reservaService = new ReservaService(configuracionService, usuarioService,
                clienteProfileService, reservaRepository, canchaRepository, pagoRepository, stockService,
                mapper, disponibilidad, TestData.reloj(AHORA));

        cliente = usuario(1L, Role.CLIENTE);
        otroCliente = usuario(2L, Role.CLIENTE);
        admin = usuario(3L, Role.ADMIN);
        for (Usuario u : List.of(cliente, otroCliente, admin)) {
            when(usuarioService.autenticado(u.getEmail())).thenReturn(u);
        }
        when(usuarioService.findByIdOrThrow(1L)).thenReturn(cliente);
        when(usuarioService.findByIdOrThrow(3L)).thenReturn(admin);

        when(clienteProfileService.findByUsuario(any())).thenReturn(new ClienteProfile());
        when(clienteProfileService.esPerfilCompleto(any())).thenReturn(true);
        when(canchaRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(TestData.cancha()));
        when(reservaRepository.saveAndFlush(any(Reserva.class))).thenAnswer(inv -> {
            Reserva r = inv.getArgument(0);
            if (r.getId() == null) {
                r.setId(50L);
            }
            return r;
        });
    }

    private static void agregarProducto(Reserva destino, String codigo, TipoProducto tipo, int cantidad,
            String precio) {
        Producto producto = Producto.builder().id((long) codigo.hashCode()).codigo(codigo).nombre(codigo).tipo(tipo)
                .precio(new BigDecimal(precio)).stock(100).build();
        destino.getProductos().add(ReservaProducto.builder().reserva(destino).producto(producto).cantidad(cantidad)
                .precioUnitario(new BigDecimal(precio)).build());
    }

    private static CrearReservaRequest pedido(LocalDate fecha, String inicio, String fin) {
        CrearReservaRequest req = new CrearReservaRequest();
        req.setCanchaId(1L);
        req.setFechaReserva(fecha);
        req.setHoraInicio(LocalTime.parse(inicio));
        req.setHoraFin(LocalTime.parse(fin));
        req.setCantidadPelotas(0);
        req.setCantidadPaletas(0);
        return req;
    }

    private void sinOtrasReservas() {
        when(reservaRepository.findQueOcupan(anyLong(), any(), any(), any(), any(), anyCollection(), any(),
                anyCollection())).thenReturn(List.of());
        when(reservaRepository.findPendientesVencidasQueSeSuperponen(anyLong(), any(), any(), any(), any(), any(),
                anyCollection())).thenReturn(List.of());
        when(reservaRepository.findPendienteVigenteMismoInicio(anyLong(), anyLong(), any(), any(), any(), any()))
                .thenReturn(Optional.empty());
        when(reservaRepository.findPendientesSinPagoActivo(anyLong(), anyLong(), any(), any(), any(),
                anyCollection())).thenReturn(List.of());
    }

    // ─────────────────────────────────────────────────────────────────────

    @Nested
    class ValidacionDeHorario {

        private void invalido(LocalDate fecha, String inicio, String fin, Class<? extends Exception> tipo,
                String mensaje) {
            assertThatThrownBy(() -> disponibilidad.validarHorario(config, fecha, LocalTime.parse(inicio),
                    LocalTime.parse(fin), AHORA))
                    .isInstanceOf(tipo)
                    .hasMessageContaining(mensaje);
        }

        @Test
        void turnoValidoDevuelveLaDuracion() {
            assertThat(disponibilidad.validarHorario(config, MANIANA, LocalTime.of(18, 0), LocalTime.of(19, 30), AHORA))
                    .isEqualTo(90);
        }

        @Test
        void rechazaHorariosInvalidos() {
            invalido(HOY.minusDays(1), "18:00", "19:30", ReservaException.class, "hoy o en el futuro");
            invalido(HOY, "09:00", "10:30", ReservaException.class, "ya pasó");
            invalido(MANIANA, "19:30", "18:00", ReservaException.class, "anterior a la hora de fin");
            invalido(MANIANA, "18:00", "19:00", ReservaException.class, "mínima");
            invalido(MANIANA, "12:00", "15:30", ReservaException.class, "máxima");
            invalido(MANIANA, "18:00", "19:45", ReservaException.class, "múltiplo de 30");
            invalido(MANIANA, "07:00", "08:30", DiaSinAperturaException.class, "apertura");
            invalido(MANIANA, "22:00", "23:30", DiaSinAperturaException.class, "cierre");
        }

        @Test
        void elDomingoCierraMasTemprano() {
            LocalDate domingo = LocalDate.of(2026, 10, 18);
            invalido(domingo, "19:00", "20:30", DiaSinAperturaException.class, "20:00");
        }
    }

    // ─────────────────────────────────────────────────────────────────────

    @Nested
    class Creacion {

        @Test
        void creaUnaReservaPendienteQueVenceEn15Minutos() {
            sinOtrasReservas();

            ReservaResponse r = reservaService.crearReserva(pedido(MANIANA, "18:00", "19:30"), cliente.getEmail());

            assertThat(r.getEstado()).isEqualTo(EstadoReserva.PENDIENTE);
            assertThat(r.getMontoTotal()).isEqualByComparingTo("15000");
            assertThat(r.getMontoSenia()).isEqualByComparingTo("7500");
            assertThat(r.getExpiresAt()).isEqualTo(AHORA.plusMinutes(15));
            assertThat(r.getClienteId()).isEqualTo(cliente.getId());
            verify(canchaRepository).findByIdForUpdate(1L);
        }

        @Test
        void elTotalIncluyeLosProductosPedidos() {
            sinOtrasReservas();
            // el servicio de productos agrega 2 pelotas a $1500 y 4 paletas a $2000
            doAnswer(inv -> {
                Reserva destino = inv.getArgument(1);
                agregarProducto(destino, Producto.CODIGO_PELOTAS, TipoProducto.VENTA, 2, "1500");
                agregarProducto(destino, Producto.CODIGO_PALETAS, TipoProducto.ALQUILER, 4, "2000");
                return null;
            }).when(stockService).moverStock(any(), any(), any(), any());
            CrearReservaRequest req = pedido(MANIANA, "18:00", "19:30");
            req.setCantidadPelotas(2);
            req.setCantidadPaletas(4);

            ReservaResponse r = reservaService.crearReserva(req, cliente.getEmail());

            assertThat(r.getMontoCancha()).isEqualByComparingTo("15000");
            assertThat(r.getMontoProductos()).isEqualByComparingTo("11000");
            assertThat(r.getMontoTotal()).isEqualByComparingTo("26000");
            assertThat(r.getMontoSenia()).isEqualByComparingTo("13000"); // 50 % del total
            assertThat(r.getCantidadPelotas()).isEqualTo(2);
            assertThat(r.getCantidadPaletas()).isEqualTo(4);
            verify(stockService).moverStock(eq(List.of()), any(Reserva.class),
                    eq(Map.of(Producto.CODIGO_PELOTAS, 2, Producto.CODIGO_PALETAS, 4)), eq(AHORA));
        }

        @Test
        void turnoOcupadoDevuelve409() {
            sinOtrasReservas();
            when(reservaRepository.findQueOcupan(anyLong(), any(), any(), any(), any(), anyCollection(), any(),
                    anyCollection())).thenReturn(List.of(reserva(9L, otroCliente, EstadoReserva.PAGADA, "15000")));

            assertThatThrownBy(() -> reservaService.crearReserva(pedido(MANIANA, "18:30", "20:00"), cliente.getEmail()))
                    .isInstanceOf(HorarioOcupadoException.class);
            verify(reservaRepository, never()).saveAndFlush(any());
        }

        @Test
        void siLaBaseDetectaElSolapamientoTambienDevuelve409() {
            sinOtrasReservas();
            when(reservaRepository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("exclusion"));

            assertThatThrownBy(() -> reservaService.crearReserva(pedido(MANIANA, "18:00", "19:30"), cliente.getEmail()))
                    .isInstanceOf(HorarioOcupadoException.class);
        }

        @Test
        void lasPendientesVencidasQueSeSuperponenSeExpiranAntes() {
            sinOtrasReservas();
            Reserva vencida = reserva(9L, otroCliente, EstadoReserva.PENDIENTE, "15000");
            vencida.setExpiresAt(AHORA.minusMinutes(1));
            Pago iniciado = pago(1L, vencida, EstadoPago.PENDIENTE, "7500");
            when(reservaRepository.findPendientesVencidasQueSeSuperponen(anyLong(), any(), any(), any(), any(), any(),
                    anyCollection())).thenReturn(List.of(9L));
            when(reservaRepository.findByIdForUpdate(9L)).thenReturn(Optional.of(vencida));

            reservaService.crearReserva(pedido(MANIANA, "18:00", "19:30"), cliente.getEmail());

            assertThat(vencida.getEstado()).isEqualTo(EstadoReserva.EXPIRADA);
            assertThat(iniciado.getEstado()).isEqualTo(EstadoPago.EXPIRADO);
            // su stock se devuelve en el mismo paso en que se toma el de la nueva reserva
            verify(stockService).moverStock(eq(List.of(vencida)), any(Reserva.class), any(), eq(AHORA));
        }

        @Test
        void otrasPendientesDelClienteEseDiaSeCancelan() {
            sinOtrasReservas();
            Reserva anterior = reserva(8L, cliente, EstadoReserva.PENDIENTE, "15000");
            when(reservaRepository.findPendientesSinPagoActivo(anyLong(), anyLong(), any(), any(), any(),
                    anyCollection())).thenReturn(List.of(8L));
            when(reservaRepository.findByIdForUpdate(8L)).thenReturn(Optional.of(anterior));

            reservaService.crearReserva(pedido(MANIANA, "20:00", "21:30"), cliente.getEmail());

            assertThat(anterior.getEstado()).isEqualTo(EstadoReserva.CANCELADA);
        }

        @Test
        void unClienteNoPuedeReservarParaOtro() {
            CrearReservaRequest req = pedido(MANIANA, "18:00", "19:30");
            req.setClienteId(2L);

            assertThatThrownBy(() -> reservaService.crearReserva(req, cliente.getEmail()))
                    .isInstanceOf(ReservaException.class)
                    .hasMessageContaining("sí mismo");
        }

        @Test
        void elAdminDebeIndicarUnClienteValido() {
            assertThatThrownBy(() -> reservaService.crearReserva(pedido(MANIANA, "18:00", "19:30"), admin.getEmail()))
                    .isInstanceOf(ReservaException.class)
                    .hasMessageContaining("clienteId");

            CrearReservaRequest paraAdmin = pedido(MANIANA, "18:00", "19:30");
            paraAdmin.setClienteId(3L);
            assertThatThrownBy(() -> reservaService.crearReserva(paraAdmin, admin.getEmail()))
                    .isInstanceOf(ReservaException.class)
                    .hasMessageContaining("no es un cliente");
        }

        @Test
        void elAdminReservaParaUnCliente() {
            sinOtrasReservas();
            CrearReservaRequest req = pedido(MANIANA, "18:00", "19:30");
            req.setClienteId(1L);

            ReservaResponse r = reservaService.crearReserva(req, admin.getEmail());

            assertThat(r.getClienteId()).isEqualTo(1L);
        }

        @Test
        void perfilIncompletoNoPuedeReservar() {
            when(clienteProfileService.esPerfilCompleto(any())).thenReturn(false);

            assertThatThrownBy(() -> reservaService.crearReserva(pedido(MANIANA, "18:00", "19:30"), cliente.getEmail()))
                    .isInstanceOf(PerfilIncompletoException.class);
        }
    }

    // ─────────────────────────────────────────────────────────────────────

    @Nested
    class CambioDeDuracion {

        private Reserva existente;

        @BeforeEach
        void reservaExistente() {
            sinOtrasReservas();
            existente = reserva(7L, cliente, EstadoReserva.PENDIENTE, "15000");
            when(reservaRepository.findPendienteVigenteMismoInicio(anyLong(), anyLong(), any(), any(), any(), any()))
                    .thenReturn(Optional.of(7L));
            when(reservaRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(existente));
        }

        @Test
        void extiendeLaReservaYRecalculaElMonto() {
            Pago iniciado = pago(1L, existente, EstadoPago.PENDIENTE, "7500");

            ReservaResponse r = reservaService.crearReserva(pedido(MANIANA, "18:00", "21:00"), cliente.getEmail());

            assertThat(r.getId()).isEqualTo(7L);
            assertThat(r.getHoraFin()).isEqualTo(LocalTime.of(21, 0));
            assertThat(r.getMontoTotal()).isEqualByComparingTo("30000");
            // el pago iniciado con el monto anterior ya no sirve
            assertThat(iniciado.getEstado()).isEqualTo(EstadoPago.EXPIRADO);
        }

        @Test
        void noPuedeExtenderseSobreOtraReserva() {
            // antes se actualizaba sin revisar el solapamiento
            when(reservaRepository.findQueOcupan(anyLong(), any(), any(), any(), any(), anyCollection(), any(),
                    anyCollection())).thenReturn(List.of(existente, reserva(9L, otroCliente, EstadoReserva.PAGADA, "1")));

            assertThatThrownBy(() -> reservaService.crearReserva(pedido(MANIANA, "18:00", "21:00"), cliente.getEmail()))
                    .isInstanceOf(HorarioOcupadoException.class);
        }

        @Test
        void cambiarSoloLosProductosRecalculaElTotal() {
            doAnswer(inv -> {
                agregarProducto(inv.getArgument(1), Producto.CODIGO_PELOTAS, TipoProducto.VENTA, 3, "1000");
                return null;
            }).when(stockService).moverStock(any(), eq(existente), any(), any());
            CrearReservaRequest req = pedido(MANIANA, "18:00", "19:30");
            req.setCantidadPelotas(3);

            ReservaResponse r = reservaService.crearReserva(req, cliente.getEmail());

            assertThat(r.getId()).isEqualTo(7L);
            assertThat(r.getMontoTotal()).isEqualByComparingTo("18000");
        }

        @Test
        void sinCambiosNoTocaNada() {
            ReservaResponse r = reservaService.crearReserva(pedido(MANIANA, "18:00", "19:30"), cliente.getEmail());

            assertThat(r.getMontoTotal()).isEqualByComparingTo("15000");
            verify(stockService).moverStock(eq(List.of()), isNull(), eq(Map.of()), any());
        }

        @Test
        void conUnPagoEnCursoNoSePuedeModificar() {
            pago(1L, existente, EstadoPago.EN_PROCESO, "7500");

            assertThatThrownBy(() -> reservaService.crearReserva(pedido(MANIANA, "18:00", "21:00"), cliente.getEmail()))
                    .isInstanceOf(EstadoInvalidoException.class);
        }
    }

    // ─────────────────────────────────────────────────────────────────────

    @Nested
    class Expiracion {

        private Reserva reservaDelCliente;

        @BeforeEach
        void reserva10() {
            reservaDelCliente = reserva(10L, cliente, EstadoReserva.PENDIENTE, "15000");
            when(reservaRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(reservaDelCliente));
        }

        @Test
        void expiraSoloSiVencioYNoTienePagosActivos() {
            assertThat(reservaService.expirarSiCorresponde(10L)).isFalse(); // todavía en plazo

            reservaDelCliente.setExpiresAt(AHORA.minusSeconds(1));
            Pago enProceso = pago(1L, reservaDelCliente, EstadoPago.EN_PROCESO, "7500");
            assertThat(reservaService.expirarSiCorresponde(10L)).isFalse(); // MP lo está procesando

            enProceso.setEstado(EstadoPago.RECHAZADO);
            assertThat(reservaService.expirarSiCorresponde(10L)).isTrue();
            assertThat(reservaDelCliente.getEstado()).isEqualTo(EstadoReserva.EXPIRADA);
        }

        @Test
        void expirarUnaReservaInexistenteNoFalla() {
            when(reservaRepository.findByIdForUpdate(99L)).thenReturn(Optional.empty());
            assertThat(reservaService.expirarSiCorresponde(99L)).isFalse();
        }

        @Test
        void elUsuarioNoPuedeExpirarUnaReservaEnPlazo() {
            assertThatThrownBy(() -> reservaService.expirarPorUsuario(10L, cliente.getEmail()))
                    .isInstanceOf(EstadoInvalidoException.class)
                    .hasMessageContaining("todavía no venció");

            reservaDelCliente.setExpiresAt(AHORA.minusMinutes(1));
            reservaService.expirarPorUsuario(10L, cliente.getEmail());
            assertThat(reservaDelCliente.getEstado()).isEqualTo(EstadoReserva.EXPIRADA);
        }
    }

    // ─────────────────────────────────────────────────────────────────────

    @Nested
    class DevolucionDePaletas {

        private Reserva jugada;
        private ReservaProducto paletas;

        @BeforeEach
        void reservaQueYaEmpezo() {
            jugada = reserva(20L, cliente, EstadoReserva.PAGADA, "21000");
            jugada.setFechaReserva(HOY);
            jugada.setHoraInicio(LocalTime.of(8, 0));
            jugada.setHoraFin(LocalTime.of(9, 30));
            agregarProducto(jugada, Producto.CODIGO_PALETAS, TipoProducto.ALQUILER, 3, "2000");
            paletas = jugada.getProductos().getFirst();
            when(reservaRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(jugada));
        }

        @Test
        void registraLaDevolucion() {
            reservaService.registrarDevolucion(20L, 2, admin.getEmail());
            verify(stockService).registrarDevolucion(paletas, 2, AHORA);
        }

        @Test
        void validaCantidadYQueNoSeRegistreDosVeces() {
            assertThatThrownBy(() -> reservaService.registrarDevolucion(20L, 4, admin.getEmail()))
                    .isInstanceOf(ReservaException.class)
                    .hasMessageContaining("entre 0 y 3");

            paletas.setCantidadDevuelta(3);
            assertThatThrownBy(() -> reservaService.registrarDevolucion(20L, 3, admin.getEmail()))
                    .isInstanceOf(EstadoInvalidoException.class)
                    .hasMessageContaining("ya fue registrada");
        }

        @Test
        void noSeRegistraAntesDelTurnoNiEnReservasCanceladasNiSinPaletas() {
            jugada.setFechaReserva(MANIANA);
            assertThatThrownBy(() -> reservaService.registrarDevolucion(20L, 3, admin.getEmail()))
                    .isInstanceOf(EstadoInvalidoException.class)
                    .hasMessageContaining("no empezó");

            jugada.setFechaReserva(HOY);
            jugada.setEstado(EstadoReserva.CANCELADA);
            assertThatThrownBy(() -> reservaService.registrarDevolucion(20L, 3, admin.getEmail()))
                    .isInstanceOf(EstadoInvalidoException.class);

            jugada.setEstado(EstadoReserva.PAGADA);
            jugada.getProductos().clear();
            assertThatThrownBy(() -> reservaService.registrarDevolucion(20L, 0, admin.getEmail()))
                    .isInstanceOf(EstadoInvalidoException.class)
                    .hasMessageContaining("no tiene paletas");
        }

        @Test
        void listaLasDevolucionesPendientes() {
            when(reservaRepository.findConAlquileresSinDevolver(eq(TipoProducto.ALQUILER),
                    eq(Reserva.ESTADOS_QUE_SE_JUEGAN), eq(HOY), eq(AHORA.toLocalTime()))).thenReturn(List.of(jugada));

            assertThat(reservaService.devolucionesPendientes()).extracting(ReservaResponse::getId).containsExactly(20L);
        }
    }

    @Test
    void obtenerUnaReservaAjenaDevuelveNoEncontrada() {
        Reserva ajena = reserva(11L, otroCliente, EstadoReserva.PENDIENTE, "15000");
        when(reservaRepository.findById(11L)).thenReturn(Optional.of(ajena));

        assertThatThrownBy(() -> reservaService.obtener(11L, cliente.getEmail()))
                .isInstanceOf(ReservaNoEncontradaException.class);
        assertThat(reservaService.obtener(11L, otroCliente.getEmail()).getId()).isEqualTo(11L);
    }

    // ─────────────────────────────────────────────────────────────────────

    @Nested
    class MisReservas {

        @BeforeEach
        void unaPagina() {
            when(reservaRepository.findAll(org.mockito.ArgumentMatchers.<Specification<Reserva>>any(),
                    any(Pageable.class))).thenAnswer(inv -> {
                        Pageable p = inv.getArgument(1);
                        return new PageImpl<>(List.of(reserva(1L, cliente, EstadoReserva.PAGADA, "15000")), p, 37);
                    });
        }

        private Pageable pedido() {
            ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
            verify(reservaRepository).findAll(org.mockito.ArgumentMatchers.<Specification<Reserva>>any(),
                    captor.capture());
            return captor.getValue();
        }

        @Test
        void devuelveUnaPaginaConElTotal() {
            PaginaResponse<ReservaResponse> r = reservaService.misReservas(cliente.getEmail(), "historial",
                    List.of(EstadoReserva.COMPLETADA), HOY.minusDays(30), HOY, false, 2, 10);

            assertThat(r.contenido()).hasSize(1);
            assertThat(r.pagina()).isEqualTo(2);
            assertThat(r.tamanio()).isEqualTo(10);
            assertThat(r.totalElementos()).isEqualTo(37);
            assertThat(r.totalPaginas()).isEqualTo(4);
            assertThat(pedido().getSort()).isEqualTo(Sort.by(Sort.Direction.DESC, "fechaReserva", "horaInicio"));
        }

        @Test
        void lasProximasVanDeLaMasCercanaALaMasLejana() {
            reservaService.misReservas(cliente.getEmail(), "PROXIMAS", null, null, null, false, 0, 50);

            assertThat(pedido().getSort()).isEqualTo(Sort.by(Sort.Direction.ASC, "fechaReserva", "horaInicio"));
        }

        @Test
        void sinTipoOrdenaDeLaMasNuevaALaMasVieja() {
            reservaService.misReservas(cliente.getEmail(), null, null, null, null, false, 0, 10);

            assertThat(pedido().getSort()).isEqualTo(Sort.by(Sort.Direction.DESC, "fechaReserva", "horaInicio"));
        }

        @Test
        void unaReservaJugadaSinPagarDelTodoDebeSaldo() {
            Reserva jugada = reserva(30L, cliente, EstadoReserva.SEÑADA, "15000");
            jugada.setFechaReserva(HOY.minusDays(1));
            Reserva futura = reserva(31L, cliente, EstadoReserva.SEÑADA, "15000");
            Reserva pagaYJugada = reserva(32L, cliente, EstadoReserva.COMPLETADA, "15000");
            pagaYJugada.setFechaReserva(HOY.minusDays(1));

            ReservaResponse r = mapper.aResponse(jugada);
            assertThat(r.getTurnoTerminado()).isTrue();
            assertThat(r.getDebeSaldo()).isTrue();

            assertThat(mapper.aResponse(futura).getDebeSaldo()).isFalse();
            assertThat(mapper.aResponse(pagaYJugada).getTurnoTerminado()).isTrue();
            assertThat(mapper.aResponse(pagaYJugada).getDebeSaldo()).isFalse();
        }

        @Test
        void listaLosTurnosJugadosQueDebenSaldo() {
            Reserva jugada = reserva(30L, cliente, EstadoReserva.PARCIALMENTE_PAGADA, "15000");
            jugada.setFechaReserva(HOY.minusDays(1));
            when(reservaRepository.findTerminadasConSaldo(Reserva.ESTADOS_CON_SALDO, HOY, AHORA.toLocalTime()))
                    .thenReturn(List.of(jugada));

            assertThat(reservaService.saldosPendientes()).extracting(ReservaResponse::getId).containsExactly(30L);
        }

        @Test
        void validaLosParametros() {
            assertThatThrownBy(() -> reservaService.misReservas(cliente.getEmail(), null, null, null, null, false, -1, 10))
                    .isInstanceOf(ReservaException.class);
            assertThatThrownBy(() -> reservaService.misReservas(cliente.getEmail(), null, null, null, null, false, 0, 51))
                    .isInstanceOf(ReservaException.class);
            assertThatThrownBy(() -> reservaService.misReservas(cliente.getEmail(), null, null, null, null, false, 0, 0))
                    .isInstanceOf(ReservaException.class);
            assertThatThrownBy(() -> reservaService.misReservas(cliente.getEmail(), null, null, HOY, HOY.minusDays(1),
                    false, 0, 10))
                    .isInstanceOf(ReservaException.class)
                    .hasMessage("La fecha desde no puede ser posterior a hasta.");
            assertThatThrownBy(() -> reservaService.misReservas(cliente.getEmail(), "futuras", null, null, null, false, 0, 10))
                    .isInstanceOf(ReservaException.class)
                    .hasMessageContaining("proximas");
        }
    }

    @Test
    void elJobSoloCompletaReservasPagadas() {
        when(reservaRepository.marcarCompletadas(eq(EstadoReserva.COMPLETADA), eq(java.util.Set.of(EstadoReserva.PAGADA)),
                eq(HOY), eq(AHORA.toLocalTime()))).thenReturn(3);

        assertThat(reservaService.marcarCompletadas()).isEqualTo(3);
    }
}
