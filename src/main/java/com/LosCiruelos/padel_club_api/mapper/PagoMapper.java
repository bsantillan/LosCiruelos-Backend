package com.LosCiruelos.padel_club_api.mapper;

import org.springframework.stereotype.Component;

import com.LosCiruelos.padel_club_api.pagos.dto.response.PagoResponse;
import com.LosCiruelos.padel_club_api.pagos.entity.Pago;

/** Pago → PagoResponse. */
@Component
public class PagoMapper {

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
        response.setJugadorId(pago.getJugador() != null ? pago.getJugador().getId() : null);
        response.setEstadoReserva(pago.getReserva().getEstado());
        return response;
    }
}
