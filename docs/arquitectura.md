# Arquitectura

Paquete raíz: `com.LosCiruelos.padel_club_api` (`src/main/java/com/LosCiruelos/padel_club_api`).

## Capas y paquetes

```
Controller ──► Services ──► Repository ──► PostgreSQL
    │              │
    │              ├── Services/Email  (EmailService + factory)
    │              └── Services/Pagos  (PaymentService + factory → Mercado Pago)
    │
    └── DTOs (Requests / Responses)       Handlers/GlobalExceptionHandler
```

| Paquete | Responsabilidad |
|---|---|
| `reservas` (dominio) | Reservas, partidos y turnos fijos, en subpaquetes: `controller` (`ReservaController`: `/reservas`, solo el cliente; `ReservaAdminController`: `/admin/reservas`, el personal; `PartidoController`, `TurnoFijoController`), `service` (`ReservaService` (ciclo de vida), `ReservaConsultaService` (consultas y listado del personal), `PartidoService`, `TurnoFijoService` (también crea los partidos de cada semana), `CancelacionService`, `PoliticaCancelacion`, `DisponibilidadService`, `StockService`), `repository` (repositories; `ReservaRepository` suma el fragmento `ReservaConsultas` + `ReservaConsultasImpl` con los totales del listado; `ReservaSpecification`, `FiltroReservas`, `UsoAlquiler`), `entity` y `entity/enums`, `dto/request` y `dto/response` |
| `pagos` (dominio) | `controller` (`PagoController`), `service` (`PagoOnlineService`: iniciar, Brick y webhook; `CobroService`: pagos en el club y bonificaciones; `ReembolsoService`: reembolsos y reintentos; `EstadosDePago`: lo que comparten los tres), `repository`, `entity` y `entity/enums` (`Pago`, `EstadoPago`), `dto/request`, `dto/response`, `payment` (contrato genérico de proveedores: `PaymentService`, `PaymentServiceFactory`, `PaymentProvider`, `PaymentResult`, `EstadoPayment`) y `mercadopago` (`MercadoPagoPaymentService`, `MercadoPagoWebhookValidator`) |
| `Controller` | Endpoints REST de los dominios que todavía no se mudaron: `AuthController`, `PerfilController`, `AdminController` (`/admin/usuarios`), `ClienteAdminController` (`/admin/clientes`), `CanchaController`, … |
| `Services` | Lógica de negocio: `AuthService`, `VerificationService`, `PasswordResetService`, `UsuarioService`, `ClienteProfileService`, `PerfilService`, `AdminService`, `CanchaService`, `ConfiguracionService` (horarios, precio, seña), `ProductoService` (catálogo y stock) |
| `Services/Email` | `EmailService` (interfaz), `GmailEmailService` (bean `"gmail"`), `EmailServiceFactory`, `EnvioPostCommit` (envía después del commit) |
| `Services/WhatsApp` | `WhatsAppService` (interfaz), `MetaWhatsAppService` (bean `"meta"`, Cloud API de Meta), `WhatsAppServiceFactory` |
| `Services/Avisos` | `AvisoReserva` (evento), `TipoAviso` (plantillas y textos), `AvisosService` (elige el canal de cada destinatario), `AvisosListener` (envía después del commit) |
| `Repository` | Repositorios Spring Data JPA (incluye queries JPQL de solapamiento y pendientes en `ReservaRepository`) |
| `Entities` / `Entities/Enum` | Entidades JPA y enums de dominio |
| `DTOs` | Objetos de request/response; `PaymentResult` (resultado normalizado del proveedor de pagos) |
| `Security` | `SecurityConfig`, `JWTFilter`, `JWTUtil`, `RateLimitFilter`, `UsuarioPrincipal`, `GoogleTokenVerifier`, `PasswordValidator` |
| `Specifications` | `UsuarioSpecification`: filtros dinámicos para el listado de usuarios del admin |
| `Handlers` | `GlobalExceptionHandler`: traduce excepciones a respuestas HTTP |
| `Exceptions` | Excepciones de negocio |
| `Jobs` | `ReservaJobs` (expiración y reservas completadas) |
| `Config` | `AppConfig` (`Reloj`, caché), `DataInitializer`, `MercadoPagoConfiguration`, `SwaggerConfig` |
| `mapper` | Mapeo entidad → DTO, uno por entidad: `ReservaMapper`, `PagoMapper`, `TurnoFijoMapper`. Los DTOs quedan solo con datos |
| `common` | Utilidades compartidas, de uso obligatorio (detalle en `CLAUDE.md`): `Reloj` (hora del club), `Turno` (duración, superposición, si empezó o terminó), `ConsultasDeTurno` (las mismas condiciones en JPQL y Criteria), `Fechas` (zona, formato de la API y textos de fechas y días), `Dinero` (redondeo, sumas y texto en pesos), `Paginacion`, `Roles`, `Busquedas` (búsqueda sin tildes) y `Telefonos` (normalización del celular) |

