# CLAUDE.md — Backend de Los Ciruelos

API del club de pádel Los Ciruelos: reservas de canchas, partidos y turnos fijos, pagos (Mercado Pago y en el club), productos (pelotas y paletas), usuarios y avisos por WhatsApp o mail.

Todo se escribe en **castellano rioplatense**: mensajes de error, textos de avisos, comentarios, documentación, nombres de tests y mensajes de commit. Los nombres de clases, métodos y variables también van en castellano (`crearReserva`, `montoPendiente`), salvo los términos técnicos de Spring.

## Stack
- Java 21, Spring Boot 4 (Web MVC, Data JPA, Security, Validation, Mail, Cache), Hibernate 7.
- PostgreSQL 16 con Flyway (`src/main/resources/db/Migrations`, más migraciones Java en `src/main/java/db/Migrations`).
- Mercado Pago (Payment Brick, Wallet y webhook), WhatsApp Cloud API de Meta, Gmail.
- Tests: JUnit 5, Mockito, AssertJ, MockMvc standalone y `@DataJpaTest` sobre H2. Cobertura mínima de JaCoCo: 70 % de líneas.

## Cómo compilar y probar
- Siempre con el certificado de Windows:
  ```bash
  export MAVEN_OPTS="-Djavax.net.ssl.trustStoreType=Windows-ROOT"
  ./mvnw clean verify          # tests + cobertura; tiene que terminar con 0 fallos
  ./mvnw -q test -Dtest='NombreTest' -Djacoco.skip=true   # un test puntual
  ```
- Usar `clean` si aparecen errores raros (`NoClassDefFoundError`, "Unresolved compilation problems"): el IDE compila en `target/` al mismo tiempo que Maven. No borrar `target/` a mano mientras el IDE está abierto.
- No correr Maven parado dentro de `target/` (Windows bloquea la carpeta y `clean` falla).
- **Prueba punta a punta** contra un PostgreSQL temporal, nunca contra la base de desarrollo:
  ```bash
  docker run -d --rm --name padel_tmp_test -e POSTGRES_DB=padel -e POSTGRES_USER=padel \
    -e POSTGRES_PASSWORD=padel -p 55432:5432 postgres:16
  # DB_URL=jdbc:postgresql://localhost:55432/padel, DB_PASSWORD=padel, APP_SEED_USUARIO_PRUEBA=true
  java -jar target/padel-club-api-0.0.1-SNAPSHOT.jar --server.port=18099
  ```
  Al terminar, frenar la app y `docker stop padel_tmp_test`. Las migraciones se prueban aplicándolas sobre una base vacía (y, si migran datos, cargando datos viejos con `--spring.flyway.target=<versión anterior>` primero).
- Los tests de repositorio usan H2 con `spring.flyway.enabled=false`, `ddl-auto=create-drop` y `hibernate.dialect=org.hibernate.dialect.H2Dialect` (sin el dialecto de H2 fallan los CHECK que genera Hibernate).

## Arquitectura
Paquetes **por dominio** (`usuarios`, `club`, `reservas`, `pagos`, `notificaciones`, `reportes`) más `common`, `config` y `security`. La reorganización se hace por etapas (ver el plan del repositorio); mientras dure, conviven los paquetes por capa (`Controller`, `Services`, `Repository`, `Entities`, `DTOs`) con los nuevos. Lo nuevo va en el paquete de su dominio.

### Responsabilidad de cada capa
1. **Controller**: solo HTTP. Ruta, `@Valid`, rol (`@PreAuthorize` por método) y una llamada a un service. Sin lógica de negocio y sin repositories. Un controller por recurso: lo público y lo del ADMIN de un mismo recurso van en el mismo controller con roles por método.
2. **Service**: un caso de uso de un dominio. Es el único lugar con `@Transactional`. No usa repositories de otro dominio (pide lo que necesita al service público de ese dominio), no presta helpers internos a otros services y no tiene métodos pasamanos (`save` que solo llama al repository).
3. **Reglas del negocio**: en las entidades (`Reserva.recalcularEstado`, `Reserva.admitePagos`, `Reserva.expirarPagosPendientes`, `Pago.getMontoRetenido`) o en clases de dominio chicas sin Spring (`PoliticaCancelacion`), para probarlas sin mocks.
4. **Repository**: uno por raíz de agregado; las entidades hijas se guardan por cascada. Las consultas de listados y reportes van en repositories de solo lectura (`…Consultas`, `ResumenRepository`). No dejar métodos sin uso.
5. **DTOs**: el mapeo se hace en mappers (`ReservaMapper`) o en `from` estáticos, no en los services. Los requests se validan con Bean Validation y mensajes en castellano.
6. **Transversal**: los avisos se publican como eventos y se envían después del commit (`AvisosListener`); los jobs (`ReservaJobs`) solo llaman a services.

### Utilidades obligatorias (`common`)
Antes de escribir lógica, buscar si ya existe. **Si algo se repite dos veces, va a `common` o a un método de la entidad.**

