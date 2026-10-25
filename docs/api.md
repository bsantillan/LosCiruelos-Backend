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
| `EstadoReserva` | `PENDIENTE`, `SEÑADA`, `PARCIALMENTE_PAGADA`, `PAGADA`, `CANCELADA`, `EXPIRADA`, `COMPLETADA` |
| `EstadoPago` | `PENDIENTE`, `APROBADO`, `RECHAZADO`, `EN_PROCESO`, `DEVUELTO`, `EXPIRADO`, `A_REEMBOLSAR`, `REEMBOLSO_PENDIENTE` |
| `PaymentProvider` | `MERCADO_PAGO`, `MANUAL`, `BONIFICACION` (el ADMIN no le cobró al cliente: no es plata) |
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
| `POST /admin/usuarios` → `201` | Crea un usuario con contraseña temporal aleatoria (12 caracteres) y le manda un email de bienvenida con la contraseña y un código de verificación. Body: `email`, `nombre`, `apellido`, `telefono`, `rol` (obligatorios); `categoria` y `posicion` obligatorias si `rol = CLIENTE`. El usuario queda habilitado y con el email sin verificar. |
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
  "turnosDisponibles": [ { "horaInicio": "08:00", "horaFin": "09:30" }, { "horaInicio": "08:30", "horaFin": "10:00" } ]
}
```
- `turnosDisponibles`: turnos de la duración pedida que se pueden reservar, cada 30 min desde la apertura, sin solaparse con ninguno ocupado. Usa las mismas reglas que `POST /reservas`.
- `montoCancha`: precio de la cancha para esa duración (sin pelotas ni paletas). Se calcula con el mismo método que usa la reserva (`montoReserva + montoXMediaHora × duración / 30`), así que coincide con el `montoCancha` que se cobra.
- `duracion` es opcional (por defecto, la mínima); tiene que ser múltiplo de 30 y estar entre el mínimo y el máximo.
- Si la fecha es hoy, `horarioMinimoInicio` es el próximo bloque disponible (`null` si ya no quedan turnos ese día).
- Errores: `400` fecha pasada, duración inválida o día sin apertura; `404` cancha inexistente.

---

## Reservas — `/reservas` (rol: CLIENTE, ADMIN, EMPLEADO)

Un CLIENTE solo ve y opera sus propias reservas. ADMIN y EMPLEADO, cualquiera. Una reserva ajena responde `404`.

**`ReservaResponse`**

`montoPagado` es solo plata cobrada; `montoBonificado`, lo que el ADMIN bonificó; `montoPendiente = montoTotal − montoPagado − montoBonificado`. En `pagos`, una bonificación aparece con `provider: "BONIFICACION"` y su `motivo`.
```json
{
  "id": 10, "canchaId": 1, "canchaNumero": 1,
  "fechaReserva": "2026-10-15", "horaInicio": "18:00", "horaFin": "19:30",
  "montoCancha": 15000.00, "montoProductos": 9000.00,
  "montoTotal": 24000.00, "montoSenia": 12000.00, "montoPagado": 0, "montoBonificado": 0,
  "montoPendiente": 24000.00,
  "cantidadPelotas": 2, "cantidadPaletas": 3,
  "productos": [
    { "productoId": 1, "codigo": "PELOTAS", "nombre": "Pelotas", "tipo": "VENTA",
      "cantidad": 2, "precioUnitario": 1500.00, "subtotal": 3000.00 },
    { "productoId": 2, "codigo": "PALETAS", "nombre": "Paletas", "tipo": "ALQUILER",
      "cantidad": 3, "precioUnitario": 2000.00, "subtotal": 6000.00,
      "cantidadDevuelta": null, "devueltaAt": null }
  ],
  "estado": "PENDIENTE",
  "clienteId": 3, "clienteNombre": "Juan Pérez",
  "createdAt": "2026-10-12T17:00:00.000-03:00", "expiresAt": "2026-10-12T17:15:00.000-03:00",
  "pagos": [ { "id": 5, "monto": 7500.00, "estado": "PENDIENTE", "provider": "MERCADO_PAGO" } ]
}
```

| Método y ruta | Descripción | Errores |
|---|---|---|
| `POST /reservas` → `201` | Crea una reserva `PENDIENTE` que vence en 15 min si no se paga. Body abajo. | `400` horario o datos inválidos · `403` perfil incompleto · `404` cancha · `409` turno ocupado, sin stock o reserva con pago en curso |
| `GET /reservas/mias` → `200` | Reservas del usuario autenticado, **paginadas y filtradas** (ver abajo). | `400` parámetros inválidos |
| `GET /reservas/{id}` → `200` | Detalle con los pagos. Sirve para consultar el resultado de un pago en proceso. | `404` |
| `DELETE /reservas/{id}/cancelar` → `200 ReservaResponse` | Cancela la reserva y hace los reembolsos según la política (ver abajo). ADMIN y EMPLEADO indican `?reembolso=TOTAL\|SENIA\|NADA`. Devuelve la reserva con los pagos actualizados. | `400` reembolso faltante o elegido por un cliente · `404` · `409` turno empezado/terminado, reserva finalizada o pago en proceso. Si Mercado Pago no acepta el reembolso, **se cancela igual** (`200`) y el pago queda `REEMBOLSO_PENDIENTE` |
| `GET /reservas/devoluciones-pendientes` → `200` | **ADMIN, EMPLEADO.** Reservas cuyo turno ya empezó y tienen paletas sin devolver. | |
| `GET /reservas/saldos-pendientes` → `200` | **ADMIN, EMPLEADO.** *Se mantiene por compatibilidad: el panel usa `GET /admin/reservas?pendientes=true`.* Turnos ya jugados que deben saldo (`SEÑADA` o `PARCIALMENTE_PAGADA` con el turno terminado), del más viejo al más nuevo. Se cobran con `pago-efectivo` / `pago-presencial`. | `403` cliente |
| `POST /reservas/{id}/devolucion` → `200` | **ADMIN, EMPLEADO.** Body `{ "paletasDevueltas": 3 }`. Registra la devolución; las faltantes se descuentan del stock. Devuelve la reserva. | `400` cantidad fuera de rango · `404` · `409` turno sin empezar, reserva sin paletas, no confirmada o devolución ya registrada |
| `POST /reservas/{id}/expirar` → `200` | Expira una `PENDIENTE` cuyo plazo ya venció (lo mismo que hace el job). | `404` · `409` todavía en plazo o no está pendiente |

### `DELETE /reservas/{id}/cancelar` — política de cancelación

| Quién | Hasta cuándo | Qué se devuelve |
|---|---|---|
| Cliente | hasta que empieza el turno | faltan ≥ `minutosReembolsoTotal` (24 h): **todo lo pagado** · faltan ≥ `minutosReembolsoSenia` (12 h): **la seña** (o lo pagado, si es menos) · menos: **nada** |
| ADMIN / EMPLEADO | hasta que termina el turno | lo que indique en `?reembolso=TOTAL\|SENIA\|NADA` (obligatorio si la reserva tiene dinero cobrado) |

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

Body de `POST /reservas`:
```json
{
  "canchaId": 1,
  "fechaReserva": "2026-10-15",
  "horaInicio": "18:00",
  "horaFin": "19:30",
  "clienteId": null,
  "cantidadPelotas": 2,
  "cantidadPaletas": 3,
  "bonificacion": { "monto": null, "motivo": "socio" }
}
```
- `bonificacion` (opcional, **solo ADMIN**): la reserva se crea y se bonifica en la misma operación, igual que `POST /pagos/{id}/bonificacion`. `monto` null bonifica el total. Un EMPLEADO o CLIENTE que lo mande recibe `403` y no se crea nada.
- `fechaReserva`: hoy o futuro. `clienteId`: `null` para un CLIENTE; para ADMIN/EMPLEADO, el id de un CLIENTE habilitado.
- `cantidadPelotas` (0 a 50) y `cantidadPaletas` (0 a 20) son opcionales; por defecto 0. El total es `montoCancha + Σ cantidad × precio unitario`, y la seña se calcula sobre ese total.
- Sin stock suficiente responde `409`, por ejemplo `"Paletas: hay 1 unidad disponible para ese horario."`.
- Los precios unitarios quedan congelados en la reserva: un cambio de precio posterior no la modifica.
- Volver a pedir la misma cancha, fecha e inicio con otra hora de fin u otras cantidades actualiza la reserva pendiente existente (si no hay pago en curso y no se superpone).
- Pedir otro horario en la misma cancha y fecha cancela las pendientes anteriores del cliente.
- Reglas completas en [flujos-de-negocio.md](flujos-de-negocio.md#reservas-y-pagos).

---

## Productos — `/productos` (público)

Catálogo y disponibilidad de pelotas y paletas: también sin iniciar sesión.

| Método y ruta | Descripción |
|---|---|
| `GET /productos` → `200` | Productos activos con precio por unidad, tipo y stock. |
| `GET /productos/disponibilidad?fecha=2026-10-20&horaInicio=18:00&horaFin=19:30` → `200` | Unidades que se pueden pedir de cada producto en ese horario: `[{ "id", "codigo", "nombre", "tipo", "precio", "disponibles" }]`. Sirve para limitar los selectores de cantidad del front. |

## Configuración: stock de productos — `/admin/configuracion/productos` (rol: ADMIN; EMPLEADO puede listar y ajustar stock)

**`ProductoResponse`**
```json
{ "id": 1, "codigo": "PELOTAS", "nombre": "Pelotas", "descripcion": "Pelota de pádel, precio por unidad",
  "tipo": "VENTA", "precio": 1500.00, "stock": 10, "activo": true }
