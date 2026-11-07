package com.LosCiruelos.padel_club_api.ventas.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.LosCiruelos.padel_club_api.club.service.ProductoService;
import com.LosCiruelos.padel_club_api.common.Reloj;
import com.LosCiruelos.padel_club_api.common.exception.EstadoInvalidoException;
import com.LosCiruelos.padel_club_api.pagos.exception.CobroQrNoConfiguradoException;
import com.LosCiruelos.padel_club_api.pagos.exception.PagoProveedorException;
import com.LosCiruelos.padel_club_api.pagos.payment.OrdenQr;
import com.LosCiruelos.padel_club_api.pagos.payment.ProveedorCobroQr;
import com.LosCiruelos.padel_club_api.pagos.payment.ProveedorCobroQr.EstadoOrdenQr;
import com.LosCiruelos.padel_club_api.pagos.payment.ProveedorCobroQr.OrdenNoCancelableException;
import com.LosCiruelos.padel_club_api.pagos.service.CajaQrService;
import com.LosCiruelos.padel_club_api.pagos.service.CajaQrService.CobroEnCaja;
import com.LosCiruelos.padel_club_api.ventas.entity.Venta;
import com.LosCiruelos.padel_club_api.ventas.entity.enums.EstadoVenta;
import com.LosCiruelos.padel_club_api.ventas.exception.ReembolsoQrException;
import com.LosCiruelos.padel_club_api.ventas.repository.VentaRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * El cobro de una venta suelta con el QR de la caja, que comparten {@link VentaService} (registrar,
 * anular y devolver) y {@link VentaQrService} (cancelar, webhook y job). Nunca se confía en una
 * notificación: siempre se consulta la order, con la venta bloqueada.
 */
@Slf4j
@Component
class CobroQrDeVentas {

    static final String CAJA_OCUPADA = "El QR de la caja está esperando otro cobro: esperá a que se pague o cancelalo.";

    private final ProveedorCobroQr proveedor;
    private final CajaQrService caja;
    private final VentaRepository ventaRepository;
    private final ProductoService productoService;
    private final Reloj reloj;
    private final int minutosVencimiento;

    CobroQrDeVentas(ProveedorCobroQr proveedor, CajaQrService caja, VentaRepository ventaRepository,
            ProductoService productoService, Reloj reloj,
            @Value("${mercadopago.qr.minutos-vencimiento:10}") int minutosVencimiento) {
        this.proveedor = proveedor;
        this.caja = caja;
        this.ventaRepository = ventaRepository;
        this.productoService = productoService;
        this.reloj = reloj;
        this.minutosVencimiento = minutosVencimiento;
    }

    void exigirConfigurado() {
        if (!proveedor.estaConfigurado()) {
            throw new CobroQrNoConfiguradoException();
        }
    }

    /**
     * Bloquea la caja y falla (409) si está esperando otro cobro: el de una reserva o el de otra venta.
     * Una venta que ya venció y el job todavía no resolvió se resuelve ahora.
     */
    void ocuparCaja() {
        caja.bloquear();
        LocalDateTime ahora = reloj.ahora();
        caja.cobroDeReservaEsperando(ahora).ifPresent(c -> {
            throw new EstadoInvalidoException(CajaQrService.ocupada(c));
        });
        for (Venta otra : ventaRepository.findByEstado(EstadoVenta.ESPERANDO_PAGO)) {
            if (otra.esQrActivo(ahora)) {
                throw new EstadoInvalidoException(CajaQrService.ocupada(enCaja(otra)));
            }
            Venta bloqueada = ventaRepository.findByIdForUpdate(otra.getId()).orElse(otra);
            if (bloqueada.esperaElPago()) {
                resolver(bloqueada);
            }
            if (bloqueada.esperaElPago()) {
                throw new EstadoInvalidoException(CAJA_OCUPADA);
            }
        }
    }

