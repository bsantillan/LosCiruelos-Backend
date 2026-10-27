package com.LosCiruelos.padel_club_api.pagos.service;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.LosCiruelos.padel_club_api.Entities.Configuracion;
import com.LosCiruelos.padel_club_api.Exceptions.EstadoInvalidoException;
import com.LosCiruelos.padel_club_api.Services.ConfiguracionService;
import com.LosCiruelos.padel_club_api.Services.UsuarioService;
import com.LosCiruelos.padel_club_api.common.Dinero;
import com.LosCiruelos.padel_club_api.common.Reloj;
import com.LosCiruelos.padel_club_api.mapper.PagoMapper;
import com.LosCiruelos.padel_club_api.pagos.dto.response.PagoResponse;
import com.LosCiruelos.padel_club_api.pagos.entity.Pago;
import com.LosCiruelos.padel_club_api.pagos.entity.enums.EstadoPago;
import com.LosCiruelos.padel_club_api.pagos.payment.PaymentProvider;
import com.LosCiruelos.padel_club_api.pagos.payment.PaymentResult;
import com.LosCiruelos.padel_club_api.pagos.payment.PaymentServiceFactory;
import com.LosCiruelos.padel_club_api.pagos.repository.PagoRepository;
import com.LosCiruelos.padel_club_api.reservas.entity.Reserva;
import com.LosCiruelos.padel_club_api.reservas.service.ReservaService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Pagos online con Mercado Pago (Brick / Wallet): iniciar el pago, cobrarlo con el Brick y
 * aplicar lo que avisa el webhook.
 *
 * Toda operación bloquea primero la reserva y trabaja con {@code reserva.getPagos()}, de modo que
 * el cobro, el webhook, la expiración y los pagos en el club nunca se pisan.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PagoOnlineService {

    private final PaymentServiceFactory paymentServiceFactory;
    private final ReservaService reservaService;
    private final PagoRepository pagoRepository;
    private final ConfiguracionService configuracionService;
    private final UsuarioService usuarioService;
    private final EstadosDePago estados;
    private final Reloj reloj;
    private final PagoMapper pagoMapper;

    // ── 1. Iniciar un pago online ────────────────────────────────────────

    /**
     * Crea un Pago PENDIENTE y su preferencia en Mercado Pago.
     * pagarTotal = true cobra todo lo que falta; false cobra lo que falta para completar la seña.
     * Si ya hay un pago iniciado por el mismo monto lo devuelve (el usuario recargó la página).
     */
    @Transactional
    public PagoResponse iniciarPago(Long reservaId, Boolean pagarTotal, String email) {
        Reserva reserva = reservaService.bloquearConAcceso(reservaId, usuarioService.autenticado(email));
        if (reserva.esPartido()) {
            throw new EstadoInvalidoException("Los partidos se pagan en el club.");
        }
        estados.validarQueAdmitePagos(reserva);
        if (reserva.tienePagoEnProceso()) {
            throw new EstadoInvalidoException("Hay un pago en proceso para esta reserva; esperá a que se resuelva.");
        }

        BigDecimal monto = calcularMontoAPagar(reserva, Boolean.TRUE.equals(pagarTotal));

        Optional<Pago> iniciado = reserva.getPagos().stream()
                .filter(p -> p.getEstado() == EstadoPago.PENDIENTE)
                .findFirst();
        if (iniciado.isPresent() && iniciado.get().getMonto().compareTo(monto) == 0
                && iniciado.get().getPaymentToken() != null) {
            return pagoMapper.aResponse(iniciado.get());
        }
        // un pago iniciado por otro monto se descarta
        iniciado.ifPresent(p -> estados.cambiar(p, EstadoPago.EXPIRADO));

        Pago pago = Pago.builder()
                .monto(monto)
                .estado(EstadoPago.PENDIENTE)
                .provider(PaymentProvider.MERCADO_PAGO)
                .createdAt(reloj.ahora())
                .build();
        reserva.agregarPago(pago);
        pago = pagoRepository.save(pago);

        // si MP falla se lanza una excepción y la transacción no guarda nada
        pago.setPaymentToken(paymentServiceFactory.getService().crearPreferencia(pago));
        pago = pagoRepository.save(pago);

        log.info("Pago #{} iniciado para la reserva #{} por ${}", pago.getId(), reserva.getId(), monto);
        return pagoMapper.aResponse(pago);
    }

    BigDecimal calcularMontoAPagar(Reserva reserva, boolean pagarTotal) {
        Configuracion config = configuracionService.obtener();
        BigDecimal pagado = reserva.getMontoCubierto();
        BigDecimal pendiente = reserva.getMontoPendiente();

        if (pendiente.signum() <= 0) {
            throw new EstadoInvalidoException("La reserva ya está paga.");
        }
        if (pagarTotal) {
            return pendiente;
        }
        BigDecimal faltaSenia = configuracionService.calcularSenia(config, reserva.getMontoTotal()).subtract(pagado);
        if (faltaSenia.signum() <= 0) {
            throw new EstadoInvalidoException("La seña ya está paga; para pagar el saldo usá pagarTotal=true.");
        }
        return faltaSenia.min(pendiente);
    }

    // ── 2. Cobrar con el Brick ───────────────────────────────────────────

    @Transactional
    public PagoResponse procesarPago(Long reservaId, Map<String, Object> formData, String email) {
        Reserva reserva = reservaService.bloquearConAcceso(reservaId, usuarioService.autenticado(email));

        // con el lock tomado, un doble click espera y acá ya no encuentra el pago PENDIENTE
        Pago pago = reserva.getPagos().stream()
                .filter(p -> p.getEstado() == EstadoPago.PENDIENTE && p.getProvider() == PaymentProvider.MERCADO_PAGO)
                .findFirst()
                .orElseThrow(() -> new EstadoInvalidoException(
                        "No hay un pago iniciado para esta reserva. Iniciá el pago nuevamente."));
        estados.validarQueAdmitePagos(reserva);

        PaymentResult resultado = paymentServiceFactory.getService().cobrar(pago, formData);
        aplicarResultado(reserva, pago, resultado);

        PagoResponse response = pagoMapper.aResponse(pago);
        response.setDetalle(resultado.getDetalle());
        return response;
    }

    // ── 3. Webhook de Mercado Pago ───────────────────────────────────────

    /**
     * Nunca se confía en el contenido de la notificación: se consulta el pago a MP
     * y se aplica el resultado. Notificaciones repetidas o fuera de orden no cambian
     * un estado final.
     */
    @Transactional
    public void procesarNotificacion(String paymentId) {
        PaymentResult resultado = paymentServiceFactory.getService().consultarPago(paymentId);

        Optional<Long> reservaId = pagoRepository.findReservaIdByExternalPaymentId(paymentId)
                .or(() -> resultado.getPagoId() != null
                        ? pagoRepository.findReservaIdByPagoId(resultado.getPagoId())
                        : Optional.empty());
        if (reservaId.isEmpty()) {
            log.warn("Notificación del pago {} de MP sin Pago asociado (external_reference={})",
                    paymentId, resultado.getExternalReference());
            return;
        }

        Reserva reserva = reservaService.bloquear(reservaId.get());
        Optional<Pago> pago = reserva.getPagos().stream()
                .filter(p -> paymentId.equals(p.getExternalPaymentId()))
                .findFirst()
                .or(() -> reserva.getPagos().stream()
                        .filter(p -> p.getId().equals(resultado.getPagoId()))
                        .findFirst());
        if (pago.isEmpty()) {
            log.warn("Notificación del pago {} de MP: no se encontró el Pago en la reserva #{}", paymentId,
                    reserva.getId());
            return;
        }

        aplicarResultado(reserva, pago.get(), resultado);
    }

    // ── Resultado del proveedor ──────────────────────────────────────────

    /** Aplica el resultado del proveedor al pago y recalcula la reserva. Es idempotente. */
    void aplicarResultado(Reserva reserva, Pago pago, PaymentResult resultado) {
        EstadoPago actual = pago.getEstado();
        String paymentId = resultado.getPaymentId();
        boolean esOtroPagoDeMp = paymentId != null && pago.getExternalPaymentId() != null
                && !paymentId.equals(pago.getExternalPaymentId());

        switch (resultado.getEstado()) {
            case APROBADO -> {
                if (actual == EstadoPago.APROBADO || actual == EstadoPago.DEVUELTO
                        || actual == EstadoPago.A_REEMBOLSAR || actual == EstadoPago.REEMBOLSO_PENDIENTE) {
                    return; // notificación repetida
                }
                pago.setExternalPaymentId(paymentId);
                boolean montoCorrecto = resultado.getMonto() == null
                        || resultado.getMonto().compareTo(pago.getMonto()) == 0;
                if (!montoCorrecto) {
                    log.error("Pago #{}: MP cobró ${} pero se esperaba ${}. Queda A_REEMBOLSAR.",
                            pago.getId(), resultado.getMonto(), pago.getMonto());
                    pago.setMontoReembolsado(pago.getMonto());
                    estados.cambiar(pago, EstadoPago.A_REEMBOLSAR);
                    estados.avisarReembolsoManual(pago, reserva, "Mercado Pago cobró "
                            + Dinero.texto(resultado.getMonto()) + " y se esperaba " + Dinero.texto(pago.getMonto())
                            + ".");
                } else if (reserva.estaInactiva()) {
                    log.warn("Pago #{} aprobado para la reserva #{} en estado {}. Queda A_REEMBOLSAR: "
                            + "hay que devolver el dinero.", pago.getId(), reserva.getId(), reserva.getEstado());
                    pago.setMontoReembolsado(pago.getMonto());
                    estados.cambiar(pago, EstadoPago.A_REEMBOLSAR);
                    estados.avisarReembolsoManual(pago, reserva, "Se cobró un pago online de una reserva "
                            + reserva.getEstado() + ".");
                } else {
                    pago.setPaidAt(reloj.ahora());
                    estados.cambiar(pago, EstadoPago.APROBADO);
                }
            }
            case RECHAZADO -> {
                // un rechazo de otro intento no pisa un pago que está en curso con otro id
                if (!esOtroPagoDeMp && (actual == EstadoPago.PENDIENTE || actual == EstadoPago.EN_PROCESO)) {
                    pago.setExternalPaymentId(paymentId);
                    estados.cambiar(pago, EstadoPago.RECHAZADO);
                }
            }
            case EN_PROCESO -> {
                if (!esOtroPagoDeMp && actual == EstadoPago.PENDIENTE) {
                    pago.setExternalPaymentId(paymentId);
                    estados.cambiar(pago, EstadoPago.EN_PROCESO);
                }
            }
            case DEVUELTO -> {
                if (!esOtroPagoDeMp && (actual == EstadoPago.APROBADO || actual == EstadoPago.EN_PROCESO
                        || actual == EstadoPago.A_REEMBOLSAR || actual == EstadoPago.REEMBOLSO_PENDIENTE)) {
                    // devolución hecha fuera del sistema (panel de MP, contracargo): es por el total
                    if (pago.getMontoReembolsado() == null) {
                        pago.setMontoReembolsado(pago.getMonto());
                    }
                    estados.cambiar(pago, EstadoPago.DEVUELTO);
                    if (actual == EstadoPago.REEMBOLSO_PENDIENTE) {
                        estados.avisarReembolsoRealizado(reserva, pago.getMontoReembolsado());
                    }
                    log.warn("Pago #{} devuelto — reserva #{}", pago.getId(), reserva.getId());
                }
            }
        }
        estados.recalcularReserva(reserva);
    }
}
