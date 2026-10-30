# Avisos por WhatsApp y mail

El backend avisa a los ADMIN de lo que pasa con las reservas y le manda al cliente un recordatorio antes del turno. Cada usuario elige en su perfil si los recibe por **mail** o por **WhatsApp** (un solo canal). WhatsApp usa la **API oficial de Meta (Cloud API)**, sin intermediarios.

## Qué se avisa

| Aviso | A quién | Cuándo |
|---|---|---|
| Reserva confirmada | ADMIN | La reserva llega por primera vez a la seña o al total (online o en el club) |
| Saldo cobrado | ADMIN | Una reserva señada queda totalmente paga |
| Reserva cancelada | ADMIN | Cancela el cliente o el personal, o Mercado Pago devuelve todo lo pagado. Dice quién canceló, cuánto se devuelve y cómo salió el reembolso: **exitoso** (Mercado Pago lo devolvió), **pendiente** (MP todavía no lo aceptó; se reintenta 24 h), **fallido** (MP lo rechazó: devolver a mano) o a devolver a mano porque se cobró en el club |
| Reembolso hecho | ADMIN | Un reembolso que había quedado pendiente al cancelar finalmente lo hizo Mercado Pago (va con la preferencia "reserva cancelada") |
| Reembolso con problemas | ADMIN | Un pago queda `A_REEMBOLSAR` fuera de una cancelación (Mercado Pago no pudo devolver en 24 h, cobró otro monto, o se cobró una reserva ya inactiva) |
| Recordatorio | Cliente | 2 h antes del turno (configurable), en reservas señadas, parciales o pagas. Incluye el saldo a pagar. El cliente lo puede desactivar en `PUT /perfil/suscripciones` |
| Cancelada por el club | Cliente | El personal cancela su reserva (si cancela el cliente no se le avisa: ya lo ve en la página) |

- El cliente elige, con el perfil completo, si recibe recordatorios y novedades/promociones; las cancelaciones se le avisan siempre. Las novedades y promociones solo quedan como preferencia: todavía no hay envío (cuando lo haya, por WhatsApp son categoría Marketing, más cara).
- Cada ADMIN elige qué avisos recibe (`PUT /perfil/avisos`); sin elegir nada, recibe todos. También puede **desactivarlos todos** con `activos: false` (por ejemplo en vacaciones) sin perder la elección de cada uno.
- El recordatorio no se manda si la reserva se confirmó dentro de la ventana (el cliente recién reservó), y se manda una sola vez.
- Los avisos se envían **después del commit** y en otro hilo: si la operación falla no se avisa nada, y un error de WhatsApp o del mail no afecta a la reserva.

## Canal de cada usuario

- **En el registro** hay que aceptar recibir notificaciones (`aceptarNotificacion`). No se elige el medio: arranca **por mail**.
- **WhatsApp**: se elige en el perfil (`PUT /perfil/notificaciones`), igual para clientes y admins, normalmente después de validar el celular. Elegirlo es dar el consentimiento que pide Meta (se guarda la fecha en `acepta_whatsapp_at`).
- **Celular verificado (obligatorio)**: al completar el perfil, el usuario pide un código de 6 dígitos que le llega por WhatsApp (`POST /perfil/telefono/codigo`; vence en 10 min, 5 intentos, máximo 3 pedidos por minuto) y lo ingresa (`POST /perfil/telefono/verificar`), elija el medio que elija. Ingresarlo prueba que el número existe, tiene WhatsApp y es del usuario. **Es parte del perfil completo** de clientes y admins: sin él, el cliente no puede reservar. Cambiar el teléfono lo deja sin verificar. Por eso **WhatsApp tiene que estar dado de alta en Meta antes de poner esto en producción**.
- **Regla de envío**: WhatsApp solo si lo eligió, tiene el teléfono verificado y el club tiene WhatsApp configurado. Si no, mail. Si WhatsApp falla, se manda el mail de respaldo.
- Los teléfonos se guardan normalizados en el formato de WhatsApp (`5491123456789`). Se aceptan con o sin `0`, `15`, `+54 9`, espacios y guiones.
- **Topes de códigos** (cada uno cuesta y no se puede usar para molestar a un número ajeno): 1 por minuto y 5 cada 24 h por usuario, y 5 cada 24 h por número entre todas las cuentas. Se guardan en la base (`envios_codigo_telefono`, V12), así que no se pierden al reiniciar. Pasado el tope responde `429` con cuánto falta. Se configuran con `telefono.codigos-por-dia-usuario`, `telefono.codigos-por-dia-numero` y `telefono.segundos-entre-codigos`. En el peor caso, un usuario gasta 5 códigos por día (~US$0,13).
- **Un celular verificado, una cuenta activa**: no se puede registrar ni cargar en el perfil un celular que otra cuenta **activa** ya verificó (`400 errors.telefono`), ni pedir o ingresar su código (`409`). Si la otra cuenta está desactivada sí se puede (el cliente se hizo una cuenta nueva): al verificarlo, la cuenta desactivada pierde la verificación, y si el admin la reactiva tiene que volver a verificar su celular. En la base lo asegura un índice único parcial (V11).
- **Celulares argentinos al enviar**: la API de Meta rechaza el formato `54 9 área número` con `131030` ("Recipient phone number not in allowed list") aunque el número esté autorizado, y acepta `54 área 15 número`. `MetaWhatsAppService` lo convierte al enviar (`Telefonos.paraEnvioWhatsApp`: `5492214375254` → `54221154375254`); en la base se sigue guardando `549…`, que es el `wa_id` que devuelve Meta.

