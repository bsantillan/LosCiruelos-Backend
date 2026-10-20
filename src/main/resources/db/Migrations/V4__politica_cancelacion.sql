-- =====================================================================
-- V4: política de cancelación con reembolso.
-- Si el cliente cancela con al menos minutos_reembolso_total de anticipación se le
-- devuelve todo lo pagado; con al menos minutos_reembolso_senia, la seña; con menos, nada.
-- =====================================================================

ALTER TABLE public.configuracion
    ADD COLUMN minutos_reembolso_total integer NOT NULL DEFAULT 1440,   -- 24 h
    ADD COLUMN minutos_reembolso_senia integer NOT NULL DEFAULT 720,    -- 12 h
    ADD CONSTRAINT ck_configuracion_reembolso
        CHECK (minutos_reembolso_total >= minutos_reembolso_senia AND minutos_reembolso_senia >= 0);

-- Parte del pago que se devolvió (DEVUELTO) o que hay que devolver (A_REEMBOLSAR)
ALTER TABLE public.pagos
    ADD COLUMN monto_reembolsado numeric(10,2),
    ADD CONSTRAINT ck_pagos_monto_reembolsado CHECK (monto_reembolsado >= 0 AND monto_reembolsado <= monto);
