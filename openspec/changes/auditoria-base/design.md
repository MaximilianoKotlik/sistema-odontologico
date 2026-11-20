# Design

## Context

Ver `proposal.md` §Why. Estado de partida (tarea 0.1, contrastado con `main` en `2991664`, 2026-11-20):

- **C-01** y **C-02** están mergeados y en `[x]`. Ya existen:
  - `IntegracionPostgresTest`: Testcontainers con PostgreSQL 16 y `docker/postgres/init/01-roles.sh`. Flyway migra con `app_owner` y la aplicación conecta con `app_rw`.
  - La migración `V1__consultorio_y_extensiones.sql` y `db/migration/CONVENCIONES.md`.
  - El placeholder `spring.flyway.placeholders.app-user: ${DB_APP_USER}`, que C-02 ya usa en `V1`.
  - `ArchitectureTest` con las reglas de capas, tenant y reloj.
  - Todavía no hay `logback-spring.xml`, ni ninguna entidad JPA de producción.
- El script de roles de C-01 hace `ALTER DEFAULT PRIVILEGES ... GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO app_rw`. **Toda tabla nueva le da `UPDATE` y `DELETE` a `app_rw` salvo que la migración los revoque.**
- Arquitectura (`08` §Patrones): monolito modular con capas `web → application → domain ← infrastructure`; Flyway con SQL plano y `ddl-auto=validate`.

## Contrato de C-02 que se consume

Tomado del código mergeado de C-02 (paquete `compartido`) y de la spec `openspec/specs/auditoria`:

| # | Pieza de C-02 | Uso en C-03 |
|---|---------------|-------------|
| K-1 | `PublicadorDeAuditoria.publicar(CambioAuditado)`. La implementación `PublicadorDeAuditoriaSpring` **falla si no hay transacción activa**, le agrega el instante del `Clock` y publica `EventoDeAuditoria(Instant ocurridoAt, CambioAuditado cambio)` como evento de Spring. | El listener se suscribe a `EventoDeAuditoria`. No agrega su propia guarda de transacción. |
| K-2 | `CambioAuditado(ConsultorioId consultorio, Actor actor, String entidad, UUID entidadId, String accion, Map valorAnterior, Map valorNuevo, String ip)`, con `Actor(ActorTipo tipo, UUID id)` y `ActorTipo` en `ODONTOLOGO`, `PACIENTE` o `SISTEMA`. | Se mapea uno a uno a las columnas. `ocurrido_at` sale del evento. |
| K-3 | El constructor de `CambioAuditado` **rechaza claves prohibidas**, también en mapas anidados: `password`, `contrasena`, `contraseña`, `token`, `secret`, `*_token`, `*_hash` y `*_secret`. También rechaza campos obligatorios faltantes y acciones fuera de `MAYUSCULAS_CON_GUIONES_BAJOS`. | C-03 no repite la validación: no se puede construir un cambio inválido. |
| K-4 | `GeneradorDeIds.nuevo()` (UUIDv7) en `compartido.domain`. | Genera el `id` del registro. |
| K-5 | `V1` y `CONVENCIONES.md`: FK a `consultorio(id)`, `UNIQUE (consultorio_id, id)`, índices que empiezan por `consultorio_id`, constraints con nombre y `REVOKE` explícito con `"${app-user}"`, verificado con un test conectado como `app_rw`. | La tabla de auditoría sigue la convención. |
| K-6 | `compartido.domain.EnmascaradorDePii.enmascarar(String)`: emails → `***@***` y teléfonos de 7 o más dígitos → `***`; deja legibles las fechas ISO. Ya lo usa el manejador de errores para el log de los 500. | Los conversores de Logback (D-6) lo reusan, así hay un solo juego de expresiones regulares. |

## Goals / Non-Goals

**Goals:**
- Que ningún cambio auditado pueda confirmarse sin su evento, y que ningún evento pueda alterarse después.
- Que la inmutabilidad dependa de la base (trigger y permisos), no de la disciplina del código.
- Que los demás módulos solo conozcan el puerto de `compartido`, nunca `auditoria`.

