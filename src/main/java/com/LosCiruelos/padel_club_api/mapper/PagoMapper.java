package com.LosCiruelos.padel_club_api.mapper;

import org.springframework.stereotype.Component;

import com.LosCiruelos.padel_club_api.common.Dinero;
import com.LosCiruelos.padel_club_api.pagos.dto.response.CobroQrResponse;
import com.LosCiruelos.padel_club_api.pagos.dto.response.PagoResponse;
import com.LosCiruelos.padel_club_api.pagos.entity.Pago;
import com.LosCiruelos.padel_club_api.pagos.payment.PaymentProvider;

/** Pago → PagoResponse y, si es un cobro con QR, CobroQrResponse. */
@Component
public class PagoMapper {

    /** Un medio de cobro de una reserva, con el endpoint para cobrar con él. */
    public com.LosCiruelos.padel_club_api.pagos.dto.response.MedioDeCobroResponse aMedioDeCobro(
            com.LosCiruelos.padel_club_api.ventas.entity.enums.MedioDePago medio, boolean qrConfigurado) {
        boolean disponible = !medio.conQr() || qrConfigurado;
        String ruta = switch (medio) {
            case EFECTIVO -> "/admin/reservas/{id}/cobros/efectivo";
            case MERCADO_PAGO -> "/admin/reservas/{id}/cobros/qr";
            case TRANSFERENCIA_MP -> "/admin/reservas/{id}/cobros/transferencia";
            case CUENTA_DNI -> "/admin/reservas/{id}/cobros/cuenta-dni";
        };
        return new com.LosCiruelos.padel_club_api.pagos.dto.response.MedioDeCobroResponse(medio, medio.etiqueta(),
                disponible, medio.conQr(), disponible ? null : "El QR de la caja de Mercado Pago no está configurado.",
                "POST", ruta);
    }

    public CobroQrResponse aCobroQr(Pago pago) {
        return new CobroQrResponse(pago.getId(), pago.getOrderId(), Dinero.redondear(pago.getMonto()),
                pago.getVenceAt(), pago.getEstado(), pago.getReserva().getEstado(),
                Dinero.redondear(pago.getReserva().getMontoPendiente()));
    }

    public PagoResponse aResponse(Pago pago) {
        PagoResponse response = new PagoResponse();
        response.setId(pago.getId());
        response.setReservaId(pago.getReserva().getId());
        response.setMonto(pago.getMonto());
        response.setEstado(pago.getEstado());
        response.setProvider(pago.getProvider());
        response.setCanal(pago.canal());
        response.setPaymentToken(pago.getPaymentToken());
        response.setExternalPaymentId(pago.getExternalPaymentId());
        response.setCreatedAt(pago.getCreatedAt());
        response.setPaidAt(pago.getPaidAt());
        response.setMontoReembolsado(pago.getMontoReembolsado());
        response.setMotivo(pago.getMotivo());
        if (pago.getProvider().seVerifica()) {
            response.setVerificado(pago.estaVerificado());
            response.setVerificadoAt(pago.getVerificadoAt());
        }
        response.setEstadoReserva(pago.getReserva().getEstado());
        return response;
    }
}
