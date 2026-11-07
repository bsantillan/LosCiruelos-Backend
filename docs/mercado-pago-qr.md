# Cobro con el QR de la caja de Mercado Pago

El club tiene **un QR de Mercado Pago impreso en el mostrador** (el QR de la caja). Para cobrar una seña, un saldo o una parte, el personal le asigna a la caja ese monto exacto; el cliente escanea el QR con la app de Mercado Pago, ve el monto de su reserva y paga, y el pago se registra solo en la reserva. No se muestra ningún QR en la pantalla del personal.

Se usa la **API de Orders** con `type: "qr"` y `config.qr.mode: "static"`.

- Documentación oficial: https://www.mercadopago.com.ar/developers/es/docs/qr-code/overview
- Endpoints y reglas de la app: [api.md](api.md) y [flujos-de-negocio.md](flujos-de-negocio.md).

## Cómo funciona

1. `POST /admin/reservas/{id}/cobros/qr` crea un pago `EN_PROCESO` y le asigna a la caja la order en Mercado Pago:
   - `POST /v1/orders` con `X-Idempotency-Key = losciruelos-qr-{uuid del pago}`;
   - `external_reference = reserva-{id}-pago-{id}`, sin datos personales;
   - `config.qr.external_pos_id` = la caja (`MP_QR_EXTERNAL_POS_ID`);
   - vence a los `MP_QR_MINUTOS_VENCIMIENTO` minutos (`expiration_time: PT10M`).

   El front muestra "esperando el pago de $X" y el tiempo que queda hasta `venceAt`.
2. **Un cobro a la vez.** La caja tiene un solo QR, así que mientras espera el pago de una reserva no se puede asignar otro cobro (`409`). Un índice único en la base lo garantiza aunque dos empleados lo pidan al mismo tiempo.
3. Mientras el cobro está activo, la reserva tiene un pago en proceso. No se puede cobrar de otra forma, cambiar los productos ni cancelarla hasta que el cobro se pague, venza o se cancele.
4. El cliente paga. Mercado Pago avisa al webhook (`/pagos/notificacion` con `type=order`).
   - La app **no confía en el aviso**: siempre consulta `GET /v1/orders/{id}`, con la reserva bloqueada.
   - `processed` por el mismo monto → `APROBADO` y la reserva se recalcula.
   - `processed` por otro monto, o con la reserva ya cancelada → `A_REEMBOLSAR`, con aviso a los ADMIN.
   - `expired` o `canceled` → `EXPIRADO`.
   - Un aviso repetido no cambia nada, porque el pago ya no está `EN_PROCESO`.
5. **Respaldo.** Si el webhook no llega, el job `ReservaJobs.revisarCobrosQr` consulta cada minuto los cobros vencidos hace más de un minuto y los resuelve igual. Si alguien pide otro cobro antes de que pase el job, el vencido se resuelve en ese momento.
6. **El empleado cierra el modal.** `DELETE …/cobros/qr` cancela la order (`POST /v1/orders/{id}/cancel`) y libera la caja. Si Mercado Pago dice que ya no se puede (`409`, porque se pagó o el pago está en curso), se consulta la order y se aplica lo que diga.
7. **Reembolsos.** Si la reserva se cancela con reembolso, el cobro con QR se devuelve con `POST /v1/orders/{id}/refund`:
   - total, sin body;
   - parcial, con `transactions: [{ id, amount }]`.

   Si MP no lo acepta en el momento, queda `REEMBOLSO_PENDIENTE` y lo reintenta el job de reembolsos. Un rechazo definitivo queda `A_REEMBOLSAR`, para devolverlo a mano.

### Ventas sueltas
Una venta con `medio: MERCADO_PAGO` (`POST /admin/ventas`) usa la misma caja:
- queda `ESPERANDO_PAGO`, con el stock apartado, y la order lleva `external_reference = venta-{id}` y las claves `losciruelos-qr-{uuid de la venta}`;
- el webhook (`type=order`) busca primero un pago de reserva con esa order y, si no hay, una venta (`VentaQrService.notificacion`); el job de respaldo es `VentaJobs.revisarCobrosQr`;
- `processed` por el total → `COBRADA`; por otro monto, `expired`, `canceled` o `refunded` → `NO_COBRADA` y el stock vuelve;
- `DELETE /admin/ventas/{id}/qr` cancela la order;
- al anularla o devolverla, el reembolso va por `/refund` en el momento (total o parcial, con una clave por operación: `losciruelos-qrrefund-{uuid}-anulacion`, `-devolucion-{n}`). Si MP no lo acepta, responde `502` y no se registra nada: se reintenta.

**Una sola caja.** Una reserva y una venta no pueden esperar el QR a la vez. Los dos cobros bloquean primero la fila de `caja_qr` y después la reserva o la venta (siempre en ese orden), y revisan que la caja no esté esperando otro cobro vigente.

## Puesta en marcha (una sola vez por cuenta)

