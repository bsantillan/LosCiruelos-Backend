package com.LosCiruelos.padel_club_api.club.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.LosCiruelos.padel_club_api.TestData;
import com.LosCiruelos.padel_club_api.club.entity.Configuracion.Horario;
import com.LosCiruelos.padel_club_api.common.exception.ReservaException;

/** Los cambios que hace el ADMIN en la configuración del club (sin Spring). */
class ConfiguracionCambiosTest {

    private final Configuracion config = TestData.configuracion();

    private static Horario horario(String dia, String apertura, String cierre) {
        return new Horario(dia, LocalTime.parse(apertura), LocalTime.parse(cierre));
    }

    @Test
    void laDuracionDeLosTurnosVaEnBloquesDe30YLaMinimaNoSuperaLaMaxima() {
        config.cambiarDuracionDeTurnos(60, 120);
        assertThat(config.getDuracionMinimaTurno()).isEqualTo(60);
        assertThat(config.getDuracionMaximaTurno()).isEqualTo(120);

        assertThatThrownBy(() -> config.cambiarDuracionDeTurnos(45, 120))
                .isInstanceOf(ReservaException.class)
                .hasMessage("La duración de los turnos tiene que ser un múltiplo de 30 minutos.");
        assertThatThrownBy(() -> config.cambiarDuracionDeTurnos(150, 120))
                .hasMessage("La duración mínima del turno no puede superar la máxima.");
        config.cambiarDuracionDeTurnos(90, 90); // iguales: un solo largo de turno
    }

    @Test
    void paraDevolverTodoHaceFaltaAlMenosLaAnticipacionDeLaSenia() {
        config.cambiarPoliticaCancelacion(2880, 1440);
        assertThat(config.getMinutosReembolsoTotal()).isEqualTo(2880);
        assertThat(config.getMinutosReembolsoSenia()).isEqualTo(1440);

        assertThatThrownBy(() -> config.cambiarPoliticaCancelacion(60, 120))
                .isInstanceOf(ReservaException.class)
                .hasMessage("La anticipación para devolver todo no puede ser menor que la de devolver la seña.");
    }

    @Test
    void losHorariosReemplazanLosDiasDeApertura() {
        DiaApertura lunes = config.getDias_apertura().get(0);

        config.cambiarHorarios(List.of(horario("domingo", "10:00", "18:00"), horario("LUNES", "07:00", "23:30"),
                horario("miercoles", "08:00", "22:00")));

        // los nombres quedan como se guardan, en orden de lunes a domingo; los que no vienen, cerrados
        assertThat(config.diasEnOrden()).extracting(DiaApertura::getDia)
                .containsExactly("Lunes", "Miércoles", "Domingo");
        // el lunes es el mismo registro, con el horario nuevo
        assertThat(config.getDias_apertura()).contains(lunes);
        assertThat(lunes.getHorario_inicio()).isEqualTo(LocalTime.of(7, 0));
        assertThat(lunes.getHorario_fin()).isEqualTo(LocalTime.of(23, 30));
    }

    @Test
    void cadaDiaUnaVezYConUnHorarioQueAlcanceParaUnTurno() {
        assertThatThrownBy(() -> config.cambiarHorarios(List.of(horario("Feriado", "08:00", "20:00"))))
                .isInstanceOf(ReservaException.class)
                .hasMessage("Día inválido: Feriado. Usá Lunes a Domingo.");
        assertThatThrownBy(() -> config.cambiarHorarios(List.of(horario("Lunes", "08:00", "20:00"),
                horario("lunes", "09:00", "20:00"))))
                .hasMessage("El lunes está repetido.");
        assertThatThrownBy(() -> config.cambiarHorarios(List.of(horario("Sábado", "20:00", "08:00"))))
                .hasMessage("El sábado, la apertura tiene que ser antes del cierre.");
        // el turno mínimo es de 90 minutos
        assertThatThrownBy(() -> config.cambiarHorarios(List.of(horario("Martes", "08:00", "09:00"))))
                .hasMessage("El martes abre menos que el turno mínimo (90 minutos).");
        // si algo falla, los días no cambian
        assertThat(config.getDias_apertura()).hasSize(7);
    }

