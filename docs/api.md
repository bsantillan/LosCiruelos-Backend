# API REST

Base URL local: `http://localhost:8080`. Todos los bodies son JSON.
Los endpoints autenticados requieren `Authorization: Bearer <accessToken>`.

Sin token, o con un token vencido o inválido, las rutas protegidas responden `401`; si el rol no alcanza, `403`. Los endpoints de `/auth` tienen un límite de pedidos por IP y por minuto (`429`).

## Fechas y horas

Todo en hora de Argentina (`America/Argentina/Buenos_Aires`), con un solo formato por tipo:

| Tipo | Respuestas | Ejemplo |
|---|---|---|
| Fecha y hora (`createdAt`, `expiresAt`, `paidAt`, `cancelableHasta`, `timestamp` de los errores, …) | `yyyy-MM-dd'T'HH:mm:ss.SSSXXX`, siempre con milisegundos y offset | `2026-10-17T18:15:00.123-03:00` |
| Fecha (`fechaReserva`, `fecha`) | `yyyy-MM-dd` | `2026-10-17` |
| Hora (`horaInicio`, `horaFin`, horarios) | `HH:mm` | `18:00` |

Con el offset, `new Date(valor)` en el navegador da el instante correcto aunque la computadora esté en otra zona. En los pedidos los formatos no cambian: fechas `yyyy-MM-dd` y horas `HH:mm`.

## Valores de enums

| Enum | Valores |
|---|---|
| `Role` | `CLIENTE`, `EMPLEADO`, `ADMIN`, `PROFESOR` |
| `Categoria` | `Primera`, `Segunda`, `Tercera`, `Cuarta`, `Quinta`, `Sexta`, `Septima`, `Principiante` |
| `Posicion` | `Drive`, `Reves`, `Ambos` |
| `TokenType` | `VERIFY_EMAIL`, `PASSWORD_RESET`, `VERIFY_PHONE` |
| `CanalNotificacion` | `EMAIL`, `WHATSAPP` |
| `EstadoReserva` | `PENDIENTE`, `CONFIRMADA` (partido sin nada pagado: no vence), `SEÑADA` (solo reservas normales: un partido no tiene seña), `PARCIALMENTE_PAGADA`, `PAGADA`, `CANCELADA`, `EXPIRADA`, `COMPLETADA` |
| `TipoReserva` | `NORMAL`, `PARTIDO` (4 jugadores, lo arma el ADMIN) |
| `EstadoPago` | `PENDIENTE`, `APROBADO`, `RECHAZADO`, `EN_PROCESO`, `DEVUELTO`, `EXPIRADO`, `A_REEMBOLSAR`, `REEMBOLSO_PENDIENTE` |
| `PaymentProvider` | `MANUAL` (efectivo), `MERCADO_PAGO`, `CUENTA_DNI` (Banco Provincia, cargado a mano; el ADMIN lo verifica contra la cuenta), `BONIFICACION` (el ADMIN no le cobró al cliente: no es plata) |
| `AuthProvider` | `LOCAL`, `GOOGLE` |

---

## Auth — `/auth` (público, salvo logout)

### `POST /auth/register` → `201 Created` (sin body)
Registra un CLIENTE local, crea su perfil de jugador y le envía un código de verificación por email.
```json
{
  "email": "juan@mail.com",          // @Email, obligatorio
  "nombre": "Juan",                  // 2-50
  "apellido": "Pérez",               // 2-50
  "password": "Secreta123!",         // 8-30 + reglas de PasswordValidator
  "termsAccepted": true,             // debe ser true
  "telefono": "011 15-1234-5678",    // se guarda como 5491112345678
  "aceptarNotificacion": true,       // obligatorio: "Acepto recibir notificaciones del club"
  "categoria": "Cuarta",
  "posicion": "Drive"
}
```
Errores: `400` email en uso (`errors.email`), contraseña inválida (`errors.password`), términos (`errors.terminos`), teléfono inválido o ya verificado en otra cuenta activa (`errors.telefono`), validación de campos. El email con el código se envía después de guardar; si falla, el registro queda hecho igual.

Teléfono: se acepta con espacios, guiones o paréntesis (`^[0-9+() -]{8,25}$`) y se guarda normalizado al formato de WhatsApp (`5491123456789`); con o sin `0`, `15` o `+54 9`.

Notificaciones: `aceptarNotificacion` tiene que ser `true` (`400 errors.aceptarNotificacion`). No se elige el medio: las notificaciones arrancan **por mail**. Después, al completar el perfil, el usuario valida su celular (`POST /perfil/telefono/codigo` y `/perfil/telefono/verificar`) y ahí puede cambiar a WhatsApp (`PUT /perfil/notificaciones`). **Sin el celular verificado el perfil no está completo** y el cliente no puede reservar.

### `POST /auth/login` → `200 LoginResponse`
```json
{ "email": "juan@mail.com", "password": "Secreta123!" }
```
Errores: `401` credenciales inválidas, `403` email no verificado (`errors.email_verificado`), `403` cuenta desactivada.

**`LoginResponse`**
```json
{
  "accessToken": "eyJ...", "refreshToken": "uuid",
  "id": 1, "nombre": "Juan", "apellido": "Pérez", "email": "juan@mail.com",
  "perfilCompleto": true, "rol": "CLIENTE"
}
```
`perfilCompleto`:
- **CLIENTE**: nombre, apellido, **celular verificado**, categoría y posición. Sin perfil completo no puede reservar ni elegir sus avisos.
- **ADMIN**: nombre, apellido y **celular verificado**.
- EMPLEADO y PROFESOR: siempre `true`.

### `POST /auth/google` → `200 LoginResponse`
```json
{ "idToken": "<id token de Google Sign-In>" }
```
Google tiene que confirmar el email (si no, `401`). Si el email no existe crea un CLIENTE con provider `GOOGLE`, ya verificado y con perfil vacío (`perfilCompleto: false`). Si existe y no estaba verificado, queda verificado. Cuenta desactivada → `403`.

### `POST /auth/verificar-token` → `200`
Verifica el código de 6 dígitos de email o de reset de contraseña.
```json
{ "email": "juan@mail.com", "codigo": "123456", "tipo_codigo": "VERIFY_EMAIL" }
```
- `VERIFY_EMAIL`: marca el email como verificado.
- `PASSWORD_RESET`: marca el código como verificado para permitir `/password-reset/confirm`.

Errores: `400 errors.codigo` (inválido, vencido, o bloqueado después de 5 intentos fallidos), `401` usuario no encontrado, `409` cuenta ya verificada.

### `POST /auth/resend-token` → `200`
```json
{ "email": "juan@mail.com", "tipo_codigo": "VERIFY_EMAIL" }
```
Reenvía un código nuevo (invalida el anterior). Si el email no existe responde 200 igual.

### `POST /auth/refresh` → `200 LoginResponse`
```json
{ "refreshToken": "uuid" }
```
Rota el refresh token: el viejo deja de servir. `401` si es inválido o venció; `403` si la cuenta fue desactivada.

### `POST /auth/logout` → `200` (autenticado)
```json
{ "refreshToken": "uuid" }
```
Borra el refresh token. Valida que pertenezca al usuario del access token. `400` si falta, `401` si no es válido o es de otro usuario.

### `POST /auth/password-reset/request` → `200`
```json
{ "email": "juan@mail.com" }
```
Envía un código de reset (vence en 15 min). Responde 200 aunque el email no exista.

### `PUT /auth/password-reset/confirm` → `200`
```json
{ "email": "juan@mail.com", "nuevaPassword": "Nueva123!" }
```
Requiere haber validado antes el código con `/auth/verificar-token` (tipo `PASSWORD_RESET`) y que no haya vencido. Cierra todas las sesiones abiertas. `400` contraseña inválida, `401` "Flujo inválido".

---

## Perfil — `/perfil` (autenticado)

### `GET /perfil` → `200 PerfilResponse`
```json
{
  "nombre": "Juan", "apellido": "Pérez", "email": "juan@mail.com", "telefono": "5491112345678",
  "telefonoVerificado": true, "canalNotificaciones": "WHATSAPP", "perfilCompleto": true,
  "categoria": "Cuarta", "posicion": "Drive",
  "cantDiasMiembro": 30,            // días desde termsAcceptedAt
  "cantPartidos": 5,                // reservas COMPLETADA
  "diasDesdeUltimoPartido": 3,      // desde la última COMPLETADA (null si nunca jugó)
  "cantPartidosEsteMes": 2,         // COMPLETADA del mes actual
  "categoriaActualizadaAt": "2026-10-01T10:00:00",
  "categoriaEditable": false        // si todavía puede cambiar la categoría
}
```
`categoria`, `posicion`, `categoriaActualizadaAt` y `categoriaEditable` solo vienen para CLIENTE.

