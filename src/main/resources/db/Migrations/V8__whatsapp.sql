-- =====================================================================
-- V8: avisos por WhatsApp o mail.
-- Cada usuario elige el canal; el teléfono se verifica con un código por WhatsApp.
-- Los ADMIN eligen qué avisos de reservas reciben, y el cliente recibe un recordatorio
-- antes del turno.
-- =====================================================================

ALTER TABLE public.usuarios
    ADD COLUMN telefono_verificado boolean NOT NULL DEFAULT false,
    ADD COLUMN canal_notificaciones varchar(20) NOT NULL DEFAULT 'EMAIL',
    ADD COLUMN acepta_whatsapp_at timestamp(6),
    ADD CONSTRAINT ck_usuarios_canal_notificaciones CHECK (canal_notificaciones IN ('EMAIL', 'WHATSAPP'));

-- Qué avisos recibe cada ADMIN (sin fila: todos)
CREATE TABLE public.avisos_admin (
    usuario_id         bigint PRIMARY KEY REFERENCES public.usuarios (id),
    reserva_confirmada boolean NOT NULL DEFAULT true,
    saldo_cobrado      boolean NOT NULL DEFAULT true,
    reserva_cancelada  boolean NOT NULL DEFAULT true,
    reembolso_manual   boolean NOT NULL DEFAULT true
);

-- Código para verificar el teléfono por WhatsApp
ALTER TABLE public.verification_tokens DROP CONSTRAINT ck_verification_tokens_type;
ALTER TABLE public.verification_tokens ADD CONSTRAINT ck_verification_tokens_type
    CHECK (type IN ('VERIFY_EMAIL', 'PASSWORD_RESET', 'VERIFY_PHONE'));

-- Recordatorio del turno
ALTER TABLE public.reservas ADD COLUMN recordatorio_enviado_at timestamp(6);

ALTER TABLE public.configuracion
    ADD COLUMN recordatorio_activo boolean NOT NULL DEFAULT true,
    ADD COLUMN minutos_recordatorio integer NOT NULL DEFAULT 120,
    ADD CONSTRAINT ck_configuracion_recordatorio CHECK (minutos_recordatorio BETWEEN 15 AND 1440);