    @Test
    void elPlazoParaPagarLaSeniaOnlineNoPasaDelInicioDelTurno() {
        // 120 minutos por defecto
        assertThat(config.vencimientoSeniaOnline(TestData.AHORA, TestData.AHORA.plusDays(1)))
                .isEqualTo(TestData.AHORA.plusMinutes(120));
        assertThat(config.vencimientoSeniaOnline(TestData.AHORA, TestData.AHORA.plusMinutes(45)))
                .isEqualTo(TestData.AHORA.plusMinutes(45));
        config.setMinutosPagoSeniaOnline(30);
        assertThat(config.vencimientoSeniaOnline(TestData.AHORA, TestData.AHORA.plusDays(1)))
                .isEqualTo(TestData.AHORA.plusMinutes(30));
    }

    @Test
    void unDiaCerradoNoAbreYSePuedeReabrir() {
        java.time.LocalDate navidad = java.time.LocalDate.of(2026, 12, 25);
        config.cerrar(navidad, "Navidad", 1L, TestData.AHORA);

        assertThat(config.cierreDel(navidad)).isPresent();
        assertThat(config.motivoCierre(navidad)).isEqualTo("El club está cerrado el viernes 25/12: Navidad.");
        assertThatThrownBy(() -> config.diaApertura(navidad))
                .isInstanceOf(com.LosCiruelos.padel_club_api.club.exception.DiaSinAperturaException.class)
                .hasMessage("El club está cerrado el viernes 25/12: Navidad.");
        // el viernes siguiente abre normal
        assertThat(config.diaApertura(navidad.plusWeeks(1)).getDia()).isEqualTo("Viernes");
        assertThat(config.cierresDesde(TestData.HOY)).extracting(DiaCerrado::getFecha).containsExactly(navidad);
        assertThat(config.cierresDesde(navidad.plusDays(1))).isEmpty();

        assertThatThrownBy(() -> config.cerrar(navidad, "Otra vez", 1L, TestData.AHORA))
                .isInstanceOf(com.LosCiruelos.padel_club_api.common.exception.EstadoInvalidoException.class)
                .hasMessage("El club ya está cerrado el viernes 25/12.");
        assertThatThrownBy(() -> config.cerrar(TestData.HOY.minusDays(1), "Tarde", 1L, TestData.AHORA))
                .isInstanceOf(ReservaException.class)
                .hasMessage("No se puede cerrar un día que ya pasó.");

        config.reabrir(navidad);
        assertThat(config.motivoCierre(navidad)).isNull();
        assertThatThrownBy(() -> config.reabrir(navidad))
                .isInstanceOf(com.LosCiruelos.padel_club_api.club.exception.DiaCerradoNoEncontradoException.class)
                .hasMessage("El club no está cerrado el viernes 25/12.");
    }

    @Test
    void unTurnoQueNoEntraEnElHorarioDiceElMotivo() {
        java.time.DayOfWeek lunes = java.time.DayOfWeek.MONDAY;
        // lunes de 08:00 a 23:00
        assertThat(config.motivoFueraDeHorario(lunes, LocalTime.of(8, 0), LocalTime.of(9, 30))).isNull();
        assertThat(config.motivoFueraDeHorario(lunes, LocalTime.of(21, 30), LocalTime.of(23, 0))).isNull();

        config.cambiarHorarios(List.of(horario("Lunes", "08:30", "22:00"), horario("Martes", "08:15", "23:00")));

        assertThat(config.motivoFueraDeHorario(lunes, LocalTime.of(8, 0), LocalTime.of(9, 30)))
                .isEqualTo("Fuera del horario del club: los lunes abre de 08:30 a 22:00.");
        assertThat(config.motivoFueraDeHorario(lunes, LocalTime.of(21, 0), LocalTime.of(22, 30)))
                .isEqualTo("Fuera del horario del club: los lunes abre de 08:30 a 22:00.");
        assertThat(config.motivoFueraDeHorario(java.time.DayOfWeek.TUESDAY, LocalTime.of(10, 0), LocalTime.of(11, 30)))
                .isEqualTo("No coincide con los turnos del club: los martes son cada 30 minutos desde las 08:15.");
        assertThat(config.motivoFueraDeHorario(java.time.DayOfWeek.SUNDAY, LocalTime.of(10, 0), LocalTime.of(11, 30)))
                .isEqualTo("El club ya no abre los domingos.");
        assertThat(config.motivoFueraDeHorario(java.time.DayOfWeek.SATURDAY, LocalTime.of(10, 0), LocalTime.of(11, 30)))
                .isEqualTo("El club ya no abre los sábados.");
    }
}
