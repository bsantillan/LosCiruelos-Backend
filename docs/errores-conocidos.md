# Errores conocidos

Estado al 15/10/2026. El relevamiento del 12/10 encontró 20 problemas más varios menores. Todos están corregidos, salvo lo listado en **Pendiente**. Cada corrección tiene tests (`mvn verify`: 153 tests, cobertura de líneas 80 %), y los flujos de reserva y pago se probaron además de punta a punta contra un PostgreSQL vacío.

## Pendiente

| Tema | Detalle |
|---|---|
| Funcionalidades | ABM de canchas, agenda del día para el personal. |
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

- **Cierre de caja**: nuevo `GET /admin/caja?fecha=` (ADMIN cualquier jornada, EMPLEADO la que está en curso) con `fecha`, `inicio`, `fin`, `cobrado`, `devuelto`, `efectivoEnCaja`, `bonificado`, `aEntregar` y `movimientos`. `GET /admin/reservas/resumen` pasa a ser **solo del ADMIN** (el EMPLEADO recibe `403`); sin fechas toma la jornada en curso.
- **Eliminar un producto**: vuelve `DELETE /admin/configuracion/productos/{id}` → `204`, solo para uno que nunca se usó. Si se usó, `409` "El producto ya se usó en reservas o ventas: desactivalo en lugar de eliminarlo." La baja y la reactivación siguen igual.
- **Bajas de turno fijo con `entregada`**: `DELETE /admin/turnos-fijos/{id}` y `DELETE /admin/turnos-fijos/{id}/fechas/{fecha}` aceptan `?entregada=` (por defecto `true`). Antes lo cobrado en el club quedaba siempre `A_REEMBOLSAR`; ahora, por defecto, se devuelve en el momento.
- **El EMPLEADO da de alta clientes**: `POST /admin/usuarios` acepta al EMPLEADO con `rol: CLIENTE`; con otro rol, `403` "Solo podés dar de alta clientes.". Editar, desactivar y reactivar siguen siendo del ADMIN.
- **Partidos y turnos fijos de 1 a 4 jugadores**: `jugadores` acepta de 1 a 4 (antes exactamente 4). El jugador 1 sigue siendo obligatorio y un cliente registrado. El error de validación pasa a ser "Un partido tiene de 1 a 4 jugadores".
- **Una `PENDIENTE` vence al empezar el turno** si eso pasa antes de los 15 minutos: `expiresAt` nunca es posterior al inicio del turno.
- **Caja por jornada**:
  - `GET /admin/reservas/resumen`: la `caja` suma por jornada (de la apertura de `desde` a la apertura del día siguiente a `hasta`), también para el ADMIN. Antes era de 00:00 a 24:00. Lo de reservas sigue por la fecha del turno.
  - `caja` suma `bonificado`, `inicio` y `fin`.
  - `GET /admin/ventas`: `desde` y `hasta` también son jornadas para el ADMIN. Una venta de las 00:10 es del día anterior.
- **El EMPLEADO devuelve, con revisión del ADMIN**:
  - `POST …/pagos/{pagoId}/devoluciones` ya no es solo del ADMIN: el EMPLEADO devuelve los cobros que registró el personal en su jornada, con `motivo` obligatorio. Si falta, `400`; fuera de la jornada, `403`.
  - Rutas nuevas, solo del ADMIN: `PUT /admin/reservas/{id}/devoluciones/{devolucionId}/revision` con `{ revisada }` y `GET /admin/reservas/devoluciones-sin-revisar`.
  - `PagoResponse.devoluciones[]` suma `revisada`, `revisadaAt` y `revisadaPor`.
  - Los ítems de `devoluciones-a-entregar` suman `estado`, `registradaPor` y `revisada`.
