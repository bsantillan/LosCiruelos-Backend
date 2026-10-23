# Variables de entorno

`application.properties` toma los secretos y los datos que dependen del entorno con la sintaxis `${VARIABLE}`. Si falta alguna variable, la aplicación **no arranca** (`Could not resolve placeholder`).

> Spring Boot **no lee el archivo `.env`**. Ese archivo solo lo usa `docker compose` y hoy tiene `DB_NAME`, `DB_USER` y `DB_PASSWORD`. El resto de las variables se cargan desde `.vscode/launch.json` (bloque `env`). Para correr desde terminal o en un servidor hay que exportarlas en el entorno, o pasarlas como `--propiedad=valor`.

## Variables

| Variable | Propiedad de Spring | Para qué se usa | Obligatoria | Ejemplo |
|---|---|---|---|---|
| `DB_URL` | `spring.datasource.url` | URL JDBC de PostgreSQL | Sí | `jdbc:postgresql://localhost:5432/padel_club` |
| `DB_USER` | `spring.datasource.username` | Usuario de la BD (también en docker-compose) | Sí | `padel` |
| `DB_PASSWORD` | `spring.datasource.password` | Contraseña de la BD (también en docker-compose) | Sí | `********` |
| `DB_NAME` | — (solo `docker-compose.yml`) | Nombre de la base que crea el contenedor; tiene que coincidir con la de `DB_URL` | Solo para Docker | `padel_club` |
| `ADMIN_EMAIL` | `admin.email` | Email del admin inicial que crea `DataInitializer` | Sí | `admin@losciruelos.com` |
| `ADMIN_PASSWORD` | `admin.password` | Contraseña del admin inicial (solo se usa si el admin no existe) | Sí | `Admin123!` |
| `JWT_SECRET` | `jwt.secret` | Clave HMAC para firmar los access tokens (HS256). **Debe tener al menos 32 bytes**; si es más corta la app falla al arrancar (`WeakKeyException`). | Sí | cadena aleatoria de 64 caracteres |
| `EMAIL_FROM` | `email.from` | Remitente de los emails | Sí | `no-reply@losciruelos.com` |
| `MAIL_USERNAME` | `spring.mail.username` | Cuenta de Gmail para SMTP | Sí | `club@gmail.com` |
| `MAIL_PASSWORD` | `spring.mail.password` | **Contraseña de aplicación** de Gmail (no la contraseña normal) | Sí | `abcd efgh ijkl mnop` |
| `GOOGLE_CLIENT_ID` | `google.client-id` | Client ID de OAuth de Google; el `idToken` del frontend debe estar emitido para este ID | Sí | `xxxx.apps.googleusercontent.com` |
| `MP_ACCESS_TOKEN` | `mercadopago.access-token` | Access token de Mercado Pago (usar el `TEST-...` en desarrollo) | Sí | `TEST-123...` |
| `MP_WEBHOOK_URL` | `mercadopago.webhook-url` | URL pública a la que MP envía las notificaciones. Debe apuntar a `/pagos/notificacion` (en local, con un túnel tipo ngrok) | Sí | `https://xxxx.ngrok.app/pagos/notificacion` |
| `FRONTEND_URL` | `app.frontend-url` | URL del frontend. Mercado Pago devuelve al cliente a `FRONTEND_URL/pago/resultado?reservaId=…&resultado=aprobado\|rechazado\|pendiente` después de pagar con su cuenta. Con `https://` además vuelve solo al aprobarse (`auto_return`) | No (por defecto `http://localhost:5173`) | `https://reservas.losciruelos.com` |
| `MP_WEBHOOK_SECRET` | `mercadopago.webhook-secret` | Clave secreta de los webhooks (MP → Tus integraciones → Webhooks). Si está, se valida la firma `x-signature` | No (recomendada en producción) | `abc123…` |
| `SERVER_PORT` | `server.port` | Puerto HTTP de la app. En Windows con Docker Desktop puede quedar reservado el rango alrededor de 8080 (ver `netsh interface ipv4 show excludedportrange protocol=tcp`) | No (por defecto `8080`) | `9090` |
| `FORWARD_HEADERS_STRATEGY` | `server.forward-headers-strategy` | `framework` (por defecto): toma la IP real del cliente de `X-Forwarded-For`, necesario detrás de un proxy (Tailscale Funnel, nginx) para que el rate limiting no trate a todos como una sola IP. `none` si el backend se expone directo, sin proxy | No | `framework` |
| `WHATSAPP_TOKEN` | `whatsapp.token` | Token permanente del usuario de sistema de Meta (Cloud API). Ver [whatsapp.md](whatsapp.md) | No (sin él los avisos van por mail) | `EAAG...` |
| `WHATSAPP_PHONE_NUMBER_ID` | `whatsapp.phone-number-id` | Identificador del número del club en Meta (no es el número) | No | `123456789012345` |
| `CORS_ALLOWED_ORIGINS` | `app.cors.allowed-origins` | Orígenes del frontend permitidos, separados por coma | No (por defecto `http://localhost:3000,http://localhost:5173`) | `https://reservas.losciruelos.com` |
| `APP_SEED_USUARIO_PRUEBA` | `app.seed.usuario-prueba` | `true` crea el cliente de prueba `user.prueba@losciruelos.com` / `UserPassword123!`. **Nunca en producción** | No (por defecto `false`) | `true` |
| `SHOW_SQL` | `spring.jpa.show-sql` | Loguea el SQL de Hibernate | No (por defecto `false`) | `true` |

