-- =====================================================================
-- V37: la transferencia a la cuenta de Mercado Pago del club (alias o CVU) es un medio propio,
-- TRANSFERENCIA_MP, distinto del cobro online y del QR de la caja (que siguen siendo MERCADO_PAGO).
-- Se cobra a mano y, como Cuenta DNI, el ADMIN la verifica contra la cuenta.
-- Los pagos y ventas "Mercado Pago cargado a mano" que había (sin pago de MP ni order del QR) eran
-- transferencias: pasan a TRANSFERENCIA_MP.
-- =====================================================================

ALTER TABLE public.pagos DROP CONSTRAINT ck_pagos_provider;
ALTER TABLE public.pagos ADD CONSTRAINT ck_pagos_provider
    CHECK (provider IN ('MERCADO_PAGO', 'MANUAL', 'BONIFICACION', 'CUENTA_DNI', 'TRANSFERENCIA_MP'));
UPDATE public.pagos SET provider = 'TRANSFERENCIA_MP'
WHERE provider = 'MERCADO_PAGO' AND external_payment_id IS NULL AND order_id IS NULL AND registrado_por_id IS NOT NULL;

ALTER TABLE public.pagos DROP CONSTRAINT ck_pagos_verificado;
ALTER TABLE public.pagos ADD CONSTRAINT ck_pagos_verificado
    CHECK ((verificado_at IS NULL AND verificado_por_id IS NULL)
        OR (verificado_at IS NOT NULL AND verificado_por_id IS NOT NULL AND provider IN ('CUENTA_DNI', 'TRANSFERENCIA_MP')));
DROP INDEX public.idx_pagos_cuenta_dni;
CREATE INDEX idx_pagos_verificables ON public.pagos (paid_at) WHERE provider IN ('CUENTA_DNI', 'TRANSFERENCIA_MP');

ALTER TABLE public.ventas DROP CONSTRAINT ck_ventas_medio;
ALTER TABLE public.ventas ADD CONSTRAINT ck_ventas_medio
    CHECK (medio IN ('EFECTIVO', 'MERCADO_PAGO', 'TRANSFERENCIA_MP', 'CUENTA_DNI'));
UPDATE public.ventas SET medio = 'TRANSFERENCIA_MP' WHERE medio = 'MERCADO_PAGO' AND order_id IS NULL;
ALTER TABLE public.ventas DROP CONSTRAINT ck_ventas_verificado;
ALTER TABLE public.ventas ADD CONSTRAINT ck_ventas_verificado
    CHECK ((verificado_at IS NULL AND verificado_por_id IS NULL)
        OR (verificado_at IS NOT NULL AND verificado_por_id IS NOT NULL AND medio IN ('CUENTA_DNI', 'TRANSFERENCIA_MP')));
