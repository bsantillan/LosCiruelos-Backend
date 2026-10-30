# Flujos de negocio

## Reservas y pagos

### Vista general

```
                      POST /reservas
                            │
                            ▼
                 ┌─────────────────────┐   15 min sin pago (job, cada 1 min)
                 │      PENDIENTE      │ ─────────────────────────────────────► EXPIRADA
                 │ (expiresAt = +15')  │ ─── cancelar / eligió otro horario ──► CANCELADA
                 └─────────────────────┘
                            │ pago aprobado (efectivo, mostrador, Brick, Wallet, webhook)
                            ▼
      PARCIALMENTE_PAGADA ──► SEÑADA ──► PAGADA      (según cuánto se pagó)
                                                 │
                                                 │ terminó el turno y está totalmente paga
                                                 │ (job cada 10 min, o en el acto si el saldo
                                                 │  se cobra después del turno)
                                                 ▼
                                            COMPLETADA

      Señada o parcial con el turno terminado: queda así hasta que se cobre el saldo.

      Reembolso total de una reserva confirmada ──► CANCELADA
```

### Reglas de concurrencia (por qué no se pisan las operaciones)

- **Crear una reserva** bloquea la fila de la **cancha** (`SELECT … FOR UPDATE`): dos pedidos por la misma cancha se atienden de a uno.
- **Toda operación sobre una reserva existente** (iniciar pago, cobrar, webhook, pago manual, cancelar, expirar) bloquea primero la fila de la **reserva** y trabaja sobre sus pagos ya bloqueados. Así el cobro con tarjeta, la notificación de Mercado Pago, el job de expiración y el empleado que cobra en efectivo nunca se pisan.
- **Garantía final en la base**: la restricción `reservas_sin_solapamiento` (PostgreSQL, `EXCLUDE USING gist`) impide dos reservas activas superpuestas en la misma cancha, aunque algo se escape del código. Si salta, la API responde `409`.

### 1. Consultar disponibilidad — `GET /canchas/{id}/disponibilidad?fecha=&duracion=`

- Devuelve el horario del día, los turnos ocupados (`slotsOcupados`) y los **turnos libres de la duración pedida** (`turnosDisponibles`), calculados con las mismas reglas que la creación de reservas.
- Si la fecha es hoy, el primer turno es el próximo bloque de 30 min contado desde la apertura (`horarioMinimoInicio`).
- **Ocupa el turno** toda reserva que no esté `CANCELADA` ni `EXPIRADA`, salvo una `PENDIENTE` vencida sin pagos aprobados ni en proceso: esa ya no bloquea, aunque el job todavía no la haya marcado. Es la misma definición que usa la creación.

### 2. Crear la reserva — `POST /reservas` (cliente) o `POST /admin/reservas` (personal)

Validaciones, en orden:
1. **Quién**: un CLIENTE reserva para sí mismo en `/reservas` (el cliente sale del token). ADMIN y EMPLEADO reservan en `/admin/reservas` e indican `clienteId`, que tiene que ser un CLIENTE habilitado. PROFESOR no reserva.
2. El cliente tiene el **perfil completo** (nombre, apellido, teléfono, categoría, posición).
3. **Horario**: fecha hoy o futura; si es hoy, el inicio no pasó; inicio < fin; duración múltiplo de 30 y entre el mínimo y el máximo configurados (90–180); dentro del horario del día; inicio alineado a bloques de 30 min desde la apertura.

