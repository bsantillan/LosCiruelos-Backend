-- =====================================================================
-- V23: un partido se paga como un todo (cómo se dividen el monto es cosa de los jugadores): los
-- pagos dejan de indicar qué jugador pagó.
-- =====================================================================

ALTER TABLE public.pagos DROP COLUMN jugador_id;
