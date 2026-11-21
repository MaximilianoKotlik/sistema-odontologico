# Design

> **Borrador parcial.** Por ahora este documento solo registra el gate de mockups de C-22, que Octi prepara como trabajo anticipado (paso 5 del plan). `proposal.md`, `specs/` y `tasks.md`, más el resto de este diseño (fuente de contenido, test de US-062 y revisión de afirmaciones contra el código), se completan cuando C-19 esté `[x]`. C-22 es governance **ALTO** (afirmaciones públicas sobre datos de salud): no se escribe código de producción hasta que Maxi apruebe el mockup.

## Context

- C-22 es la página pública `/seguridad` (US-062): cómo se tratan los datos, auditoría inmutable, exportación completa, hosting y lo que todavía no está, sin declarar cumplimiento que no se pueda respaldar (RN-PL-05). Cada afirmación tiene que corresponder a una capacidad implementada y la página muestra la fecha de actualización (RN-PL-06).
- Capacidades que respalda: aislamiento por tenant (C-02), auditoría append-only (C-03), autenticación y bloqueo (C-04, C-05), exportación (C-19), consulta de la auditoría (C-18), token del paciente (C-15, C-16).
- Base visual: sistema de diseño rev. 1 y la estructura de la página pública `/precios` de C-21.

## Decisions

Las decisiones de UI y de contenido están en `mockups/README.md` §Decisiones, aprobadas por Maxi en la revisión de #40 (2026-11-20). La principal para la implementación:

- **Contenido en una fuente versionada compilada con la página**: Markdown o YAML en el repo, convertido al compilar. Sin endpoint ni estados de carga; cada cambio de texto pasa por un PR.

## Open Questions

- PA-02 (proveedor y país del VPS, backups) y el proveedor SMTP: aparecen como `(TBD)` y **bloquean la publicación**.
- Confirmar TLS en el VPS antes de afirmar "conexión cifrada".
- Email de seguridad (sí, decidido por Maxi): `(TBD)` hasta definir el dominio (DD-34); bloquea la publicación como PA-02.

## UI Mockups

- Design system revision: 1
- Decision: yes (2026-11-20)
- Mockups: openspec/changes/pagina-seguridad/mockups/ (`seguridad.html`, más `seguridad.css` y `mockup.js`)
- Approval: pending
- Preview: rendered (2026-11-20, segunda versión con la revisión de #40); `mockups/previews/*-mobile-390x844.png`, `mockups/previews/*-desktop-1440x900.png`
- Visual verify: pending
