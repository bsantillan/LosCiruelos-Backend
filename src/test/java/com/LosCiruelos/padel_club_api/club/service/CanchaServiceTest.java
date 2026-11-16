package com.LosCiruelos.padel_club_api.club.service;

import com.LosCiruelos.padel_club_api.club.entity.Cancha;
import com.LosCiruelos.padel_club_api.club.exception.CanchaNotFoundException;
import java.util.Optional;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.ArgumentMatchers.any;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.LosCiruelos.padel_club_api.mapper.CanchaMapper;
import com.LosCiruelos.padel_club_api.TestData;
import com.LosCiruelos.padel_club_api.club.repository.CanchaRepository;

class CanchaServiceTest {

    private final CanchaRepository canchaRepository = mock(CanchaRepository.class);

    @Test
    void listaLasCanchas() {
        CanchaService servicio = new CanchaService(canchaRepository, new CanchaMapper());
        when(canchaRepository.findByActivaTrueOrderByNumeroAsc()).thenReturn(List.of(TestData.cancha()));
        assertThat(servicio.listarCanchas()).hasSize(1);
        assertThat(servicio.listarCanchas().getFirst().getDescripcion()).isEqualTo("Cancha 1");
    }

    @Test
    void enElArranqueCreaLasCanchasSoloSiNoHayNinguna() {
        CanchaService servicio = new CanchaService(canchaRepository, new CanchaMapper());
        when(canchaRepository.count()).thenReturn(0L);

        assertThat(servicio.crearCanchasSiNoHay(4, "Césped")).isTrue();
        verify(canchaRepository, times(4)).save(any(Cancha.class));

        when(canchaRepository.count()).thenReturn(4L);
        assertThat(servicio.crearCanchasSiNoHay(4, "Césped")).isFalse();
        verify(canchaRepository, times(4)).save(any(Cancha.class));
    }

    @Test
    void bloquearUnaCanchaInexistenteDevuelveNoEncontrada() {
        CanchaService servicio = new CanchaService(canchaRepository, new CanchaMapper());
        when(canchaRepository.findByIdForUpdate(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.bloquear(9L))
                .isInstanceOf(CanchaNotFoundException.class)
                .hasMessage("Cancha no encontrada: 9");
    }

    @Test
    void unaCanchaDadaDeBajaNoAceptaReservasNiApareceEnLaDisponibilidad() {
        CanchaService servicio = new CanchaService(canchaRepository, new CanchaMapper());
        Cancha inactiva = TestData.cancha();
        inactiva.setActiva(false);
        when(canchaRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(inactiva));
        when(canchaRepository.findById(1L)).thenReturn(Optional.of(inactiva));

        assertThatThrownBy(() -> servicio.bloquear(1L))
                .isInstanceOf(com.LosCiruelos.padel_club_api.common.exception.EstadoInvalidoException.class)
                .hasMessageContaining("está dada de baja");
        assertThatThrownBy(() -> servicio.activaOrThrow(1L)).isInstanceOf(CanchaNotFoundException.class);
    }
}
