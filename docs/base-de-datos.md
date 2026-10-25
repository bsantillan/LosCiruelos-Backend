# Base de datos

PostgreSQL 16. El esquema lo crea Flyway en `src/main/resources/db/Migrations`: `V1__schema_inicial.sql` tiene el esquema completo (unifica las migraciones de desarrollo anteriores), `V2__stock_inicial_productos.sql` carga el stock inicial de pelotas y paletas, `V3__indice_mis_reservas.sql` agrega el índice del listado de reservas del cliente , `V4__politica_cancelacion.sql` agrega la política de reembolso a la configuración y `monto_reembolsado` a los pagos y `V5__reembolsos_pendientes_y_clave_idempotencia.sql` agrega el estado `REEMBOLSO_PENDIENTE`, `reembolso_solicitado_at` y `clave_idempotencia`,, `V6__stock_50_pelotas_y_paletas.sql` deja el stock en 50 pelotas y 50 paletas, `V7__precio_pelotas_y_paletas.sql` les pone precio de $5000 y `V8__whatsapp.sql` agrega el canal de notificaciones y la verificación del teléfono de los usuarios, la tabla `avisos_admin`, el recordatorio del turno y el tipo de código `VERIFY_PHONE`,, `V9__avisos_admin_activos.sql` el interruptor general de los avisos de cada ADMIN, `V10__preferencias_notificaciones.sql` el consentimiento de notificaciones del registro y los avisos que elige el cliente, `V11__celular_verificado_unico.sql` un índice único parcial (un celular verificado pertenece a una sola cuenta activa), `V12__topes_codigo_telefono.sql` la tabla `envios_codigo_telefono` para los topes de códigos por WhatsApp y `V13__NormalizarTelefonos` (migración **Java**, en `src/main/java/db/Migrations`) normaliza los teléfonos guardados antes de V8 con la misma regla que la app, sin tocar la verificación (los inválidos quedan en null),, `V14__indices_panel_reservas.sql` agrega índices para el listado del personal, `V15__bonificaciones.sql` el provider `BONIFICACION` y, en los pagos, quién los registró y el motivo, y `V16__fecha_reembolso.sql` la fecha de reembolso de los pagos (`reembolsado_at`) y los índices del cierre de caja. V16 completa los datos viejos con la fecha más cercana que hay (pedido del reembolso a MP, cobro o creación del pago) y pone `paid_at` a los cobros de MP que quedaron devueltos o por devolver sin haber pasado por `APROBADO`. Hibernate solo **valida** que las entidades coincidan con las tablas (`ddl-auto=validate`).

## Cómo funcionan las migraciones

- Cada archivo `V<n>__descripcion.sql` es un cambio de esquema. Flyway los aplica **una vez y en orden**, y anota cuáles ya corrió en la tabla `flyway_schema_history`.
- Al arrancar, la app ejecuta las migraciones que falten. En todas las bases (la tuya, la de otro desarrollador, producción) el esquema queda idéntico, y nadie tiene que correr SQL a mano.
- **Una migración aplicada no se edita.** Flyway guarda un checksum de cada archivo; si cambia, la app no arranca. Para modificar el esquema se crea `V2__…sql`, `V3__…sql`, etc.
- `baseline-on-migrate` está en `false`: si la app se conecta a una base que ya tiene tablas pero no tiene historial de Flyway, falla en vez de saltear la V1 en silencio.
- Para empezar de cero en desarrollo: `docker compose down -v` (borra el volumen con los datos) y `docker compose up -d`. Al arrancar, la app crea todo.

## Diagrama de relaciones

```
usuarios 1──1 clientes            (clientes.usuario_id, UNIQUE)
usuarios 1──1 refresh_tokens      (refresh_tokens.usuario_id, UNIQUE)
usuarios 1──N verification_tokens
usuarios 1──N reservas            (reservas.cliente_id  = para quién es)
usuarios 1──N reservas            (reservas.created_by_id = quién la creó)
canchas  1──N reservas
reservas 1──N reserva_productos N──1 productos
reservas 1──N pagos
configuracion 1──N dias           (dias.configuracion_general_id)
```

## Tablas

### `usuarios` (`Usuario`)
| Columna | Tipo | Notas |
|---|---|---|
| id | bigint identity | PK |
| nombre, apellido | varchar(50) | nullable |
| email | varchar(100) | NOT NULL, UNIQUE |
| telefono | varchar(20) | formato de WhatsApp, solo dígitos (`5491123456789`) |
| telefono_verificado | boolean | se confirmó con el código por WhatsApp (V8). Único por celular entre las cuentas activas (`uk_usuarios_celular_verificado_activo`, V11) |
| canal_notificaciones | varchar(20) | CHECK: EMAIL, WHATSAPP (V8, por defecto EMAIL) |
| acepta_whatsapp_at | timestamp | cuándo aceptó recibir WhatsApp (V8) |
| notificaciones_aceptadas_at | timestamp | cuándo aceptó recibir notificaciones en el registro (V10) |
| recibe_recordatorios | boolean | el cliente quiere el recordatorio del turno (V10, true) |
| recibe_promociones | boolean | el cliente quiere novedades y promociones (V10, false) |
| password_hash | varchar(255) | null en usuarios de Google |
| terms_accepted | boolean | |
| terms_accepted_at | timestamp | también se usa como fecha de alta |
| email_verificado | boolean | |
| enabled | boolean | false = cuenta desactivada (la verificación está en `email_verificado`) |
| rol | varchar(20) | CHECK: CLIENTE, EMPLEADO, ADMIN, PROFESOR |
| provider | varchar(20) | CHECK: LOCAL, GOOGLE |

