# Design

> **Borrador parcial.** Por ahora este documento solo registra el gate de mockups de C-17, que Octi prepara como trabajo anticipado (paso 4 del plan). `proposal.md`, `specs/` y `tasks.md`, más el resto de este diseño (endpoints, auditoría, aislamiento por tenant y tests), se completan cuando C-09 y C-11 estén `[x]`. C-17 es governance **MEDIO** y tiene pantallas: no se escribe código de producción hasta que Maxi apruebe los mockups.

## Context

- C-17 permite revisar los pacientes que la deduplicación por DNI marcó con `requiere_revision` (US-070, RN-RP-03, DD-24): listado con filtro "requiere revisión", contador visible desde la agenda y la acción de confirmar o corregir nombre, apellido y email (ver `CHANGES.md` §C-17).
- Contratos que va a consumir (todavía no existen): `GET /api/v1/pacientes?requiereRevision=true` (paginado, por tenant), `PATCH /api/v1/pacientes/{id}` y `POST /api/v1/pacientes/{id}/confirmar-revision`, con auditoría `PACIENTE_REVISADO` (RN-AD-05) y 404 ante otro consultorio (RN-TE-04).
- Base visual: sistema de diseño rev. 1, shell de C-06 y agenda de C-13.

## Decisions

Las decisiones de UI están en `mockups/README.md` §Decisiones para revisar. Hay una que excede la UI y necesita a Maxi:

- **Guardar lo informado en la reserva** (propuesta, pendiente de DD): para mostrar qué difiere, la deduplicación tendría que guardar el nombre, apellido y email escritos en la reserva que disparó la marca, y borrarlos al revisar. Toca C-10, C-11, C-15 y C-17.

## Open Questions

- ¿Se aprueba guardar lo informado en la reserva (decisión 1 del README de mockups)? Si no, la revisión muestra solo los datos guardados y el turno de origen.
- `CHANGES.md` describe la deduplicación de C-11, C-15 y C-17 por teléfono; DD-24 la cambió a DNI. Hay que corregir el texto.

## UI Mockups

- Design system revision: 1
- Decision: yes (2026-11-20)
- Mockups: openspec/changes/pacientes-revision/mockups/ (`agenda-aviso.html`, `pacientes.html`, `paciente.html`, más `base.css`, `agenda.css`, `pacientes.css` y `mockup.js`)
- Approval: pending
- Preview: rendered (2026-11-20); `mockups/previews/*-mobile-390x844.png`, `mockups/previews/*-desktop-1440x900.png`
- Visual verify: pending
