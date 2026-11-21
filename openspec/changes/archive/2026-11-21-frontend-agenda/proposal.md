# Proposal

> Change **C-13** de [CHANGES.md](../../../CHANGES.md) · Governance **MEDIO** · Depende de `C-07` (`frontend-auth`) y `C-12` (`agenda-api-odontologo`) · Responsable: Nacho · Aprobación: Maxi.
>
> Trabajo anticipado (paso 3 del plan del equipo): esta propuesta y los mockups se preparan antes de que C-07 y C-12 estén `[x]`. El código de producción no se escribe hasta que los dos estén mergeados y Maxi apruebe los mockups.

## Why

La agenda es la pantalla que el odontólogo abre todos los días, muchas veces entre paciente y paciente y desde el celular (US-020). Hoy `/app/agenda` es un placeholder de C-06. C-13 la reemplaza por una agenda diaria y semanal legible a 360 px, que distingue a simple vista estados, sobreturnos, bloqueos y cancelados, y que avisa qué cambiaron los pacientes desde el link (DD-30). Es la base sobre la que C-14 agrega las acciones (crear, reprogramar, cancelar, ajustar duración, nota y bloqueos). Está en el camino crítico.

## What Changes

- **Vista diaria** (por defecto en mobile): línea de tiempo del día en orden cronológico, con turnos, sobreturnos, bloqueos, huecos libres dentro del horario de atención, tramos fuera de horario y la marca de "ahora". Una tira con los días de la semana permite cambiar de día.
- **Vista semanal** (por defecto desde 768 px): grilla proporcional de lunes a domingo con el horario de atención sombreado. En mobile, la semana se muestra como una lista agrupada por día.
- **Tarjetas de turno** con color + ícono + texto para cada estado (Reservado, Confirmado, Cancelado, Ausente), sobreturno con borde punteado, bloqueo rayado y cancelados atenuados. Un interruptor "Mostrar cancelados" los oculta o los muestra.
- **Marca de "nuevo"** (DD-30) en los turnos que el paciente reservó, reprogramó o canceló desde el link, con un aviso que resume cuántos hay.
- **Detalle de turno** (`/app/agenda/turnos/{id}`): paciente (nombre, DNI, teléfono para llamar, email), prestación, duración, horario, estado, sobreturno, origen, cancelación y nota interna. Muestra los botones de acción, cuyos formularios y diálogos son de C-14.
- **Estados de pantalla**: cargando, día sin turnos, día sin horario de atención, error recuperable, sin conexión con la última agenda guardada (solo lectura) y sin conexión sin agenda guardada, sobre los componentes de C-06.
- **Fechas y horas** en la zona horaria del consultorio con `fechas.ts` de C-06 (RN-TE-05).
- **Mockups** de la vista diaria, la semanal y el detalle, revisados y aprobados por Maxi antes de implementar.
- **Tests Vitest**: render de tarjetas y vistas por estado, distribución de turnos superpuestos, huecos libres y fuera de horario, cambio de día y semana, fechas con TZ distinta de la del navegador, y modo sin conexión.

**Fuera de alcance**:
- Crear, cambiar de estado, reprogramar, ajustar duración, editar la nota, crear y borrar bloqueos (C-14).
- Banner de plan o prueba (C-20).
- Revisión de pacientes con datos distintos (C-17).
- Auditoría (C-18).

## Capabilities

### New Capabilities

- `app-agenda/vista-agenda`: vistas diaria y semanal de la agenda del odontólogo, sus marcas visuales, la navegación entre días y semanas y los estados de pantalla.
- `app-agenda/detalle-turno`: pantalla de detalle de un turno con los datos del paciente y del turno.

### Modified Capabilities

<!-- Ninguna. `pwa-offline` (C-06) ya define la caché de la agenda; C-13 la consume sin cambiar sus requisitos. -->

## Impact

- **Código**:
  - `frontend/src/features/agenda/`: cliente de la API, modelo de la vista, distribución de superpuestos y componentes de tarjeta, línea de tiempo y grilla;
  - `frontend/src/pages/agenda.ts` (reemplaza el placeholder de C-06) y `pages/turno.ts`;
  - la ruta nueva `/app/agenda/turnos/{id}`.
- **API**: consume `GET /api/v1/agenda?desde&hasta` (C-12) y `GET /api/v1/disponibilidad` (C-08). No agrega endpoints.
- **Coordinación con C-12 (Fran)**: el design (D2) fija lo que la vista necesita de la respuesta de la agenda. Hay dos puntos que el scope de C-12 no nombra y conviene acordar antes de implementarlo:
  - los datos del detalle del turno (`GET /api/v1/turnos/{id}`, o que la agenda ya los traiga);
  - cómo se marca un "nuevo" como visto.
- **Dependencias**: ninguna nueva.
- **Preguntas abiertas**: no toca ninguna Alta. Respeta DD-24 (DNI), DD-30 (marca de "nuevo") y DD-36 (un solo sillón).
