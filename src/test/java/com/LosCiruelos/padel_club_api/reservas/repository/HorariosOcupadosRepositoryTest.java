package com.LosCiruelos.padel_club_api.reservas.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import com.LosCiruelos.padel_club_api.TestData;
import com.LosCiruelos.padel_club_api.club.dto.response.FueraDeHorarioResponse;
import com.LosCiruelos.padel_club_api.club.entity.Cancha;
import com.LosCiruelos.padel_club_api.club.entity.Configuracion;
import com.LosCiruelos.padel_club_api.mapper.FueraDeHorarioMapper;
import com.LosCiruelos.padel_club_api.reservas.entity.Reserva;
import com.LosCiruelos.padel_club_api.reservas.entity.TurnoFijo;
import com.LosCiruelos.padel_club_api.reservas.entity.enums.EstadoReserva;
import com.LosCiruelos.padel_club_api.reservas.entity.enums.TipoReserva;
import com.LosCiruelos.padel_club_api.reservas.service.HorariosOcupadosService;
import com.LosCiruelos.padel_club_api.usuarios.entity.Usuario;
import com.LosCiruelos.padel_club_api.usuarios.entity.enums.Role;

/**
 * Qué reservas y turnos fijos quedan afuera de un horario nuevo (H2). Hoy es el martes 13/10/2026
 * a las 10:00; el horario nuevo abre a las 08:30 de lunes a viernes, y ni sábados ni domingos.
 */
@DataJpaTest(properties = { "spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect" })
class HorariosOcupadosRepositoryTest {

    private static final LocalDate HOY = TestData.HOY; // martes 13/10/2026
    private static final LocalDate LUNES = LocalDate.of(2026, 10, 19);

    @Autowired
    private TestEntityManager em;

    @Autowired
    private ReservaRepository reservaRepository;

    @Autowired
    private TurnoFijoRepository turnoFijoRepository;

    private Usuario cliente;
    private Cancha cancha;
    private HorariosOcupadosService servicio;
    private Configuracion horarioNuevo;

    @BeforeEach
    void datos() {
        cliente = em.persist(TestData.usuario(null, Role.CLIENTE));
        cancha = em.persist(Cancha.builder().numero(2).descripcion("Cancha 2").build());
        servicio = new HorariosOcupadosService(reservaRepository, turnoFijoRepository, new FueraDeHorarioMapper(),
                TestData.reloj(TestData.AHORA));
        horarioNuevo = TestData.configuracion();
        horarioNuevo.cambiarHorarios(List.of("Lunes", "Martes", "Miércoles", "Jueves", "Viernes").stream()
                .map(d -> new Configuracion.Horario(d, LocalTime.of(8, 30), LocalTime.of(23, 0)))
                .toList());
    }

    private Reserva reserva(LocalDate fecha, String inicio, String fin, EstadoReserva estado) {
        return em.persist(Reserva.builder().cancha(cancha).cliente(cliente).createdBy(cliente).fechaReserva(fecha)
                .horaInicio(LocalTime.parse(inicio)).horaFin(LocalTime.parse(fin)).estado(estado)
                .tipo(TipoReserva.NORMAL).montoTotal(new BigDecimal("15000")).createdAt(TestData.AHORA).build());
    }

    private TurnoFijo turnoFijo(DayOfWeek dia, String inicio, String fin, boolean activo) {
        return em.persist(TurnoFijo.builder().cancha(cancha).diaSemana(dia).horaInicio(LocalTime.parse(inicio))
                .horaFin(LocalTime.parse(fin)).desde(HOY).activo(activo).createdBy(cliente)
                .createdAt(TestData.AHORA).build());
    }

    @Test
    void listaLasReservasYTurnosFijosQueQuedanAfuera() {
        Reserva lunesTemprano = reserva(LUNES, "08:00", "09:30", EstadoReserva.SEÑADA);
        reserva(LUNES, "08:30", "10:00", EstadoReserva.PENDIENTE); // entra
        Reserva sabado = reserva(LocalDate.of(2026, 10, 17), "10:00", "11:30", EstadoReserva.CONFIRMADA);
        reserva(LUNES, "08:00", "09:30", EstadoReserva.CANCELADA); // cancelada: no cuenta
        reserva(LUNES.plusDays(1), "08:00", "09:30", EstadoReserva.EXPIRADA); // vencida: no cuenta
        reserva(HOY, "08:00", "09:30", EstadoReserva.SEÑADA); // ya empezó: no cuenta
        reserva(HOY.minusDays(1), "08:00", "09:30", EstadoReserva.COMPLETADA); // pasada
        TurnoFijo lunesFijo = turnoFijo(DayOfWeek.MONDAY, "08:00", "09:30", true);
        turnoFijo(DayOfWeek.TUESDAY, "09:00", "10:30", true); // entra
        turnoFijo(DayOfWeek.SUNDAY, "10:00", "11:30", false); // dado de baja: no cuenta
        TurnoFijo domingo = turnoFijo(DayOfWeek.SUNDAY, "18:00", "19:30", true);
        em.flush();
        em.clear();

        FueraDeHorarioResponse fuera = servicio.fueraDeHorario(horarioNuevo);

        assertThat(fuera.reservasFueraDeHorario()).extracting(FueraDeHorarioResponse.Reserva::id)
                .containsExactly(sabado.getId(), lunesTemprano.getId());
        assertThat(fuera.reservasFueraDeHorario().get(1)).satisfies(r -> {
            assertThat(r.fecha()).isEqualTo(LUNES);
            assertThat(r.horaInicio()).isEqualTo(LocalTime.of(8, 0));
            assertThat(r.canchaNumero()).isEqualTo(2);
            assertThat(r.clienteNombre()).isEqualTo((cliente.getNombre() + " " + cliente.getApellido()).trim());
            assertThat(r.tipo()).isEqualTo("NORMAL");
        });
        assertThat(fuera.turnosFijosFueraDeHorario()).extracting(FueraDeHorarioResponse.TurnoFijo::id)
                .containsExactly(lunesFijo.getId(), domingo.getId());

        // con el horario de siempre no queda nada afuera
        FueraDeHorarioResponse actual = servicio.fueraDeHorario(TestData.configuracion());
        assertThat(actual.reservasFueraDeHorario()).isEmpty();
        assertThat(actual.turnosFijosFueraDeHorario()).isEmpty();
    }
}
