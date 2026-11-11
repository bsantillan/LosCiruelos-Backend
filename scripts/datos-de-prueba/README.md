# Datos de prueba

`datos-de-prueba.sql` deja la base de **desarrollo** con un ecosistema completo para probar el sistema a mano. **Nunca se corre en producción.** Ni la app ni Flyway lo ejecutan: siempre se corre a mano.

Las fechas son **relativas al día y la hora en que se corre**: siempre hay reservas pasadas, de hoy y futuras.

## Qué borra y qué conserva

**Borra todos los datos:** reservas, pagos, partidos, turnos fijos, ventas, devoluciones, días cerrados, avisos, configuración, canchas, productos, el resto de las cuentas y los tokens (sesiones, códigos de verificación). El esquema y `flyway_schema_history` no se tocan.

**Conserva solo estas cuentas, con su contraseña:**

| Cuenta | Qué pasa |
|---|---|
| Los ADMIN (entre ellos el de `ADMIN_EMAIL`) | Se conservan tal cual. Si no había ninguno, lo crea la app al arrancar con `ADMIN_EMAIL`/`ADMIN_PASSWORD`. |
| `empleado@test.com` (EMPLEADO) | Se conserva; si no existe, se crea con `Prueba123!`. |
| El cliente principal (`mail_cliente`, por defecto `cliente@test.com`) | Se conserva; si no existe, se crea con `Prueba123!`, perfil completo y el celular de `tel_cliente`. |

## Qué carga

### Configuración del club
- Turno mínimo de 90 minutos a $15.000 y $5.000 cada media hora de más (hasta 180 minutos); seña del 50 %.
- Cancelación: se devuelve todo hasta 24 h antes y la seña hasta 12 h antes.
- Recordatorio 2 h antes; se reserva hasta 30 días adelante; la seña online que pide el personal se paga dentro de las 2 h.
- Horarios: lunes a viernes de 8 a 23, sábados de 9 a 22 y domingos de 9 a 20.
- Un **día cerrado** dentro de 20 días ("Mantenimiento de las canchas"), sin reservas ese día.
- **4 canchas.**

### Productos (todos controlan stock)

| Producto | Tipo | Para probar |
|---|---|---|
| Pelotas | VENTA, se ofrece al reservar | stock normal |
| Paletas | ALQUILER, se ofrece al reservar | 12 en total; se cuenta lo que está en uso |
| Agua mineral | VENTA | stock normal |
| Gatorade, Overgrip | VENTA | poco stock (3 y 2) |
| Barrita de cereal | VENTA | sin stock |
| Muñequera | VENTA | dada de baja (inactiva) |

### Cuentas
Todas las nuevas entran con la contraseña **`Prueba123!`**, mail `@test.com` y avisos por mail.

| Cuenta | Para qué |
|---|---|
| `ana`, `martin`, `lucia`, `juan`, `camila`, `diego`, `valentina`, `nicolas`, `florencia`, `lucas`, `micaela`, `federico` `@test.com` | 12 clientes con el perfil completo, de distintas categorías y posiciones. |
| `sofia@test.com` | Perfil incompleto: sin categoría ni posición. El personal le reservó igual. |
| `tomas@test.com` | Perfil incompleto: el celular sin verificar. |

Los avisos del ADMIN quedan activos, salvo el de saldo cobrado.

### Reservas
Repartidas en las 4 canchas, entre las 9 y las 20 (valen para cualquier día de la semana), con los 4 medios de cobro (efectivo, Mercado Pago con QR u online, transferencia y Cuenta DNI) y bonificaciones del ADMIN:

- **Pasadas:**
  - pagadas (seña online y saldo en efectivo, QR, transferencia verificada, Cuenta DNI verificada y sin verificar);
  - jugadas con deuda: una señada que **no devolvió 2 paletas** y otra con un pago menor que la seña;
  - una con **2 paletas devueltas** y otra con 2 aguas sumadas después de jugar;
  - canceladas: a tiempo con la devolución por Mercado Pago y tarde con la seña retenida;
  - vencidas: una del cliente que no pagó la seña online y una cargada por el personal que no se cobró.
- **De hoy** (según la hora a la que se corre, solo si el club está abierto): una **en curso** con saldo, una **por jugar pagada** con QR y otra **por jugar señada** con Cuenta DNI sin verificar.
- **Futuras:**
  - señadas (online, transferencia sin verificar, Cuenta DNI verificada y QR) y pagadas (efectivo y una bonificada entera);
  - **una de cada modalidad del personal:** `EN_EL_CLUB` (pendiente, vence a los 15 minutos), `SENIA_ONLINE` (pendiente, vence a las 2 h) y `SIN_SENIA` (confirmada por el ADMIN);
  - una de 2 h con paletas y una pelota;
  - una con un pago parcial;
  - una cancelada a tiempo con la plata a devolver a mano (`A_REEMBOLSAR`).

Los pagos de Mercado Pago son **simulados**: tienen ids de pago, preferencias y orders de prueba (`mp-prueba-…`, `ORD-PRUEBA-…`) y nunca se llamó a Mercado Pago.

### Partidos y turnos fijos
- **Partidos sueltos** de 4 jugadores con invitados sin cuenta: uno jugado y pagado por cada jugador, y uno futuro con 2 de los 4 pagos.
- **Turno fijo activo:** cancha 1, de 18:00 a 19:30, el día de la semana de dentro de 3 días, con una pelota por partido. Tiene sus 2 partidos jugados de las últimas semanas y los 4 que vienen hasta el tope de 30 días.
- **Turno fijo dado de baja:** cancha 4, de 9:00 a 10:30, con un partido jugado y la última fecha cancelada.

### Ventas sueltas
Registradas por el empleado en los últimos días, con los 4 medios (la de Mercado Pago, con el QR de la caja), con y sin cliente:
- transferencias y Cuenta DNI verificadas y sin verificar;
- una de hoy **anulada** por el empleado (no cuenta en la caja y su stock volvió);
- una de hace 6 días con una **devolución** de hoy (un agua vencida que no vuelve al stock).

## Cómo correrlo

Con la base de desarrollo levantada (contenedor `padel_club_db`):

```bash
docker exec -i padel_club_db sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -X -q' \
  < scripts/datos-de-prueba/datos-de-prueba.sql
```

### Con tu cuenta como cliente principal
Para conservar tu cuenta de cliente (o crearla con tu mail y tu celular, así te llegan los avisos y los códigos):

```bash
docker exec -i padel_club_db sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -X -q -v mail_cliente=tu@mail.com -v tel_cliente=5491122334455' \
  < scripts/datos-de-prueba/datos-de-prueba.sql
```

- **Celular:** en formato de WhatsApp, solo dígitos: `549`, la característica sin el 0 y el número sin el 15. Se usa solo si la cuenta se crea.
- **Mail:** no puede ser el de un ADMIN.
- Si no pasás `mail_cliente`, se conserva (o se crea) `cliente@test.com` y **se borra cualquier otra cuenta de cliente**, también la tuya.

Si algo choca, el script se cancela entero y la base queda como estaba.

## Después de correrlo

- **Reiniciar la app.** Así la caché de la configuración no queda con datos viejos.
- **Volver a iniciar sesión.** Las sesiones de antes se borraron.
- **Se puede volver a correr** cuando quieras empezar de cero: las cuentas que se conservan mantienen su contraseña.
- La reserva `EN_EL_CLUB` pendiente vence a los 15 minutos y la `SENIA_ONLINE`, a las 2 h: después el job las pasa a `EXPIRADA`.