### `PUT /perfil` → `200 PerfilResponse`
Datos personales. El celular se edita en "Celular y avisos" (`PUT /perfil/notificaciones`), así que **`telefono` es opcional** acá.
```json
{ "nombre": "Juan", "apellido": "Pérez", "categoria": "Tercera", "posicion": "Reves" }
```
```json
{ "nombre": "Juan", "apellido": "Pérez", "telefono": "11 1234-5678", "categoria": "Tercera", "posicion": "Reves" }
```
- Se validan los campos (`@Valid`): nombre y apellido obligatorios, de 2 a 50 caracteres.
- `telefono` **opcional**:
  - Si no viene (o es `null`), no se tocan el teléfono ni su verificación. Sirve también para quien no tiene celular cargado (el ADMIN inicial, los que entran con Google).
  - Si viene, se valida (`^[0-9+() -]{8,25}$` y numeración real) y se guarda normalizado al formato de WhatsApp (`5491123456789`), con o sin `0`, `15` o `+54 9`. Si es **otro número**, queda sin verificar; el mismo número escrito de otra forma (`11 2233-4455` y `5491122334455`) no pierde la verificación. Si otra cuenta activa ya lo tiene verificado, `400 errors.telefono`.
- El celular verificado sigue siendo requisito de `perfilCompleto`.
- CLIENTE: `categoria` y `posicion` obligatorias (`400 errors.posicion / errors.categoria`). La categoría se puede **cambiar una sola vez y dentro de las 24 h** desde que se cargó; si no se puede, responde `403` y no se guarda nada. El resto de los datos se puede cambiar siempre.
- Otros roles: no pueden enviar `categoria` ni `posicion` (`400`).
- Nombre y apellido se guardan capitalizados.

### `DELETE /perfil/desactivar` → `200` (rol: CLIENTE)
Sin body. Desactiva la cuenta (`enabled = false`) y cierra todas las sesiones. Respuesta: `{ "message": "Se eliminó correctamente su cuenta" }`.

### Notificaciones (cualquier rol)

Cada usuario elige si recibe los avisos por mail o por WhatsApp. Detalle de qué se avisa, alta en Meta y plantillas: [whatsapp.md](whatsapp.md).

**`NotificacionesResponse`**
```json
{
  "canal": "WHATSAPP",          // el que eligió
  "canalEnUso": "EMAIL",        // el que se usa: EMAIL hasta verificar el teléfono
  "telefono": "5491112345678",
  "telefonoVerificado": false,
  "whatsappDisponible": true    // si el club tiene WhatsApp configurado
}
```

| Método y ruta | Descripción | Errores |
|---|---|---|
| `GET /perfil/notificaciones` → `200 NotificacionesResponse` | Canal y teléfono del usuario. | |
| `PUT /perfil/notificaciones` → `200 NotificacionesResponse` | Body `{ "canal": "EMAIL" \| "WHATSAPP", "telefono": "opcional" }`. Elegir `WHATSAPP` registra el consentimiento y, si el teléfono no está verificado, manda el código. Volver a `EMAIL` retira el consentimiento. | `400` sin teléfono o teléfono inválido |
| `POST /perfil/telefono/codigo` → `200` | Manda (o reenvía) el código por WhatsApp. Topes: 1 por minuto y 5 cada 24 h por usuario, 5 cada 24 h por número (entre todas las cuentas), además de 3 por minuto por IP. | `400` sin teléfono · `409` ya verificado, celular verificado en otra cuenta o WhatsApp no disponible · `429` tope alcanzado (el mensaje dice cuánto falta) |
| `POST /perfil/telefono/verificar` → `200 NotificacionesResponse` | Body `{ "codigo": "123456" }`. | `400 errors.codigo` |
| `GET /perfil/suscripciones` → `200` | **CLIENTE.** Qué avisos recibe: `{ "cancelaciones": true, "recordatorios": true, "promociones": false, "editable": true }`. Las cancelaciones se avisan siempre; `editable` es `false` mientras el perfil no esté completo. | `403` otros roles |
| `PUT /perfil/suscripciones` → `200` | **CLIENTE.** Body `{ "recordatorios": true, "promociones": false }` (los dos obligatorios). Las novedades y promociones solo quedan guardadas: todavía no se envían. | `400` · `403` perfil incompleto (celular sin verificar) u otro rol |
| `GET /perfil/avisos` → `200` | **ADMIN.** Qué avisos de reservas recibe: `{ "activos", "reservaConfirmada", "saldoCobrado", "reservaCancelada", "reembolsoManual" }` (todos `true` por defecto). `activos` es el interruptor general: en `false` no le llega ninguno. | `403` otros roles |
| `PUT /perfil/avisos` → `200` | **ADMIN.** Mismo body: los cuatro avisos obligatorios; `activos` opcional (si no viene, no cambia). Apagar `activos` pausa todo y conserva la elección de cada aviso. | `400` · `403` |
| `POST /perfil/avisos/prueba` → `200` | **ADMIN.** Manda un aviso de ejemplo por su canal: `{ "enviado": true, "canal": "WHATSAPP", "destino": "549..." }`. | `403` |
| `GET /admin/configuracion/avisos` → `200` | **ADMIN.** Recordatorio del turno: `{ "recordatorioActivo": true, "minutosRecordatorio": 120 }`. | |
| `PUT /admin/configuracion/avisos` → `200` | **ADMIN.** Mismo body; minutos entre 15 y 1440. | `400` |
| `GET /admin/configuracion/reservas` → `200` | **ADMIN.** Hasta cuántos días adelante puede reservar el cliente: `{ "diasAnticipacion": 30 }`. | |
| `PUT /admin/configuracion/reservas` → `200` | **ADMIN.** Mismo body; entre 1 y 365 días. También es hasta dónde se generan los partidos de los turnos fijos. | `400` |

---

## Administración de usuarios — `/admin/usuarios` (rol: ADMIN)

**`UsuarioAdminResponse`**
```json
{
  "id": 7, "nombre": "Ana", "apellido": "Gómez", "email": "ana@mail.com", "telefono": "5491122334455",
  "telefonoVerificado": false, "canalNotificaciones": "EMAIL",
  "rol": "CLIENTE", "enabled": false, "emailVerificado": false, "provider": "LOCAL",
  "categoria": "Quinta", "posicion": "Ambos"
}
```

| Método y ruta | Descripción |
|---|---|
| `POST /admin/usuarios` → `201` | Crea un usuario con contraseña temporal aleatoria (12 caracteres) y le manda un email de bienvenida con la contraseña y un código de verificación. Body: `email`, `nombre`, `apellido`, `telefono`, `rol` (obligatorios); `categoria` y `posicion` opcionales para un CLIENTE (sin ellas queda con el perfil incompleto y las carga él; sirve para dar de alta en el momento al responsable de un partido). El usuario queda habilitado y con el email sin verificar. |
| `PUT /admin/usuarios/{id}` → `200` | Edita `nombre`, `apellido`, `telefono`, `rol` (obligatorios) y `categoria`/`posicion` (solo CLIENTE, obligatorias en ese caso). Si deja de ser CLIENTE se borra su perfil de jugador; si pasa a CLIENTE se le crea. No se puede editar a un ADMIN ni convertir a alguien en ADMIN. Todo se valida antes de guardar. |
| `GET /admin/usuarios` → `200 [ ... ]` | Lista usuarios (nunca incluye ADMIN). Filtros opcionales por query string: `rol`, `enabled`, `nombre` (busca en nombre o apellido, *contiene*), `email` (*contiene*), `categoria`, `posicion` (estos dos solo junto con `rol=CLIENTE`). |
| `GET /admin/usuarios/{id}` → `200` | Detalle de un usuario. |
| `PUT /admin/usuarios/{id}/reactivar` → `200` | `enabled = true`. |
| `DELETE /admin/usuarios/{id}/desactivar` → `200` | `enabled = false` y borra sus refresh tokens. No aplica a un ADMIN. |

---

## Canchas — `/canchas` (público)

Se pueden consultar sin iniciar sesión (un visitante ve los horarios libres y los precios); para reservar sí hace falta una cuenta con el perfil completo. Si se manda un token vencido o inválido, se responde igual, como visitante.

### `GET /canchas` → `200`
```json
[ { "id": 1, "numero": 1, "descripcion": "Cancha de césped sintético con iluminación LED." } ]
```

