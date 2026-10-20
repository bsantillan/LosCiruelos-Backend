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
| `aviso_reserva_confirmada` | Utilidad | Nueva reserva: {{1}} reservó la cancha {{2}} para el {{3}}, de {{4}}. Pagó {{5}} de {{6}}. |
| `aviso_saldo_cobrado` | Utilidad | Saldo cobrado: la reserva de {{1}} (cancha {{2}}, {{3}}, de {{4}}) quedó paga. Total: {{5}}. |
| `aviso_reserva_cancelada` | Utilidad | Reserva cancelada: {{1}}, cancha {{2}}, {{3}}, de {{4}}. {{5}} |
| `aviso_reembolso_manual` | Utilidad | Atención: hay que devolver {{1}} a mano. Reserva #{{2}} de {{3}}. Motivo: {{4}} |
| `aviso_reembolso_realizado` | Utilidad | Reembolso hecho: Mercado Pago devolvió {{1}} de la reserva cancelada #{{2}} de {{3}}, que había quedado pendiente. |
| `recordatorio_turno` | Utilidad | Hola {{1}}, te recordamos tu turno en Los Ciruelos: cancha {{2}}, {{3}}, de {{4}}. {{5}} |
| `reserva_cancelada_por_club` | Utilidad | Hola {{1}}, el club canceló tu reserva de la cancha {{2}} del {{3}}, de {{4}}. {{5}} |
| `codigo_verificacion` | Autenticación | Plantilla de autenticación de Meta con botón **Copiar código** (Meta arma el texto: "{{1}} es tu código de verificación"). Vencimiento sugerido: 10 minutos |

Ejemplos de valores: `{{3}}` = `miércoles 21/10`, `{{4}}` = `18:00 a 19:30`, montos = `$7.500`.

Los textos están también en `Services/Avisos/TipoAviso.java`: si se cambia una plantilla en Meta, hay que cambiarla ahí (el mail usa el mismo texto).

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
