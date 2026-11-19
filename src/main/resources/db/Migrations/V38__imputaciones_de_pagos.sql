-- =====================================================================
-- V38: imputación de los pagos por concepto. Cada parte de un pago dice qué cubre: la cancha o una
-- línea de producto de la reserva (reserva_productos). En un pago cobrado, la suma de sus
-- imputaciones es lo que el club se queda de él (monto − lo devuelto). Las de un QR esperando el
-- pago son las que se aplican cuando Mercado Pago lo aprueba.
-- Los pagos que ya existen se imputan en V39 (migración Java), con la regla automática.
-- =====================================================================

CREATE TABLE public.pago_imputaciones (
    id                  BIGSERIAL PRIMARY KEY,
    pago_id             BIGINT        NOT NULL REFERENCES public.pagos (id) ON DELETE CASCADE,
    concepto            VARCHAR(20)   NOT NULL,
    reserva_producto_id BIGINT        REFERENCES public.reserva_productos (id),
    monto               NUMERIC(10,2) NOT NULL,
    CONSTRAINT ck_pago_imputaciones_concepto CHECK (concepto IN ('CANCHA', 'PRODUCTO')),
    CONSTRAINT ck_pago_imputaciones_linea CHECK ((concepto = 'CANCHA') = (reserva_producto_id IS NULL)),
    CONSTRAINT ck_pago_imputaciones_monto CHECK (monto > 0)
);

CREATE INDEX idx_pago_imputaciones_pago ON public.pago_imputaciones (pago_id);
CREATE INDEX idx_pago_imputaciones_linea ON public.pago_imputaciones (reserva_producto_id)
    WHERE reserva_producto_id IS NOT NULL;
