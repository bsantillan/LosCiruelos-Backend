package com.LosCiruelos.padel_club_api.club.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.LosCiruelos.padel_club_api.TestData;
import com.LosCiruelos.padel_club_api.club.repository.CanchaRepository;

class CanchaServiceTest {

    private final CanchaRepository canchaRepository = mock(CanchaRepository.class);

    @Test
    void listaLasCanchas() {
        CanchaService servicio = new CanchaService(canchaRepository);
        when(canchaRepository.findAll()).thenReturn(List.of(TestData.cancha()));
        assertThat(servicio.listarCanchas()).hasSize(1);
        assertThat(servicio.listarCanchas().getFirst().getDescripcion()).isEqualTo("Cancha 1");
    }
}
