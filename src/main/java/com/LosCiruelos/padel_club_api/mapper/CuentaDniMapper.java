package com.LosCiruelos.padel_club_api.mapper;

import org.springframework.stereotype.Component;

import com.LosCiruelos.padel_club_api.common.Dinero;
import com.LosCiruelos.padel_club_api.pagos.dto.response.CobroCuentaDniResponse;
import com.LosCiruelos.padel_club_api.pagos.dto.response.CobroCuentaDniResponse.Origen;
import com.LosCiruelos.padel_club_api.pagos.entity.Pago;
import com.LosCiruelos.padel_club_api.ventas.entity.Venta;

/** Pago de una reserva o venta suelta con Cuenta DNI → CobroCuentaDniResponse. */
@Component
public class CuentaDniMapper {

    public CobroCuentaDniResponse aResponse(Pago pago) {
        return new CobroCuentaDniResponse(Origen.RESERVA, pago.getId(), pago.getReserva().getId(),
                pago.getProvider() == com.LosCiruelos.padel_club_api.pagos.payment.PaymentProvider.TRANSFERENCIA_MP
                        ? com.LosCiruelos.padel_club_api.ventas.entity.enums.MedioDePago.TRANSFERENCIA_MP
                        : com.LosCiruelos.padel_club_api.ventas.entity.enums.MedioDePago.CUENTA_DNI,
                Dinero.redondear(pago.getMonto()), pago.getPaidAt(), pago.estaVerificado(), pago.getVerificadoAt(),
                false);
    }

    public CobroCuentaDniResponse aResponse(Venta venta) {
        return new CobroCuentaDniResponse(Origen.VENTA, venta.getId(), null, venta.getMedio(),
                Dinero.redondear(venta.getTotal()),
                venta.getCreatedAt(), venta.estaVerificada(), venta.getVerificadoAt(), venta.estaAnulada());
    }
}
