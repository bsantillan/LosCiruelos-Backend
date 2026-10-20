-- =====================================================================
-- V6: stock de 50 pelotas y 50 paletas.
-- Reemplaza el stock actual (V2 cargaba 300 pelotas). Después se ajusta desde
-- /admin/configuracion/productos.
-- =====================================================================

UPDATE public.productos SET stock = 50 WHERE codigo IN ('PELOTAS', 'PALETAS');
