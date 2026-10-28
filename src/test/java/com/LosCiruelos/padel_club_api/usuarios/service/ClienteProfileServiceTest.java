package com.LosCiruelos.padel_club_api.usuarios.service;

import static com.LosCiruelos.padel_club_api.TestData.usuario;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.LosCiruelos.padel_club_api.usuarios.entity.ClienteProfile;
import com.LosCiruelos.padel_club_api.usuarios.entity.Usuario;
import com.LosCiruelos.padel_club_api.usuarios.entity.enums.Categoria;
import com.LosCiruelos.padel_club_api.usuarios.entity.enums.Posicion;
import com.LosCiruelos.padel_club_api.usuarios.entity.enums.Role;
import com.LosCiruelos.padel_club_api.usuarios.repository.ClienteProfileRepository;

class ClienteProfileServiceTest {

    private final ClienteProfileRepository repo = mock(ClienteProfileRepository.class);
    private final ClienteProfileService perfiles = new ClienteProfileService(repo);

    @Test
    void elClienteNecesitaElCelularVerificadoParaTenerElPerfilCompleto() {
        Usuario cliente = usuario(1L, Role.CLIENTE);
        ClienteProfile perfil = ClienteProfile.builder().usuario(cliente).categoria(Categoria.Cuarta)
                .posicion(Posicion.Drive).build();
        when(repo.findByUsuario(cliente)).thenReturn(Optional.of(perfil));

        assertThat(perfiles.perfilCompleto(cliente)).isTrue();

        cliente.setTelefonoVerificado(false);
        assertThat(perfiles.perfilCompleto(cliente)).isFalse();
        assertThat(perfil.estaCompleto()).isFalse();
    }

    @Test
    void elAdminTambienYElEmpleadoNo() {
        Usuario admin = usuario(2L, Role.ADMIN);
        admin.setTelefonoVerificado(false);
        assertThat(perfiles.perfilCompleto(admin)).isFalse();
        admin.setTelefonoVerificado(true);
        assertThat(perfiles.perfilCompleto(admin)).isTrue();

        Usuario empleado = usuario(3L, Role.EMPLEADO);
        empleado.setTelefono(null);
        assertThat(perfiles.perfilCompleto(empleado)).isTrue();
    }
}
