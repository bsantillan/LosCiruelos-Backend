-- =====================================================================
-- V47: cada mensaje de WhatsApp que el club mandó bien (cada uno lo cobra Meta), para que el ADMIN
-- vea cuántos se mandan. No se guardan datos personales: el motivo (código del celular o aviso),
-- qué aviso, si era para un cliente y cuándo.
-- =====================================================================

CREATE TABLE public.mensajes_whatsapp (
    id           bigserial   PRIMARY KEY,
    motivo       varchar(20) NOT NULL,
    aviso        varchar(40),
    para_cliente boolean     NOT NULL,
    enviado_at   timestamp   NOT NULL,
    CONSTRAINT ck_mensajes_whatsapp_motivo CHECK (motivo IN ('CODIGO', 'AVISO')),
    CONSTRAINT ck_mensajes_whatsapp_aviso CHECK ((motivo = 'AVISO') = (aviso IS NOT NULL))
);

CREATE INDEX idx_mensajes_whatsapp_enviado_at ON public.mensajes_whatsapp (enviado_at);
