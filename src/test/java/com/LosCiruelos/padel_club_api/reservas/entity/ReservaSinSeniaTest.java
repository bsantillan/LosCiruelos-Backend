package com.LosCiruelos.padel_club_api.reservas.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.LosCiruelos.padel_club_api.TestData;
import com.LosCiruelos.padel_club_api.reservas.entity.enums.EstadoReserva;
import com.LosCiruelos.padel_club_api.reservas.entity.enums.ModalidadPago;
import com.LosCiruelos.padel_club_api.usuarios.entity.enums.Role;

/** Una reserva SIN_SENIA (la cargó el ADMIN para cobrarla en el club) está confirmada aunque no tenga pagos. */
class ReservaSinSeniaTest {

    private static final BigDecimal SENIA = new BigDecimal("7500");

    private Reserva reserva(ModalidadPago modalidad, EstadoReserva estado) {
        Reserva r = TestData.reserva(10L, TestData.usuario(1L, Role.CLIENTE), estado, "15000");
        r.setModalidadPago(modalidad);
        return r;
    }

    @Test
    void sinNadaPagadoSigueConfirmada() {
        Reserva sinSenia = reserva(ModalidadPago.SIN_SENIA, EstadoReserva.CONFIRMADA);

        // por ejemplo, al sumarle productos: no se cancela por no tener nada pagado
        assertThat(sinSenia.recalcularEstado(SENIA, TestData.AHORA)).isFalse();
        assertThat(sinSenia.getEstado()).isEqualTo(EstadoReserva.CONFIRMADA);
        assertThat(sinSenia.confirmadaSinPagar()).isTrue();
    }

    @Test
    void unaDelClubOConfirmadaQueQuedaSinNadaSeCancelaComoSiempre() {
        Reserva enElClub = reserva(ModalidadPago.EN_EL_CLUB, EstadoReserva.SEÑADA);

        enElClub.recalcularEstado(SENIA, TestData.AHORA);

        assertThat(enElClub.getEstado()).isEqualTo(EstadoReserva.CANCELADA);
        assertThat(enElClub.confirmadaSinPagar()).isFalse();
    }
}