- **Devoluciones en el club entregadas en el momento**:
  - `POST …/pagos/{pagoId}/devoluciones` acepta `entregada` (por defecto `true`): una devolución de un cobro del club queda `DEVUELTO` en el acto. Antes quedaba siempre `A_REEMBOLSAR`; para eso, mandá `false`.
  - `DELETE /admin/reservas/{id}/cancelar` acepta `?entregada=` (por defecto `true`) para lo cobrado en el club.
  - El aviso de la cancelación dice "Se devolvieron $X en el club." cuando se entregó en el momento.
- **Anular y devolver cobros de las reservas**:
  - Rutas nuevas:
    - `POST /admin/reservas/{id}/pagos/{pagoId}/anulacion` con `{ motivo }`;
    - `POST /admin/reservas/{id}/pagos/{pagoId}/devoluciones` con `{ lineas: [{ concepto, lineaId, monto }], motivo }` (solo ADMIN, `201`);
    - `PUT /admin/reservas/{id}/devoluciones/{devolucionId}/entrega`;
    - `GET /admin/reservas/devoluciones-a-entregar`.

    Las tres operaciones responden `ReservaResponse`.
  - Nuevo estado de pago `ANULADO`.
  - `PagoResponse` suma `devoluciones: [{ id, monto, lineas, estado, motivo, creadoAt, entregadoAt, usuario, entregadoPor }]`, `anuladoAt`, `motivoAnulacion` y `anuladoPor`.
  - Un pago devuelto en parte por línea **sigue `APROBADO`** con `montoReembolsado` acumulado.
  - La caja (`resumen.caja.devoluciones`) resta las devoluciones cuando se entregan o MP las confirma. Antes, lo `A_REEMBOLSAR` nunca se restaba.
  - Una reserva confirmada que queda sin nada cubierto (por anular o devolver) queda `CONFIRMADA`: ya no pasa a `CANCELADA` ni a `PENDIENTE`.
- **Imputación de los pagos por concepto**:
  - `ReservaResponse` suma `montoCanchaPagado` y `montoCanchaPendiente`; cada producto (`productos[]`) suma `lineaId`, `montoPagado` y `montoPendiente`.
  - `PagoResponse` suma `imputaciones: [{ concepto, lineaId, nombre, monto }]`.
  - Los cobros del personal (`cobros/efectivo`, `transferencia`, `presencial`, `cuenta-dni`, `qr`) y `bonificacion` aceptan `imputaciones` opcional (sin ellas, se imputa solo: primero la cancha).
  - Sacar o bajar un producto con plata cobrada, o bajar el precio de la cancha de un partido por debajo de lo cobrado, responde `409` "Gatorade tiene $2.000 cobrados: anulá o devolvé ese cobro antes de sacarlo." con `lineaId` y `montoCobrado` en el body. Reemplaza al error "El total nuevo ($X) es menor que lo ya pagado ($Y).". Lo que no tiene nada cobrado se saca aunque la reserva esté paga en parte.
- **Jornada del EMPLEADO**: su "hoy" pasa a ser la jornada en curso (de la apertura del día a la apertura del día siguiente). Pasada la medianoche sigue viendo y operando la jornada anterior: reservas de esa fecha en adelante, el resumen de esa fecha y las ventas registradas dentro de la jornada (antes, el día calendario). Nuevo `GET /admin/jornada` → `{ fecha, inicio, fin }` para saber qué fecha mandarle en `desde`/`hasta`. Una venta registrada entre la medianoche y la apertura ahora es de la jornada anterior.
- **Permisos del EMPLEADO en la API** (antes solo los aplicaba el front):
  - `GET /admin/reservas`: sin `desde` toma hoy; `desde` o `hasta` anteriores a hoy → `403` "Solo podés ver las reservas de hoy en adelante." (también con `pendientes=true`; los totales cambian en consecuencia).
  - `GET /admin/reservas/{id}`: una reserva de un día anterior → `404`.
  - Cobros (efectivo, transferencia, `presencial`, Cuenta DNI, QR), cancelación, productos y devolución de paletas sobre una reserva de un día anterior → `403` "Solo el ADMIN puede operar reservas de días anteriores.".
  - `GET /admin/reservas/devoluciones-pendientes` y `/saldos-pendientes`: solo las de hoy.
  - `GET /admin/usuarios` y `GET /admin/usuarios/{id}` ahora también para el EMPLEADO (el detalle de un ADMIN → `404`); crear, editar, desactivar y reactivar siguen siendo del ADMIN.
