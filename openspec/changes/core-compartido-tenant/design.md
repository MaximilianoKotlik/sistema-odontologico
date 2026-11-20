# Design

## Context

- Ver `proposal.md` §Why. Este change crea la **primera migración** y el paquete `compartido` sobre el esqueleto que deja C-01 (Gradle, Spring Boot 3.3, Flyway, Testcontainers, ArchUnit, `ArchitectureTest` base, roles `app_owner`/`app_rw`). C-02 no se puede implementar hasta que C-01 esté mergeado; el paquete base (`ar.<...>.agenda`) lo fija C-01 y acá se escribe como `<base>`.
- Arquitectura de [08](../../../knowledge-base/08_arquitectura_propuesta.md): monolito modular, capas `web → application → domain ← infrastructure`, `domain` sin Spring ni JPA, migraciones en SQL plano con `ddl-auto=validate`.
- Todavía no existe ninguna tabla de negocio con `consultorio_id`: los tests de aislamiento necesitan tablas de prueba propias (ver D7).
- Governance **CRITICO** (aislamiento entre tenants = datos de pacientes): ningún código de producción antes de la aprobación de Maxi.

## Goals / Non-Goals

**Goals:**
- Que sea imposible (por base o por build) escribir una query de negocio sin tenant o relacionar datos de dos consultorios.
- Un único contrato de errores y un único reloj que los changes siguientes solo consumen.
- Dejar a C-03 y C-04 enchufarse sin tocar `compartido`: C-03 suscribe al evento de auditoría; C-04 puebla `TenantContext`.

**Non-Goals:**
- Row Level Security de Postgres (backlog en 04 §Aislamiento).
- Filtro HTTP de autenticación, JWT y resolución por slug público (C-04 / C-15).
- Persistencia de auditoría y enmascarado de PII en logs como mecanismo general (C-03). Acá solo se garantiza que el 500 no expone detalles.
- Mensajes en español para el usuario final: el backend devuelve `codigo`; la traducción es del frontend.

## Decisions

### D1 — `ConsultorioId` + `TenantContext` de alcance request, repositorios con parámetro explícito
- `ConsultorioId` es un `record` del dominio (envuelve `UUID`, no nulo).
- `TenantContext` es una interfaz en `compartido.application` con `ConsultorioId actual()` (lanza `TenantNoResueltoException` → 500 si está vacío) y `void establecer(ConsultorioId)` (falla si ya hay otro distinto). Implementación `@RequestScope`; C-04 la llama desde su filtro.
- Los casos de uso leen `tenantContext.actual()` **una vez** y pasan el `ConsultorioId` a cada método de repositorio. Los repositorios **no** leen el contexto por su cuenta.
- *Alternativas*: `ThreadLocal` estático (se pierde en `@Async` y es fácil olvidar limpiarlo); filtro de Hibernate `@Filter` (invisible en la firma, no aplica a JDBC nativo ni a `EXCLUDE`). El parámetro explícito hace el tenant visible en cada firma y verificable con ArchUnit, que es lo que pide 04 §Aislamiento.

### D2 — Reloj único: bean `Clock` en UTC
- `@Bean Clock clock()` → `Clock.systemUTC()`; prohibido `Instant.now()`/`LocalDate.now()` sin argumento fuera de ese bean (regla ArchUnit sobre llamadas a `now()` sin `Clock`). Tests usan `Clock.fixed`.
- La conversión a `America/Argentina/Buenos_Aires` u otra zona del consultorio queda en presentación (regla dura 14).

### D3 — UUIDv7 con `java-uuid-generator`
- `GeneradorDeIds` (interfaz en `compartido.domain`, impl. con `Generators.timeBasedEpochGenerator()`), inyectable y reemplazable en tests.
- *Alternativas*: `gen_random_uuid()` de Postgres (v4, sin orden temporal, y 04 pide generación en la app); implementación propia (riesgo de bugs en un componente crítico). Java 21 no trae UUIDv7.

