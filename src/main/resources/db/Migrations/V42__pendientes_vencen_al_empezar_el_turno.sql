-- =====================================================================
-- V42: una reserva PENDIENTE vence a los 15 minutos o al empezar el turno, lo que pase primero.
-- Las pendientes que ya había con un vencimiento posterior al inicio del turno pasan a vencer al
-- empezar el turno (el job las expira y libera el turno y el stock).
-- =====================================================================

UPDATE public.reservas
SET expires_at = fecha_reserva + hora_inicio
WHERE estado = 'PENDIENTE'
  AND expires_at IS NOT NULL
  AND expires_at > fecha_reserva + hora_inicio;
