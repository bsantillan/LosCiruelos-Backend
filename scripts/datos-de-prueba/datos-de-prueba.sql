-- =====================================================================
-- Datos de prueba para la base de DESARROLLO. Nunca en producción.
--
-- Borra todos los datos (el esquema y el historial de Flyway no se tocan) y carga un ecosistema
-- completo para probar el sistema a mano. Se conservan, con su contraseña, solo estas cuentas:
--   · los ADMIN (entre ellos el de ADMIN_EMAIL);
--   · el EMPLEADO empleado@test.com (si no existe, se crea con Prueba123!);
--   · el cliente principal (variable mail_cliente; si no existe, se crea con Prueba123!).
-- Todo lo demás se borra: reservas, pagos, partidos, turnos fijos, ventas, devoluciones, cierres,
-- productos, canchas, configuración, el resto de los clientes y los tokens.
--
-- Las fechas son relativas al día y la hora en que se corre. Cómo correrlo: ver README.md en esta
-- carpeta. Todas las cuentas nuevas entran con la contraseña Prueba123!.
--
-- Variables opcionales (psql -v): mail_cliente y tel_cliente, el mail y el celular del cliente
-- principal (el celular en formato de WhatsApp: 549 + característica + número, solo dígitos; se
-- usa solo si la cuenta se crea). Sin ellas usa cliente@test.com y un celular inexistente.
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

-- (las variables de psql no llegan adentro de un DO ni de una función: pasan por set_config)
SELECT set_config('datos.mail_cliente', :'mail_cliente', true), set_config('datos.tel_cliente', :'tel_cliente', true) \gset

-- ── Cuentas que se conservan ─────────────────────────────────────────────
CREATE TEMP TABLE cuentas_a_conservar ON COMMIT DROP AS
SELECT id FROM usuarios
WHERE rol = 'ADMIN' OR email IN ('empleado@test.com', current_setting('datos.mail_cliente'));

-- ── Borrar todos los datos ───────────────────────────────────────────────
-- configuracion arrastra dias y dias_cerrados; canchas, reservas y turnos fijos con sus hijas
TRUNCATE pagos, reserva_jugadores, reserva_productos, reservas, turno_fijo_jugadores, turno_fijo_productos,
         turnos_fijos, venta_devolucion_items, venta_devoluciones, venta_items, ventas, dias_cerrados, dias,
         configuracion, canchas, productos, refresh_tokens, verification_tokens, envios_codigo_telefono
         RESTART IDENTITY CASCADE;

DELETE FROM clientes WHERE usuario_id NOT IN (SELECT id FROM cuentas_a_conservar);
DELETE FROM empleados WHERE usuario_id NOT IN (SELECT id FROM cuentas_a_conservar);
DELETE FROM profesores WHERE usuario_id NOT IN (SELECT id FROM cuentas_a_conservar);
DELETE FROM admins WHERE usuario_id NOT IN (SELECT id FROM cuentas_a_conservar);
DELETE FROM usuarios WHERE id NOT IN (SELECT id FROM cuentas_a_conservar);

-- ── Configuración del club ──────────────────────────────────────────────
-- turno mínimo de 90 min a $15.000 y $5.000 cada media hora de más; seña del 50 %; devolución total
-- hasta 24 h antes y de la seña hasta 12 h antes; recordatorio 2 h antes; se reserva hasta 30 días
-- adelante; la seña online del personal se paga dentro de las 2 h
INSERT INTO configuracion (id, monto_reserva, monto_x_media_hora, porcentaje_senia, duracion_minima_turno,
                           duracion_maxima_turno, minutos_reembolso_total, minutos_reembolso_senia,
                           recordatorio_activo, minutos_recordatorio, dias_anticipacion_reserva,
                           minutos_pago_senia_online)
VALUES (1, 15000, 5000, 50, 90, 180, 1440, 720, true, 120, 30, 120);

INSERT INTO dias (dia, horario_inicio, horario_fin, configuracion_general_id) VALUES
    ('Lunes', '08:00', '23:00', 1), ('Martes', '08:00', '23:00', 1), ('Miércoles', '08:00', '23:00', 1),
    ('Jueves', '08:00', '23:00', 1), ('Viernes', '08:00', '23:00', 1), ('Sábado', '09:00', '22:00', 1),
    ('Domingo', '09:00', '20:00', 1);

INSERT INTO canchas (numero, descripcion) VALUES
    (1, 'Cancha de césped sintético con iluminación LED.'),
    (2, 'Cancha de césped sintético con iluminación LED.'),
    (3, 'Cancha panorámica de vidrio.'),
    (4, 'Cancha techada.');

-- Todos controlan stock. Las paletas son de alquiler: el stock es el total de paletas del club.
INSERT INTO productos (codigo, nombre, descripcion, tipo, precio, stock, activo, ofrecer_al_reservar, controla_stock) VALUES
    ('PELOTAS', 'Pelotas', 'Pelota de pádel, precio por unidad', 'VENTA', 5000, 40, true, true, true),
    ('PALETAS', 'Paletas', 'Alquiler de paleta durante el turno, precio por unidad', 'ALQUILER', 5000, 12, true, true, true),
    (NULL, 'Agua mineral', 'Botella de 500 ml', 'VENTA', 1500, 30, true, false, true),
    (NULL, 'Gatorade', 'Botella de 500 ml', 'VENTA', 3000, 9, true, false, true),         -- queda con poco stock
    (NULL, 'Overgrip', 'Cinta para el mango de la paleta', 'VENTA', 2500, 4, true, false, true), -- queda con poco stock
    (NULL, 'Barrita de cereal', NULL, 'VENTA', 1200, 0, true, false, true),               -- sin stock
    (NULL, 'Muñequera', 'Ya no se vende', 'VENTA', 2000, 6, false, false, true);           -- dado de baja

