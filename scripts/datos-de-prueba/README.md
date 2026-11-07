# Datos de prueba

`datos-de-prueba.sql` deja la base de **desarrollo** con un ecosistema de datos para probar el sistema a mano. **Nunca se corre en producción.** Ni la app ni Flyway lo ejecutan: siempre se corre a mano.

## Qué hace

1. **Borra todos los datos.** El esquema y `flyway_schema_history` no se tocan.
2. **Vuelve a cargar lo básico**, igual que las migraciones y la carga inicial:
   - la configuración ($5.000 la media hora, seña del 50 %, turnos de 90 a 180 minutos, el cliente reserva hasta 30 días adelante);
   - los días de apertura;
   - las 4 canchas;
   - las pelotas y paletas, que se ofrecen al reservar, y dos productos que se venden en el club: agua mineral (con stock) y Gatorade (sin control de stock, como uno cargado a mano).
3. **Conserva los ADMIN que había:** mismo mail, contraseña, nombre, celular y canal de avisos. Si no había ninguno, lo crea la app al arrancar, con `ADMIN_EMAIL`/`ADMIN_PASSWORD`.
4. **Carga las cuentas de prueba.** Todas entran con la contraseña **`Prueba123!`**.

   | Cuenta | Rol | Para qué |
   |---|---|---|
   | `cliente@test.com` (o el mail que pases) | CLIENTE | El principal: perfil completo, varias reservas. |
   | `ana@test.com`, `martin@test.com`, `lucia@test.com` | CLIENTE | Perfiles completos, con categorías distintas. |
   | `sofia@test.com` | CLIENTE | Perfil incompleto: no puede reservar. |
   | `valentina@test.com` | CLIENTE | Entró con Google, así que no tiene contraseña. |
   | `diego@test.com` | CLIENTE | Cuenta desactivada. |
   | `empleado@test.com` | EMPLEADO | |
   | `profesor@test.com` | PROFESOR | |

5. **Carga reservas en varios estados**, con fechas relativas al día en que se corre:
   - **futuras:**
     - una pendiente, que vence a los 15 minutos;
     - señadas, por Mercado Pago, en efectivo y con Cuenta DNI;
     - pagadas, una con bonificación del ADMIN;
     - una con paletas alquiladas y una pelota;
     - una con un pago parcial;
   - **canceladas:**
     - con devolución por Mercado Pago;
     - con la seña retenida;
     - con plata a devolver a mano (`A_REEMBOLSAR`);
   - **pasadas:**
     - completadas;
     - una jugada que debe el saldo y no devolvió las paletas;
     - una con 2 aguas que el personal sumó después de jugar;
     - una que venció sin pagar.

6. **Carga ventas sueltas** (sin reserva) que registró el empleado: dos de hoy, una de ayer y una de la semana pasada, en efectivo, con Mercado Pago y con Cuenta DNI, con y sin cliente, una de hoy anulada por el empleado (no cuenta en la caja) y una devolución de hoy (un agua vencida) de la venta de la semana pasada. Las ventas con Mercado Pago del script son como las cargadas a mano antes del cobro con QR (sin order): para probar el QR, registrá una nueva con `medio: MERCADO_PAGO`.

   Alcanzan para ver con datos el panel, el resumen, la caja, los saldos pendientes, las paletas sin devolver y las ventas.

Los partidos de 4 jugadores y los turnos fijos todavía no se cargan.

## Cómo correrlo

Con la base de desarrollo levantada (contenedor `padel_club_db`):

```bash
docker exec -i padel_club_db sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -X -q' \
  < scripts/datos-de-prueba/datos-de-prueba.sql
```

### Recibir los avisos de la app

El cliente principal puede tener **tu mail y tu celular**, para que te lleguen los avisos y los códigos:

```bash
docker exec -i padel_club_db sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -X -q -v mail_cliente=tu@mail.com -v tel_cliente=5491122334455' \
  < scripts/datos-de-prueba/datos-de-prueba.sql
```

- **Celular:** en formato de WhatsApp, solo dígitos: `549`, la característica sin el 0 y el número sin el 15.
- **Mail:** no puede ser el mismo de un ADMIN, porque el mail es único.
- **Celular verificado:** no puede ser el mismo que el de un ADMIN, porque un celular verificado es de una sola cuenta.

Si algo choca, el script se cancela entero y la base queda como estaba.

### El resto de las cuentas

Tienen mails `@test.com` y celulares que no existen, con avisos por mail, así que no le llega nada a nadie.

## Después de correrlo

- **Reiniciar la app.** Así la caché de la configuración y las sesiones abiertas no quedan con datos viejos.
- **Volver a iniciar sesión.** Los tokens de antes ya no sirven.
- **Se puede volver a correr** cuando quieras empezar de cero.
