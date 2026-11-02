-- =====================================================================
-- V28: cobros con Cuenta DNI (Banco Provincia). No hay API: el personal registra el pago a mano
-- con el número de comprobante, que no se puede cargar dos veces. Las ventas sueltas también se
-- pueden cobrar con Cuenta DNI.
-- =====================================================================

ALTER TABLE public.pagos DROP CONSTRAINT ck_pagos_provider;
ALTER TABLE public.pagos ADD CONSTRAINT ck_pagos_provider
    CHECK (provider IN ('MERCADO_PAGO', 'MANUAL', 'BONIFICACION', 'CUENTA_DNI'));

ALTER TABLE public.pagos ADD COLUMN comprobante varchar(64);
ALTER TABLE public.pagos ADD CONSTRAINT ck_pagos_comprobante
    CHECK (comprobante IS NULL OR provider = 'CUENTA_DNI');
CREATE UNIQUE INDEX uk_pagos_comprobante ON public.pagos (comprobante) WHERE comprobante IS NOT NULL;

ALTER TABLE public.ventas DROP CONSTRAINT ck_ventas_medio;
ALTER TABLE public.ventas ADD CONSTRAINT ck_ventas_medio CHECK (medio IN ('EFECTIVO', 'MERCADO_PAGO', 'CUENTA_DNI'));
