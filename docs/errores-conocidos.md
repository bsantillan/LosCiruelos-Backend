# Errores conocidos

Estado al 15/10/2026. El relevamiento del 12/10 encontró 20 problemas más varios menores. Todos están corregidos, salvo lo listado en **Pendiente**. Cada corrección tiene tests (`mvn verify`: 153 tests, cobertura de líneas 80 %), y los flujos de reserva y pago se probaron además de punta a punta contra un PostgreSQL vacío.

## Pendiente

| Tema | Detalle |
|---|---|
| Reembolsos | Los pagos online se devuelven solos al cancelar; si MP no acepta el reembolso en el momento, quedan `REEMBOLSO_PENDIENTE` y un job los reintenta durante 24 h. Los pagos en el club, y los que MP no pudo devolver, quedan `A_REEMBOLSAR` con su `montoReembolsado`: falta un endpoint de admin para listarlos y marcarlos como devueltos (hoy se ven en el log). |
| Configuración | Los tiempos de la política de cancelación (`minutos_reembolso_total`, `minutos_reembolso_senia`) y el resto de la configuración se cambian en la base: falta un endpoint de admin (al cambiarlos, reiniciar la app por la caché). |
| Funcionalidades | ABM de canchas, edición del resto de la configuración del club (horarios, tarifas, seña), agenda del día para el personal. La reserva solo pide pelotas y paletas: otros productos del catálogo todavía no se pueden agregar a una reserva. |
| Rate limiting | Es en memoria (sirve para una instancia). Con varias instancias hay que moverlo a Redis o al proxy. Detrás de un proxy toma la IP real de `X-Forwarded-For` (`server.forward-headers-strategy=framework`, por defecto). |
| WhatsApp | **Bloqueante**: como el perfil completo exige el celular verificado por WhatsApp, sin el alta en Meta ningún cliente nuevo puede reservar. Falta dar de alta el número del club, verificar el negocio y cargar las 8 plantillas de [whatsapp.md](whatsapp.md). Hasta entonces los avisos van por mail. Las novedades y promociones solo se guardan como preferencia: falta el envío. Los estados de entrega de Meta (leído, no entregado) no se reciben: haría falta su webhook, que también se va a necesitar para el bot. |
| Firma del webhook | Se valida solo si se configura `MP_WEBHOOK_SECRET`. Igual, el webhook nunca confía en el contenido: siempre consulta el pago a MP. |
| Base de desarrollo existente | Las migraciones se unificaron en una sola V1. Una base creada antes de este cambio hay que borrarla y recrearla (`docker compose down -v` y `docker compose up -d`); si no, la app no arranca. |
| Puerto 8080 en Windows | Con Docker Desktop iniciado, Windows puede reservar el rango 8079–8178 y la app no levanta en 8080 ("Port 8080 was already in use"). Ver con `netsh interface ipv4 show excludedportrange protocol=tcp`; usar otro puerto (`--server.port=…`) o reiniciar el servicio `winnat`. |

## Corregido

