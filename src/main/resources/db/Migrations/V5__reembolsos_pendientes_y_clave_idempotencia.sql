-- =====================================================================
-- V5: reembolsos que Mercado Pago todavía no acepta y clave de idempotencia por pago.
-- =====================================================================

-- REEMBOLSO_PENDIENTE: la reserva se canceló pero MP rechazó el reembolso (por ejemplo,
-- el pago todavía se está acreditando). Un job lo reintenta cada 2 minutos.
ALTER TABLE public.pagos DROP CONSTRAINT ck_pagos_estado;
ALTER TABLE public.pagos ADD CONSTRAINT ck_pagos_estado CHECK (estado IN
    ('PENDIENTE', 'APROBADO', 'RECHAZADO', 'EN_PROCESO', 'DEVUELTO', 'EXPIRADO', 'A_REEMBOLSAR',
     'REEMBOLSO_PENDIENTE'));

-- Cuándo se pidió el reembolso pendiente: pasadas 24 h sin éxito, queda A_REEMBOLSAR
ALTER TABLE public.pagos ADD COLUMN reembolso_solicitado_at timestamp(6);

CREATE INDEX idx_pagos_reembolso_pendiente ON public.pagos (id) WHERE estado = 'REEMBOLSO_PENDIENTE';

-- Clave del pago ante Mercado Pago (X-Idempotency-Key). No se deriva del id porque si la
-- base se recrea los ids se repiten y MP devolvería el resultado de un pedido viejo.
ALTER TABLE public.pagos ADD COLUMN clave_idempotencia uuid;
UPDATE public.pagos SET clave_idempotencia = gen_random_uuid();
ALTER TABLE public.pagos
    ALTER COLUMN clave_idempotencia SET NOT NULL,
    ADD CONSTRAINT uk_pagos_clave_idempotencia UNIQUE (clave_idempotencia);
