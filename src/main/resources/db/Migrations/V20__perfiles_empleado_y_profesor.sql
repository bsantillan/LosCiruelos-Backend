-- =====================================================================
-- V20: el perfil del EMPLEADO y del PROFESOR. Todavía no tienen datos propios, pero cada
-- rol tiene su perfil colgado de la cuenta (como clientes y admins): se crea el de cada
-- usuario que ya tiene ese rol.
-- =====================================================================

CREATE TABLE public.empleados (
    usuario_id bigint PRIMARY KEY REFERENCES public.usuarios (id)
);

CREATE TABLE public.profesores (
    usuario_id bigint PRIMARY KEY REFERENCES public.usuarios (id)
);

INSERT INTO public.empleados (usuario_id) SELECT id FROM public.usuarios WHERE rol = 'EMPLEADO';
INSERT INTO public.profesores (usuario_id) SELECT id FROM public.usuarios WHERE rol = 'PROFESOR';
