package com.LosCiruelos.padel_club_api.DTOs.Responses;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;

import com.LosCiruelos.padel_club_api.Entities.Pago;
import com.LosCiruelos.padel_club_api.Entities.Producto;
import com.LosCiruelos.padel_club_api.Entities.Reserva;
import com.LosCiruelos.padel_club_api.Entities.Enum.EstadoReserva;

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
    BigDecimal montoPagado;
    BigDecimal montoPendiente;
    Integer cantidadPelotas;
    Integer cantidadPaletas;
    List<ReservaProductoResponse> productos;
    EstadoReserva estado;
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

    public static ReservaResponse from(Reserva reserva) {
        ReservaResponse response = new ReservaResponse();
        response.setId(reserva.getId());
        response.setCanchaId(reserva.getCancha().getId());
        response.setCanchaNumero(reserva.getCancha().getNumero());
        response.setFechaReserva(reserva.getFechaReserva());
        response.setHoraInicio(reserva.getHoraInicio());
        response.setHoraFin(reserva.getHoraFin());
        response.setMontoTotal(reserva.getMontoTotal());
        response.setMontoCancha(reserva.getMontoCancha());
        response.setMontoProductos(reserva.getMontoProductos());
        response.setMontoPagado(reserva.getMontoPagado());
        response.setMontoPendiente(reserva.getMontoPendiente());
        response.setCantidadPelotas(reserva.cantidadDe(Producto.CODIGO_PELOTAS));
        response.setCantidadPaletas(reserva.cantidadDe(Producto.CODIGO_PALETAS));
        response.setProductos(reserva.getProductos().stream().map(ReservaProductoResponse::from).toList());
        response.setEstado(reserva.getEstado());
        response.setClienteId(reserva.getCliente().getId());
        response.setClienteNombre(nombreCompleto(reserva));
        response.setCreatedAt(reserva.getCreatedAt());
        response.setExpiresAt(reserva.getExpiresAt());
        response.setPagos(reserva.getPagos().stream()
                .sorted(Comparator.comparing(Pago::getCreatedAt))
                .map(PagoResponse::from)
                .toList());
        return response;
    }

    private static String nombreCompleto(Reserva reserva) {
        String nombre = reserva.getCliente().getNombre();
        String apellido = reserva.getCliente().getApellido();
        return ((nombre != null ? nombre : "") + " " + (apellido != null ? apellido : "")).trim();
    }
}
