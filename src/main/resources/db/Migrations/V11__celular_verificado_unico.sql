-- =====================================================================
-- V11: un celular verificado pertenece a una sola cuenta activa.
-- Las cuentas desactivadas no cuentan (un cliente puede haberse hecho una cuenta nueva).
-- =====================================================================

-- Si ya hubiera repetidos, se queda verificada la cuenta más antigua y las demás vuelven a verificarlo
UPDATE public.usuarios u
SET telefono_verificado = false
WHERE u.telefono_verificado AND u.enabled
  AND EXISTS (SELECT 1 FROM public.usuarios o
              WHERE o.telefono = u.telefono AND o.telefono_verificado AND o.enabled AND o.id < u.id);

-- Última barrera ante dos verificaciones simultáneas del mismo celular
CREATE UNIQUE INDEX uk_usuarios_celular_verificado_activo
    ON public.usuarios (telefono)
    WHERE telefono_verificado AND enabled;
