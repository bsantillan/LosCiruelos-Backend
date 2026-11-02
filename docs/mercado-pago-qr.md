# Cobro con QR dinámico de Mercado Pago

El personal cobra en el club con un QR por el monto exacto (seña, saldo o una parte). El cliente lo escanea con la app de Mercado Pago y el pago se registra solo en la reserva. Se usa la **API de Orders** con `type: "qr"` y `config.qr.mode: "dynamic"`.

- Documentación oficial: https://www.mercadopago.com.ar/developers/es/docs/qr-code/overview
- Endpoints y reglas de la app: [api.md](api.md#cobro-con-qr-dinámico--adminreservasreservaidcobrosqr-rol-admin-empleado) y [flujos-de-negocio.md](flujos-de-negocio.md).

## Cómo funciona

1. `POST /admin/reservas/{id}/cobros/qr` crea un pago `EN_PROCESO` y la order en Mercado Pago:
   - `POST /v1/orders` con `X-Idempotency-Key = losciruelos-qr-{uuid del pago}`;
   - `external_reference = reserva-{id}-pago-{id}`, sin datos personales;
   - vence a los `MP_QR_MINUTOS_VENCIMIENTO` minutos (`expiration_time: PT10M`).

   La respuesta trae `qrData` (la trama EMVCo), que el front dibuja como QR.
2. Mientras el QR está activo, la reserva tiene un pago en proceso. No se puede cobrar de otra forma, cambiar los productos ni cancelarla hasta que el QR se pague, venza o se cancele.
3. El cliente paga. Mercado Pago avisa al webhook (`/pagos/notificacion` con `type=order`).
   - La app **no confía en el aviso**: siempre consulta `GET /v1/orders/{id}`, con la reserva bloqueada.
   - `processed` por el mismo monto → `APROBADO` y la reserva se recalcula.
   - `processed` por otro monto, o con la reserva ya cancelada → `A_REEMBOLSAR`, con aviso a los ADMIN.
   - `expired` o `canceled` → `EXPIRADO`.
   - Un aviso repetido no cambia nada, porque el pago ya no está `EN_PROCESO`.
4. **Respaldo.** Si el webhook no llega, el job `ReservaJobs.revisarCobrosQr` consulta cada minuto los QR vencidos hace más de un minuto y los resuelve igual.
5. **El empleado cierra el modal.** `DELETE …/cobros/qr` cancela la order (`POST /v1/orders/{id}/cancel`). Si Mercado Pago dice que ya no se puede (`409`, porque se pagó o el pago está en curso), se consulta la order y se aplica lo que diga.
6. **Reembolsos.** Si la reserva se cancela con reembolso, el cobro con QR se devuelve con `POST /v1/orders/{id}/refund`:
   - total, sin body;
   - parcial, con `transactions: [{ id, amount }]`.

   Si MP no lo acepta en el momento, queda `REEMBOLSO_PENDIENTE` y lo reintenta el job de reembolsos. Un rechazo definitivo queda `A_REEMBOLSAR`, para devolverlo a mano.

## Puesta en marcha (una sola vez por cuenta)

1. **Credenciales.** `MP_ACCESS_TOKEN` es el mismo del pago online. En desarrollo se usa el de la cuenta de prueba "vendedor" (Tus integraciones > Credenciales de prueba).
2. **Sucursal.** `POST https://api.mercadopago.com/users/{user_id}/stores`.
   - Obligatorios: `name`, `external_id` (hasta 60 caracteres, único) y `location` (`street_number`, `street_name`, `city_name`, `state_name`, `latitude`, `longitude`, `reference`).
3. **Caja.** `POST https://api.mercadopago.com/v2/pos`, con `X-Idempotency-Key`.
   - `store_id` o `external_store_id`;
   - `external_id` (hasta 40 caracteres, único): sin él no se pueden crear orders de QR;
   - `config.qr.operating_mode` (`pdv` o `standalone`).
4. **Variables.** `MP_QR_EXTERNAL_POS_ID` = el `external_id` de la caja. Opcional: `MP_QR_MINUTOS_VENCIMIENTO` (por defecto 10). Sin la caja, `cobros/qr` responde `503` y el resto de la app sigue andando.
5. **Webhook.** En Tus integraciones > Webhooks > Configurar notificaciones, se marca el evento **"Order (Mercado Pago)"** con la misma URL del webhook de pagos (`https://<dominio>/pagos/notificacion`). Con credenciales de prueba se configura en "Modo productivo" de esa cuenta de prueba.

## Cómo probar

- **Sin Mercado Pago:** la prueba punta a punta apunta `MP_API_URL` a un stub local que imita la API de Orders y simula el pago (sección 17).
- **Con las cuentas de prueba de Mercado Pago:**
  1. Configurar la app con el token de la cuenta vendedor de prueba y la caja creada con esa cuenta.
  2. Generar un QR desde el panel o con `POST /admin/reservas/{id}/cobros/qr`.
  3. Abrir la app de Mercado Pago en un celular con la cuenta **comprador de prueba** y escanear el QR.
  4. Pagar con saldo o con una tarjeta de prueba. Mastercard `5031 7557 3453 0604` y Visa `4509 9535 6623 3704` (CVV 123, vto 11/30) se aprueban; Mastercard débito `5287 3388 1025 3304` se rechaza.
  5. El modal (`GET …/cobros/qr`) pasa a `APROBADO`. Si el webhook no llega (por ejemplo, la app en una máquina local sin URL pública), lo resuelve el job un minuto después del vencimiento.
  6. Desde el panel de Webhooks se puede "Simular notificación" con el id de la order.

## Pendiente de verificar con Mercado Pago

La documentación oficial (leída el 02/11/2026) no confirma o contradice estos puntos:

| Tema | Qué dice la documentación | Qué hace la app |
|---|---|---|
| **Pagar el QR con Cuenta DNI u otro banco** | No figura. La única interoperabilidad descrita es para pagos con **tarjeta de crédito** desde billeteras externas incorporadas a un programa, y la configuran ellas. | Nada: el QR se paga con la app de Mercado Pago. Para Cuenta DNI está el cobro a mano con comprobante. **Consultarlo con Mercado Pago antes de contar con eso.** |
| Firma `x-signature` de los avisos de QR | La página de QR dice que se valida con la clave secreta. La página general de webhooks dice que las notificaciones de QR **no** se pueden validar con la clave secreta. | No valida la firma de los avisos `order`. No hace falta: nunca usa el contenido del aviso y siempre consulta la order. |
| Monto cobrado en `GET /v1/orders/{id}` | El ejemplo trae `transactions.payments[].paid_amount`, pero con un valor distinto de `amount` y sin explicación. | Usa `paid_amount` si viene y si no, `amount`, y lo compara con el monto del QR. |
| Plazo para reembolsar | 180 días en la guía, 360 en la referencia. | Si MP rechaza el reembolso por plazo, queda `A_REEMBOLSAR`. |
| `items[].unit_measure` | Sin lista de valores (solo el ejemplo `"kg"`). | No manda `items`: en los ejemplos son opcionales. |
| `fixed_amount=true` al crear la caja | Aparece en la guía de prueba, pero no en la tabla de campos de la caja. | No aplica a la app: es al crear la caja. |
