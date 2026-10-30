package com.LosCiruelos.padel_club_api.usuarios.entity;

import static com.LosCiruelos.padel_club_api.TestData.usuario;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.LosCiruelos.padel_club_api.notificaciones.aviso.TipoAviso;
import com.LosCiruelos.padel_club_api.usuarios.entity.enums.Role;

/** Los avisos que elige el ADMIN en su perfil. */
class AdminProfileTest {

    private final Usuario admin = usuario(1L, Role.ADMIN);

    @Test
    void sinElegirNadaRecibeTodosLosAvisosDelAdmin() {
        AdminProfile avisos = AdminProfile.porDefecto(admin);

        assertThat(avisos.quiere(TipoAviso.RESERVA_CONFIRMADA)).isTrue();
        assertThat(avisos.quiere(TipoAviso.SALDO_COBRADO)).isTrue();
        assertThat(avisos.quiere(TipoAviso.RESERVA_CANCELADA)).isTrue();
        assertThat(avisos.quiere(TipoAviso.REEMBOLSO_MANUAL)).isTrue();
        assertThat(avisos.quiere(TipoAviso.REEMBOLSO_REALIZADO)).isTrue();
    }

    @Test
    void losAvisosDelClienteNoLeLlegan() {
        AdminProfile avisos = AdminProfile.porDefecto(admin);

        assertThat(avisos.quiere(TipoAviso.RECORDATORIO)).isFalse();
        assertThat(avisos.quiere(TipoAviso.CANCELADA_POR_CLUB)).isFalse();
    }

    @Test
    void elReembolsoRealizadoSigueLoQueEligioParaLasCancelaciones() {
        AdminProfile avisos = AdminProfile.porDefecto(admin);
        avisos.elegir(null, true, true, false, true);

        assertThat(avisos.quiere(TipoAviso.RESERVA_CANCELADA)).isFalse();
        assertThat(avisos.quiere(TipoAviso.REEMBOLSO_REALIZADO)).isFalse();
        assertThat(avisos.quiere(TipoAviso.REEMBOLSO_MANUAL)).isTrue();
    }

    @Test
    void pausarTodoConservaLoQueEligioDeCadaAviso() {
        AdminProfile avisos = AdminProfile.porDefecto(admin);
        avisos.elegir(false, false, true, true, true);

        assertThat(avisos.quiere(TipoAviso.SALDO_COBRADO)).isFalse();
        assertThat(avisos.getSaldoCobrado()).isTrue();
        assertThat(avisos.getReservaConfirmada()).isFalse();

        avisos.elegir(null, false, true, true, true); // sin activos queda como estaba
        assertThat(avisos.getActivos()).isFalse();
        avisos.elegir(true, false, true, true, true);
        assertThat(avisos.quiere(TipoAviso.SALDO_COBRADO)).isTrue();
    }

    @Test
    void sinPerfilTodaviaRecibeTodosLosAvisos() {
        assertThat(admin.getAdminProfile()).isNull();

        assertThat(admin.avisosAdmin().quiere(TipoAviso.RESERVA_CONFIRMADA)).isTrue();
        assertThat(admin.avisosAdmin().quiere(TipoAviso.REEMBOLSO_MANUAL)).isTrue();
        assertThat(admin.getAdminProfile()).isNull(); // leerlo no le crea el perfil
    }
}
