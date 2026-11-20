# Design

## Context

Ver `proposal.md` §Why. Estado de partida (actualizado al traer `main`, 2026-11-20):

- **C-01** (`foundation-setup`) está **mergeado** (PR #6). Ya existen:
  - El paquete base `ar.sistemaodontologico` con `auditoria/` vacío.
  - `IntegracionPostgresTest`: Testcontainers con PostgreSQL 16 y el script `docker/postgres/init/01-roles.sh`. Flyway migra con `app_owner` y la aplicación conecta con `app_rw`.
  - `ArchitectureTest`.
  - Todavía no hay `logback-spring.xml` ni migraciones.
- El script de roles de C-01 hace `ALTER DEFAULT PRIVILEGES ... GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO app_rw`. **Toda tabla nueva le da `UPDATE` y `DELETE` a `app_rw` salvo que la migración los revoque.**
- **C-02** (`core-compartido-tenant`): su **propuesta está mergeada** (PR #4) pero el código todavía no, y el change sigue en `[ ]`. Este diseño consume el contrato de esa propuesta (§Contrato de C-02). Si la implementación de C-02 lo cambia, se ajusta en la tarea 0.1.
- Arquitectura (`08` §Patrones): monolito modular con capas `web → application → domain ← infrastructure`; Flyway con SQL plano y `ddl-auto=validate`.

## Contrato de C-02 que se consume

Tomado de `openspec/changes/core-compartido-tenant` (design D3, D7 y D10, y la spec `auditoria`):

| # | Pieza de C-02 | Uso en C-03 |
|---|---------------|-------------|
| K-1 | `PublicadorDeAuditoria.publicar(EventoDeAuditoria)` sobre `ApplicationEventPublisher`. **Falla si no hay transacción activa.** | C-03 solo se suscribe; no agrega su propia guarda de transacción. |
| K-2 | `EventoDeAuditoria`: un `record` con consultorio, instante del `Clock`, `actorTipo` (`ODONTOLOGO`/`PACIENTE`/`SISTEMA`), `actorId?`, entidad, `entidadId`, acción (`String` en MAYÚSCULAS validado por patrón), `valorAnterior?`/`valorNuevo?` como `Map<String, Object>` e `ip?`. | Se mapea uno a uno a las columnas. El instante viene en el evento y se guarda en `ocurrido_at`. |
| K-3 | El constructor del record **rechaza claves prohibidas** (`password`, `password_hash`, `token`, `*_token`, `*_hash`) y campos obligatorios faltantes. | C-03 no repite la validación: no se puede construir un evento inválido. |
| K-4 | `GeneradorDeIds` (UUIDv7) en `compartido.domain`. | Genera el `id` del registro. |
| K-5 | La migración `V1__consultorio_y_extensiones.sql` y la convención de D7: `consultorio_id UUID NOT NULL REFERENCES consultorio(id)`, `UNIQUE (consultorio_id, id)`, índices que empiezan por `consultorio_id` y constraints con nombre. | La tabla de auditoría sigue la convención. |

Diferencia menor dentro de C-02: la propuesta nombra el evento `EventoAuditoriaPublicado (ApplicationEvent)` y el diseño publica el record `EventoDeAuditoria`. El listener se suscribe al tipo que publique la implementación real (tarea 0.1).

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
- **El rol no va fijo en el SQL**: es configurable con `DB_APP_USER` (`01-roles.sh`, `application.yml`). La migración usa el placeholder de Flyway `${app-user}`, definido en `application.yml` como `spring.flyway.placeholders.app-user: ${DB_APP_USER}`. Con el nombre fijo, en un entorno donde el rol se llame distinto la migración fallaría o no protegería nada. La convención vale para todas las migraciones con `GRANT`/`REVOKE`; se le avisa a Fran (C-02 D7 y C-04 D12 también nombran `app_rw`).
- *Por qué las dos capas*: el permiso protege al rol de la aplicación. El trigger protege también contra un rol con permisos de más, como un `GRANT` equivocado en el futuro. `TRUNCATE` no dispara triggers de fila; por eso lleva el suyo.
- *Alternativa descartada*: solo permisos. Un error de configuración lo rompería sin que lo note ningún test.
- *Límite aceptado*: `app_owner` puede deshabilitar el trigger, pero es el rol de las migraciones y la aplicación no lo usa en ejecución.

### D-2 Persistencia con JDBC, sin entidad JPA
- El repositorio en `auditoria.infrastructure` inserta con `JdbcClient` (`CAST(? AS jsonb)`, `CAST(? AS inet)`). Los mapas se serializan con el `ObjectMapper` de Spring.
- *Por qué*: un evento solo se inserta. Una entidad JPA invita a hacer `save()` sobre una instancia cargada, que sería un `UPDATE`, y además exige mapear JSONB e INET. `JdbcClient` usa el mismo `DataSource` y comparte la transacción del `JpaTransactionManager`.
- *Alternativa descartada*: entidad con `@Immutable`. Ignora los updates en silencio en lugar de fallar.

### D-3 Listener `BEFORE_COMMIT`
- `@TransactionalEventListener(phase = BEFORE_COMMIT)` en `auditoria.application`: mapea el evento e inserta. Si lanza una excepción, se aborta el commit y el cambio auditado se revierte (RN-AD-03).
- **Flush antes de insertar**: Spring invoca los listeners `BEFORE_COMMIT` en `triggerBeforeCommit`, antes de `doCommit`, que es cuando Hibernate hace el flush de los cambios pendientes. Un `INSERT` por JDBC en ese momento no ve las filas que el caso de uso todavía tiene solo en el contexto de persistencia. Si el evento referencia una de ellas, la FK falla (por ejemplo, el registro de C-04 crea el consultorio con JPA y publica `CUENTA_CREADA` en la misma transacción). Por eso el listener llama a `EntityManager.flush()` antes de insertar. El flush no cambia la semántica: esas sentencias se iban a ejecutar igual en el commit.
- No hace falta `fallbackExecution`: C-02 ya impide publicar fuera de una transacción (K-1).
- *Alternativa descartada*: que cada caso de uso llame directo al repositorio de auditoría. Acopla todos los módulos a `auditoria` y ArchUnit lo prohíbe.

### D-4 `id` desde la aplicación e instante desde el evento
- El `id` sale de `GeneradorDeIds` (K-4). `ocurrido_at` es el instante que trae el evento, tomado del `Clock` en C-02. La columna conserva `DEFAULT now()` solo como red.
- *Por qué*: `now()` de Postgres es la hora de inicio de la transacción y no se puede fijar en los tests. El `Clock` sí.

### D-5 Esquema según la convención de C-02
- Columnas de `04` §EventoAuditoria, con `consultorio_id NOT NULL REFERENCES consultorio(id)`, `UNIQUE (consultorio_id, id)` y `ck_evento_auditoria_actor_tipo`.
- Índices `(consultorio_id, ocurrido_at DESC)` y `(consultorio_id, entidad, entidad_id)`.
- No hay FK hacia la entidad auditada, porque `entidad`/`entidad_id` es polimórfico.
- La FK a `consultorio` va **sin `ON DELETE CASCADE`**. Con cascada, borrar un consultorio intentaría borrar sus eventos, el trigger lo rechazaría y fallaría el borrado entero. La baja de consultorios no está en v1; cómo se retienen o anonimizan los eventos tras una baja se decide con PA-07.

### D-6 Enmascarado de PII en logs
- Un conversor de Logback (`MessageConverter` propio) se registra en un `logback-spring.xml` nuevo (C-01 no lo tiene) y aplica expresiones regulares sobre el mensaje formateado:
  - emails → `a***@example.com`;
  - teléfonos E.164 o con separadores → se conservan los últimos 2 dígitos;
  - cadenas base64url de 43 o más caracteres (tokens de 256 bits).
- **También las excepciones**: el conversor de `%msg` no toca el stack trace, y las excepciones de Postgres llevan valores en el detalle (por ejemplo `Key (email)=(...) already exists`). Un `ThrowableProxyConverter` propio aplica las mismas expresiones regulares a la salida de `%ex`, y el patrón de `logback-spring.xml` usa ese conversor en lugar del de por defecto.
- Al fallar, el listener loguea solo `accion`, `entidad` y `entidadId`.
- *Ubicación*: `compartido.infrastructure.logging`, porque aplica a toda la aplicación. C-02 deja explícitamente este mecanismo para C-03 (Non-Goals de su design).
- *Alternativa descartada*: confiar en que nadie loguee PII. No escala con un equipo de 5 personas.

### D-7 Estrategia de tests (Strict TDD)
- **Unitarios** (sin Spring): el mapeo del evento al registro y el conversor de enmascarado.
- **Integración**: tests que extienden `IntegracionPostgresTest`, con la aplicación conectada como `app_rw`:
  - persistencia completa;
  - `UPDATE`, `DELETE` y `TRUNCATE` rechazados;
  - trigger activo aunque un rol de prueba tenga el permiso;
  - rollback del caso de uso que no deja evento;
  - fallo de auditoría que revierte un cambio hecho en una tabla de prueba (creada con `JdbcTemplate` desde el test, como D8 de C-02);
  - cambio auditado hecho **con JPA** y referenciado por el evento: un consultorio nuevo, persistido con la entidad JPA de C-02 (o, si C-02 no la tiene, con una entidad JPA mínima en `src/test` mapeada a `consultorio`), y el evento de ese consultorio en la misma transacción. Sin el flush de D-3, este test falla por la FK;
  - consultorio inexistente que falla;
  - eventos de A y B aislados.
- **ArchUnit**: ningún módulo fuera de `auditoria` depende de `auditoria..`.
- Datos ficticios evidentes (`@example.com`, `+54 9 11 0000-00xx`).

## Risks / Trade-offs

- [La implementación de C-02 se aparta de su propuesta] → Solo cambia el mapeo del listener. La tarea 0.1 lo contrasta después del rebase.
- [Choque de número de migración: C-04 también propone `V2` provisorio] → Se numera al hacer rebase, con el siguiente libre, justo antes de marcar el PR como listo.
- [La lista de claves prohibidas de C-02 no cubre `contrasena` ni `secret`] → Queda fuera de este change. Se le sugiere a Fran en la revisión de C-02.
- [El enmascarado por regex deja pasar formatos raros o enmascara de más] → Se cubre con tests de casos conocidos; preferimos enmascarar de más.
- [Los eventos de operaciones que fallan se pierden con el rollback] → Un evento `BEFORE_COMMIT` existe solo si la transacción confirma. Un login fallido que termina en excepción revierte la transacción y se lleva el `LOGIN_FALLIDO`. Además, si el email no existe no hay `consultorio_id` (`NOT NULL` con FK). C-03 no lo resuelve, pero deja la dirección para C-05:
  - **Que la operación fallida confirme su transacción**: el caso de uso devuelve un resultado (éxito o tipo de fallo) en lugar de lanzar una excepción, y la capa web lo traduce a 401/423. Así el evento y el contador de intentos persisten juntos. Es la opción que toma la propuesta de C-05.
  - **Alternativa**: publicar ese evento en una transacción propia (`REQUIRES_NEW`). Sirve cuando la operación no puede evitar el rollback, a costa de que el evento y el cambio queden en transacciones distintas.
  - **Sin consultorio** (email inexistente): no se registra evento. Queda en el log con el email enmascarado (D-6) y lo acota el rate limit de C-05. Registrarlo exigiría `consultorio_id` nullable, que rompe la regla de que todo evento pertenece a un consultorio.
- [Migraciones de C-02 y C-04 con `app_rw` fijo] → Se coordina con Fran que usen el mismo placeholder `${app-user}` (D-1).

## Migration Plan

- Despliegue: la migración es aditiva (tabla, función, triggers y permisos). No toca datos.
- Rollback: no se edita una migración mergeada (regla dura 5). Si hiciera falta revertir, se hace con una migración nueva; antes de producción no hay datos que preservar.

## UI Mockups

- Design system revision: 1
- Decision: no (no UI) — 2026-11-20
