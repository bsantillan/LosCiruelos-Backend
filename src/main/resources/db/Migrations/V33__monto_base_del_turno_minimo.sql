-- =====================================================================
-- V33: monto_reserva pasa a ser el precio del turno mínimo (duracion_minima_turno), y
-- monto_x_media_hora se suma solo por cada media hora por encima del mínimo:
--   precio = monto_reserva + monto_x_media_hora * ((duracion - duracion_minima_turno) / 30)
-- Antes era monto_reserva + monto_x_media_hora * (duracion / 30). Para que ningún precio cambie,
-- al monto base se le suman las medias horas del turno mínimo. Las reservas ya creadas guardan su
-- monto y no se tocan.
-- =====================================================================

UPDATE public.configuracion
SET monto_reserva = monto_reserva + monto_x_media_hora * (duracion_minima_turno / 30);
