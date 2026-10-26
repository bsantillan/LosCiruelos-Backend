package com.LosCiruelos.padel_club_api.Repository;

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
import org.springframework.data.domain.Pageable;

import com.LosCiruelos.padel_club_api.TestData;
import com.LosCiruelos.padel_club_api.Entities.Cancha;
import com.LosCiruelos.padel_club_api.Entities.Reserva;
import com.LosCiruelos.padel_club_api.Entities.ReservaJugador;
import com.LosCiruelos.padel_club_api.Entities.TurnoFijo;
import com.LosCiruelos.padel_club_api.Entities.Usuario;
import com.LosCiruelos.padel_club_api.Entities.Enum.EstadoReserva;
import com.LosCiruelos.padel_club_api.Entities.Enum.Role;
import com.LosCiruelos.padel_club_api.Entities.Enum.TipoReserva;
import com.LosCiruelos.padel_club_api.Specifications.ReservaSpecification;

/** Consultas de partidos y turnos fijos contra H2 (esquema generado por Hibernate). Hoy es martes 13/10/2026. */
@DataJpaTest(properties = { "spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect" })
class PartidosRepositoryTest {

    private static final LocalDate HOY = TestData.HOY;
    private static final LocalDate MIERCOLES = HOY.plusDays(1);

    @Autowired
    private TestEntityManager em;

    @Autowired
    private TurnoFijoRepository turnoFijoRepository;

    @Autowired
    private ReservaRepository reservaRepository;

    private Usuario admin;
    private Usuario jugador;
    private Usuario otro;
    private Cancha cancha;
    private TurnoFijo tf;

    @BeforeEach
    void datos() {
        admin = em.persist(conEmail(TestData.usuario(null, Role.ADMIN), "admin@test.com"));
        jugador = em.persist(conEmail(TestData.usuario(null, Role.CLIENTE), "jugador@test.com"));
        otro = em.persist(conEmail(TestData.usuario(null, Role.CLIENTE), "otro@test.com"));
        cancha = em.persist(Cancha.builder().numero(1).descripcion("Cancha 1").build());

        // los miércoles de 20:00 a 21:30 desde mañana
        tf = em.persist(TurnoFijo.builder().cancha(cancha).diaSemana(DayOfWeek.WEDNESDAY)
                .horaInicio(LocalTime.of(20, 0)).horaFin(LocalTime.of(21, 30)).desde(MIERCOLES)
                .cantidadPelotas(0).cantidadPaletas(0).activo(true).createdBy(admin)
                .createdAt(TestData.AHORA).build());
        em.flush();
    }

    private static Usuario conEmail(Usuario usuario, String email) {
        usuario.setEmail(email);
        return usuario;
    }

    private Reserva partido(LocalDate fecha, EstadoReserva estado, TurnoFijo turnoFijo, Usuario registrado) {
        Reserva r = Reserva.builder().cancha(cancha).cliente(admin).createdBy(admin).fechaReserva(fecha)
                .horaInicio(LocalTime.of(20, 0)).horaFin(LocalTime.of(21, 30)).montoTotal(new BigDecimal("15000"))
                .estado(estado).tipo(TipoReserva.PARTIDO).turnoFijo(turnoFijo).createdAt(TestData.AHORA).build();
        r.getJugadores().add(ReservaJugador.builder().reserva(r).posicion(1).usuario(registrado).build());
        for (int posicion = 2; posicion <= 4; posicion++) {
            r.getJugadores().add(ReservaJugador.builder().reserva(r).posicion(posicion).nombre("Invitado " + posicion)
                    .build());
        }
        return em.persist(r);
    }

    private List<TurnoFijo> bloquean(LocalDate fecha, String inicio, String fin) {
        return turnoFijoRepository.findQueBloquean(cancha.getId(), fecha.getDayOfWeek(), fecha, LocalTime.parse(inicio),
                LocalTime.parse(fin));
    }

