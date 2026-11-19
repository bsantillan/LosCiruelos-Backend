-- =====================================================================
-- V40: anular y devolver cobros de las reservas.
-- - Un cobro del club cargado por error se anula (estado ANULADO): no cuenta en la caja ni cubre
--   nada, y queda quién, cuándo y por qué.
-- - Lo que se le devuelve al cliente de un cobro queda en pago_devoluciones (con sus líneas: cuánto
--   de la cancha o de cada producto). Las devoluciones salen de la caja cuando se entregan o Mercado
--   Pago las confirma (entregado_at).
-- Los reembolsos que ya había (pagos DEVUELTO, A_REEMBOLSAR o REEMBOLSO_PENDIENTE) pasan a ser una
-- devolución cada uno, sin detalle por línea, con el estado del pago; los DEVUELTO, entregados en su
-- reembolsado_at (así la caja de cada día no cambia).
-- =====================================================================

ALTER TABLE public.pagos DROP CONSTRAINT ck_pagos_estado;
ALTER TABLE public.pagos ADD CONSTRAINT ck_pagos_estado CHECK (estado IN
    ('PENDIENTE', 'APROBADO', 'RECHAZADO', 'EN_PROCESO', 'DEVUELTO', 'EXPIRADO', 'A_REEMBOLSAR',
     'REEMBOLSO_PENDIENTE', 'ANULADO'));

ALTER TABLE public.pagos
    ADD COLUMN anulado_at       TIMESTAMP,
    ADD COLUMN anulado_por_id   BIGINT REFERENCES public.usuarios (id),
    ADD COLUMN motivo_anulacion VARCHAR(200);
ALTER TABLE public.pagos ADD CONSTRAINT ck_pagos_anulado CHECK (
    (estado = 'ANULADO') = (anulado_at IS NOT NULL AND anulado_por_id IS NOT NULL AND motivo_anulacion IS NOT NULL));

CREATE TABLE public.pago_devoluciones (
    id                BIGSERIAL PRIMARY KEY,
    pago_id           BIGINT        NOT NULL REFERENCES public.pagos (id) ON DELETE CASCADE,
    monto             NUMERIC(10,2) NOT NULL,
    estado            VARCHAR(20)   NOT NULL,
    motivo            VARCHAR(200),
    created_at        TIMESTAMP     NOT NULL,
    registrada_por_id BIGINT        REFERENCES public.usuarios (id),
    entregado_at      TIMESTAMP,
    entregado_por_id  BIGINT        REFERENCES public.usuarios (id),
    clave             UUID          NOT NULL UNIQUE,
    CONSTRAINT ck_pago_devoluciones_estado CHECK (estado IN ('A_REEMBOLSAR', 'REEMBOLSO_PENDIENTE', 'DEVUELTO')),
    CONSTRAINT ck_pago_devoluciones_monto CHECK (monto > 0),
    CONSTRAINT ck_pago_devoluciones_entrega CHECK ((estado = 'DEVUELTO') = (entregado_at IS NOT NULL))
);
CREATE INDEX idx_pago_devoluciones_pago ON public.pago_devoluciones (pago_id);
CREATE INDEX idx_pago_devoluciones_entregado_at ON public.pago_devoluciones (entregado_at) WHERE estado = 'DEVUELTO';
CREATE INDEX idx_pago_devoluciones_a_entregar ON public.pago_devoluciones (created_at) WHERE estado = 'A_REEMBOLSAR';

CREATE TABLE public.pago_devolucion_lineas (
    id                  BIGSERIAL PRIMARY KEY,
    devolucion_id       BIGINT        NOT NULL REFERENCES public.pago_devoluciones (id) ON DELETE CASCADE,
    concepto            VARCHAR(20)   NOT NULL,
    -- la línea se puede sacar de la reserva después de devolverla: queda el nombre
    reserva_producto_id BIGINT        REFERENCES public.reserva_productos (id) ON DELETE SET NULL,
    nombre              VARCHAR(120)  NOT NULL,
    monto               NUMERIC(10,2) NOT NULL,
    CONSTRAINT ck_pago_devolucion_lineas_concepto CHECK (concepto IN ('CANCHA', 'PRODUCTO')),
    CONSTRAINT ck_pago_devolucion_lineas_monto CHECK (monto > 0)
);
CREATE INDEX idx_pago_devolucion_lineas_devolucion ON public.pago_devolucion_lineas (devolucion_id);

-- Los reembolsos que ya había: una devolución por pago (null en monto_reembolsado era el total)
UPDATE public.pagos SET monto_reembolsado = monto
WHERE estado IN ('DEVUELTO', 'A_REEMBOLSAR', 'REEMBOLSO_PENDIENTE') AND monto_reembolsado IS NULL;

INSERT INTO public.pago_devoluciones (pago_id, monto, estado, motivo, created_at, entregado_at, clave)
SELECT id, monto_reembolsado, estado, NULL,
       COALESCE(reembolso_solicitado_at, reembolsado_at, paid_at, created_at),
       CASE WHEN estado = 'DEVUELTO' THEN COALESCE(reembolsado_at, paid_at, created_at) END,
       gen_random_uuid()
FROM public.pagos
WHERE estado IN ('DEVUELTO', 'A_REEMBOLSAR', 'REEMBOLSO_PENDIENTE')
  AND provider <> 'BONIFICACION'
  AND monto_reembolsado > 0;