- **Transferencia de Mercado Pago como medio propio**:
  - Reservas: nuevo `POST /admin/reservas/{id}/cobros/transferencia` (`provider = TRANSFERENCIA_MP`); `cobros/presencial` queda como alias deprecado (ahora registra una transferencia). `PagoResponse` suma `canal` (`EFECTIVO`, `MERCADO_PAGO_ONLINE`, `MERCADO_PAGO_QR`, `TRANSFERENCIA_MP`, `CUENTA_DNI`, `BONIFICACION`). Los pagos viejos "Mercado Pago cargado a mano" pasan a `TRANSFERENCIA_MP`.
  - Ventas: nuevo medio `TRANSFERENCIA_MP` (las ventas viejas con Mercado Pago sin QR pasan a ese medio). `GET /admin/ventas/medios` lo suma.
  - Verificación: el ADMIN verifica también las transferencias (`PUT …/verificacion` en reservas y ventas); cambian los mensajes ("Solo se verifican … con Cuenta DNI o por transferencia.", "Solo el ADMIN puede verificar un pago." / "… una venta."). Nuevo `GET /admin/pagos/verificables?desde&hasta&verificado&medio`; `GET /admin/pagos/cuenta-dni` sigue. Los cobros a verificar suman `medio`.
  - Nuevo `GET /admin/reservas/medios-de-cobro` (los 4 medios con el endpoint de cada uno).
  - Totales: `caja.detalleMercadoPago` en el resumen, `totales.detalleMercadoPago` en el listado de reservas y `totales.mercadoPago` en el de ventas (`{ total, qr, online, transferencia }`); `mercadoPago` sigue sumando todo.
- **Listado de ventas**: `GET /admin/ventas` ahora es paginado y devuelve `{ contenido, pagina, tamanio, totalElementos, totalPaginas, totales }` (antes, una lista). Suma los filtros `productoId`, `cliente` (mínimo 3 letras), `clienteId`, `medio`, `estado`, `page`, `size` y `orden`; con una sola fecha ya no es `400`. `totales`: `ventas`, `anuladas`, `total` (neto), `devuelto` y `porMedio`.
- **Productos**: todos controlan stock (`controlaStock: false` es `400`); `disponibles` ya nunca es `null`. `DELETE /admin/configuracion/productos/{id}` se reemplaza por `PUT …/{id}/baja`, y se suma `PUT …/{id}/reactivacion`; el `PUT` completo ignora `activo`. Un producto escrito a mano nace con stock igual a lo que se vende o agrega (queda en 0).
- **Modalidad de pago de la reserva del personal**: `POST /admin/reservas` acepta `modalidadPago` (`EN_EL_CLUB` por defecto, `SENIA_ONLINE`, `SIN_SENIA` solo ADMIN). `ReservaResponse` suma `modalidadPago` (null en las del cliente y los partidos); `expiresAt` es `null` en una `SIN_SENIA`. La configuración suma `minutosPagoSeniaOnline` (opcional en el `PUT`). Aviso nuevo al cliente, `SENIA_PENDIENTE` (plantilla de WhatsApp `reserva_pagar_senia_v1`, a dar de alta en Meta).
- **Reserva del personal para un cliente con el perfil incompleto**: `POST /admin/reservas` ya no responde `403` "El cliente … debe completar su perfil antes de reservar." (alcanza con que sea un CLIENTE activo). `POST /reservas` (el propio cliente) sigue exigiéndolo.
- **Cambio de horario confirmado y días cerrados**:
  - `PUT /admin/configuracion` con `"confirmar": true` ahora **cancela** las reservas que quedan fuera del horario nuevo (reembolso total y aviso al cliente) y la respuesta suma `cancelaciones: { canceladas, sinCancelar }`. Mostrar las que no se pudieron cancelar para hacerlo a mano. El mensaje del `409` cambió.
  - Nuevos `GET`/`POST /admin/configuracion/cierres` y `DELETE /admin/configuracion/cierres/{fecha}` (ADMIN): días puntuales en que el club no abre, con el mismo `409` y `confirmar` (cancela las reservas de ese día).
  - `GET /club/horarios` suma `cierres: [{ fecha, motivo }]` (de hoy en adelante): la grilla tiene que mostrar esos días cerrados.
