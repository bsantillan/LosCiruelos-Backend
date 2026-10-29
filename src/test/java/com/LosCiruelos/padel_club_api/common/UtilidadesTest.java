package com.LosCiruelos.padel_club_api.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.stream.Stream;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

import com.LosCiruelos.padel_club_api.TestData;
import com.LosCiruelos.padel_club_api.usuarios.entity.Usuario;
import com.LosCiruelos.padel_club_api.usuarios.entity.enums.Role;
import com.LosCiruelos.padel_club_api.common.exception.PermisoDenegadoException;
import com.LosCiruelos.padel_club_api.common.exception.ReservaException;

/** Utilidades compartidas de {@code common}. */
class UtilidadesTest {

    @Nested
    class RelojDelClub {

        @Test
        void unRelojFijoDevuelveSiempreLaMismaHoraDeArgentina() {
            Reloj reloj = Reloj.fijo(LocalDateTime.of(2026, 10, 13, 10, 0));

            assertThat(reloj.ahora()).isEqualTo(LocalDateTime.of(2026, 10, 13, 10, 0));
            assertThat(reloj.hoy()).isEqualTo(LocalDate.of(2026, 10, 13));
            assertThat(reloj.horaActual()).isEqualTo(LocalTime.of(10, 0));
            // 10:00 en Argentina son las 13:00 UTC
            assertThat(reloj.instante().toString()).isEqualTo("2026-10-13T13:00:00Z");
        }

        @Test
        void elRelojDelSistemaTienePrecisionDeMilisegundos() {
            Reloj reloj = Reloj.delSistema();
            for (int i = 0; i < 20; i++) {
                assertThat(reloj.ahora().getNano() % 1_000_000).isZero();
            }
        }
    }

    @Nested
    class Turnos {

        private final Turno turno = new Turno(LocalDate.of(2026, 10, 14), LocalTime.of(18, 0), LocalTime.of(19, 30));

        @Test
        void duracionEInicioYFinEnLaFecha() {
            assertThat(turno.duracionMinutos()).isEqualTo(90);
            assertThat(turno.inicioEnFecha()).isEqualTo(LocalDateTime.of(2026, 10, 14, 18, 0));
            assertThat(turno.finEnFecha()).isEqualTo(LocalDateTime.of(2026, 10, 14, 19, 30));
        }

        @Test
        void seSuperponenSiUnoEmpiezaAntesDeQueTermineElOtro() {
            assertThat(Turno.seSuperponen(LocalTime.of(18, 0), LocalTime.of(19, 30), LocalTime.of(19, 0),
                    LocalTime.of(20, 30))).isTrue();
            // los bordes no cuentan: uno termina cuando empieza el otro
            assertThat(Turno.seSuperponen(LocalTime.of(18, 0), LocalTime.of(19, 30), LocalTime.of(19, 30),
                    LocalTime.of(21, 0))).isFalse();
            assertThat(turno.seSuperponeCon(new Turno(LocalDate.of(2026, 10, 15), LocalTime.of(18, 0),
                    LocalTime.of(19, 30)))).isFalse();
        }

        @Test
        void empezoYTermino() {
            assertThat(turno.empezo(LocalDateTime.of(2026, 10, 14, 17, 59))).isFalse();
            assertThat(turno.empezo(LocalDateTime.of(2026, 10, 14, 18, 0))).isTrue();
            assertThat(turno.termino(LocalDateTime.of(2026, 10, 14, 19, 29))).isFalse();
            assertThat(turno.termino(LocalDateTime.of(2026, 10, 14, 19, 30))).isTrue();
        }
    }

    @Nested
    class Montos {

        @Test
        void redondeaACentavosYSuma() {
            assertThat(Dinero.redondear(new BigDecimal("7500.005"))).isEqualTo(new BigDecimal("7500.01"));
            assertThat(Dinero.redondear(new BigDecimal("15000"))).isEqualTo(new BigDecimal("15000.00"));
            assertThat(Dinero.sumar(Stream.of(new BigDecimal("1.50"), new BigDecimal("2")))).isEqualByComparingTo("3.5");
            assertThat(Dinero.sumar(Stream.empty())).isEqualByComparingTo("0");
        }

        @Test
        void textoEnPesosArgentinos() {
            assertThat(Dinero.texto(new BigDecimal("7500"))).isEqualTo("$7.500");
            assertThat(Dinero.texto(new BigDecimal("7500.50"))).isEqualTo("$7.500,5");
            assertThat(Dinero.texto(null)).isEqualTo("$0");
        }
    }

    @Nested
    class TextosDeFechas {

        @Test
        void fechaHoraYDiasEnCastellano() {
            assertThat(Fechas.textoFecha(LocalDate.of(2026, 10, 14))).isEqualTo("miércoles 14/10");
            assertThat(Fechas.textoHora(LocalTime.of(9, 5))).isEqualTo("09:05");
            assertThat(Fechas.nombreDia(DayOfWeek.SATURDAY)).isEqualTo("sábado");
            assertThat(Fechas.nombreDiaCapitalizado(DayOfWeek.WEDNESDAY)).isEqualTo("Miércoles");
        }
    }

    @Nested
    class Paginas {

        @Test
        void validaPaginaYTamanio() {
            assertThat(Paginacion.pagina(2, 10, 50, Sort.unsorted()).getPageNumber()).isEqualTo(2);
            assertThatThrownBy(() -> Paginacion.validar(-1, 10, 50))
                    .isInstanceOf(ReservaException.class)
                    .hasMessage("La página no puede ser negativa.");
            assertThatThrownBy(() -> Paginacion.validar(0, 51, 50))
                    .hasMessage("El tamaño de página tiene que estar entre 1 y 50.");
            assertThatThrownBy(() -> Paginacion.validar(0, 0, 50)).isInstanceOf(ReservaException.class);
        }
    }

    @Nested
    class RolesDelPersonal {

        @Test
        void personalYAdmin() {
            Usuario admin = TestData.usuario(1L, Role.ADMIN);
            Usuario empleado = TestData.usuario(2L, Role.EMPLEADO);
            Usuario cliente = TestData.usuario(3L, Role.CLIENTE);

            assertThat(Roles.esPersonal(admin)).isTrue();
            assertThat(Roles.esPersonal(empleado)).isTrue();
            assertThat(Roles.esPersonal(cliente)).isFalse();
            Roles.exigirAdmin(admin, "no");
            assertThatThrownBy(() -> Roles.exigirAdmin(empleado, "Solo el ADMIN."))
                    .isInstanceOf(PermisoDenegadoException.class)
                    .hasMessage("Solo el ADMIN.");
        }
    }
}
