package com.LosCiruelos.padel_club_api.ventas.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.LosCiruelos.padel_club_api.common.Reloj;
import com.LosCiruelos.padel_club_api.common.exception.EstadoInvalidoException;
import com.LosCiruelos.padel_club_api.mapper.VentaMapper;
import com.LosCiruelos.padel_club_api.pagos.service.CajaQrService.CobroEnCaja;
import com.LosCiruelos.padel_club_api.usuarios.service.UsuarioService;
import com.LosCiruelos.padel_club_api.ventas.dto.response.VentaResponse;
import com.LosCiruelos.padel_club_api.ventas.entity.Venta;
import com.LosCiruelos.padel_club_api.ventas.entity.enums.EstadoVenta;
import com.LosCiruelos.padel_club_api.ventas.exception.VentaNoEncontradaException;
import com.LosCiruelos.padel_club_api.ventas.repository.VentaRepository;

import lombok.RequiredArgsConstructor;

/**
 * Las ventas que esperan el pago con el QR de la caja: el personal cancela el QR, y el webhook o
 * el job de respaldo confirman el cobro. Cada una se resuelve con la venta bloqueada y una sola vez.
 */
@Service
@RequiredArgsConstructor
public class VentaQrService {

    /** Margen después del vencimiento antes de que el job consulte la order. */
    static final int SEGUNDOS_DE_GRACIA = 60;

    private final VentaRepository ventaRepository;
    private final CobroQrDeVentas cobroQr;
    private final UsuarioService usuarioService;
    private final VentaMapper ventaMapper;
    private final Reloj reloj;

    /** El personal cerró el modal sin que se pagara: la venta queda NO_COBRADA y el stock vuelve. */
    @Transactional
    public VentaResponse cancelar(Long ventaId, String email) {
        usuarioService.autenticado(email);
        Venta venta = bloquear(ventaId);
        if (!venta.esperaElPago()) {
            throw new EstadoInvalidoException("La venta no está esperando el pago con QR.");
        }
        cobroQr.cancelar(venta);
        return ventaMapper.aResponse(venta);
    }

    /** La venta que la caja está esperando cobrar, si su QR no venció (para el cobro con QR de una reserva). */
    public Optional<CobroEnCaja> ventaEsperando(LocalDateTime ahora) {
        return ventaRepository.findByEstado(EstadoVenta.ESPERANDO_PAGO).stream()
                .filter(v -> v.esQrActivo(ahora))
                .findFirst()
                .map(CobroQrDeVentas::enCaja);
    }

    /**
     * Notificación de una order (webhook). Es idempotente: una venta que ya no espera el pago no se toca.
     *
     * @return false si la order no es de ninguna venta
     */
    @Transactional
    public boolean notificacion(String orderId) {
        Optional<Long> ventaId = ventaRepository.findIdByOrderId(orderId);
        if (ventaId.isEmpty()) {
            return false;
        }
        Venta venta = bloquear(ventaId.get());
        if (venta.esperaElPago()) {
            cobroQr.resolver(venta);
        }
        return true;
    }

    /** Las ventas con QR que siguen esperando y ya vencieron (con un margen): el job las revisa una por una. */
    public List<Long> idsQrVencidas() {
        return ventaRepository.findIdsQrVencidas(EstadoVenta.ESPERANDO_PAGO,
                reloj.ahora().minusSeconds(SEGUNDOS_DE_GRACIA));
    }

    /** Revisa una venta con el QR vencido por si no llegó la notificación. @return el estado en que quedó */
    @Transactional
    public EstadoVenta revisar(Long ventaId) {
        Venta venta = ventaRepository.findByIdForUpdate(ventaId).orElse(null);
        if (venta == null) {
            return null;
        }
        if (venta.esperaElPago()) {
            cobroQr.resolver(venta);
        }
        return venta.getEstado();
    }

    private Venta bloquear(Long ventaId) {
        return ventaRepository.findByIdForUpdate(ventaId).orElseThrow(() -> new VentaNoEncontradaException(ventaId));
    }
}
