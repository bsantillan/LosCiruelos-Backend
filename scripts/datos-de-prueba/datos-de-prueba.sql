-- =====================================================================
-- Datos de prueba para la base de DESARROLLO. Nunca en producción.
--
-- Borra todos los datos (el esquema y el historial de Flyway no se tocan) y carga un ecosistema
-- para probar el sistema a mano: configuración, canchas y productos como los deja la app, los
-- ADMIN que había (mismo mail y contraseña), un empleado, un profesor, clientes y reservas en
-- varios estados, un consumo cargado después de jugar y ventas sueltas. Las fechas son relativas
-- al día en que se corre.
--
-- Cómo correrlo: ver README.md en esta carpeta. Todas las cuentas nuevas entran con la
-- contraseña Prueba123! (los ADMIN conservan la suya).
--
-- Variables opcionales (psql -v): mail_cliente y tel_cliente, el mail y el celular del cliente
-- principal (el celular en formato de WhatsApp: 549 + característica + número, solo dígitos).
-- Sin ellas usa cliente@test.com y un celular inexistente.
-- =====================================================================

\set ON_ERROR_STOP on
SET client_encoding = 'UTF8';
SET client_min_messages = warning;
-- las fechas y horas como las ve la app (now() y current_date del contenedor están en UTC)
SET TIME ZONE 'America/Argentina/Buenos_Aires';

\if :{?mail_cliente}
\else
\set mail_cliente 'cliente@test.com'
\endif
\if :{?tel_cliente}
\else
\set tel_cliente '5491100000001'
\endif

BEGIN;

CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- ── Los ADMIN se conservan: mismo mail, contraseña, nombre, celular y canal de avisos ──
CREATE TEMP TABLE admins_a_conservar ON COMMIT DROP AS
SELECT email, password_hash, nombre, apellido, provider, telefono, telefono_verificado, canal_notificaciones,
       acepta_whatsapp_at
FROM usuarios
WHERE rol = 'ADMIN'
ORDER BY id;

-- ── Borrar todos los datos ───────────────────────────────────────────────
TRUNCATE admins, canchas, clientes, configuracion, dias, empleados, envios_codigo_telefono, pagos,
         productos, profesores, refresh_tokens, reserva_jugadores, reserva_productos, reservas,
         turno_fijo_jugadores, turno_fijo_productos, turnos_fijos, usuarios, venta_items, ventas,
         verification_tokens
         RESTART IDENTITY CASCADE;

-- ── Lo básico: lo mismo que dejan las migraciones y la carga inicial ─────
INSERT INTO configuracion (id, monto_reserva, monto_x_media_hora, porcentaje_senia, duracion_minima_turno,
                           duracion_maxima_turno, minutos_reembolso_total, minutos_reembolso_senia,
                           recordatorio_activo, minutos_recordatorio, dias_anticipacion_reserva)
VALUES (1, 15000, 5000, 50, 90, 180, 1440, 720, true, 120, 30);

INSERT INTO dias (dia, horario_inicio, horario_fin, configuracion_general_id) VALUES
    ('Lunes', '08:00', '23:00', 1), ('Martes', '08:00', '23:00', 1), ('Miércoles', '08:00', '23:00', 1),
    ('Jueves', '08:00', '23:00', 1), ('Viernes', '08:00', '23:00', 1), ('Sábado', '09:00', '22:00', 1),
    ('Domingo', '09:00', '20:00', 1);

INSERT INTO canchas (numero, descripcion)
SELECT n, 'Cancha de césped sintético con iluminación LED.' FROM generate_series(1, 4) n;

INSERT INTO productos (codigo, nombre, descripcion, tipo, precio, stock, activo, ofrecer_al_reservar, controla_stock) VALUES
    ('PELOTAS', 'Pelotas', 'Pelota de pádel, precio por unidad', 'VENTA', 5000, 50, true, true, true),
    ('PALETAS', 'Paletas', 'Alquiler de paleta durante el turno, precio por unidad', 'ALQUILER', 5000, 50, true, true, true),
    -- se venden en el club (no se ofrecen al reservar); todos controlan stock
    (NULL, 'Agua mineral', 'Botella de 500 ml', 'VENTA', 1500, 24, true, false, true),
    (NULL, 'Gatorade', NULL, 'VENTA', 3000, 20, true, false, true);

