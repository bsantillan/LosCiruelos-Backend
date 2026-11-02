package com.LosCiruelos.padel_club_api.pagos.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.LosCiruelos.padel_club_api.common.Dinero;
import com.LosCiruelos.padel_club_api.common.Fechas;
import com.LosCiruelos.padel_club_api.common.Reloj;
import com.LosCiruelos.padel_club_api.common.exception.EstadoInvalidoException;
import com.LosCiruelos.padel_club_api.common.exception.ReservaException;
import com.LosCiruelos.padel_club_api.mapper.PagoMapper;
import com.LosCiruelos.padel_club_api.pagos.dto.response.CobroQrResponse;
import com.LosCiruelos.padel_club_api.pagos.entity.Pago;
import com.LosCiruelos.padel_club_api.pagos.entity.enums.EstadoPago;
import com.LosCiruelos.padel_club_api.pagos.exception.CobroQrNoConfiguradoException;
import com.LosCiruelos.padel_club_api.pagos.exception.PagoNoEncontradoException;
import com.LosCiruelos.padel_club_api.pagos.payment.PaymentProvider;
import com.LosCiruelos.padel_club_api.pagos.payment.ProveedorCobroQr;
import com.LosCiruelos.padel_club_api.pagos.payment.ProveedorCobroQr.EstadoOrdenQr;
import com.LosCiruelos.padel_club_api.pagos.payment.ProveedorCobroQr.OrdenNoCancelableException;
import com.LosCiruelos.padel_club_api.pagos.payment.ProveedorCobroQr.OrdenQr;
import com.LosCiruelos.padel_club_api.pagos.repository.PagoRepository;
import com.LosCiruelos.padel_club_api.reservas.entity.Reserva;
import com.LosCiruelos.padel_club_api.reservas.service.ReservaService;
import com.LosCiruelos.padel_club_api.usuarios.entity.Usuario;
import com.LosCiruelos.padel_club_api.usuarios.service.UsuarioService;

import lombok.extern.slf4j.Slf4j;

/**
 * Cobro en el club con QR dinámico: el personal genera un QR por el monto exacto, el cliente lo
 * paga con su billetera y el pago se confirma solo (webhook o, si no llega, el job de respaldo).
 *
 * Mientras el QR está activo el pago queda EN_PROCESO, así que no se puede cobrar de otra forma,
 * cambiar los productos ni cancelar la reserva hasta que se pague, venza o se cancele: el saldo no
 * cambia con el QR en pantalla. Nunca se confía en lo que trae una notificación: siempre se consulta
 * la order al proveedor, con la reserva bloqueada, y un pago que ya no está EN_PROCESO no se toca.
 */
@Slf4j
@Service
public class CobroQrService {

    /** Margen después del vencimiento antes de que el job consulte la order. */
    static final int SEGUNDOS_DE_GRACIA = 60;

    private final ReservaService reservaService;
    private final PagoRepository pagoRepository;
    private final UsuarioService usuarioService;
    private final EstadosDePago estados;
    private final ProveedorCobroQr proveedor;
    private final PagoMapper pagoMapper;
    private final Reloj reloj;
    private final int minutosVencimiento;

    public CobroQrService(ReservaService reservaService, PagoRepository pagoRepository, UsuarioService usuarioService,
            EstadosDePago estados, ProveedorCobroQr proveedor, PagoMapper pagoMapper, Reloj reloj,
            @Value("${mercadopago.qr.minutos-vencimiento:10}") int minutosVencimiento) {
        this.reservaService = reservaService;
        this.pagoRepository = pagoRepository;
        this.usuarioService = usuarioService;
        this.estados = estados;
        this.proveedor = proveedor;
        this.pagoMapper = pagoMapper;
        this.reloj = reloj;
        this.minutosVencimiento = minutosVencimiento;
    }

    // ── El personal ──────────────────────────────────────────────────────

