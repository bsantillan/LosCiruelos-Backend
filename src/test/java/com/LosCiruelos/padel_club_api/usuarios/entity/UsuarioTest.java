package com.LosCiruelos.padel_club_api.usuarios.entity;

import static com.LosCiruelos.padel_club_api.TestData.AHORA;
import static com.LosCiruelos.padel_club_api.TestData.usuario;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.LosCiruelos.padel_club_api.common.exception.EstadoInvalidoException;
import com.LosCiruelos.padel_club_api.common.exception.ReservaException;
import com.LosCiruelos.padel_club_api.notificaciones.aviso.TipoAviso;
import com.LosCiruelos.padel_club_api.usuarios.entity.enums.CanalNotificacion;
import com.LosCiruelos.padel_club_api.usuarios.entity.enums.Categoria;
import com.LosCiruelos.padel_club_api.usuarios.entity.enums.Role;

class UsuarioTest {

    @Test
    void elegirWhatsappRegistraElConsentimientoSoloLaPrimeraVez() {
        Usuario usuario = usuario(1L, Role.CLIENTE);
        usuario.setTelefono("5491122334455");

        usuario.elegirCanal(CanalNotificacion.WHATSAPP, AHORA, true);
        usuario.elegirCanal(CanalNotificacion.WHATSAPP, AHORA.plusDays(1), true);

        assertThat(usuario.getCanalNotificaciones()).isEqualTo(CanalNotificacion.WHATSAPP);
        assertThat(usuario.getAceptaWhatsappAt()).isEqualTo(AHORA);
    }

    @Test
    void sinTelefonoNoSePuedeElegirWhatsapp() {
        Usuario usuario = usuario(1L, Role.CLIENTE);
        usuario.setTelefono(null);

        assertThatThrownBy(() -> usuario.elegirCanal(CanalNotificacion.WHATSAPP, AHORA, true))
                .isInstanceOf(ReservaException.class)
                .hasMessage("Cargá tu teléfono para recibir avisos por WhatsApp.");
        assertThat(usuario.getCanalNotificaciones()).isEqualTo(CanalNotificacion.EMAIL);
    }

    @Test
    void volverAlMailRetiraElConsentimiento() {
        Usuario usuario = usuario(1L, Role.CLIENTE);
        usuario.setTelefono("5491122334455");
        usuario.elegirCanal(CanalNotificacion.WHATSAPP, AHORA, true);

        usuario.elegirCanal(CanalNotificacion.EMAIL, AHORA, true);

        assertThat(usuario.getCanalNotificaciones()).isEqualTo(CanalNotificacion.EMAIL);
        assertThat(usuario.getAceptaWhatsappAt()).isNull();
    }

    @Test
    void cadaRolTieneSoloSuPerfil() {
        Usuario usuario = usuario(1L, null);

        usuario.asignarRol(Role.CLIENTE);
        assertThat(usuario.getRol()).isEqualTo(Role.CLIENTE);
        assertThat(usuario.getClienteProfile().getUsuario()).isSameAs(usuario);
        assertThat(usuario.getAdminProfile()).isNull();

        usuario.asignarRol(Role.EMPLEADO);
        assertThat(usuario.getClienteProfile()).isNull();
        assertThat(usuario.getEmpleadoProfile().getUsuario()).isSameAs(usuario);

        usuario.asignarRol(Role.PROFESOR);
        assertThat(usuario.getEmpleadoProfile()).isNull();
        assertThat(usuario.getProfesorProfile()).isNotNull();

        usuario.asignarRol(Role.ADMIN);
        assertThat(usuario.getProfesorProfile()).isNull();
        assertThat(usuario.getAdminProfile().quiere(TipoAviso.RESERVA_CONFIRMADA)).isTrue();
    }

    @Test
    void mantenerElRolConservaSuPerfil() {
        Usuario cliente = usuario(1L, null);
        cliente.asignarRol(Role.CLIENTE);
        ClienteProfile perfil = cliente.getClienteProfile();
        perfil.setCategoria(Categoria.Cuarta);

        cliente.asignarRol(Role.CLIENTE);

        assertThat(cliente.getClienteProfile()).isSameAs(perfil);
        assertThat(cliente.getClienteProfile().getCategoria()).isEqualTo(Categoria.Cuarta);
    }

    @Test
    void losRecordatoriosYLasPromocionesSonSoloParaClientes() {
        Usuario empleado = usuario(2L, Role.EMPLEADO);
        empleado.asignarRol(Role.EMPLEADO);

        assertThat(empleado.recibeRecordatorios()).isFalse();
        assertThat(empleado.recibePromociones()).isFalse();
    }

    @Test
    void sinWhatsappParaClientesElClienteNoPuedeElegirloPeroElPersonalSi() {
        Usuario cliente = usuario(1L, Role.CLIENTE);
        cliente.setTelefono("5491122334455");
        Usuario admin = usuario(2L, Role.ADMIN);
        admin.setTelefono("5491166778899");

        assertThatThrownBy(() -> cliente.elegirCanal(CanalNotificacion.WHATSAPP, AHORA, false))
                .isInstanceOf(EstadoInvalidoException.class)
                .hasMessage("El club no manda avisos por WhatsApp por ahora: te llegan por mail.");
        assertThat(cliente.getCanalNotificaciones()).isEqualTo(CanalNotificacion.EMAIL);
        cliente.elegirCanal(CanalNotificacion.EMAIL, AHORA, false); // el mail siempre se puede elegir

        admin.elegirCanal(CanalNotificacion.WHATSAPP, AHORA, false);
        assertThat(admin.getCanalNotificaciones()).isEqualTo(CanalNotificacion.WHATSAPP);
    }

    @Test
    void sinWhatsappParaClientesAlClienteLeAlcanzaConCargarElCelular() {
        Usuario cliente = usuario(1L, Role.CLIENTE);
        cliente.setTelefonoVerificado(false);
        Usuario admin = usuario(2L, Role.ADMIN);
        admin.setTelefonoVerificado(false);

        assertThat(cliente.datosPersonalesCompletos(true)).isFalse();
        assertThat(cliente.datosPersonalesCompletos(false)).isTrue();
        assertThat(admin.datosPersonalesCompletos(false)).isFalse(); // el personal sí lo verifica

        cliente.setTelefono(null);
        assertThat(cliente.datosPersonalesCompletos(false)).isFalse();
    }
}