- **Medios de cobro de las ventas**: nuevo `GET /admin/ventas/medios` (ADMIN y EMPLEADO) con los medios en orden, su etiqueta, si se cobra con QR y si están disponibles (Mercado Pago, solo con el QR de la caja configurado). Antes esa ruta caía en `GET /admin/ventas/{id}` y respondía `400`.
- **Precio del turno**: `montoReserva` de la configuración (`GET`/`PUT /admin/configuracion`) ahora es el precio del turno mínimo, y `montoXMediaHora` se suma solo por cada media hora por encima del mínimo. La migración V33 ajustó el valor guardado (de $0 a $15.000 con la configuración inicial), así que los precios no cambian. Si el front calcula un precio a mano, usar `montoReserva + montoXMediaHora × (duración − duracionMinimaTurno) / 30`, o mejor el `montoCancha` de la disponibilidad.
- **Listado de reservas del personal por turno fijo**: `GET /admin/reservas` acepta `turnoFijoId` (los partidos de ese turno fijo) y `deTurnoFijo=true|false` (solo los que salen de un turno fijo, o solo los que no). Se combinan con los demás filtros y con los totales.
- **Horarios del club y reservas afuera**:
  - `PUT /admin/configuracion` puede responder `409` con `reservasFueraDeHorario` y `turnosFijosFueraDeHorario` si el horario nuevo deja reservas o turnos fijos afuera. El front muestra la lista y, si el ADMIN acepta, vuelve a mandar el mismo body con `"confirmar": true` (las reservas no se cancelan).
  - Nuevo `GET /club/horarios` (público): días y horarios de apertura de lunes a domingo, `duracionMinimaTurno` y `duracionMaximaTurno`, para la grilla del panel y el sitio.
  - En `GET /admin/turnos-fijos` y `GET /admin/turnos-fijos/{id}`, `fechasOmitidas` ya no es `null`: trae las fechas que no se van a crear porque el turno quedó fuera del horario (lista vacía si entra).
- **Configuración del club**: nuevos `GET /admin/configuracion` y `PUT /admin/configuracion` (solo ADMIN) con toda la configuración junta: precios, seña, duración de los turnos, política de cancelación, recordatorio, anticipación y días y horarios de apertura. El `GET` devuelve el mismo objeto que recibe el `PUT`. `/admin/configuracion/avisos` y `/admin/configuracion/reservas` no cambian.
- **Anulación de ventas sueltas**:
  - `VentaResponse` suma `estado` (`COBRADA` o `ANULADA`), `anuladaAt`, `anuladaPor` y `motivoAnulacion`. El listado de ventas trae también las anuladas: mostrarlas marcadas.
  - Nuevos (ADMIN y EMPLEADO): `POST /admin/ventas/{id}/anulacion` con `{ motivo }` (el EMPLEADO solo anula las de hoy sin verificar), `PUT /admin/ventas/{id}/cliente` con `{ clienteId }` (`null`: venta al público) y `GET /admin/ventas/{id}`.
  - `GET /admin/pagos/cuenta-dni` suma `anulada` en cada cobro.
  - Una venta anulada sale de `caja`, `ventas` y `productos` del resumen.
