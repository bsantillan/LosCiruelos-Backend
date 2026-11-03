-- =====================================================================
-- V29: cobro en el club con el QR de la caja de Mercado Pago (API de Orders, modo estático). El
-- QR está impreso en el mostrador; el sistema le asigna a la caja el monto a cobrar y el cliente
-- lo escanea. Un cobro por QR es un pago MERCADO_PAGO que guarda la order de MP y hasta cuándo se
-- puede pagar; mientras se espera el pago queda EN_PROCESO.
-- =====================================================================

ALTER TABLE public.pagos ADD COLUMN order_id varchar(64);
ALTER TABLE public.pagos ADD COLUMN vence_at timestamp(6);
ALTER TABLE public.pagos ADD CONSTRAINT ck_pagos_order_id
    CHECK (order_id IS NULL OR provider = 'MERCADO_PAGO');
CREATE UNIQUE INDEX uk_pagos_order_id ON public.pagos (order_id) WHERE order_id IS NOT NULL;
-- hay un solo QR en la caja: un solo cobro esperando el pago a la vez (aunque dos empleados lo
-- pidan al mismo tiempo para reservas distintas)
CREATE UNIQUE INDEX uk_pagos_qr_en_curso ON public.pagos ((true)) WHERE vence_at IS NOT NULL AND estado = 'EN_PROCESO';
-- el job de respaldo busca los QR que siguen esperando y ya vencieron
CREATE INDEX idx_pagos_qr_activos ON public.pagos (vence_at) WHERE order_id IS NOT NULL AND estado = 'EN_PROCESO';