1. **Credenciales.** El QR necesita una aplicación de Mercado Pago de **pagos presenciales con Código QR** (Tus integraciones > Crear aplicación > Pagos presenciales > Código QR). El token de una aplicación de pagos online no sirve: Mercado Pago responde `403` "At least one policy returned UNAUTHORIZED." en sucursales, cajas y orders. Su access token va en `MP_QR_ACCESS_TOKEN`; si las dos cosas fueran de la misma aplicación, alcanza con `MP_ACCESS_TOKEN`. En desarrollo se usa el de la cuenta vendedor de prueba de esa aplicación (Credenciales de prueba).
2. **Sucursal.** `POST https://api.mercadopago.com/users/{user_id}/stores`.
   - Obligatorios: `name`, `external_id` (hasta 60 caracteres, único) y `location` (`street_number`, `street_name`, `city_name`, `state_name`, `latitude`, `longitude`, `reference`).
3. **Caja.** `POST https://api.mercadopago.com/v2/pos`, con `X-Idempotency-Key`.
   - `store_id` o `external_store_id`;
   - `external_id` (hasta 40 caracteres, único): sin él no se pueden crear orders de QR;
   - `config.qr.operating_mode` (`pdv` o `standalone`).

   La respuesta trae `qr_response` con la imagen del QR de esa caja (`image`) y plantillas para imprimir (`template_document`, `template_image`). **Ese QR es el que se imprime y se pone en el mostrador.**
4. **Variables.** `MP_QR_EXTERNAL_POS_ID` = el `external_id` de la caja. Opcional: `MP_QR_MINUTOS_VENCIMIENTO` (por defecto 10). Sin la caja, `cobros/qr` responde `503` y el resto de la app sigue andando.
5. **Webhook.** En Tus integraciones > Webhooks > Configurar notificaciones, se marca el evento **"Order (Mercado Pago)"** con la misma URL del webhook de pagos (`https://<dominio>/pagos/notificacion`). Con credenciales de prueba se configura en "Modo productivo" de esa cuenta de prueba.

## Cómo probar

- **Sin Mercado Pago:** la prueba punta a punta apunta `MP_API_URL` a un stub local que imita la API de Orders y simula el pago (sección 17).
- **Con las cuentas de prueba de Mercado Pago:**
  1. Configurar la app con el token de la cuenta vendedor de prueba y la caja creada con esa cuenta.
  2. Asignar un cobro desde el panel o con `POST /admin/reservas/{id}/cobros/qr`.
  3. Abrir la app de Mercado Pago en un celular con la cuenta **comprador de prueba** y escanear el QR impreso de la caja (el `qr_response.image` de la caja de prueba).
  4. Pagar con saldo o con una tarjeta de prueba. Mastercard `5031 7557 3453 0604` y Visa `4509 9535 6623 3704` (CVV 123, vto 11/30) se aprueban; Mastercard débito `5287 3388 1025 3304` se rechaza.
  5. El modal (`GET …/cobros/qr`) pasa a `APROBADO`. Si el webhook no llega (por ejemplo, la app en una máquina local sin URL pública), lo resuelve el job un minuto después del vencimiento.
  6. Desde el panel de Webhooks se puede "Simular notificación" con el id de la order.

## Pendiente de verificar con Mercado Pago

La documentación oficial (leída el 02/11/2026) no confirma o contradice estos puntos:

| Tema | Qué dice la documentación | Qué hace la app |
|---|---|---|
| **Pagar el QR con Cuenta DNI u otro banco** | No figura. La única interoperabilidad descrita es para pagos con **tarjeta de crédito** desde billeteras externas incorporadas a un programa, y la configuran ellas. | Nada: el QR se paga con la app de Mercado Pago. Para Cuenta DNI está el cobro a mano, que después verifica el ADMIN. **Consultarlo con Mercado Pago antes de contar con eso.** |
| Firma `x-signature` de los avisos de QR | La página de QR dice que se valida con la clave secreta. La página general de webhooks dice que las notificaciones de QR **no** se pueden validar con la clave secreta. | No valida la firma de los avisos `order`. No hace falta: nunca usa el contenido del aviso y siempre consulta la order. |
| Monto cobrado en `GET /v1/orders/{id}` | El ejemplo trae `transactions.payments[].paid_amount`, pero con un valor distinto de `amount` y sin explicación. | Usa `paid_amount` si viene y si no, `amount`, y lo compara con el monto del QR. |
| Plazo para reembolsar | 180 días en la guía, 360 en la referencia. | Si MP rechaza el reembolso por plazo, queda `A_REEMBOLSAR`. |
| `items[].unit_measure` | Sin lista de valores (solo el ejemplo `"kg"`). | No manda `items`: en los ejemplos son opcionales. |
| `fixed_amount=true` al crear la caja | Aparece en la guía de prueba, pero no en la tabla de campos de la caja. | No aplica a la app: es al crear la caja. Probar si hace falta para que el QR impreso muestre el monto de la order. |
| Dos orders en la misma caja | No dice qué pasa si se crea una order en una caja que ya tiene otra esperando el pago. | La app nunca lo hace: un solo cobro esperando a la vez. |
| Escanear el QR sin cobro asignado | No dice qué ve el cliente. | Nada que hacer en la app: el personal asigna el cobro antes de que el cliente escanee. |
