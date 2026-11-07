package com.LosCiruelos.padel_club_api.club.entity;

import com.LosCiruelos.padel_club_api.common.Busquedas;
import com.LosCiruelos.padel_club_api.common.Fechas;
import com.LosCiruelos.padel_club_api.common.Dinero;
import com.LosCiruelos.padel_club_api.common.Turno;
import com.LosCiruelos.padel_club_api.common.exception.ReservaException;
import com.LosCiruelos.padel_club_api.club.exception.DiaSinAperturaException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.math.RoundingMode;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "Configuracion")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Configuracion {

    @Id
    private Long id;

    @Column(nullable = false, name = "monto_reserva", precision = 10, scale = 2)
    private BigDecimal montoReserva;

    @Column(nullable = false, name = "porcentaje_senia", precision = 5, scale = 2)
    private BigDecimal porcentajeSenia;

    @Column(nullable = false, name = "duracion_minima_turno")
    private Integer duracionMinimaTurno;

    @Column(nullable = false, name = "duracion_maxima_turno")
    private Integer duracionMaximaTurno;

    @Column(nullable = false, name = "monto_x_media_hora", precision = 10, scale = 2)
    private BigDecimal montoXMediaHora;

    // Política de cancelación del cliente: con al menos esta anticipación (minutos) se devuelve
    // todo lo pagado / la seña; con menos, nada
    @Builder.Default
    @Column(nullable = false, name = "minutos_reembolso_total")
    private Integer minutosReembolsoTotal = 1440;

    @Builder.Default
    @Column(nullable = false, name = "minutos_reembolso_senia")
    private Integer minutosReembolsoSenia = 720;

    // Recordatorio al cliente antes del turno (por WhatsApp o mail, según su perfil)
    @Builder.Default
    @Column(nullable = false, name = "recordatorio_activo")
    private Boolean recordatorioActivo = true;

    @Builder.Default
    @Column(nullable = false, name = "minutos_recordatorio")
    private Integer minutosRecordatorio = 120;

    // El cliente reserva como mucho con estos días de anticipación; los turnos fijos generan sus
    // partidos hasta ahí
    @Builder.Default
    @Column(nullable = false, name = "dias_anticipacion_reserva")
    private Integer diasAnticipacionReserva = 30;

    @Builder.Default
    @OneToMany(fetch = FetchType.EAGER, cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(nullable = false, name = "configuracion_general_id")
    private List<DiaApertura> dias_apertura = new ArrayList<>();

    /** Los turnos se arman en bloques de 30 minutos desde la apertura. */
    public static final int MINUTOS_POR_BLOQUE = 30;

    // ── Cambios del ADMIN ────────────────────────────────────────────────

    /** Duración mínima y máxima de los turnos: múltiplos de 30 minutos y la mínima no mayor que la máxima. */
    public void cambiarDuracionDeTurnos(int minima, int maxima) {
        if (minima % MINUTOS_POR_BLOQUE != 0 || maxima % MINUTOS_POR_BLOQUE != 0) {
            throw new ReservaException("La duración de los turnos tiene que ser un múltiplo de 30 minutos.");
        }
        if (minima > maxima) {
            throw new ReservaException("La duración mínima del turno no puede superar la máxima.");
        }
        duracionMinimaTurno = minima;
        duracionMaximaTurno = maxima;
    }

    /** Con al menos {@code total} minutos se devuelve todo; con al menos {@code senia}, la seña. */
    public void cambiarPoliticaCancelacion(int total, int senia) {
        if (total < senia) {
            throw new ReservaException("La anticipación para devolver todo no puede ser menor que la de devolver la seña.");
        }
        minutosReembolsoTotal = total;
        minutosReembolsoSenia = senia;
    }

    /** Un día de apertura: el nombre (Lunes a Domingo, con o sin tilde) y su horario. */
    public record Horario(String dia, LocalTime apertura, LocalTime cierre) {
    }

    /**
     * Reemplaza los días y horarios de apertura (un día que no está, el club no abre). Cada día va una
     * sola vez, abre antes de cerrar y por lo menos el turno mínimo (aplicar antes la duración nueva).
     */
    public void cambiarHorarios(List<Horario> horarios) {
        Set<String> vistos = new HashSet<>();
        List<DiaApertura> nuevos = new ArrayList<>();
        for (Horario h : horarios) {
            String dia = nombreDelDia(h.dia());
            String enOracion = dia.toLowerCase(Fechas.ES_AR);
            if (!vistos.add(dia)) {
                throw new ReservaException("El " + enOracion + " está repetido.");
            }
            if (!h.apertura().isBefore(h.cierre())) {
                throw new ReservaException("El " + enOracion + ", la apertura tiene que ser antes del cierre.");
            }
            if (Turno.minutosEntre(h.apertura(), h.cierre()) < duracionMinimaTurno) {
                throw new ReservaException("El %s abre menos que el turno mínimo (%d minutos)."
                        .formatted(enOracion, duracionMinimaTurno));
            }
            nuevos.add(DiaApertura.builder().dia(dia).horario_inicio(h.apertura()).horario_fin(h.cierre()).build());
        }
        // los días que siguen se actualizan; los demás se borran y los nuevos se suman
        dias_apertura.removeIf(d -> !vistos.contains(d.getDia()));
        for (DiaApertura nuevo : nuevos) {
            dias_apertura.stream().filter(d -> d.getDia().equals(nuevo.getDia())).findFirst()
                    .ifPresentOrElse(d -> {
                        d.setHorario_inicio(nuevo.getHorario_inicio());
                        d.setHorario_fin(nuevo.getHorario_fin());
                    }, () -> dias_apertura.add(nuevo));
        }
    }

    /** Los días de apertura de lunes a domingo. */
    public List<DiaApertura> diasEnOrden() {
        return dias_apertura.stream()
                .sorted(Comparator.comparingInt(d -> diaDeLaSemana(d.getDia()).getValue()))
                .toList();
    }

    /** "miercoles", "MIÉRCOLES" → "Miércoles" (como se guardan). */
    private static String nombreDelDia(String texto) {
        String buscado = Busquedas.normalizar(texto);
        for (DayOfWeek d : DayOfWeek.values()) {
            if (Busquedas.normalizar(Fechas.nombreDia(d)).equals(buscado)) {
                return Fechas.nombreDiaCapitalizado(d);
            }
        }
        throw new ReservaException("Día inválido: " + texto + ". Usá Lunes a Domingo.");
    }

    private static DayOfWeek diaDeLaSemana(String nombre) {
        String buscado = Busquedas.normalizar(nombre);
        for (DayOfWeek d : DayOfWeek.values()) {
            if (Busquedas.normalizar(Fechas.nombreDia(d)).equals(buscado)) {
                return d;
            }
        }
        return DayOfWeek.SUNDAY;
    }

    // ── Reglas ───────────────────────────────────────────────────────────

    /** Horario de apertura del día de la semana de {@code fecha}. */
    public DiaApertura diaApertura(LocalDate fecha) {
        String nombreDia = Fechas.nombreDiaCapitalizado(fecha.getDayOfWeek());
        return dias_apertura.stream()
                .filter(d -> d.getDia().equalsIgnoreCase(nombreDia))
                .findFirst()
                .orElseThrow(() -> new DiaSinAperturaException("El club no abre los " + nombreDia + "."));
    }

    /** Último día que el cliente puede reservar: hoy más los días de anticipación. */
    public LocalDate reservableHasta(LocalDate hoy) {
        return hoy.plusDays(diasAnticipacionReserva);
    }

    /** El cliente no puede reservar más allá de los días de anticipación. */
    public void validarAnticipacion(LocalDate fecha, LocalDate hoy) {
        LocalDate hasta = reservableHasta(hoy);
        if (fecha.isAfter(hasta)) {
            throw new ReservaException("Podés reservar hasta el %s (%d días de anticipación)."
                    .formatted(Fechas.textoFecha(hasta), diasAnticipacionReserva));
        }
    }

    /** Duración múltiplo de 30 y dentro del mínimo y máximo configurados. */
    public void validarDuracion(long duracionMinutos) {
        if (duracionMinutos <= 0 || duracionMinutos % MINUTOS_POR_BLOQUE != 0) {
            throw new ReservaException("El turno debe durar un múltiplo de 30 minutos.");
        }
        if (duracionMinutos < duracionMinimaTurno) {
            throw new ReservaException(
                    "La duración mínima del turno es de %d minutos.".formatted(duracionMinimaTurno));
        }
        if (duracionMinutos > duracionMaximaTurno) {
            throw new ReservaException(
                    "La duración máxima del turno es de %d minutos.".formatted(duracionMaximaTurno));
        }
    }

    /** montoReserva + montoXMediaHora × cantidad de bloques de 30 minutos. */
    public BigDecimal montoTurno(long duracionMinutos) {
        long bloques = duracionMinutos / MINUTOS_POR_BLOQUE;
        return Dinero.redondear(montoReserva.add(montoXMediaHora.multiply(BigDecimal.valueOf(bloques))));
    }

    /** Seña = total × porcentajeSenia / 100, redondeada a centavos. */
    public BigDecimal senia(BigDecimal montoTotal) {
        return montoTotal.multiply(porcentajeSenia).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }
}
