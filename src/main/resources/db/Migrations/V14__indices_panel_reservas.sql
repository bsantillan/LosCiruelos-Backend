-- =====================================================================
-- V14: índices para el listado de reservas del personal (GET /admin/reservas).
-- Los filtros más usados son por fecha del turno con o sin estado (el día, el mes, las que
-- deben plata) y por cancha; el orden es por fecha y hora del turno.
-- =====================================================================

-- Fecha y hora del turno (reemplaza a idx_reservas_fecha: también sirve para ordenar)
CREATE INDEX idx_reservas_fecha_hora ON public.reservas (fecha_reserva, hora_inicio);
DROP INDEX IF EXISTS public.idx_reservas_fecha;

-- Por estado y fecha: "las que deben plata" (SEÑADA, PARCIALMENTE_PAGADA) y el filtro por estado
CREATE INDEX idx_reservas_estado_fecha ON public.reservas (estado, fecha_reserva);
