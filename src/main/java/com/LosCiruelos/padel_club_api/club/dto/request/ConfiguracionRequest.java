package com.LosCiruelos.padel_club_api.club.dto.request;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** Toda la configuración del club que edita el ADMIN: reemplaza los valores actuales. */
@Data
public class ConfiguracionRequest {

    // ── Precio del turno: montoReserva + montoXMediaHora por cada media hora ──
    @NotNull(message = "Es obligatorio")
    @DecimalMin(value = "0", message = "No puede ser negativo")
    @Digits(integer = 8, fraction = 2, message = "Hasta 8 enteros y 2 decimales")
    private BigDecimal montoReserva;

    @NotNull(message = "Es obligatorio")
    @DecimalMin(value = "0", inclusive = false, message = "Tiene que ser mayor a 0")
    @Digits(integer = 8, fraction = 2, message = "Hasta 8 enteros y 2 decimales")
    private BigDecimal montoXMediaHora;

    @NotNull(message = "Es obligatorio")
    @DecimalMin(value = "0", message = "Entre 0 y 100")
    @DecimalMax(value = "100", message = "Entre 0 y 100")
    @Digits(integer = 3, fraction = 2, message = "Hasta 2 decimales")
    private BigDecimal porcentajeSenia;

    // ── Duración de los turnos (múltiplos de 30 minutos) ──
    @NotNull(message = "Es obligatorio")
    @Min(value = 30, message = "Mínimo 30 minutos")
    @Max(value = 600, message = "Máximo 600 minutos")
    private Integer duracionMinimaTurno;

    @NotNull(message = "Es obligatorio")
    @Min(value = 30, message = "Mínimo 30 minutos")
    @Max(value = 600, message = "Máximo 600 minutos")
    private Integer duracionMaximaTurno;

    // ── Política de cancelación del cliente (minutos antes del turno) ──
    @NotNull(message = "Es obligatorio")
    @Min(value = 0, message = "No puede ser negativo")
    @Max(value = 10080, message = "Máximo 10080 minutos (7 días)")
    private Integer minutosReembolsoTotal;

    @NotNull(message = "Es obligatorio")
    @Min(value = 0, message = "No puede ser negativo")
    @Max(value = 10080, message = "Máximo 10080 minutos (7 días)")
    private Integer minutosReembolsoSenia;

    // ── Recordatorio del turno ──
    @NotNull(message = "Es obligatorio")
    private Boolean recordatorioActivo;

    @NotNull(message = "Es obligatorio")
    @Min(value = 15, message = "Mínimo 15 minutos")
    @Max(value = 1440, message = "Máximo 1440 minutos (24 h)")
    private Integer minutosRecordatorio;

    // ── Anticipación máxima para reservar ──
    @NotNull(message = "Es obligatorio")
    @Min(value = 1, message = "Mínimo 1 día")
    @Max(value = 365, message = "Máximo 365 días")
    private Integer diasAnticipacion;

    // ── Reserva del personal con la seña online: minutos para pagarla (opcional: null no la cambia) ──
    @Min(value = 15, message = "Mínimo 15 minutos")
    @Max(value = 2880, message = "Máximo 2880 minutos (48 h)")
    private Integer minutosPagoSeniaOnline;

    // ── Días y horarios de apertura: un día que no está, el club no abre ──
    @NotEmpty(message = "Indicá al menos un día de apertura")
    @Size(max = 7, message = "Son 7 días como máximo")
    private List<@Valid @NotNull(message = "Indicá el día") Horario> horarios;

    // opcional: true guarda aunque el horario nuevo deje afuera reservas o turnos fijos (no se tocan)
    private Boolean confirmar;

    @Data
    public static class Horario {
        @NotBlank(message = "Indicá el día (Lunes a Domingo)")
        private String dia;

        @NotNull(message = "Indicá la hora de apertura")
        private LocalTime apertura;

        @NotNull(message = "Indicá la hora de cierre")
        private LocalTime cierre;
    }
}
