package com.LosCiruelos.padel_club_api.Services;

import static com.LosCiruelos.padel_club_api.TestData.AHORA;
import static com.LosCiruelos.padel_club_api.TestData.usuario;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.LosCiruelos.padel_club_api.TestData;
import com.LosCiruelos.padel_club_api.DTOs.Requests.PerfilRequest;
import com.LosCiruelos.padel_club_api.DTOs.Responses.PerfilResponse;
import com.LosCiruelos.padel_club_api.Entities.ClienteProfile;
import com.LosCiruelos.padel_club_api.reservas.entity.Reserva;
import com.LosCiruelos.padel_club_api.Entities.Usuario;
import com.LosCiruelos.padel_club_api.Entities.Enum.Categoria;
import com.LosCiruelos.padel_club_api.reservas.entity.enums.EstadoReserva;
import com.LosCiruelos.padel_club_api.Entities.Enum.Posicion;
import com.LosCiruelos.padel_club_api.Entities.Enum.Role;
import com.LosCiruelos.padel_club_api.Exceptions.CategoriaException;
import com.LosCiruelos.padel_club_api.Exceptions.ValidationException;
import com.LosCiruelos.padel_club_api.Repository.ClienteProfileRepository;
import com.LosCiruelos.padel_club_api.Security.JWTUtil;
import com.LosCiruelos.padel_club_api.reservas.service.ReservaService;

class PerfilServiceTest {

    private UsuarioService usuarioService;
    private ClienteProfileRepository clienteProfileRepository;
    private ReservaService reservaService;
    private JWTUtil jwtUtil;
    private PerfilService perfilService;

    private Usuario cliente;
    private ClienteProfile perfil;

