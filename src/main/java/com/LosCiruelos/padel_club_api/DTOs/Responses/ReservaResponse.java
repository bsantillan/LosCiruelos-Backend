package com.LosCiruelos.padel_club_api.DTOs.Responses;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import com.LosCiruelos.padel_club_api.Entities.Reserva;
import com.LosCiruelos.padel_club_api.Entities.Enum.EstadoReserva;

import lombok.Data;

@Data
public class ReservaResponse {
    Long id;
    Integer canchaId;
    Integer canchaNumero;
    LocalDate fechaReserva;
    LocalTime horaInicio;
    LocalTime horaFin;
    BigDecimal montoTotal;
    BigDecimal montoPagado;
    BigDecimal montoPendiente;
    Boolean pelotas;
    Boolean paletas;
    EstadoReserva estado;
    Long clienteId;
    String clienteNombre;
    LocalDateTime createdAt;
    LocalDateTime expiresAt;

    public static ReservaResponse from(Reserva reserva) {
        ReservaResponse response = new ReservaResponse();
        response.setId(reserva.getId());
        response.setCanchaId(reserva.getCancha().getId());
        response.setCanchaNumero(reserva.getCancha().getNumero());
        response.setFechaReserva(reserva.getFechaReserva());
        response.setHoraInicio(reserva.getHoraInicio());
        response.setHoraFin(reserva.getHoraFin());
        response.setMontoTotal(reserva.getMontoTotal());
        response.setMontoPagado(reserva.getMontoPagado());
        response.setMontoPendiente(reserva.getMontoPendiente());
        response.setPelotas(reserva.getPelotas());
        response.setPaletas(reserva.getPaletas());
        response.setEstado(reserva.getEstado());
        response.setClienteId(reserva.getCliente().getId());
        response.setClienteNombre(reserva.getCliente().getNombre() + " " + reserva.getCliente().getApellido());
        response.setCreatedAt(reserva.getCreatedAt());
        response.setExpiresAt(reserva.getExpiresAt());
        return response;
    }
}