-- ── Ayudas (solo existen durante esta sesión) ────────────────────────────
CREATE FUNCTION pg_temp.usuario(p_email text) RETURNS bigint LANGUAGE sql AS
$$ SELECT id FROM usuarios WHERE email = p_email $$;

CREATE FUNCTION pg_temp.admin() RETURNS bigint LANGUAGE sql AS
$$ SELECT min(id) FROM usuarios WHERE rol = 'ADMIN' $$;

-- Cuenta con su perfil de rol (como Usuario.asignarRol), avisos por mail
CREATE FUNCTION pg_temp.cuenta(p_email text, p_nombre text, p_apellido text, p_rol text, p_telefono text,
        p_tel_verificado boolean, p_provider text DEFAULT 'LOCAL', p_enabled boolean DEFAULT true)
RETURNS bigint LANGUAGE plpgsql AS $$
DECLARE v_id bigint;
BEGIN
    INSERT INTO usuarios (nombre, apellido, email, telefono, telefono_verificado, password_hash, terms_accepted,
                          terms_accepted_at, notificaciones_aceptadas_at, email_verificado, enabled, rol, provider,
                          canal_notificaciones)
    VALUES (p_nombre, p_apellido, p_email, p_telefono, p_tel_verificado,
            CASE WHEN p_provider = 'LOCAL' THEN crypt('Prueba123!', gen_salt('bf', 10)) END,
            true, now() - interval '60 days', now() - interval '60 days', true, p_enabled, p_rol, p_provider, 'EMAIL')
    RETURNING id INTO v_id;
    CASE p_rol
        WHEN 'EMPLEADO' THEN INSERT INTO empleados (usuario_id) VALUES (v_id);
        WHEN 'PROFESOR' THEN INSERT INTO profesores (usuario_id) VALUES (v_id);
        WHEN 'ADMIN' THEN INSERT INTO admins (usuario_id) VALUES (v_id);
        ELSE NULL; -- el perfil del cliente lo carga pg_temp.cliente
    END CASE;
    RETURN v_id;
END $$;

CREATE FUNCTION pg_temp.cliente(p_email text, p_nombre text, p_apellido text, p_telefono text,
        p_tel_verificado boolean, p_categoria text, p_posicion text, p_provider text DEFAULT 'LOCAL',
        p_enabled boolean DEFAULT true)
RETURNS bigint LANGUAGE plpgsql AS $$
DECLARE v_id bigint;
BEGIN
    v_id := pg_temp.cuenta(p_email, p_nombre, p_apellido, 'CLIENTE', p_telefono, p_tel_verificado, p_provider,
                           p_enabled);
    INSERT INTO clientes (usuario_id, categoria, posicion, categoria_actualizada_at)
    VALUES (v_id, p_categoria, p_posicion, CASE WHEN p_categoria IS NOT NULL THEN now() - interval '60 days' END);
    RETURN v_id;
END $$;

-- Reserva normal; el monto de la cancha sale de la configuración (como Configuracion.montoTurno)
CREATE FUNCTION pg_temp.reserva(p_cliente text, p_creada_por text, p_cancha int, p_dias int, p_inicio time,
        p_fin time, p_estado text, p_creada_hace interval DEFAULT interval '2 days',
        p_extra numeric DEFAULT 0)
RETURNS bigint LANGUAGE plpgsql AS $$
DECLARE v_id bigint; v_total numeric;
BEGIN
    -- monto_reserva es el turno mínimo; se suma monto_x_media_hora por cada media hora de más
    SELECT monto_reserva + monto_x_media_hora
           * (greatest(extract(epoch FROM p_fin - p_inicio)::int / 60 - duracion_minima_turno, 0) / 30)
    INTO v_total FROM configuracion WHERE id = 1;
    INSERT INTO reservas (cancha_id, cliente_id, created_by_id, fecha_reserva, hora_inicio, hora_fin, monto_total,
                          estado, created_at, expires_at, tipo)
    VALUES ((SELECT id FROM canchas WHERE numero = p_cancha), pg_temp.usuario(p_cliente),
            pg_temp.usuario(p_creada_por), current_date + p_dias, p_inicio, p_fin, v_total + p_extra, p_estado,
            now() - p_creada_hace,
            CASE WHEN p_estado = 'PENDIENTE' THEN now() + interval '15 minutes'
                 WHEN p_estado = 'EXPIRADA' THEN now() - p_creada_hace + interval '15 minutes' END,
            'NORMAL')
    RETURNING id INTO v_id;
    RETURN v_id;
