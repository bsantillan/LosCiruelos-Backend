-- =====================================================================
-- V24: un partido no tiene seña. Pasa de CONFIRMADA a PARCIALMENTE_PAGADA con lo primero que se
-- paga y a PAGADA (o COMPLETADA) cuando se paga entero: los partidos SEÑADA pasan a
-- PARCIALMENTE_PAGADA.
-- =====================================================================

UPDATE public.reservas SET estado = 'PARCIALMENTE_PAGADA' WHERE tipo = 'PARTIDO' AND estado = 'SEÑADA';