- **Ventas sueltas con Mercado Pago por el QR de la caja**:
  - `POST /admin/ventas` con `medio: "MERCADO_PAGO"` ya no deja la venta cobrada: queda `ESPERANDO_PAGO` y responde `qr: { orderId, venceAt }`. El front tiene que mostrar el modal de espera (como el del QR de una reserva), consultar `GET /admin/ventas/{id}` hasta que quede `COBRADA` o `NO_COBRADA`, y al cerrarlo sin pagar llamar a `DELETE /admin/ventas/{id}/qr`.
  - Errores nuevos de `POST /admin/ventas` con Mercado Pago: `409` caja ocupada, `502` y `503` (QR sin configurar).
  - `VentaResponse.estado` suma `ESPERANDO_PAGO` y `NO_COBRADA`, y la respuesta suma `qr`.
  - El cobro con QR de una reserva también responde `409` si la caja espera el pago de una venta ("… de la venta #N …").
  - Anular o devolver una venta cobrada con QR devuelve la plata por Mercado Pago; si MP no lo acepta, `502`.
- **Devoluciones de ventas sueltas**:
  - Nuevo, solo ADMIN: `POST /admin/ventas/{id}/devoluciones` con `{ motivo, items: [{ productoId, cantidad, vuelveAlStock }] }`.
  - `VentaResponse` suma `montoDevuelto` y `devoluciones`, y cada ítem, `devueltas`.
  - `GET /admin/reservas/resumen`: `ventas` suma `devoluciones`; `caja.devoluciones` (y por lo tanto `caja.neto`) incluye lo devuelto de ventas en el período; `productos` queda neto de lo devuelto.
  - Una venta con devoluciones ya no se puede anular (`409`).
- **Disponibilidad de productos para el personal**: nuevo `GET /admin/configuracion/productos/disponibilidad?fecha&horaInicio&horaFin[&excluirReservaId]` (ADMIN y EMPLEADO), con las unidades libres de todos los productos activos, también los que no se ofrecen al reservar. Al editar una reserva o un partido, pasar `excluirReservaId` para que lo que ya tiene cuente como libre. `GET /productos/disponibilidad` (del cliente) no cambia: sigue trayendo solo los que se ofrecen al reservar, y suma el campo `ofrecerAlReservar` (siempre `true` ahí).
- **Cobro con el QR impreso de la caja de Mercado Pago** (ADMIN y EMPLEADO): nuevos `POST`, `GET` y `DELETE /admin/reservas/{id}/cobros/qr`, que responden `CobroQrResponse`. No hay QR para mostrar en pantalla: el cliente escanea el QR del mostrador; el front muestra "esperando el pago de $X" y el tiempo que queda. La caja tiene un solo QR: si está esperando el pago de otra reserva, `POST` responde `409`. Mientras el cobro está activo, la reserva tiene un pago `EN_PROCESO`: cobrar en efectivo, sumar productos o cancelar responden `409` hasta que se pague, venza o se cancele. Con el QR sin configurar (`MP_QR_EXTERNAL_POS_ID`), `503`. `PagoResponse` puede traer pagos `MERCADO_PAGO` cobrados por QR (sin `externalPaymentId`).
- **Medios de pago: efectivo, Mercado Pago y Cuenta DNI**:
  - Nuevo `POST /admin/reservas/{id}/cobros/cuenta-dni` con `{ monto }` (ADMIN y EMPLEADO). `PaymentProvider` suma `CUENTA_DNI` y `PagoResponse` suma `verificado` y `verificadoAt` (solo en los pagos con Cuenta DNI).
  - Nuevos, solo ADMIN: `PUT /admin/reservas/{id}/pagos/{pagoId}/verificacion` con `{ verificado }`, `PUT /admin/ventas/{id}/verificacion` con `{ verificada }` y `GET /admin/pagos/cuenta-dni?desde&hasta&verificado` (todo lo cobrado con Cuenta DNI, reservas y ventas, para controlar contra la cuenta). `VentaResponse` suma `verificada` y `verificadaAt`.
  - **La caja del resumen cambia de forma**: `caja` pasa a `{ total, efectivo, mercadoPago, cuentaDni, devoluciones, neto }`. Se van `mercadoPagoOnline` y `mercadoPagoPresencial` (ahora es todo `mercadoPago`).
  - Los totales del listado (`GET /admin/reservas`) suman `cobradoCuentaDni`; `cobradoMercadoPago` ya no incluye Cuenta DNI.
  - Las ventas sueltas aceptan `medio: "CUENTA_DNI"`.
