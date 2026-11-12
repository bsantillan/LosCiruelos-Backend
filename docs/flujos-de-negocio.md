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
- `reservableHasta` dice hasta qué día puede reservar el cliente (ver el punto 2).
- **Ocupa el turno** toda reserva que no esté `CANCELADA` ni `EXPIRADA`, salvo una `PENDIENTE` vencida sin pagos aprobados ni en proceso: esa ya no bloquea, aunque el job todavía no la haya marcado. Es la misma definición que usa la creación.

### 2. Crear la reserva — `POST /reservas` (cliente) o `POST /admin/reservas` (personal)

Validaciones, en orden:
1. **Quién**: un CLIENTE reserva para sí mismo en `/reservas` (el cliente sale del token). ADMIN y EMPLEADO reservan en `/admin/reservas` e indican `clienteId`, que tiene que ser un CLIENTE habilitado. PROFESOR no reserva.
**Cómo se paga una reserva que carga el personal** (`modalidadPago`): `EN_EL_CLUB` (por defecto) vence a los 15 minutos si no se cobra la seña; `SENIA_ONLINE` vence a los `minutosPagoSeniaOnline` de la configuración (nunca después del inicio del turno) y se le manda al cliente el link para pagar la seña (`TipoAviso.SENIA_PENDIENTE`; el link lo arma `AvisosService` con `FRONTEND_URL`; por WhatsApp va en el botón de la plantilla y por mail, al final del texto); `SIN_SENIA` (solo ADMIN) queda `CONFIRMADA` sin pagos, como un partido (`Reserva.confirmadaSinPagar`: no se cancela por no tener nada pagado).

2. Si reserva el cliente, tiene el **perfil completo** (nombre, apellido, teléfono, categoría, posición). Si la carga el personal no hace falta: alcanza con que sea un CLIENTE activo (por ejemplo, uno que el ADMIN acaba de dar de alta). Sus avisos van por el canal que pueda usar (mail si no verificó el WhatsApp) y nunca cortan la creación.
3. **Anticipación** (solo el cliente): la fecha no puede pasar de hoy + los días de anticipación de la configuración (30 por defecto; el ADMIN los cambia en `PUT /admin/configuracion/reservas`). Si no, `400` "Podés reservar hasta el … (n días de anticipación).". El personal no tiene tope: puede cargar reservas y partidos más lejanos.
4. **Horario**: fecha hoy o futura; si es hoy, el inicio no pasó; inicio < fin; duración múltiplo de 30 y entre el mínimo y el máximo configurados (90–180); dentro del horario del día; inicio alineado a bloques de 30 min desde la apertura.