## Proveedores intercambiables (patrón factory)

Email y pagos se resuelven por nombre de bean:

- `EmailServiceFactory` recibe un `Map<String, EmailService>` con todos los beans y devuelve el que coincide con `email.provider` (hoy `gmail`). Para agregar SendGrid alcanza con crear un `@Service("sendgrid")` que implemente `EmailService` y cambiar la propiedad.
- `PaymentServiceFactory` hace lo mismo con `payment.provider` (hoy `mercadopago`).
- `WhatsAppServiceFactory` hace lo mismo con `whatsapp.provider` (hoy `meta`). Para cambiar de proveedor (Twilio, 360dialog, …) alcanza con una nueva implementación de `WhatsAppService` registrada con su nombre (`@Service("twilio")`) y cambiar la propiedad.
- El **canal** de cada aviso (mail o WhatsApp) no lo decide un factory sino `AvisosService.canalPara`, en el momento de enviar, según lo que eligió cada usuario en su perfil. El factory elige el **proveedor** de cada canal.

## Seguridad

- **Sin estado** (`SessionCreationPolicy.STATELESS`), CSRF deshabilitado, contraseñas con BCrypt.
- **`RateLimitFilter`**: límite de pedidos por IP y por minuto para `/auth/*` (responde `429`).
- **`JWTFilter`** (antes de `UsernamePasswordAuthenticationFilter`): lee `Authorization: Bearer <token>`, valida firma y expiración, busca al usuario por email (subject del JWT) y solo lo autentica si `enabled = true`. La autoridad es `ROLE_<rol>`.
- **Access token**: JWT HS256 con el email como subject, dura `jwt.expiration` = 900 s (15 min). Se firma con `JWT_SECRET`.
- **Refresh token**: UUID guardado en la tabla `refresh_tokens`, dura `refresh_token.expiration` = 604800 s (7 días). **Uno por usuario**: cada login borra los anteriores, y cada `/auth/refresh` lo rota (borra el viejo y crea uno nuevo).
- **Reglas por URL** (`SecurityConfig`):

| Patrón | Acceso |
|---|---|
| `POST /auth/logout` | autenticado |
| `/auth/**` | público |
| `POST /pagos/notificacion` | público (webhook de Mercado Pago) |
| `/v3/api-docs/**`, `/swagger-ui/**`, `/actuator/health` | público |
| `GET /canchas`, `GET /canchas/*/disponibilidad`, `GET /productos`, `GET /productos/disponibilidad` | público (un visitante ve horarios libres y precios; reservar requiere sesión) |
| `GET /admin/reservas`, `GET /admin/clientes` | `ADMIN`, `EMPLEADO` |
| `GET /admin/resumen` | `ADMIN` (cualquier período), `EMPLEADO` (solo hoy) |
| `/admin/**` | `ADMIN` (incluye `/admin/partidos` y `/admin/turnos-fijos`) |
| `POST /pagos/*/pago-efectivo`, `POST /pagos/*/pago-presencial` | `ADMIN`, `EMPLEADO` |
| cualquier otra | autenticado |

- Además, `@EnableMethodSecurity` activa los `@PreAuthorize` de los controllers (por ejemplo, `/reservas/**` solo para CLIENTE, ADMIN y EMPLEADO).
- Sin token o con token vencido → `401`; sin permisos → `403` (ambos con body JSON).
- **Acceso a reservas**: además del rol, `ReservaService.verificarAcceso` exige que un CLIENTE sea el dueño de la reserva.
- **CORS**: orígenes de `CORS_ALLOWED_ORIGINS` (por defecto `http://localhost:3000` y `http://localhost:5173`), métodos GET/POST/PUT/DELETE/PATCH/OPTIONS, headers `Authorization` y `Content-Type`, con credenciales.
- **Roles** (`Role`): `CLIENTE`, `EMPLEADO`, `ADMIN`, `PROFESOR`.
- **Contraseñas** (`PasswordValidator`): mínimo 8 caracteres, con minúscula, mayúscula, número y carácter especial.

## Tareas programadas

`@EnableScheduling` está en `PadelClubApiApplication`.