-- ── Ayudas (solo existen durante esta sesión) ────────────────────────────
CREATE FUNCTION pg_temp.usuario(p_email text) RETURNS bigint LANGUAGE sql AS
$$ SELECT id FROM usuarios WHERE email = p_email $$;

CREATE FUNCTION pg_temp.admin() RETURNS bigint LANGUAGE sql AS
$$ SELECT min(id) FROM usuarios WHERE rol = 'ADMIN' $$;

CREATE FUNCTION pg_temp.empleado() RETURNS bigint LANGUAGE sql AS
$$ SELECT pg_temp.usuario('empleado@test.com') $$;

CREATE FUNCTION pg_temp.producto(p_nombre text) RETURNS bigint LANGUAGE sql AS
$$ SELECT id FROM productos WHERE nombre = p_nombre $$;

-- Cuenta con su perfil de rol (como Usuario.asignarRol); avisos por mail salvo que se pida WhatsApp
CREATE FUNCTION pg_temp.cuenta(p_email text, p_nombre text, p_apellido text, p_rol text, p_telefono text,
        p_tel_verificado boolean, p_canal text DEFAULT 'EMAIL')
RETURNS bigint LANGUAGE plpgsql AS $$
DECLARE v_id bigint;
BEGIN
    INSERT INTO usuarios (nombre, apellido, email, telefono, telefono_verificado, password_hash, terms_accepted,
                          terms_accepted_at, notificaciones_aceptadas_at, email_verificado, enabled, rol, provider,
                          canal_notificaciones, acepta_whatsapp_at)
    VALUES (p_nombre, p_apellido, p_email, p_telefono, p_tel_verificado, crypt('Prueba123!', gen_salt('bf', 10)),
            true, now() - interval '60 days', now() - interval '60 days', true, true, p_rol, 'LOCAL', p_canal,
            CASE WHEN p_canal = 'WHATSAPP' THEN now() - interval '50 days' END)
    RETURNING id INTO v_id;
    CASE p_rol
        WHEN 'EMPLEADO' THEN INSERT INTO empleados (usuario_id) VALUES (v_id);
        WHEN 'ADMIN' THEN INSERT INTO admins (usuario_id) VALUES (v_id);
        ELSE NULL; -- el perfil del cliente lo carga pg_temp.cliente
    END CASE;
    RETURN v_id;
END $$;

CREATE FUNCTION pg_temp.cliente(p_email text, p_nombre text, p_apellido text, p_telefono text,
        p_tel_verificado boolean, p_categoria text, p_posicion text, p_canal text DEFAULT 'EMAIL')
RETURNS bigint LANGUAGE plpgsql AS $$
DECLARE v_id bigint;
BEGIN
    v_id := pg_temp.cuenta(p_email, p_nombre, p_apellido, 'CLIENTE', p_telefono, p_tel_verificado, p_canal);
    INSERT INTO clientes (usuario_id, categoria, posicion, categoria_actualizada_at, recibe_recordatorios,
                          recibe_promociones)
    VALUES (v_id, p_categoria, p_posicion, CASE WHEN p_categoria IS NOT NULL THEN now() - interval '60 days' END,
            true, p_canal = 'WHATSAPP');
    RETURN v_id;
END $$;

-- Monto de la cancha según la configuración (como Configuracion.montoTurno)
CREATE FUNCTION pg_temp.monto_turno(p_inicio time, p_fin time) RETURNS numeric LANGUAGE sql AS $$
    SELECT monto_reserva + monto_x_media_hora
           * (greatest(extract(epoch FROM p_fin - p_inicio)::int / 60 - duracion_minima_turno, 0) / 30)
    FROM configuracion WHERE id = 1
$$;

-- Reserva NORMAL. p_extra suma los productos. Las que carga el personal llevan modalidad de pago
-- (EN_EL_CLUB salvo que se diga otra); las del cliente, ninguna. Una PENDIENTE vence a los 15 min de
-- creada, o con la seña online a las 2 h (nunca después del inicio del turno).
CREATE FUNCTION pg_temp.reserva(p_cliente text, p_creada_por text, p_cancha int, p_dias int, p_inicio time,
        p_fin time, p_estado text, p_creada_hace interval DEFAULT interval '2 days',
        p_extra numeric DEFAULT 0, p_modalidad text DEFAULT NULL)
RETURNS bigint LANGUAGE plpgsql AS $$
DECLARE v_id bigint; v_creada timestamp := now() - p_creada_hace; v_modalidad text; v_vence timestamp;
BEGIN
    v_modalidad := CASE WHEN p_creada_por = p_cliente THEN NULL ELSE coalesce(p_modalidad, 'EN_EL_CLUB') END;
    v_vence := CASE WHEN v_modalidad = 'SENIA_ONLINE'
                        THEN least(v_creada + interval '120 minutes', (current_date + p_dias) + p_inicio)
                    WHEN v_modalidad = 'SIN_SENIA' THEN NULL
                    ELSE v_creada + interval '15 minutes' END;
    INSERT INTO reservas (cancha_id, cliente_id, created_by_id, fecha_reserva, hora_inicio, hora_fin, monto_total,
                          estado, created_at, expires_at, tipo, modalidad_pago)
    VALUES ((SELECT id FROM canchas WHERE numero = p_cancha), pg_temp.usuario(p_cliente),
            pg_temp.usuario(p_creada_por), current_date + p_dias, p_inicio, p_fin,
            pg_temp.monto_turno(p_inicio, p_fin) + p_extra, p_estado, v_creada,
            CASE WHEN p_estado IN ('PENDIENTE', 'EXPIRADA') THEN v_vence END, 'NORMAL', v_modalidad)
    RETURNING id INTO v_id;
    RETURN v_id;
END $$;