    static CobroEnCaja enCaja(Venta venta) {
        return new CobroEnCaja("la venta #" + venta.getId(), venta.getTotal(), venta.getVenceAt());
    }

    LocalDateTime vencimiento() {
        return reloj.ahora().plusMinutes(minutosVencimiento);
    }

    /** Le asigna a la caja una order por el total de la venta (ya guardada: su id va en la referencia). */
    void asignar(Venta venta) {
        String orderId = proveedor.crear(orden(venta), "Los Ciruelos - venta #" + venta.getId(), minutosVencimiento);
        venta.setOrderId(orderId);
        log.info("QR de la caja (order {}) por ${} para la venta #{}", orderId, venta.getTotal(), venta.getId());
    }

    /** El personal cerró el modal: cancela la order; si ya no se puede, se aplica lo que diga el proveedor. */
    void cancelar(Venta venta) {
        try {
            proveedor.cancelar(orden(venta));
            noCobrada(venta);
            log.info("QR de la venta #{} (order {}) cancelado", venta.getId(), venta.getOrderId());
        } catch (OrdenNoCancelableException e) {
            log.info("La order {} ya no se puede cancelar; se consulta su estado", venta.getOrderId());
            resolver(venta);
        }
    }

    /** Consulta la order y aplica su estado a la venta (que espera el pago y está bloqueada). */
    void resolver(Venta venta) {
        EstadoOrdenQr orden = proveedor.consultar(venta.getOrderId());
        switch (orden.estado()) {
            case CREADA -> log.debug("La order {} sigue esperando el pago", venta.getOrderId());
            case PROCESADA -> {
                if (orden.montoPagado() != null && orden.montoPagado().compareTo(venta.getTotal()) != 0) {
                    log.error("Venta #{} (order {}): MP cobró ${} pero se esperaba ${}. Queda NO_COBRADA: hay que "
                            + "devolver lo cobrado desde el panel de Mercado Pago.", venta.getId(), venta.getOrderId(),
                            orden.montoPagado(), venta.getTotal());
                    noCobrada(venta);
                    return;
                }
                venta.confirmarCobro();
                log.info("Venta #{} (order {}) cobrada con QR: ${}", venta.getId(), venta.getOrderId(),
                        venta.getTotal());
            }
            case CANCELADA, VENCIDA, DEVUELTA -> {
                noCobrada(venta);
                log.info("QR de la venta #{} (order {}): {}", venta.getId(), venta.getOrderId(), orden.estado());
            }
        }
    }

    /**
     * Devuelve {@code monto} de una venta cobrada con QR por la API del proveedor. {@code operacion}
     * distingue cada reembolso (la anulación o cada devolución) en su clave de idempotencia.
     *
     * @throws ReembolsoQrException (502) si el proveedor no lo acepta
     */
    void reembolsar(Venta venta, BigDecimal monto, String operacion) {
        OrdenQr orden = new OrdenQr(venta.getOrderId(), referencia(venta),
                venta.getClaveIdempotencia() + "-" + operacion, venta.getTotal());
        try {
            proveedor.reembolsar(orden, monto);
            log.info("Venta #{}: Mercado Pago devolvió ${} (order {}, {})", venta.getId(), monto, venta.getOrderId(),
                    operacion);
        } catch (PagoProveedorException e) { // también ReembolsoFallidoException
            log.error("Venta #{}: Mercado Pago no devolvió ${} (order {})", venta.getId(), monto, venta.getOrderId());
            throw new ReembolsoQrException();
        }
    }

    private void noCobrada(Venta venta) {
        venta.marcarNoCobrada();
        productoService.reponerVentaSuelta(venta.cantidades());
    }

    private static OrdenQr orden(Venta venta) {
        return new OrdenQr(venta.getOrderId(), referencia(venta), venta.getClaveIdempotencia().toString(),
                venta.getTotal());
    }

    /** Sin datos personales: solo el id. */
    private static String referencia(Venta venta) {
        return "venta-" + venta.getId();
    }
}
