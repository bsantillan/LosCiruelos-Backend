-- =====================================================================
-- V19: el perfil del ADMIN. Los avisos de reservas que elige cada ADMIN son datos de su
-- cuenta: avisos_admin pasa a ser admins (una fila por ADMIN, con clave usuario_id).
-- Los ADMIN sin fila recibían todos los avisos: se les crea con todo activado.
-- =====================================================================

ALTER TABLE public.avisos_admin RENAME TO admins;
ALTER TABLE public.admins RENAME CONSTRAINT avisos_admin_pkey TO admins_pkey;
ALTER TABLE public.admins RENAME CONSTRAINT avisos_admin_usuario_id_fkey TO admins_usuario_id_fkey;

INSERT INTO public.admins (usuario_id)
SELECT u.id
FROM public.usuarios u
WHERE u.rol = 'ADMIN'
  AND NOT EXISTS (SELECT 1 FROM public.admins a WHERE a.usuario_id = u.id);