-- Partido del ADMIN con 4 jugadores: un mail es un cliente; 'invitado:Nombre' es un invitado sin
-- cuenta. El responsable (cliente de la reserva) es el primer jugador con cuenta.
CREATE FUNCTION pg_temp.partido(p_cancha int, p_dias int, p_inicio time, p_fin time, p_estado text,
        p_jugadores text[], p_turno_fijo bigint DEFAULT NULL, p_creado_hace interval DEFAULT interval '5 days')
RETURNS bigint LANGUAGE plpgsql AS $$
DECLARE v_id bigint; v_responsable bigint; i int;
BEGIN
    SELECT pg_temp.usuario(j) INTO v_responsable FROM unnest(p_jugadores) j WHERE j NOT LIKE 'invitado:%' LIMIT 1;
    INSERT INTO reservas (cancha_id, cliente_id, created_by_id, fecha_reserva, hora_inicio, hora_fin, monto_total,
                          estado, created_at, tipo, turno_fijo_id)
    VALUES ((SELECT id FROM canchas WHERE numero = p_cancha), v_responsable, pg_temp.admin(), current_date + p_dias,
            p_inicio, p_fin, pg_temp.monto_turno(p_inicio, p_fin), p_estado, now() - p_creado_hace, 'PARTIDO',
            p_turno_fijo)
    RETURNING id INTO v_id;
    FOR i IN 1 .. array_length(p_jugadores, 1) LOOP
        IF p_jugadores[i] LIKE 'invitado:%' THEN
            INSERT INTO reserva_jugadores (reserva_id, posicion, nombre, telefono)
            VALUES (v_id, i, substr(p_jugadores[i], 10), '54911000009' || lpad(i::text, 2, '0'));
        ELSE
            INSERT INTO reserva_jugadores (reserva_id, posicion, usuario_id) VALUES (v_id, i, pg_temp.usuario(p_jugadores[i]));
        END IF;
    END LOOP;
    RETURN v_id;
END $$;

-- Pago de una reserva. Medios:
--   MANUAL (efectivo en el club), ONLINE (Mercado Pago con el Payment Brick: id de pago simulado,
--   nunca se llamó a MP), QR (Mercado Pago con el QR de la caja: order simulada), TRANSFERENCIA_MP
--   y CUENTA_DNI (el ADMIN los verifica después; p_verificado), BONIFICACION (del ADMIN, con motivo).
-- p_devuelto es cuánto se devolvió (o hay que devolver) de ese pago.
CREATE FUNCTION pg_temp.pago(p_reserva bigint, p_monto numeric, p_medio text, p_pagado_hace interval,
        p_estado text DEFAULT 'APROBADO', p_verificado boolean DEFAULT false, p_motivo text DEFAULT NULL,
        p_devuelto numeric DEFAULT NULL, p_devuelto_hace interval DEFAULT NULL)
RETURNS void LANGUAGE plpgsql AS $$
DECLARE v_provider text; v_registra bigint; v_cuando timestamp := now() - p_pagado_hace; v_n int;
BEGIN
    v_provider := CASE p_medio WHEN 'ONLINE' THEN 'MERCADO_PAGO' WHEN 'QR' THEN 'MERCADO_PAGO' ELSE p_medio END;
    v_registra := CASE WHEN p_medio = 'ONLINE' THEN NULL
                       WHEN p_medio = 'BONIFICACION' THEN pg_temp.admin()
                       ELSE pg_temp.empleado() END;
    SELECT count(*) + 1 INTO v_n FROM pagos WHERE reserva_id = p_reserva;
    INSERT INTO pagos (reserva_id, monto, estado, provider, created_at, paid_at, external_payment_id, preference_id,
                       order_id, vence_at, monto_reembolsado, reembolsado_at, clave_idempotencia, registrado_por_id,
                       motivo, verificado_at, verificado_por_id)
    VALUES (p_reserva, p_monto, p_estado, v_provider, v_cuando,
            CASE WHEN p_estado <> 'EXPIRADO' THEN v_cuando END,
            CASE WHEN p_medio IN ('ONLINE', 'QR') AND p_estado <> 'EXPIRADO'
                 THEN 'mp-prueba-' || p_reserva || '-' || v_n END,
            CASE WHEN p_medio = 'ONLINE' THEN 'pref-prueba-' || p_reserva || '-' || v_n END,
            CASE WHEN p_medio = 'QR' THEN 'ORD-PRUEBA-' || p_reserva || '-' || v_n END,
            CASE WHEN p_medio = 'QR' THEN v_cuando + interval '10 minutes' END,
            p_devuelto, CASE WHEN p_estado IN ('DEVUELTO', 'A_REEMBOLSAR') THEN now() - p_devuelto_hace END, gen_random_uuid(),
            v_registra, p_motivo,
            CASE WHEN p_verificado THEN v_cuando + interval '1 day' END,
            CASE WHEN p_verificado THEN pg_temp.admin() END);
END $$;

-- Productos de una reserva (con el precio de hoy). Lo de VENTA baja el stock; las paletas (ALQUILER)
-- no: el stock es el total y se cuenta lo que está en uso.
CREATE FUNCTION pg_temp.consumo(p_reserva bigint, p_producto text, p_cantidad int, p_devueltas int DEFAULT NULL,
        p_devueltas_at timestamp DEFAULT NULL)
RETURNS void LANGUAGE plpgsql AS $$
BEGIN
    INSERT INTO reserva_productos (reserva_id, producto_id, cantidad, precio_unitario, cantidad_devuelta, devuelta_at)
    SELECT p_reserva, id, p_cantidad, precio, p_devueltas, p_devueltas_at FROM productos WHERE nombre = p_producto;
    UPDATE productos SET stock = stock - p_cantidad WHERE nombre = p_producto AND tipo = 'VENTA';
END $$;

