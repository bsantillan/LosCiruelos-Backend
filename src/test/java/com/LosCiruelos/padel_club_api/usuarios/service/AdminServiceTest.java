package com.LosCiruelos.padel_club_api.usuarios.service;

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

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.LosCiruelos.padel_club_api.usuarios.dto.request.CrearUsuarioAdminRequest;
import com.LosCiruelos.padel_club_api.usuarios.dto.request.EditarUsuarioAdminRequest;
import com.LosCiruelos.padel_club_api.usuarios.dto.request.FiltroUsuarioRequest;
import com.LosCiruelos.padel_club_api.usuarios.dto.response.UsuarioAdminResponse;
import com.LosCiruelos.padel_club_api.usuarios.entity.ClienteProfile;
import com.LosCiruelos.padel_club_api.usuarios.entity.Usuario;
import com.LosCiruelos.padel_club_api.usuarios.entity.enums.AuthProvider;
import com.LosCiruelos.padel_club_api.usuarios.entity.enums.Categoria;
import com.LosCiruelos.padel_club_api.usuarios.entity.enums.Posicion;
import com.LosCiruelos.padel_club_api.usuarios.entity.enums.Role;
import com.LosCiruelos.padel_club_api.Exceptions.EmailEnUsoException;
import com.LosCiruelos.padel_club_api.Security.JWTUtil;
import com.LosCiruelos.padel_club_api.Security.PasswordValidator;

class AdminServiceTest {

    private ClienteProfileService clienteProfileService;
    private UsuarioService usuarioService;
    private JWTUtil jwtUtil;
    private VerificationService verificationService;
    private AdminService adminService;
    private Usuario cliente;