### D4 — Problem Details con `ProblemDetail` de Spring 6
- `@RestControllerAdvice` global (`ManejadorGlobalDeErrores`) que extiende `ResponseEntityExceptionHandler` y agrega `codigo` (y `errores` en validación) como propiedades.
- Jerarquía de excepciones de dominio sin Spring: `ErrorDeNegocio(codigo, status lógico)` con subclases `RecursoNoEncontrado`, `ConflictoDeNegocio`, `ValidacionDeNegocio`. El mapeo a HTTP vive solo en `web`.
- `OptimisticLockingFailureException` → 409 `CONFLICTO_CONCURRENCIA`; `HttpMessageNotReadableException` → 400 `CUERPO_INVALIDO`; `MethodArgumentNotValidException` → 400 `VALIDACION`; `Exception` → 500 `ERROR_INTERNO` con `detail` genérico y log con id de correlación.
- `type` = `urn:agenda:error:<codigo-en-minusculas>` (no URL navegable: no hay sitio de documentación de errores en v1).
- *Alternativa*: formato propio `{error, mensaje}` → descartado por RN-GL-03.

### D5 — Teléfono: `libphonenumber`, validación por "posible" y no por "válido"
- `NormalizadorDeTelefono` en `compartido.domain` (depende solo de `libphonenumber`, sin Spring): `parse(texto, "AR")` → `isPossibleNumber` → `format(E164)`.
- Se usa `isPossibleNumber` (longitud y estructura) en vez de `isValidNumber` (rangos asignados) porque la regla dura 17 obliga a usar números ficticios `+54 9 11 0000-00xx` en tests, seeds y mockups, y esos rangos **no están asignados**: con `isValidNumber` el sistema no podría ni probarse con datos ficticios. El costo es aceptar algún número no asignado, que igual no se puede verificar sin mensajería (PA-01).
- Los primeros tests del ciclo TDD verifican con la versión real de la librería que `011 15-0000-0001` y `+54 9 11 0000-0001` normalizan igual; si no, se ajusta el escenario de la spec antes de seguir.

### D6 — Slug: `CHECK` en base + validador y sugeridor puros en dominio
- `CHECK (slug ~ '^([a-z0-9])(-?[a-z0-9])+$' AND char_length(slug) BETWEEN 3 AND 40)` y `UNIQUE (slug)` en la migración. La lista de reservadas vive en el dominio (`PalabrasReservadas`), no en la base, para poder ampliarla sin migración.
- Reservadas iniciales: `app, api, login, registro, precios, admin, c, t, www, static, assets, health, docs, swagger, v3`. Las cuatro últimas cubren rutas técnicas de C-01 (Actuator, OpenAPI).
- `SugeridorDeSlug`: `Normalizer.NFD` + quitar marcas diacríticas, minúsculas, `[^a-z0-9]+ → -`, recorte a 40 y sin guion final; si queda corto o reservado, sufijo `-consultorio`. La unicidad (sufijo `-2`, `-3`…) la resuelve C-04 al registrar, porque necesita el repositorio.
- `SLUG_EN_USO` (409) se mapea desde la violación de la constraint `uk_consultorio_slug` (`DataIntegrityViolationException` con nombre de constraint) para cubrir también carreras.

### D7 — Migración `V1__consultorio_y_extensiones.sql`
- `CREATE EXTENSION IF NOT EXISTS btree_gist; CREATE EXTENSION IF NOT EXISTS citext;` (la ejecuta `app_owner`; si C-01 no le da permiso de crear extensiones, se crean en el init de Docker y la migración solo verifica — a confirmar con C-01).
- Tabla `consultorio` exactamente como 04 §Consultorio, con constraints con nombre (`ck_consultorio_*`, `uk_consultorio_slug`), `created_at`/`updated_at TIMESTAMPTZ NOT NULL DEFAULT now()` y `version BIGINT NOT NULL DEFAULT 0`.
- `GRANT SELECT, INSERT, UPDATE ON consultorio TO app_rw` (sin `DELETE`: la baja de consultorio no está en v1).
- **Convención de FKs compuestas** documentada en `backend/src/main/resources/db/migration/CONVENCIONES.md`: cada tabla de negocio declara `consultorio_id UUID NOT NULL REFERENCES consultorio(id)`, `UNIQUE (consultorio_id, id)` y referencia a otras tablas de negocio con `FOREIGN KEY (consultorio_id, x_id) REFERENCES x (consultorio_id, id)`; todo índice empieza por `consultorio_id`.