| # | Problema | Corrección |
|---|---|---|
| 1 | `@PreAuthorize` sin efecto | `@EnableMethodSecurity`, más reglas por URL para los pagos manuales |
| 2 | Webhook de MP bloqueado y con parámetros equivocados | `/pagos/notificacion` público; acepta `data.id`, `id` y body JSON; valida `x-signature` |
| 2b | **La URL del webhook nunca se enviaba a MP**: se usaba `@Value` de Google en lugar de Spring | Inyección por constructor con la anotación de Spring |
| 3 | Pagos con Brick sin `external_reference` | Se envía `external_reference` y `metadata.pago_id`; el webhook busca por cualquiera de los dos |
| 4 | El monto lo decidía el front | Se cobra `pago.monto`; el webhook marca `A_REEMBOLSAR` si MP cobró otro monto; idempotencia con `X-Idempotency-Key` |
| 5 | Sin control de dueño (IDOR) | Toda operación sobre una reserva verifica dueño o staff; si no corresponde responde 404 |
| 6 | Errores de negocio como 500 | Handler por tipo: 400 / 401 / 403 / 404 / 405 / 409 / 502; el 500 ya no expone detalles internos |
| 7 | Seguridad varia | Usuario de prueba solo con `APP_SEED_USUARIO_PRUEBA=true`; sin `System.out` del token; `SecureRandom`; 5 intentos por código; rate limiting; código único por usuario y tipo; Google exige email verificado |
| 8 | Migraciones ≠ entidades | Esquema final unificado en `V1__schema_inicial.sql` |
| 9 | Flyway no corría con Spring Boot 4 | `spring-boot-starter-flyway` + `flyway-database-postgresql`. Verificado: antes la app no arrancaba con una base vacía (`missing table [canchas]`) |
| 10 | springdoc 2.x con Spring Boot 4 | springdoc 3.0.3 |
| 11 | Limpieza nocturna de refresh tokens fallaba | `DELETE` JPQL con su propia transacción |
| 12 | `mvn verify` fallaba | 153 tests unitarios y de controllers; `contextLoads` solo corre si hay `DB_URL` |
| 13 | El pago manual calculaba mal el estado | La reserva mantiene sus pagos en memoria (`agregarPago`) y el estado siempre se recalcula a partir de ellos |
| 14 | No se podía cobrar el saldo de una reserva parcial | Se aceptan pagos en `PARCIALMENTE_PAGADA` y `SEÑADA`, también después del turno y también online; si quedan pagas con el turno terminado se completan |
| 15 | El webhook des-señaba o revivía reservas | El rechazo no toca la reserva; un aprobado sobre una reserva cancelada o expirada queda `A_REEMBOLSAR`; transiciones de pago que no vuelven atrás |
| 16 | Solapamientos y job | Lock de cancha y de reserva; restricción `EXCLUDE` en la BD; mismo criterio de "ocupa el turno" en disponibilidad y creación; extender una reserva revisa solapamiento; el job no se corta si una reserva falla |
| 17 | Disponibilidad | Sin error después de las 23:30; usa `duracion`; devuelve `turnosDisponibles` y `horarioMinimoInicio`; rechaza fechas pasadas |
| 18 | Perfil | Categoría: una vez y dentro de 24 h, sin bloquear el resto del perfil; transacción y validación previa; `@Valid`; job que marca `COMPLETADA`; estadísticas sobre partidos completados |
| 19 | Google con cuenta local sin verificar | `enabled` significa solo "no desactivada"; la migración habilita las cuentas pendientes de verificación |
| 20 | Edición de usuarios del admin a medias | Validación previa y `@Transactional`; no se edita ni desactiva a un ADMIN |
| — | Usuario de prueba sin perfil de jugador (no podía reservar) | `DataInitializer` le crea el perfil; `PUT /perfil` crea el perfil si falta |
| — | Emails dentro de transacciones | Se envían después del commit: si falla el SMTP no se revierte el registro, y si se revierte la operación no llega un código inexistente |
| — | Menores | CORS por variable (`CORS_ALLOWED_ORIGINS`); `SHOW_SQL` apagado por defecto; `open-in-view=false`; un solo bean OpenAPI; caché de configuración activa; 401 para token ausente o vencido; DTOs validados en `/auth`; montos con `BigDecimal`; categoría y posición guardadas como texto; nombres de campos corregidos (`descripcion`, `montoXMediaHora`, `porcentajeSenia`) |

## Cambios de contrato para el front

