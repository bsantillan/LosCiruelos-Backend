package com.LosCiruelos.padel_club_api.pagos.dto.response;

import java.math.BigDecimal;

/**
 * Lo cobrado con Mercado Pago, abierto por canal: {@code total} = qr + online + transferencia.
 * En las ventas sueltas, {@code online} es siempre 0 (no se pagan online).
 */
public record DetalleMercadoPago(BigDecimal total, BigDecimal qr, BigDecimal online, BigDecimal transferencia) {
}
