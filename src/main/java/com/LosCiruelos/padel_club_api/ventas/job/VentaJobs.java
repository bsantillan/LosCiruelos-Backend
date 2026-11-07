package com.LosCiruelos.padel_club_api.ventas.job;

import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.LosCiruelos.padel_club_api.ventas.service.VentaQrService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Tareas programadas de las ventas sueltas: solo llaman a los services. */
@Slf4j
@Component
@RequiredArgsConstructor
public class VentaJobs {

    private final VentaQrService ventaQrService;

    /**
     * Cada minuto: revisa las ventas con QR que vencieron sin que llegara la notificación de Mercado
     * Pago (se cobraron, vencieron o se cancelaron). Cada una en su propia transacción.
     */
    @Scheduled(fixedDelay = 60_000, initialDelay = 45_000)
    public void revisarCobrosQr() {
        List<Long> vencidas = ventaQrService.idsQrVencidas();
        for (Long id : vencidas) {
            try {
                ventaQrService.revisar(id);
            } catch (Exception e) {
                log.error("No se pudo revisar el cobro con QR de la venta #{}", id, e);
            }
        }
        if (!vencidas.isEmpty()) {
            log.info("Job de ventas con QR: {} revisadas", vencidas.size());
        }
    }
}
