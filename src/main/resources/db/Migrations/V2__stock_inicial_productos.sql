-- =====================================================================
-- V2: stock inicial de los productos del sistema.
-- Solo se carga si el stock sigue en 0 (no pisa un stock ya configurado).
-- El precio sigue en 0: lo define el admin desde /admin/configuracion/productos.
-- =====================================================================

UPDATE public.productos SET stock = 300 WHERE codigo = 'PELOTAS' AND stock = 0;
UPDATE public.productos SET stock = 50  WHERE codigo = 'PALETAS' AND stock = 0;
