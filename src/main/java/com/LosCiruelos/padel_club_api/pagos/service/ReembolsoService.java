package com.LosCiruelos.padel_club_api.pagos.service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.LosCiruelos.padel_club_api.pagos.exception.PagoNoEncontradoException;
import com.LosCiruelos.padel_club_api.pagos.exception.PagoProveedorException;
import com.LosCiruelos.padel_club_api.pagos.exception.ReembolsoFallidoException;
import com.LosCiruelos.padel_club_api.common.Dinero;
import com.LosCiruelos.padel_club_api.common.Reloj;
import com.LosCiruelos.padel_club_api.pagos.entity.Pago;
import com.LosCiruelos.padel_club_api.pagos.entity.enums.EstadoPago;
import com.LosCiruelos.padel_club_api.pagos.payment.PaymentService;
import com.LosCiruelos.padel_club_api.pagos.payment.ProveedorCobroQr;
import com.LosCiruelos.padel_club_api.pagos.payment.ProveedorCobroQr.EstadoOrden;
import com.LosCiruelos.padel_club_api.pagos.payment.PaymentServiceFactory;
import com.LosCiruelos.padel_club_api.pagos.repository.PagoRepository;
import com.LosCiruelos.padel_club_api.reservas.entity.Reserva;
import com.LosCiruelos.padel_club_api.reservas.service.ReservaService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Reembolsos de lo pagado cuando se cancela una reserva: por API de Mercado Pago lo cobrado online
 * y con QR (con reintentos si MP no lo acepta en el momento) y a mano lo cobrado en el club en
 * efectivo, con Cuenta DNI o cargado a mano.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReembolsoService {

    /** Pasado este plazo sin poder reembolsar por API, el pago se devuelve a mano. */
    static final Duration PLAZO_REINTENTO_REEMBOLSO = Duration.ofHours(24);

    private final PaymentServiceFactory paymentServiceFactory;
    private final ProveedorCobroQr proveedorQr;
    private final ReservaService reservaService;
    private final PagoRepository pagoRepository;
    private final EstadosDePago estados;
    private final Reloj reloj;

    /**
     * Reparte {@code monto} entre los pagos aprobados de la reserva (ya bloqueada):
     * primero los cobrados online por Mercado Pago, que se devuelven por API y quedan DEVUELTO;
     * después los cobrados en el club, que quedan A_REEMBOLSAR para que el personal los devuelva.
     * Cada pago guarda en montoReembolsado cuánto se devolvió de él.
     *
     * Si MP no acepta un reembolso (ej. el pago recién cobrado todavía se está acreditando),
     * el pago queda REEMBOLSO_PENDIENTE y lo reintenta {@link #reintentarReembolso}: la
     * cancelación sigue adelante igual.
     */
    public void reembolsar(Reserva reserva, BigDecimal monto) {
        BigDecimal restante = monto;
        List<Pago> aprobados = reserva.getPagos().stream()
                .filter(p -> p.getEstado() == EstadoPago.APROBADO && !p.esBonificacion()) // no es plata
                .sorted(Comparator.comparing((Pago p) -> !p.seDevuelvePorApi())
                        .thenComparing(Pago::getCreatedAt, Comparator.reverseOrder()))
                .toList();

        for (Pago pago : aprobados) {
            if (restante.signum() <= 0) {
                break;
            }
            BigDecimal aDevolver = restante.min(pago.getMontoRetenido());
            if (aDevolver.signum() <= 0) {
                continue;
            }
            pago.setMontoReembolsado(aDevolver);
            if (pago.seDevuelvePorApi()) {
                try {
                    devolverPorApi(pago, aDevolver);
                    estados.cambiar(pago, EstadoPago.DEVUELTO);
                } catch (ReembolsoFallidoException e) {
                    pago.setReembolsoSolicitadoAt(reloj.ahora());
                    if (e.isDefinitivo()) {
                        // el aviso de la cancelación ya dice lo que hay que devolver a mano
                        pasarAReembolsoManual(pago, reserva, null);
                    } else {
                        estados.cambiar(pago, EstadoPago.REEMBOLSO_PENDIENTE);
                        log.warn("Pago #{}: MP no aceptó el reembolso de ${}; queda REEMBOLSO_PENDIENTE "
                                + "para reintentarlo (reserva #{})", pago.getId(), aDevolver, reserva.getId());
                    }
                }
            } else {
                estados.cambiar(pago, EstadoPago.A_REEMBOLSAR);
                log.warn("Pago #{} cobrado en el club: hay que devolver ${} a mano (reserva #{})", pago.getId(),
                        aDevolver, reserva.getId());
            }
            restante = restante.subtract(aDevolver);
        }
    }

    /** Ids de los pagos con un reembolso de MP por reintentar. */
    public List<Long> idsReembolsosPendientes() {
        return pagoRepository.findIdsByEstado(EstadoPago.REEMBOLSO_PENDIENTE);
    }

    /**
     * Reintenta el reembolso de un pago REEMBOLSO_PENDIENTE, con la reserva bloqueada y la misma
     * clave de idempotencia. Antes consulta el pago en MP, por si el reembolso ya se hizo (un
     * intento anterior que respondió con error, o una devolución desde el panel de MP).
     * <ul>
     * <li>se pudo reembolsar, o MP ya lo tiene devuelto → DEVUELTO;</li>
     * <li>falla que no se arregla reintentando, o pasaron {@link #PLAZO_REINTENTO_REEMBOLSO}
     * → A_REEMBOLSAR (a devolver a mano) y log.error;</li>
     * <li>si no, sigue REEMBOLSO_PENDIENTE para el próximo intento.</li>
     * </ul>
     *
     * @return el estado en que quedó el pago
     */
    @Transactional
    public EstadoPago reintentarReembolso(Long pagoId) {
        Long reservaId = pagoRepository.findReservaIdByPagoId(pagoId).orElse(null);
        if (reservaId == null) {
            return null;
        }
        Reserva reserva = reservaService.bloquear(reservaId);
        Pago pago = reserva.getPagos().stream()
                .filter(p -> p.getId().equals(pagoId))
                .findFirst()
                .orElse(null);
        if (pago == null || pago.getEstado() != EstadoPago.REEMBOLSO_PENDIENTE) {
            return pago != null ? pago.getEstado() : null; // otro proceso ya lo resolvió (ej. el webhook)
        }

        BigDecimal aDevolver = pago.getMontoReembolsado() != null ? pago.getMontoReembolsado() : pago.getMonto();
        if (pago.esCobroQr()) {
            return reintentarReembolsoQr(pago, reserva, aDevolver);
        }
        PaymentService proveedor = paymentServiceFactory.getService();
        try {
            BigDecimal yaDevuelto = proveedor.consultarPago(pago.getExternalPaymentId()).getMontoDevuelto();
            if (yaDevuelto != null && yaDevuelto.signum() > 0) {
                if (yaDevuelto.compareTo(aDevolver) == 0) {
                    estados.cambiar(pago, EstadoPago.DEVUELTO);
                    log.info("Pago #{}: MP ya tenía devueltos ${}; queda DEVUELTO", pago.getId(), yaDevuelto);
                    estados.avisarReembolsoRealizado(reserva, aDevolver);
                } else {
                    // alguien devolvió otro monto desde el panel: que lo revise una persona
                    log.error("Pago #{}: MP tiene devueltos ${} pero había que devolver ${}", pago.getId(),
                            yaDevuelto, aDevolver);
                    pasarAReembolsoManual(pago, reserva, "Mercado Pago tiene devueltos " + Dinero.texto(yaDevuelto)
                            + " y había que devolver " + Dinero.texto(aDevolver) + ". Revisalo en el panel de MP.");
                }
                return pago.getEstado();
            }

            proveedor.reembolsar(pago, aDevolver);
            estados.cambiar(pago, EstadoPago.DEVUELTO);
            estados.avisarReembolsoRealizado(reserva, aDevolver);
            log.info("Pago #{}: reembolso de ${} hecho en el reintento (reserva #{})", pago.getId(), aDevolver,
                    reserva.getId());
        } catch (ReembolsoFallidoException | PagoNoEncontradoException e) {
            boolean definitivo = e instanceof PagoNoEncontradoException
                    || ((ReembolsoFallidoException) e).isDefinitivo();
            if (definitivo || plazoDeReintentoVencido(pago)) {
                pasarAReembolsoManual(pago, reserva, "Mercado Pago no aceptó el reembolso (pago MP "
                        + pago.getExternalPaymentId() + ").");
            }
        } catch (PagoProveedorException e) {
            // MP no respondió a la consulta: se reintenta en el próximo ciclo
            if (plazoDeReintentoVencido(pago)) {
                pasarAReembolsoManual(pago, reserva, "Mercado Pago no respondió durante 24 h (pago MP "
                        + pago.getExternalPaymentId() + ").");
            }
        }
        return pago.getEstado();
    }

    /** Como {@link #reintentarReembolso} para un cobro con QR: se consulta la order antes de reintentar. */
    private EstadoPago reintentarReembolsoQr(Pago pago, Reserva reserva, BigDecimal aDevolver) {
        try {
            if (proveedorQr.consultar(pago.getOrderId()).estado() == EstadoOrden.DEVUELTA
                    && aDevolver.compareTo(pago.getMonto()) == 0) {
                estados.cambiar(pago, EstadoPago.DEVUELTO);
                log.info("QR #{}: MP ya tenía devuelta la order {}; queda DEVUELTO", pago.getId(), pago.getOrderId());
                estados.avisarReembolsoRealizado(reserva, aDevolver);
                return pago.getEstado();
            }
            proveedorQr.reembolsar(pago, aDevolver);
            estados.cambiar(pago, EstadoPago.DEVUELTO);
            estados.avisarReembolsoRealizado(reserva, aDevolver);
            log.info("QR #{}: reembolso de ${} hecho en el reintento (reserva #{})", pago.getId(), aDevolver,
                    reserva.getId());
        } catch (ReembolsoFallidoException e) {
            if (e.isDefinitivo() || plazoDeReintentoVencido(pago)) {
                pasarAReembolsoManual(pago, reserva, "Mercado Pago no aceptó el reembolso (order "
                        + pago.getOrderId() + ").");
            }
        } catch (PagoProveedorException e) {
            if (plazoDeReintentoVencido(pago)) {
                pasarAReembolsoManual(pago, reserva, "Mercado Pago no respondió durante 24 h (order "
                        + pago.getOrderId() + ").");
            }
        }
        return pago.getEstado();
    }

    /** Un pago online se devuelve con la API de pagos; un QR, con la de orders. */
    private void devolverPorApi(Pago pago, BigDecimal monto) {
        if (pago.esCobroQr()) {
            proveedorQr.reembolsar(pago, monto);
        } else {
            paymentServiceFactory.getService().reembolsar(pago, monto);
        }
    }

    private boolean plazoDeReintentoVencido(Pago pago) {
        LocalDateTime desde = pago.getReembolsoSolicitadoAt() != null ? pago.getReembolsoSolicitadoAt()
                : pago.getCreatedAt();
        return !reloj.ahora().isBefore(desde.plus(PLAZO_REINTENTO_REEMBOLSO));
    }

    /** @param motivoAviso si no es null, se avisa a los ADMIN con ese motivo */
    private void pasarAReembolsoManual(Pago pago, Reserva reserva, String motivoAviso) {
        estados.cambiar(pago, EstadoPago.A_REEMBOLSAR);
        log.error("Pago #{} (MP {}): no se pudo reembolsar por Mercado Pago. Queda A_REEMBOLSAR: hay que "
                + "devolver ${} a mano (reserva #{})", pago.getId(), pago.getExternalPaymentId(),
                pago.getMontoReembolsado(), reserva.getId());
        if (motivoAviso != null) {
            estados.avisarReembolsoManual(pago, reserva, motivoAviso);
        }
    }
}
