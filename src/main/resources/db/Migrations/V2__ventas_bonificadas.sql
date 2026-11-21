-- =====================================================================
-- V2: el ADMIN puede bonificar una venta suelta (no se le cobra al cliente). Los productos se entregan
-- (el stock baja igual), pero no es plata: no cuenta en la caja ni en lo vendido, va en lo bonificado.
-- =====================================================================

ALTER TABLE public.ventas DROP CONSTRAINT ck_ventas_medio;
ALTER TABLE public.ventas ADD CONSTRAINT ck_ventas_medio
    CHECK (medio IN ('EFECTIVO', 'MERCADO_PAGO', 'TRANSFERENCIA_MP', 'TARJETA_MP', 'CUENTA_DNI', 'TARJETA', 'BONIFICACION'));

-- el motivo que escribe el ADMIN (opcional), solo en las bonificadas
ALTER TABLE public.ventas ADD COLUMN motivo_bonificacion varchar(200);
ALTER TABLE public.ventas ADD CONSTRAINT ck_ventas_motivo_bonificacion
    CHECK (motivo_bonificacion IS NULL OR medio = 'BONIFICACION');