**Non-Goals:**
- Validar el contenido del evento (lo hace C-02, K-3) y garantizar que haya transacción (C-02, K-1).
- La consulta del registro, el endpoint, los filtros, la UI y el test de aislamiento 404: van en C-18.
- Emitir los eventos de cada dominio (auth en C-05, configuración en C-08, agenda en C-11/C-12).
- Auditar lecturas (`11`, para la v2 con historia clínica). Retención tras la baja (PA-07).
- Auditar operaciones que **fallan**, como `LOGIN_FALLIDO` (RN-AD-04). Queda para C-05; ver Risks.

## Decisions

### D-1 Inmutabilidad en dos capas: trigger y permisos
- Función `evento_auditoria_inmutable()` que hace `RAISE EXCEPTION` con un `SQLSTATE` propio, enganchada en `BEFORE UPDATE OR DELETE ... FOR EACH ROW` y en `BEFORE TRUNCATE ... FOR EACH STATEMENT`.
- En la misma migración: `REVOKE UPDATE, DELETE, TRUNCATE ON evento_auditoria FROM "${app-user}"` (obligatorio por los privilegios por defecto de C-01) y `GRANT INSERT, SELECT ... TO "${app-user}"`.
- **El rol no va fijo en el SQL**: es configurable con `DB_APP_USER` (`01-roles.sh`, `application.yml`). La migración usa el placeholder de Flyway `${app-user}`, que C-02 ya dejó definido en `application.yml` (K-5). Con el nombre fijo, en un entorno donde el rol se llame distinto la migración fallaría o no protegería nada.
- El `GRANT INSERT, SELECT` es redundante con los privilegios por defecto, pero se deja explícito para que la migración documente qué puede hacer la aplicación.
- *Por qué las dos capas*: el permiso protege al rol de la aplicación. El trigger protege también contra un rol con permisos de más, como un `GRANT` equivocado en el futuro. `TRUNCATE` no dispara triggers de fila; por eso lleva el suyo.
- *Alternativa descartada*: solo permisos. Un error de configuración lo rompería sin que lo note ningún test.
- *Límite aceptado*: `app_owner` puede deshabilitar el trigger, pero es el rol de las migraciones y la aplicación no lo usa en ejecución.

### D-2 Persistencia con JDBC, sin entidad JPA
- El repositorio en `auditoria.infrastructure` inserta con `JdbcClient` (`CAST(? AS jsonb)`, `CAST(? AS inet)`). Los mapas se serializan con el `ObjectMapper` de Spring.
- *Por qué*: un evento solo se inserta. Una entidad JPA invita a hacer `save()` sobre una instancia cargada, que sería un `UPDATE`, y además exige mapear JSONB e INET. `JdbcClient` usa el mismo `DataSource` y comparte la transacción del `JpaTransactionManager`.
- *Alternativa descartada*: entidad con `@Immutable`. Ignora los updates en silencio en lugar de fallar.

### D-3 Listener `BEFORE_COMMIT`
- `@TransactionalEventListener(phase = BEFORE_COMMIT)` en `auditoria.application` (`RegistradorDeAuditoria`): mapea el evento al registro de dominio y lo guarda por el puerto `RepositorioDeAuditoria`. Si lanza una excepción, se aborta el commit y el cambio auditado se revierte (RN-AD-03).
- **Flush antes de insertar**: Spring invoca los listeners `BEFORE_COMMIT` en `triggerBeforeCommit`, antes de `doCommit`, que es cuando Hibernate hace el flush de los cambios pendientes. Un `INSERT` por JDBC en ese momento no ve las filas que el caso de uso todavía tiene solo en el contexto de persistencia. Si el evento referencia una de ellas, la FK falla (por ejemplo, el registro de C-04 crea el consultorio con JPA y publica `CUENTA_CREADA` en la misma transacción). Por eso se llama a `EntityManager.flush()` antes de insertar. El flush no cambia la semántica: esas sentencias se iban a ejecutar igual en el commit.
- *Dónde va el flush*: en el adaptador JDBC (`RepositorioDeAuditoriaJdbc.guardar`), no en el listener. Es el adaptador el que esquiva a JPA; si mañana la persistencia cambiara, el flush se iría con él, y la capa `application` no depende de `jakarta.persistence`.
- No hace falta `fallbackExecution`: C-02 ya impide publicar fuera de una transacción (K-1).
- *Alternativa descartada*: que cada caso de uso llame directo al repositorio de auditoría. Acopla todos los módulos a `auditoria` y ArchUnit lo prohíbe.

