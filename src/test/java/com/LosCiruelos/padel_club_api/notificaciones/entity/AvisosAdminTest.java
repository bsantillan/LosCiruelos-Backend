package com.LosCiruelos.padel_club_api.notificaciones.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.LosCiruelos.padel_club_api.notificaciones.aviso.TipoAviso;

class AvisosAdminTest {

    @Test
    void sinElegirNadaRecibeTodosLosAvisosDelAdmin() {
        AvisosAdmin avisos = AvisosAdmin.porDefecto(1L);

        assertThat(avisos.quiere(TipoAviso.RESERVA_CONFIRMADA)).isTrue();
        assertThat(avisos.quiere(TipoAviso.SALDO_COBRADO)).isTrue();
        assertThat(avisos.quiere(TipoAviso.RESERVA_CANCELADA)).isTrue();
        assertThat(avisos.quiere(TipoAviso.REEMBOLSO_MANUAL)).isTrue();
        assertThat(avisos.quiere(TipoAviso.REEMBOLSO_REALIZADO)).isTrue();
    }

    @Test
    void losAvisosDelClienteNoLeLlegan() {
        AvisosAdmin avisos = AvisosAdmin.porDefecto(1L);

        assertThat(avisos.quiere(TipoAviso.RECORDATORIO)).isFalse();
        assertThat(avisos.quiere(TipoAviso.CANCELADA_POR_CLUB)).isFalse();
    }

    @Test
    void elReembolsoRealizadoSigueLoQueEligioParaLasCancelaciones() {
        AvisosAdmin avisos = AvisosAdmin.porDefecto(1L);
        avisos.elegir(null, true, true, false, true);

        assertThat(avisos.quiere(TipoAviso.RESERVA_CANCELADA)).isFalse();
        assertThat(avisos.quiere(TipoAviso.REEMBOLSO_REALIZADO)).isFalse();
        assertThat(avisos.quiere(TipoAviso.REEMBOLSO_MANUAL)).isTrue();
    }

    @Test
    void pausarTodoConservaLoQueEligioDeCadaAviso() {
        AvisosAdmin avisos = AvisosAdmin.porDefecto(1L);
        avisos.elegir(false, false, true, true, true);

        assertThat(avisos.quiere(TipoAviso.SALDO_COBRADO)).isFalse();
        assertThat(avisos.getSaldoCobrado()).isTrue();
        assertThat(avisos.getReservaConfirmada()).isFalse();

        avisos.elegir(null, false, true, true, true); // sin activos queda como estaba
        assertThat(avisos.getActivos()).isFalse();
        avisos.elegir(true, false, true, true, true);
        assertThat(avisos.quiere(TipoAviso.SALDO_COBRADO)).isTrue();
    }
}
