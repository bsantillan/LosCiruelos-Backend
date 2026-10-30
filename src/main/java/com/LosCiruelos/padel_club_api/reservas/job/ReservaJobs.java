package com.LosCiruelos.padel_club_api.reservas.job;

import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.LosCiruelos.padel_club_api.pagos.entity.enums.EstadoPago;
import com.LosCiruelos.padel_club_api.pagos.service.ReembolsoService;
import com.LosCiruelos.padel_club_api.reservas.service.TurnoFijoService;
import com.LosCiruelos.padel_club_api.reservas.service.RecordatorioService;
import com.LosCiruelos.padel_club_api.reservas.service.ReservaService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReservaJobs {

    private final ReservaService reservaService;
    private final ReembolsoService reembolsoService;
    private final RecordatorioService recordatorioService;
    private final TurnoFijoService turnoFijoService;

    /**
     * Cada minuto: expira las reservas PENDIENTE cuyo plazo para pagar venció y que no
     * tienen pagos aprobados ni en proceso. Cada reserva se procesa en su propia
     * transacción; si una falla, se sigue con las demás.
     */
    @Scheduled(fixedDelay = 60_000, initialDelay = 30_000)
    public void expirarReservasVencidas() {
        List<Long> vencidas = reservaService.idsPendientesVencidas();
        if (vencidas.isEmpty()) {
            return;
        }

        int expiradas = 0;
        for (Long id : vencidas) {
            try {
                if (reservaService.expirarSiCorresponde(id)) {
                    expiradas++;
                }
            } catch (Exception e) {
                log.error("No se pudo expirar la reserva #{}", id, e);
            }
        }
        log.info("Job de expiración: {} de {} reservas vencidas expiradas", expiradas, vencidas.size());
    }

    /**
     * Cada 2 minutos: reintenta los reembolsos que Mercado Pago no aceptó al cancelar
     * (pagos REEMBOLSO_PENDIENTE). Cada pago en su propia transacción, con la reserva bloqueada.
     */
    @Scheduled(fixedDelay = 120_000, initialDelay = 45_000)
    public void reintentarReembolsos() {
        List<Long> pendientes = reembolsoService.idsReembolsosPendientes();
        if (pendientes.isEmpty()) {
            return;
        }

        int devueltos = 0;
        for (Long id : pendientes) {
            try {
                if (reembolsoService.reintentarReembolso(id) == EstadoPago.DEVUELTO) {
                    devueltos++;
                }
            } catch (Exception e) {
                log.error("No se pudo reintentar el reembolso del pago #{}", id, e);
            }
        }
        log.info("Job de reembolsos: {} de {} reembolsos pendientes hechos", devueltos, pendientes.size());
    }

    /** Cada 5 minutos: recordatorio al cliente antes del turno (por WhatsApp o mail). */
    @Scheduled(fixedDelay = 300_000, initialDelay = 90_000)
    public void enviarRecordatorios() {
        int enviados = 0;
        for (Long id : recordatorioService.pendientes()) {
            try {
                if (recordatorioService.enviar(id)) {
                    enviados++;
                }
            } catch (Exception e) {
                log.error("No se pudo enviar el recordatorio de la reserva #{}", id, e);
            }
        }
        if (enviados > 0) {
            log.info("Job de recordatorios: {} enviados", enviados);
        }
    }

    /**
     * Cada hora: crea los partidos de los turnos fijos que falten dentro de la anticipación
     * (cada fecha en su propia transacción; las que no se pueden crear se reintentan la próxima vez).
     */
    @Scheduled(fixedDelay = 3_600_000, initialDelay = 120_000)
    public void crearPartidosDeTurnosFijos() {
        int creados = turnoFijoService.generarTodos();
        if (creados > 0) {
            log.info("Job de turnos fijos: {} partidos creados", creados);
        }
    }

    /** Cada 10 minutos: las reservas señadas o pagas cuyo turno ya terminó pasan a COMPLETADA. */
    @Scheduled(fixedDelay = 600_000, initialDelay = 60_000)
    public void completarReservasJugadas() {
        int completadas = reservaService.marcarCompletadas();
        if (completadas > 0) {
            log.info("Job de completadas: {} reservas marcadas como COMPLETADA", completadas);
        }
    }
}
