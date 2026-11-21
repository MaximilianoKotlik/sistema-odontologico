# Proposal

> Change **C-17** de [CHANGES.md](../../../CHANGES.md) · Governance **MEDIO** · Depende de `C-09` (`frontend-configuracion`) y `C-11` (`agenda-casos-de-uso`) · Responsable: Octi · Aprobación: Maxi.
>
> Es trabajo anticipado. Los mockups ya están aprobados (#36, versión `dcb700b`) con la DD-38. C-10 (#18) y C-11 (#38) tienen propuesta en revisión. El código se escribe recién cuando C-09 y C-11 estén `[x]`.

## Why

La deduplicación por DNI (RN-RP-03, DD-24) reutiliza al paciente sin tocar su ficha y lo marca con `requiere_revision` cuando alguien reserva con ese DNI y otro nombre. Hoy esa marca no tiene salida: el odontólogo no se entera, no ve qué difiere y no puede confirmar ni corregir. Sin C-17, los errores de tipeo y los familiares que reservan con el DNI equivocado se acumulan en silencio (US-070).

## What Changes

- **API de pacientes** (backend, módulo `agenda`), siempre filtrada por el consultorio de la sesión:
  - `GET /api/v1/pacientes?requiereRevision=true|false&page&size`: lista paginada, ordenada por apellido y nombre, con el total. Cada paciente trae DNI, teléfono, email, `requiereRevision` y, si está marcado, lo informado en la reserva (DD-38).
  - `GET /api/v1/pacientes/{id}`: el detalle para la pantalla de revisión, con lo informado y el turno de origen. **Suma un endpoint al alcance de CHANGES.md**: hace falta para abrir la revisión desde un link o al recargar.
  - `PATCH /api/v1/pacientes/{id}`: corrige nombre, apellido y email. DNI y teléfono no se editan en v1.
  - `POST /api/v1/pacientes/{id}/confirmar-revision`: mantiene los datos guardados.
  - El `PATCH` y la confirmación ponen `requiere_revision = false`, borran lo informado (DD-38) y auditan `PACIENTE_REVISADO` solo con los campos que cambian (RN-AD-05), en la misma transacción.
  - Un paciente de otro consultorio responde 404 (RN-TE-04). Los datos de pacientes no van a los logs (RN-GL-01).
- **Pantallas** (frontend, feature `pacientes/`):
  - `/app/pacientes`: lista con el filtro "Requieren revisión" (con contador) o "Todos", paginada con "Mostrar más".
  - `/app/pacientes/{id}`: comparación de lo guardado con lo escrito en la reserva, mostrando solo lo que difiere, y tres salidas sin confirmación: mantener lo guardado, usar lo de la reserva o corregir a mano.
  - Aviso compacto en la agenda ("3 pacientes para revisar"), pegado al aviso de cambios de DD-30.
  - Sin caché offline: la lista y la revisión necesitan conexión.
- **Shell**: sección nueva "Pacientes", en la barra lateral (desktop) y dentro de "Más" (mobile), con el contador de pacientes para revisar.
- **Tests**: US-070, auditoría solo con lo modificado, borrado de lo informado al revisar, aislamiento por consultorio en cada endpoint, y Vitest del filtro, la comparación y el formulario.

**Fuera de alcance**:
- La deduplicación y la marca de revisión (C-11 y C-15), y las columnas de DD-38 en la migración (C-10).
- Buscar pacientes, editar DNI o teléfono, borrar pacientes.
- Bloquear las escrituras con el plan `VENCIDO`: lo define C-20; los endpoints de escritura de C-17 quedan dentro de esa regla (lo verifica C-23).

## Capabilities

### New Capabilities

- `revision-pacientes`: consulta de los pacientes del consultorio con el filtro de revisión pendiente, y las dos formas de resolverla (confirmar o corregir), con la auditoría y el borrado de lo informado.
- `pantalla-pacientes`: la lista de pacientes, la pantalla de revisión y el aviso de la agenda que permiten al odontólogo enterarse y resolver las revisiones.

### Modified Capabilities

- `app-shell`: la app resuelve las rutas `/app/pacientes` y `/app/pacientes/{id}`, y la navegación suma "Pacientes" (barra lateral y "Más") con el contador de revisiones pendientes.

## Impact

- **Código**: `backend/.../agenda/**` (consulta y casos de uso de revisión, controlador `web`), `frontend/src/features/pacientes/**`, las páginas en `frontend/src/pages/`, el shell (`frontend/src/app/shell.ts`, `app.ts`) y el aviso de la agenda de C-13. `api-types.ts` regenerado.
- **API**: 4 endpoints privados nuevos. Sin migración propia.
- **Dependencias**: ninguna nueva.
- **Coordinación**:
  - **C-10 (Fran)**: las columnas `nombre_informado`, `apellido_informado`, `email_informado`, `informado_at` e `informado_turno_id` (DD-38, `04_modelo_de_datos.md` §Paciente) van en su migración `V6`. Maxi se lo pidió en #18.
  - **C-11 (Fran) y C-15 (Mati)**: al marcar para revisión tienen que guardar lo informado (queda la última reserva). Además, hay dos diferencias entre sus propuestas que conviene unificar antes de implementar: el nombre del evento (C-11 propone `PACIENTE_MARCADO_PARA_REVISION` y C-15 `PACIENTE_REQUIERE_REVISION`) y la regla para decidir si el nombre difiere (C-15 ignora las tildes y C-11 no). C-17 usa la misma regla para mostrar qué difiere (design D7).
  - **C-06, C-07 y C-13 (Nacho)**: C-17 suma "Pacientes" a la navegación del shell y el aviso a la agenda. C-07 también modifica la navegación ("Cambiar contraseña" en "Más"): el que archive segundo integra el delta.
  - **C-20**: los endpoints de escritura de C-17 respetan el bloqueo del plan `VENCIDO`.
- **Preguntas abiertas**: no toca ninguna Alta.