-- ── Cuentas ──────────────────────────────────────────────────────────────
DO $$
BEGIN
    IF pg_temp.usuario('empleado@test.com') IS NULL THEN
        PERFORM pg_temp.cuenta('empleado@test.com', 'Carla', 'Méndez', 'EMPLEADO', '5491100000101', true);
    END IF;
    IF pg_temp.usuario(current_setting('datos.mail_cliente')) IS NULL THEN
        PERFORM pg_temp.cliente(current_setting('datos.mail_cliente'), 'Cliente', 'Principal',
                                current_setting('datos.tel_cliente'), true, 'Cuarta', 'Drive');
    END IF;

    -- clientes con el perfil completo
    PERFORM pg_temp.cliente('ana@test.com', 'Ana', 'Gómez', '5491100000002', true, 'Quinta', 'Reves');
    PERFORM pg_temp.cliente('martin@test.com', 'Martín', 'López', '5491100000003', true, 'Tercera', 'Ambos');
    PERFORM pg_temp.cliente('lucia@test.com', 'Lucía', 'Fernández', '5491100000004', true, 'Sexta', 'Drive');
    PERFORM pg_temp.cliente('juan@test.com', 'Juan', 'Pérez', '5491100000005', true, 'Cuarta', 'Reves');
    PERFORM pg_temp.cliente('camila@test.com', 'Camila', 'Rodríguez', '5491100000006', true, 'Quinta', 'Drive');
    PERFORM pg_temp.cliente('diego@test.com', 'Diego', 'Torres', '5491100000007', true, 'Segunda', 'Drive');
    PERFORM pg_temp.cliente('valentina@test.com', 'Valentina', 'Díaz', '5491100000008', true, 'Septima', 'Reves');
    PERFORM pg_temp.cliente('nicolas@test.com', 'Nicolás', 'Romero', '5491100000009', true, 'Tercera', 'Reves');
    PERFORM pg_temp.cliente('florencia@test.com', 'Florencia', 'Álvarez', '5491100000010', true, 'Sexta', 'Ambos');
    PERFORM pg_temp.cliente('lucas@test.com', 'Lucas', 'Benítez', '5491100000011', true, 'Principiante', 'Drive');
    PERFORM pg_temp.cliente('micaela@test.com', 'Micaela', 'Suárez', '5491100000012', true, 'Cuarta', 'Ambos');
    PERFORM pg_temp.cliente('federico@test.com', 'Federico', 'Molina', '5491100000013', true, 'Primera', 'Drive');
    -- perfil incompleto: sin categoría ni posición (el personal le puede reservar igual)
    PERFORM pg_temp.cliente('sofia@test.com', 'Sofía', 'Ruiz', '5491100000014', true, NULL, NULL);
    -- perfil incompleto: el celular sin verificar (los avisos le llegan por mail)
    PERFORM pg_temp.cliente('tomas@test.com', 'Tomás', 'Acosta', '5491100000015', false, 'Quinta', 'Reves');
END $$;

-- Avisos que elige cada ADMIN (ver AdminProfile)
UPDATE admins SET activos = true, reserva_confirmada = true, saldo_cobrado = false, reserva_cancelada = true,
                  reembolso_manual = true;

-- ── Turnos fijos ─────────────────────────────────────────────────────────
CREATE FUNCTION pg_temp.dia_semana(p_fecha date) RETURNS text LANGUAGE sql AS
$$ SELECT (ARRAY['MONDAY','TUESDAY','WEDNESDAY','THURSDAY','FRIDAY','SATURDAY','SUNDAY'])[extract(isodow FROM p_fecha)::int] $$;

-- Activo: cancha 1 de 18:00 a 19:30 el mismo día de la semana que dentro de 3 días, con sus partidos jugados de las últimas 2 semanas y los que vienen hasta el tope de 30 días.
-- Dado de baja: cancha 4 de 9:00 a 10:30, con un partido jugado y la última fecha cancelada.
INSERT INTO turnos_fijos (cancha_id, dia_semana, hora_inicio, hora_fin, desde, activo, created_by_id, created_at,
                          dado_de_baja_at)
VALUES ((SELECT id FROM canchas WHERE numero = 1), pg_temp.dia_semana(current_date + 3), '18:00', '19:30',
        current_date - 11, true, pg_temp.admin(), now() - interval '12 days', NULL),
       ((SELECT id FROM canchas WHERE numero = 4), pg_temp.dia_semana(current_date + 5), '09:00', '10:30',
        current_date - 16, false, pg_temp.admin(), now() - interval '17 days', now() - interval '10 days');

INSERT INTO turno_fijo_jugadores (turno_fijo_id, posicion, usuario_id, nombre, telefono) VALUES
    (1, 1, pg_temp.usuario('juan@test.com'), NULL, NULL),
    (1, 2, pg_temp.usuario('nicolas@test.com'), NULL, NULL),
    (1, 3, pg_temp.usuario('federico@test.com'), NULL, NULL),
    (1, 4, NULL, 'Gustavo (invitado)', '5491100000901'),
    (2, 1, pg_temp.usuario('camila@test.com'), NULL, NULL),
    (2, 2, pg_temp.usuario('florencia@test.com'), NULL, NULL),
    (2, 3, pg_temp.usuario('micaela@test.com'), NULL, NULL),
    (2, 4, pg_temp.usuario('valentina@test.com'), NULL, NULL);
-- el turno fijo activo lleva 1 pelota por partido
INSERT INTO turno_fijo_productos (turno_fijo_id, producto_id, cantidad) VALUES (1, pg_temp.producto('Pelotas'), 1);

-- ── Reservas ─────────────────────────────────────────────────────────────
-- Montos con la configuración de arriba: 90 min = $15.000 (seña $7.500), 120 min = $20.000.
-- Horarios entre las 9 y las 20, que valen cualquier día de la semana. Cuatro canchas:
-- ninguna reserva activa se superpone con otra en la misma cancha.
DO $$
DECLARE
    c text := current_setting('datos.mail_cliente');
    tf_jugadores text[] := ARRAY['juan@test.com', 'nicolas@test.com', 'federico@test.com', 'invitado:Gustavo (invitado)'];
    r bigint;
    d int;
