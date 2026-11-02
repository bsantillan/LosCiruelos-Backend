-- =====================================================================
-- V29: cobro en el club con QR dinámico de Mercado Pago (API de Orders). Un cobro por QR es un
-- pago MERCADO_PAGO que guarda la order de MP, la trama del QR (para volver a mostrarlo) y
-- cuándo vence. Mientras el QR está activo el pago queda EN_PROCESO.
-- =====================================================================

ALTER TABLE public.pagos ADD COLUMN order_id varchar(64);
ALTER TABLE public.pagos ADD COLUMN qr_data text;
ALTER TABLE public.pagos ADD COLUMN vence_at timestamp(6);
ALTER TABLE public.pagos ADD CONSTRAINT ck_pagos_order_id
    CHECK (order_id IS NULL OR provider = 'MERCADO_PAGO');
CREATE UNIQUE INDEX uk_pagos_order_id ON public.pagos (order_id) WHERE order_id IS NOT NULL;
-- el job de respaldo busca los QR activos ya vencidos
CREATE INDEX idx_pagos_qr_activos ON public.pagos (vence_at) WHERE order_id IS NOT NULL AND estado = 'EN_PROCESO';
