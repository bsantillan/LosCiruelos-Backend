package com.LosCiruelos.padel_club_api.Services;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;

import com.LosCiruelos.padel_club_api.Entities.Reserva;
import com.LosCiruelos.padel_club_api.Entities.Enum.EstadoPago;
import com.LosCiruelos.padel_club_api.Entities.Enum.EstadoReserva;
import com.LosCiruelos.padel_club_api.Exceptions.ReservaException;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReservaExpirationService {

    private final ReservaService reservaService;
    private final PagoService pagoService;

    @Transactional
    public void expirarReserva(Long id) {
        Reserva reserva = reservaService.findByIdOrThrow(id,
                new ReservaException("Reserva no encontrada"));

        if (reserva.getEstado() != EstadoReserva.PENDIENTE) {
            throw new ReservaException("Solo se pueden expirar reservas PENDIENTE.");
        }

        boolean tienePagoActivo = pagoService.findByReservaId(id)
                .stream()
                .anyMatch(p -> p.getEstado() == EstadoPago.EN_PROCESO
                        || p.getEstado() == EstadoPago.APROBADO);

        if (tienePagoActivo) {
            log.info("Reserva #{} no expirada — tiene un pago activo", id);
            return;
        }

        if (reserva.getExpiresAt().isAfter(LocalDateTime.now())) {
            throw new ReservaException("La reserva todavía no expiró.");
        }

        reserva.setEstado(EstadoReserva.EXPIRADA);
        reservaService.save(reserva);

        pagoService.findByReservaId(id)
                .stream()
                .filter(p -> p.getEstado() == EstadoPago.PENDIENTE)
                .forEach(p -> {
                    p.setEstado(EstadoPago.EXPIRADO);
                    pagoService.save(p);
                });

        log.info("Reserva #{} expirada", id);
    }
}