### `clientes` (`ClienteProfile`)
| Columna | Tipo | Notas |
|---|---|---|
| id | bigint identity | PK |
| usuario_id | bigint | FK → usuarios, UNIQUE |
| categoria | varchar(20) | nombre del enum `Categoria` (Primera … Principiante) |
| posicion | varchar(10) | `Drive`, `Reves`, `Ambos` |
| categoria_actualizada_at | timestamp | cuándo se cargó la categoría (empieza el plazo de 24 h) |
| categoria_modificada | boolean | el cliente ya usó su único cambio de categoría |

### `configuracion` (`Configuracion`)
Una sola fila (id = 1): `minutos_reembolso_total int` (1440) y `minutos_reembolso_senia int` (720) para la política de cancelación, `recordatorio_activo boolean` (true) y `minutos_recordatorio int` (120, entre 15 y 1440) para el recordatorio del turno, `monto_reserva numeric(10,2)`, `monto_x_media_hora numeric(10,2)`, `porcentaje_senia numeric(5,2)`, `duracion_minima_turno int`, `duracion_maxima_turno int`.

### `dias` (`DiaApertura`)
`id bigint identity`, `dia varchar(10)` ("Lunes" … "Domingo"), `horario_inicio time`, `horario_fin time`, `configuracion_general_id` FK → configuracion.

### `canchas` (`Cancha`)
`id bigint identity`, `numero integer`, `descripcion varchar(300)`.

### `reservas` (`Reserva`)
| Columna | Tipo | Notas |
|---|---|---|
| id | bigint identity | PK |
| cancha_id | bigint | FK → canchas |
| cliente_id | bigint | FK → usuarios |
| created_by_id | bigint | FK → usuarios |
| fecha_reserva | date | |
| hora_inicio, hora_fin | time(0) | |
| monto_total | numeric(10,2) | |
| estado | varchar(255) | CHECK: PENDIENTE, SEÑADA, PARCIALMENTE_PAGADA, PAGADA, CANCELADA, EXPIRADA, COMPLETADA |
| created_at | timestamp | |
| expires_at | timestamp | creación + 15 min (plazo para pagar) |
| recordatorio_enviado_at | timestamp | cuándo se mandó el recordatorio del turno (V8) |

Restricción `reservas_sin_solapamiento`: `EXCLUDE USING gist (cancha_id WITH =, tsrange(fecha+inicio, fecha+fin) WITH &&) WHERE estado NOT IN ('CANCELADA','EXPIRADA')`. Dos reservas activas de la misma cancha no se pueden superponer (requiere la extensión `btree_gist`).

### `productos` (`Producto`)
| Columna | Tipo | Notas |
|---|---|---|
| id | bigint identity | PK |
| codigo | varchar(30) | UNIQUE; `PELOTAS` y `PALETAS` (productos del sistema), null en el resto |
| nombre | varchar(100) | único sin distinguir mayúsculas |
| descripcion | varchar(300) | |
| tipo | varchar(20) | CHECK: VENTA, ALQUILER |
| precio | numeric(10,2) | por unidad, ≥ 0 |
| stock | integer | ≥ 0; VENTA = disponibles, ALQUILER = total |
| activo | boolean | baja lógica |

### `reserva_productos` (`ReservaProducto`)
| Columna | Tipo | Notas |
|---|---|---|
| id | bigint identity | PK |
| reserva_id | bigint | FK → reservas |
| producto_id | bigint | FK → productos |
| cantidad | integer | > 0 |
| precio_unitario | numeric(10,2) | precio congelado al reservar |
| cantidad_devuelta | integer | alquileres: unidades devueltas; null = devolución sin registrar |
| devuelta_at | timestamp | cuándo se registró la devolución |

`UNIQUE (reserva_id, producto_id)`.

