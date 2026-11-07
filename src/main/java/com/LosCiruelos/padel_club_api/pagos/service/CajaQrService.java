package com.LosCiruelos.padel_club_api.pagos.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.LosCiruelos.padel_club_api.common.Dinero;
import com.LosCiruelos.padel_club_api.common.Fechas;
import com.LosCiruelos.padel_club_api.pagos.entity.enums.EstadoPago;
import com.LosCiruelos.padel_club_api.pagos.repository.PagoRepository;

import lombok.RequiredArgsConstructor;

/**
 * La caja tiene un solo QR para las reservas y las ventas sueltas. Quien quiere ocuparla la bloquea
 * primero ({@link #bloquear}, antes que la reserva o la venta) y después revisa que no esté
 * esperando otro cobro. Así dos empleados no la ocupan a la vez y los locks se toman siempre en el
 * mismo orden.
 */
@Service
@RequiredArgsConstructor
public class CajaQrService {

    /** Lo que la caja está esperando cobrar: {@code que} es "la reserva #N" o "la venta #N". */
    public record CobroEnCaja(String que, BigDecimal monto, LocalDateTime venceAt) {
    }

    private final PagoRepository pagoRepository;

    /** Bloquea la caja hasta que termine la transacción. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void bloquear() {
        pagoRepository.bloquearCajaQr();
    }

    /** El cobro de una reserva que la caja está esperando, si todavía no venció. */
    public Optional<CobroEnCaja> cobroDeReservaEsperando(LocalDateTime ahora) {
        return pagoRepository.findQrEnCurso(EstadoPago.EN_PROCESO).stream()
                .filter(p -> p.getVenceAt().isAfter(ahora))
                .findFirst()
                .map(p -> new CobroEnCaja("la reserva #" + p.getReserva().getId(), p.getMonto(), p.getVenceAt()));
    }

    /** El mensaje del 409 cuando la caja está ocupada. */
    public static String ocupada(CobroEnCaja cobro) {
        return "El QR de la caja está esperando el pago de %s de %s hasta las %s: esperá a que se pague o cancelalo."
                .formatted(Dinero.texto(cobro.monto()), cobro.que(), Fechas.textoHora(cobro.venceAt().toLocalTime()));
    }
}
