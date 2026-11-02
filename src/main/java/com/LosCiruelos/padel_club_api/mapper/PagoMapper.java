package com.LosCiruelos.padel_club_api.mapper;

import org.springframework.stereotype.Component;

import com.LosCiruelos.padel_club_api.common.Dinero;
import com.LosCiruelos.padel_club_api.pagos.dto.response.CobroQrResponse;
import com.LosCiruelos.padel_club_api.pagos.dto.response.PagoResponse;
import com.LosCiruelos.padel_club_api.pagos.entity.Pago;

/** Pago → PagoResponse y, si es un cobro con QR, CobroQrResponse. */
@Component
public class PagoMapper {

    public CobroQrResponse aCobroQr(Pago pago) {
        return new CobroQrResponse(pago.getId(), pago.getOrderId(), pago.getQrData(), Dinero.redondear(pago.getMonto()),
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
        response.setPaymentToken(pago.getPaymentToken());
        response.setExternalPaymentId(pago.getExternalPaymentId());
        response.setCreatedAt(pago.getCreatedAt());
        response.setPaidAt(pago.getPaidAt());
        response.setMontoReembolsado(pago.getMontoReembolsado());
        response.setMotivo(pago.getMotivo());
        response.setComprobante(pago.getComprobante());
        response.setEstadoReserva(pago.getReserva().getEstado());
        return response;
    }
}
