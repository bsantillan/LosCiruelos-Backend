package com.LosCiruelos.padel_club_api.pagos.service;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.LosCiruelos.padel_club_api.common.exception.ReservaException;
import com.LosCiruelos.padel_club_api.mapper.PagoMapper;
import com.LosCiruelos.padel_club_api.pagos.dto.response.PagoResponse;
import com.LosCiruelos.padel_club_api.pagos.payment.PaymentProvider;
import com.LosCiruelos.padel_club_api.pagos.repository.PagoRepository;

import lombok.RequiredArgsConstructor;

/** Consultas de pagos para el ADMIN: los cobros con Cuenta DNI para controlarlos contra la cuenta. */
@Service
@RequiredArgsConstructor
public class PagoConsultaService {

    private final PagoRepository pagoRepository;
    private final PagoMapper pagoMapper;

    /** Pagos con Cuenta DNI cobrados entre desde y hasta (inclusive); verificado null: todos. */
    @Transactional(readOnly = true)
    public List<PagoResponse> cuentaDni(LocalDate desde, LocalDate hasta, Boolean verificado) {
        if (desde.isAfter(hasta)) {
            throw new ReservaException("La fecha desde no puede ser posterior a hasta.");
        }
        return pagoRepository.cuentaDni(PaymentProvider.CUENTA_DNI, desde.atStartOfDay(),
                hasta.plusDays(1).atStartOfDay(), verificado).stream()
                .map(pagoMapper::aResponse)
                .toList();
    }
}