    @BeforeEach
    void setUp() {
        clienteProfileService = mock(ClienteProfileService.class);
        usuarioService = mock(UsuarioService.class);
        jwtUtil = mock(JWTUtil.class);
        verificationService = mock(VerificationService.class);
        adminService = new AdminService(clienteProfileService, usuarioService, jwtUtil, verificationService);

        cliente = usuario(1L, Role.CLIENTE);
        when(usuarioService.findByIdOrThrow(1L)).thenReturn(cliente);
        when(usuarioService.updateUsuario(any(), any(), any(), any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private static CrearUsuarioAdminRequest crear(Role rol, Categoria categoria, Posicion posicion) {
        CrearUsuarioAdminRequest req = new CrearUsuarioAdminRequest();
        req.setEmail("nuevo@test.com");
        req.setNombre("Nuevo");
        req.setApellido("Usuario");
        req.setTelefono("1122334455");
        req.setRol(rol);
        req.setCategoria(categoria);
        req.setPosicion(posicion);
        return req;
    }

    private static EditarUsuarioAdminRequest editar(Role rol, Categoria categoria, Posicion posicion) {
        EditarUsuarioAdminRequest req = new EditarUsuarioAdminRequest();
        req.setNombre("Editado");
        req.setApellido("Usuario");
        req.setTelefono("1122334455");
        req.setRol(rol);
        req.setCategoria(categoria);
        req.setPosicion(posicion);
        return req;
    }

    @Test
    void creaUnClienteConContraseniaTemporalSeguraYLeEnviaLaBienvenida() {
        Usuario creado = usuario(9L, Role.CLIENTE);
        when(usuarioService.crearUsuario(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(creado);

        adminService.crearUsuario(crear(Role.CLIENTE, Categoria.Quinta, Posicion.Ambos));

        ArgumentCaptor<String> password = ArgumentCaptor.forClass(String.class);
        verify(usuarioService).crearUsuario(eq("nuevo@test.com"), eq("Nuevo"), eq("Usuario"), eq("1122334455"),
                password.capture(), eq(Role.CLIENTE), eq(AuthProvider.LOCAL), eq(true), eq(false), eq(true));
        assertThat(password.getValue()).hasSize(12);
        assertThat(PasswordValidator.esValida(password.getValue())).isTrue();
        verify(clienteProfileService).crearClienteProfile(creado, Categoria.Quinta, Posicion.Ambos);
        verify(verificationService).enviarBienvenida(creado.getEmail(), password.getValue());
    }

    @Test
    void validacionesAlCrear() {
        when(usuarioService.findByEmail("nuevo@test.com")).thenReturn(cliente);
        assertThatThrownBy(() -> adminService.crearUsuario(crear(Role.EMPLEADO, null, null)))
                .isInstanceOf(EmailEnUsoException.class);

        when(usuarioService.findByEmail("nuevo@test.com")).thenReturn(null);
        assertThatThrownBy(() -> adminService.crearUsuario(crear(Role.CLIENTE, null, Posicion.Drive)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void editarValidaAntesDeModificar() {
        // antes el rol nuevo se guardaba aunque después fallara la validación
        assertThatThrownBy(() -> adminService.editarUsuario(1L, editar(Role.CLIENTE, null, null)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> adminService.editarUsuario(1L, editar(Role.EMPLEADO, Categoria.Cuarta, null)))
                .isInstanceOf(IllegalArgumentException.class);
        verify(usuarioService, never()).updateUsuario(any(), anyString(), anyString(), anyString());
        assertThat(cliente.getRol()).isEqualTo(Role.CLIENTE);
    }

    @Test
    void editarUnClienteActualizaSuPerfil() {
        ClienteProfile perfil = ClienteProfile.builder().usuario(cliente).categoria(Categoria.Cuarta).build();
        when(clienteProfileService.findByUsuario(cliente)).thenReturn(perfil);
        when(clienteProfileService.updateClienteProfile(any(), any(), any(), any())).thenReturn(perfil);

        UsuarioAdminResponse r = adminService.editarUsuario(1L, editar(Role.CLIENTE, Categoria.Primera, Posicion.Reves));

        verify(clienteProfileService).updateClienteProfile(perfil, Categoria.Primera, Posicion.Reves, null);
        assertThat(r.getRol()).isEqualTo(Role.CLIENTE);
    }

    @Test
    void pasarAEmpleadoQuitaElPerfilDeJugador() {
        adminService.editarUsuario(1L, editar(Role.EMPLEADO, null, null));

        assertThat(cliente.getRol()).isEqualTo(Role.EMPLEADO);
        assertThat(cliente.getClienteProfile()).isNull();
    }

    @Test
    void noSeEditaNiDesactivaAUnAdministrador() {
        Usuario admin = usuario(3L, Role.ADMIN);
        when(usuarioService.findByIdOrThrow(3L)).thenReturn(admin);

        assertThatThrownBy(() -> adminService.editarUsuario(3L, editar(Role.EMPLEADO, null, null)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> adminService.desactivarUsuario(3L)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void desactivarYReactivar() {
        adminService.desactivarUsuario(1L);
        assertThat(cliente.getEnabled()).isFalse();
        verify(jwtUtil).deleteAllRefreshTokens(cliente);

        adminService.reactivarUsuario(1L);
        assertThat(cliente.getEnabled()).isTrue();
        assertThat(cliente.getTelefonoVerificado()).isTrue();
    }

    @Test
    void alReactivarPierdeElCelularSiOtraCuentaActivaLoVerifico() {
        cliente.setEnabled(false);
        when(usuarioService.telefonoVerificadoEnOtraCuenta(cliente.getTelefono(), cliente.getId())).thenReturn(true);

        adminService.reactivarUsuario(1L);

        assertThat(cliente.getEnabled()).isTrue();
        assertThat(cliente.getTelefonoVerificado()).isFalse();
    }

    @Test
    void listarYObtener() {
        when(usuarioService.findAll(any())).thenReturn(List.of(cliente));
        assertThat(adminService.listarUsuarios(new FiltroUsuarioRequest())).hasSize(1);
        assertThat(adminService.getUsuario(1L).getEmail()).isEqualTo(cliente.getEmail());

        FiltroUsuarioRequest invalido = new FiltroUsuarioRequest();
        invalido.setCategoria(Categoria.Cuarta);
        assertThatThrownBy(() -> adminService.listarUsuarios(invalido)).isInstanceOf(IllegalArgumentException.class);
    }
}