Después, con la cancha bloqueada:
1. Las **PENDIENTE vencidas** que se superponen se marcan `EXPIRADA` (y sus pagos iniciados `EXPIRADO`).
2. Si el mismo cliente ya tiene una **PENDIENTE vigente con la misma cancha, fecha e inicio**, se interpreta como un cambio: se actualizan fin, cantidades de productos (con su stock) y monto. Antes se verifica que el nuevo horario no choque con otra reserva y que no haya un pago en curso. Si cambió el monto, el pago iniciado se descarta.
3. Las **otras PENDIENTE vigentes del cliente** en esa cancha y fecha, sin pagos activos, se cancelan (el cliente eligió otro horario).
4. Si el turno está ocupado → `409`.
5. **Stock**, en un solo paso: se devuelve el stock de las reservas que se expiraron o cancelaron en los pasos anteriores y se toman los productos pedidos (ver [Productos y stock](#productos-y-stock)). Sin stock → `409`.
6. Se guarda en `PENDIENTE` con `expiresAt = ahora + 15 min`.

**Monto de la cancha** = `montoReserva + montoXMediaHora × ((duración − duracionMinimaTurno) / 30)`: `montoReserva` es el precio del turno mínimo y se suma `montoXMediaHora` por cada media hora de más. Con la configuración inicial (mínimo 90 minutos, $15 000 y $5 000), 90 min = $15 000, 120 = $20 000 y 180 = $30 000. Vale igual para reservas, partidos, partidos de turnos fijos y el `montoCancha` de la disponibilidad (todos usan `Configuracion.montoTurno`). Un turno más corto que el mínimo (uno viejo, si el ADMIN subió el mínimo) cuesta `montoReserva`. Las reservas creadas guardan su monto: un cambio de precio no las toca.
**Monto de productos** = `Σ cantidad × precio de cada producto pedido` (precios del momento, congelados en la reserva). El cliente pide solo los que se ofrecen al reservar; el personal, cualquiera activo.
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

### 4. Pagar en el club — `POST /admin/reservas/{reservaId}/cobros/efectivo`, `/cobros/presencial` y `/cobros/cuenta-dni`
- Solo ADMIN y EMPLEADO (controlado por URL y por `@PreAuthorize`). El EMPLEADO solo cobra reservas de la jornada en curso en adelante (`Reserva.exigirQueLaPuedaOperar`, desde `ReservaService.bloquearComoPersonal`); una anterior es del ADMIN (`403`). La jornada del día D va de la apertura de D a la apertura de D+1 según el horario semanal (`Configuracion.jornadaEn`, `JornadaService.actual`): pasada la medianoche sigue la del día anterior.
- Body `{ "monto": 7500 }`: mayor a 0, como máximo lo pendiente, hasta 2 decimales.
- `cobros/efectivo` registra `provider = MANUAL`; `cobros/presencial` registra `MERCADO_PAGO` cargado a mano; `cobros/cuenta-dni` registra `CUENTA_DNI`.
- **Medios de pago**: para la caja solo hay tres, efectivo, Mercado Pago y Cuenta DNI. Lo que no es efectivo es plata que entra a una cuenta, sin importar si fue online o en el club.
- **Los mismos 4 medios en reservas y ventas**: efectivo, Mercado Pago con el QR de la caja, Mercado Pago por transferencia (alias o CVU del club, `TRANSFERENCIA_MP`) y Cuenta DNI. La transferencia se registra a mano (`cobros/transferencia`; `cobros/presencial` es un alias deprecado) y, como Cuenta DNI, el ADMIN la verifica contra la cuenta (`GET /admin/pagos/verificables`). En la caja y en los totales, Mercado Pago suma todo (online, QR y transferencia) con el detalle de cada canal.
- **Cuenta DNI** (Banco Provincia) no tiene API: el personal cobra desde la app del banco y registra el pago, que cuenta en la caja en el momento pero queda **sin verificar**. El ADMIN controla la cuenta del club (`GET /admin/pagos/cuenta-dni?verificado=false`, con los pagos de reservas y las ventas sueltas juntos) y marca cada cobro como verificado; solo él puede marcarlo o desmarcarlo. Si después se cancela con reembolso, el pago queda `A_REEMBOLSAR` y se devuelve a mano.
- Se acepta en reservas `PENDIENTE` dentro del plazo, `SEÑADA` y `PARCIALMENTE_PAGADA`, también después del turno (el saldo se suele cobrar después de jugar).
- Si el pago deja la reserva **totalmente paga y el turno ya terminó**, pasa a `COMPLETADA` en el acto. Si se paga antes o durante el turno, queda `PAGADA` y la completa el job cuando termina.
- Si hay un pago online `EN_PROCESO` responde `409`, para evitar cobrar dos veces.
- Si había un pago online iniciado y sin cobrar, se descarta.

### 4 quater. Cobrar con el QR de la caja de Mercado Pago — `/admin/reservas/{reservaId}/cobros/qr`
- El club tiene **un QR de Mercado Pago impreso en el mostrador**. El personal le asigna a la caja la seña, el saldo o una parte de una reserva; el cliente escanea el QR, ve ese monto en su app y paga. El pago queda `EN_PROCESO` con la order de Mercado Pago y vence a los 10 minutos.
- **Un cobro a la vez**: como hay un solo QR, mientras la caja espera el pago de una reserva no se puede asignar otro cobro (`409`, con el monto y hasta qué hora), aunque sea de otra reserva o lo pidan dos empleados al mismo tiempo. Un cobro vencido que todavía no resolvió el job se resuelve en el momento y la caja queda libre.
- **Mientras el cobro está activo**, la reserva tiene un pago en proceso. No se puede cobrar de otra forma, cambiar los productos ni cancelar la reserva, y la reserva no expira: así el saldo no cambia mientras el cliente paga. Pedirlo otra vez por el mismo monto devuelve el mismo cobro; por otro monto, `409`.
- **Confirmación**: el webhook (tipo `order`) o, si no llega, el job de cada minuto consultan la order a Mercado Pago, con la reserva bloqueada; nunca se usa lo que trae el aviso.
  - Cobrada por el mismo monto → `APROBADO` y la reserva se recalcula (seña, parcial o paga).
  - Otro monto, o la reserva ya cancelada → `A_REEMBOLSAR`, con aviso a los ADMIN.
  - Vencida o cancelada → `EXPIRADO`.
  - Un aviso repetido no cambia nada.
- **Cerrar el modal** cancela la order y libera la caja. Si ya se pagó, se registra el cobro.
- **Reembolso**: al cancelar con reembolso, el cobro con QR se devuelve por la API de Mercado Pago, igual que un pago online (con reintentos). En la caja cuenta como `mercadoPago`.
- **Pendiente con Mercado Pago**: la documentación no confirma que el QR se pueda pagar desde Cuenta DNI u otros bancos. Para Cuenta DNI está el cobro a mano, que después verifica el ADMIN (ver [mercado-pago-qr.md](mercado-pago-qr.md)).

### 4 bis. Bonificar (no cobrar) — `POST /admin/reservas/{reservaId}/bonificacion`, solo ADMIN
- El dueño a veces no le cobra a un cliente (socio, torneo, cortesía). Puede bonificar **todo o una parte**, al crear la reserva (campo `bonificacion` en `POST /admin/reservas`, todo en una transacción) o después.
- Body opcional `{ "monto": 7500, "motivo": "socio" }`: sin `monto` bonifica todo lo pendiente; más que lo pendiente → `400`. EMPLEADO o CLIENTE → `403`.
- Se acepta en las mismas reservas que un pago en el club, con el mismo `409` si hay un pago online en proceso.
- Queda un pago `provider = BONIFICACION`, `APROBADO`, con `registrado_por_id` y `motivo`. Cubre el monto para el estado de la reserva, pero **no es plata**: no suma en `montoPagado` ni en lo cobrado del resumen y del listado (va en `montoBonificado`), no se devuelve al cancelar y no manda aviso.

### 4 ter. Partidos de 4 jugadores y turnos fijos — solo ADMIN
- **Armar un partido** (`POST /admin/reservas/partidos`): el ADMIN elige cancha, fecha, horario y 4 jugadores, cada uno un cliente registrado o un invitado (nombre y teléfono opcional). **El jugador 1 es el responsable**: tiene que ser un cliente registrado y es el cliente de la reserva (el ADMIN lo busca por mail o nombre y, si no tiene cuenta, se la crea con lo mínimo; el ADMIN queda como quien la armó). Mismas reglas de horario, solapamiento y stock que una reserva normal. Queda `CONFIRMADA`: no vence y ocupa la cancha aunque nadie haya pagado.
- **Cobrar**: en el club, **un solo monto general** (cómo se lo dividen es cosa de ellos): se cobra y se bonifica como una reserva normal, sin indicar el jugador. **Un partido no tiene seña**: `CONFIRMADA` sin nada pagado, `PARCIALMENTE_PAGADA` con algo, `PAGADA` entero (y `COMPLETADA` cuando se juega pago); si se juega sin pagar entero queda como deuda. No se paga online y no se le avisa al ADMIN de cada pago.
- **Modificar** (`PUT /admin/reservas/partidos/{id}`): jugadores por posición (se puede reemplazar a cualquiera; el jugador 1 sigue siendo el responsable), cancha, horario y productos, mientras no se haya jugado. El total no puede quedar por debajo de lo pagado.
- **Cancelar**: solo el ADMIN, con `DELETE /admin/reservas/{id}/cancelar` (reembolso si hay plata). Se avisa a los jugadores registrados.
- **Jugadores registrados**: ven el partido en "Mis reservas" y su detalle, sin poder pagarlo ni cancelarlo; los que son clientes reciben el recordatorio (si lo tienen activado) con el saldo del partido. En el panel, el filtro por cliente trae también los partidos en los que juega.
- **Turno fijo** (`/admin/turnos-fijos`): el mismo partido todas las semanas (el día de `desde`). Bloquea el horario siempre, también en la disponibilidad. Un job crea cada hora los partidos hasta el tope para reservar (hoy + 30 días por defecto), así que se van sumando fechas a medida que pasa el tiempo (cada uno se paga aparte); si una fecha está ocupada se informa y se reintenta.
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
- **Quién**: el dueño de la reserva, o ADMIN/EMPLEADO (un partido, solo el ADMIN; el EMPLEADO, solo reservas de hoy en adelante). Se pueden cancelar reservas `PENDIENTE`, `CONFIRMADA`, `SEÑADA`, `PARCIALMENTE_PAGADA` y `PAGADA`, sin pagos en proceso.
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

### 9 bis. Productos que suma el personal — `POST/DELETE /admin/reservas/{id}/productos`
- Lo que el cliente pide en el club (una bebida, otro tubo de pelotas) se carga en su reserva o partido **antes, durante o después del turno**, mientras esté confirmado (también `COMPLETADA`) y sin un pago online en proceso.
- Un producto del catálogo o uno escrito a mano (nombre y precio): si no existe con ese nombre, se da de alta como producto de venta, sin ofrecerse al reservar y con stock igual a la cantidad que se agrega o se vende (queda en 0).
- El total cambia y el estado se recalcula con lo pagado: una reserva `PAGADA` o `COMPLETADA` que suma algo vuelve a quedar con saldo (`SEÑADA` o `PARCIALMENTE_PAGADA`; un partido, `PARCIALMENTE_PAGADA`) y, si ya se jugó, aparece como deuda hasta que se cobra.
- Sacar unidades devuelve el stock de venta; el total no puede quedar por debajo de lo ya pagado.

### 9 ter. Ventas sueltas — `/admin/ventas`
- El personal registra lo que vende sin reserva (una bebida, pelotas): se cobra en el momento, en efectivo, con Cuenta DNI o con Mercado Pago, y se puede asociar a un cliente.
- **Con Mercado Pago se cobra con el QR de la caja y se confirma solo**, igual que el cobro con QR de una reserva: la venta queda `ESPERANDO_PAGO` con el stock apartado; cuando Mercado Pago confirma el pago (webhook o, si no llega, el job `VentaJobs.revisarCobrosQr` cada minuto) pasa a `COBRADA` y recién ahí cuenta en la caja. Si el QR vence o el empleado cierra el modal (`DELETE /admin/ventas/{id}/qr`), queda `NO_COBRADA` y el stock vuelve. Nunca se confía en el aviso: siempre se consulta la order, con la venta bloqueada. Si MP cobró otro monto, la venta queda `NO_COBRADA` y hay que devolverlo desde el panel de MP (queda en el log).
  - La caja tiene **un solo QR para reservas y ventas**: mientras espera un cobro, no se puede asignar otro (`409`). Los dos cobros bloquean primero la caja (`CajaQrService.bloquear`, la fila de `caja_qr`) y después la reserva o la venta, así dos empleados no la ocupan a la vez.
- Cada ítem es un producto de venta del catálogo o uno escrito a mano, que queda dado de alta igual que en una reserva. Los alquileres (paletas) no se venden sueltos.
- El stock baja en el momento (con los productos bloqueados en orden de id).
- **Una venta registrada no se edita ni se borra.** Si se cargó mal, el cliente no pagó o se cobró por otro medio, se **anula** (`POST /admin/ventas/{id}/anulacion`) con un motivo, y queda quién y cuándo: la venta pasa a `ANULADA`, sale de la caja y de lo vendido del resumen, y su stock vuelve. Si había que cobrarla de otra forma, se vuelve a cargar. Si se cobró con el QR, Mercado Pago devuelve el total solo (si no lo acepta, no se anula y se puede reintentar); si no, la plata se devuelve a mano por el mismo medio. Una venta esperando el QR no se anula: primero se cancela el QR.
  - El EMPLEADO anula solo las ventas de hoy que el ADMIN todavía no verificó. El ADMIN anula cualquiera; si es de un día anterior, la caja de ese día cambia.
  - Lo único que se edita es el cliente asociado (`PUT /admin/ventas/{id}/cliente`), porque no mueve plata ni stock. El EMPLEADO, solo en las de hoy.
- **Devolución** (solo ADMIN, `POST /admin/ventas/{id}/devoluciones`): el cliente devuelve todo o parte de lo que compró, después de la venta. Queda una devolución con fecha, quién la registró, motivo y monto (las unidades al precio de la venta), y cada ítem dice si el producto vuelve al stock (si sirve). Si se cobró con el QR, Mercado Pago devuelve el monto solo (si no lo acepta, no se registra); si no, la plata se devuelve a mano por el mismo medio.
  - Cuenta en la caja **del día en que se devuelve** (`caja.devoluciones`), así que la caja de los días anteriores no cambia. Lo vendido (`productos`) queda neto de lo devuelto.
  - Se puede devolver en varias veces; nunca más de lo que queda de cada producto. Una venta con devoluciones ya no se anula (lo que queda se devuelve) y una anulada no se devuelve.
  - Toda operación sobre una venta la bloquea primero (`VentaService.bloquear`), así dos personas no la anulan a la vez ni se repone el stock dos veces.
- Una venta con Cuenta DNI queda sin verificar hasta que el ADMIN la controla contra la cuenta (`PUT /admin/ventas/{id}/verificacion`), igual que un pago de una reserva.
- Se cobra en efectivo, con Mercado Pago o con Cuenta DNI, y entra en la caja del día (`efectivo`, `mercadoPago` o `cuentaDni`), en `ventas` y en `productos` del resumen; las anuladas no. Una venta esperando el QR o no cobrada tampoco. El EMPLEADO ve solo las de hoy, igual que el resumen.

### 10. Consultas
- `GET /reservas/mias`: reservas del usuario, de la más reciente a la más antigua.
- `GET /reservas/{id}`: detalle con sus pagos (dueño o staff). El front la consulta para saber cómo terminó un pago `EN_PROCESO`.

### Productos y stock

Pelotas, paletas y lo que el ADMIN sume (por ejemplo, agua) son productos del catálogo (`/admin/configuracion/productos`), con precio por unidad y stock. Cada producto dice:
- **si se ofrece al reservar** (`ofrecerAlReservar`): el cliente lo ve y lo pide al reservar. Los demás los carga solo el personal, en una reserva, un partido o una venta suelta;
- **el stock**: todos los productos lo controlan (desde V36 ya no hay productos "ilimitados"); se repone con `PATCH …/stock`.

Los productos **nunca se borran** (ni por la API ni por cascada): se dan de baja (`PUT …/{id}/baja`) y se reactivan (`PUT …/{id}/reactivacion`). Las reservas, ventas y devoluciones viejas los conservan.

**Quién maneja el stock**: el ADMIN crea y edita productos (nombre, precio, tipo, stock). El EMPLEADO puede ver el catálogo y ajustar el stock (`PATCH …/stock`, por ejemplo +12 al reponer o −1 por una rotura) y registra las devoluciones de paletas.

| Tipo | Qué significa el stock | Al reservar | Al cancelar o expirar | Al terminar el turno |
|---|---|---|---|---|
| `VENTA` (pelotas, bebidas) | unidades disponibles hoy | baja (también al sumarlo después o en una venta suelta) | vuelve | no vuelve (se entregaron) |
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
| `montoReserva` | 15000 | precio del turno mínimo (`duracionMinimaTurno`); hasta V33 era un monto fijo que se sumaba a las medias horas |
| `montoXMediaHora` | 5000 | lo que se suma por cada media hora por encima del mínimo |
| `porcentajeSenia` | 50 | % del total que es seña |
| `duracionMinimaTurno` / `duracionMaximaTurno` | 90 / 180 | minutos |
| días | L‑V 08–23, Sáb 09–22, Dom 09–20 | horario por día |

Además: la política de cancelación (`minutosReembolsoTotal` / `minutosReembolsoSenia`, 1440 / 720), el recordatorio del turno (activo, 120 minutos antes) y la anticipación para reservar (30 días).

El ADMIN la consulta y la cambia entera con `GET`/`PUT /admin/configuracion` (ver [api.md](api.md#configuración-del-club--adminconfiguracion-rol-admin)). Las reglas de los cambios están en `Configuracion` (`cambiarDuracionDeTurnos`, `cambiarPoliticaCancelacion`, `cambiarHorarios`). Un cambio rige para lo que se reserve o se cobre desde ahora: las reservas que ya existen conservan su precio y su seña. Un día que no está en los horarios, el club no abre. Los horarios (y la duración de los turnos) son públicos en `GET /club/horarios`.

**Cambio de horario con reservas afuera.** Antes de guardar, `ConfiguracionGeneralService` pide a reservas (`HorariosOcupados`, que implementa `HorariosOcupadosService`) las reservas futuras que siguen en pie y los turnos fijos activos que no entran en el horario nuevo (la regla es `Configuracion.motivoFueraDeHorario`). Solo cuenta lo que el cambio deja afuera nuevo. Si hay algo y no vino `"confirmar": true`, responde 409 con la lista y no guarda nada. Con `confirmar`, primero se guarda el cambio (`CambiosDeConfiguracion`, en su transacción) y después se cancelan esas reservas una por una, cada una en su transacción, como si las cancelara el ADMIN (`CancelacionService.cancelarComoPersonal` con reembolso `TOTAL` y aviso `CANCELADA_POR_CLUB`). Si alguna no se puede cancelar (un pago de Mercado Pago en proceso), el cambio queda guardado y esa reserva se informa en `cancelaciones.sinCancelar`. Los turnos fijos que quedaron afuera no generan partidos nuevos (sus fechas salen en `fechasOmitidas` con el motivo).

**Días cerrados.** El ADMIN cierra el club un día puntual (`POST /admin/configuracion/cierres`): se guarda en `dias_cerrados`, colgado de la configuración (`Configuracion.cerrar`, `reabrir`, `motivoCierre`). Ese día `Configuracion.diaApertura` responde que el club está cerrado, así que no se puede reservar, la disponibilidad lo informa y los turnos fijos no crean su partido. Si ese día hay reservas que todavía no empezaron, se piden confirmación y, al confirmar, se cancelan igual que con un cambio de horario. Reabrir el día (`DELETE …/cierres/{fecha}`) no recupera las reservas canceladas.

Se guarda en caché en memoria y se invalida al guardar; si se cambia directamente en la base hay que reiniciar la app.
