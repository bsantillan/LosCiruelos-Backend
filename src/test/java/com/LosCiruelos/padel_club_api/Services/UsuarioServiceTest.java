package com.LosCiruelos.padel_club_api.Services;

import static com.LosCiruelos.padel_club_api.TestData.usuario;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.LosCiruelos.padel_club_api.Entities.Usuario;
import com.LosCiruelos.padel_club_api.Entities.Enum.AuthProvider;
import com.LosCiruelos.padel_club_api.Entities.Enum.Role;
import com.LosCiruelos.padel_club_api.Exceptions.ValidationException;
import com.LosCiruelos.padel_club_api.Repository.UsuarioRepository;

class UsuarioServiceTest {

    private final UsuarioRepository repo = mock(UsuarioRepository.class);
    private final UsuarioService usuarios = new UsuarioService(repo, mock(PasswordEncoder.class));

    @Test
    void noSePuedeRegistrarUnCelularVerificadoEnOtraCuentaActiva() {
        when(repo.existeCelularVerificadoEnOtraCuentaActiva("5491161234567", -1L)).thenReturn(true);

        assertThatThrownBy(() -> usuarios.crearUsuario("n@test.com", "Ana", "Gomez", "11 6123-4567", "Clave123!",
                Role.CLIENTE, AuthProvider.LOCAL, true, false, true))
                .isInstanceOf(ValidationException.class)
                .satisfies(e -> assertThat(((ValidationException) e).getErrores())
                        .containsEntry("telefono", UsuarioService.MENSAJE_CELULAR_EN_USO));
        verify(repo, never()).save(any());
    }

    @Test
    void siLaOtraCuentaNoLoVerificoOEstaDesactivadaSePuede() {
        // la consulta solo cuenta celulares verificados de cuentas activas
        when(repo.existeCelularVerificadoEnOtraCuentaActiva(anyString(), anyLong())).thenReturn(false);
        when(repo.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));

        Usuario nuevo = usuarios.crearUsuario("n@test.com", "Ana", "Gomez", "11 6123-4567", "Clave123!",
                Role.CLIENTE, AuthProvider.LOCAL, true, false, true);

        assertThat(nuevo.getTelefono()).isEqualTo("5491161234567");
    }

    @Test
    void cambiarAUnCelularDeOtraCuentaActivaNoSePuede() {
        Usuario usuario = usuario(1L, Role.CLIENTE);
        when(repo.existeCelularVerificadoEnOtraCuentaActiva("5491161234567", 1L)).thenReturn(true);

        assertThatThrownBy(() -> usuarios.cambiarTelefono(usuario, "11 6123-4567"))
                .isInstanceOf(ValidationException.class);
        assertThat(usuario.getTelefono()).isEqualTo("5491122334455");
    }

    @Test
    void guardarElMismoCelularNoVuelveAControlar() {
        Usuario usuario = usuario(1L, Role.CLIENTE);

        usuarios.cambiarTelefono(usuario, "+54 9 11 2233-4455");

        verify(repo, never()).existeCelularVerificadoEnOtraCuentaActiva(anyString(), anyLong());
        assertThat(usuario.getTelefonoVerificado()).isTrue();
    }
}