END $$;

-- Pago: MANUAL (efectivo en el club), MERCADO_PAGO (online con external_id, o en el club sin él),
-- CUENTA_DNI (el ADMIN lo verifica después contra la cuenta) o BONIFICACION. p_devuelto es cuánto se devolvió (o hay que devolver) de ese pago.
CREATE FUNCTION pg_temp.pago(p_reserva bigint, p_monto numeric, p_estado text, p_provider text,
        p_pagado_hace interval, p_registrado_por bigint DEFAULT NULL, p_external text DEFAULT NULL,
        p_devuelto numeric DEFAULT NULL, p_devuelto_hace interval DEFAULT NULL, p_motivo text DEFAULT NULL)
RETURNS void LANGUAGE sql AS $$
    INSERT INTO pagos (reserva_id, monto, estado, provider, created_at, paid_at, external_payment_id, preference_id,
                       monto_reembolsado, reembolsado_at, clave_idempotencia, registrado_por_id, motivo)
    VALUES (p_reserva, p_monto, p_estado, p_provider, now() - p_pagado_hace,
            CASE WHEN p_estado <> 'EXPIRADO' THEN now() - p_pagado_hace END,
            p_external, CASE WHEN p_external IS NOT NULL OR p_estado = 'EXPIRADO' THEN 'pref-prueba-' || p_reserva END,
            p_devuelto, now() - p_devuelto_hace, gen_random_uuid(), p_registrado_por, p_motivo)
$$;

-- ── Usuarios ─────────────────────────────────────────────────────────────
-- (las variables de psql no llegan adentro de un DO: el mail y el celular pasan por set_config)
INSERT INTO usuarios (nombre, apellido, email, password_hash, telefono, telefono_verificado, terms_accepted,
                      terms_accepted_at, email_verificado, enabled, rol, provider, canal_notificaciones,
                      acepta_whatsapp_at)
SELECT nombre, apellido, email, password_hash, telefono, telefono_verificado, true, now() - interval '90 days', true,
       true, 'ADMIN', provider, canal_notificaciones, acepta_whatsapp_at
FROM admins_a_conservar;
INSERT INTO admins (usuario_id) SELECT id FROM usuarios WHERE rol = 'ADMIN';

SELECT set_config('datos.mail_cliente', :'mail_cliente', true), set_config('datos.tel_cliente', :'tel_cliente', true) \gset
DO $$
BEGIN
    PERFORM pg_temp.cuenta('empleado@test.com', 'Carla', 'Méndez', 'EMPLEADO', '5491100000101', true);
    PERFORM pg_temp.cuenta('profesor@test.com', 'Pablo', 'Sosa', 'PROFESOR', '5491100000102', true);

    PERFORM pg_temp.cliente(current_setting('datos.mail_cliente'), 'Cliente', 'Principal',
                            current_setting('datos.tel_cliente'), true, 'Cuarta', 'Drive');
    PERFORM pg_temp.cliente('ana@test.com', 'Ana', 'Gómez', '5491100000002', true, 'Quinta', 'Reves');
    PERFORM pg_temp.cliente('martin@test.com', 'Martín', 'López', '5491100000003', true, 'Tercera', 'Ambos');
    PERFORM pg_temp.cliente('lucia@test.com', 'Lucía', 'Fernández', '5491100000004', true, 'Sexta', 'Drive');
    -- perfil incompleto: sin categoría ni posición y con el celular sin verificar (no puede reservar)
    PERFORM pg_temp.cliente('sofia@test.com', 'Sofía', 'Ruiz', '5491100000005', false, NULL, NULL);
    -- entró con Google: sin contraseña
    PERFORM pg_temp.cliente('valentina@test.com', 'Valentina', 'Díaz', '5491100000006', true, 'Septima', 'Reves',
                           'GOOGLE');
    -- cuenta desactivada
    PERFORM pg_temp.cliente('diego@test.com', 'Diego', 'Torres', '5491100000007', true, 'Segunda', 'Drive', 'LOCAL',
                           false);
