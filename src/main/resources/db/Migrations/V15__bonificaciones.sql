-- =====================================================================
-- V15: bonificaciones del ADMIN y quién registra cada pago en el club.
-- Una bonificación es un pago con provider BONIFICACION: cubre el monto del turno (la reserva
-- queda confirmada o paga) pero no es plata. No cuenta como cobrado ni se devuelve al cancelar.
-- =====================================================================

ALTER TABLE public.pagos DROP CONSTRAINT ck_pagos_provider;
ALTER TABLE public.pagos ADD CONSTRAINT ck_pagos_provider
    CHECK (provider IN ('MERCADO_PAGO', 'MANUAL', 'BONIFICACION'));

ALTER TABLE public.pagos
    ADD COLUMN registrado_por_id bigint REFERENCES public.usuarios (id),
    ADD COLUMN motivo varchar(200);
