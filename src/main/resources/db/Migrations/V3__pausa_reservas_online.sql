-- =====================================================================
-- V3: el ADMIN puede pausar las reservas online de los clientes (por ejemplo, mientras cambia los precios).
-- Pausadas, el cliente no reserva desde la web; el personal sigue cargando reservas desde el panel.
-- =====================================================================

ALTER TABLE public.configuracion ADD COLUMN reservas_online boolean DEFAULT true NOT NULL;
-- lo que ven los clientes mientras están pausadas (opcional)
ALTER TABLE public.configuracion ADD COLUMN mensaje_reservas_pausadas varchar(200);
