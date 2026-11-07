-- =====================================================================
-- V30: anulación de ventas sueltas. Una venta registrada no se borra: se anula con motivo y queda
-- quién y cuándo. Una venta anulada no cuenta en la caja ni en lo vendido, y su stock vuelve.
-- Las ventas que ya había quedan COBRADA.
-- =====================================================================

ALTER TABLE public.ventas ADD COLUMN estado varchar(20) NOT NULL DEFAULT 'COBRADA';
ALTER TABLE public.ventas ADD CONSTRAINT ck_ventas_estado CHECK (estado IN ('COBRADA', 'ANULADA'));

ALTER TABLE public.ventas ADD COLUMN anulada_at timestamp(6);
ALTER TABLE public.ventas ADD COLUMN anulada_por_id bigint REFERENCES public.usuarios (id);
ALTER TABLE public.ventas ADD COLUMN motivo_anulacion varchar(200);
ALTER TABLE public.ventas ADD CONSTRAINT ck_ventas_anulada
    CHECK ((estado = 'ANULADA' AND anulada_at IS NOT NULL AND anulada_por_id IS NOT NULL AND motivo_anulacion IS NOT NULL)
        OR (estado <> 'ANULADA' AND anulada_at IS NULL AND anulada_por_id IS NULL AND motivo_anulacion IS NULL));
