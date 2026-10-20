-- =====================================================================
-- V10: consentimiento de notificaciones en el registro y avisos que elige el cliente.
-- Las cancelaciones se avisan siempre; los recordatorios y las novedades/promociones son opcionales.
-- =====================================================================

ALTER TABLE public.usuarios
    ADD COLUMN notificaciones_aceptadas_at timestamp(6),
    ADD COLUMN recibe_recordatorios boolean NOT NULL DEFAULT true,
    ADD COLUMN recibe_promociones boolean NOT NULL DEFAULT false;
