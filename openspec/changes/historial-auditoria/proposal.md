# Proposal

> Change **C-18** de [CHANGES.md](../../../CHANGES.md) · Governance **MEDIO** · Depende de `C-12` (`agenda-api-odontologo`) y `C-13` (`frontend-agenda`) · Responsable: Mati · Aprobación: Maxi.
>
> Es trabajo anticipado. Se apoya en C-03 (mergeado: `evento_auditoria` append-only), en la propuesta y los mockups de C-13 (mergeados en `openspec/changes/frontend-agenda/`) y en las propuestas de C-15 (#30) y C-19 (#32), que agregan eventos al registro. C-12 todavía no tiene propuesta. Maxi aprobó esta propuesta y los mockups en el PR #35 (versión `9cb3635`). El código se escribe recién cuando C-12 y C-13 estén `[x]`.

## Why

La auditoría inmutable es uno de los pilares del diferenciador de transparencia (Discovery D.3, US-040). Le permite al odontólogo resolver dudas con un paciente ("yo no cancelé", "el turno era a las 10") con un registro que nadie puede editar. Desde C-03, el registro se escribe y es append-only, pero **no hay forma de leerlo**: `RepositorioDeAuditoria` solo inserta, y la sección "Registro de cambios" del app-shell (C-06) muestra "Esta sección todavía no está disponible". C-18 lo hace consultable, por turno y para todo el consultorio, con los filtros de RN-AD-06.

## What Changes

- **Historial de un turno**: `GET /api/v1/turnos/{id}/historial` devuelve los eventos del turno en orden cronológico, del más viejo al más nuevo. Cada evento trae fecha, actor (tipo y nombre), acción, valor anterior y nuevo. Un turno de otro consultorio o inexistente responde 404.
- **Registro del consultorio**: `GET /api/v1/auditoria`, paginado por cursor, con filtros combinables:
  - `turnoId`;
  - rango de fechas `desde`/`hasta`, en fechas locales del consultorio e inclusivas;
  - `accion`, que se puede repetir.

  Sin filtros, devuelve todos los eventos del consultorio: turnos, bloqueos, configuración, prestaciones, disponibilidad, pacientes, accesos y exportaciones.
- **Contexto legible**:
  - el nombre del actor (el odontólogo, o el paciente cuando el actor es `PACIENTE`);
  - para los eventos de turnos, la fecha del turno y el paciente, así cada fila se entiende sin abrir nada.
- **Sin escrituras**: el change solo lee `evento_auditoria`, con el `SELECT` que `app_rw` ya tiene. No hay migraciones ni eventos nuevos. Leer el registro no se audita en v1.
- **Frontend**:
  - **Tarjeta "Historial"** en el detalle del turno (`/app/agenda/turnos/{id}`, de C-13), como línea de tiempo con el "antes → después" de cada cambio y un link "Ver en el registro".
  - **Sección "Registro de cambios"** (`/app/auditoria`): filtros por turno, rango de fechas y tipo de acción, guardados en la URL para que el botón Atrás funcione. Lista de eventos con "Cargar más", estado vacío y estado sin resultados.
  - Mockups aprobados antes de implementar.
- **Contrato OpenAPI** y `api-types.ts` regenerados.
- **Tests**:
  - US-040: un turno reservado por link y reprogramado muestra 2 eventos en orden. Filtros solos, combinados y sin resultados.
  - Paginación estable con eventos nuevos entre páginas.
  - Aislamiento por tenant en los dos endpoints, y Vitest de las dos pantallas.

**Fuera de alcance**:
- Auditar las lecturas del registro y de la historia clínica (v2, [11](../../../knowledge-base/11_marco_legal_argentino.md)).
- Exportar el registro: ya va completo en el ZIP de C-19 (`auditoria.json` y `auditoria.csv`).
- Búsqueda de texto libre y filtro por actor.
- Notificaciones.

## Capabilities

### New Capabilities

- `consulta-auditoria`: lectura del registro de auditoría del consultorio, por turno y con filtros por rango de fechas y acción, paginada, con el actor y el contexto del turno resueltos y aislada por tenant.
- `pantalla-registro-cambios`: la sección `/app/auditoria` y la tarjeta "Historial" del detalle del turno, con sus filtros, la paginación, los estados y la presentación legible de los cambios.

### Modified Capabilities

Ninguna. `auditoria` define la escritura y la inmutabilidad del registro, que no cambian. La ruta `/app/auditoria` ya está en el app-shell.

## Impact

- **Código**:
  - `backend/.../auditoria/**`: consulta, repositorio de lectura y controlador.
  - `frontend/src/features/auditoria/**`, `frontend/src/pages/auditoria.ts` y la tarjeta en `features/agenda/detalle.ts` (de C-13).
  - Sin migraciones: los índices `ix_evento_auditoria_consultorio_ocurrido` y `ix_evento_auditoria_consultorio_entidad` de C-03 cubren las consultas (design D4).
- **API**: 2 endpoints privados nuevos y `api-types.ts` regenerado.
- **Dependencias**: ninguna nueva.
- **Coordinación**:
  - **C-12 (Fran)**: el historial necesita saber si el turno existe en el consultorio, para responder 404. Se usa el mismo puerto de lectura que `GET /api/v1/turnos/{id}`.
  - **C-13 (Nacho)**: la tarjeta "Historial" se suma a su página de detalle.
  - **C-15 y C-19**: sus eventos (`actor_tipo = PACIENTE`, `EXPORTACION_GENERADA`) tienen que verse bien en el registro (design D6).
- **Preguntas abiertas**: no toca ninguna Alta.
- **Changes desbloqueados**: C-23 (junto con el resto de la fase 5).