BEGIN
    -- ── Pasadas ──
    -- seña online y el saldo en efectivo al llegar
    r := pg_temp.reserva(c, c, 1, -1, '18:00', '19:30', 'COMPLETADA', interval '4 days');
    PERFORM pg_temp.pago(r, 7500, 'ONLINE', interval '4 days');
    PERFORM pg_temp.pago(r, 7500, 'MANUAL', interval '1 day');

    -- pagada entera con el QR de la caja
    r := pg_temp.reserva('ana@test.com', 'ana@test.com', 2, -1, '15:00', '16:30', 'COMPLETADA', interval '3 days');
    PERFORM pg_temp.pago(r, 15000, 'QR', interval '1 day');

    -- después de jugar se le sumaron 2 aguas; el saldo lo pagó por transferencia (verificada): $15.000 + $3.000
    r := pg_temp.reserva(c, c, 3, -1, '12:00', '13:30', 'COMPLETADA', interval '3 days', 3000);
    PERFORM pg_temp.consumo(r, 'Agua mineral', 2);
    PERFORM pg_temp.pago(r, 7500, 'ONLINE', interval '3 days');
    PERFORM pg_temp.pago(r, 10500, 'TRANSFERENCIA_MP', interval '1 day', 'APROBADO', true);

    -- bonificada a medias por el ADMIN y el resto con Cuenta DNI (verificada)
    r := pg_temp.reserva('lucia@test.com', 'lucia@test.com', 1, -2, '16:30', '18:00', 'COMPLETADA', interval '5 days');
    PERFORM pg_temp.pago(r, 7500, 'BONIFICACION', interval '5 days', 'APROBADO', false, 'Socia del club');
    PERFORM pg_temp.pago(r, 7500, 'CUENTA_DNI', interval '2 days', 'APROBADO', true);

    -- cancelada tarde: el club retuvo la seña (sin reembolso)
    r := pg_temp.reserva('martin@test.com', 'martin@test.com', 2, -2, '18:00', '19:30', 'CANCELADA', interval '6 days');
    PERFORM pg_temp.pago(r, 7500, 'MANUAL', interval '6 days');

    -- jugada con deuda: pagó la seña y no devolvió las 2 paletas alquiladas: $15.000 + $10.000, seña $12.500
    r := pg_temp.reserva('martin@test.com', 'empleado@test.com', 4, -3, '15:00', '16:30', 'SEÑADA', interval '5 days',
                         10000);
    PERFORM pg_temp.consumo(r, 'Paletas', 2);
    PERFORM pg_temp.pago(r, 12500, 'MANUAL', interval '4 days');

    -- pagada con 2 paletas que devolvió; la transferencia del saldo todavía no se verificó
    r := pg_temp.reserva('juan@test.com', 'juan@test.com', 3, -3, '18:00', '19:30', 'COMPLETADA', interval '6 days',
                         10000);
    PERFORM pg_temp.consumo(r, 'Paletas', 2, 2, (current_date - 3) + time '19:35');
    PERFORM pg_temp.pago(r, 12500, 'ONLINE', interval '6 days');
    PERFORM pg_temp.pago(r, 12500, 'TRANSFERENCIA_MP', interval '3 days');

    -- jugada con deuda: pagó en efectivo menos que la seña
    r := pg_temp.reserva('lucas@test.com', 'empleado@test.com', 2, -6, '18:00', '19:30', 'PARCIALMENTE_PAGADA',
                         interval '8 days');
    PERFORM pg_temp.pago(r, 5000, 'MANUAL', interval '6 days');

    -- vencidas: el cliente no pagó la seña online a tiempo; a otra cargada por el personal no se le cobró
    r := pg_temp.reserva('valentina@test.com', 'valentina@test.com', 2, -5, '09:00', '10:30', 'EXPIRADA', interval '7 days');
    PERFORM pg_temp.pago(r, 7500, 'ONLINE', interval '7 days', 'EXPIRADO');
    r := pg_temp.reserva('camila@test.com', 'empleado@test.com', 3, -5, '10:30', '12:00', 'EXPIRADA', interval '6 days');

    -- 2 h, pagada online
    r := pg_temp.reserva('diego@test.com', 'diego@test.com', 1, -6, '10:00', '12:00', 'COMPLETADA', interval '10 days');
    PERFORM pg_temp.pago(r, 20000, 'ONLINE', interval '10 days');

    -- cancelada a tiempo: se le devolvió todo por Mercado Pago
    r := pg_temp.reserva(c, c, 1, -8, '12:00', '13:30', 'CANCELADA', interval '12 days');
    PERFORM pg_temp.pago(r, 7500, 'ONLINE', interval '12 days', 'DEVUELTO', false, NULL, 7500, interval '10 days');

    -- Cuenta DNI que el ADMIN todavía no verificó
    r := pg_temp.reserva('micaela@test.com', 'empleado@test.com', 2, -10, '15:00', '16:30', 'COMPLETADA', interval '12 days');
    PERFORM pg_temp.pago(r, 15000, 'CUENTA_DNI', interval '10 days');

    -- partido suelto con dos invitados: cada jugador pagó su parte
    r := pg_temp.partido(4, -7, '16:30', '18:00', 'COMPLETADA',
                         ARRAY['federico@test.com', 'invitado:Hernán', 'diego@test.com', 'invitado:Pablo'],
                         NULL, interval '9 days');
    PERFORM pg_temp.pago(r, 3750, 'MANUAL', interval '7 days');
    PERFORM pg_temp.pago(r, 3750, 'MANUAL', interval '7 days');
    PERFORM pg_temp.pago(r, 3750, 'TRANSFERENCIA_MP', interval '7 days', 'APROBADO', true);
    PERFORM pg_temp.pago(r, 3750, 'QR', interval '7 days');

    -- partidos jugados del turno fijo activo (con su pelota): $15.000 + $5.000
    FOREACH d IN ARRAY ARRAY[-11, -4] LOOP
        r := pg_temp.partido(1, d, '18:00', '19:30', 'COMPLETADA', tf_jugadores, 1, interval '12 days');
        PERFORM pg_temp.consumo(r, 'Pelotas', 1);
        UPDATE reservas SET monto_total = monto_total + 5000 WHERE id = r;
        PERFORM pg_temp.pago(r, 5000, 'MANUAL', make_interval(days => -d));
        PERFORM pg_temp.pago(r, 5000, 'MANUAL', make_interval(days => -d));
        PERFORM pg_temp.pago(r, 5000, 'CUENTA_DNI', make_interval(days => -d), 'APROBADO', true);
        PERFORM pg_temp.pago(r, 5000, 'MANUAL', make_interval(days => -d));
    END LOOP;

    -- turno fijo dado de baja: un partido jugado y la fecha siguiente cancelada
    r := pg_temp.partido(4, -16, '09:00', '10:30', 'COMPLETADA',
                         ARRAY['camila@test.com', 'florencia@test.com', 'micaela@test.com', 'valentina@test.com'], 2,
                         interval '17 days');
    PERFORM pg_temp.pago(r, 15000, 'MANUAL', interval '16 days');
    r := pg_temp.partido(4, -9, '09:00', '10:30', 'CANCELADA',
                         ARRAY['camila@test.com', 'florencia@test.com', 'micaela@test.com', 'valentina@test.com'], 2,
                         interval '17 days');

    -- ── Futuras ──
    -- señada online por el cliente principal
    r := pg_temp.reserva(c, c, 1, 1, '18:00', '19:30', 'SEÑADA', interval '1 day');
    PERFORM pg_temp.pago(r, 7500, 'ONLINE', interval '1 day');

    -- una de cada modalidad de pago del personal
    -- EN_EL_CLUB: pendiente, vence a los 15 min si no se cobra en el club
    r := pg_temp.reserva('ana@test.com', 'empleado@test.com', 2, 1, '18:00', '19:30', 'PENDIENTE', interval '2 minutes',
                         0, 'EN_EL_CLUB');
    -- SENIA_ONLINE: pendiente, el cliente tiene 2 h para pagar la seña con el link
    r := pg_temp.reserva('martin@test.com', 'empleado@test.com', 3, 1, '16:30', '18:00', 'PENDIENTE', interval '5 minutes',
                         0, 'SENIA_ONLINE');
    -- SIN_SENIA: confirmada por el ADMIN sin cobrar nada
    r := pg_temp.reserva('lucia@test.com', 'empleado@test.com', 4, 1, '18:00', '19:30', 'CONFIRMADA', interval '1 day',
                         0, 'SIN_SENIA');
    UPDATE reservas SET created_by_id = pg_temp.admin() WHERE id = r;

    -- pagada entera en efectivo
    r := pg_temp.reserva('ana@test.com', 'empleado@test.com', 1, 2, '15:00', '16:30', 'PAGADA', interval '2 days');
    PERFORM pg_temp.pago(r, 15000, 'MANUAL', interval '2 days');

    -- señada por transferencia sin verificar
    r := pg_temp.reserva('camila@test.com', 'empleado@test.com', 2, 2, '18:00', '19:30', 'SEÑADA', interval '1 day');
    PERFORM pg_temp.pago(r, 7500, 'TRANSFERENCIA_MP', interval '1 day');

    -- partido suelto con un invitado: pagaron 2 de los 4
    r := pg_temp.partido(2, 3, '18:00', '19:30', 'PARCIALMENTE_PAGADA',
                         ARRAY['nicolas@test.com', 'micaela@test.com', 'invitado:Rodrigo', 'lucas@test.com'],
                         NULL, interval '2 days');
    PERFORM pg_temp.pago(r, 3750, 'MANUAL', interval '1 day');
    PERFORM pg_temp.pago(r, 3750, 'QR', interval '1 day');

    -- los partidos que vienen del turno fijo activo (todavía sin pagar)
    FOREACH d IN ARRAY ARRAY[3, 10, 17, 24] LOOP
        r := pg_temp.partido(1, d, '18:00', '19:30', 'CONFIRMADA', tf_jugadores, 1, interval '12 days');
        PERFORM pg_temp.consumo(r, 'Pelotas', 1);
        UPDATE reservas SET monto_total = monto_total + 5000 WHERE id = r;
    END LOOP;

    -- 2 h con 2 paletas y 1 pelota, señada online: $20.000 + $15.000, seña $17.500
    r := pg_temp.reserva('martin@test.com', 'martin@test.com', 3, 4, '17:00', '19:00', 'SEÑADA', interval '3 days', 15000);
    PERFORM pg_temp.consumo(r, 'Paletas', 2);
    PERFORM pg_temp.consumo(r, 'Pelotas', 1);
    PERFORM pg_temp.pago(r, 17500, 'ONLINE', interval '3 days');

    -- el personal le cobró una parte, menos que la seña
    r := pg_temp.reserva('lucas@test.com', 'empleado@test.com', 4, 5, '10:30', '12:00', 'PARCIALMENTE_PAGADA', interval '2 days');
    PERFORM pg_temp.pago(r, 3000, 'MANUAL', interval '2 days');

    -- pagada en efectivo y cancelada a tiempo: hay que devolverle la plata a mano
    r := pg_temp.reserva('florencia@test.com', 'empleado@test.com', 1, 6, '16:30', '18:00', 'CANCELADA', interval '5 days');
    PERFORM pg_temp.pago(r, 15000, 'MANUAL', interval '5 days', 'A_REEMBOLSAR', false, NULL, 15000, interval '3 hours');

    -- bonificada entera por el ADMIN
    r := pg_temp.reserva('federico@test.com', 'empleado@test.com', 2, 7, '16:30', '18:00', 'PAGADA', interval '1 day');
    UPDATE reservas SET created_by_id = pg_temp.admin() WHERE id = r;
    PERFORM pg_temp.pago(r, 15000, 'BONIFICACION', interval '1 day', 'APROBADO', false, 'Ganador del torneo');

    -- señada con Cuenta DNI (verificada) y con el QR de la caja
    r := pg_temp.reserva('valentina@test.com', 'empleado@test.com', 3, 8, '18:00', '19:30', 'SEÑADA', interval '3 days');
    PERFORM pg_temp.pago(r, 7500, 'CUENTA_DNI', interval '3 days', 'APROBADO', true);
    r := pg_temp.reserva('juan@test.com', 'empleado@test.com', 4, 9, '15:00', '16:30', 'SEÑADA', interval '2 days');
    PERFORM pg_temp.pago(r, 7500, 'QR', interval '2 days');

    -- a la clienta sin categoría le reservó el personal (no hace falta el perfil completo)
    r := pg_temp.reserva('sofia@test.com', 'empleado@test.com', 1, 12, '10:30', '12:00', 'SEÑADA', interval '1 day');
    PERFORM pg_temp.pago(r, 7500, 'MANUAL', interval '1 day');
    r := pg_temp.reserva('nicolas@test.com', 'nicolas@test.com', 2, 12, '18:00', '19:30', 'SEÑADA', interval '1 hour');
    PERFORM pg_temp.pago(r, 7500, 'ONLINE', interval '1 hour');