### `pagos` (`Pago`)
| Columna | Tipo | Notas |
|---|---|---|
| id | bigint identity | PK |
| reserva_id | bigint | FK → reservas |
| monto | numeric(10,2) | |
| estado | varchar(255) | CHECK: PENDIENTE, APROBADO, RECHAZADO, EN_PROCESO, DEVUELTO, EXPIRADO, A_REEMBOLSAR, REEMBOLSO_PENDIENTE |
| provider | varchar(255) | CHECK: MERCADO_PAGO, MANUAL, BONIFICACION (V15: el ADMIN no le cobró al cliente; cubre el monto pero no es plata) |
| registrado_por_id | bigint | FK → usuarios: quién registró el pago en el club o la bonificación (V15) |
| motivo | varchar(200) | motivo de una bonificación (V15) |
| preference_id | varchar(255) | id de la preferencia de MP (campo `paymentToken` en la entidad) |
| external_payment_id | varchar(255) | id del pago en MP (único cuando no es null) |
| created_at, paid_at | timestamp | |
| monto_reembolsado | numeric(10,2) | cuánto se devolvió (`DEVUELTO`) o hay que devolver (`A_REEMBOLSAR`, `REEMBOLSO_PENDIENTE`); puede ser parcial |
| reembolso_solicitado_at | timestamp | cuándo se pidió el reembolso que MP no aceptó; a las 24 h sin éxito pasa a `A_REEMBOLSAR` |
| reembolsado_at | timestamp | cuándo pasó a `DEVUELTO` o `A_REEMBOLSAR` (V16); las `devoluciones` de la caja del resumen usan esta fecha. Índices `idx_pagos_paid_at` e `idx_pagos_reembolsado_at` |
| clave_idempotencia | uuid NOT NULL UNIQUE | se genera al crear el pago y no cambia; arma el `X-Idempotency-Key` de MP (V5 la completó con `gen_random_uuid()` en los pagos existentes) |

### `refresh_tokens` (`RefreshToken`)
`id`, `token varchar(255) UNIQUE`, `fecha_expiracion timestamptz`, `usuario_id` FK UNIQUE.

### `envios_codigo_telefono` (`EnvioCodigoTelefono`)
Un registro por cada código de verificación mandado por WhatsApp (V12): `usuario_id` FK, `telefono`, `enviado_at`. Se usa para los topes por usuario y por número en 24 h; los registros de más de 48 h se borran al mandar el siguiente. Índices por `(usuario_id, enviado_at)` y `(telefono, enviado_at)`.

### `avisos_admin` (`AvisosAdmin`)
Qué avisos de reservas recibe cada ADMIN (V8). `usuario_id` PK y FK a usuarios; `activos` (interruptor general, V9), `reserva_confirmada`, `saldo_cobrado`, `reserva_cancelada`, `reembolso_manual` (boolean, true por defecto). Sin fila: recibe todos.

### `verification_tokens` (`VerificationToken`)
| Columna | Tipo | Notas |
|---|---|---|
| id | bigint identity | PK |
| usuario_id | bigint | FK → usuarios |
| token | varchar(6) | código de 6 dígitos |
| type | varchar(20) | CHECK: VERIFY_EMAIL, PASSWORD_RESET, VERIFY_PHONE · UNIQUE (usuario_id, type) |
| fecha_expiracion | timestamptz | |
| verificado | boolean | el código de reset ya se validó |
| intentos | integer | intentos fallidos (máximo 5) |

## Índices

| Índice | Columnas | Para qué |
|---|---|---|
| `idx_reservas_estado_expires` | reservas(estado, expires_at) | Job de expiración |
| `idx_reservas_cancha_fecha` | reservas(cancha_id, fecha_reserva) | Solapamientos y disponibilidad |
| `idx_reservas_fecha_hora` | reservas(fecha_reserva, hora_inicio) | Alquileres por día, job de completadas y listado del personal por fecha y orden del turno (V14, reemplaza a `idx_reservas_fecha`) |
| `idx_reservas_estado_fecha` | reservas(estado, fecha_reserva) | Listado del personal por estado y "pendientes" (V14) |
| `idx_reservas_cliente_fecha` | reservas(cliente_id, fecha_reserva DESC, hora_inicio DESC) | "Mis reservas" (V3, reemplaza a `idx_reservas_cliente`) |
| `idx_reserva_productos_producto` | reserva_productos(producto_id) | Uso de productos alquilados |
| `idx_pagos_reserva` | pagos(reserva_id) | Pagos de una reserva |
| `uk_pagos_external_payment_id` | pagos(external_payment_id), único si no es null | Webhook de MP |
| `uk_productos_nombre` | lower(productos.nombre), único | Nombres sin repetir |

Además, las restricciones `UNIQUE` (email, código de producto, `verification_tokens(usuario_id, type)`, `refresh_tokens(usuario_id)`, `reserva_productos(reserva_id, producto_id)`) crean sus propios índices.

## Datos iniciales

- La migración V1 carga los productos `PELOTAS` (VENTA) y `PALETAS` (ALQUILER) con precio y stock en 0, la V2 les ponía el stock inicial (300 pelotas y 50 paletas) , la V6 lo fija en **50 pelotas y 50 paletas** y la V7 les pone precio de **$5000 por unidad**. Después el admin los cambia desde la API.
- `DataInitializer` (al arrancar la app) crea el admin, la configuración, las 4 canchas y, si `APP_SEED_USUARIO_PRUEBA=true`, el usuario de prueba.
