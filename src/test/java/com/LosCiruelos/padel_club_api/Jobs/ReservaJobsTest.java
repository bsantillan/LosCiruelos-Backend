package com.LosCiruelos.padel_club_api.Jobs;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.LosCiruelos.padel_club_api.Entities.Enum.EstadoPago;
import com.LosCiruelos.padel_club_api.Services.PagoService;
import com.LosCiruelos.padel_club_api.Services.RecordatorioService;
import com.LosCiruelos.padel_club_api.Services.ReservaService;

class ReservaJobsTest {

    private final ReservaService reservaService = mock(ReservaService.class);
    private final PagoService pagoService = mock(PagoService.class);
    private final RecordatorioService recordatorioService = mock(RecordatorioService.class);
    private final ReservaJobs jobs = new ReservaJobs(reservaService, pagoService, recordatorioService);

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
        when(pagoService.idsReembolsosPendientes()).thenReturn(List.of(1L, 2L, 3L));
        when(pagoService.reintentarReembolso(1L)).thenReturn(EstadoPago.DEVUELTO);
        when(pagoService.reintentarReembolso(2L)).thenThrow(new IllegalStateException("falla"));
        when(pagoService.reintentarReembolso(3L)).thenReturn(EstadoPago.REEMBOLSO_PENDIENTE);

        jobs.reintentarReembolsos();

        verify(pagoService).reintentarReembolso(3L);
    }

    @Test
    void sinReembolsosPendientesNoHaceNada() {
        when(pagoService.idsReembolsosPendientes()).thenReturn(List.of());
        jobs.reintentarReembolsos();
        verify(pagoService).idsReembolsosPendientes();
    }
}
