package com.LosCiruelos.padel_club_api.notificaciones.aviso;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import com.LosCiruelos.padel_club_api.notificaciones.service.AvisosService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Envía los avisos después del commit y en otro hilo:
 * - si la operación hace rollback, no se avisa nada que no pasó;
 * - la llamada a WhatsApp o al mail no demora la respuesta al cliente;
 * - un error de envío no afecta a la operación, que ya se guardó.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AvisosListener {

    private final AvisosService avisosService;

    @Async("avisosExecutor")
    @TransactionalEventListener(fallbackExecution = true)
    public void alPublicarse(AvisoReserva aviso) {
        try {
            avisosService.enviar(aviso);
        } catch (Exception e) {
            log.error("No se pudo procesar el aviso {} de la reserva #{}", aviso.tipo(), aviso.reservaId(), e);
        }
    }
}