- Validaciones `@Valid`: el body ahora es `{ "timestamp", "errors": {…} }` (antes `{ "status": 400, "errors": {…} }`).
- Cancha inexistente y día sin apertura: `{ "error": "…" }` (antes `{ "errors": "…" }`).
- Errores de reservas y pagos: 400 / 404 / 409 / 502 en vez de 500.
- Sin token o con token vencido: **401** (antes 403). El front puede usarlo para disparar el refresh.
- `ReservaResponse` suma `montoSenia` y `pagos`; `PagoResponse` suma `externalPaymentId`, `estadoReserva` y `detalle`; `PerfilResponse` suma `categoriaEditable`, y `diasDesdeUltimoPartido` puede ser `null`.
- `DisponibilidadResponse` suma `duracion`, `horarioMinimoInicio` y `turnosDisponibles`.
- Nuevos endpoints: `GET /reservas/mias` y `GET /reservas/{id}`.
- **`GET /reservas/mias` ahora es paginado**: responde `{ contenido, pagina, tamanio, totalElementos, totalPaginas }` en lugar de una lista, con filtros `tipo`, `estado`, `desde`, `hasta`, `page`, `size`.
- **`DELETE /reservas/{id}/cancelar`** responde `200` con la `ReservaResponse` (antes sin body), cancela también reservas señadas o pagadas con reembolso, y el personal indica `?reembolso=TOTAL|SENIA|NADA`. `ReservaResponse` suma `cancelable`, `cancelableHasta`, `montoReembolso`, `reembolsoTotalHasta` y `reembolsoSeniaHasta`; `PagoResponse` suma `montoReembolsado`.
- **Pelotas y paletas**: el body de `POST /reservas` cambió `"pelotas": true/false` y `"paletas": true/false` por `"cantidadPelotas"` y `"cantidadPaletas"` (números, opcionales). Ojo: si el front sigue mandando los campos viejos, se ignoran sin error y la reserva queda sin productos. `ReservaResponse` cambió igual y suma `montoCancha`, `montoProductos` y `productos`.
- Nuevos endpoints de productos: `GET /productos`, `GET /productos/disponibilidad` y la sección `/admin/configuracion/productos`.
- **Fechas**: todas las fecha-hora salen como `2026-10-17T18:15:00.123-03:00` (antes sin offset, por ejemplo `2026-10-17T18:15:00.123`), también el `timestamp` de los errores (antes en UTC con `Z`). Las horas salen `HH:mm`. `new Date(valor)` funciona igual; si el front cortaba el string a mano (por ejemplo `valor.slice(11, 16)` para la hora), sigue funcionando.
- **Nuevo estado de pago `REEMBOLSO_PENDIENTE`**: la cancelación ya no responde `502` si MP no acepta el reembolso; cancela igual y el pago queda en ese estado hasta que el job lo devuelva. Mostrarlo como "reembolso en proceso".
- **`GET /reservas/mias` oculta siempre las canceladas y vencidas sin pagos que hayan movido plata**, también al filtrar por estado y en `totalElementos`; solo `incluirDescartadas=true` las trae. `ReservaResponse` suma `turnoTerminado` y `debeSaldo`. Nuevo endpoint del personal: `GET /reservas/saldos-pendientes`.
- **Registro**: suma `aceptarNotificacion` (obligatorio, `true`). No se elige el medio: arranca por mail. El celular se valida después, al completar el perfil (`POST /perfil/telefono/codigo` y `/perfil/telefono/verificar`), y ahí se puede cambiar a WhatsApp.
- **Perfil completo**: ahora exige el **celular verificado**, para CLIENTE y ADMIN (`LoginResponse.perfilCompleto` y `PerfilResponse.perfilCompleto`). Un cliente sin verificar no puede reservar (`403`). Los clientes que ya existen tienen que verificar su celular.
- **Avisos del cliente**: `GET/PUT /perfil/suscripciones` (recordatorios y promociones; las cancelaciones siempre).
- **Avisos por WhatsApp o mail**: En el perfil, una sección de notificaciones (`GET/PUT /perfil/notificaciones`, `POST /perfil/telefono/codigo` y `/verificar`). Para el ADMIN, qué avisos recibe y un interruptor para desactivarlos todos (`GET/PUT /perfil/avisos` con `activos`, `POST /perfil/avisos/prueba`) y el recordatorio (`GET/PUT /admin/configuracion/avisos`). `PerfilResponse` y `UsuarioAdminResponse` suman `telefonoVerificado` y `canalNotificaciones`.
- **Topes del código por WhatsApp**: `POST /perfil/telefono/codigo` responde `429` con el motivo y cuánto falta (1 por minuto, 5 por día por usuario y 5 por día por número). Mostrá el mensaje tal cual.
- **Celular en uso**: si el celular ya está verificado en otra cuenta activa, el registro y el perfil responden `400 errors.telefono` ("Ese celular ya está verificado en otra cuenta…") y pedir o verificar el código, `409`.
- **Teléfonos**: se aceptan con espacios, guiones y paréntesis, y se devuelven normalizados (`5491123456789`). Un número que no es válido responde `400 errors.telefono`.
- **Consultas públicas**: `GET /canchas`, `GET /canchas/{id}/disponibilidad`, `GET /productos` y `GET /productos/disponibilidad` ya no piden sesión: un visitante puede ver horarios libres y precios. Para reservar sigue haciendo falta iniciar sesión.
- `DELETE /perfil/desactivar` ya no necesita body.
- `pagarTotal` es opcional (por defecto `false` = seña). `iniciar` y `pago` también los pueden usar ADMIN y EMPLEADO.
- Un usuario registrado y no verificado ahora tiene `enabled: true` en el listado del admin (la verificación se ve en `emailVerificado`).
