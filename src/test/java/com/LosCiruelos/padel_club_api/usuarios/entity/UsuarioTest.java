package com.LosCiruelos.padel_club_api.usuarios.entity;

import static com.LosCiruelos.padel_club_api.TestData.AHORA;
import static com.LosCiruelos.padel_club_api.TestData.usuario;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.LosCiruelos.padel_club_api.Exceptions.ReservaException;
import com.LosCiruelos.padel_club_api.usuarios.entity.enums.CanalNotificacion;
import com.LosCiruelos.padel_club_api.usuarios.entity.enums.Role;

class UsuarioTest {

    @Test
    void elegirWhatsappRegistraElConsentimientoSoloLaPrimeraVez() {
        Usuario usuario = usuario(1L, Role.CLIENTE);
        usuario.setTelefono("5491122334455");

        usuario.elegirCanal(CanalNotificacion.WHATSAPP, AHORA);
        usuario.elegirCanal(CanalNotificacion.WHATSAPP, AHORA.plusDays(1));

        assertThat(usuario.getCanalNotificaciones()).isEqualTo(CanalNotificacion.WHATSAPP);
        assertThat(usuario.getAceptaWhatsappAt()).isEqualTo(AHORA);
    }

    @Test
    void sinTelefonoNoSePuedeElegirWhatsapp() {
        Usuario usuario = usuario(1L, Role.CLIENTE);
        usuario.setTelefono(null);

        assertThatThrownBy(() -> usuario.elegirCanal(CanalNotificacion.WHATSAPP, AHORA))
                .isInstanceOf(ReservaException.class)
                .hasMessage("Cargá tu teléfono para recibir avisos por WhatsApp.");
        assertThat(usuario.getCanalNotificaciones()).isEqualTo(CanalNotificacion.EMAIL);
    }

    @Test
    void volverAlMailRetiraElConsentimiento() {
        Usuario usuario = usuario(1L, Role.CLIENTE);
        usuario.setTelefono("5491122334455");
        usuario.elegirCanal(CanalNotificacion.WHATSAPP, AHORA);

        usuario.elegirCanal(CanalNotificacion.EMAIL, AHORA);

        assertThat(usuario.getCanalNotificaciones()).isEqualTo(CanalNotificacion.EMAIL);
        assertThat(usuario.getAceptaWhatsappAt()).isNull();
    }
}
