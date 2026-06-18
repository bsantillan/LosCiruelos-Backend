package com.LosCiruelos.padel_club_api.Jobs;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.LosCiruelos.padel_club_api.Entities.Reserva;
import com.LosCiruelos.padel_club_api.Entities.Enum.EstadoReserva;
import com.LosCiruelos.padel_club_api.Services.ReservaExpirationService;
import com.LosCiruelos.padel_club_api.Services.ReservaService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReservaExpirationJob {

    private final ReservaService reservaService;
    private final ReservaExpirationService reservaExpirationService;

    // cada 5 minutos
    @Scheduled(fixedDelay = 300000)
    public void expirarReservasVencidas() {
        List<Reserva> vencidas = reservaService.findByEstadoAndExpiresAtBefore(
                EstadoReserva.PENDIENTE, LocalDateTime.now());

        if (vencidas.isEmpty()) {
            log.info("Job: No hay nada que expirar");
            return;
        }

        log.info("Job: expirando {} reservas vencidas", vencidas.size());
        vencidas.forEach(r -> reservaExpirationService.expirarReserva(r.getId()));
    }
}
