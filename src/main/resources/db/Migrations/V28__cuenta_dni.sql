-- =====================================================================
-- V28: cobros con Cuenta DNI (Banco Provincia). No hay API: el personal registra el pago a mano y
-- el ADMIN lo marca como verificado cuando lo controla en la cuenta del club. Las ventas sueltas
-- también se pueden cobrar con Cuenta DNI.
-- =====================================================================

ALTER TABLE public.pagos DROP CONSTRAINT ck_pagos_provider;
ALTER TABLE public.pagos ADD CONSTRAINT ck_pagos_provider
    CHECK (provider IN ('MERCADO_PAGO', 'MANUAL', 'BONIFICACION', 'CUENTA_DNI'));

-- quién y cuándo comprobó que la plata entró a la cuenta (null: sin verificar)
ALTER TABLE public.pagos ADD COLUMN verificado_at timestamp(6);
ALTER TABLE public.pagos ADD COLUMN verificado_por_id bigint REFERENCES public.usuarios (id);
ALTER TABLE public.pagos ADD CONSTRAINT ck_pagos_verificado
    CHECK ((verificado_at IS NULL AND verificado_por_id IS NULL)
        OR (verificado_at IS NOT NULL AND verificado_por_id IS NOT NULL AND provider = 'CUENTA_DNI'));
CREATE INDEX idx_pagos_cuenta_dni ON public.pagos (paid_at) WHERE provider = 'CUENTA_DNI';

ALTER TABLE public.ventas DROP CONSTRAINT ck_ventas_medio;
ALTER TABLE public.ventas ADD CONSTRAINT ck_ventas_medio CHECK (medio IN ('EFECTIVO', 'MERCADO_PAGO', 'CUENTA_DNI'));