    /**
     * Genera el QR por {@code monto} (null: todo lo que falta). Si ya hay uno activo por el mismo
     * monto, devuelve ese; por otro monto, 409.
     */
    @Transactional
    public CobroQrResponse generar(Long reservaId, BigDecimal monto, String email) {
        if (!proveedor.estaConfigurado()) {
            throw new CobroQrNoConfiguradoException();
        }
        Usuario personal = usuarioService.autenticado(email);
        Reserva reserva = reservaService.bloquearConAcceso(reservaId, personal);
        LocalDateTime ahora = reloj.ahora();

        // un QR vencido que todavía no se resolvió (el job pasa cada minuto) se resuelve ahora
        reserva.getPagos().stream()
                .filter(p -> p.esCobroQr() && p.getEstado() == EstadoPago.EN_PROCESO && !p.esQrActivo(ahora))
                .toList()
                .forEach(p -> resolver(reserva, p));

        Optional<Pago> activo = qrActivo(reserva, ahora);
        BigDecimal aCobrar = monto != null ? monto : reserva.getMontoPendiente();
        if (activo.isPresent()) {
            if (activo.get().getMonto().compareTo(aCobrar) == 0) {
                return pagoMapper.aCobroQr(activo.get());
            }
            throw new EstadoInvalidoException("Ya hay un QR activo para esta reserva: cancelalo antes de generar otro.");
        }

        estados.validarQueAdmitePagos(reserva);
        if (reserva.tienePagoEnProceso()) {
            throw new EstadoInvalidoException(
                    "Hay un pago online en proceso para esta reserva; esperá a que se resuelva.");
        }
        if (aCobrar.signum() <= 0) {
            throw new EstadoInvalidoException("La reserva no tiene saldo pendiente.");
        }
        if (aCobrar.compareTo(reserva.getMontoPendiente()) > 0) {
            throw new ReservaException("El monto ($%s) supera el pendiente ($%s)."
                    .formatted(aCobrar, reserva.getMontoPendiente()));
        }
        // un pago online iniciado y sin cobrar ya no corresponde
        reserva.expirarPagosPendientes().forEach(pagoRepository::save);

        Pago pago = Pago.builder()
                .monto(aCobrar)
                .estado(EstadoPago.EN_PROCESO)
                .provider(PaymentProvider.MERCADO_PAGO)
                .createdAt(ahora)
                .venceAt(ahora.plusMinutes(minutosVencimiento))
                .registradoPorId(personal.getId())
                .build();
        reserva.agregarPago(pago);
        pago = pagoRepository.save(pago); // el id va en la referencia de la order

        // si el proveedor falla, la transacción se revierte y no queda el pago
        OrdenQr orden = proveedor.crear(pago, descripcion(reserva), minutosVencimiento);
        pago.setOrderId(orden.orderId());
        pago.setQrData(orden.qrData());
        log.info("QR #{} (order {}) por ${} para la reserva #{}, generado por {}", pago.getId(), orden.orderId(),
                aCobrar, reserva.getId(), email);
        return pagoMapper.aCobroQr(pago);
    }

    /** El último QR de la reserva, para que el modal muestre el estado (solo lee la base). */
    @Transactional(readOnly = true)
    public CobroQrResponse ultimo(Long reservaId, String email) {
        usuarioService.autenticado(email);
        return pagoRepository.findByReservaId(reservaId).stream()
                .filter(Pago::esCobroQr)
                .max(Comparator.comparing(Pago::getCreatedAt).thenComparing(Pago::getId))
                .map(pagoMapper::aCobroQr)
                .orElseThrow(() -> new PagoNoEncontradoException("La reserva no tiene un cobro con QR."));
    }

    /**
     * El personal cerró el modal: cancela la order. Si ya no se puede cancelar (se pagó o el pago
     * está en curso), se consulta y se aplica lo que diga el proveedor.
     */
    @Transactional
    public CobroQrResponse cancelar(Long reservaId, String email) {
        Usuario personal = usuarioService.autenticado(email);
        Reserva reserva = reservaService.bloquearConAcceso(reservaId, personal);
        Pago pago = reserva.getPagos().stream()
                .filter(p -> p.esCobroQr() && p.getEstado() == EstadoPago.EN_PROCESO)
                .findFirst()
                .orElseThrow(() -> new EstadoInvalidoException("No hay un QR activo para esta reserva."));
        try {
            proveedor.cancelar(pago);
            estados.cambiar(pago, EstadoPago.EXPIRADO);
            log.info("QR #{} (order {}) cancelado por {}", pago.getId(), pago.getOrderId(), email);
        } catch (OrdenNoCancelableException e) {
            log.info("La order {} ya no se puede cancelar; se consulta su estado", pago.getOrderId());
            resolver(reserva, pago);
        }
        return pagoMapper.aCobroQr(pago);
    }

    // ── Confirmación (webhook y job) ─────────────────────────────────────

    /** Notificación de una order (webhook): se consulta la order y se aplica. Es idempotente. */
    @Transactional
    public void notificacion(String orderId) {
        Optional<Long> reservaId = pagoRepository.findReservaIdByOrderId(orderId);
        if (reservaId.isEmpty()) {
            log.warn("Notificación de la order {} sin Pago asociado", orderId);
            return;
        }
        Reserva reserva = reservaService.bloquear(reservaId.get());
        reserva.getPagos().stream()
                .filter(p -> orderId.equals(p.getOrderId()) && p.getEstado() == EstadoPago.EN_PROCESO)
                .findFirst()
                .ifPresent(p -> resolver(reserva, p)); // si ya no está EN_PROCESO, es una notificación repetida
    }

