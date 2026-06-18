-- Reservas: para el job de expiración
CREATE INDEX idx_reservas_estado_expires ON public.reservas(estado, expires_at);

-- Reservas: para buscar solapamientos
CREATE INDEX idx_reservas_cancha_fecha ON public.reservas(cancha_id, fecha_reserva);

-- Reservas: para listar reservas de un cliente
CREATE INDEX idx_reservas_cliente ON public.reservas(cliente_id);

-- Pagos: para buscar pagos de una reserva
CREATE INDEX idx_pagos_reserva ON public.pagos(reserva_id);

-- Pagos: para el webhook de MP
CREATE INDEX idx_pagos_external_id ON public.pagos(external_payment_id);

-- Verification tokens: para buscar por usuario y tipo
CREATE INDEX idx_verification_usuario_type ON public.verification_tokens(usuario_id, type);

-- Refresh tokens: para buscar por usuario
CREATE INDEX idx_refresh_tokens_usuario ON public.refresh_tokens(usuario_id);