END $$;

-- ── Reservas de hoy (dependen de la hora en que se corre) ───────────────
-- En curso (empezó hace media hora, con saldo), por jugar pagada y por jugar señada con Cuenta DNI
-- sin verificar. Solo se cargan las que entran en el horario del club de hoy.
DO $$
DECLARE
    v_abre time; v_cierra time;
    v_media timestamp := date_trunc('hour', now()) + CASE WHEN extract(minute FROM now()) >= 30 THEN interval '30 minutes'
                                                         ELSE interval '0' END;
    v_inicio time; r bigint;
BEGIN
    SELECT horario_inicio, horario_fin INTO v_abre, v_cierra FROM dias
    WHERE dia = (ARRAY['Lunes','Martes','Miércoles','Jueves','Viernes','Sábado','Domingo'])[extract(isodow FROM current_date)::int];

    -- en curso: empezó en la media hora anterior y termina dentro de una hora
    v_inicio := (v_media - interval '30 minutes')::time;
    IF (v_media - interval '30 minutes')::date = current_date AND v_inicio >= v_abre
       AND v_inicio + interval '90 minutes' <= v_cierra AND v_inicio < '22:30' THEN
        r := pg_temp.reserva('nicolas@test.com', 'nicolas@test.com', 2, 0, v_inicio, v_inicio + interval '90 minutes',
                             'SEÑADA', interval '2 days');
        PERFORM pg_temp.pago(r, 7500, 'ONLINE', interval '2 days');
    ELSE
        RAISE NOTICE 'Hoy no hay reserva en curso: el club está cerrado a esta hora.';
    END IF;

    -- por jugar: empiezan dentro de una hora
    v_inicio := (v_media + interval '60 minutes')::time;
    IF (v_media + interval '60 minutes')::date = current_date AND v_inicio >= v_abre
       AND v_inicio + interval '90 minutes' <= v_cierra AND v_inicio < '22:30' THEN
        r := pg_temp.reserva('camila@test.com', 'empleado@test.com', 3, 0, v_inicio, v_inicio + interval '90 minutes',
                             'PAGADA', interval '1 day');
        PERFORM pg_temp.pago(r, 15000, 'QR', interval '1 day');
        r := pg_temp.reserva('diego@test.com', 'empleado@test.com', 4, 0, v_inicio, v_inicio + interval '90 minutes',
                             'SEÑADA', interval '3 hours');
        PERFORM pg_temp.pago(r, 7500, 'CUENTA_DNI', interval '3 hours');
    ELSE
        RAISE NOTICE 'Hoy no hay reservas por jugar: el club cierra antes.';
    END IF;
