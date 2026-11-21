# Proposal

> Change **C-14** de [CHANGES.md](../../../CHANGES.md) · Governance **MEDIO** · Depende de `C-13` (`frontend-agenda`) · Responsable: Nacho · Aprobación: Maxi.
>
> Trabajo anticipado (paso 3 del plan del equipo): esta propuesta y los mockups se preparan antes de que C-13 esté `[x]`. El código de producción no se escribe hasta que C-13 esté mergeado y Maxi apruebe los mockups.

## Why

C-13 deja la agenda para leer. Sin C-14, el odontólogo no puede cargar el turno del paciente que llamó, agregar un sobreturno por una urgencia, confirmar, cancelar, mover un turno, ajustar cuánto duró la atención, anotar algo ni bloquear sus vacaciones (US-012, US-021 a US-025). Son las acciones de todos los días, y la mayoría tienen reglas que el backend rechaza con 409 (solapamiento, bloqueo, fuera de horario, transición inválida). La pantalla tiene que explicar cada rechazo y ofrecer la salida, como crear el turno como sobreturno o reprogramar los turnos que impiden un bloqueo.

## What Changes

- **Nuevo turno y sobreturno** (FL-03), en un diálogo que en mobile ocupa la pantalla. Se abre desde "Nuevo turno" o desde un hueco "Libre" de C-13, con el horario ya cargado. Pide:
  - paciente: búsqueda por nombre, DNI o teléfono, o alta rápida con nombre, apellido, DNI, celular y email opcional (DD-24, DD-32);
  - prestación, día y hora de inicio en pasos de 5 minutos;
  - la casilla "Sobreturno".
  El fin se muestra calculado (RN-AG-12) y no se escribe.
- **Cambios de estado** desde el detalle: Confirmar (directo, con aviso), Cancelar (diálogo con motivo opcional) y Marcar ausente (solo si el turno ya empezó, con confirmación). Solo se ofrecen las transiciones válidas (RN-AG-06).
- **Reprogramar**: diálogo con día y hora y los huecos libres de ese día para elegir con un toque. Conserva la duración y muestra el fin nuevo (RN-AG-11, RN-AG-12).
- **Ajustar duración**: selector de −5 / +5 minutos con atajos que muestra el fin nuevo (US-024).
- **Nota interna**: editor con contador de 500 caracteres que avisa que el paciente no la ve (US-025).
- **Bloqueos** (`/app/bloqueos`):
  - listado de los próximos bloqueos;
  - crear un bloqueo por rango de horas o días completos, con motivo;
  - eliminar un bloqueo con confirmación.
  Ante 409 `BLOQUEO_CON_TURNOS`, se muestra la lista de turnos en conflicto con acceso a cada uno (RN-AG-05).
- **Aviso de turnos fuera de horario** (decisión de Maxi en #15, alcance en #34): al guardar los horarios en Configuración (pantalla de C-09), si quedan turnos futuros fuera de la grilla nueva, se listan con un link a cada uno. No bloquea el guardado. Va en C-14 porque necesita la API de turnos de C-12.
- **Errores de C-12 traducidos a mensajes y salidas**:
  - `TURNO_SOLAPADO` (con el turno que choca y la opción "Crear como sobreturno");
  - `FUERA_DE_DISPONIBILIDAD` (con la opción "Crear como sobreturno");
  - `HORARIO_BLOQUEADO`, `TRANSICION_INVALIDA` y `CONFLICTO_CONCURRENCIA`;
  - 400 por duración o nota, y 403 `PLAN_VENCIDO`.
- **Mockups** de todos los formularios y diálogos, revisados y aprobados por Maxi antes de implementar.
- **Tests Vitest**: validaciones (múltiplos de 5, fin derivado, rango del bloqueo, DNI, nota), mapeo de errores a mensajes y acciones, transiciones ofrecidas por estado y huecos libres para reprogramar.

**Fuera de alcance**:
- Revisión de pacientes con datos distintos (C-17).
- Editar los datos de un paciente.
- Arrastrar turnos en la grilla.
- Bloqueos recurrentes.

## Capabilities

### New Capabilities

- `app-agenda/gestion-turnos`: alta de turnos y sobreturnos, cambios de estado, reprogramación, ajuste de duración y nota interna desde la app del odontólogo.
- `app-agenda/bloqueos`: listado, alta y eliminación de bloqueos de horario desde la app del odontólogo.

### Modified Capabilities

<!-- Ninguna. Usa las capabilities de C-13 (vista-agenda y detalle-turno), todavía en propuesta, sin cambiar sus requisitos. -->

## Impact

- **Código**:
  - `frontend/src/features/turnos/` (formularios, validaciones, mapeo de errores y cliente de la API);
  - `frontend/src/features/bloqueos/`;
  - el aviso de turnos fuera de horario en la pestaña Horarios de C-09 (`features/configuracion/`);
  - `pages/bloqueos.ts`, que reemplaza el placeholder de C-06.
- **API**: consume los endpoints de C-12 (`POST /turnos`, `PATCH /turnos/{id}/estado`, `POST /turnos/{id}/reprogramar`, `PATCH /turnos/{id}/duracion`, `PATCH /turnos/{id}/nota`, `POST/DELETE /bloqueos`) y las prestaciones activas de C-08. El aviso usa `GET /api/v1/turnos/fuera-de-disponibilidad` (C-12, #34).
- **Coordinación con C-12 (Fran)**: Maxi sumó al alcance de C-12 (#34) la búsqueda de pacientes (`GET /api/v1/pacientes?q=`), el listado de bloqueos futuros (`GET /api/v1/bloqueos?desde=`), el turno con el que choca en `TURNO_SOLAPADO` y `GET /api/v1/turnos/fuera-de-disponibilidad`.
- **Dependencias**: ninguna nueva.
- **Preguntas abiertas**: no toca ninguna Alta. Respeta DD-24 (DNI), DD-32 (teléfono obligatorio), DD-36 (un sillón) y DD-37: el paciente sin DNI argentino pide el turno por teléfono y el odontólogo lo carga a mano con un DNI. El texto de ayuda para ese caso queda para revisar (design D4).
