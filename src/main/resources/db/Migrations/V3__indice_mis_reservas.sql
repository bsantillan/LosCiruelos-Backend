-- Listado "Mis reservas": filtra por cliente y ordena por fecha y hora del turno
CREATE INDEX idx_reservas_cliente_fecha ON public.reservas (cliente_id, fecha_reserva DESC, hora_inicio DESC);

-- El índice anterior por cliente queda cubierto por este
DROP INDEX IF EXISTS public.idx_reservas_cliente;
