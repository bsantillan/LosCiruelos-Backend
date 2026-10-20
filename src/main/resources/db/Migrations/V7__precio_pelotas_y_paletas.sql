-- =====================================================================
-- V7: precio de $5000 por unidad para pelotas y paletas (antes en 0).
-- Las reservas existentes no cambian: cada ítem guarda el precio del momento.
-- Después se ajusta desde /admin/configuracion/productos.
-- =====================================================================

UPDATE public.productos SET precio = 5000 WHERE codigo IN ('PELOTAS', 'PALETAS');