    /** Los QR que siguen esperando y ya vencieron (con un margen): el job los revisa uno por uno. */
    public List<Long> idsQrVencidos() {
        return pagoRepository.findIdsQrVencidos(EstadoPago.EN_PROCESO,
                reloj.ahora().minusSeconds(SEGUNDOS_DE_GRACIA));
    }

    /** Revisa un QR vencido por si no llegó la notificación. @return el estado en que quedó */
    @Transactional
    public EstadoPago revisar(Long pagoId) {
        Long reservaId = pagoRepository.findReservaIdByPagoId(pagoId).orElse(null);
        if (reservaId == null) {
            return null;
        }
        Reserva reserva = reservaService.bloquear(reservaId);
        Pago pago = reserva.getPagos().stream().filter(p -> p.getId().equals(pagoId)).findFirst().orElse(null);
        if (pago == null || pago.getEstado() != EstadoPago.EN_PROCESO) {
            return pago != null ? pago.getEstado() : null;
        }
        resolver(reserva, pago);
        return pago.getEstado();
    }

    /** Consulta la order y aplica su estado al pago (que está EN_PROCESO y con la reserva bloqueada). */
    private void resolver(Reserva reserva, Pago pago) {
        EstadoOrdenQr orden = proveedor.consultar(pago.getOrderId());
        switch (orden.estado()) {
            case CREADA -> log.debug("La order {} sigue esperando el pago", pago.getOrderId());
            case PROCESADA -> aprobar(reserva, pago, orden.montoPagado());
            case CANCELADA, VENCIDA -> {
                estados.cambiar(pago, EstadoPago.EXPIRADO);
                log.info("QR #{} (order {}): {}", pago.getId(), pago.getOrderId(), orden.estado());
            }
            case DEVUELTA -> {
                // se cobró y se devolvió desde el panel de MP antes de que llegara la confirmación
                pago.setMontoReembolsado(pago.getMonto());
                estados.cambiar(pago, EstadoPago.DEVUELTO);
                log.warn("QR #{} (order {}) cobrado y devuelto desde Mercado Pago", pago.getId(), pago.getOrderId());
            }
        }
    }

    private void aprobar(Reserva reserva, Pago pago, BigDecimal montoPagado) {
        if (montoPagado != null && montoPagado.compareTo(pago.getMonto()) != 0) {
            log.error("QR #{} (order {}): MP cobró ${} pero se esperaba ${}. Queda A_REEMBOLSAR.", pago.getId(),
                    pago.getOrderId(), montoPagado, pago.getMonto());
            pago.setMontoReembolsado(pago.getMonto());
            estados.cambiar(pago, EstadoPago.A_REEMBOLSAR);
            estados.avisarReembolsoManual(pago, reserva, "Mercado Pago cobró " + Dinero.texto(montoPagado)
                    + " con el QR y se esperaba " + Dinero.texto(pago.getMonto()) + ".");
            return;
        }
        if (reserva.estaInactiva()) {
            log.warn("QR #{} cobrado para la reserva #{} en estado {}. Queda A_REEMBOLSAR.", pago.getId(),
                    reserva.getId(), reserva.getEstado());
            pago.setMontoReembolsado(pago.getMonto());
            estados.cambiar(pago, EstadoPago.A_REEMBOLSAR);
            estados.avisarReembolsoManual(pago, reserva, "Se cobró un QR de una reserva " + reserva.getEstado()
                    + ".");
            return;
        }
        estados.cambiar(pago, EstadoPago.APROBADO);
        estados.recalcularReserva(reserva);
        log.info("QR #{} (order {}) cobrado: ${} — reserva #{} queda {}", pago.getId(), pago.getOrderId(),
                pago.getMonto(), reserva.getId(), reserva.getEstado());
    }

    private static Optional<Pago> qrActivo(Reserva reserva, LocalDateTime ahora) {
        return reserva.getPagos().stream().filter(p -> p.esQrActivo(ahora)).findFirst();
    }

    /** Lo que ve el cliente en su billetera: sin datos personales. */
    private static String descripcion(Reserva reserva) {
        return "Los Ciruelos - cancha " + reserva.getCancha().getNumero() + " - "
                + Fechas.textoFecha(reserva.getFechaReserva()) + " " + Fechas.textoHora(reserva.getHoraInicio());
    }
}