### `GET /canchas/{id}/disponibilidad?fecha=2026-10-15&duracion=90` → `200`
```json
{
  "horarioApertura": "08:00", "horarioCierre": "23:00",
  "duracionMinimaTurno": 90, "duracionMaximaTurno": 180,
  "duracion": 90,
  "montoCancha": 15000.00,
  "horarioMinimoInicio": "08:00",
  "slotsOcupados":     [ { "horaInicio": "18:00", "horaFin": "19:30" } ],
  "turnosDisponibles": [ { "horaInicio": "08:00", "horaFin": "09:30" }, { "horaInicio": "08:30", "horaFin": "10:00" } ],
  "reservableHasta": "2026-11-14"
}
```
- `turnosDisponibles`: turnos de la duración pedida que se pueden reservar, cada 30 min desde la apertura, sin solaparse con ninguno ocupado. Usa las mismas reglas que `POST /reservas`.
- `montoCancha`: precio de la cancha para esa duración (sin pelotas ni paletas). Se calcula con el mismo método que usa la reserva (`montoReserva + montoXMediaHora × duración / 30`), así que coincide con el `montoCancha` que se cobra.
- `duracion` es opcional (por defecto, la mínima); tiene que ser múltiplo de 30 y estar entre el mínimo y el máximo.
- Si la fecha es hoy, `horarioMinimoInicio` es el próximo bloque disponible (`null` si ya no quedan turnos ese día).
- `reservableHasta`: el último día que el **cliente** puede reservar (hoy + los días de anticipación de la configuración, 30 por defecto). Sirve para limitar el calendario. La disponibilidad responde también fechas posteriores, porque el personal puede reservar más lejos.
- Errores: `400` fecha pasada, duración inválida o día sin apertura; `404` cancha inexistente.

---

## Reservas — `/reservas` (rol: CLIENTE)

Un CLIENTE solo ve y opera sus propias reservas, y además **ve** (sin pagar ni cancelar) los partidos en los que juega. ADMIN y EMPLEADO, cualquiera. Una reserva ajena responde `404`.

**`ReservaResponse`**

