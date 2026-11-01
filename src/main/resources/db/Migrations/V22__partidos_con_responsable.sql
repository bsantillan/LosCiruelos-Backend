-- =====================================================================
-- V22: el cliente de un partido es su responsable, el jugador 1 (un cliente registrado), y no el
-- ADMIN que lo armó (que queda en created_by_id).
-- Partidos y turnos fijos que tienen un invitado como jugador 1: el primer jugador registrado
-- pasa a la posición 1 (intercambian lugares). Los que no tienen ningún registrado quedan como
-- están: el partido sigue a nombre del ADMIN y el turno fijo no genera partidos hasta que se lo
-- modifique.
-- =====================================================================

-- las restricciones de posición son diferidas: el intercambio se controla al terminar
WITH cambio AS (
    SELECT j.reserva_id, min(j.posicion) AS registrado
    FROM public.reserva_jugadores j
    WHERE j.usuario_id IS NOT NULL
      AND EXISTS (SELECT 1 FROM public.reserva_jugadores p
                  WHERE p.reserva_id = j.reserva_id AND p.posicion = 1 AND p.usuario_id IS NULL)
    GROUP BY j.reserva_id
)
UPDATE public.reserva_jugadores j
SET posicion = CASE WHEN j.posicion = 1 THEN c.registrado ELSE 1 END
FROM cambio c
WHERE j.reserva_id = c.reserva_id AND j.posicion IN (1, c.registrado);

WITH cambio AS (
    SELECT j.turno_fijo_id, min(j.posicion) AS registrado
    FROM public.turno_fijo_jugadores j
    WHERE j.usuario_id IS NOT NULL
      AND EXISTS (SELECT 1 FROM public.turno_fijo_jugadores p
                  WHERE p.turno_fijo_id = j.turno_fijo_id AND p.posicion = 1 AND p.usuario_id IS NULL)
    GROUP BY j.turno_fijo_id
)
UPDATE public.turno_fijo_jugadores j
SET posicion = CASE WHEN j.posicion = 1 THEN c.registrado ELSE 1 END
FROM cambio c
WHERE j.turno_fijo_id = c.turno_fijo_id AND j.posicion IN (1, c.registrado);

UPDATE public.reservas r
SET cliente_id = j.usuario_id
FROM public.reserva_jugadores j
WHERE r.tipo = 'PARTIDO'
  AND j.reserva_id = r.id AND j.posicion = 1 AND j.usuario_id IS NOT NULL;