Después, con la cancha bloqueada:
1. Las **PENDIENTE vencidas** que se superponen se marcan `EXPIRADA` (y sus pagos iniciados `EXPIRADO`).
2. Si el mismo cliente ya tiene una **PENDIENTE vigente con la misma cancha, fecha e inicio**, se interpreta como un cambio: se actualizan fin, cantidades de productos (con su stock) y monto. Antes se verifica que el nuevo horario no choque con otra reserva y que no haya un pago en curso. Si cambió el monto, el pago iniciado se descarta.
3. Las **otras PENDIENTE vigentes del cliente** en esa cancha y fecha, sin pagos activos, se cancelan (el cliente eligió otro horario).
4. Si el turno está ocupado → `409`.
5. **Stock**, en un solo paso: se devuelve el stock de las reservas que se expiraron o cancelaron en los pasos anteriores y se toman los productos pedidos (ver [Productos y stock](#productos-y-stock)). Sin stock → `409`.
6. Se guarda en `PENDIENTE` con `expiresAt = ahora + 15 min`.

**Monto de la cancha** = `montoReserva + montoXMediaHora × (duración / 30)`. Con la configuración inicial, 90 min = $15 000.
**Monto de productos** = `cantidadPelotas × precio de Pelotas + cantidadPaletas × precio de Paletas` (precios del momento, congelados en la reserva).
**Total** = cancha + productos. **Seña** = `total × porcentajeSenia / 100`. Ej.: 90 min + 2 pelotas a $1 500 + 3 paletas a $2 000 → total $24 000, seña $12 000.

Si cualquier paso falla, la transacción se revierte entera: no quedan reservas canceladas a medias.

### 3. Pagar online con Mercado Pago

#### 3.1 Iniciar — `POST /pagos/{reservaId}/iniciar?pagarTotal=true|false`
- Se puede iniciar si la reserva está `PENDIENTE` y dentro del plazo, o si está `SEÑADA` o `PARCIALMENTE_PAGADA` (también después del turno, para pagar el saldo).
- Monto:
  - `pagarTotal=true` → todo lo que falta pagar.
  - `pagarTotal=false` → lo que falta para completar la seña. Si la seña ya está paga responde `409`.
- Crea un `Pago` `PENDIENTE` y una **preferencia** de MP con `external_reference = id de la reserva` y `metadata.pago_id = id del pago`. Devuelve el `preference_id` en `paymentToken`, para inicializar el Payment Brick (tarjetas) y el Wallet Brick (cuenta de Mercado Pago).
- La preferencia lleva:
  - `back_urls` a `FRONTEND_URL/pago/resultado?reservaId=…&resultado=aprobado|rechazado|pendiente`, y `auto_return` si el front está en `https`;
  - `binary_mode`: el pago queda aprobado o rechazado en el momento, nunca pendiente;
  - si la reserva está `PENDIENTE`, **vence junto con la reserva** (`expiration_date_to = expiresAt`): MP no deja pagar fuera de plazo.
- Si ya había un pago iniciado por el mismo monto lo devuelve (el usuario recargó la página). Si era por otro monto, lo descarta (`EXPIRADO`) y crea uno nuevo.
- Si hay un pago `EN_PROCESO` responde `409`.
- Si MP falla responde `502` y no se guarda nada.

#### 3.2 Cobrar con tarjeta — `POST /pagos/{reservaId}/pago`
- Body: el `formData` del Brick (`token`, `payment_method_id`, `installments`, `issuer_id`, `payer`).
- **El monto lo pone el servidor** (`pago.monto`): `transaction_amount` del front se ignora.
- Se envía con `X-Idempotency-Key` propio del pago: si el pedido se repite (doble click, reintento), MP no cobra dos veces. Además el lock de la reserva hace que el segundo pedido ya no encuentre el pago `PENDIENTE`.
- Resultado:

| MP responde | Pago | Reserva |
|---|---|---|
| `approved` | `APROBADO` | se recalcula (`SEÑADA` / `PAGADA` / …) |
| `rejected`, `cancelled` | `RECHAZADO` | sin cambios (se puede iniciar otro pago) |
| `pending`, `in_process`, `in_mediation`, `authorized` | `EN_PROCESO` | sigue `PENDIENTE` pero **no expira** mientras MP decide |

- La respuesta incluye `estadoReserva` y `detalle` (motivo de MP, ej. `cc_rejected_insufficient_amount`).

#### 3.2 bis Pagar con la cuenta de Mercado Pago — Wallet Brick
- El front muestra el botón "Pagar con Mercado Pago" (Wallet Brick) con el mismo `preferenceId` y `redirectMode: 'blank'`: **Mercado Pago se abre en otra pestaña** (o en la app de MP en el celular) y la web queda abierta. Mercado Pago no ofrece un modo popup.
- El pago lo crea Mercado Pago, no el backend: el backend se entera por el **webhook** (por eso en local hace falta ngrok) y busca el `Pago` por `metadata.pago_id`.
- La pestaña de MP vuelve a `FRONTEND_URL/pago/resultado`. La pestaña original consulta `GET /reservas/{id}` cada pocos segundos hasta que la reserva cambia de estado.

#### 3.3 Notificación de Mercado Pago — `POST /pagos/notificacion` (webhook, público)
- Acepta los formatos de MP: `?type=payment&data.id=…`, el mismo dato en el body JSON, o IPN `?topic=payment&id=…`. Otros tipos (`merchant_order`, etc.) responden `200` y se ignoran.
- Si viene `x-signature` y está configurado `MP_WEBHOOK_SECRET`, se valida la firma HMAC; si no coincide → `401`.
- **Nunca se confía en el contenido de la notificación**: se consulta el pago a la API de MP con nuestro access token y se aplica ese resultado.
- El `Pago` se encuentra por `external_payment_id` o, si todavía no lo tiene (pagos con el botón de MP / Wallet), por `metadata.pago_id`.
- Reglas al aplicar el resultado (idempotentes: notificaciones repetidas o fuera de orden no rompen nada):
  - **Aprobado** sobre una reserva `CANCELADA` o `EXPIRADA`, o por un monto distinto al esperado → pago `A_REEMBOLSAR` (se loguea un warning/error: hay que devolver el dinero). La reserva **no** revive.
  - **Aprobado** normal → `APROBADO` y se recalcula la reserva.
  - **Rechazado** → el pago queda `RECHAZADO`; la reserva **no se toca** (si ya estaba señada, sigue señada).
  - **Devuelto / contracargo** → pago `DEVUELTO`; si la reserva queda sin dinero aprobado, pasa a `CANCELADA`.
  - Un pago que ya está `APROBADO`, `DEVUELTO`, `A_REEMBOLSAR` o `REEMBOLSO_PENDIENTE` no vuelve atrás (un `REEMBOLSO_PENDIENTE` sí pasa a `DEVUELTO` si MP avisa que se devolvió).
- Si MP dice que el pago no existe (por ejemplo, "Simular notificación" del panel, que usa un id ficticio) → `200` y un warning en el log, para que MP no reintente.
- Otros errores de MP al consultar → `502`, para que MP reintente la notificación.

### 4. Pagar en el club — `POST /admin/reservas/{reservaId}/cobros/efectivo` y `/cobros/presencial`
- Solo ADMIN y EMPLEADO (controlado por URL y por `@PreAuthorize`).
- Body `{ "monto": 7500 }`: mayor a 0, como máximo lo pendiente, hasta 2 decimales.
- `cobros/efectivo` registra `provider = MANUAL`; `cobros/presencial` registra `MERCADO_PAGO` (tarjeta o QR en el mostrador).
- Se acepta en reservas `PENDIENTE` dentro del plazo, `SEÑADA` y `PARCIALMENTE_PAGADA`, también después del turno (el saldo se suele cobrar después de jugar).
- Si el pago deja la reserva **totalmente paga y el turno ya terminó**, pasa a `COMPLETADA` en el acto. Si se paga antes o durante el turno, queda `PAGADA` y la completa el job cuando termina.
- Si hay un pago online `EN_PROCESO` responde `409`, para evitar cobrar dos veces.
- Si había un pago online iniciado y sin cobrar, se descarta.

### 4 bis. Bonificar (no cobrar) — `POST /admin/reservas/{reservaId}/bonificacion`, solo ADMIN
- El dueño a veces no le cobra a un cliente (socio, torneo, cortesía). Puede bonificar **todo o una parte**, al crear la reserva (campo `bonificacion` en `POST /admin/reservas`, todo en una transacción) o después.
- Body opcional `{ "monto": 7500, "motivo": "socio" }`: sin `monto` bonifica todo lo pendiente; más que lo pendiente → `400`. EMPLEADO o CLIENTE → `403`.
- Se acepta en las mismas reservas que un pago en el club, con el mismo `409` si hay un pago online en proceso.
- Queda un pago `provider = BONIFICACION`, `APROBADO`, con `registrado_por_id` y `motivo`. Cubre el monto para el estado de la reserva, pero **no es plata**: no suma en `montoPagado` ni en lo cobrado del resumen y del listado (va en `montoBonificado`), no se devuelve al cancelar y no manda aviso.

### 4 ter. Partidos de 4 jugadores y turnos fijos — solo ADMIN
- **Armar un partido** (`POST /admin/reservas/partidos`): el ADMIN elige cancha, fecha, horario y 4 jugadores, cada uno un cliente registrado o un invitado (nombre y teléfono opcional). Mismas reglas de horario, solapamiento y stock que una reserva normal. Queda `CONFIRMADA`: no vence y ocupa la cancha aunque nadie haya pagado. Su "cliente" es el ADMIN.
- **Cobrar**: en el club, cada pago (o bonificación) se registra a nombre de un jugador (`jugadorId`). Cada jugador tiene su parte (total / 4) y la respuesta muestra cuánto pagó y cuánto debe; uno puede pagar por otros. El estado de la reserva sigue la tabla de abajo con lo pagado entre todos. No se paga online y no se le avisa al ADMIN de cada pago.
- **Modificar** (`PUT /admin/reservas/partidos/{id}`): jugadores por posición (no se reemplaza a quien ya pagó), cancha, horario y productos, mientras no se haya jugado. El total no puede quedar por debajo de lo pagado.
- **Cancelar**: solo el ADMIN, con `DELETE /admin/reservas/{id}/cancelar` (reembolso si hay plata). Se avisa a los jugadores registrados.
- **Jugadores registrados**: ven el partido en "Mis reservas" y su detalle, sin poder pagarlo ni cancelarlo; los que son clientes reciben el recordatorio (si lo tienen activado) con lo que les falta de su parte.
- **Turno fijo** (`/admin/turnos-fijos`): el mismo partido todas las semanas (el día de `desde`). Bloquea el horario siempre, también en la disponibilidad. Un job crea cada hora los partidos de las próximas 4 semanas (cada uno se paga aparte); si una fecha está ocupada se informa y se reintenta.
  - **Baja de una fecha**: se cancela ese partido (o se registra la fecha como cancelada si todavía no se creó) y el horario queda libre ese día.
  - **Modificar**: los partidos futuros sin pagos se vuelven a crear con los datos nuevos; los que tienen pagos quedan como estaban.
  - **Baja general**: se cancelan los partidos que no empezaron (con reembolso si alguno tiene plata) y el horario se libera.

### 5. Estado de la reserva según lo pagado

Se recalcula siempre a partir de los pagos `APROBADO` (plata más bonificaciones), nunca se asigna a mano:

| Pagado + bonificado | Estado |
|---|---|
| 0 | `PENDIENTE` (o `CANCELADA` si estaba confirmada y se devolvió todo). Un partido queda `CONFIRMADA` |
| más de 0 y menos que la seña | `PARCIALMENTE_PAGADA` |
| la seña o más, menos que el total | `SEÑADA` |
| el total | `PAGADA`, o `COMPLETADA` si el turno ya terminó |

`CANCELADA`, `EXPIRADA` y `COMPLETADA` no cambian por pagos. Una reserva `COMPLETADA` siempre está totalmente paga.

### 6. Cancelar — `DELETE /reservas/{id}/cancelar` (cliente) o `DELETE /admin/reservas/{id}/cancelar` (personal)
- **Quién**: el dueño de la reserva, o ADMIN/EMPLEADO (un partido, solo el ADMIN). Se pueden cancelar reservas `PENDIENTE`, `CONFIRMADA`, `SEÑADA`, `PARCIALMENTE_PAGADA` y `PAGADA`, sin pagos en proceso.
- **Cliente**: puede cancelar hasta que empieza el turno y el reembolso lo define la política (configurable en `configuracion`):

| Anticipación | Reembolso |
|---|---|
| ≥ `minutosReembolsoTotal` (1440 = 24 h) | todo lo pagado |
| ≥ `minutosReembolsoSenia` (720 = 12 h) | la seña (o lo pagado, si es menos) |
| menos | nada |

- **Personal**: puede cancelar hasta que termina el turno y elige el reembolso (`TOTAL`, `SENIA` o `NADA`), porque a veces la cancelación es por el club.
- **Cómo se devuelve**: primero los pagos online de Mercado Pago (reembolso por API, total o parcial, con clave de idempotencia; quedan `DEVUELTO`), después los cobrados en el club (quedan `A_REEMBOLSAR`, a devolver a mano).
- **Si MP no acepta el reembolso** (por ejemplo, un pago recién cobrado que todavía se está acreditando), la reserva se cancela igual (`200`) y ese pago queda `REEMBOLSO_PENDIENTE`. El job de reembolsos lo reintenta cada 2 minutos, con la reserva bloqueada y la misma clave de idempotencia (MP nunca devuelve dos veces):
  - antes de reintentar consulta el pago en MP: si ya figura devuelto ese monto (un intento anterior que respondió con error, o una devolución desde el panel), queda `DEVUELTO` sin pedir otro reembolso;
  - si el reembolso sale → `DEVUELTO`;
  - si MP tiene devuelto otro monto, el pago no existe en MP, o pasan 24 h sin lograrlo → `A_REEMBOLSAR` y un `log.error`, para devolverlo a mano.
- La reserva pasa a `CANCELADA`, los pagos iniciados sin cobrar pasan a `EXPIRADO` y vuelve el stock de pelotas.
- El front no replica la regla: cada `ReservaResponse` trae `cancelable`, `cancelableHasta`, `montoReembolso`, `reembolsoTotalHasta` y `reembolsoSeniaHasta`.

### 6 ter. Avisos por WhatsApp o mail
- **A los ADMIN** (cada uno elige cuáles en `PUT /perfil/avisos`; se guarda en su perfil de ADMIN): reserva confirmada (llega por primera vez a la seña o al total), saldo cobrado, reserva cancelada (quién, cuánto se devuelve, qué queda para devolver a mano) y reembolso con problemas (pago `A_REEMBOLSAR`).
- **Registro**: aceptar recibir notificaciones (`aceptarNotificacion`) es obligatorio; arrancan por mail. Al completar el perfil se valida el celular y ahí se puede cambiar a WhatsApp.
- **El cliente elige** (con el perfil completo) si recibe recordatorios y novedades/promociones (`PUT /perfil/suscripciones`; se guarda en su perfil de jugador); las cancelaciones se le avisan siempre. Los recordatorios y las promociones son **solo para clientes**: un jugador de un partido que no es cliente (por ejemplo, un EMPLEADO) no recibe el recordatorio.
- **Al cliente**: recordatorio 2 h antes del turno (configurable en `PUT /admin/configuracion/avisos`) y aviso cuando **el club** le cancela una reserva. Si cancela él, no se le avisa: ya lo ve en la página.
- **Canal**: cada usuario elige mail o WhatsApp en su perfil (o con el checkbox del registro). WhatsApp se usa solo con el teléfono verificado por código; si no, o si WhatsApp falla, va por mail. Nunca por los dos.
- Se envían después del commit y en otro hilo: no demoran la respuesta y un error de envío no afecta a la reserva. Detalle en [whatsapp.md](whatsapp.md).

### 6 bis. Mis reservas — `GET /reservas/mias`
Paginado (`page`, `size` hasta 50) y con filtros: `tipo=proximas|historial`, `estado`, `desde`, `hasta`. **Próximas** usa el mismo criterio que la disponibilidad (el turno no terminó y la reserva ocupa la cancha); **historial** es todo lo demás.
Se ocultan siempre las **descartadas**: canceladas o vencidas sin ningún pago que haya movido plata (aprobado, devuelto, con reembolso pendiente, a reembolsar o en revisión; los iniciados sin cobrar, rechazados o vencidos no cuentan). También se ocultan al filtrar por `estado=CANCELADA` o `EXPIRADA`, y no cuentan en `totalElementos`. No se borran (sirven para auditoría, estadísticas y reclamos): solo se ven con `incluirDescartadas=true`. Una cancelada con reembolso sí se muestra siempre.

### 7. Expirar
- **Job** (cada 1 minuto): toma las `PENDIENTE` con plazo vencido y expira cada una en su propia transacción, revisando con la reserva bloqueada que siga vencida y sin pagos activos. Si una falla, sigue con las demás.
- **`POST /reservas/{id}/expirar`** (dueño o staff): lo mismo para una reserva puntual, por ejemplo cuando el contador del front llega a cero.
- **Al crear otra reserva** sobre ese horario (paso 2.1).

### 8. Completar
- **Job (cada 10 minutos)**: las reservas `PAGADA` cuyo turno ya terminó pasan a `COMPLETADA`.
- **Pago del saldo después del turno** (en el club u online): la reserva pasa a `COMPLETADA` en el acto.
- Las reservas `SEÑADA` o `PARCIALMENTE_PAGADA` **no** se completan solas, para no esconder una deuda: quedan así hasta que se cobre el saldo. Mientras tanto la respuesta trae `turnoTerminado: true` y `debeSaldo: true` (el cliente la ve como "jugada con saldo pendiente"), y el personal las ve en `GET /reservas/saldos-pendientes` para cobrarlas o registrar el pago que se olvidó cargar.
- De las reservas `COMPLETADA` salen las estadísticas del perfil (partidos jugados).

### 9. Devolución de paletas — personal del club
- `GET /reservas/devoluciones-pendientes`: reservas cuyo turno ya empezó y tienen paletas sin devolver.
- `POST /reservas/{id}/devolucion` con `{ "paletasDevueltas": 3 }`: registra cuántas volvieron. Se puede registrar una sola vez, desde que empieza el turno, en reservas señadas, parciales, pagadas o completadas.
- Si vuelven menos de las alquiladas, las faltantes (rotas o perdidas) **se descuentan del stock total** y queda un warning en el log.
- **Mientras no se registre la devolución**, las paletas de un turno terminado cuentan como afuera del club y no se pueden volver a alquilar. Ejemplo: 4 paletas en total, 3 de un turno de las 18:00 sin devolver → a las 20:00 solo se ofrece 1.

### 10. Consultas
- `GET /reservas/mias`: reservas del usuario, de la más reciente a la más antigua.
- `GET /reservas/{id}`: detalle con sus pagos (dueño o staff). El front la consulta para saber cómo terminó un pago `EN_PROCESO`.

### Productos y stock

Pelotas y paletas son productos del catálogo (`/admin/configuracion/productos`), con precio por unidad y stock. El admin puede sumar otros productos al catálogo (por ejemplo, agua), aunque por ahora la reserva solo pide pelotas y paletas.

**Quién maneja el stock**: el ADMIN crea y edita productos (nombre, precio, tipo, stock). El EMPLEADO puede ver el catálogo y ajustar el stock (`PATCH …/stock`, por ejemplo +12 al reponer o −1 por una rotura) y registra las devoluciones de paletas.

| Tipo | Qué significa el stock | Al reservar | Al cancelar o expirar | Al terminar el turno |
|---|---|---|---|---|
| `VENTA` (pelotas) | unidades disponibles hoy | baja | vuelve | no vuelve (se entregaron) |
| `ALQUILER` (paletas) | unidades totales del club | no cambia; se controla el uso en ese horario | se libera sola (la reserva deja de contar) | quedan "afuera" hasta que el personal registra la devolución; las faltantes bajan el stock |

- **Alquiler**: las unidades libres para un horario son `stock − máximo en uso al mismo tiempo − unidades de turnos terminados sin devolver`, sumando **todas las canchas**. Se cuentan las reservas que ocupan su turno (las mismas que bloquean la cancha). Ejemplo con 4 paletas: si hay 2 alquiladas de 18:00 a 19:30 y 2 de 19:30 a 21:00, para 18:30 a 20:00 quedan 2, porque nunca se usan las 4 juntas.
- **Momento**: el stock se toma al crear la reserva `PENDIENTE`, así quien está pagando lo tiene asegurado. Si la reserva se cancela, expira o se reembolsa entera, se devuelve.
- **Concurrencia**: los productos se bloquean siempre después de las reservas y en orden de id, todos juntos en un solo paso (`ProductoService.moverStock`). Dos pedidos simultáneos por las últimas unidades: uno recibe `201` y el otro `409`.
- **Precio congelado**: cada ítem guarda el precio unitario del momento; si el admin cambia el precio, las reservas existentes no cambian. Si el cliente modifica la reserva, se toma el precio vigente.

### Estados del pago

| Estado | Significado |
|---|---|
| `PENDIENTE` | Iniciado (preferencia creada), sin cobrar |
| `APROBADO` | Cobrado |
| `RECHAZADO` | MP lo rechazó o se canceló |
| `EN_PROCESO` | MP todavía lo está procesando |
| `DEVUELTO` | Reembolsado o contracargo |
| `EXPIRADO` | Nunca se cobró y quedó sin efecto (reserva vencida o cancelada, cambio de monto, pago manual) |
| `A_REEMBOLSAR` | Hay que devolver `montoReembolsado` a mano: pago en el club de una reserva cancelada, cobro online sobre una reserva inactiva o por un monto distinto, o reembolso de MP que no se pudo hacer en 24 h |
| `REEMBOLSO_PENDIENTE` | Se canceló la reserva pero MP todavía no aceptó el reembolso de `montoReembolsado`; el job lo reintenta cada 2 minutos |

Cada pago tiene una `clave_idempotencia` (UUID que se genera al crearlo y no cambia). Las operaciones con MP la usan como `X-Idempotency-Key`: `losciruelos-pref-{uuid}`, `losciruelos-cobro-{uuid}` y `losciruelos-reembolso-{uuid}`. No se usa el id del pago porque, si la base se recrea, los ids se repiten y MP respondería con el resultado de un pedido viejo.

---

## Usuarios y autenticación

### Significado de los flags del usuario
- `emailVerificado`: el usuario confirmó su email (código o Google).
- `enabled`: la cuenta **no está desactivada**. Solo pasa a `false` cuando el admin o el propio cliente la desactivan.

### Registro local
1. `POST /auth/register` valida email libre, contraseña fuerte (8+ caracteres, minúscula, mayúscula, número y especial) y términos.
2. Crea el usuario (CLIENTE, `emailVerificado=false`, `enabled=true`) y su perfil de jugador, en una transacción.
3. Después del commit envía un código de 6 dígitos (24 h). Si el envío falla, se loguea y el registro queda hecho; el usuario puede pedir otro código.
4. `POST /auth/verificar-token` marca el email como verificado.

### Códigos (verificación y reset)
- 6 dígitos generados con `SecureRandom`. Hay un solo código vigente por usuario y tipo.
- **Máximo 5 intentos fallidos**: después el código queda bloqueado y hay que pedir otro.
- Reset de contraseña: pedir código (15 min) → verificarlo → confirmar la nueva contraseña. Al cambiarla se cierran todas las sesiones.

### Login
- Local: contraseña → email verificado (`403 email_verificado`) → cuenta habilitada (`403`).
- Google: el `idToken` se valida contra `GOOGLE_CLIENT_ID` y Google tiene que confirmar el email. Si el email no existe, se crea un CLIENTE verificado con perfil vacío (`perfilCompleto: false`). Si existe y no estaba verificado, queda verificado.
- `perfilCompleto`: el CLIENTE necesita nombre, apellido, **celular verificado** (código por WhatsApp), categoría y posición; el ADMIN, nombre, apellido y celular verificado; EMPLEADO y PROFESOR siempre `true`. Un CLIENTE sin perfil completo no puede reservar (`403`), tampoco si el personal reserva por él.
- Tokens: access JWT de 15 min y refresh de 7 días, uno por usuario (iniciar sesión en otro lado cierra la anterior). `/auth/refresh` rota el refresh token y rechaza cuentas desactivadas.

### Límite de intentos
Por IP y por minuto: login 10, Google 20, registro 5, verificar código 10, reenviar código 3, pedir reset 3, confirmar reset 5, refresh 30. Al superarlo responde `429` con `Retry-After`.

## Perfil de jugador
- Nombre, apellido, teléfono y posición se pueden cambiar siempre.
- **Categoría**: se puede cambiar **una sola vez y dentro de las 24 h** desde que se cargó por primera vez. La primera carga (por ejemplo, después de entrar con Google) no cuenta como cambio. Después solo la cambia el club (admin). `categoriaEditable` en `GET /perfil` indica si todavía se puede.
- Primero se valida todo y después se guarda, en una transacción: si la categoría no se puede cambiar, no se guarda nada.
- Estadísticas: `cantPartidos` y `cantPartidosEsteMes` cuentan reservas `COMPLETADA`; `diasDesdeUltimoPartido` es `null` si nunca jugó.
- `DELETE /perfil/desactivar` desactiva la cuenta y cierra todas las sesiones.

## Configuración del club

Además de la tabla de abajo, la configuración incluye la sección **Stock de productos** (ver [Productos y stock](#productos-y-stock)).

Registro único (id = 1) creado por `DataInitializer`:

| Campo | Inicial | Uso |
|---|---|---|
| `montoReserva` | 0 | monto fijo por reserva |
| `montoXMediaHora` | 5000 | precio por bloque de 30 min |
| `porcentajeSenia` | 50 | % del total que es seña |
| `duracionMinimaTurno` / `duracionMaximaTurno` | 90 / 180 | minutos |
| días | L‑V 08–23, Sáb 09–22, Dom 09–20 | horario por día |

Se guarda en caché en memoria; si se cambia directamente en la base hay que reiniciar la app (todavía no hay endpoint para editarla).
