-- =====================================================================
-- V9: interruptor general de los avisos de cada ADMIN.
-- Con activos = false no recibe ningún aviso, pero se conserva qué eligió de cada uno.
-- =====================================================================

ALTER TABLE public.avisos_admin ADD COLUMN activos boolean NOT NULL DEFAULT true;
