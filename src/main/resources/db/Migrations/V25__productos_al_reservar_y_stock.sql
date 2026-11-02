-- =====================================================================
-- V25: qué productos se ofrecen al reservar y cuáles controlan stock.
-- ofrecer_al_reservar: el cliente lo ve y lo pide al reservar; si no, solo lo agrega el personal.
-- controla_stock: si es false, no se limitan las unidades ni se mueve el stock (por ejemplo, un
-- producto cargado a mano en una venta). Los que ya existen siguen como hasta ahora (los dos en true).
-- =====================================================================

ALTER TABLE public.productos
    ADD COLUMN ofrecer_al_reservar boolean NOT NULL DEFAULT false,
    ADD COLUMN controla_stock boolean NOT NULL DEFAULT true;

UPDATE public.productos SET ofrecer_al_reservar = true;
