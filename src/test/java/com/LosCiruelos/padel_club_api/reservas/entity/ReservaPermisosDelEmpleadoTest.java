package com.LosCiruelos.padel_club_api.reservas.entity;

import static com.LosCiruelos.padel_club_api.TestData.HOY;
import static com.LosCiruelos.padel_club_api.TestData.reserva;
import static com.LosCiruelos.padel_club_api.TestData.usuario;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.LosCiruelos.padel_club_api.common.exception.PermisoDenegadoException;
import com.LosCiruelos.padel_club_api.reservas.entity.enums.EstadoReserva;
import com.LosCiruelos.padel_club_api.usuarios.entity.Usuario;
import com.LosCiruelos.padel_club_api.usuarios.entity.enums.Role;

/** El EMPLEADO ve y opera las reservas de hoy en adelante; el ADMIN, también las de días anteriores. */
class ReservaPermisosDelEmpleadoTest {

    private final Usuario admin = usuario(3L, Role.ADMIN);
    private final Usuario empleado = usuario(4L, Role.EMPLEADO);

    private Reserva delDia(int dias) {
        Reserva r = reserva(10L, usuario(1L, Role.CLIENTE), EstadoReserva.SEÑADA, "15000");
        r.setFechaReserva(HOY.plusDays(dias));
        return r;
    }

    @Test
    void unaDeAyerQuedaFueraDelEmpleadoPeroNoDelAdmin() {
        assertThat(delDia(-1).quedaFueraDelEmpleado(empleado, HOY)).isTrue();
        assertThat(delDia(-1).quedaFueraDelEmpleado(admin, HOY)).isFalse();
    }

    @Test
    void lasDeHoyYLasFuturasSonDelEmpleado() {
        assertThat(delDia(0).quedaFueraDelEmpleado(empleado, HOY)).isFalse();
        assertThat(delDia(5).quedaFueraDelEmpleado(empleado, HOY)).isFalse();
        delDia(0).exigirQueLaPuedaOperar(empleado, HOY);
    }

    @Test
    void elEmpleadoNoOperaUnaDeAyer() {
        assertThatThrownBy(() -> delDia(-1).exigirQueLaPuedaOperar(empleado, HOY))
                .isInstanceOf(PermisoDenegadoException.class)
                .hasMessage("Solo el ADMIN puede operar reservas de días anteriores.");
        delDia(-1).exigirQueLaPuedaOperar(admin, HOY);
    }
}
