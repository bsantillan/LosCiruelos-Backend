-- =====================================================================
-- V18: los avisos opcionales que elige el cliente (recordatorios y novedades/promociones)
-- pasan de la cuenta (usuarios) a su perfil de jugador (clientes): solo los recibe el CLIENTE.
-- =====================================================================

ALTER TABLE public.clientes
    ADD COLUMN recibe_recordatorios boolean NOT NULL DEFAULT true,
    ADD COLUMN recibe_promociones boolean NOT NULL DEFAULT false;

-- Todo CLIENTE tiene su perfil: a las cuentas viejas sin fila se les crea vacío, para que
-- no pierdan lo que eligieron
INSERT INTO public.clientes (usuario_id, categoria_modificada)
SELECT u.id, false
FROM public.usuarios u
WHERE u.rol = 'CLIENTE'
  AND NOT EXISTS (SELECT 1 FROM public.clientes c WHERE c.usuario_id = u.id);

UPDATE public.clientes c
SET recibe_recordatorios = u.recibe_recordatorios,
    recibe_promociones   = u.recibe_promociones
FROM public.usuarios u
WHERE u.id = c.usuario_id;

ALTER TABLE public.usuarios
    DROP COLUMN recibe_recordatorios,
    DROP COLUMN recibe_promociones;
