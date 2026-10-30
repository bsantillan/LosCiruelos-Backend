package com.LosCiruelos.padel_club_api.usuarios.service;

import static com.LosCiruelos.padel_club_api.TestData.AHORA;
import static com.LosCiruelos.padel_club_api.TestData.usuario;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.LosCiruelos.padel_club_api.TestData;
import com.LosCiruelos.padel_club_api.usuarios.entity.RefreshToken;
import com.LosCiruelos.padel_club_api.usuarios.entity.Usuario;
import com.LosCiruelos.padel_club_api.usuarios.entity.enums.Role;
import com.LosCiruelos.padel_club_api.usuarios.exception.CredencialesInvalidasException;
import com.LosCiruelos.padel_club_api.usuarios.job.SesionesJobs;
import com.LosCiruelos.padel_club_api.usuarios.repository.RefreshTokenRepository;

/** Las sesiones (refresh tokens) de cada usuario. Hoy es el martes 13/10/2026 10:00. */
class RefreshTokenServiceTest {

    private static final Instant AHORA_INSTANTE = AHORA.atZone(TestData.ZONA).toInstant();

    private RefreshTokenRepository repo;
    private RefreshTokenService sesiones;

    @BeforeEach
    void setUp() {
        repo = mock(RefreshTokenRepository.class);
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        sesiones = new RefreshTokenService(repo, TestData.reloj(AHORA));
        ReflectionTestUtils.setField(sesiones, "expiracionSegundos", 604800L);
    }

    @Test
    void unaSesionDuraUnaSemanaYSeRota() {
        Usuario usuario = usuario(1L, Role.CLIENTE);
        RefreshToken creado = sesiones.crear(usuario);
        assertThat(creado.getFechaExpiracion()).isEqualTo(AHORA_INSTANTE.plusSeconds(604800));

        when(repo.findByToken(creado.getToken())).thenReturn(Optional.of(creado));
        assertThat(sesiones.esValido(creado.getToken())).isTrue();
        assertThat(sesiones.usuarioDelToken(creado.getToken())).isEqualTo(usuario);
        RefreshToken rotado = sesiones.rotar(creado.getToken());
        assertThat(rotado.getToken()).isNotEqualTo(creado.getToken());
        assertThat(rotado.getUsuario()).isEqualTo(usuario);
        verify(repo).delete(creado);
    }

    @Test
    void unaSesionVencidaNoSirveYSeBorra() {
        RefreshToken vencido = RefreshToken.builder().token("viejo").usuario(usuario(1L, Role.CLIENTE))
                .fechaExpiracion(AHORA_INSTANTE.minusSeconds(1)).build();
        when(repo.findByToken("viejo")).thenReturn(Optional.of(vencido));

        assertThat(sesiones.esValido("viejo")).isFalse();
        verify(repo).delete(vencido);
    }

    @Test
    void unTokenQueNoExisteNoSirve() {
        when(repo.findByToken("nada")).thenReturn(Optional.empty());

        assertThat(sesiones.esValido("nada")).isFalse();
        assertThatThrownBy(() -> sesiones.usuarioDelToken("nada")).isInstanceOf(CredencialesInvalidasException.class);
        assertThatThrownBy(() -> sesiones.rotar("nada")).isInstanceOf(CredencialesInvalidasException.class);
    }

    @Test
    void todasLasNochesSeBorranLasVencidas() {
        new SesionesJobs(sesiones).borrarSesionesVencidas();

        verify(repo).deleteAllByFechaExpiracionBefore(AHORA_INSTANTE);
    }
}