| Para | Usar | Nunca |
|---|---|---|
| Fecha y hora actual | `Reloj` (`ahora()`, `hoy()`, `horaActual()`, `instante()`); en tests `TestData.reloj(AHORA)` o `Reloj.fijo(...)` | `LocalDateTime.now()`, `Clock` suelto |
| Horarios de un turno | `Turno` (`duracionMinutos`, `seSuperponen`, `empezo`, `termino`); `reserva.turno()`, `turnoFijo.turnoEl(fecha)` | `Duration.between(...)` o comparaciones de horas a mano |
| Condiciones de horario en JPQL/Criteria | `ConsultasDeTurno` (`SE_SUPERPONE`, `TERMINADO`, `EMPEZO`, `noTermino`) | copiar el fragmento en otra consulta |
| Textos de fechas y días | `Fechas` (`textoFecha`, `textoHora`, `nombreDia`, `nombreDiaCapitalizado`, `formatear`) | `DateTimeFormatter.ofPattern` sueltos |
| Montos | `Dinero` (`redondear`, `sumar`, `texto`) | `setScale(2, …)`, `reduce(BigDecimal.ZERO, …)`, `DecimalFormat` |
| Página y tamaño | `Paginacion` (`validar`, `pagina`) | validar página/tamaño a mano |
| Usuario de la sesión | `UsuarioService.autenticado(email)` | `findByEmailOrThrow(email, new CredencialesInvalidasException(...))` |
| Roles en un service | `Roles` (`esPersonal`, `esAdmin`, `exigirAdmin`) | `getRol() == Role.ADMIN` repetido |
| Búsqueda sin tildes y teléfonos | `Busquedas`, `Telefonos` | normalizar a mano |

### Convenciones
- **Fechas y horas**: todo en hora de Argentina (`Fechas.ZONA`). En la API: fecha-hora `2026-10-17T18:15:00.123-03:00`, hora `HH:mm`, fecha `yyyy-MM-dd`.
- **Montos**: `BigDecimal`, 2 decimales en las respuestas (vía `Dinero`).
- **Errores de negocio**: subclases de `ReservaException`, que llevan el status (400 datos inválidos, 403 `PermisoDenegadoException`, 404 no encontrado, 409 `EstadoInvalidoException`/`HorarioOcupadoException`). Un recurso ajeno responde 404 para no revelar ids. El formato de error lo arma `GlobalExceptionHandler`.
- **Concurrencia**: toda operación que modifica una reserva la carga primero con lock (`ReservaService.bloquear`, `findByIdForUpdate`); la creación bloquea la cancha y la generación de turnos fijos bloquea el turno fijo. El stock se mueve en un solo paso con los productos bloqueados en orden de id (`moverStock`). Los avisos salen después del commit.
- **Configuración del club**: se lee con `ConfiguracionService.obtener()` (cacheada); al modificarla se invalida la caché.

## Base de datos
- Un cambio de esquema = una migración nueva `V<n>__descripcion.sql`. **Nunca editar una migración que ya está en `main`** (sí se puede reescribir una que solo existe en la rama actual). Hibernate solo valida (`ddl-auto=validate`).
- Las migraciones que tocan datos se prueban con datos viejos en el PostgreSQL temporal.
- En JPQL, cuidado con las columnas que pueden ser null (`expires_at`, `cliente_id` en partidos): `col <= :x` con null no es verdadero ni falso.
- Restricciones que se intercambian en un mismo flush (posiciones de jugadores) van `DEFERRABLE INITIALLY DEFERRED`.

## Tests
- Services: tests unitarios con Mockito (patrón de `PagoServiceTest`, `ReservaServiceTest`); reglas de entidades y utilidades, sin Spring.
- Controllers: MockMvc standalone con `GlobalExceptionHandler` (patrón de `ControllersTest`).
- Consultas JPQL/Criteria nuevas: `@DataJpaTest` sobre H2 (patrón de `ResumenRepositoryTest`, `PartidosRepositoryTest`).
- Datos de prueba en `TestData` (hoy es el martes 13/10/2026 10:00). Nombres de tests en castellano que describen el comportamiento (`unPartidoNoSePagaOnline`).
- Antes de dar algo por terminado: `./mvnw clean verify` en verde y, si toca flujos de reservas o pagos, la prueba punta a punta.

## Documentación (se actualiza en el mismo cambio)
- `docs/api.md`: endpoints, requests, respuestas y errores.
- `docs/flujos-de-negocio.md`: reglas y flujos.
- `docs/base-de-datos.md`: tablas, columnas y migraciones.
- `docs/errores-conocidos.md`: **lista de cambios para el front** (contratos que cambian).
- `docs/arquitectura.md`, `docs/whatsapp.md`, `docs/variables-de-entorno.md` cuando corresponda.

## Git
- No commitear ni pushear sin que se pida (o sin que lo diga el plan aprobado). Trabajar en una rama, nunca directo en `main`.
- Commits chicos y por etapa, con mensaje en castellano (`feat(reservas): …`, `fix(pagos): …`, `refactor(common): …`) y la línea `Co-Authored-By` que corresponda.

## Seguridad y datos
- No mostrar ni copiar los secretos de `.vscode/launch.json` ni tokens (Mercado Pago, Meta, Google, JWT).
- No tocar la base de desarrollo `padel_club_db` (contenedor Docker) sin permiso: para probar, el PostgreSQL temporal.
- No mandar datos personales (mails, teléfonos) a servicios externos fuera de los envíos propios de la app.
