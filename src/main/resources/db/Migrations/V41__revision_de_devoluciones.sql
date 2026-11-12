-- =====================================================================
-- V41: el EMPLEADO también devuelve cobros, y el ADMIN revisa lo que devolvió. Cada devolución dice
-- si está revisada, cuándo y por quién. Las del ADMIN y las del sistema nacen revisadas; las del
-- EMPLEADO, sin revisar. Las que ya había quedan revisadas.
-- =====================================================================

ALTER TABLE public.pago_devoluciones
    ADD COLUMN revisada        BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN revisada_at     TIMESTAMP,
    ADD COLUMN revisada_por_id BIGINT REFERENCES public.usuarios (id);

CREATE INDEX idx_pago_devoluciones_sin_revisar ON public.pago_devoluciones (created_at) WHERE NOT revisada;
