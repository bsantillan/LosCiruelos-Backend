package com.LosCiruelos.padel_club_api.reservas.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import com.LosCiruelos.padel_club_api.pagos.dto.response.PagoResponse;
import com.LosCiruelos.padel_club_api.reservas.entity.enums.EstadoReserva;
import com.LosCiruelos.padel_club_api.reservas.entity.enums.TipoReserva;

import lombok.Data;

@Data
public class ReservaResponse {
    Long id;
    Long canchaId;
    Integer canchaNumero;
    LocalDate fechaReserva;
    LocalTime horaInicio;
    LocalTime horaFin;
    // montoTotal = montoCancha + montoProductos
    BigDecimal montoTotal;
    BigDecimal montoCancha;
    BigDecimal montoProductos;
    BigDecimal montoSenia;
    // plata cobrada (sin bonificaciones)
    BigDecimal montoPagado;
    // lo que el ADMIN bonificó; montoPendiente = montoTotal - montoPagado - montoBonificado
    BigDecimal montoBonificado;
    BigDecimal montoPendiente;
    Integer cantidadPelotas;
    Integer cantidadPaletas;
    List<ReservaProductoResponse> productos;
    EstadoReserva estado;
    // NORMAL o PARTIDO (de 4 jugadores, armado por el ADMIN)
    TipoReserva tipo;
    // partido creado por un turno fijo
    Long turnoFijoId;
    // partido: los 4 jugadores con lo que puso cada uno (vacío en una reserva NORMAL)
    List<JugadorResponse> jugadores;
    // en un partido es el ADMIN que lo armó
    Long clienteId;
    String clienteNombre;
    LocalDateTime createdAt;
    // hasta cuándo se puede pagar mientras está PENDIENTE
    LocalDateTime expiresAt;
    List<PagoResponse> pagos;

    // Cancelación por el cliente (según la política de la configuración)
    // si hoy se puede cancelar
    Boolean cancelable;
    // hasta cuándo se puede cancelar (inicio del turno)
    LocalDateTime cancelableHasta;
    // cuánto se devolvería si se cancela ahora
    BigDecimal montoReembolso;
    // hasta cuándo se devuelve todo lo pagado / la seña (null si no aplica)
    LocalDateTime reembolsoTotalHasta;
    LocalDateTime reembolsoSeniaHasta;

    // el turno ya terminó (se jugó, si la reserva estaba confirmada)
    Boolean turnoTerminado;
    // turno jugado sin pagar del todo (SEÑADA o PARCIALMENTE_PAGADA): falta cobrar montoPendiente
    Boolean debeSaldo;
}