## Alta en Meta (una sola vez)

1. **Portfolio de negocio**: crear uno en [business.facebook.com](https://business.facebook.com) a nombre del club.
2. **App**: en [developers.facebook.com](https://developers.facebook.com) → *Crear app* → tipo *Empresa* → agregar el producto **WhatsApp**. Meta da un **número de prueba** gratis, que sirve para desarrollar (solo puede escribirle a hasta 5 números que se cargan en el panel).
3. **Número del club**: en *WhatsApp > Configuración de la API* → *Agregar número*. Puede ser un chip, una eSIM o un fijo (se verifica con una llamada). No puede estar usado en la app de WhatsApp al mismo tiempo, salvo con la "coexistencia" que se configura por un proveedor autorizado.
4. **Nombre visible**: por ejemplo "Los Ciruelos Pádel". Meta lo revisa.
5. **Verificación del negocio** (recomendada): en el portfolio → *Centro de seguridad*. Sin verificar se le puede escribir a 250 personas distintas por día; verificado, a 2.000 o más.
6. **Token permanente**: en el portfolio → *Usuarios del sistema* → crear uno (rol admin) → *Generar token* para la app, con los permisos `whatsapp_business_messaging` y `whatsapp_business_management`, sin vencimiento.
7. **Variables de entorno** del backend:
   - `WHATSAPP_TOKEN`: el token del paso 6.
   - `WHATSAPP_PHONE_NUMBER_ID`: el *Identificador del número de teléfono* (no es el número).
   Sin estas variables el backend arranca igual y manda todo por mail (lo avisa en el log al iniciar).
8. **Plantillas**: cargarlas como se indica abajo y esperar la aprobación (minutos u horas).

## Plantillas

Se crean en *WhatsApp Manager > Plantillas de mensajes*, idioma **Español (ARG)** (`es_AR`), con el **nombre exacto** y el texto de la tabla. Las variables `{{1}}`, `{{2}}`… van en ese orden. Meta pide un ejemplo para cada variable al crearlas.

| Nombre | Categoría | Texto |
|---|---|---|
| `aviso_reserva_confirmada_v2` | Utilidad | Nueva reserva en Los Ciruelos: {{1}} reservó la cancha {{2}} para el {{3}}, de {{4}}. Pagó {{5}} de un total de {{6}}. Podés ver el detalle en el panel del club. |
| `aviso_saldo_cobrado_v2` | Utilidad | Saldo cobrado en Los Ciruelos: la reserva de {{1}} en la cancha {{2}} para el {{3}}, de {{4}}, quedó totalmente paga. Total cobrado: {{5}}. Podés ver el detalle en el panel del club. |
| `aviso_reserva_cancelada_v2` | Utilidad | Reserva cancelada en Los Ciruelos: {{1}}, cancha {{2}}, el {{3}}, de {{4}}. Detalle: {{5}} Podés ver la reserva en el panel del club. |
| `aviso_reembolso_manual_v2` | Utilidad | Atención: hay que devolver {{1}} a mano por la reserva #{{2}} de {{3}}. Motivo: {{4}} Revisalo en el panel de Mercado Pago o en el panel del club. |
| `aviso_reembolso_realizado_v2` | Utilidad | Reembolso hecho: Mercado Pago devolvió {{1}} de la reserva cancelada #{{2}} de {{3}}, que había quedado pendiente. No hace falta hacer nada más. |
| `recordatorio_turno_v2` | Utilidad | Hola {{1}}, te recordamos tu turno en Los Ciruelos: cancha {{2}}, el {{3}}, de {{4}}. {{5}} Si no podés venir, cancelá la reserva desde la página. ¡Te esperamos! |
| `reserva_cancelada_por_club_v2` | Utilidad | Hola {{1}}, el club canceló tu reserva de la cancha {{2}} del {{3}}, de {{4}}. {{5}} Disculpá las molestias. |
| `codigo_verificacion_v2` | Autenticación | Plantilla de autenticación de Meta con botón **Copiar código** (Meta arma el texto: "{{1}} es tu código de verificación"). Vencimiento sugerido: 10 minutos |

Título, pie y botón de cada plantilla (texto fijo, sin variables: el backend solo manda los parámetros del cuerpo y, en la de autenticación, el código del botón):

| Plantilla | Título | Pie de página | Botón (Visitar sitio web) | URL |
|---|---|---|---|---|
| `aviso_reserva_confirmada_v2` | Nueva reserva | Los Ciruelos Pádel · Aviso para el club | Abrir panel | `https://los-ciruelos.tail83f639.ts.net/admin/reservas` |
| `aviso_saldo_cobrado_v2` | Saldo cobrado | Los Ciruelos Pádel · Aviso para el club | Abrir panel | `https://los-ciruelos.tail83f639.ts.net/admin/reservas` |
| `aviso_reserva_cancelada_v2` | Reserva cancelada | Los Ciruelos Pádel · Aviso para el club | Abrir panel | `https://los-ciruelos.tail83f639.ts.net/admin/reservas` |
| `aviso_reembolso_manual_v2` | Reembolso pendiente de devolver | Revisalo en el panel de Mercado Pago | Abrir Mercado Pago | `https://www.mercadopago.com.ar/activities` |
| `aviso_reembolso_realizado_v2` | Reembolso realizado | Los Ciruelos Pádel · Aviso para el club | Abrir panel | `https://los-ciruelos.tail83f639.ts.net/admin/reservas` |
| `recordatorio_turno_v2` | Recordatorio de tu turno | Los Ciruelos Pádel · Gestioná tus avisos en tu perfil | Ver mis reservas | `https://los-ciruelos.tail83f639.ts.net/mis-reservas` |
| `reserva_cancelada_por_club_v2` | Tu reserva fue cancelada | Los Ciruelos Pádel · Gestioná tus avisos en tu perfil | Ver mis reservas | `https://los-ciruelos.tail83f639.ts.net/mis-reservas` |
| `codigo_verificacion_v2` | — | Lo arma Meta (vencimiento: 10 minutos, igual que el backend) | Copiar código (lo agrega Meta) | — |

Si se cambia el nombre de una plantilla en Meta (por ejemplo a `_v3`), hay que cambiarlo en `TipoAviso.java` y, para el código, en `whatsapp.plantilla-codigo`.

Reglas de Meta al cargarlas: las variables son de tipo **número** (`{{1}}`, `{{2}}`…, no con nombre), **ninguna puede ir al principio ni al final** del texto, y tiene que haber bastante texto fijo por cada variable (si no, Meta responde "demasiadas variables para su longitud"). Si en Meta se carga otro texto, cambialo también en `TipoAviso.java`: el mail de respaldo usa el mismo.

Ejemplos de valores: `{{3}}` = `miércoles 21/10`, `{{4}}` = `18:00 a 19:30`, montos = `$7.500`.

Los textos están también en `notificaciones/aviso/TipoAviso.java`: si se cambia una plantilla en Meta, hay que cambiarla ahí (el mail usa el mismo texto).

## Límites y costos

- **Límite**: personas distintas a las que el club les escribe primero en 24 h móviles (no cantidad de mensajes). Empieza en 250 y sube a 2.000 al verificar el negocio. Lo que se manda dentro de las 24 h después de que la persona le escribió al club no cuenta.
- **Costo**: Meta cobra cada plantilla entregada, según la categoría y el país. En Argentina, Utilidad y Autenticación rondan los US$0,026 por mensaje (confirmar en la [calculadora oficial](https://whatsappbusiness.com/es-la/products/platform-pricing/)). Desde abril de 2026 se puede pagar en pesos.
- **Gratis**: todo lo que se manda dentro de las 24 h después de que la persona le escribió al club. Si el dueño le manda un mensaje al número del club cada mañana, sus avisos de ese día no se cobran.
- **Calidad**: si muchos usuarios bloquean o denuncian el número, Meta baja el límite o lo suspende. Por eso solo se manda a quien eligió WhatsApp.

## Cómo probar

1. Con el número de prueba de Meta: cargar `WHATSAPP_TOKEN` (token temporal de la app sirve) y `WHATSAPP_PHONE_NUMBER_ID`, y agregar el celular propio como destinatario en el panel de Meta.
2. Mientras las plantillas propias no estén aprobadas, Meta solo permite `hello_world`: los avisos van a fallar con un error de plantilla en el log (y salen por mail).
3. Elegir WhatsApp en `PUT /perfil/notificaciones`, verificar el código con `POST /perfil/telefono/verificar` y mandar un aviso de prueba con `POST /perfil/avisos/prueba` (ADMIN).
4. Los errores de Meta quedan en el log con su código (por ejemplo `131026`: el número no tiene WhatsApp; `132001`: la plantilla no existe o no está aprobada en ese idioma).
