package com.LosCiruelos.padel_club_api.club.dto.response;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;

/**
 * Toda la configuración del club (lo que edita el ADMIN con PUT /admin/configuracion).
 *
 * @param whatsappClientes si el club manda WhatsApp a los clientes (apagado: avisos por mail y el celular
 *                         del cliente no se verifica)
 * @param minutosPagoSeniaOnline minutos para pagar la seña online de una reserva que cargó el personal
 * @param horarios      los días que abre, de lunes a domingo (un día que no está, el club no abre)
 * @param cancelaciones solo al guardar con confirmar: las reservas fuera del horario nuevo que se
 *                      cancelaron (null en la consulta)
 */
public record ConfiguracionResponse(BigDecimal montoReserva, BigDecimal montoXMediaHora, BigDecimal porcentajeSenia,
        int duracionMinimaTurno, int duracionMaximaTurno, int minutosReembolsoTotal, int minutosReembolsoSenia,
        boolean recordatorioActivo, int minutosRecordatorio, boolean whatsappClientes, int diasAnticipacion,
        int minutosPagoSeniaOnline,
        List<Horario> horarios, CancelacionesResponse cancelaciones) {

    public record Horario(String dia, LocalTime apertura, LocalTime cierre) {
    }
}