### D8 — Tests de aislamiento con tablas y controlador de prueba, sin contaminar producción
- Como no hay tablas de negocio, el test de integración crea en `@BeforeAll` (vía `JdbcTemplate`, no Flyway) `recurso_prueba` y `recurso_prueba_hijo` siguiendo la convención de D7, y un repositorio + `@RestController` definidos **solo en `src/test`**.
- Casos: FK compuesta cruzada falla; listado con A no ve B; `GET` de un id de B con A activo y de un id inexistente dan el mismo 404. Al llegar C-08/C-10 cada change agrega su propio test de aislamiento sobre tablas reales (regla dura 6).
- *Alternativa*: migración de test en `db/test-migration` → descartada: mezcla versiones de Flyway de test y producción.

### D9 — Reglas ArchUnit nuevas
1. Clases `..infrastructure..` que implementen un puerto `*Repositorio` de negocio: todo método público `buscar*/listar*/existe*/contar*/eliminar*` tiene un parámetro `ConsultorioId`.
2. Ninguna interfaz que extienda `org.springframework.data.repository.Repository` se usa fuera de `..infrastructure..` (evita `findById` heredado expuesto a `application`).
3. Nadie llama `Instant.now()`, `LocalDate.now()`, `LocalDateTime.now()`, `ZonedDateTime.now()` sin argumentos (D2).
4. `..domain..` no depende de `org.springframework..` ni `jakarta.persistence..` (ya en C-01; se verifica que cubra `compartido`).
- Exclusión explícita y documentada: el repositorio de `consultorio` (la raíz del tenant se busca por su propio id o slug).

### D10 — Puerto de auditoría
- `PublicadorDeAuditoria` (interfaz en `compartido.application`) con `publicar(EventoDeAuditoria)`; `EventoDeAuditoria` es un `record` con validación en el constructor (campos obligatorios y claves prohibidas de la spec `auditoria`).
- Implementación `PublicadorDeAuditoriaSpring` sobre `ApplicationEventPublisher`; verifica `TransactionSynchronizationManager.isActualTransactionActive()` y falla si no hay transacción. C-03 agrega el `@TransactionalEventListener(phase = BEFORE_COMMIT)` que persiste.
- `valor_anterior`/`valor_nuevo` como `Map<String, Object>` (serializable a JSONB en C-03); la acción es un `String` en `MAYUSCULAS` validado por patrón, para que cada módulo declare sus acciones sin tocar `compartido`.

## Risks / Trade-offs

- [C-01 todavía no existe: paquete base, roles y permisos de extensiones no están definidos] → la propuesta se aprueba ahora, pero el apply arranca recién con C-01 en `[x]`; la tarea 1 revisa D7 contra lo que entregó C-01.
- [PA-01 (identidad del paciente) sigue abierta] → C-02 solo provee el normalizador E.164, útil también para el teléfono del consultorio; la regla de deduplicación de pacientes se decide en C-10/C-15. No se toca la pregunta.
- [PA-03 (modelo de precios) sigue abierta] → la tabla solo incluye `plan` y `prueba_hasta` con valores ya fijados en 04 y DD; precios y duración de la prueba no se codifican acá (C-04 calcula `prueba_hasta`, C-20 el ciclo de vida).
- [`isPossibleNumber` acepta números no asignados] → aceptado (D5); no hay forma de verificar titularidad sin mensajería, fuera de v1 (DD-07).
- [Las reglas ArchUnit por nombre de método pueden tener falsos negativos] → se complementan con test de aislamiento obligatorio por endpoint (regla dura 6) y revisión técnica de Mati.
- [Un `TenantContext` `@RequestScope` no existe en jobs programados] → los jobs (C-20) establecen el tenant explícitamente por iteración; documentado en el Javadoc de `TenantContext`.

## Migration Plan

- Base vacía: `V1` es la primera migración; no hay datos que migrar. Rollback = no mergear (en local, `docker compose down -v`). Una vez mergeada, cualquier ajuste va en `V2+` (regla dura 5).

## UI Mockups

- Decision: no (no UI) — 2026-11-20. Change solo de backend, sin pantallas.

## Open Questions

- Si C-01 crea las extensiones en el init de Docker o deja que lo haga Flyway (solo cambia la primera línea de `V1`, ver D7).