### D-4 `id` desde la aplicación e instante desde el evento
- El `id` sale de `GeneradorDeIds` (K-4). `ocurrido_at` es el instante que trae el evento, tomado del `Clock` en C-02. La columna conserva `DEFAULT now()` solo como red.
- *Por qué*: `now()` de Postgres es la hora de inicio de la transacción y no se puede fijar en los tests. El `Clock` sí.
- **IP inválida → `null`** (sugerencia de Maxi en la revisión del PR #12): `CambioAuditado` no valida la IP. Si llega algo que no es un literal IPv4 o IPv6, el registro de dominio la guarda como `null` en lugar de dejar que el `CAST(... AS inet)` falle y revierta el cambio auditado. La validación es sintáctica y **nunca resuelve nombres por DNS** (un `InetAddress.getByName("host")` haría una consulta de red).

### D-5 Esquema según la convención de C-02
- Columnas de `04` §EventoAuditoria, con `consultorio_id NOT NULL REFERENCES consultorio(id)`, `UNIQUE (consultorio_id, id)` y `ck_evento_auditoria_actor_tipo`.
- Índices `(consultorio_id, ocurrido_at DESC)` y `(consultorio_id, entidad, entidad_id)`.
- No hay FK hacia la entidad auditada, porque `entidad`/`entidad_id` es polimórfico.
- La FK a `consultorio` va **sin `ON DELETE CASCADE`**. Con cascada, borrar un consultorio intentaría borrar sus eventos, el trigger lo rechazaría y fallaría el borrado entero. La baja de consultorios no está en v1; cómo se retienen o anonimizan los eventos tras una baja se decide con PA-07.

### D-6 Enmascarado de PII en logs
- **Un solo juego de expresiones regulares**: los conversores delegan en `EnmascaradorDePii` de C-02 (K-6), como pidió Maxi en la revisión del PR #12. C-03 le agrega un caso: cadenas base64url de 43 o más caracteres (tokens de 256 bits, también los hashes SHA-256 en hexadecimal) → `***`. Los UUID (36 caracteres) quedan legibles.
- **Mensaje**: `MensajeSinPii` (extiende `MessageConverter`) enmascara el mensaje formateado.
- **Excepciones**: el conversor del mensaje no toca el stack trace, y las excepciones de Postgres llevan valores en el detalle (por ejemplo `Key (email)=(...) already exists`). `ExcepcionSinPii` extiende el `ExtendedWhitespaceThrowableProxyConverter` de Spring Boot (el de `%wEx`) y enmascara su salida.
- **`logback-spring.xml`** (nuevo): incluye los `defaults.xml` y el `console-appender.xml` de Spring Boot y, **después** de incluirlos, redefine las palabras de conversión `m`, `msg`, `message`, `wEx` y `ex` con los conversores propios. Así el patrón por defecto de Spring Boot queda igual pero enmascarado.
- Al fallar, el listener loguea solo `accion`, `entidad` y `entidadId`, nunca los valores, y relanza la excepción.
- *Ubicación*: los conversores van en `compartido.infrastructure.logging`, porque aplican a toda la aplicación. C-02 dejó explícitamente este mecanismo para C-03 (Non-Goals de su design).
- *Alternativa descartada*: confiar en que nadie loguee PII. No escala con un equipo de 5 personas.

### D-7 Estrategia de tests (Strict TDD)
- **Unitarios** (sin Spring): el mapeo del evento al registro (incluida la IP inválida), el token en `EnmascaradorDePii` y los conversores.
- **Integración**: tests que extienden `IntegracionPostgresTest`, con la aplicación conectada como `app_rw`:
  - persistencia completa;
  - `UPDATE`, `DELETE` y `TRUNCATE` rechazados para `app_rw` (permisos);
  - trigger activo aunque el rol tenga el permiso: se prueba conectado como `app_owner`, que como dueño de la tabla tiene todos los privilegios, así que solo lo frena el trigger;
  - rollback del caso de uso que no deja evento;
  - fallo de auditoría que revierte el cambio: el caso de uso inserta un consultorio y publica un evento de **otro** consultorio inexistente; la FK del evento falla y el consultorio tampoco queda;
  - cambio auditado hecho **con JPA** y referenciado por el evento: C-02 no tiene entidades JPA, así que el test usa una entidad mínima mapeada a `consultorio` en `pruebas.auditoria` (fuera de `ar.sistemaodontologico`, por lo que pasó en C-01 con una `@Entity` de test en la raíz), registrada con `@EntityScan` solo en ese test. Sin el flush de D-3, falla por la FK;
  - consultorio inexistente que falla;
  - eventos de A y B aislados.
  - logs: con `OutputCaptureExtension`, una `DataIntegrityViolationException` con un email ficticio no lo muestra ni en el mensaje ni en el stack trace, y el log del listener ante un fallo no incluye los valores.
- **ArchUnit**: ningún módulo fuera de `auditoria` depende de `auditoria..`.
- Datos ficticios evidentes (`@example.com`, `+54 9 11 0000-00xx`).

## Risks / Trade-offs

- [Choque de número de migración: C-04 también propone `V2` provisorio] → Se numera al hacer rebase, con el siguiente libre, justo antes de marcar el PR como listo. Hoy `V2` está libre.
- [El enmascarado por regex deja pasar formatos raros o enmascara de más] → Se cubre con tests de casos conocidos; preferimos enmascarar de más. El patrón de tokens también tapa cualquier palabra de 43 o más caracteres alfanuméricos; en los logs actuales no hay ninguna legítima.
- [Los eventos de operaciones que fallan se pierden con el rollback] → Un evento `BEFORE_COMMIT` existe solo si la transacción confirma. Un login fallido que termina en excepción revierte la transacción y se lleva el `LOGIN_FALLIDO`. Además, si el email no existe no hay `consultorio_id` (`NOT NULL` con FK). C-03 no lo resuelve, pero deja la dirección para C-05:
  - **Que la operación fallida confirme su transacción**: el caso de uso devuelve un resultado (éxito o tipo de fallo) en lugar de lanzar una excepción, y la capa web lo traduce a 401/423. Así el evento y el contador de intentos persisten juntos. Es la opción que toma la propuesta de C-05.
  - **Alternativa**: publicar ese evento en una transacción propia (`REQUIRES_NEW`). Sirve cuando la operación no puede evitar el rollback, a costa de que el evento y el cambio queden en transacciones distintas.
  - **Sin consultorio** (email inexistente): no se registra evento. Queda en el log con el email enmascarado (D-6) y lo acota el rate limit de C-05. Registrarlo exigiría `consultorio_id` nullable, que rompe la regla de que todo evento pertenece a un consultorio.
- [C-04 todavía nombra `app_rw` fijo en su D12] → C-02 ya usa `${app-user}`. Maxi le pidió a Fran que C-04 lo corrija en su implementación.

## Migration Plan

- Despliegue: la migración es aditiva (tabla, función, triggers y permisos). No toca datos.
- Rollback: no se edita una migración mergeada (regla dura 5). Si hiciera falta revertir, se hace con una migración nueva; antes de producción no hay datos que preservar.

## UI Mockups

- Design system revision: 1
- Decision: no (no UI) — 2026-11-20
