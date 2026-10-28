package com.LosCiruelos.padel_club_api.usuarios.service;

import static com.LosCiruelos.padel_club_api.TestData.usuario;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import com.LosCiruelos.padel_club_api.usuarios.dto.response.ClienteBusquedaResponse;
import com.LosCiruelos.padel_club_api.usuarios.entity.ClienteProfile;
import com.LosCiruelos.padel_club_api.usuarios.entity.Usuario;
import com.LosCiruelos.padel_club_api.usuarios.entity.enums.Categoria;
import com.LosCiruelos.padel_club_api.usuarios.entity.enums.Posicion;
import com.LosCiruelos.padel_club_api.usuarios.entity.enums.Role;
import com.LosCiruelos.padel_club_api.Exceptions.ReservaException;
import com.LosCiruelos.padel_club_api.usuarios.repository.ClienteProfileRepository;
import com.LosCiruelos.padel_club_api.usuarios.repository.UsuarioRepository;

class BusquedaClientesServiceTest {

    private final UsuarioRepository repo = mock(UsuarioRepository.class);
    private final BusquedaClientesService clientes = new BusquedaClientesService(repo,
            new ClienteProfileService(mock(ClienteProfileRepository.class)));

    @Test
    void devuelveLosDatosDelClienteYSiTieneElPerfilCompleto() {
        Usuario ana = usuario(5L, Role.CLIENTE);
        ana.setClienteProfile(ClienteProfile.builder().usuario(ana).categoria(Categoria.Cuarta)
                .posicion(Posicion.Drive).build());
        Usuario sinPerfil = usuario(6L, Role.CLIENTE);
        when(repo.findAll(org.mockito.ArgumentMatchers.<org.springframework.data.jpa.domain.Specification<Usuario>>any(),
                any(Pageable.class))).thenReturn(new PageImpl<>(List.of(ana, sinPerfil)));

        List<ClienteBusquedaResponse> r = clientes.buscar("nom", true);

        assertThat(r).hasSize(2);
        assertThat(r.get(0)).isEqualTo(new ClienteBusquedaResponse(5L, "Nombre5", "Apellido5", "usuario5@test.com",
                "5491122334455", true, Categoria.Cuarta, Posicion.Drive, true, true));
        assertThat(r.get(1).perfilCompleto()).isFalse();
        assertThat(r.get(1).categoria()).isNull();
    }

    @Test
    void comoMaximo20ResultadosOrdenadosPorApellido() {
        when(repo.findAll(org.mockito.ArgumentMatchers.<org.springframework.data.jpa.domain.Specification<Usuario>>any(),
                any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        clientes.buscar("perez", false);

        ArgumentCaptor<Pageable> pedido = ArgumentCaptor.forClass(Pageable.class);
        verify(repo).findAll(org.mockito.ArgumentMatchers.<org.springframework.data.jpa.domain.Specification<Usuario>>any(),
                pedido.capture());
        assertThat(pedido.getValue().getPageSize()).isEqualTo(20);
        assertThat(pedido.getValue().getSort()).isEqualTo(Sort.by("apellido", "nombre", "id"));
    }

    @Test
    void pideAlMenos2Caracteres() {
        assertThatThrownBy(() -> clientes.buscar("a", true)).isInstanceOf(ReservaException.class)
                .hasMessageContaining("al menos 2");
        assertThatThrownBy(() -> clientes.buscar("   ", true)).isInstanceOf(ReservaException.class);
        assertThatThrownBy(() -> clientes.buscar(null, true)).isInstanceOf(ReservaException.class);
        verifyNoInteractions(repo);
    }
}
