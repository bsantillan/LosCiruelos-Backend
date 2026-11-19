-- =====================================================================
-- V43: cuándo y quién canceló cada reserva (para las métricas de cancelaciones: por el cliente o por
-- el club, y con cuánta anticipación). Las que ya estaban canceladas quedan sin dato (null).
-- cancelada_por_id null con cancelada_at: la canceló el sistema (por ejemplo, Mercado Pago devolvió todo).
-- =====================================================================

ALTER TABLE public.reservas
    ADD COLUMN cancelada_at     TIMESTAMP,
    ADD COLUMN cancelada_por_id BIGINT REFERENCES public.usuarios (id);
