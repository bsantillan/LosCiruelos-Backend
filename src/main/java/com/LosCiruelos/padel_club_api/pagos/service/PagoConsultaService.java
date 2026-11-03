package com.LosCiruelos.padel_club_api.pagos.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.LosCiruelos.padel_club_api.common.exception.ReservaException;
import com.LosCiruelos.padel_club_api.mapper.CuentaDniMapper;
import com.LosCiruelos.padel_club_api.pagos.dto.response.CobroCuentaDniResponse;
import com.LosCiruelos.padel_club_api.pagos.payment.PaymentProvider;
import com.LosCiruelos.padel_club_api.pagos.repository.PagoRepository;
import com.LosCiruelos.padel_club_api.ventas.service.VentaConsultaService;

import lombok.RequiredArgsConstructor;

/** Consultas de pagos para el ADMIN: todo lo cobrado con Cuenta DNI, para controlarlo contra la cuenta. */
@Service
@RequiredArgsConstructor
public class PagoConsultaService {

    private final PagoRepository pagoRepository;
    private final VentaConsultaService ventaConsultaService;
    private final CuentaDniMapper cuentaDniMapper;

    /**
     * Pagos de reservas y ventas sueltas con Cuenta DNI cobrados entre desde y hasta (inclusive), del
     * más viejo al más nuevo; verificado null: todos.
     */
    @Transactional(readOnly = true)
    public List<CobroCuentaDniResponse> cuentaDni(LocalDate desde, LocalDate hasta, Boolean verificado) {
        if (desde.isAfter(hasta)) {
            throw new ReservaException("La fecha desde no puede ser posterior a hasta.");
        }
        LocalDateTime inicio = desde.atStartOfDay();
        LocalDateTime fin = hasta.plusDays(1).atStartOfDay();
        return Stream.concat(
                pagoRepository.cuentaDni(PaymentProvider.CUENTA_DNI, inicio, fin, verificado).stream()
                        .map(cuentaDniMapper::aResponse),
                ventaConsultaService.cuentaDni(inicio, fin, verificado).stream().map(cuentaDniMapper::aResponse))
                .sorted(Comparator.comparing(CobroCuentaDniResponse::fecha))
                .toList();
    }
}
