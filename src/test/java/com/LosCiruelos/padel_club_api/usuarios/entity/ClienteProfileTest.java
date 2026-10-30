package com.LosCiruelos.padel_club_api.usuarios.entity;

import static com.LosCiruelos.padel_club_api.TestData.AHORA;
import static com.LosCiruelos.padel_club_api.TestData.usuario;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.LosCiruelos.padel_club_api.usuarios.exception.CategoriaException;
import com.LosCiruelos.padel_club_api.usuarios.entity.enums.Categoria;
import com.LosCiruelos.padel_club_api.usuarios.entity.enums.Posicion;
import com.LosCiruelos.padel_club_api.usuarios.entity.enums.Role;

/** Reglas del perfil de jugador: si está completo y cuándo se puede cambiar la categoría. */
class ClienteProfileTest {

    private final Usuario cliente = usuario(1L, Role.CLIENTE);

    @Test
    void estaCompletoConDatosPersonalesCategoriaYPosicion() {
        ClienteProfile perfil = ClienteProfile.builder().usuario(cliente).categoria(Categoria.Cuarta)
                .posicion(Posicion.Drive).build();
        assertThat(perfil.estaCompleto()).isTrue();

        perfil.setPosicion(null);
        assertThat(perfil.estaCompleto()).isFalse();

        perfil.setPosicion(Posicion.Drive);
        cliente.setTelefonoVerificado(false);
        assertThat(perfil.estaCompleto()).isFalse();
    }

    @Test
    void laPrimeraCargaDeLaCategoriaArrancaElPlazoDe24Horas() {
        ClienteProfile perfil = ClienteProfile.builder().usuario(cliente).build();

        perfil.cambiarCategoria(Categoria.Cuarta, AHORA);

        assertThat(perfil.getCategoria()).isEqualTo(Categoria.Cuarta);
        assertThat(perfil.getCategoriaActualizadaAt()).isEqualTo(AHORA);
        assertThat(perfil.getCategoriaModificada()).isFalse();
        assertThat(perfil.puedeCambiarCategoria(AHORA.plusHours(24))).isTrue();
        assertThat(perfil.puedeCambiarCategoria(AHORA.plusHours(25))).isFalse();
    }

    @Test
    void laCategoriaSeCambiaUnaSolaVezDentroDelPlazo() {
        ClienteProfile perfil = ClienteProfile.builder().usuario(cliente).categoria(Categoria.Cuarta)
                .categoriaActualizadaAt(AHORA).build();

        perfil.cambiarCategoria(Categoria.Cuarta, AHORA.plusHours(30)); // la misma: no cuenta como cambio
        perfil.cambiarCategoria(Categoria.Quinta, AHORA.plusHours(1));

        assertThat(perfil.getCategoria()).isEqualTo(Categoria.Quinta);
        assertThat(perfil.getCategoriaModificada()).isTrue();
        assertThatThrownBy(() -> perfil.cambiarCategoria(Categoria.Sexta, AHORA.plusHours(2)))
                .isInstanceOf(CategoriaException.class)
                .hasMessage("Ya modificaste tu categoría. Contactá al club para realizar otro cambio.");
    }

    @Test
    void pasadoElPlazoNoSePuedeCambiar() {
        ClienteProfile perfil = ClienteProfile.builder().usuario(cliente).categoria(Categoria.Cuarta)
                .categoriaActualizadaAt(AHORA).build();

        assertThatThrownBy(() -> perfil.cambiarCategoria(Categoria.Quinta, AHORA.plusHours(25)))
                .isInstanceOf(CategoriaException.class)
                .hasMessage("El período para modificar tu categoría expiró. Contactá al club para realizar cambios.");
    }

    @Test
    void elClienteEligeSusAvisosOpcionales() {
        ClienteProfile perfil = ClienteProfile.builder().usuario(cliente).build();
        cliente.setClienteProfile(perfil);
        // por defecto recibe recordatorios y no promociones
        assertThat(cliente.recibeRecordatorios()).isTrue();
        assertThat(cliente.recibePromociones()).isFalse();

        perfil.elegirSuscripciones(false, true);

        assertThat(cliente.recibeRecordatorios()).isFalse();
        assertThat(cliente.recibePromociones()).isTrue();
    }
}