    @BeforeEach
    void setUp() {
        usuarioService = mock(UsuarioService.class);
        clienteProfileRepository = mock(ClienteProfileRepository.class);
        reservaService = mock(ReservaService.class);
        jwtUtil = mock(JWTUtil.class);
        perfilService = new PerfilService(usuarioService, new ClienteProfileService(clienteProfileRepository),
                reservaService, jwtUtil, TestData.reloj(AHORA));

        cliente = usuario(1L, Role.CLIENTE);
        perfil = ClienteProfile.builder().id(1L).usuario(cliente).categoria(Categoria.Cuarta).posicion(Posicion.Drive)
                .categoriaActualizadaAt(AHORA.minusHours(2)).categoriaModificada(false).build();

        when(usuarioService.autenticado(cliente.getEmail())).thenReturn(cliente);
        when(usuarioService.updateUsuario(any(), any(), any(), any())).thenAnswer(inv -> inv.getArgument(0));
        when(clienteProfileRepository.findByUsuario(cliente)).thenReturn(Optional.of(perfil));
        when(clienteProfileRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(reservaService.ultimaReservaCompletada(any())).thenReturn(Optional.empty());
    }

    private static PerfilRequest pedido(Categoria categoria, Posicion posicion, String telefono) {
        PerfilRequest req = new PerfilRequest();
        req.setNombre("Juan");
        req.setApellido("Perez");
        req.setTelefono(telefono);
        req.setCategoria(categoria);
        req.setPosicion(posicion);
        return req;
    }

    @Test
    void pasadas24HorasSePuedeEditarTodoMenosLaCategoria() {
        // antes, pasadas las 24 hs no se podía editar ni el teléfono
        perfil.setCategoriaActualizadaAt(AHORA.minusDays(5));

        PerfilResponse r = perfilService.updatePerfil(pedido(Categoria.Cuarta, Posicion.Reves, "1199998888"),
                cliente.getEmail());

        assertThat(r.getPosicion()).isEqualTo(Posicion.Reves);
        assertThat(r.getCategoriaEditable()).isFalse();
        verify(usuarioService).updateUsuario(cliente, "Juan", "Perez", "1199998888");
    }

    @Test
    void laCategoriaSeCambiaUnaSolaVezDentroDe24Horas() {
        PerfilResponse r = perfilService.updatePerfil(pedido(Categoria.Tercera, Posicion.Drive, "1199998888"),
                cliente.getEmail());
        assertThat(r.getCategoria()).isEqualTo(Categoria.Tercera);
        assertThat(perfil.getCategoriaModificada()).isTrue();

        assertThatThrownBy(() -> perfilService.updatePerfil(pedido(Categoria.Segunda, Posicion.Drive, "1100000000"),
                cliente.getEmail()))
                .isInstanceOf(CategoriaException.class)
                .hasMessageContaining("Ya modificaste");
    }

    @Test
    void siLaCategoriaNoSePuedeCambiarNoSeGuardaNada() {
        // antes nombre y teléfono se guardaban aunque la respuesta fuera 403
        perfil.setCategoriaActualizadaAt(AHORA.minusHours(25));

        assertThatThrownBy(() -> perfilService.updatePerfil(pedido(Categoria.Primera, Posicion.Drive, "1100000000"),
                cliente.getEmail()))
                .isInstanceOf(CategoriaException.class)
                .hasMessageContaining("expiró");
        verify(usuarioService, never()).updateUsuario(any(), anyString(), anyString(), anyString());
    }

    @Test
    void laPrimeraCargaDeCategoriaNoCuentaComoCambio() {
        perfil.setCategoria(null);
        perfil.setCategoriaActualizadaAt(null);

        perfilService.updatePerfil(pedido(Categoria.Quinta, Posicion.Ambos, "1199998888"), cliente.getEmail());

        assertThat(perfil.getCategoriaActualizadaAt()).isEqualTo(AHORA);
        assertThat(perfil.getCategoriaModificada()).isFalse();
    }

    @Test
    void clienteSinPerfilLoCreaAlActualizar() {
        when(clienteProfileRepository.findByUsuario(cliente)).thenReturn(Optional.empty());

        PerfilResponse r = perfilService.updatePerfil(pedido(Categoria.Quinta, Posicion.Ambos, "1199998888"),
                cliente.getEmail());

        assertThat(r.getCategoria()).isEqualTo(Categoria.Quinta);
    }

    @Test
    void clienteDebeEnviarCategoriaYPosicion() {
        assertThatThrownBy(() -> perfilService.updatePerfil(pedido(null, null, "1199998888"), cliente.getEmail()))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void otrosRolesNoTienenCategoria() {
        Usuario empleado = usuario(2L, Role.EMPLEADO);
        when(usuarioService.autenticado(empleado.getEmail())).thenReturn(empleado);

        assertThatThrownBy(() -> perfilService.updatePerfil(pedido(Categoria.Cuarta, null, "1199998888"),
                empleado.getEmail()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(perfilService.updatePerfil(pedido(null, null, "1199998888"), empleado.getEmail()).getCategoria())
                .isNull();
    }

    @Test
    void estadisticasCuentanSoloPartidosCompletados() {
        Reserva jugada = TestData.reserva(1L, cliente, EstadoReserva.COMPLETADA, "15000");
        jugada.setFechaReserva(AHORA.toLocalDate().minusDays(3));
        when(reservaService.contarPorEstado(cliente, EstadoReserva.COMPLETADA)).thenReturn(4L);
        when(reservaService.countPartidosEsteMes(1L, EstadoReserva.COMPLETADA)).thenReturn(2L);
        when(reservaService.ultimaReservaCompletada(cliente)).thenReturn(Optional.of(jugada));

        PerfilResponse r = perfilService.getPerfil(cliente.getEmail());

        assertThat(r.getCantPartidos()).isEqualTo(4);
        assertThat(r.getCantPartidosEsteMes()).isEqualTo(2L);
        assertThat(r.getDiasDesdeUltimoPartido()).isEqualTo(3);
        assertThat(r.getCantDiasMiembro()).isEqualTo(10);
    }

    @Test
    void sinPartidosJugadosDiasDesdeUltimoEsNull() {
        assertThat(perfilService.getPerfil(cliente.getEmail()).getDiasDesdeUltimoPartido()).isNull();
    }

    @Test
    void desactivarCierraTodasLasSesiones() {
        perfilService.desactivarPerfil(cliente.getEmail());

        verify(usuarioService).desactivarUsuario(cliente);
        verify(jwtUtil).deleteAllRefreshTokens(cliente);
    }

    // ── Teléfono opcional en PUT /perfil (con el UsuarioService real) ─────

    @org.junit.jupiter.api.Nested
    class TelefonoOpcional {

        private com.LosCiruelos.padel_club_api.Repository.UsuarioRepository usuarioRepository;
        private PerfilService perfiles;

        @BeforeEach
        void conUsuarioServiceReal() {
            usuarioRepository = mock(com.LosCiruelos.padel_club_api.Repository.UsuarioRepository.class);
            when(usuarioRepository.findByEmail(cliente.getEmail())).thenReturn(Optional.of(cliente));
            when(usuarioRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            UsuarioService real = new UsuarioService(usuarioRepository,
                    mock(org.springframework.security.crypto.password.PasswordEncoder.class));
            perfiles = new PerfilService(real, new ClienteProfileService(clienteProfileRepository), reservaService,
                    jwtUtil, TestData.reloj(AHORA));
        }

        @Test
        void sinTelefonoConservaElTelefonoYSuVerificacion() {
            PerfilResponse r = perfiles.updatePerfil(pedido(Categoria.Cuarta, Posicion.Reves, null), cliente.getEmail());

            assertThat(cliente.getTelefono()).isEqualTo("5491122334455");
            assertThat(cliente.getTelefonoVerificado()).isTrue();
            assertThat(cliente.getNombre()).isEqualTo("Juan");
            assertThat(r.getPerfilCompleto()).isTrue();
        }

        @Test
        void sinTelefonoFuncionaParaQuienNoTieneTelefono() {
            cliente.setTelefono(null);
            cliente.setTelefonoVerificado(false);

            PerfilResponse r = perfiles.updatePerfil(pedido(Categoria.Cuarta, Posicion.Reves, null), cliente.getEmail());

            assertThat(cliente.getTelefono()).isNull();
            assertThat(cliente.getApellido()).isEqualTo("Perez");
            assertThat(r.getPerfilCompleto()).isFalse(); // le falta el celular verificado
        }

        @Test
        void conUnTelefonoNuevoQuedaSinVerificar() {
            perfiles.updatePerfil(pedido(Categoria.Cuarta, Posicion.Reves, "11 6123-4567"), cliente.getEmail());

            assertThat(cliente.getTelefono()).isEqualTo("5491161234567");
            assertThat(cliente.getTelefonoVerificado()).isFalse();
        }

        @Test
        void elMismoTelefonoEnOtroFormatoNoPierdeLaVerificacion() {
            perfiles.updatePerfil(pedido(Categoria.Cuarta, Posicion.Reves, "11 2233-4455"), cliente.getEmail());

            assertThat(cliente.getTelefono()).isEqualTo("5491122334455");
            assertThat(cliente.getTelefonoVerificado()).isTrue();
        }

        @Test
        void unTelefonoGuardadoEnFormatoViejoTampocoLaPierde() {
            cliente.setTelefono("1122334455"); // guardado antes de V8

            perfiles.updatePerfil(pedido(Categoria.Cuarta, Posicion.Reves, "+54 9 11 2233-4455"), cliente.getEmail());

            assertThat(cliente.getTelefono()).isEqualTo("5491122334455");
            assertThat(cliente.getTelefonoVerificado()).isTrue();
        }
    }
}