END $$;

-- ── Ventas sueltas (sin reserva), cobradas en el momento ────────────────
-- Medios: EFECTIVO, MERCADO_PAGO (con el QR de la caja: order simulada), TRANSFERENCIA_MP y
-- CUENTA_DNI (estos dos los verifica el ADMIN).
CREATE FUNCTION pg_temp.venta(p_hace interval, p_medio text, p_cliente text, p_productos text[], p_cantidades int[],
        p_verificada boolean DEFAULT false)
RETURNS bigint LANGUAGE plpgsql AS $$
DECLARE v_id bigint; v_cuando timestamp := now() - p_hace;
BEGIN
    INSERT INTO ventas (created_at, total, medio, cliente_id, registrado_por_id, estado, order_id, vence_at,
                        clave_idempotencia, verificado_at, verificado_por_id)
    VALUES (v_cuando, 0, p_medio, pg_temp.usuario(p_cliente), pg_temp.empleado(), 'COBRADA',
            CASE WHEN p_medio = 'MERCADO_PAGO' THEN 'ORD-PRUEBA-VENTA-' || md5(random()::text) END,
            CASE WHEN p_medio = 'MERCADO_PAGO' THEN v_cuando + interval '10 minutes' END,
            gen_random_uuid(),
            CASE WHEN p_verificada THEN v_cuando + interval '1 hour' END,
            CASE WHEN p_verificada THEN pg_temp.admin() END)
    RETURNING id INTO v_id;
    INSERT INTO venta_items (venta_id, producto_id, cantidad, precio_unitario)
    SELECT v_id, p.id, x.cantidad, p.precio
    FROM unnest(p_productos, p_cantidades) AS x(nombre, cantidad) JOIN productos p ON p.nombre = x.nombre;
    UPDATE ventas SET total = (SELECT sum(cantidad * precio_unitario) FROM venta_items WHERE venta_id = v_id)
    WHERE id = v_id;
    UPDATE productos p SET stock = p.stock - i.cantidad FROM venta_items i WHERE i.venta_id = v_id AND i.producto_id = p.id;
    RETURN v_id;
