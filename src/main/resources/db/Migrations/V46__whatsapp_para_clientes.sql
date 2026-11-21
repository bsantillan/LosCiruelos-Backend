-- =====================================================================
-- V46: el ADMIN elige si el club manda mensajes de WhatsApp a los clientes. Apagado, a los
-- clientes no les sale ningún WhatsApp (ni avisos ni el código del celular): los avisos van
-- por mail y el celular se pide pero no se verifica. Arranca prendido, como hasta ahora.
-- =====================================================================

ALTER TABLE public.configuracion
    ADD COLUMN whatsapp_clientes boolean NOT NULL DEFAULT true;
