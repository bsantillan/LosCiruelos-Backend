-- =====================================================================
-- V35: cómo se paga una reserva que carga el personal.
--   EN_EL_CLUB   el cliente está en el club: PENDIENTE que vence a los 15 minutos si no se cobra la seña.
--   SENIA_ONLINE el cliente no está: PENDIENTE con un plazo más largo (configuracion.minutos_pago_senia_online,
--                nunca más allá del inicio del turno) y se le manda el link para pagar la seña.
--   SIN_SENIA    solo ADMIN: CONFIRMADA sin pagos, como un partido; se cobra en el club.
-- Las reservas que hace el propio cliente (y los partidos) quedan con modalidad_pago null.
-- Las que ya había cargado el personal eran todas EN_EL_CLUB.
-- =====================================================================

ALTER TABLE public.configuracion ADD COLUMN minutos_pago_senia_online integer NOT NULL DEFAULT 120;

ALTER TABLE public.reservas ADD COLUMN modalidad_pago varchar(20);
ALTER TABLE public.reservas ADD CONSTRAINT ck_reservas_modalidad_pago
    CHECK (modalidad_pago IS NULL OR modalidad_pago IN ('EN_EL_CLUB', 'SENIA_ONLINE', 'SIN_SENIA'));

UPDATE public.reservas SET modalidad_pago = 'EN_EL_CLUB'
WHERE tipo = 'NORMAL' AND created_by_id IS NOT NULL AND created_by_id <> cliente_id;
