# Proposal

> Change **C-10** de [CHANGES.md](../../../CHANGES.md) · Governance **ALTO** · Depende de `C-08` (`consultorio-config-backend`) · Responsable: Fran · Aprobación: Maxi.
>
> Este change usa C-02 (mergeado) y las propuestas de C-03, C-04 y C-08 (PR #15, en borrador). El código no se escribe hasta que se cumplan tres condiciones:
> - C-08 está `[x]`.
> - Maxi aprobó esta propuesta (ALTO: se propone y se espera la revisión).
> - **PA-01 está resuelta** (ver Impact).

## Why

La agenda es el núcleo del producto, y la regla más crítica del Discovery es que dos turnos no se solapen salvo sobreturno marcado (RN-AG-01). Hoy no existen las tablas `paciente`, `turno` ni `bloqueo`, ni las reglas que deciden dónde cabe un turno. C-10 fija esas invariantes en dos capas antes de que existan los casos de uso (C-11), la API (C-12) y la reserva pública (C-15):
- en un dominio puro, testeable en milisegundos;
- en PostgreSQL, que rechaza el solapamiento aunque falle el código (DD-12).

Está en el camino crítico: C-11, C-12, C-13, C-14, C-15 y C-18 se apoyan en este modelo.

## What Changes

- **Migración** `V{n}__agenda_paciente_turno_bloqueo.sql`, según 04 y `CONVENCIONES.md`:
  - `paciente`: único `(consultorio_id, telefono_e164)`, datos mínimos, `origen`, `requiere_revision` y consentimiento (fecha y versión). Sin `DELETE` para `app_rw` (RN-AG-07).
  - `turno`: `duracion_min`, `fin` consistente con `inicio + duracion_min` (CHECK), `es_sobreturno`, cuatro estados, `origen`, `cancelado_por` y `motivo_cancelacion` coherentes con el estado, `nota_interna VARCHAR(500)` y `version`. Lleva FKs compuestas a `paciente` y `tipo_prestacion` y la restricción **`EXCLUDE USING gist`** sobre los turnos activos no sobreturno. Sin `DELETE` para `app_rw`.
  - `bloqueo`: `inicio`, `fin` y `motivo`, con índice GiST `(consultorio_id, tstzrange(inicio, fin))`.
  - Instantes alineados a múltiplos de 5 minutos en la base (RN-AG-08).
- **Dominio puro** en `agenda.domain`, sin Spring ni JPA:
  - Value objects: `Intervalo` (semiabierto `[inicio, fin)`) y `Duracion` (múltiplo de 5, de 5 a 480).
  - Entidades: `Turno` (con `fin` derivado, RN-AG-12), `Bloqueo` y `Paciente`.
  - `MaquinaDeEstadosTurno` (RN-AG-06).
  - `ValidadorDeSolapamiento`: RN-AG-01, RN-AG-03 y RN-AG-04 para turnos y sobreturnos.
  - `ValidadorDeBloqueo`: RN-AG-05, rechaza el bloqueo y lista los turnos en conflicto.
  - `CalculadoraDeHuecos`: disponibilidad semanal en la zona del consultorio, bloqueos, turnos activos (incluidos los sobreturnos), paso desde el inicio de la franja, anticipación mínima y horizonte (RN-RP-01).
- **Tests**:
  - Unitarios de dominio con `Clock` inyectado (la mayoría de los tests).
  - Integración con Testcontainers: la base rechaza el solapamiento aun salteando el servicio, y se prueban las FKs compuestas, los CHECKs y los permisos.
  - La regla ArchUnit `dominioSinFramework` (C-02) pasa a cubrir `agenda.domain`.
- **Seed local**: se completan los datos de ejemplo de 04 §Seed (5 pacientes ficticios y 10 turnos de la semana en curso, con 1 sobreturno y 1 bloqueo), solo con el perfil `local`.

**Granularidad de la prevención de solapamientos (requisito de CHANGES.md).**
- La práctica de referencia es evaluar el no-solapamiento **por profesional y por sillón/box**.
- La v1 lo evalúa **por consultorio**: hay un único profesional y un único sillón implícito (RN-AG-13, DD-17), y multi-profesional y multi-sillón son funcionalidades opcionales del Discovery (§6, ítem 7). Así, la restricción es una sola: `EXCLUDE (consultorio_id WITH =, tstzrange(inicio, fin, '[)') WITH &&)`.
- **Punto de extensión** (RN-AG-13): al incorporar sillones o profesionales, `turno` suma `profesional_id` y `sillon_id`, y la restricción actual se reemplaza por **dos** exclusiones, `(consultorio_id, profesional_id, rango)` y `(consultorio_id, sillon_id, rango)`. La migración es aditiva: asigna los turnos existentes al profesional titular y a un sillón por defecto.
- [PA-14](../../../knowledge-base/10_preguntas_abiertas.md) es la pregunta abierta que decide cuándo adelantar multi-sillón. Mientras siga abierta, el modelo no agrega columnas especulativas. El dominio recibe el ámbito del solapamiento como parámetro, para que el cambio sea local (ver design D6).

**Fuera de alcance**:
- Los casos de uso, las transacciones y la auditoría de turnos, bloqueos y pacientes (C-11).
- Repositorios y mapeo JPA (C-11).
- Endpoints (C-12, C-15).
- `token_gestion_turno` (C-15).
- La revisión de pacientes marcados (C-17).
- La deduplicación al reservar por link (C-15, RN-RP-03): este change solo deja la clave única en la base.

## Capabilities

### New Capabilities

- `turnos`: modelo del turno, con duración y fin derivado, estados y sus transiciones, marca de sobreturno y prevención de solapamientos en dominio y base.
- `bloqueos`: períodos en los que el consultorio no atiende, y su interacción con los turnos activos.
- `pacientes`: datos mínimos del paciente por consultorio, identidad por teléfono E.164 y marca de revisión.
- `huecos-disponibles`: cálculo de los horarios en que cabe un turno de cierta duración, a partir de la disponibilidad, los bloqueos y los turnos.

### Modified Capabilities

Ninguna. `aislamiento-tenant` ya exige FKs compuestas y filtro por consultorio, y este change solo lo aplica a tablas nuevas. `disponibilidad-semanal` y `prestaciones` llegan con C-08 y no cambian.

## Impact

- **Código**:
  - `backend/.../agenda/domain/**` (nuevo).
  - Migración `V{n}__agenda_paciente_turno_bloqueo.sql`, con número provisorio que se fija al rebasar después de C-03, C-04, C-05 y C-08.
  - Extensión de `db/seed-local/R__demo.sql` (C-08).
  - Tests en `backend/src/test/java/.../agenda/**`.
- **API**: ninguna. El contrato OpenAPI no cambia.
- **Dependencias**: ninguna nueva. `btree_gist` ya está desde la V1.
- **Preguntas abiertas**:
  - **PA-01 (Alta, abierta)**: la identidad del paciente decide la clave única de `paciente`. El modelo sigue el default DD-09: teléfono E.164 único por consultorio. Por la regla dura 16, **el apply necesita que el Product Owner confirme PA-01** (pasa a `DD-NN`) o que se registre otra decisión. Si cambia la clave, cambian esta migración y C-15.
  - PA-10 (Media): teléfono obligatorio también en la carga manual. Se sigue el default (`NOT NULL`).
  - PA-14 (Media): no bloquea; ver la granularidad más arriba.
- **Changes desbloqueados**: C-11 y, a través de él, C-12 y C-15.
