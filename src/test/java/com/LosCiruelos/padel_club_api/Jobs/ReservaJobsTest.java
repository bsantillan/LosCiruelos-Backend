package com.LosCiruelos.padel_club_api.Jobs;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.LosCiruelos.padel_club_api.pagos.entity.enums.EstadoPago;
import com.LosCiruelos.padel_club_api.reservas.service.TurnoFijoService;
import com.LosCiruelos.padel_club_api.pagos.service.ReembolsoService;
import com.LosCiruelos.padel_club_api.Services.RecordatorioService;
import com.LosCiruelos.padel_club_api.reservas.service.ReservaService;

class ReservaJobsTest {

    private final ReservaService reservaService = mock(ReservaService.class);
    private final ReembolsoService reembolsoService = mock(ReembolsoService.class);
    private final RecordatorioService recordatorioService = mock(RecordatorioService.class);
    private final TurnoFijoService turnoFijoService = mock(TurnoFijoService.class);
    private final ReservaJobs jobs = new ReservaJobs(reservaService, reembolsoService, recordatorioService, turnoFijoService);

    @Test
    void siUnaReservaFallaSeSigueConLasDemas() {
        // antes una excepción cortaba el forEach y las siguientes no se expiraban
        when(reservaService.idsPendientesVencidas()).thenReturn(List.of(1L, 2L, 3L));
        when(reservaService.expirarSiCorresponde(1L)).thenReturn(true);
        when(reservaService.expirarSiCorresponde(2L)).thenThrow(new IllegalStateException("falla"));
        when(reservaService.expirarSiCorresponde(3L)).thenReturn(true);

        jobs.expirarReservasVencidas();

        verify(reservaService).expirarSiCorresponde(3L);
    }

    @Test
    void sinVencidasNoHaceNada() {
        when(reservaService.idsPendientesVencidas()).thenReturn(List.of());
        jobs.expirarReservasVencidas();
        verify(reservaService).idsPendientesVencidas();
    }

    @Test
    void completaLasReservasJugadas() {
        when(reservaService.marcarCompletadas()).thenReturn(2);
        jobs.completarReservasJugadas();
        verify(reservaService).marcarCompletadas();
    }

    @Test
    void siUnReembolsoFallaSeSigueConLosDemas() {
        when(reembolsoService.idsReembolsosPendientes()).thenReturn(List.of(1L, 2L, 3L));
        when(reembolsoService.reintentarReembolso(1L)).thenReturn(EstadoPago.DEVUELTO);
        when(reembolsoService.reintentarReembolso(2L)).thenThrow(new IllegalStateException("falla"));
        when(reembolsoService.reintentarReembolso(3L)).thenReturn(EstadoPago.REEMBOLSO_PENDIENTE);

        jobs.reintentarReembolsos();

        verify(reembolsoService).reintentarReembolso(3L);
    }

    @Test
    void sinReembolsosPendientesNoHaceNada() {
        when(reembolsoService.idsReembolsosPendientes()).thenReturn(List.of());
        jobs.reintentarReembolsos();
        verify(reembolsoService).idsReembolsosPendientes();
    }
}
