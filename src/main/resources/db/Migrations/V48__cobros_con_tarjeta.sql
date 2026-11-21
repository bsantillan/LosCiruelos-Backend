-- =====================================================================
-- V48: cobros con tarjeta en el club. TARJETA_MP: en el posnet de Mercado Pago (la plata entra a la
-- cuenta de MP); TARJETA: en otro posnet (Clover u otro). Se cargan a mano y no se verifican (el
-- posnet aprueba el cobro en el momento), así que no entran en ck_pagos_verificado ni en
-- idx_pagos_verificables.
-- =====================================================================

ALTER TABLE public.pagos DROP CONSTRAINT ck_pagos_provider;
ALTER TABLE public.pagos ADD CONSTRAINT ck_pagos_provider
    CHECK (provider IN ('MERCADO_PAGO', 'MANUAL', 'BONIFICACION', 'CUENTA_DNI', 'TRANSFERENCIA_MP', 'TARJETA_MP', 'TARJETA'));

ALTER TABLE public.ventas DROP CONSTRAINT ck_ventas_medio;
ALTER TABLE public.ventas ADD CONSTRAINT ck_ventas_medio
    CHECK (medio IN ('EFECTIVO', 'MERCADO_PAGO', 'TRANSFERENCIA_MP', 'TARJETA_MP', 'CUENTA_DNI', 'TARJETA'));
