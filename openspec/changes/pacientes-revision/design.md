# Design

## Context

- Ver `proposal.md` para la motivación. C-17 resuelve las revisiones que deja la deduplicación por DNI (US-070, RN-RP-03, DD-24): lista con filtro, revisión que compara lo guardado con lo escrito en la reserva (DD-38), y confirmar o corregir.
- **Lo que ya existe en `main`**: el `TenantContext` y la regla ArchUnit de repositorios con `ConsultorioId` (C-02), el puerto `PublicadorDeAuditoria` con `CambioAuditado` (C-03), el manejador global de errores con Problem Details (C-02), y el shell, el router y el cliente HTTP del frontend (C-06). El Service Worker solo cachea `GET /api/v1/agenda`, así que `/api/v1/pacientes` nunca queda guardado en el dispositivo.
- **Lo que viene de otros changes**:
  - C-10 (#18): tabla `paciente` con `requiere_revision` y, por DD-38, `nombre_informado`, `apellido_informado`, `email_informado`, `informado_at` e `informado_turno_id` en la migración `V6`, más el repositorio de pacientes por consultorio.
  - C-11 (#38) y C-15 (#30): marcan para revisión al deduplicar y guardan lo informado (queda la última reserva).
  - C-13: la agenda donde va el aviso. C-07 también toca la navegación del shell.
- Base visual: sistema de diseño rev. 1 y mockups aprobados (#36, versión `dcb700b`).

## Goals / Non-Goals

**Goals:**

- Que el odontólogo resuelva una revisión en un toque, viendo exactamente qué difiere.
- Que la ficha no cambie hasta que él elija, y que lo informado no quede guardado después de revisar.

**Non-Goals:**

- Buscar pacientes, editarlos fuera de una revisión, editar DNI o teléfono, borrar pacientes.
- Ver el historial de variantes que escribieron los pacientes: queda solo la última (DD-38).

## Decisions

Las decisiones de UI están en `mockups/README.md` §Decisiones, aprobadas por Maxi en la revisión de #36 (2026-11-20). La que excede la UI quedó registrada:

- **Guardar lo informado en la reserva** ([DD-38](../../../knowledge-base/09_decisiones_y_supuestos.md)): la deduplicación guarda nombre, apellido y email escritos en la reserva que disparó la marca, más el instante y el turno de origen, y C-17 los borra al revisar. Columnas en la `V6` de C-10 (Fran); las llenan C-11 y C-15.

Decisiones de implementación:

### D1 — Backend en el módulo `agenda`

El paciente vive en `agenda` (`08_arquitectura_propuesta.md`), así que C-17 suma ahí un controlador `web` con los cuatro endpoints, un caso de uso de aplicación para revisar (confirmar y corregir) y una consulta de pacientes paginada, sobre el repositorio de pacientes de C-10. Todas las consultas reciben el `ConsultorioId` del `TenantContext`; el id del body o de la query nunca define el consultorio.

- **Alternativa descartada**: un módulo `pacientes` nuevo. Partiría un agregado que C-10 y C-11 ya ubican en `agenda`.

### D2 — "Usar lo de la reserva" es un `PATCH` con lo informado

No hay un endpoint aparte: el frontend manda en el `PATCH` el nombre, el apellido y el email informados que recibió en el detalle. Así hay una sola forma de corregir, con la misma validación y la misma auditoría.

- **Trade-off aceptado**: si entre que el odontólogo abrió la revisión y tocó "Usar «…»" llega otra reserva con otro nombre, se aplica lo que él vio. Es lo que eligió y queda auditado.

### D3 — El contador sale del total de la lista

La cantidad de pacientes para revisar se obtiene con `GET /api/v1/pacientes?requiereRevision=true&size=1`, leyendo `total`. El shell la pide al iniciar la sesión y después de cada revisión, y la comparte con la agenda (aviso) y la navegación (contador). Sin endpoint nuevo.

- **Alternativa descartada**: sumar el contador a la respuesta de la agenda (C-12). Acoplaría dos changes y la caché offline de la agenda guardaría un dato de pacientes.

### D4 — Paginado y orden estable

Parámetros `page` (desde 0) y `size` (20 por defecto, máximo 50), orden por apellido, nombre e id para que el paginado no repita ni saltee pacientes. La respuesta trae `items` y `total`. "Mostrar más pacientes" pide la página siguiente y la agrega.

### D5 — Código de error `SIN_REVISION_PENDIENTE`

Confirmar o corregir un paciente sin revisión pendiente responde 409 `SIN_REVISION_PENDIENTE` (tipo `CONFLICTO` de C-02). Cubre la doble confirmación y la revisión desde dos pestañas; la pantalla recarga el paciente y lo muestra revisado.

### D6 — Auditoría con solo lo que cambió

`PACIENTE_REVISADO` se publica por `PublicadorDeAuditoria` en la misma transacción, con un `CambioAuditado` por campo que cambió (`nombre`, `apellido`, `email`) más `requiere_revision`. Lo informado no se audita: se borra (DD-38, RN-AD-05). Una confirmación audita solo `requiere_revision`.

### D7 — La comparación usa la misma regla que la deduplicación

La pantalla decide qué datos "difieren" con la misma comparación que usa la deduplicación para marcar (email sin distinguir mayúsculas). Así nunca muestra "Coinciden todos los datos" en un paciente marcado por una diferencia que el backend sí vio, ni marca "Distinto" por una mayúscula.

- **Hoy hay dos reglas**: C-11 compara nombre y apellido "sin distinguir mayúsculas ni espacios en los extremos", y C-15, "sin distinguir mayúsculas, tildes ni espacios extra". C-17 usa la que quede al unificarlas (se pide junto con el nombre del evento, en el proposal). La tarea 1.2 lo verifica.

### D8 — Navegación y rutas

`/app/pacientes` y `/app/pacientes/:id` se suman a la tabla de rutas del shell (`app.ts`). "Pacientes" entra en la barra lateral después de "Bloqueos" y en la hoja "Más" en mobile, con el contador de D3. Es el delta de la spec `app-shell`.

## Risks / Trade-offs

- **[C-10 o C-11 no incluyen DD-38]** → sin lo informado, la comparación no tiene la columna "En la reserva". La tarea 1.2 lo verifica antes de empezar; si falta, se frena y se coordina con Fran.
- **[Nombre del evento de marca distinto entre C-11 y C-15]** → no bloquea a C-17, que solo escribe `PACIENTE_REVISADO`, pero el Registro de cambios (C-18) mostraría dos nombres para lo mismo. Se pide unificarlo (proposal, Coordinación).
- **[Conflicto con C-07 en la navegación]** → los dos modifican "Navegación adaptada al ancho" de `app-shell`. El que archive segundo integra el texto del otro.
- **[Plan `VENCIDO`]** → C-20 define el bloqueo de escrituras; los endpoints de escritura de C-17 lo respetan y C-23 lo verifica.

## Migration Plan

Sin migración propia: las columnas de DD-38 las crea C-10. Se despliega después de C-09, C-10 y C-11. Sin datos que migrar.

## Open Questions

- Ninguna. El texto de §C-11 en `CHANGES.md` (deduplicación por teléfono) lo corrige Fran en la propuesta de C-11.

## UI Mockups

- Design system revision: 1
- Decision: yes (2026-11-20)
- Mockups: openspec/changes/pacientes-revision/mockups/ (`agenda-aviso.html`, `pacientes.html`, `paciente.html`, más `base.css`, `agenda.css`, `pacientes.css` y `mockup.js`)
- Approval: approved (2026-11-20, by Maxi), versión dcb700b (#36)
- Preview: rendered (2026-11-20, segunda versión con la revisión de #36); `mockups/previews/*-mobile-390x844.png`, `mockups/previews/*-desktop-1440x900.png`
- Visual verify: pending
