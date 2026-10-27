package com.LosCiruelos.padel_club_api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import com.LosCiruelos.padel_club_api.club.entity.Cancha;
import com.LosCiruelos.padel_club_api.club.entity.Configuracion;
import com.LosCiruelos.padel_club_api.club.entity.DiaApertura;
import com.LosCiruelos.padel_club_api.pagos.entity.Pago;
import com.LosCiruelos.padel_club_api.reservas.entity.Reserva;
import com.LosCiruelos.padel_club_api.reservas.entity.ReservaJugador;
import com.LosCiruelos.padel_club_api.Entities.Usuario;
import com.LosCiruelos.padel_club_api.Entities.Enum.AuthProvider;
import com.LosCiruelos.padel_club_api.pagos.entity.enums.EstadoPago;
import com.LosCiruelos.padel_club_api.reservas.entity.enums.EstadoReserva;
import com.LosCiruelos.padel_club_api.pagos.payment.PaymentProvider;
import com.LosCiruelos.padel_club_api.Entities.Enum.Role;
import com.LosCiruelos.padel_club_api.reservas.entity.enums.TipoReserva;
import com.LosCiruelos.padel_club_api.common.Reloj;

/** Datos de prueba compartidos por los tests unitarios. */
public final class TestData {

    public static final ZoneId ZONA = ZoneId.of("America/Argentina/Buenos_Aires");

    // Martes 13/10/2026 a las 10:00
    public static final LocalDateTime AHORA = LocalDateTime.of(2026, 10, 13, 10, 0);
    public static final LocalDate HOY = AHORA.toLocalDate();
    public static final LocalDate MANIANA = HOY.plusDays(1);

    private TestData() {
    }

    public static Reloj reloj(LocalDateTime momento) {
        return Reloj.fijo(momento);
    }

    /** Configuración por defecto del DataInitializer: seña 50 %, 90-180 min, $5000 cada 30 min. */
    public static Configuracion configuracion() {
        List<DiaApertura> dias = new ArrayList<>();
        for (String dia : List.of("Lunes", "Martes", "Miércoles", "Jueves", "Viernes")) {
            dias.add(DiaApertura.builder().dia(dia).horario_inicio(LocalTime.of(8, 0))
                    .horario_fin(LocalTime.of(23, 0)).build());
        }
        dias.add(DiaApertura.builder().dia("Sábado").horario_inicio(LocalTime.of(9, 0))
                .horario_fin(LocalTime.of(22, 0)).build());
        dias.add(DiaApertura.builder().dia("Domingo").horario_inicio(LocalTime.of(9, 0))
                .horario_fin(LocalTime.of(20, 0)).build());

        return Configuracion.builder()
                .id(1L)
                .montoReserva(BigDecimal.ZERO)
                .porcentajeSenia(new BigDecimal("50"))
                .duracionMinimaTurno(90)
                .duracionMaximaTurno(180)
                .montoXMediaHora(new BigDecimal("5000"))
                .dias_apertura(dias)
                .build();
    }

    public static Usuario usuario(Long id, Role rol) {
        return Usuario.builder()
                .id(id)
                .email("usuario" + id + "@test.com")
                .nombre("Nombre" + id)
                .apellido("Apellido" + id)
                .telefono("5491122334455")
                .telefonoVerificado(true)
                .rol(rol)
                .provider(AuthProvider.LOCAL)
                .termsAccepted(true)
                .termsAcceptedAt(AHORA.minusDays(10))
                .emailVerificado(true)
                .enabled(true)
                .build();
    }

    public static Cancha cancha() {
        return Cancha.builder().id(1L).numero(1).descripcion("Cancha 1").build();
    }

    public static Reserva reserva(Long id, Usuario cliente, EstadoReserva estado, String total) {
        return Reserva.builder()
                .id(id)
                .cancha(cancha())
                .cliente(cliente)
                .createdBy(cliente)
                .fechaReserva(MANIANA)
                .horaInicio(LocalTime.of(18, 0))
                .horaFin(LocalTime.of(19, 30))
                .montoTotal(new BigDecimal(total))
                .estado(estado)
                .createdAt(AHORA.minusMinutes(5))
                .expiresAt(AHORA.plusMinutes(10))
                .build();
    }

    public static Pago pago(Long id, Reserva reserva, EstadoPago estado, String monto) {
        Pago pago = Pago.builder()
                .id(id)
                .monto(new BigDecimal(monto))
                .estado(estado)
                .provider(PaymentProvider.MERCADO_PAGO)
                .createdAt(AHORA.minusMinutes(1))
                .build();
        reserva.agregarPago(pago);
        return pago;
    }

    /**
     * Partido CONFIRMADA (sin pagos, no vence) armado por {@code admin}: el jugador 1 es
     * {@code registrado} y los otros tres son invitados. Los jugadores tienen ids 201 a 204.
     */
    public static Reserva partido(Long id, Usuario admin, Usuario registrado, String total) {
        Reserva partido = reserva(id, admin, EstadoReserva.CONFIRMADA, total);
        partido.setTipo(TipoReserva.PARTIDO);
        partido.setExpiresAt(null);
        partido.getJugadores().add(ReservaJugador.builder().id(201L).reserva(partido).posicion(1).usuario(registrado)
                .build());
        for (int posicion = 2; posicion <= 4; posicion++) {
            partido.getJugadores().add(ReservaJugador.builder().id(200L + posicion).reserva(partido)
                    .posicion(posicion).nombre("Invitado " + posicion).telefono("11 5555-000" + posicion).build());
        }
        return partido;
    }
}