| Tarea | Frecuencia | Qué hace |
|---|---|---|
| `ReservaJobs.expirarReservasVencidas` | cada 1 min | Expira, una por una y con la reserva bloqueada, las `PENDIENTE` vencidas sin pagos aprobados ni en proceso. Si una falla, sigue con las demás. |
| `ReservaJobs.reintentarReembolsos` | cada 2 min | Reintenta, uno por uno y con la reserva bloqueada, los reembolsos de MP que no salieron al cancelar (`REEMBOLSO_PENDIENTE`). Éxito → `DEVUELTO`; sin éxito en 24 h o falla definitiva → `A_REEMBOLSAR`. |
| `ReservaJobs.enviarRecordatorios` | cada 5 min | Recordatorio al cliente antes del turno (2 h por defecto) por su canal; una vez por reserva, con la reserva bloqueada. |
| `ReservaJobs.completarReservasJugadas` | cada 10 min | Pasa a `COMPLETADA` las reservas `PAGADA` cuyo turno terminó. Las señadas o parciales quedan como están hasta que se cobre el saldo. |
| `JWTUtil.cleanExpiredTokens` | todos los días a las 02:00 | Borra los refresh tokens vencidos. |

## Concurrencia en reservas y pagos

- Crear una reserva bloquea la fila de la cancha (`CanchaRepository.findByIdForUpdate`).
- Orden fijo de locks: **cancha → reservas → productos (por id)**. El stock se mueve en un solo paso al final de cada operación (`ProductoService.moverStock`), así ninguna operación toma un producto y después espera una reserva.
- Cualquier operación sobre una reserva existente la bloquea primero (`ReservaRepository.findByIdForUpdate`) y trabaja con `reserva.getPagos()`. Las búsquedas previas al lock devuelven solo ids, para que Hibernate no deje entidades desactualizadas en memoria.
- La restricción `reservas_sin_solapamiento` de PostgreSQL es la última barrera contra la doble reserva.
- Los emails se envían después del commit (`EnvioPostCommit`): un error de SMTP no revierte la operación.

## Integraciones externas

| Servicio | Dónde | Uso |
|---|---|---|
| Google Identity | `GoogleTokenVerifier` | Verifica el `idToken` de Google Sign-In contra `GOOGLE_CLIENT_ID` y extrae email, nombre y apellido. |
| WhatsApp (Cloud API de Meta) | `MetaWhatsAppService` (vía `WhatsAppServiceFactory`), `AvisosService`, `AvisosListener` | Plantillas aprobadas: avisos a los ADMIN, recordatorio y cancelación por el club al cliente, código para verificar el teléfono. Los avisos se publican como eventos (`AvisoReserva`) y se envían después del commit en el executor `avisosExecutor` (2 hilos); cada usuario elige mail o WhatsApp. Ver [whatsapp.md](whatsapp.md). |
| Gmail SMTP | `GmailEmailService` | Emails HTML: código de verificación, código de reset de contraseña, bienvenida con contraseña temporal. |
| Mercado Pago | `MercadoPagoConfiguration`, `MercadoPagoPaymentService`, `MercadoPagoWebhookValidator` | Configura el access token del SDK. Crea preferencias (`crearPreferencia`), cobra con el token del Payment Brick por el monto del servidor y con clave de idempotencia (`cobrar`), consulta pagos al recibir notificaciones (`consultarPago`), reembolsa (`reembolsar`) y valida la firma de los webhooks. Las claves de idempotencia salen del UUID de cada pago (`losciruelos-{pref\|cobro\|reembolso}-{uuid}`). |

## Otros detalles

- **Zona horaria**: una sola constante, `Fechas.ZONA` (`America/Argentina/Buenos_Aires`). La usan el `Reloj` de la app (`Reloj.delSistema()`, de a milisegundos), la zona por defecto de la JVM (fijada en `main`), Hibernate (`hibernate.jdbc.time_zone`), Jackson (`spring.jackson.time-zone`) y el vencimiento de las preferencias de MP.
- **Formato de fechas del JSON** (`Fechas.moduloJackson`, registrado como bean en `AppConfig`): `LocalDateTime` → `2026-10-17T18:15:00.123-03:00`, `LocalTime` → `18:00`, `LocalDate` → `2026-10-17`. El `timestamp` de los errores usa el mismo formato y el mismo reloj.
- **Esquema**: lo gestiona Flyway; Hibernate solo valida (`ddl-auto=validate`).
- **Configuración del club**: un único registro `Configuracion` con id = 1 (ver [flujos-de-negocio.md](flujos-de-negocio.md)), en caché en memoria (`AppConfig` activa `@EnableCaching`).
- **Reloj**: los servicios usan el `Reloj` de `AppConfig` (`ahora()`, `hoy()`) en lugar de `now()` directo, para poder testear con horas fijas (`Reloj.fijo(...)`).
- **OpenAPI**: `SwaggerConfig` define la info de la API y el esquema de seguridad `bearerAuth`.
- **Actuator**: incluido; sus endpoints requieren autenticación.