END $$;

-- ── Reservas ─────────────────────────────────────────────────────────────
-- Montos con la configuración de arriba: 90 min = $15.000 (seña $7.500), 120 min = $20.000.
-- Horarios entre las 9 y las 20, que valen cualquier día de la semana.
DO $$
DECLARE
    c text := current_setting('datos.mail_cliente');
    emp bigint := pg_temp.usuario('empleado@test.com');
    r bigint;
BEGIN
    -- Futuras
    r := pg_temp.reserva(c, c, 1, 1, '18:00', '19:30', 'SEÑADA', interval '1 day');
    PERFORM pg_temp.pago(r, 7500, 'APROBADO', 'MERCADO_PAGO', interval '1 day', NULL, 'mp-prueba-' || r);

    r := pg_temp.reserva(c, c, 2, 3, '18:00', '19:30', 'PENDIENTE', interval '1 minute');  -- vence en 15 minutos

    r := pg_temp.reserva('ana@test.com', 'empleado@test.com', 1, 2, '16:00', '17:30', 'PAGADA');
    PERFORM pg_temp.pago(r, 15000, 'APROBADO', 'MANUAL', interval '2 days', emp);

    -- 2 h con 2 paletas alquiladas y 1 pelota vendida: $20.000 + $15.000
    r := pg_temp.reserva('martin@test.com', 'martin@test.com', 3, 5, '17:00', '19:00', 'SEÑADA', interval '3 days',
                         15000);
    INSERT INTO reserva_productos (reserva_id, producto_id, cantidad, precio_unitario)
    SELECT r, id, CASE codigo WHEN 'PALETAS' THEN 2 ELSE 1 END, precio FROM productos;
    UPDATE productos SET stock = stock - 1 WHERE codigo = 'PELOTAS';
    PERFORM pg_temp.pago(r, 17500, 'APROBADO', 'MERCADO_PAGO', interval '3 days', NULL, 'mp-prueba-' || r);

    -- bonificada a medias por el ADMIN y el resto en efectivo
    r := pg_temp.reserva('lucia@test.com', 'lucia@test.com', 4, 7, '18:00', '19:30', 'PAGADA', interval '4 days');
    PERFORM pg_temp.pago(r, 7500, 'APROBADO', 'BONIFICACION', interval '4 days', pg_temp.admin(), NULL, NULL, NULL,
                         'Socia del club');
    PERFORM pg_temp.pago(r, 7500, 'APROBADO', 'MANUAL', interval '4 days', emp);

    -- el personal le cobró una parte, menos que la seña
    r := pg_temp.reserva(c, 'empleado@test.com', 2, 10, '10:00', '11:30', 'PARCIALMENTE_PAGADA');
    PERFORM pg_temp.pago(r, 3000, 'APROBADO', 'MANUAL', interval '2 days', emp);

    -- hoy a la tarde, señada con Cuenta DNI en el club (para el resumen de hoy del EMPLEADO)
    r := pg_temp.reserva('ana@test.com', 'empleado@test.com', 4, 0, '18:30', '20:00', 'SEÑADA', interval '2 hours');
    PERFORM pg_temp.pago(r, 7500, 'APROBADO', 'CUENTA_DNI', interval '2 hours', emp);

    -- Canceladas
    -- a tiempo: se le devolvió todo por Mercado Pago
    r := pg_temp.reserva(c, c, 3, 4, '15:00', '16:30', 'CANCELADA', interval '3 days');
    PERFORM pg_temp.pago(r, 7500, 'APROBADO', 'MERCADO_PAGO', interval '3 days', NULL, 'mp-prueba-' || r);
    UPDATE pagos SET estado = 'DEVUELTO', monto_reembolsado = 7500, reembolsado_at = now() - interval '1 hour'
    WHERE reserva_id = r;

    -- tarde: el club retuvo la seña
    r := pg_temp.reserva('martin@test.com', 'martin@test.com', 2, -2, '18:00', '19:30', 'CANCELADA', interval '6 days');
    PERFORM pg_temp.pago(r, 7500, 'APROBADO', 'MANUAL', interval '6 days', emp);

    -- pagada en efectivo y cancelada a tiempo: hay que devolverle la plata a mano
    r := pg_temp.reserva('lucia@test.com', 'empleado@test.com', 1, 6, '17:00', '18:30', 'CANCELADA', interval '5 days');
    PERFORM pg_temp.pago(r, 15000, 'A_REEMBOLSAR', 'MANUAL', interval '5 days', emp, NULL, 15000, interval '3 hours');

    -- Pasadas
    r := pg_temp.reserva(c, c, 1, -2, '18:00', '19:30', 'COMPLETADA', interval '7 days');
    PERFORM pg_temp.pago(r, 7500, 'APROBADO', 'MERCADO_PAGO', interval '7 days', NULL, 'mp-prueba-' || r);
    PERFORM pg_temp.pago(r, 7500, 'APROBADO', 'MANUAL', interval '2 days', emp);

    r := pg_temp.reserva('ana@test.com', 'ana@test.com', 2, -1, '16:00', '17:30', 'COMPLETADA', interval '3 days');
    PERFORM pg_temp.pago(r, 15000, 'APROBADO', 'MERCADO_PAGO', interval '1 day', emp);

    -- ya se jugó, quedó debiendo el saldo y no devolvió las 2 paletas: $15.000 + $10.000, seña $12.500
    r := pg_temp.reserva('martin@test.com', 'martin@test.com', 4, -3, '15:00', '16:30', 'SEÑADA', interval '5 days',
                         10000);
    INSERT INTO reserva_productos (reserva_id, producto_id, cantidad, precio_unitario)
    SELECT r, id, 2, precio FROM productos WHERE codigo = 'PALETAS';
    PERFORM pg_temp.pago(r, 12500, 'APROBADO', 'MANUAL', interval '4 days', emp);

    -- después de jugar el personal le sumó 2 aguas, que pagó en efectivo con el saldo: $15.000 + $3.000
    r := pg_temp.reserva(c, c, 3, -1, '12:00', '13:30', 'COMPLETADA', interval '3 days', 3000);
    INSERT INTO reserva_productos (reserva_id, producto_id, cantidad, precio_unitario)
    SELECT r, id, 2, precio FROM productos WHERE nombre = 'Agua mineral';
    UPDATE productos SET stock = stock - 2 WHERE nombre = 'Agua mineral';
    PERFORM pg_temp.pago(r, 7500, 'APROBADO', 'MERCADO_PAGO', interval '3 days', NULL, 'mp-prueba-' || r);
    PERFORM pg_temp.pago(r, 10500, 'APROBADO', 'MANUAL', interval '1 day', emp);

    r := pg_temp.reserva('diego@test.com', 'diego@test.com', 1, -6, '10:00', '12:00', 'COMPLETADA', interval '10 days');
    PERFORM pg_temp.pago(r, 20000, 'APROBADO', 'MERCADO_PAGO', interval '10 days', NULL, 'mp-prueba-' || r);

    -- no pagó a tiempo: el pago online quedó vencido
    r := pg_temp.reserva('lucia@test.com', 'lucia@test.com', 3, -1, '18:00', '19:30', 'EXPIRADA', interval '4 days');
    PERFORM pg_temp.pago(r, 7500, 'EXPIRADO', 'MERCADO_PAGO', interval '4 days');