- **Productos en las reservas, consumos y ventas sueltas**:
  - **Pedido de productos genérico**: `POST /reservas`, `POST /admin/reservas`, los partidos y los turnos fijos reemplazan `cantidadPelotas` y `cantidadPaletas` por `"productos": [{ "productoId": 1, "cantidad": 2 }]`. Si el front sigue mandando los campos viejos, se ignoran sin error y la reserva queda sin productos. `ReservaResponse` ya no trae `cantidadPelotas` ni `cantidadPaletas` (los productos están en `productos`); `TurnoFijoResponse` los cambia por `productos: [{ productoId, nombre, cantidad }]`.
  - **Qué ve el cliente**: `GET /productos` y `GET /productos/disponibilidad` traen solo los productos que se ofrecen al reservar. Pedir otro en `POST /reservas` responde `400` "X no se puede pedir al reservar: pedíselo al club.". En la disponibilidad, `disponibles` es `null` en un producto sin control de stock.
  - `ProductoResponse` y el body de `/admin/configuracion/productos` suman `ofrecerAlReservar` y `controlaStock`.
  - Nuevos `POST /admin/reservas/{id}/productos` y `DELETE /admin/reservas/{id}/productos/{productoId}?cantidad=` (ADMIN y EMPLEADO): el personal suma o saca productos de una reserva o partido, también ya jugado. Una reserva paga o completada que suma algo **vuelve a quedar con saldo** (`SEÑADA`/`PARCIALMENTE_PAGADA`) y puede aparecer como deuda.
  - Nuevos `POST /admin/ventas` y `GET /admin/ventas?desde&hasta` (ADMIN y EMPLEADO; el EMPLEADO solo ve hoy): ventas sueltas sin reserva.
  - `GET /admin/reservas/resumen` suma `ventas: { cantidad, total }`; `caja.efectivo`, `caja.mercadoPagoPresencial`, `caja.total` y `caja.neto` incluyen las ventas sueltas, y `productos` suma lo vendido suelto.
- **Partidos con responsable y monto general**:
  - El **jugador 1 tiene que ser un cliente registrado** (`400` si es un invitado): es el responsable y el `clienteId`/`clienteNombre` de la reserva (antes era el ADMIN que lo armó). En el panel, el filtro por cliente trae también los partidos en los que juega.
  - **Un solo monto por partido**: se van `parte`, `pagado`, `bonificado` y `debe` de cada jugador, `jugadorId` de `PagoResponse` y `jugadorId` de los cobros y la bonificación (en un partido ya no se manda; sin `monto`, la bonificación cubre todo lo que falta). Se puede reemplazar a un jugador aunque el partido tenga pagos.
  - **Un partido no tiene seña**: con algo pagado queda `PARCIALMENTE_PAGADA` (nunca `SEÑADA`) hasta que se paga entero.
  - `POST /admin/usuarios`: `categoria` y `posicion` de un CLIENTE pasan a ser opcionales (alta con lo mínimo).
