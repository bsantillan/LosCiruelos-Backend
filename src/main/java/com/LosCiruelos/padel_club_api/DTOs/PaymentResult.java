package com.LosCiruelos.padel_club_api.DTOs;

import java.math.BigDecimal;

import com.LosCiruelos.padel_club_api.Entities.Enum.EstadoPagoExterno;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class PaymentResult {
    private EstadoPagoExterno estado;
    private String externalReference;
    private BigDecimal monto;
}
