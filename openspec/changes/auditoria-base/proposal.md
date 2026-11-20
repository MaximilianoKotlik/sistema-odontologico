# Proposal

> Change **C-03** de [CHANGES.md](../../../CHANGES.md) · Governance **CRITICO** · Depende de `C-02` (`core-compartido-tenant`) · Responsable: Mati · Aprobación: Maxi.
>
> **Se apoya en la propuesta de C-02 mergeada en el PR #4, cuyo código todavía no está en `main`.** Usa estas piezas de C-02: `PublicadorDeAuditoria`, `EventoDeAuditoria`, `GeneradorDeIds`, la tabla `consultorio` y la convención de FKs. Si la implementación de C-02 cambia alguna de ellas, esta propuesta se ajusta.

## Why

La transparencia es el diferenciador del producto, y el registro de auditoría inmutable es una de sus promesas centrales: RN-AD-01..03, LG-05 de la Ley 25.326 y la página `/seguridad` (RN-PL-06), que solo puede afirmar lo que esté implementado. C-02 define cómo se publican los eventos de auditoría, pero nada los guarda todavía. Sin C-03, los changes que modifican turnos, bloqueos, configuración o credenciales (C-05, C-08, C-11 y siguientes) no pueden cumplir la regla dura 7 del proyecto: un evento de auditoría en la misma transacción.

## What Changes

- Nueva migración Flyway con la tabla `evento_auditoria`, según `04_modelo_de_datos.md` §EventoAuditoria y la convención de FKs de C-02:
  - Columnas: `id`, `consultorio_id`, `ocurrido_at`, `actor_tipo`, `actor_id`, `entidad`, `entidad_id`, `accion`, `valor_anterior`/`valor_nuevo` JSONB e `ip` INET.
  - Índices `(consultorio_id, ocurrido_at DESC)` y `(consultorio_id, entidad, entidad_id)`.
- Inmutabilidad garantizada en la base (RN-AD-02), en dos capas:
  - Triggers `BEFORE UPDATE OR DELETE` (por fila) y `BEFORE TRUNCATE` (por sentencia) que lanzan una excepción.
  - Permisos: `REVOKE UPDATE, DELETE, TRUNCATE` explícito al rol de la aplicación. Es necesario porque los privilegios por defecto de C-01 le dan `UPDATE` y `DELETE` sobre toda tabla nueva. El rol no va fijo en el SQL: sale de `DB_APP_USER` mediante un placeholder de Flyway.
- Módulo `auditoria`: un listener `@TransactionalEventListener(phase = BEFORE_COMMIT)` que inserta en la misma transacción cada `EventoDeAuditoria` que publica el puerto de C-02. Si la inserción falla, se revierte el cambio auditado (RN-AD-03). Antes de insertar hace flush de Hibernate, para que el evento pueda referenciar filas que el caso de uso creó con JPA en la misma transacción.
- Enmascarado de PII en los logs: emails, teléfonos y tokens se enmascaran con conversores de Logback, tanto en el mensaje como en el stack trace de las excepciones. El listener nunca loguea `valor_anterior` ni `valor_nuevo`.
- Tests de integración sobre `IntegracionPostgresTest` (de C-01), con la aplicación conectada como `app_rw`:
  - `UPDATE`, `DELETE` y `TRUNCATE` fallan.
  - El evento se revierte junto con la transacción.
  - Un fallo de auditoría revierte el cambio.
  - Aislamiento por tenant del lado de la escritura.
- Fuera de alcance:
  - El contenido del evento, la prohibición de secretos y la exigencia de transacción ya los resuelve C-02.
  - La consulta del registro (endpoint, filtros, UI y test de 404 entre consultorios) va en C-18.
  - Los eventos de cada dominio los emite cada change: auth en C-05, configuración en C-08, agenda en C-11/C-12.
  - Retención y borrado tras la baja: PA-07.
  - Auditar operaciones que fallan, como `LOGIN_FALLIDO`: lo resuelve C-05. `design.md` §Risks deja la dirección.

## Capabilities

### New Capabilities
- (ninguna)

### Modified Capabilities
- `auditoria`: capability que crea C-02. Se agregan los requisitos de persistencia append-only, atomicidad con el cambio, inmutabilidad en la base, pertenencia a un consultorio y enmascarado de PII en logs. C-18 le va a sumar después los de consulta.

## Impact

- **Código**: el paquete `auditoria/` (`domain`, `application`, `infrastructure`) y `compartido/infrastructure/logging/` (conversor de Logback). Consume las piezas de C-02 sin modificarlas.
- **Base**: una migración nueva, numerada al hacer rebase sobre `main` (C-04 también propone `V2` provisorio).
- **Configuración**: un `logback-spring.xml` nuevo en `backend/src/main/resources/`.
- **Dependencias**: ninguna nueva; alcanza con Jackson, Spring JDBC y Testcontainers, que ya están.
- **Governance**: CRITICO. No se escribe código de producción hasta que Maxi apruebe la propuesta y C-02 esté en `[x]`.