END $$;

-- ── Ventas sueltas (sin reserva), cobradas en el momento ────────────────
CREATE FUNCTION pg_temp.venta(p_hace interval, p_medio text, p_cliente text, p_productos text[], p_cantidades int[])
RETURNS void LANGUAGE plpgsql AS $$
DECLARE v_id bigint;
BEGIN
    INSERT INTO ventas (created_at, total, medio, cliente_id, registrado_por_id)
    VALUES (now() - p_hace, 0, p_medio, pg_temp.usuario(p_cliente), pg_temp.usuario('empleado@test.com'))
    RETURNING id INTO v_id;
    INSERT INTO venta_items (venta_id, producto_id, cantidad, precio_unitario)
    SELECT v_id, p.id, x.cantidad, p.precio
    FROM unnest(p_productos, p_cantidades) AS x(nombre, cantidad) JOIN productos p ON p.nombre = x.nombre;
    UPDATE ventas SET total = (SELECT sum(cantidad * precio_unitario) FROM venta_items WHERE venta_id = v_id)
    WHERE id = v_id;
    -- el stock baja solo en los que lo controlan
    UPDATE productos p SET stock = p.stock - i.cantidad
    FROM venta_items i WHERE i.venta_id = v_id AND i.producto_id = p.id AND p.controla_stock;
