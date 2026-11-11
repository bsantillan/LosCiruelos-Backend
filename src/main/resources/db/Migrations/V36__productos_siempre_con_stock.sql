-- =====================================================================
-- V36: todos los productos controlan stock (el stock queda como está). Los que no lo controlaban
-- (cargados a mano, como el Gatorade con stock 0) se trataban como ilimitados; desde ahora se
-- reponen como cualquier otro. Los productos nunca se borran: se dan de baja (activo = false).
-- =====================================================================

UPDATE public.productos SET controla_stock = true WHERE controla_stock = false;
ALTER TABLE public.productos ALTER COLUMN controla_stock SET DEFAULT true;
ALTER TABLE public.productos ADD CONSTRAINT ck_productos_controla_stock CHECK (controla_stock);
