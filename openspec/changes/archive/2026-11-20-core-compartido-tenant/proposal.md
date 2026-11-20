# Proposal

> Change **C-02** de [CHANGES.md](../../../CHANGES.md) · Governance **CRITICO** · Depende de `C-01` (`foundation-setup`) · Responsable: Fran · Aprobación: Maxi.

## Why

Todos los changes de backend que siguen (auditoría, auth, configuración, agenda, reserva pública) necesitan resolver **de qué consultorio son los datos**, cómo se devuelven los errores, qué hora es y cómo se publican los eventos de auditoría. Si cada change resuelve esto por su cuenta, el aislamiento entre tenants (RN-TE-01, RN-TE-04) queda librado a la disciplina de cada PR y una sola query sin `consultorio_id` filtra datos de pacientes de otro consultorio (Ley 25.326). C-02 fija ese núcleo una sola vez, con invariantes en la base y reglas de arquitectura que fallan el build, antes de que exista la primera tabla de negocio.

## What Changes

- **Migración Flyway `V1`** (la primera del proyecto): extensiones `btree_gist` y `citext`; tabla `consultorio` según [04 §Consultorio](../../../knowledge-base/04_modelo_de_datos.md) con sus `CHECK` (slug, anticipaciones, paso de huecos, plan) y `UNIQUE (slug)`; constraint `UNIQUE (id)` reutilizable como destino de FKs y convención de FKs compuestas `(consultorio_id, id)` documentada para las tablas siguientes.
- **Identificadores**: generador de UUIDv7 en la aplicación (PK sin secuencias expuestas).
- **Paquete `compartido`**:
  - `ConsultorioId` (value object) y `TenantContext` explícito: el tenant activo se obtiene del contexto (que C-04 poblará desde el claim `cid`) y se pasa **explícitamente** a cada repositorio; nunca desde body ni query.
  - `Clock` inyectado como bean único de tiempo, en UTC.
  - Manejo global de errores en **Problem Details (RFC 7807)** con `codigo` estable, incluyendo `RECURSO_NO_ENCONTRADO` (404, también para recursos de otro tenant), `VALIDACION` (400), `CONFLICTO_CONCURRENCIA` (409) y `ERROR_INTERNO` (500) sin filtrar detalles.
  - Normalizador de teléfono a **E.164** con `libphonenumber` (región por defecto AR).
  - Validador de **slug** (formato RN-TE-03 + palabras reservadas) y generador de slug sugerido a partir del nombre.
- **Puerto de publicación de auditoría**: interfaz `PublicadorDeAuditoria` y evento `EventoAuditoriaPublicado` (`ApplicationEvent`). La persistencia **no** entra acá: llega en C-03.
- **Reglas de arquitectura (ArchUnit)**: los repositorios de negocio no exponen `findById`/`findAll` sin tenant y todo método de consulta recibe `ConsultorioId`.
- **Tests**: unitarios de slug, E.164 y mapeo de errores; integración con Testcontainers (PostgreSQL 16) de la migración V1 y de **aislamiento entre dos consultorios** (otro consultorio ⇒ 404).

Fuera de alcance: endpoints de negocio, `usuario` y JWT (C-04), filtro que puebla `TenantContext` (C-04), tabla `evento_auditoria` y su listener (C-03), endpoints de configuración del consultorio y seed local (C-08), Row Level Security (backlog).

## Capabilities

### New Capabilities

- `aislamiento-tenant`: tabla `consultorio` como tenant, resolución explícita del consultorio activo, FKs compuestas, repositorios siempre filtrados por consultorio y respuesta 404 ante recursos ajenos.
- `slug-consultorio`: formato, unicidad, palabras reservadas y sugerencia del slug del link público.
- `errores-api`: contrato de errores HTTP en Problem Details con `codigo` estable.
- `telefono-e164`: normalización y validación de teléfonos argentinos a E.164.
- `auditoria`: puerto de publicación de eventos de auditoría dentro de la transacción del cambio (C-03 agregará la persistencia append-only como delta sobre esta capability).

### Modified Capabilities

_Ninguna: es el primer change con specs._

## Impact

- **Código**: `backend/src/main/java/<paquete-base>/compartido/**`, `backend/src/main/resources/db/migration/V1__consultorio_y_extensiones.sql`, tests espejo y `ArchitectureTest`.
- **Dependencias nuevas**: `com.googlecode.libphonenumber:libphonenumber`; generador UUIDv7 (`com.fasterxml.uuid:java-uuid-generator`). ArchUnit y Testcontainers ya los trae C-01.
- **API**: no agrega endpoints; define el formato de error que todos los endpoints posteriores heredan (contrato del frontend vía `api-types.ts`).
- **Base de datos**: primera migración; afecta a todas las tablas futuras (convención de `consultorio_id` + FK compuesta).
- **Changes desbloqueados**: C-03, C-04 (y transitivamente todo el camino crítico) y C-21.
- **Preguntas abiertas**: toca **PA-01** (teléfono E.164 como clave del paciente) y **PA-03** (plan/prueba). Ver `design.md` §Riesgos: este change solo provee el normalizador y las columnas de plan con sus valores por defecto ya decididos (DD-09, prueba 30 días), sin fijar la regla de deduplicación de pacientes ni el precio.