`MP_FRONTEND_URL` ya no se usa; si está definida, se ignora (se reemplazó por `FRONTEND_URL`).

## Propiedades fijas en `application.properties`

| Propiedad | Valor | Descripción |
|---|---|---|
| `jwt.expiration` | `900` | Duración del access token en segundos (15 min) |
| `refresh_token.expiration` | `604800` | Duración del refresh token en segundos (7 días) |
| `email.provider` | `gmail` | Bean de `EmailService` a usar |
| `payment.provider` | `mercadopago` | Bean de `PaymentService` a usar |
| `whatsapp.provider` | `meta` | Bean de `WhatsAppService` a usar |
| `telefono.codigos-por-dia-usuario` / `telefono.codigos-por-dia-numero` | `5` / `5` | Topes de códigos de verificación por WhatsApp cada 24 h |
| `telefono.segundos-entre-codigos` | `60` | Espera mínima entre pedidos de código del mismo usuario |
| `spring.mail.host` / `port` | `smtp.gmail.com` / `587` | SMTP con STARTTLS y TLSv1.2 |
| `spring.jpa.hibernate.ddl-auto` | `validate` | Hibernate no modifica el esquema |
| `spring.jpa.show-sql` | `${SHOW_SQL:false}` | SQL de Hibernate en el log |
| `spring.jpa.open-in-view` | `false` | No se mantiene la sesión de Hibernate abierta durante la vista |
| `spring.jpa.properties.hibernate.jdbc.time_zone` | `America/Argentina/Buenos_Aires` | |
| `spring.jackson.time-zone` | `America/Argentina/Buenos_Aires` | Zona de Jackson (los formatos están en `Config/Fechas`) |
| `spring.jackson.datatype.datetime.write-dates-as-timestamps` | `false` | Fechas como texto ISO. En Jackson 3 esta opción ya no está en `spring.jackson.serialization.*`: escrita ahí, la app no arranca |
| `spring.flyway.*` | enabled, `classpath:db/Migrations`, baseline en V1 | |

Las líneas de SendGrid están comentadas y no se usan.

## Plantilla `.env.example`

Sirve tanto para `docker compose` como de referencia para `launch.json` o el entorno del servidor:

```dotenv
# --- Base de datos (docker-compose + Spring) ---
DB_NAME=padel_club
DB_USER=padel
DB_PASSWORD=cambiar
DB_URL=jdbc:postgresql://localhost:5432/padel_club

# --- Admin inicial ---
ADMIN_EMAIL=admin@losciruelos.com
ADMIN_PASSWORD=CambiarEsto123!

# --- JWT (mínimo 32 bytes) ---
JWT_SECRET=reemplazar-por-una-cadena-aleatoria-de-al-menos-32-bytes

# --- Email (Gmail con contraseña de aplicación) ---
EMAIL_FROM=no-reply@losciruelos.com
MAIL_USERNAME=cuenta@gmail.com
MAIL_PASSWORD=contraseña-de-aplicacion

# --- Google Sign-In ---
GOOGLE_CLIENT_ID=xxxxxxxx.apps.googleusercontent.com

# --- Mercado Pago ---
MP_ACCESS_TOKEN=TEST-xxxxxxxx
MP_WEBHOOK_URL=https://tu-tunel.ngrok.app/pagos/notificacion
MP_WEBHOOK_SECRET=

# --- Opcionales ---
FRONTEND_URL=http://localhost:5173
SERVER_PORT=9090
CORS_ALLOWED_ORIGINS=http://localhost:3000,http://localhost:5173
APP_SEED_USUARIO_PRUEBA=true   # solo en desarrollo
SHOW_SQL=false
```
