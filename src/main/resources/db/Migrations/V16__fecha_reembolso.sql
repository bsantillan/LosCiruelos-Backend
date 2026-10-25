-- =====================================================================
-- V16: cuándo se devolvió cada pago, para el cierre de caja del resumen.
-- reembolsado_at se completa cuando el pago pasa a DEVUELTO o A_REEMBOLSAR.
-- =====================================================================

ALTER TABLE public.pagos ADD COLUMN reembolsado_at timestamp(6);

-- Datos viejos: no se guardó la fecha del reembolso. Se usa la más cercana que hay:
-- cuándo se pidió a Mercado Pago (si el primer intento falló), si no cuándo se cobró y, si
-- tampoco está, cuándo se creó el pago.
UPDATE public.pagos
SET reembolsado_at = COALESCE(reembolso_solicitado_at, paid_at, created_at)
WHERE estado IN ('DEVUELTO', 'A_REEMBOLSAR');

-- Cobros de Mercado Pago que quedaron devueltos o por devolver sin pasar por APROBADO (monto
-- distinto, reserva inactiva, devolución que llegó antes): la plata entró, pero no tenían
-- fecha de cobro. Se usa la de creación del pago.
UPDATE public.pagos
SET paid_at = created_at
WHERE paid_at IS NULL
  AND provider <> 'BONIFICACION'
  AND estado IN ('DEVUELTO', 'A_REEMBOLSAR', 'REEMBOLSO_PENDIENTE');

-- Cierre de caja: pagos por fecha de cobro y devoluciones por fecha de reembolso
CREATE INDEX idx_pagos_paid_at ON public.pagos (paid_at);
CREATE INDEX idx_pagos_reembolsado_at ON public.pagos (reembolsado_at);
