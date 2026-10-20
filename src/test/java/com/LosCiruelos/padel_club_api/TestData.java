package com.LosCiruelos.padel_club_api;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import com.LosCiruelos.padel_club_api.Entities.Cancha;
import com.LosCiruelos.padel_club_api.Entities.Configuracion;
import com.LosCiruelos.padel_club_api.Entities.DiaApertura;
import com.LosCiruelos.padel_club_api.Entities.Pago;
import com.LosCiruelos.padel_club_api.Entities.Reserva;
import com.LosCiruelos.padel_club_api.Entities.Usuario;
import com.LosCiruelos.padel_club_api.Entities.Enum.AuthProvider;
import com.LosCiruelos.padel_club_api.Entities.Enum.EstadoPago;
import com.LosCiruelos.padel_club_api.Entities.Enum.EstadoReserva;
import com.LosCiruelos.padel_club_api.Entities.Enum.PaymentProvider;
import com.LosCiruelos.padel_club_api.Entities.Enum.Role;

/** Datos de prueba compartidos por los tests unitarios. */
public final class TestData {

    public static final ZoneId ZONA = ZoneId.of("America/Argentina/Buenos_Aires");

    // Martes 13/10/2026 a las 10:00
    public static final LocalDateTime AHORA = LocalDateTime.of(2026, 10, 13, 10, 0);
    public static final LocalDate HOY = AHORA.toLocalDate();
    public static final LocalDate MANIANA = HOY.plusDays(1);

    private TestData() {
    }

    public static Clock reloj(LocalDateTime momento) {
        return Clock.fixed(momento.atZone(ZONA).toInstant(), ZONA);
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
}
