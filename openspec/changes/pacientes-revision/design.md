# Design

> **Borrador parcial.** Por ahora este documento solo registra el gate de mockups de C-17, que Octi prepara como trabajo anticipado (paso 4 del plan). `proposal.md`, `specs/` y `tasks.md`, más el resto de este diseño (endpoints, auditoría, aislamiento por tenant y tests), se completan cuando C-09 y C-11 estén `[x]`. C-17 es governance **MEDIO** y tiene pantallas: no se escribe código de producción hasta que Maxi apruebe los mockups.

## Context

- C-17 permite revisar los pacientes que la deduplicación por DNI marcó con `requiere_revision` (US-070, RN-RP-03, DD-24): listado con filtro "requiere revisión", contador visible desde la agenda y la acción de confirmar o corregir nombre, apellido y email (ver `CHANGES.md` §C-17).
- Contratos que va a consumir (todavía no existen): `GET /api/v1/pacientes?requiereRevision=true` (paginado, por tenant), `PATCH /api/v1/pacientes/{id}` y `POST /api/v1/pacientes/{id}/confirmar-revision`, con auditoría `PACIENTE_REVISADO` (RN-AD-05) y 404 ante otro consultorio (RN-TE-04).
- Base visual: sistema de diseño rev. 1, shell de C-06 y agenda de C-13.

## Decisions

Las decisiones de UI están en `mockups/README.md` §Decisiones, aprobadas por Maxi en la revisión de #36 (2026-11-20). La que excede la UI quedó registrada:

- **Guardar lo informado en la reserva** ([DD-38](../../../knowledge-base/09_decisiones_y_supuestos.md)): la deduplicación guarda nombre, apellido y email escritos en la reserva que disparó la marca, más el instante y el turno de origen, y C-17 los borra al revisar. Columnas en la `V6` de C-10 (Fran); las llenan C-11 y C-15.

## Open Questions

- Ninguna de UI. El texto de §C-11 en `CHANGES.md` (deduplicación por teléfono) lo corrige Fran en la propuesta de C-11.

## UI Mockups

- Design system revision: 1
- Decision: yes (2026-11-20)
- Mockups: openspec/changes/pacientes-revision/mockups/ (`agenda-aviso.html`, `pacientes.html`, `paciente.html`, más `base.css`, `agenda.css`, `pacientes.css` y `mockup.js`)
- Approval: pending
- Preview: rendered (2026-11-20, segunda versión con la revisión de #36); `mockups/previews/*-mobile-390x844.png`, `mockups/previews/*-desktop-1440x900.png`
- Visual verify: pending