END $$;

-- hoy (para la caja del EMPLEADO), ayer y la semana pasada
SELECT pg_temp.venta(interval '1 hour', 'EFECTIVO', NULL, ARRAY['Agua mineral', 'Gatorade'], ARRAY[2, 1]);
SELECT pg_temp.venta(interval '30 minutes', 'MERCADO_PAGO', 'ana@test.com', ARRAY['Pelotas'], ARRAY[3]);
SELECT pg_temp.venta(interval '1 day', 'CUENTA_DNI', current_setting('datos.mail_cliente'), ARRAY['Gatorade'], ARRAY[2]);
-- el ADMIN ya controló la de ayer en la cuenta; el pago con Cuenta DNI de hoy sigue sin verificar
UPDATE ventas SET verificado_at = now(), verificado_por_id = pg_temp.admin() WHERE medio = 'CUENTA_DNI';
SELECT pg_temp.venta(interval '6 days', 'MERCADO_PAGO', NULL, ARRAY['Agua mineral', 'Pelotas'], ARRAY[4, 1]);
-- una de hoy que el empleado cargó mal y anuló: no cuenta en la caja y su stock volvió
SELECT pg_temp.venta(interval '20 minutes', 'EFECTIVO', NULL, ARRAY['Gatorade'], ARRAY[3]);
UPDATE ventas v SET estado = 'ANULADA', anulada_at = now() - interval '15 minutes',
       anulada_por_id = pg_temp.usuario('empleado@test.com'), motivo_anulacion = 'Se cargó dos veces'
WHERE v.id = (SELECT max(id) FROM ventas);
UPDATE productos p SET stock = p.stock + i.cantidad
FROM venta_items i WHERE i.venta_id = (SELECT max(id) FROM ventas) AND i.producto_id = p.id AND p.controla_stock;
-- la de la semana pasada: hoy el cliente devolvió un agua vencida (no vuelve al stock; cuenta en la caja de hoy)
WITH v AS (SELECT id FROM ventas WHERE created_at < now() - interval '5 days' ORDER BY id LIMIT 1),
     i AS (UPDATE venta_items SET devueltas = 1
           WHERE venta_id = (SELECT id FROM v) AND producto_id = (SELECT id FROM productos WHERE nombre = 'Agua mineral')
           RETURNING id, precio_unitario),
     d AS (INSERT INTO venta_devoluciones (venta_id, created_at, registrada_por_id, motivo, monto)
           SELECT (SELECT id FROM v), now() - interval '10 minutes', pg_temp.admin(), 'Agua vencida', precio_unitario FROM i
           RETURNING id)
INSERT INTO venta_devolucion_items (devolucion_id, venta_item_id, cantidad, vuelve_al_stock)
SELECT d.id, i.id, 1, false FROM d, i;

-- ── Un día cerrado (sin reservas ese día) ───────────────────────────────
INSERT INTO dias_cerrados (configuracion_id, fecha, motivo, created_at, created_by_id)
VALUES (1, current_date + 40, 'Mantenimiento de las canchas', now(), pg_temp.admin());

DROP EXTENSION pgcrypto;

COMMIT;

-- ── Resumen ──────────────────────────────────────────────────────────────
\echo
\echo 'Usuarios por rol (las cuentas nuevas entran con Prueba123!; los ADMIN, con su contraseña de siempre):'
SELECT rol, count(*) AS cantidad, string_agg(email, ', ' ORDER BY id) AS cuentas FROM usuarios GROUP BY rol ORDER BY rol;
\echo 'Reservas por estado:'
SELECT estado, count(*) AS cantidad FROM reservas GROUP BY estado ORDER BY estado;
\echo 'Devoluciones de ventas:'
SELECT created_at::date AS dia, motivo, monto FROM venta_devoluciones ORDER BY id;
\echo 'Ventas sueltas:'
SELECT created_at::date AS dia, medio, estado, count(*) AS cantidad, sum(total) AS total FROM ventas GROUP BY 1, 2, 3 ORDER BY 1, 2, 3;
