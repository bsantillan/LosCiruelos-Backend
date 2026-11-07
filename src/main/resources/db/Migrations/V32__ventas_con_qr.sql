-- =====================================================================
-- V32: ventas sueltas cobradas con el QR de la caja de Mercado Pago. Una venta con Mercado Pago
-- queda ESPERANDO_PAGO (con el stock apartado) hasta que MP confirma el cobro: pasa a COBRADA o,
-- si el QR vence o se cancela, a NO_COBRADA (y el stock vuelve). La caja tiene un solo QR para
-- reservas y ventas: caja_qr es una fila que bloquean los dos cobros antes de ocuparla.
-- =====================================================================

ALTER TABLE public.ventas DROP CONSTRAINT ck_ventas_estado;
ALTER TABLE public.ventas ADD CONSTRAINT ck_ventas_estado
    CHECK (estado IN ('COBRADA', 'ANULADA', 'ESPERANDO_PAGO', 'NO_COBRADA'));

ALTER TABLE public.ventas ADD COLUMN order_id varchar(64);
ALTER TABLE public.ventas ADD COLUMN vence_at timestamp(6);
-- identifica a la venta ante Mercado Pago (claves de idempotencia); las que ya había reciben una
ALTER TABLE public.ventas ADD COLUMN clave_idempotencia uuid NOT NULL DEFAULT gen_random_uuid();
ALTER TABLE public.ventas ADD CONSTRAINT uk_ventas_clave_idempotencia UNIQUE (clave_idempotencia);
ALTER TABLE public.ventas ADD CONSTRAINT ck_ventas_qr
    CHECK ((order_id IS NULL AND vence_at IS NULL) OR medio = 'MERCADO_PAGO');
ALTER TABLE public.ventas ADD CONSTRAINT ck_ventas_esperando_pago
    CHECK (estado <> 'ESPERANDO_PAGO' OR vence_at IS NOT NULL);
CREATE UNIQUE INDEX uk_ventas_order_id ON public.ventas (order_id) WHERE order_id IS NOT NULL;
-- un solo QR en la caja: una sola venta esperando el pago a la vez
CREATE UNIQUE INDEX uk_ventas_qr_en_curso ON public.ventas ((true)) WHERE estado = 'ESPERANDO_PAGO';

-- el lock común de la caja (reservas y ventas): siempre una sola fila
CREATE TABLE public.caja_qr (
    id smallint PRIMARY KEY,
    CONSTRAINT ck_caja_qr_una_fila CHECK (id = 1)
);
INSERT INTO public.caja_qr (id) VALUES (1);