    @Test
    void elTurnoFijoBloqueaSuHorarioCadaSemanaHastaQueTengaPartido() {
        LocalDate enDosMeses = MIERCOLES.plusWeeks(8);
        assertThat(bloquean(MIERCOLES, "19:00", "20:30")).containsExactly(tf);
        assertThat(bloquean(enDosMeses, "21:00", "22:30")).containsExactly(tf);

        // otro horario, otro día, o antes de que empiece: libre
        assertThat(bloquean(MIERCOLES, "18:00", "19:30")).isEmpty();
        assertThat(bloquean(MIERCOLES, "21:30", "23:00")).isEmpty();
        assertThat(bloquean(MIERCOLES.plusDays(1), "20:00", "21:30")).isEmpty();
        assertThat(bloquean(MIERCOLES.minusWeeks(1), "20:00", "21:30")).isEmpty();

        // con su partido creado (lo ocupa la reserva) o con la fecha dada de baja, no bloquea
        partido(MIERCOLES, EstadoReserva.CONFIRMADA, tf, jugador);
        partido(enDosMeses, EstadoReserva.CANCELADA, tf, jugador);
        em.flush();
        assertThat(bloquean(MIERCOLES, "20:00", "21:30")).isEmpty();
        assertThat(bloquean(enDosMeses, "20:00", "21:30")).isEmpty();
        assertThat(bloquean(MIERCOLES.plusWeeks(1), "20:00", "21:30")).containsExactly(tf);

        // dado de baja deja de bloquear
        tf.setActivo(false);
        em.flush();
        assertThat(bloquean(MIERCOLES.plusWeeks(1), "20:00", "21:30")).isEmpty();
    }

    @Test
    void turnosFijosSuperpuestosEnLaMismaCanchaYDia() {
        assertThat(turnoFijoRepository.findSuperpuestos(-1L, cancha.getId(), DayOfWeek.WEDNESDAY,
                LocalTime.of(21, 0), LocalTime.of(22, 30))).containsExactly(tf);
        assertThat(turnoFijoRepository.findSuperpuestos(tf.getId(), cancha.getId(), DayOfWeek.WEDNESDAY,
                LocalTime.of(21, 0), LocalTime.of(22, 30))).isEmpty();
        assertThat(turnoFijoRepository.findSuperpuestos(-1L, cancha.getId(), DayOfWeek.THURSDAY,
                LocalTime.of(20, 0), LocalTime.of(21, 30))).isEmpty();
    }

    @Test
    void partidosFuturosDelTurnoFijo() {
        Reserva jugado = partido(HOY.minusDays(6), EstadoReserva.COMPLETADA, tf, jugador);
        Reserva proximo = partido(MIERCOLES, EstadoReserva.CONFIRMADA, tf, jugador);
        Reserva liberado = partido(MIERCOLES.plusWeeks(1), EstadoReserva.CANCELADA, tf, jugador);
        partido(MIERCOLES.plusWeeks(2), EstadoReserva.CONFIRMADA, null, jugador); // suelto
        em.flush();

        assertThat(reservaRepository.findIdsFuturosDelTurnoFijo(tf.getId(), HOY, LocalTime.of(10, 0)))
                .containsExactly(proximo.getId(), liberado.getId());
        assertThat(reservaRepository.existsByTurnoFijoAndFechaReserva(tf, MIERCOLES)).isTrue();
        assertThat(reservaRepository.findByTurnoFijoAndFechaReserva(tf, HOY.minusDays(6))).contains(jugado);
    }

    @Test
    void misReservasIncluyeLosPartidosEnLosQueJuega() {
        Reserva suyo = partido(MIERCOLES, EstadoReserva.CONFIRMADA, null, jugador);
        partido(MIERCOLES.plusWeeks(1), EstadoReserva.CONFIRMADA, null, otro);
        Reserva propia = em.persist(Reserva.builder().cancha(cancha).cliente(jugador).createdBy(jugador)
                .fechaReserva(MIERCOLES.plusDays(1)).horaInicio(LocalTime.of(18, 0)).horaFin(LocalTime.of(19, 30))
                .montoTotal(new BigDecimal("15000")).estado(EstadoReserva.SEÑADA).createdAt(TestData.AHORA).build());
        em.flush();

        assertThat(reservaRepository.findAll(ReservaSpecification.delClienteOJugador(jugador), Pageable.unpaged())
                .getContent()).containsExactlyInAnyOrder(suyo, propia);
        // el admin que los armó los ve como suyos
        assertThat(reservaRepository.findAll(ReservaSpecification.delClienteOJugador(admin), Pageable.unpaged())
                .getContent()).hasSize(2);
    }
}