- **Anticipación máxima para reservar** (30 días por defecto):
  - `POST /reservas` (el cliente) responde `400` "Podés reservar hasta el … (n días de anticipación)." si la fecha pasa del tope. El personal (`POST /admin/reservas` y los partidos) no tiene tope.
  - `GET /canchas/{id}/disponibilidad` suma `reservableHasta` (fecha): el último día que el cliente puede reservar, para limitar el calendario.
  - Nuevo `GET/PUT /admin/configuracion/reservas` (ADMIN): `{ "diasAnticipacion": 30 }`, entre 1 y 365.
  - Los partidos de los turnos fijos se generan hasta ese mismo tope (antes, 4 semanas).
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
- **Partidos de 4 jugadores y turnos fijos (ADMIN)**: `POST/PUT /admin/partidos` y `/admin/turnos-fijos` (alta, modificación, baja general y baja de una fecha). Cambios que afectan al front:
  - **Nuevo estado `CONFIRMADA`** (partido sin nada pagado, no vence). Tratalo como confirmado: ocupa la cancha y, si ya se jugó, debe plata (`debeSaldo`).
  - `ReservaResponse` suma `tipo` (`NORMAL` | `PARTIDO`), `turnoFijoId` y `jugadores` (con `parte`, `pagado`, `bonificado` y `debe` por jugador; `[]` en una reserva normal). `PagoResponse` suma `jugadorId`.
  - En un partido, `pago-efectivo`, `pago-presencial` y `bonificacion` **exigen `jugadorId`** (en una reserva normal no se manda: `400`). Un partido no se paga online.
  - `GET /reservas/mias` incluye los partidos en los que juega el usuario (solo lectura: `cancelable: false`, sin teléfonos de los demás). Cancelar un partido es solo del ADMIN (`403` para EMPLEADO).
  - Un cliente que intenta reservar el horario de un turno fijo recibe `409` "Ese horario es un turno fijo del club en esa cancha."; la disponibilidad ya lo muestra ocupado.
  - `GET /admin/reservas` suma el filtro `tipo` y `pendientes=true` incluye `CONFIRMADA`. El resumen cuenta los partidos `CONFIRMADA` como turnos (deuda si ya se jugaron).
- **Bonificaciones (solo ADMIN)**: `POST /pagos/{id}/bonificacion` y `bonificacion` en `POST /reservas` para no cobrarle al cliente todo o una parte. `ReservaResponse` suma `montoBonificado` (`montoPagado` es solo plata); `PagoResponse` suma `motivo`; el provider `BONIFICACION`; el resumen y los totales del listado suman `montoBonificado`.
- **Panel del personal**: `GET /admin/reservas` (listado paginado con filtros por fechas, estado, cliente, cancha y "pendientes", más totales del filtro) y `GET /admin/clientes?buscar=` (búsqueda de clientes), para ADMIN y EMPLEADO. `GET /reservas/saldos-pendientes` queda solo por compatibilidad.
- **Rutas de reservas y pagos separadas por actor** (mismas reglas, respuestas y errores; cambia solo la ruta). `/reservas` queda **solo para el CLIENTE** (el personal recibe `403`) y lo del personal pasa a `/admin/reservas` (un CLIENTE recibe `403`):
  | Antes (personal) | Ahora |
  |---|---|
  | `POST /reservas` con `clienteId` (y `bonificacion`, solo ADMIN) | `POST /admin/reservas` (mismo body) |
  | `GET /reservas/{id}` | `GET /admin/reservas/{id}` |
  | `DELETE /reservas/{id}/cancelar?reembolso=…` | `DELETE /admin/reservas/{id}/cancelar?reembolso=…` |
  | `GET /reservas/saldos-pendientes` | `GET /admin/reservas/saldos-pendientes` |
  | `GET /reservas/devoluciones-pendientes` | `GET /admin/reservas/devoluciones-pendientes` |
  | `POST /reservas/{id}/devolucion` | `POST /admin/reservas/{id}/devolucion` |
  | `POST /pagos/{id}/pago-efectivo` | `POST /admin/reservas/{id}/cobros/efectivo` (mismo body) |
  | `POST /pagos/{id}/pago-presencial` | `POST /admin/reservas/{id}/cobros/presencial` (mismo body) |
  | `POST /pagos/{id}/bonificacion` (ADMIN) | `POST /admin/reservas/{id}/bonificacion` (mismo body) |
  | `POST /admin/partidos` (ADMIN) | `POST /admin/reservas/partidos` (mismo body) |
  | `PUT /admin/partidos/{id}` (ADMIN) | `PUT /admin/reservas/partidos/{id}` (mismo body) |
  | `GET /admin/resumen?desde&hasta` (ADMIN, EMPLEADO) | `GET /admin/reservas/resumen?desde&hasta` (mismos parámetros, respuesta y permisos) |
  | `GET /admin/clientes?buscar&soloActivos` (ADMIN, EMPLEADO) | `GET /admin/usuarios/clientes?buscar&soloActivos` (mismos parámetros, respuesta y permisos) |

  Para el cliente no cambia nada, salvo que en `POST /reservas` ya no se leen `clienteId` ni `bonificacion` (antes daban `400` o `403`; ahora se ignoran) y en `DELETE /reservas/{id}/cancelar` ya no se lee `reembolso` (antes `400`; ahora se aplica la política). `/pagos/{id}/iniciar` y `/pagos/{id}/pago` quedan solo para el CLIENTE (el personal ya no inicia pagos online: `403`); el webhook sigue en `/pagos/notificacion`.