`montoPagado` es solo plata cobrada; `montoBonificado`, lo que el ADMIN bonificó; `montoPendiente = montoTotal − montoPagado − montoBonificado`. En `pagos`, una bonificación aparece con `provider: "BONIFICACION"` y su `motivo`. `tipo` es `NORMAL` o `PARTIDO`; en un partido vienen `jugadores` (ver [Partidos](#partidos-y-turnos-fijos--adminpartidos-adminturnos-fijos-rol-admin)), `turnoFijoId` si lo creó un turno fijo, y `clienteId` es el jugador 1 (el responsable). En una reserva normal `jugadores` es `[]`.
```json
{
  "id": 10, "canchaId": 1, "canchaNumero": 1,
  "fechaReserva": "2026-10-15", "horaInicio": "18:00", "horaFin": "19:30",
  "montoCancha": 15000.00, "montoProductos": 9000.00,
  "montoTotal": 24000.00, "montoSenia": 12000.00, "montoPagado": 0, "montoBonificado": 0,
  "montoPendiente": 24000.00,
  "productos": [
    { "productoId": 1, "codigo": "PELOTAS", "nombre": "Pelotas", "tipo": "VENTA",
      "cantidad": 2, "precioUnitario": 1500.00, "subtotal": 3000.00 },
    { "productoId": 2, "codigo": "PALETAS", "nombre": "Paletas", "tipo": "ALQUILER",
      "cantidad": 3, "precioUnitario": 2000.00, "subtotal": 6000.00,
      "cantidadDevuelta": null, "devueltaAt": null }
  ],
  "estado": "PENDIENTE", "tipo": "NORMAL", "turnoFijoId": null, "jugadores": [],
  "clienteId": 3, "clienteNombre": "Juan Pérez",
  "createdAt": "2026-10-12T17:00:00.000-03:00", "expiresAt": "2026-10-12T17:15:00.000-03:00",
  "pagos": [ { "id": 5, "monto": 7500.00, "estado": "PENDIENTE", "provider": "MERCADO_PAGO" } ]
}
```

Todas las rutas de `/reservas` son **solo del CLIENTE** (el personal recibe `403`); el cliente es siempre el usuario autenticado. Lo del personal está en `/admin/reservas` (Panel del personal, más abajo).

| Método y ruta | Descripción | Errores |
|---|---|---|
| `POST /reservas` → `201` | Crea una reserva `PENDIENTE` para el cliente autenticado, que vence en 15 min si no se paga. Body abajo. | `400` horario o datos inválidos, o fecha más allá de la anticipación ("Podés reservar hasta el sábado 14/11 (30 días de anticipación).") · `403` perfil incompleto · `404` cancha · `409` turno ocupado, sin stock o reserva con pago en curso |
| `GET /reservas/mias` → `200` | Reservas del usuario autenticado, **paginadas y filtradas** (ver abajo). | `400` parámetros inválidos |
| `GET /reservas/{id}` → `200` | Detalle con los pagos: una reserva propia o un partido en el que juega. Sirve para consultar el resultado de un pago en proceso. | `404` |
| `DELETE /reservas/{id}/cancelar` → `200 ReservaResponse` | Cancela la reserva y hace los reembolsos según la política (ver abajo). Devuelve la reserva con los pagos actualizados. El personal cancela con `DELETE /admin/reservas/{id}/cancelar`. | `403` partido (lo cancela el ADMIN) · `404` · `409` turno empezado/terminado, reserva finalizada o pago en proceso. Si Mercado Pago no acepta el reembolso, **se cancela igual** (`200`) y el pago queda `REEMBOLSO_PENDIENTE` |
| `POST /reservas/{id}/expirar` → `200` | Expira una `PENDIENTE` cuyo plazo ya venció (lo mismo que hace el job). | `404` · `409` todavía en plazo o no está pendiente |

### Cancelar — política de cancelación

| Quién | Hasta cuándo | Qué se devuelve |
|---|---|---|
| Cliente (`DELETE /reservas/{id}/cancelar`) | hasta que empieza el turno | faltan ≥ `minutosReembolsoTotal` (24 h): **todo lo pagado** · faltan ≥ `minutosReembolsoSenia` (12 h): **la seña** (o lo pagado, si es menos) · menos: **nada** |
| ADMIN / EMPLEADO (`DELETE /admin/reservas/{id}/cancelar`) | hasta que termina el turno | lo que indique en `?reembolso=TOTAL\|SENIA\|NADA` (obligatorio si la reserva tiene dinero cobrado) |

- Una reserva `PENDIENTE` se cancela sin reembolso (no tiene dinero cobrado).
- Los reembolsos se reparten primero sobre los pagos online de Mercado Pago (se devuelven por API y quedan `DEVUELTO`) y después sobre los cobrados en el club (quedan `A_REEMBOLSAR` para que el personal los devuelva). `montoReembolsado` de cada pago dice cuánto se devolvió.
- Si MP no acepta el reembolso (pasa con pagos recién cobrados que todavía se están acreditando), la reserva se cancela igual y ese pago queda `REEMBOLSO_PENDIENTE`: un job lo reintenta cada 2 minutos y pasa a `DEVUELTO`. Si no lo logra en 24 h, o MP responde algo que no se arregla reintentando, queda `A_REEMBOLSAR` (a devolver a mano). Para el front ya cuenta como devuelto (`montoPagado` lo descuenta): conviene mostrarlo como "reembolso en proceso".
- Se devuelve el stock de pelotas.

**Campos de `ReservaResponse` para el front** (calculados con la regla del cliente, en el momento de la consulta):

| Campo | Qué es |
|---|---|
| `cancelable` | si el cliente puede cancelar ahora |
| `cancelableHasta` | hasta cuándo (inicio del turno) |
| `montoReembolso` | cuánto se le devolvería si cancela ahora |
| `reembolsoTotalHasta` | hasta cuándo se devuelve todo (null si no aplica) |
| `reembolsoSeniaHasta` | hasta cuándo se devuelve la seña (null si no aplica) |

**Turnos jugados**: `turnoTerminado` dice si el turno ya terminó, y `debeSaldo` si además la reserva sigue `SEÑADA` o `PARCIALMENTE_PAGADA` (se jugó pero falta cobrar `montoPendiente`). El front puede mostrarla como "Jugada · saldo pendiente $X". Pasa a `COMPLETADA` cuando el personal registra el pago del saldo.

### `GET /reservas/mias` — filtros y paginación

Todos los parámetros son opcionales:

| Parámetro | Valores | Por defecto | Qué hace |
|---|---|---|---|
| `tipo` | `proximas` \| `historial` | todas | **proximas**: el turno no terminó y la reserva ocupa la cancha (señada, parcial, pagada, o pendiente en plazo o con un pago en proceso). **historial**: todo lo demás (completadas, canceladas, expiradas, pendientes vencidas y turnos ya jugados). |
| `estado` | `EstadoReserva` separados por coma | todos | Ej. `estado=SEÑADA,PAGADA`. Se combina con `tipo`. |
| `desde` / `hasta` | `YYYY-MM-DD` | — | Rango sobre la fecha del turno (inclusive). |
| `incluirDescartadas` | `true` \| `false` | `false` | Por defecto **no se muestran las descartadas**: `CANCELADA` o `EXPIRADA` sin ningún pago `APROBADO`, `DEVUELTO`, `REEMBOLSO_PENDIENTE`, `A_REEMBOLSAR` o `EN_PROCESO` (los `PENDIENTE`, `RECHAZADO` y `EXPIRADO` no cuentan: no movieron plata). Se ocultan **siempre**, también filtrando por `estado`; solo `true` las trae (soporte, futuro panel de admin). `totalElementos` y `totalPaginas` también las excluyen. No se borran de la base. |
| `page` | ≥ 0 | `0` | Página, desde 0. |
| `size` | 1 a 50 | `10` | Tamaño de página. |

Orden fijo: `proximas` de la más cercana a la más lejana; `historial` y sin `tipo`, de la más nueva a la más vieja.

```json
{
  "contenido": [ /* ReservaResponse */ ],
  "pagina": 0,
  "tamanio": 10,
  "totalElementos": 37,
  "totalPaginas": 4
}
```

Errores `400`: `size` fuera de 1–50, `page` negativa, `desde` posterior a `hasta` ("La fecha desde no puede ser posterior a hasta."), `tipo` o `estado` inexistente, fecha mal formada.

Body de `POST /reservas` (el de `POST /admin/reservas` suma `clienteId` y `bonificacion`):
```json
{
  "canchaId": 1,
  "fechaReserva": "2026-10-15",
  "horaInicio": "18:00",
  "horaFin": "19:30",
  "productos": [ { "productoId": 1, "cantidad": 2 }, { "productoId": 2, "cantidad": 3 } ]
}
```
- `fechaReserva`: hoy o futuro.
- `productos` es opcional (hasta 20 ítems, cada uno con `cantidad` de 1 a 99; un producto repetido suma sus cantidades). El total es `montoCancha + Σ cantidad × precio unitario`, y la seña se calcula sobre ese total.
- El cliente solo puede pedir los productos que **se ofrecen al reservar** (`GET /productos`); otro responde `400` "Gatorade no se puede pedir al reservar: pedíselo al club.". El personal (`POST /admin/reservas`, partidos y turnos fijos) puede pedir cualquier producto activo. Un producto inexistente → `400` "El producto 9 no existe.".
- Los productos sin control de stock no se limitan.
- Sin stock suficiente responde `409`, por ejemplo `"Paletas: hay 1 unidad disponible para ese horario."`.
- Los precios unitarios quedan congelados en la reserva: un cambio de precio posterior no la modifica.
- Volver a pedir la misma cancha, fecha e inicio con otra hora de fin u otras cantidades actualiza la reserva pendiente existente (si no hay pago en curso y no se superpone).
- Pedir otro horario en la misma cancha y fecha cancela las pendientes anteriores del cliente.
- Reglas completas en [flujos-de-negocio.md](flujos-de-negocio.md#reservas-y-pagos).

---

## Productos — `/productos` (público)

Lo que el cliente puede pedir al reservar (los productos activos con `ofrecerAlReservar`): también sin iniciar sesión.

| Método y ruta | Descripción |
|---|---|
| `GET /productos` → `200` | Productos que se ofrecen al reservar, con precio por unidad, tipo y stock. |
| `GET /productos/disponibilidad?fecha=2026-10-20&horaInicio=18:00&horaFin=19:30` → `200` | Unidades que se pueden pedir de cada uno en ese horario: `[{ "id", "codigo", "nombre", "tipo", "precio", "disponibles" }]`. `disponibles` es `null` en un producto sin control de stock (no tiene tope). Sirve para limitar los selectores de cantidad del front. |

## Configuración: stock de productos — `/admin/configuracion/productos` (rol: ADMIN; EMPLEADO puede listar y ajustar stock)

**`ProductoResponse`**
```json
{ "id": 1, "codigo": "PELOTAS", "nombre": "Pelotas", "descripcion": "Pelota de pádel, precio por unidad",
  "tipo": "VENTA", "precio": 1500.00, "stock": 10, "activo": true,
  "ofrecerAlReservar": true, "controlaStock": true }
```
- `ofrecerAlReservar`: si el cliente lo ve y lo puede pedir al reservar (`GET /productos`). Si no, solo lo carga el personal (en una reserva, un partido o una venta suelta).
- `controlaStock`: si es `false`, el stock no se mira ni se descuenta (por ejemplo, algo que se compra cuando hace falta).
- `tipo`: `VENTA` (el stock son las unidades disponibles y baja con cada reserva) o `ALQUILER` (el stock es el total de unidades; se controla cuántas están en uso en cada horario).
- `codigo`: `PELOTAS` y `PALETAS` son los productos del sistema que se piden en la reserva; los que crea el admin tienen `codigo: null`.

| Método y ruta | Descripción | Errores |
|---|---|---|
| `GET /admin/configuracion/productos` → `200` | **ADMIN, EMPLEADO.** Todos los productos, incluidos los inactivos. | |
| `POST /admin/configuracion/productos` → `201` | Crea un producto. Body: `nombre` (obligatorio, único), `descripcion`, `tipo`, `precio` (≥ 0, 2 decimales), `stock` (≥ 0), `activo` (por defecto `true`), `ofrecerAlReservar` (por defecto `false`) y `controlaStock` (por defecto `true`). | `400` validación · `409` nombre repetido |
| `PUT /admin/configuracion/productos/{id}` → `200` | Reemplaza nombre, descripción, precio y stock; `activo`, `ofrecerAlReservar` y `controlaStock` se cambian solo si vienen. El `tipo` no se puede cambiar. | `400` · `404` · `409` |
| `PATCH /admin/configuracion/productos/{id}/stock` → `200` | **ADMIN, EMPLEADO.** Suma o resta unidades: `{ "ajuste": 12 }` (reposición), `{ "ajuste": -1 }` (rotura). | `404` · `409` si quedaría negativo |
| `DELETE /admin/configuracion/productos/{id}` → `200` | Baja lógica (`activo = false`): deja de poder pedirse; las reservas existentes lo conservan. | `404` |

Pelotas y paletas arrancan con stock de **50 pelotas y 50 paletas** (migración V6) y precio de **$5000 por unidad** (migración V7). El admin los cambia desde `/admin/configuracion/productos`.

---

## Pagos — `/pagos` (cliente y webhook) y cobros del personal en `/admin/reservas/{id}`

**`PagoResponse`**
```json
{
  "id": 5, "reservaId": 10, "monto": 7500.00, "estado": "APROBADO",
  "provider": "MERCADO_PAGO",
  "paymentToken": "<preference_id>",
  "externalPaymentId": "1234567890",
  "createdAt": "2026-10-12T17:01:00.000-03:00", "paidAt": "2026-10-12T17:02:10.000-03:00",
  "montoReembolsado": null,
  "verificado": null, "verificadoAt": null,
  "estadoReserva": "SEÑADA",
  "detalle": "accredited"
}
```
`paymentToken` es el id de la preferencia (para inicializar el Brick o el botón de MP); `externalPaymentId`, el id del pago en MP; `verificado` y `verificadoAt`, si el ADMIN comprobó en la cuenta del club que entró un pago con Cuenta DNI, y cuándo (`null` en los otros medios); `estadoReserva`, el estado de la reserva después de la operación; `detalle`, el `status_detail` de MP (solo al cobrar).

| Método y ruta | Rol | Descripción | Errores |
|---|---|---|---|
| `POST /pagos/{reservaId}/iniciar?pagarTotal=false` | CLIENTE (dueño) | Un partido no se paga online (`409`). Crea el pago `PENDIENTE` y la preferencia de MP (sirve para el Payment Brick y para el Wallet Brick; vuelve a `FRONTEND_URL/pago/resultado` y vence con la reserva). `pagarTotal=true` cobra todo el saldo; `false` (por defecto) lo que falta para la seña. Si ya hay uno iniciado por el mismo monto, lo devuelve. | `404` · `409` vencida, estado inválido, seña ya paga o pago en proceso · `502` MP |
| `POST /pagos/{reservaId}/pago` | CLIENTE (dueño) | Cobra con el `formData` del Payment Brick (`token`, `payment_method_id`, `installments`, `issuer_id`, `payer`). El monto lo define el servidor. | `400` datos de tarjeta · `404` · `409` sin pago iniciado o reserva fuera de plazo · `502` MP |
| `POST /pagos/notificacion` | público (Mercado Pago) | Webhook. Acepta `?type=payment&data.id=…`, body `{"type":"payment","data":{"id":…}}` o `?topic=payment&id=…`. Valida `x-signature` si está configurado `MP_WEBHOOK_SECRET`. También recibe las orders de los cobros con QR (`?type=order&data.id=ORD…` o body `{"type":"order","data":{"id":…}}`): consulta la order a MP y no valida la firma (ver [mercado-pago-qr.md](mercado-pago-qr.md)). | `200` también si el pago no existe en MP (se ignora) · `401` firma inválida · `502` error al consultar MP (MP reintenta) |
| `POST /admin/reservas/{reservaId}/bonificacion` | **ADMIN** | Bonifica la reserva cuando no se le cobra al cliente (socio, torneo, cortesía): todo o una parte. Body opcional `{ "monto": 7500, "motivo": "socio" }`; sin `monto` bonifica todo lo que falta. Queda un pago `provider = BONIFICACION` que cubre el monto (la reserva se confirma o queda paga) pero **no es plata**: no cuenta como cobrado, no se devuelve si se cancela y no manda aviso. Se acepta en las mismas reservas que un pago en el club. | `400` monto inválido o mayor al pendiente · `403` EMPLEADO o CLIENTE · `409` reserva paga, vencida o con pago online en proceso |
| `POST /admin/reservas/{reservaId}/cobros/efectivo` | ADMIN, EMPLEADO | Pago en efectivo ya aprobado (`provider = MANUAL`). Body `{ "monto": 5000 }`, también en un **partido** (se cobra el monto general, sin indicar el jugador). Si deja la reserva paga y el turno ya terminó, la reserva pasa a `COMPLETADA`. | `400` monto inválido o mayor al pendiente · `409` estado o pago online en proceso |
| `POST /admin/reservas/{reservaId}/cobros/presencial` | ADMIN, EMPLEADO | Igual, con Mercado Pago cargado a mano (`provider = MERCADO_PAGO`; por ejemplo, una transferencia a la cuenta del club). | igual |
| `POST /admin/reservas/{reservaId}/cobros/cuenta-dni` | ADMIN, EMPLEADO | Igual, con Cuenta DNI (`provider = CUENTA_DNI`). Cuenta DNI no tiene API: el personal cobra desde la app del banco y lo registra acá. Body `{ "monto": 7500 }`. Queda **sin verificar** (`verificado: false`) hasta que el ADMIN lo controle contra la cuenta. Si se cancela con reembolso, se devuelve a mano (`A_REEMBOLSAR`). | los de `cobros/efectivo` |
| `PUT /admin/reservas/{reservaId}/pagos/{pagoId}/verificacion` | **ADMIN** | Marca un pago con Cuenta DNI como verificado (el ADMIN comprobó en la cuenta del club que la plata entró) o lo desmarca. Body `{ "verificado": true }`. Responde el pago con `verificado` y `verificadoAt`. | `400 errors.verificado` · `403` EMPLEADO ("Solo el ADMIN puede verificar un pago con Cuenta DNI.") · `404` reserva o pago · `409` "Solo se verifican los pagos con Cuenta DNI." |
| `GET /admin/pagos/cuenta-dni?desde=2026-10-01&hasta=2026-10-31&verificado=false` | **ADMIN** | Pagos con Cuenta DNI cobrados en el período (por fecha de cobro), del más viejo al más nuevo, para controlarlos contra la cuenta. `verificado=false` trae los pendientes de verificar; sin el parámetro, todos. Responde `[PagoResponse]` (con `reservaId`). | `400` falta una fecha o `desde` posterior a `hasta` · `403` EMPLEADO o CLIENTE |

### Cobro con QR dinámico — `/admin/reservas/{reservaId}/cobros/qr` (rol: ADMIN, EMPLEADO)

El personal genera un QR de Mercado Pago por el monto exacto; el cliente lo paga con la app de Mercado Pago y el pago se registra solo. Detalle, puesta en marcha y prueba en [mercado-pago-qr.md](mercado-pago-qr.md).

**`CobroQrResponse`**
```json
{
  "pagoId": 31, "orderId": "ORD01JQ4S4KY8HWQ6NA5PXB65B3D3", "qrData": "00020101021243650016com.mercadolibre…",
  "monto": 7500.00, "venceAt": "2026-10-13T10:10:00.000-03:00",
  "estado": "EN_PROCESO", "estadoReserva": "PENDIENTE", "montoPendiente": 15000.00
}
```
`qrData` es la trama del QR (EMVCo): el front la convierte en imagen. `estado` es el del pago:
- `EN_PROCESO`: esperando el pago;
- `APROBADO`: cobrado;
- `EXPIRADO`: venció o se canceló;
- `A_REEMBOLSAR`: Mercado Pago cobró otro monto o la reserva ya no estaba activa (hay que devolverlo).

| Método y ruta | Descripción | Errores |
|---|---|---|
| `POST …/cobros/qr` → `200` | Genera el QR. Body opcional `{ "monto": 7500 }`; sin body, por todo lo que falta. Si ya hay un QR activo por el mismo monto, devuelve ese. El pago queda `EN_PROCESO` hasta que se paga, vence (10 min) o se cancela: mientras tanto no se puede cobrar de otra forma, cambiar productos ni cancelar la reserva (`409`). Se acepta en las mismas reservas que un pago en el club, también en partidos. | `400` monto inválido o mayor al pendiente · `404` · `409` QR activo por otro monto ("Ya hay un QR activo para esta reserva: cancelalo antes de generar otro."), reserva que no admite pagos o pago en proceso · `502` Mercado Pago · `503` "El cobro con QR no está configurado." |
| `GET …/cobros/qr` → `200` | El último QR de la reserva, para que el modal muestre el estado (consultarlo cada 3 s, por ejemplo). Solo lee la base. | `404` "La reserva no tiene un cobro con QR." |
| `DELETE …/cobros/qr` → `200` | El empleado cerró el modal: cancela la order. Si Mercado Pago ya la cobró, la registra como cobrada y responde `APROBADO`. | `409` "No hay un QR activo para esta reserva." · `502` |

---

## Partidos y turnos fijos — `/admin/reservas/partidos`, `/admin/turnos-fijos` (rol: ADMIN)

Un **partido** es una reserva `tipo: "PARTIDO"` que arma el ADMIN con **4 jugadores**: clientes registrados (`usuarioId`) o invitados sin cuenta (`nombre` y, opcional, `telefono`). **El jugador 1 tiene que ser un cliente registrado: es el responsable** y el cliente de la reserva (`clienteId`). Si no tiene cuenta, el ADMIN lo busca en `GET /admin/usuarios/clientes?buscar=` y, si no está, se la crea con `POST /admin/usuarios` (con lo mínimo).
- **No vence y no tiene seña.** Sin nada pagado queda `CONFIRMADA`; con algo pagado, `PARCIALMENTE_PAGADA`; entero, `PAGADA` (o `COMPLETADA`). Ocupa la cancha y aparece como deuda si se juega sin pagar entero.
- **Se cobra en el club, como un todo.** Un solo monto general: se cobra y se bonifica como una reserva normal (`cobros/efectivo`, `cobros/presencial`, `bonificacion`), sin indicar el jugador; cómo se lo dividen es cosa de ellos. No se paga online (`409`).
- **Lo armó el ADMIN** (`createdBy`), pero su cliente es el responsable. Los jugadores registrados lo ven en `GET /reservas/mias` y en `GET /reservas/{id}` (sin los teléfonos de los demás), pero no lo pueden pagar ni cancelar.
- **Lo cancela solo el ADMIN** (`DELETE /admin/reservas/{id}/cancelar`, con `reembolso` si hay plata cobrada). Se avisa a los jugadores registrados; a los ADMIN no se les avisa de los pagos ni de la cancelación.

Los partidos se consultan, se cobran y se cancelan como cualquier reserva en `/admin/reservas/{id}`; solo el alta y la modificación tienen rutas propias.

### `POST /admin/reservas/partidos` → `201 ReservaResponse`
```json
{
  "canchaId": 1, "fechaReserva": "2026-10-28", "horaInicio": "18:00", "horaFin": "19:30",
  "jugadores": [
    { "usuarioId": 3 },
    { "usuarioId": 7 },
    { "nombre": "Juan" },
    { "nombre": "Pedro", "telefono": "11 5555-0000" }
  ],
  "productos": [ { "productoId": 1, "cantidad": 1 } ]
}
```
- `jugadores`: exactamente 4, en orden (la posición es el lugar en la lista). Cada uno con `usuarioId` (un CLIENTE activo, sin repetir) o `nombre` (2 a 100 caracteres). El **jugador 1 tiene que ser un cliente registrado** (`400` "El jugador 1 tiene que ser un cliente registrado: es el responsable del partido.").
- Mismas reglas de horario, solapamiento, turnos fijos y stock que `POST /reservas`. El precio es el de siempre (cancha + productos).
- Errores: `400` datos o jugadores inválidos · `403` no es ADMIN · `404` cancha · `409` turno ocupado (también por un turno fijo) o sin stock.

### `PUT /admin/reservas/partidos/{id}` → `200 ReservaResponse`
Mismo body, completo. Cambia jugadores, cancha, fecha, horario o productos de un partido que todavía no se jugó.
- Los jugadores se actualizan **por posición**; se puede reemplazar a cualquiera, aunque el partido tenga pagos. El jugador 1 sigue siendo el responsable (si cambia, cambia el cliente de la reserva).
- Si cambia el turno se recalcula el precio de la cancha; si no, se mantiene. El total nuevo no puede quedar por debajo de lo ya pagado (`409`). El estado se recalcula con lo pagado.
- Un partido de un turno fijo no cambia de fecha (`400`): se da de baja esa fecha y se arma un partido aparte.
- Errores: `400` no es un partido o datos inválidos · `409` partido jugado/cancelado, turno ocupado o total menor a lo pagado.

### Jugadores (`ReservaResponse.jugadores`)
```json
"jugadores": [
  { "id": 41, "posicion": 1, "usuarioId": 3, "nombre": "Ana López", "telefono": "5491122334455", "invitado": false },
  { "id": 43, "posicion": 3, "usuarioId": null, "nombre": "Juan", "telefono": null, "invitado": true }
]
```
- No hay montos por jugador: lo que falta pagar es el `montoPendiente` del partido.
- `telefono`: el del cliente registrado o el del invitado. **Solo para el personal**: a un jugador le llega `null`.

### Turnos fijos
Un **turno fijo** es el mismo partido (cancha, día de la semana, horario y jugadores) todas las semanas desde `desde`, hasta que se da de baja.
- **Bloqueo del horario.** El horario queda bloqueado todas las semanas: un cliente no lo puede reservar (`409` "Ese horario es un turno fijo del club en esa cancha.") y la disponibilidad lo muestra ocupado, aunque falten meses.
- **Partidos semanales.** Los crea un job cada hora **hasta el tope para reservar** de la configuración (hoy + 30 días por defecto, `GET/PUT /admin/configuracion/reservas`), con los jugadores y productos del turno fijo y el precio vigente. Cada semana se paga aparte. Al crear o modificar el turno fijo se crean en el momento. A medida que pasan los días se suman las fechas nuevas; si el ADMIN achica el tope, los partidos ya creados se mantienen.
- **Fechas omitidas.** Si una fecha no se puede crear (otra reserva ya ocupa ese horario, el club no abre, falta stock), se informa en `fechasOmitidas` y el job lo vuelve a intentar.

**`TurnoFijoResponse`**
```json
{
  "id": 8, "canchaId": 2, "canchaNumero": 2, "diaSemana": "THURSDAY",
  "horaInicio": "20:00", "horaFin": "21:30", "desde": "2026-10-29",
  "productos": [ { "productoId": 1, "nombre": "Pelotas", "cantidad": 1 } ], "activo": true,
  "createdAt": "2026-10-25T10:00:00.000-03:00", "dadoDeBajaAt": null,
  "jugadores": [ { "posicion": 1, "usuarioId": 3, "nombre": "Ana López", "telefono": "5491122334455", "invitado": false } ],
  "proximosPartidos": [
    { "reservaId": 70, "fecha": "2026-10-29", "estado": "CONFIRMADA", "montoTotal": 15000.00, "montoPendiente": 15000.00 },
    { "reservaId": 71, "fecha": "2026-11-05", "estado": "CANCELADA", "montoTotal": 15000.00, "montoPendiente": 0 }
  ],
  "fechasOmitidas": [ { "fecha": "2026-11-12", "motivo": "El horario solicitado se superpone con un turno ya reservado en esa cancha." } ],
  "partidosSinCambiar": null
}
```
`proximosPartidos` son los partidos creados cuyo turno todavía no empezó, incluidas las fechas dadas de baja (`CANCELADA`). `fechasOmitidas` viene solo al crear o modificar; `partidosSinCambiar`, solo al modificar.

| Método y ruta | Descripción | Errores |
|---|---|---|
| `GET /admin/turnos-fijos?activos=true` → `200 [TurnoFijoResponse]` | `activos=true` solo activos, `false` solo dados de baja, sin el parámetro todos. | |
| `GET /admin/turnos-fijos/{id}` → `200` | Detalle con sus próximos partidos. | `404` |
| `POST /admin/turnos-fijos` → `201` | Body `{ "canchaId", "desde", "horaInicio", "horaFin", "jugadores": [4], "productos": [{ "productoId", "cantidad" }] }` (los productos de cada partido). El día de la semana es el de `desde` (hoy o futuro). El horario se valida en la primera fecha que se juega. | `400` datos o jugadores inválidos, horario fuera de apertura · `404` cancha · `409` otro turno fijo activo superpuesto en esa cancha y día |
| `PUT /admin/turnos-fijos/{id}` → `200` | Mismo body, completo (un `desde` que ya pasó se acepta si no cambia). Los partidos futuros **sin pagos** se vuelven a crear con los datos nuevos; los que **tienen pagos** quedan como estaban y vienen en `partidosSinCambiar` (se editan uno por uno con `PUT /admin/partidos/{id}`). Las fechas dadas de baja siguen libres. | igual · `409` dado de baja |
| `DELETE /admin/turnos-fijos/{id}?reembolso=TOTAL\|SENIA\|NADA` → `200` | **Baja general**: deja de bloquear el horario y cancela los partidos que todavía no empezaron (aviso a los jugadores). `reembolso` es obligatorio si alguno tiene plata cobrada. Los ya jugados no cambian. | `400` falta el reembolso · `409` ya dado de baja |
| `DELETE /admin/turnos-fijos/{id}/fechas/{fecha}?reembolso=…` → `200 ReservaResponse` | **Baja de una fecha** (esa semana no vienen): cancela el partido de esa fecha o, si todavía no se creó, la registra como cancelada. Ese horario queda libre para otros y no se vuelve a crear. | `400` el turno fijo no se juega esa fecha o falta el reembolso · `409` fecha ya dada de baja o turno empezado |

---

## Panel del personal — `/admin/reservas` y `/admin/usuarios/clientes` (rol: ADMIN, EMPLEADO)

### `GET /admin/reservas` → `200 ListadoReservasResponse`
Reservas pasadas y futuras de cualquier cliente, para consultarlas y cobrar en el club. Todos los parámetros son opcionales y se combinan (se cumplen todos a la vez).

| Parámetro | Valores | Por defecto | Qué hace |
|---|---|---|---|
| `desde`, `hasta` | `yyyy-MM-dd` | sin límite | Fecha del turno (`fechaReserva`), inclusive. Un día: `desde` = `hasta` |
| `estado` | `EstadoReserva` separados por coma | todos | Ej. `estado=SEÑADA,PAGADA`. **Incluye canceladas y vencidas** (a diferencia de `/reservas/mias`) |
| `clienteId` | id | — | Reservas de un cliente |
| `cliente` | texto | — | Busca en nombre, apellido, email o "nombre apellido" del cliente; contiene, sin distinguir mayúsculas ni tildes (`jose` encuentra a "José Pérez") |
| `canchaId` | id | — | Una cancha |
| `tipo` | `NORMAL` \| `PARTIDO` | los dos | Solo reservas normales o solo partidos |
| `pendientes` | `true` \| `false` | `false` | Solo las que deben plata: `CONFIRMADA`, `SEÑADA` y `PARCIALMENTE_PAGADA`, hayan jugado o no (las `PENDIENTE` sin pago no entran). Combinado con `estado`, quedan las que cumplen los dos |
| `orden` | `asc` \| `desc` | `asc` si `desde` es hoy o posterior; `desc` en el resto | Por fecha y hora del turno |
| `page` | ≥ 0 | `0` | Página, desde 0 |
| `size` | 1 a 100 | `20` | Tamaño de página |

```json
{
  "contenido": [ /* ReservaResponse completo, con pagos */ ],
  "pagina": 0, "tamanio": 20, "totalElementos": 57, "totalPaginas": 3,
  "totales": { "reservas": 57, "montoTotal": 855000.00, "montoCobrado": 690000.00,
               "cobradoEfectivo": 410000.00, "cobradoMercadoPago": 250000.00, "cobradoCuentaDni": 30000.00,
               "montoBonificado": 15000.00,
               "montoPendiente": 165000.00 }
}
```

`totales` es del **filtro completo**, no solo de la página:
- `reservas`: cantidad (igual a `totalElementos`).
- `montoTotal`: suma de `montoTotal`.
- `montoCobrado`: lo que el club se quedó de los pagos (aprobado menos lo devuelto o por devolver; de una cancelada sin reembolso, lo pagado).
- `montoBonificado`: lo que el ADMIN bonificó (no es plata y no está en `montoCobrado`).
- `cobradoEfectivo`, `cobradoMercadoPago` y `cobradoCuentaDni`: `montoCobrado` por medio de pago. Efectivo son los `cobros/efectivo`; Mercado Pago, los cobros online (Brick o Wallet) y los del club (`cobros/presencial`); Cuenta DNI, los `cobros/cuenta-dni`. Siempre suman `montoCobrado`.
- `montoPendiente`: lo que deben las `SEÑADA` y `PARCIALMENTE_PAGADA` (las canceladas, vencidas o pendientes no deben).

Errores: `400` si `desde` es posterior a `hasta`, un estado no existe (`errors.estado`), una fecha no tiene el formato, `size` está fuera de 1 a 100, `page` es negativa u `orden` no es `asc`/`desc` · `403` CLIENTE o PROFESOR.

El filtrado, el orden, el paginado y los totales se hacen en la base (índices de V14).

### `GET /admin/usuarios/clientes?buscar=perez&soloActivos=true` → `200 [ ClienteBusquedaResponse ]`
Búsqueda de clientes para el filtro por cliente y para reservar a nombre de uno (`POST /admin/reservas` con `clienteId`).

| Parámetro | Obligatorio | Qué hace |
|---|---|---|
| `buscar` | Sí, mínimo 2 caracteres | Nombre, apellido, email o "nombre apellido"; contiene, sin distinguir mayúsculas ni tildes |
| `soloActivos` | No (`true`) | `false` incluye las cuentas desactivadas |

Devuelve hasta 20 clientes ordenados por apellido y nombre. **Solo usuarios CLIENTE** (nunca ADMIN, EMPLEADO ni PROFESOR).

```json
[ { "id": 7, "nombre": "José", "apellido": "Pérez", "email": "jose@mail.com", "telefono": "5491122334455",
    "telefonoVerificado": true, "categoria": "Cuarta", "posicion": "Drive", "perfilCompleto": true, "enabled": true } ]
```

`perfilCompleto: false` avisa que no se le puede reservar todavía (le falta el celular verificado, la categoría o la posición).

Errores: `400 errors.buscar` si falta · `400` si tiene menos de 2 caracteres · `403` CLIENTE o PROFESOR.

### El resto de `/admin/reservas`

| Método y ruta | Descripción | Errores |
|---|---|---|
| `POST /admin/reservas` → `201` | Reserva a nombre de un cliente. Mismo body que `POST /reservas` más `clienteId` (obligatorio: un CLIENTE habilitado con el perfil completo) y `bonificacion` (opcional, **solo ADMIN**: se crea y se bonifica en la misma operación, igual que `POST /admin/reservas/{id}/bonificacion`; `monto` null bonifica el total). | Los de `POST /reservas` · `400` "ADMIN y EMPLEADO deben indicar el clienteId de la reserva.", "El usuario indicado no es un cliente." o cuenta desactivada · `403` bonificación de un EMPLEADO (no se crea nada) |
| `GET /admin/reservas/{id}` → `200` | Detalle de cualquier reserva, con los pagos. | `404` |
| `DELETE /admin/reservas/{id}/cancelar?reembolso=TOTAL\|SENIA\|NADA` → `200 ReservaResponse` | Cancela cualquier reserva hasta que termina el turno. `reembolso` es obligatorio si tiene dinero cobrado. Un partido lo cancela solo el ADMIN. | `400` reembolso faltante · `403` EMPLEADO con un partido · `404` · `409` como en `/reservas` |
| `GET /admin/reservas/saldos-pendientes` → `200` | Turnos ya jugados que deben saldo (`SEÑADA` o `PARCIALMENTE_PAGADA` con el turno terminado), del más viejo al más nuevo. El listado con `?pendientes=true` también los muestra. | |
| `GET /admin/reservas/devoluciones-pendientes` → `200` | Reservas cuyo turno ya empezó y tienen paletas sin devolver. | |
| `POST /admin/reservas/{id}/devolucion` → `200` | Body `{ "paletasDevueltas": 3 }`. Registra la devolución; las faltantes se descuentan del stock. Devuelve la reserva. | `400` cantidad fuera de rango · `404` · `409` turno sin empezar, reserva sin paletas, no confirmada o devolución ya registrada |
| `POST /admin/reservas/{id}/productos` → `200 ReservaResponse` | Suma un producto a una reserva o partido: lo que el cliente pide en el club antes, durante o después del turno. Body `{ "productoId": 1, "cantidad": 2 }` o, para algo que no está en el catálogo, `{ "nombre": "Gatorade", "precio": 3000, "cantidad": 1 }`: si ya hay un producto con ese nombre (sin importar mayúsculas) se usa ese, con su precio; si no, queda dado de alta como producto de venta, sin control de stock y sin ofrecerse al reservar. Si la reserva ya lo tenía, suma unidades al mismo precio que ya tenía; uno nuevo toma el precio de hoy. El total sube y el estado se recalcula: una reserva paga (o completada) vuelve a quedar con saldo. | `400` sin producto ni nombre y precio, o producto dado de baja · `404` reserva o producto · `409` reserva pendiente, cancelada o expirada, pago online en proceso o sin stock |
| `DELETE /admin/reservas/{id}/productos/{productoId}?cantidad=1` → `200 ReservaResponse` | Saca unidades de un producto (sin `cantidad`, todas). El stock de venta vuelve y el total baja; no puede quedar por debajo de lo ya pagado. | `400` la reserva no tiene ese producto o cantidad fuera de rango · `404` · `409` como al sumar, total menor a lo pagado o un alquiler con la devolución ya registrada |

Cobrar en el club la seña, el saldo o el total: `POST /admin/reservas/{reservaId}/cobros/efectivo` y `/cobros/presencial`, body `{ "monto": 7500 }`. Aceptan reservas `SEÑADA` y `PARCIALMENTE_PAGADA` aunque el turno no se haya jugado, y `PENDIENTE` mientras esté en plazo (15 min).

---

## Resumen del club — `/admin/reservas/resumen` (rol: ADMIN, EMPLEADO)

### `GET /admin/reservas/resumen?desde=2026-10-01&hasta=2026-10-31` → `200 ResumenResponse`
Resumen del período para el panel del club. Las fechas son inclusive (`yyyy-MM-dd`) y van las dos juntas o ninguna: **sin fechas, el resumen es del día de hoy**.

- `jugados`, `porJugar` y `cancelaciones` toman las **reservas cuyo turno (`fechaReserva`) cae en el período**.
- `caja` toma los **pagos cobrados y devueltos en el período**, sea cual sea la fecha del turno, más las **ventas sueltas** del período: es el cierre de caja. Una seña pagada hoy para un turno del mes que viene está en la caja de hoy y en `porJugar` del período del turno.
- `ventas`: las ventas sueltas del período (cuántas y cuánto se cobró); ya están sumadas en `caja`.
- `productos` suma lo pedido en las reservas del período y lo vendido suelto en esas fechas.

| Rol | Qué puede ver |
|---|---|
| ADMIN | Cualquier período (hasta 366 días) |
| EMPLEADO | **Solo el día de hoy**: `GET /admin/reservas/resumen` sin fechas, o con `desde` y `hasta` iguales a hoy. Otro período → `403` "Solo podés ver el resumen de hoy." |

```json
{
  "desde": "2026-10-01", "hasta": "2026-10-31",

  "jugados": {
    "turnos": 28,
    "montoTotal": 420000.00,
    "montoCobrado": 380000.00,
    "montoBonificado": 15000.00,
    "deuda": 25000.00
  },
  "porJugar": {
    "turnos": 6,
    "montoTotal": 90000.00,
    "cobradoAnticipado": 45000.00,
    "montoBonificado": 0.00,
    "aCobrar": 45000.00
  },
  "caja": {
    "total": 230000.00,
    "efectivo": 90000.00,
    "mercadoPago": 100000.00,
    "cuentaDni": 40000.00,
    "devoluciones": 7500.00,
    "neto": 222500.00
  },
  "cancelaciones": { "turnos": 3, "retenido": 7500.00 },
  "ventas": { "cantidad": 12, "total": 30000.00 },
  "productos": [ { "productoId": 1, "nombre": "Pelotas", "tipo": "VENTA", "cantidad": 40, "monto": 200000.00 } ]
}
```

**Turnos del período.** Son las reservas `SEÑADA`, `PARCIALMENTE_PAGADA`, `PAGADA` o `COMPLETADA` (no cuentan `PENDIENTE`, `CANCELADA` ni `EXPIRADA`). Un turno está **jugado** si ya terminó (`fechaReserva` + `horaFin` ≤ ahora, en hora de Argentina) y **por jugar** si no. Si `hasta` es anterior a hoy, `porJugar` queda en 0; si `desde` es posterior a hoy, `jugados` queda en 0.

| Campo | Cómo se calcula |
|---|---|
| `jugados.turnos` / `porJugar.turnos` | Cantidad de reservas |
| `jugados.montoTotal` / `porJugar.montoTotal` | Suma de `montoTotal` (cancha + productos) |
| `jugados.montoCobrado` / `porJugar.cobradoAnticipado` | La plata que el club se quedó de esas reservas: pagos aprobados menos lo devuelto o por devolver, sin bonificaciones. Es la suma de `montoPagado` de cada reserva |
| `jugados.montoBonificado` / `porJugar.montoBonificado` | Lo que el ADMIN bonificó en esas reservas (no es plata) |
| `jugados.deuda` | `montoTotal − montoCobrado − montoBonificado`: turnos jugados que no se terminaron de pagar |
| `porJugar.aCobrar` | `montoTotal − cobradoAnticipado − montoBonificado`: el saldo normal de los turnos que vienen |

**Caja** (por fecha de pago, de las 00:00 de `desde` a las 24:00 de `hasta`):

| Campo | Cómo se calcula |
|---|---|
| `efectivo` | Monto completo de los pagos `MANUAL` (`cobros/efectivo`) cobrados en el período, más las ventas sueltas en `EFECTIVO` |
| `mercadoPago` | Pagos `MERCADO_PAGO` cobrados en el período, online o en el club, más las ventas sueltas con `MERCADO_PAGO` |
| `cuentaDni` | Pagos `CUENTA_DNI` (`cobros/cuenta-dni`) cobrados en el período, más las ventas sueltas con `CUENTA_DNI` |
| `total` | `efectivo + mercadoPago + cuentaDni` |
| `devoluciones` | Lo devuelto (`montoReembolsado`) de los pagos `DEVUELTO` cuyo reembolso se hizo en el período |
| `neto` | `total − devoluciones` (puede ser negativo un día con devoluciones y sin cobros) |

- Entran los pagos que alguna vez se cobraron (`APROBADO`, `DEVUELTO`, `A_REEMBOLSAR`, `REEMBOLSO_PENDIENTE`) por su `paidAt`, con el monto completo: lo devuelto se resta en `devoluciones` el día del reembolso. Las bonificaciones no entran nunca.
- Lo que queda `A_REEMBOLSAR` (por ejemplo, un pago en efectivo de una reserva cancelada con reembolso, que el personal devuelve a mano) **no** está en `devoluciones`: el sistema no registra cuándo se entregó esa plata. Sí está en `total` el día que se cobró.
- Datos anteriores a la migración V16: no se guardaba la fecha del reembolso. Para esos pagos se usó la fecha en que se pidió el reembolso a Mercado Pago si el primer intento falló; si no, la fecha de cobro (o de creación del pago). Las devoluciones viejas pueden aparecer el día del cobro en vez del día en que se devolvieron.

**Cancelaciones y productos**:

| Campo | Cómo se calcula |
|---|---|
| `cancelaciones.turnos` | Reservas `CANCELADA` del período (las `EXPIRADA` no cuentan) |
| `cancelaciones.retenido` | De esas reservas, lo pagado que el club no devolvió (sin bonificaciones) |
| `ventas.cantidad` / `ventas.total` | Ventas sueltas registradas en el período (por fecha de la venta) y lo cobrado |
| `productos` | Por producto y en orden de id, unidades y monto (con el precio congelado en cada reserva o venta) de las reservas `CONFIRMADA`, `SEÑADA`, `PARCIALMENTE_PAGADA`, `PAGADA` y `COMPLETADA` del período, más las ventas sueltas del período; solo los que tienen cantidad > 0. Las paletas cuentan las alquiladas |

- Montos con 2 decimales; fechas `yyyy-MM-dd`.
- Todo se calcula con consultas agregadas en la base.
- Errores: `400` si viene una sola de las dos fechas · `400 errors.desde` / `errors.hasta` si una fecha no tiene el formato · `400` si `desde` es posterior a `hasta` · `400` si el período supera los 366 días · `403` si un EMPLEADO pide otro período que no sea hoy · `403` para CLIENTE.

---

## Ventas sueltas — `/admin/ventas` (rol: ADMIN, EMPLEADO)

Lo que el club vende sin reserva, partido ni turno (una bebida, un tubo de pelotas). Se cobra en el momento y entra en la caja del día. Lo que se consume en un turno se carga en la reserva (`POST /admin/reservas/{id}/productos`), no acá.

**`VentaResponse`**
```json
{
  "id": 4, "fecha": "2026-10-13T10:00:00.000-03:00", "total": 7500.00, "medio": "EFECTIVO",
  "clienteId": 3, "clienteNombre": "Juan Pérez", "registradaPor": "Carla Méndez",
  "items": [
    { "productoId": 5, "nombre": "Agua mineral", "cantidad": 3, "precioUnitario": 1500.00, "subtotal": 4500.00 },
    { "productoId": 9, "nombre": "Gatorade", "cantidad": 1, "precioUnitario": 3000.00, "subtotal": 3000.00 }
  ]
}
```

| Método y ruta | Descripción | Errores |
|---|---|---|
| `POST /admin/ventas` → `201 VentaResponse` | Registra una venta ya cobrada. Body abajo. | ver abajo |
| `GET /admin/ventas?desde=2026-10-01&hasta=2026-10-31` → `200 [VentaResponse]` | Ventas del período (por fecha de la venta), de la más nueva a la más vieja. Sin fechas, las de hoy. El ADMIN ve cualquier período; el EMPLEADO, solo hoy. | `400` una sola fecha o `desde` posterior a `hasta` · `403` EMPLEADO con otro período ("Solo podés ver las ventas de hoy.") o CLIENTE |

Body de `POST /admin/ventas`:
```json
{
  "medio": "EFECTIVO",
  "clienteId": 3,
  "items": [
    { "productoId": 5, "cantidad": 3 },
    { "nombre": "Gatorade", "precio": 3000, "cantidad": 1 }
  ]
}
```
- `medio`: `EFECTIVO`, `MERCADO_PAGO` o `CUENTA_DNI`. Obligatorio.
- `clienteId`: opcional; si viene, tiene que ser un CLIENTE (`400` "El usuario indicado no es un cliente.").
- `items`: de 1 a 20; cada uno con `cantidad` de 1 a 99 y un producto del catálogo (`productoId`) o uno escrito a mano (`nombre` de 2 a 100 caracteres y `precio`). A mano: si ya hay un producto con ese nombre (sin importar mayúsculas) se usa ese, con su precio; si no, queda dado de alta como producto de venta, sin control de stock y sin ofrecerse al reservar (el ADMIN lo puede editar después). Un producto repetido suma sus cantidades.
- Se cobra el precio de hoy de cada producto. El stock baja en los que lo controlan.
- Errores: `400` validación (`errors.items`, `errors.medio`…), ítem sin producto ni nombre y precio ("Indicá el producto (productoId) o su nombre y precio."), un alquiler ("Paletas se alquila con un turno: no se vende suelto.") o un producto dado de baja · `404` producto o cliente inexistente · `409` sin stock ("Agua mineral: hay 2 unidades en stock.") · `403` CLIENTE.

---

## Formato de errores

```json
{ "timestamp": "2026-10-12T17:00:00.000-03:00", "error": "mensaje" }
{ "timestamp": "2026-10-12T17:00:00.000-03:00", "errors": { "campo": "mensaje" } }
```

| Situación | HTTP | Forma |
|---|---|---|
| Validación `@Valid`, parámetro faltante o de tipo inválido | 400 | `errors{campo}` |
| JSON mal formado o valor de enum inexistente | 400 | `error` |
| `EmailEnUso`, `PasswordInvalida`, `TerminosNoAceptados`, `CodigoInvalido` | 400 | `errors.email` / `password` / `terminos` / `codigo` |
| Horario, duración o monto inválidos; día sin apertura | 400 | `error` |
| Sin token, token vencido o inválido; credenciales inválidas | 401 | `error` |
| Firma del webhook inválida | 401 | sin body |
| Sin permisos para el rol; cuenta desactivada; email sin verificar; perfil incompleto; categoría no editable | 403 | `error` / `errors.email_verificado` |
| Reserva, cancha, usuario o recurso inexistente (o reserva ajena) | 404 | `error` |
| Método HTTP no soportado | 405 | `error` |
| Turno ocupado; estado de reserva o pago que no permite la operación; conflicto de datos; cuenta ya verificada | 409 | `error` / `errors.cuenta_verificada` |
| Demasiados pedidos a `/auth` | 429 | `error` + header `Retry-After` |
| Falla de Mercado Pago | 502 | `error` |
| Error inesperado | 500 | `error` genérico (el detalle queda en el log) |
