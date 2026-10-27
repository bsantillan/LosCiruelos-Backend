package com.LosCiruelos.padel_club_api.pagos.service;

import static com.LosCiruelos.padel_club_api.TestData.AHORA;
import static com.LosCiruelos.padel_club_api.TestData.pago;
import static com.LosCiruelos.padel_club_api.TestData.reserva;
import static com.LosCiruelos.padel_club_api.TestData.usuario;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.context.ApplicationEventPublisher;

import com.LosCiruelos.padel_club_api.Services.ConfiguracionService;
import com.LosCiruelos.padel_club_api.Services.UsuarioService;
import com.LosCiruelos.padel_club_api.TestData;
import com.LosCiruelos.padel_club_api.mapper.PagoMapper;
import com.LosCiruelos.padel_club_api.pagos.entity.Pago;
import com.LosCiruelos.padel_club_api.pagos.payment.EstadoPayment;
import com.LosCiruelos.padel_club_api.pagos.payment.PaymentResult;
import com.LosCiruelos.padel_club_api.pagos.repository.PagoRepository;
import com.LosCiruelos.padel_club_api.reservas.service.ReservaConsultaService;
import com.LosCiruelos.padel_club_api.reservas.service.ReservaService;
import com.LosCiruelos.padel_club_api.reservas.service.StockService;
import com.LosCiruelos.padel_club_api.reservas.entity.Reserva;
import com.LosCiruelos.padel_club_api.Entities.Usuario;
import com.LosCiruelos.padel_club_api.reservas.entity.enums.EstadoReserva;
import com.LosCiruelos.padel_club_api.Entities.Enum.Role;
import com.LosCiruelos.padel_club_api.Repository.ConfiguracionRepository;
import com.LosCiruelos.padel_club_api.pagos.payment.PaymentService;
import com.LosCiruelos.padel_club_api.pagos.payment.PaymentServiceFactory;
import com.LosCiruelos.padel_club_api.common.Reloj;

/**
 * Lo común a los tests de los servicios de pagos: los tres servicios con el mismo
 * {@link EstadosDePago} real, el proveedor y los repositories mockeados.
 */
abstract class PagosTestBase {

    protected PaymentService mercadoPago;
    protected ReservaService reservaService;
    protected ReservaConsultaService reservaConsultaService;
    protected PagoRepository pagoRepository;
    protected UsuarioService usuarioService;
    protected PagoOnlineService pagoOnlineService;
    protected CobroService cobroService;
    protected ReembolsoService reembolsoService;
    protected ApplicationEventPublisher eventos;

    protected Usuario cliente;
    protected Usuario empleado;

    @BeforeEach
    void setUp() {
        mercadoPago = mock(PaymentService.class);
        PaymentServiceFactory factory = mock(PaymentServiceFactory.class);
        when(factory.getService()).thenReturn(mercadoPago);

        ConfiguracionRepository configuracionRepository = mock(ConfiguracionRepository.class);
        when(configuracionRepository.obtener()).thenReturn(TestData.configuracion());

        reservaService = mock(ReservaService.class);
        pagoRepository = mock(PagoRepository.class);
        usuarioService = mock(UsuarioService.class);

        // save devuelve el mismo pago y le asigna id si es nuevo (como IDENTITY)
        when(pagoRepository.save(any(Pago.class))).thenAnswer(inv -> {
            Pago p = inv.getArgument(0);
            if (p.getId() == null) {
                p.setId(100L);
            }
            return p;
        });

        cliente = usuario(1L, Role.CLIENTE);
        empleado = usuario(2L, Role.EMPLEADO);
        when(usuarioService.autenticado(cliente.getEmail())).thenReturn(cliente);
        when(usuarioService.autenticado(empleado.getEmail())).thenReturn(empleado);

        ConfiguracionService configuracionService = new ConfiguracionService(configuracionRepository);
        Reloj reloj = TestData.reloj(AHORA);
        eventos = mock(ApplicationEventPublisher.class);
        reservaConsultaService = mock(ReservaConsultaService.class);
        EstadosDePago estados = new EstadosDePago(pagoRepository, configuracionService, mock(StockService.class),
                reloj, eventos);
        pagoOnlineService = new PagoOnlineService(factory, reservaService, pagoRepository, configuracionService,
                usuarioService, estados, reloj, new PagoMapper());
        cobroService = new CobroService(reservaService, reservaConsultaService, pagoRepository, usuarioService, estados,
                reloj, new PagoMapper());
        reembolsoService = new ReembolsoService(factory, reservaService, pagoRepository, estados, reloj);
    }

    protected Reserva reservaBloqueada(EstadoReserva estado) {
        Reserva reserva = reserva(10L, cliente, estado, "15000");
        when(reservaService.bloquearConAcceso(eq(10L), any())).thenReturn(reserva);
        when(reservaService.bloquear(10L)).thenReturn(reserva);
        return reserva;
    }

    protected static PaymentResult resultado(EstadoPayment estado, String monto, String paymentId, Long pagoId) {
        return PaymentResult.builder()
                .estado(estado)
                .monto(monto != null ? new BigDecimal(monto) : null)
                .paymentId(paymentId)
                .pagoId(pagoId)
                .externalReference("10")
                .build();
    }
}