END $$;

DO $$
DECLARE v bigint;
BEGIN
    -- hoy (para la caja del EMPLEADO)
    PERFORM pg_temp.venta(interval '1 hour', 'EFECTIVO', NULL, ARRAY['Agua mineral', 'Gatorade'], ARRAY[2, 1]);
    PERFORM pg_temp.venta(interval '40 minutes', 'MERCADO_PAGO', 'ana@test.com', ARRAY['Pelotas'], ARRAY[3]);
    PERFORM pg_temp.venta(interval '25 minutes', 'TRANSFERENCIA_MP', 'juan@test.com', ARRAY['Overgrip', 'Gatorade'], ARRAY[1, 2]);
    PERFORM pg_temp.venta(interval '10 minutes', 'CUENTA_DNI', NULL, ARRAY['Agua mineral'], ARRAY[3]);
    -- una de hoy que el empleado cargó mal y anuló: no cuenta en la caja y su stock volvió
    v := pg_temp.venta(interval '20 minutes', 'EFECTIVO', NULL, ARRAY['Gatorade'], ARRAY[3]);
    UPDATE ventas SET estado = 'ANULADA', anulada_at = now() - interval '15 minutes', anulada_por_id = pg_temp.empleado(),
                      motivo_anulacion = 'Se cargó dos veces'
    WHERE id = v;
    UPDATE productos p SET stock = p.stock + i.cantidad FROM venta_items i WHERE i.venta_id = v AND i.producto_id = p.id;

    -- días anteriores
    PERFORM pg_temp.venta(interval '1 day 2 hours', 'CUENTA_DNI', current_setting('datos.mail_cliente'),
                          ARRAY['Gatorade'], ARRAY[2], true);
    PERFORM pg_temp.venta(interval '1 day 5 hours', 'TRANSFERENCIA_MP', 'martin@test.com', ARRAY['Pelotas'], ARRAY[6], true);
    PERFORM pg_temp.venta(interval '2 days 3 hours', 'EFECTIVO', 'lucia@test.com', ARRAY['Agua mineral', 'Overgrip'], ARRAY[2, 1]);
    PERFORM pg_temp.venta(interval '2 days 6 hours', 'CUENTA_DNI', NULL, ARRAY['Pelotas'], ARRAY[3]);
    PERFORM pg_temp.venta(interval '3 days 1 hour', 'MERCADO_PAGO', NULL, ARRAY['Agua mineral', 'Gatorade'], ARRAY[4, 1]);

    -- la de hace 6 días: hoy el cliente devolvió un agua vencida (no vuelve al stock; cuenta en la caja de hoy)
    v := pg_temp.venta(interval '6 days', 'MERCADO_PAGO', 'diego@test.com', ARRAY['Agua mineral', 'Pelotas'], ARRAY[4, 1]);
    WITH i AS (UPDATE venta_items SET devueltas = 1
               WHERE venta_id = v AND producto_id = pg_temp.producto('Agua mineral')
               RETURNING id, precio_unitario),
         d AS (INSERT INTO venta_devoluciones (venta_id, created_at, registrada_por_id, motivo, monto)
               SELECT v, now() - interval '30 minutes', pg_temp.admin(), 'Agua vencida', precio_unitario FROM i
               RETURNING id)
    INSERT INTO venta_devolucion_items (devolucion_id, venta_item_id, cantidad, vuelve_al_stock)
    SELECT d.id, i.id, 1, false FROM d, i;
END $$;

-- ── Un día cerrado (sin reservas ese día) ───────────────────────────────
INSERT INTO dias_cerrados (configuracion_id, fecha, motivo, created_at, created_by_id)
VALUES (1, current_date + 20, 'Mantenimiento de las canchas', now() - interval '1 day', pg_temp.admin());

DROP EXTENSION pgcrypto;

COMMIT;

-- ── Resumen ──────────────────────────────────────────────────────────────
\echo
\echo 'Usuarios por rol (las cuentas nuevas entran con Prueba123!; las que se conservaron, con su contraseña):'
SELECT rol, count(*) AS cantidad, string_agg(email, ', ' ORDER BY id) AS cuentas FROM usuarios GROUP BY rol ORDER BY rol;
\echo 'Reservas por tipo y estado:'
SELECT tipo, estado, count(*) AS cantidad FROM reservas GROUP BY 1, 2 ORDER BY 1, 2;
\echo 'Pagos por medio:'
SELECT provider, CASE WHEN order_id IS NOT NULL THEN 'QR' WHEN external_payment_id IS NOT NULL THEN 'online' END AS canal,
       estado, count(*) AS cantidad, sum(monto) AS total, count(verificado_at) AS verificados
FROM pagos GROUP BY 1, 2, 3 ORDER BY 1, 2, 3;
\echo 'Ventas sueltas:'
SELECT created_at::date AS dia, medio, estado, count(*) AS cantidad, sum(total) AS total FROM ventas GROUP BY 1, 2, 3 ORDER BY 1, 2, 3;
\echo 'Stock de los productos:'
SELECT nombre, tipo, stock, activo FROM productos ORDER BY id;
