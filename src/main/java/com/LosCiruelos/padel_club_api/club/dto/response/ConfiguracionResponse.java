package com.LosCiruelos.padel_club_api.club.dto.response;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;

/**
 * Toda la configuración del club (lo que edita el ADMIN con PUT /admin/configuracion).
 *
 * @param horarios los días que abre, de lunes a domingo (un día que no está, el club no abre)
 */
public record ConfiguracionResponse(BigDecimal montoReserva, BigDecimal montoXMediaHora, BigDecimal porcentajeSenia,
        int duracionMinimaTurno, int duracionMaximaTurno, int minutosReembolsoTotal, int minutosReembolsoSenia,
        boolean recordatorioActivo, int minutosRecordatorio, int diasAnticipacion, List<Horario> horarios) {

    public record Horario(String dia, LocalTime apertura, LocalTime cierre) {
    }
}
