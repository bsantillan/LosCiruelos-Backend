-- =====================================================================
-- V21: anticipación máxima para reservar. El cliente reserva como mucho con estos días de
-- anticipación (el personal no tiene tope) y los turnos fijos generan sus partidos hasta ahí.
-- =====================================================================

ALTER TABLE public.configuracion
    ADD COLUMN dias_anticipacion_reserva integer NOT NULL DEFAULT 30,
    ADD CONSTRAINT ck_configuracion_dias_anticipacion CHECK (dias_anticipacion_reserva BETWEEN 1 AND 365);