```
- `tipo`: `VENTA` (el stock son las unidades disponibles y baja con cada reserva) o `ALQUILER` (el stock es el total de unidades; se controla cuántas están en uso en cada horario).
- `codigo`: `PELOTAS` y `PALETAS` son los productos del sistema que se piden en la reserva; los que crea el admin tienen `codigo: null`.

| Método y ruta | Descripción | Errores |
|---|---|---|
| `GET /admin/configuracion/productos` → `200` | **ADMIN, EMPLEADO.** Todos los productos, incluidos los inactivos. | |
| `POST /admin/configuracion/productos` → `201` | Crea un producto. Body: `nombre` (obligatorio, único), `descripcion`, `tipo`, `precio` (≥ 0, 2 decimales), `stock` (≥ 0), `activo` (por defecto `true`). | `400` validación · `409` nombre repetido |
| `PUT /admin/configuracion/productos/{id}` → `200` | Reemplaza nombre, descripción, precio, stock y activo. El `tipo` no se puede cambiar. | `400` · `404` · `409` |
| `PATCH /admin/configuracion/productos/{id}/stock` → `200` | **ADMIN, EMPLEADO.** Suma o resta unidades: `{ "ajuste": 12 }` (reposición), `{ "ajuste": -1 }` (rotura). | `404` · `409` si quedaría negativo |
| `DELETE /admin/configuracion/productos/{id}` → `200` | Baja lógica (`activo = false`): deja de poder pedirse; las reservas existentes lo conservan. | `404` |

Pelotas y paletas arrancan con stock de **50 pelotas y 50 paletas** (migración V6) y precio de **$5000 por unidad** (migración V7). El admin los cambia desde `/admin/configuracion/productos`.

---

## Pagos — `/pagos`

**`PagoResponse`**
```json
{
  "id": 5, "reservaId": 10, "monto": 7500.00, "estado": "APROBADO",
  "provider": "MERCADO_PAGO",
  "paymentToken": "<preference_id>",
  "externalPaymentId": "1234567890",
  "createdAt": "2026-10-12T17:01:00.000-03:00", "paidAt": "2026-10-12T17:02:10.000-03:00",
  "montoReembolsado": null,
  "estadoReserva": "SEÑADA",
  "detalle": "accredited"
}
```
`paymentToken` es el id de la preferencia (para inicializar el Brick o el botón de MP); `externalPaymentId`, el id del pago en MP; `estadoReserva`, el estado de la reserva después de la operación; `detalle`, el `status_detail` de MP (solo al cobrar).

| Método y ruta | Rol | Descripción | Errores |
|---|---|---|---|
| `POST /pagos/{reservaId}/iniciar?pagarTotal=false` | CLIENTE (dueño), ADMIN, EMPLEADO | Crea el pago `PENDIENTE` y la preferencia de MP (sirve para el Payment Brick y para el Wallet Brick; vuelve a `FRONTEND_URL/pago/resultado` y vence con la reserva). `pagarTotal=true` cobra todo el saldo; `false` (por defecto) lo que falta para la seña. Si ya hay uno iniciado por el mismo monto, lo devuelve. | `404` · `409` vencida, estado inválido, seña ya paga o pago en proceso · `502` MP |
| `POST /pagos/{reservaId}/pago` | CLIENTE (dueño), ADMIN, EMPLEADO | Cobra con el `formData` del Payment Brick (`token`, `payment_method_id`, `installments`, `issuer_id`, `payer`). El monto lo define el servidor. | `400` datos de tarjeta · `404` · `409` sin pago iniciado o reserva fuera de plazo · `502` MP |
| `POST /pagos/notificacion` | público (Mercado Pago) | Webhook. Acepta `?type=payment&data.id=…`, body `{"type":"payment","data":{"id":…}}` o `?topic=payment&id=…`. Valida `x-signature` si está configurado `MP_WEBHOOK_SECRET`. | `200` también si el pago no existe en MP (se ignora) · `401` firma inválida · `502` error al consultar MP (MP reintenta) |
| `POST /pagos/{reservaId}/bonificacion` | **ADMIN** | Bonifica la reserva cuando no se le cobra al cliente (socio, torneo, cortesía): todo o una parte. Body opcional `{ "monto": 7500, "motivo": "socio" }`; sin `monto` bonifica todo lo que falta. Queda un pago `provider = BONIFICACION` que cubre el monto (la reserva se confirma o queda paga) pero **no es plata**: no cuenta como cobrado, no se devuelve si se cancela y no manda aviso. Se acepta en las mismas reservas que un pago en el club. | `400` monto inválido o mayor al pendiente · `403` EMPLEADO o CLIENTE · `409` reserva paga, vencida o con pago online en proceso |
| `POST /pagos/{reservaId}/pago-efectivo` | ADMIN, EMPLEADO | Pago en efectivo ya aprobado (`provider = MANUAL`). Body `{ "monto": 5000 }`. Si deja la reserva paga y el turno ya terminó, la reserva pasa a `COMPLETADA`. | `400` monto inválido o mayor al pendiente · `409` estado o pago online en proceso |
| `POST /pagos/{reservaId}/pago-presencial` | ADMIN, EMPLEADO | Igual, con tarjeta o QR en el club (`provider = MERCADO_PAGO`). | igual |

---

## Panel del personal — `/admin/reservas` y `/admin/clientes` (rol: ADMIN, EMPLEADO)

### `GET /admin/reservas` → `200 ListadoReservasResponse`
Reservas pasadas y futuras de cualquier cliente, para consultarlas y cobrar en el club. Todos los parámetros son opcionales y se combinan (se cumplen todos a la vez).

| Parámetro | Valores | Por defecto | Qué hace |
|---|---|---|---|
| `desde`, `hasta` | `yyyy-MM-dd` | sin límite | Fecha del turno (`fechaReserva`), inclusive. Un día: `desde` = `hasta` |
| `estado` | `EstadoReserva` separados por coma | todos | Ej. `estado=SEÑADA,PAGADA`. **Incluye canceladas y vencidas** (a diferencia de `/reservas/mias`) |
| `clienteId` | id | — | Reservas de un cliente |
| `cliente` | texto | — | Busca en nombre, apellido, email o "nombre apellido" del cliente; contiene, sin distinguir mayúsculas ni tildes (`jose` encuentra a "José Pérez") |
| `canchaId` | id | — | Una cancha |
| `pendientes` | `true` \| `false` | `false` | Solo las que deben plata: `SEÑADA` y `PARCIALMENTE_PAGADA`, hayan jugado o no (las `PENDIENTE` sin pago no entran). Combinado con `estado`, quedan las que cumplen los dos |
| `orden` | `asc` \| `desc` | `asc` si `desde` es hoy o posterior; `desc` en el resto | Por fecha y hora del turno |
| `page` | ≥ 0 | `0` | Página, desde 0 |
| `size` | 1 a 100 | `20` | Tamaño de página |

```json
{
  "contenido": [ /* ReservaResponse completo, con pagos */ ],
  "pagina": 0, "tamanio": 20, "totalElementos": 57, "totalPaginas": 3,
  "totales": { "reservas": 57, "montoTotal": 855000.00, "montoCobrado": 690000.00,
               "cobradoEfectivo": 410000.00, "cobradoMercadoPago": 280000.00, "montoBonificado": 15000.00,
               "montoPendiente": 165000.00 }
}
```

`totales` es del **filtro completo**, no solo de la página:
- `reservas`: cantidad (igual a `totalElementos`).
- `montoTotal`: suma de `montoTotal`.
- `montoCobrado`: lo que el club se quedó de los pagos (aprobado menos lo devuelto o por devolver; de una cancelada sin reembolso, lo pagado).
- `montoBonificado`: lo que el ADMIN bonificó (no es plata y no está en `montoCobrado`).
- `cobradoEfectivo` y `cobradoMercadoPago`: `montoCobrado` por medio de pago. Efectivo son los `pago-efectivo`; Mercado Pago, los cobros online (Brick o Wallet) y con tarjeta o QR en el mostrador (`pago-presencial`). Siempre suman `montoCobrado`.
- `montoPendiente`: lo que deben las `SEÑADA` y `PARCIALMENTE_PAGADA` (las canceladas, vencidas o pendientes no deben).

Errores: `400` si `desde` es posterior a `hasta`, un estado no existe (`errors.estado`), una fecha no tiene el formato, `size` está fuera de 1 a 100, `page` es negativa u `orden` no es `asc`/`desc` · `403` CLIENTE o PROFESOR.

El filtrado, el orden, el paginado y los totales se hacen en la base (índices de V14).

### `GET /admin/clientes?buscar=perez&soloActivos=true` → `200 [ ClienteBusquedaResponse ]`
Búsqueda de clientes para el filtro por cliente y para reservar a nombre de uno (`POST /reservas` con `clienteId`).

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

### Lo que usa el panel además de esto
| Acción | Endpoint |
|---|---|
| Ver el detalle de cualquier reserva | `GET /reservas/{id}` |
| Cobrar en el club la seña, el saldo o el total | `POST /pagos/{reservaId}/pago-efectivo` y `/pago-presencial`, body `{ "monto": 7500 }`. Aceptan reservas `SEÑADA` y `PARCIALMENTE_PAGADA` aunque el turno no se haya jugado, y `PENDIENTE` mientras esté en plazo (15 min) |
| Cancelar | `DELETE /reservas/{id}/cancelar?reembolso=TOTAL\|SENIA\|NADA` |
| Reservar a nombre de un cliente | `POST /reservas` con `clienteId` (el cliente tiene que tener el perfil completo) |

`GET /reservas/saldos-pendientes` sigue funcionando por compatibilidad, pero el panel usa `GET /admin/reservas?pendientes=true`.

---

## Resumen del club — `/admin/resumen` (rol: ADMIN, EMPLEADO)

### `GET /admin/resumen?desde=2026-10-01&hasta=2026-10-31` → `200 ResumenResponse`
Resumen del período para el panel del club. La base son las **reservas cuyo turno (`fechaReserva`) cae en el período**. Las fechas son inclusive (`yyyy-MM-dd`) y van las dos juntas o ninguna: **sin fechas, el resumen es del día de hoy**.

| Rol | Qué puede ver |
|---|---|
| ADMIN | Cualquier período (hasta 366 días) |
| EMPLEADO | **Solo el día de hoy**: `GET /admin/resumen` sin fechas, o con `desde` y `hasta` iguales a hoy. Otro período → `403` "Solo podés ver el resumen de hoy." |

```json
{
  "desde": "2026-10-01", "hasta": "2026-10-31",
  "montoEsperado": 450000.00,
  "montoCobrado": 390000.00, "cobradoOnline": 240000.00, "cobradoEnClub": 150000.00,
  "cobradoEfectivo": 120000.00, "cobradoMercadoPago": 270000.00, "montoBonificado": 0.00,
  "montoPendiente": 60000.00,
  "retenidoCancelaciones": 7500.00,
  "partidosJugados": 28, "partidosCancelados": 3,
  "productos": [ { "productoId": 1, "nombre": "Pelotas", "tipo": "VENTA", "cantidad": 40, "monto": 200000.00 } ]
}
```

| Campo | Cómo se calcula |
|---|---|
| `montoEsperado` | Suma de `montoTotal` de las reservas `SEÑADA`, `PARCIALMENTE_PAGADA`, `PAGADA` y `COMPLETADA` (no cuentan `CANCELADA`, `EXPIRADA` ni `PENDIENTE`) |
| `montoCobrado` | Lo cobrado de esas reservas: pagos aprobados menos lo devuelto (o por devolver). Es `cobradoOnline` + `cobradoEnClub` |
| `cobradoOnline` | Cobros online de Mercado Pago (Brick o Wallet, con id de pago de MP) |
| `cobradoEnClub` | Cobros registrados en el club: `pago-efectivo` y `pago-presencial` |
| `cobradoEfectivo` | De `montoCobrado`, lo cobrado en efectivo (`pago-efectivo`) |
| `cobradoMercadoPago` | De `montoCobrado`, lo cobrado con Mercado Pago: online y con tarjeta o QR en el mostrador (`pago-presencial`). `cobradoEfectivo + cobradoMercadoPago = montoCobrado`, igual que `cobradoOnline + cobradoEnClub` |
| `montoBonificado` | Lo que el ADMIN bonificó en esas reservas: cubre el turno pero no es plata, así que no está en `montoCobrado` ni en sus divisiones |
| `montoPendiente` | `montoEsperado − montoCobrado − montoBonificado` |
| `retenidoCancelaciones` | De las reservas `CANCELADA`, lo pagado que no se devolvió |
| `partidosJugados` | Reservas `SEÑADA`, `PARCIALMENTE_PAGADA`, `PAGADA` o `COMPLETADA` cuyo turno ya terminó |
| `partidosCancelados` | Reservas `CANCELADA` del período (las `EXPIRADA` no cuentan) |
| `productos` | Por producto, unidades y monto (con el precio congelado en cada reserva) de las reservas `SEÑADA`, `PARCIALMENTE_PAGADA`, `PAGADA` y `COMPLETADA`; solo los que tienen cantidad > 0. Las paletas cuentan las alquiladas |

- Montos con 2 decimales; fechas `yyyy-MM-dd`.
- Todo se calcula con consultas agregadas en la base.
- Errores: `400` si viene una sola de las dos fechas · `400 errors.desde` / `errors.hasta` si una fecha no tiene el formato · `400` si `desde` es posterior a `hasta` · `400` si el período supera los 366 días · `403` si un EMPLEADO pide otro período que no sea hoy · `403` para CLIENTE.

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