- **Cambio de contrato en `GET /admin/resumen`** (mismos parámetros y permisos): la respuesta pasa a `{ desde, hasta, jugados, porJugar, caja, cancelaciones, productos }`. Se separan los turnos jugados (con su `deuda`) de los por jugar (`aCobrar`), y `caja` es el cierre de caja por fecha de pago. Se sacaron `montoEsperado`, `montoCobrado`, `cobradoOnline`, `cobradoEnClub`, `cobradoEfectivo`, `cobradoMercadoPago`, `montoBonificado`, `montoPendiente`, `retenidoCancelaciones`, `partidosJugados` y `partidosCancelados`. Ver `docs/api.md`.
- **Nuevo endpoint del panel**: `GET /admin/resumen?desde&hasta` (ADMIN cualquier período; EMPLEADO solo hoy, sin fechas) con montos esperados, cobrados (online y en el club), pendientes, retenido por cancelaciones, partidos y productos del período. Ver `docs/api.md`.
- **`PUT /perfil`: `telefono` es opcional.** Si no viene no se toca el celular ni su verificación; el celular se edita en `PUT /perfil/notificaciones`. El mismo número en otro formato no pierde la verificación.
- **Teléfonos viejos normalizados (V13)**: una migración pasó los teléfonos guardados antes de V8 (por ejemplo `1122334455`) al formato de WhatsApp (`5491122334455`) sin tocar la verificación; los que no eran válidos quedaron vacíos y sin verificar (esos usuarios tienen que cargar su celular de nuevo).
- **Celular en uso**: si el celular ya está verificado en otra cuenta activa, el registro y el perfil responden `400 errors.telefono` ("Ese celular ya está verificado en otra cuenta…") y pedir o verificar el código, `409`.
- **Teléfonos**: se aceptan con espacios, guiones y paréntesis, y se devuelven normalizados (`5491123456789`). Un número que no es válido responde `400 errors.telefono`.
- **Consultas públicas**: `GET /canchas`, `GET /canchas/{id}/disponibilidad`, `GET /productos` y `GET /productos/disponibilidad` ya no piden sesión: un visitante puede ver horarios libres y precios. Para reservar sigue haciendo falta iniciar sesión.
- `DELETE /perfil/desactivar` ya no necesita body.
- `pagarTotal` es opcional (por defecto `false` = seña). `iniciar` y `pago` también los pueden usar ADMIN y EMPLEADO.
- Un usuario registrado y no verificado ahora tiene `enabled: true` en el listado del admin (la verificación se ve en `emailVerificado`).
