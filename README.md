# Los Ciruelos Padel Club — Backend

API REST del club de pádel **Los Ciruelos**: registro y login de usuarios (email + contraseña o Google), perfiles de jugador, administración de usuarios, consulta de disponibilidad de canchas, reservas de turnos y cobro de reservas con Mercado Pago o en el club.

## Stack

| Componente | Versión / detalle |
|---|---|
| Java | 21 LTS |
| Spring Boot | 4.0.6 (Web MVC, Data JPA, Security, Validation, Mail, Actuator) |
| Base de datos | PostgreSQL 16 (vía `docker-compose.yml`) |
| Migraciones | Flyway (`spring-boot-starter-flyway`, `src/main/resources/db/Migrations`) |
| Autenticación | JWT (jjwt 0.13, HS256) + refresh tokens guardados en BD; login con Google (`google-api-client`) |
| Emails | Gmail SMTP (`spring-boot-starter-mail`) |
| Pagos | Mercado Pago SDK Java 2.1.24 (preferencias + Payment Brick) |
| Documentación de API | springdoc-openapi 3.0.3 (Swagger UI) |
| Otros | Lombok, JaCoCo |

## Cómo levantarlo en local

1. **Base de datos** — crear un `.env` en la raíz con `DB_NAME`, `DB_USER` y `DB_PASSWORD` y ejecutar:
   ```bash
   docker compose up -d
   ```
   Esto levanta PostgreSQL 16 en `localhost:5432` con un volumen persistente (`padel_data`).

2. **Variables de entorno de la aplicación** — Spring Boot **no lee el archivo `.env`**. Todas las variables (`DB_URL`, `JWT_SECRET`, `MP_ACCESS_TOKEN`, etc.) tienen que estar definidas en el entorno del proceso: hoy se cargan desde `.vscode/launch.json` (bloque `env`). Lista completa y ejemplos en [docs/variables-de-entorno.md](docs/variables-de-entorno.md).

3. **Ejecutar** — requiere JDK 21:
   ```bash
   ./mvnw spring-boot:run        # Linux/macOS/Git Bash
   mvnw.cmd spring-boot:run      # Windows
   ```
   O con "Run/Debug" desde VS Code, que usa `launch.json`.

4. **Swagger UI**: `http://localhost:8080/swagger-ui/index.html` (spec en `/v3/api-docs`). Para endpoints protegidos, usar "Authorize" con el `accessToken` del login.

### Datos que se crean al arrancar

`DataInitializer` crea lo siguiente si no existe:

- Usuario **ADMIN** con `ADMIN_EMAIL` / `ADMIN_PASSWORD`.
- Usuario **CLIENTE de prueba** `user.prueba@losciruelos.com` / `UserPassword123!`, con perfil completo, **solo si** `APP_SEED_USUARIO_PRUEBA=true` (para desarrollo).
- **Configuración** del club (id = 1): seña 50 %, turnos de 90 a 180 min, $5000 cada 30 min, horarios L‑V 08–23, sábado 09–22, domingo 09–20.
- **4 canchas** (números 1 a 4).

## Documentación

| Documento | Contenido |
|---|---|
| [docs/arquitectura.md](docs/arquitectura.md) | Estructura del proyecto, capas, seguridad, jobs programados, integraciones |
| [docs/api.md](docs/api.md) | Todos los endpoints: ruta, rol, request, response y formato de errores |
| [docs/flujos-de-negocio.md](docs/flujos-de-negocio.md) | Registro, verificación, login, reservas, pagos, estados y reglas |
| [docs/base-de-datos.md](docs/base-de-datos.md) | Cómo funcionan las migraciones, tablas, relaciones, constraints e índices |
| [docs/variables-de-entorno.md](docs/variables-de-entorno.md) | Variables de entorno y propiedades de configuración |
| [docs/whatsapp.md](docs/whatsapp.md) | Avisos por WhatsApp y mail: qué se avisa, alta en Meta, plantillas, límites y costos |
| [docs/errores-conocidos.md](docs/errores-conocidos.md) | Qué se corrigió, qué queda pendiente y cambios de contrato para el front |

## Tests

```bash
./mvnw test      # 302 tests unitarios y de controllers + reporte de JaCoCo en target/site/jacoco
./mvnw verify    # además exige 70 % de cobertura de líneas (hoy: 82 %)
```

- Los tests unitarios no necesitan base de datos ni variables de entorno.
- `PadelClubApiApplicationTests.contextLoads` levanta la app completa y solo corre si está definida `DB_URL`.
- Si Maven falla con `PKIX path building failed` (antivirus o proxy que inspecciona HTTPS), usar el almacén de certificados de Windows: `set MAVEN_OPTS=-Djavax.net.ssl.trustStoreType=Windows-ROOT`.
