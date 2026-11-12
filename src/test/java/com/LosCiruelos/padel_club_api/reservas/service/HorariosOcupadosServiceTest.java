package com.LosCiruelos.padel_club_api.reservas.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.LosCiruelos.padel_club_api.TestData;
import com.LosCiruelos.padel_club_api.club.dto.response.CancelacionesResponse;
import com.LosCiruelos.padel_club_api.common.exception.EstadoInvalidoException;
import com.LosCiruelos.padel_club_api.mapper.FueraDeHorarioMapper;
import com.LosCiruelos.padel_club_api.reservas.entity.enums.OpcionReembolso;
import com.LosCiruelos.padel_club_api.reservas.repository.ReservaRepository;
import com.LosCiruelos.padel_club_api.reservas.repository.TurnoFijoRepository;

/** Al confirmar un horario nuevo o un día cerrado, las reservas afuera se cancelan como lo haría el ADMIN. */
class HorariosOcupadosServiceTest {

    @Test
    void cancelaCadaReservaConReembolsoTotalEInformaLasQueNoPudo() {
        CancelacionService cancelacion = mock(CancelacionService.class);
        when(cancelacion.cancelarComoPersonal(32L, OpcionReembolso.TOTAL, false, "admin@test.com")).thenThrow(
                new EstadoInvalidoException("La reserva tiene un pago en proceso; esperá a que Mercado Pago lo resuelva."));
        HorariosOcupadosService servicio = new HorariosOcupadosService(mock(ReservaRepository.class),
                mock(TurnoFijoRepository.class), new FueraDeHorarioMapper(), cancelacion, TestData.reloj(TestData.AHORA));

        CancelacionesResponse r = servicio.cancelar(List.of(31L, 32L, 33L), "admin@test.com");

        // una que falla no frena a las demás (cada una en su transacción)
        assertThat(r.canceladas()).containsExactly(31L, 33L);
        assertThat(r.sinCancelar()).containsExactly(new CancelacionesResponse.SinCancelar(32L,
                "La reserva tiene un pago en proceso; esperá a que Mercado Pago lo resuelva."));
        verify(cancelacion).cancelarComoPersonal(31L, OpcionReembolso.TOTAL, false, "admin@test.com");
        verify(cancelacion).cancelarComoPersonal(33L, OpcionReembolso.TOTAL, false, "admin@test.com");
    }
}
