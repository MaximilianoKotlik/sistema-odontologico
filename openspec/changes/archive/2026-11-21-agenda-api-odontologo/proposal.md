# Proposal

> Change **C-12** de [CHANGES.md](../../../CHANGES.md) · Governance **MEDIO** · Depende de `C-11` (`agenda-casos-de-uso`, propuesta en #38) · Responsable: Fran · Aprobación: Maxi.
>
> Propuesta anticipada. El código no se escribe hasta que se cumplan dos condiciones:
> - C-11 está `[x]`.
> - Maxi aprobó esta propuesta. Es MEDIO y sin pantallas, pero define el contrato que ya usan C-13 y C-14, así que se acuerda antes.

## Why

La agenda del odontólogo no tiene API. C-13 (agenda) y C-14 (turnos y bloqueos) ya tienen sus propuestas y mockups aprobados, y trabajan contra dobles de `fetch` con un contrato "a acordar con C-12".

C-12 publica ese contrato sobre los casos de uso de C-11, más las cinco piezas que Maxi sumó al alcance en #34:
- la marca de novedad;
- la búsqueda de pacientes;
- el listado de bloqueos;
- el turno con el que choca `TURNO_SOLAPADO`;
- los turnos que quedaron fuera de la disponibilidad.

## What Changes

- **Agenda y detalle**:
  - `GET /api/v1/agenda?desde&hasta` (instantes UTC) devuelve una vista liviana para la caché offline de C-06: sin DNI, teléfono, email ni texto de la nota.
    - De los turnos trae `cambioPaciente` (`RESERVO`, `REPROGRAMO`, `CANCELO` o `null`) y la `version` para editar.
    - Trae también los bloqueos del rango.
  - `GET /api/v1/turnos/{id}`: el detalle completo de C-13, con el paciente, la nota, la cancelación y el horario anterior si el paciente reprogramó.
- **Escritura de turnos**, sobre C-11, con el odontólogo como actor:
  - `POST /api/v1/turnos`, con un paciente existente o un alta rápida;
  - `PATCH /api/v1/turnos/{id}/estado`, `POST /api/v1/turnos/{id}/reprogramar`, `PATCH /api/v1/turnos/{id}/duracion` y `PATCH /api/v1/turnos/{id}/nota`, todos con `version`.
- **Marca de novedad (DD-30)**: `POST /api/v1/turnos/{id}/visto` limpia `turno.novedad_paciente_at`. Es idempotente.
  - La columna la crea la migración del primero que se mergee entre C-12 y C-15 (decisión de Maxi en #30).
  - Este PR propone que no genere un evento de auditoría (D5).
- **Bloqueos**: `POST /api/v1/bloqueos`, `DELETE /api/v1/bloqueos/{id}` (audita `BLOQUEO_ELIMINADO`) y `GET /api/v1/bloqueos?desde=`, que lista los bloqueos que siguen vigentes.
- **Búsqueda de pacientes**: `GET /api/v1/pacientes?q=` por nombre, apellido, DNI o teléfono, para el buscador del turno manual de C-14.
- **Turnos fuera de la disponibilidad**: `GET /api/v1/turnos/fuera-de-disponibilidad` devuelve los turnos futuros activos, no sobreturnos, que quedaron fuera de la grilla vigente (RN-AG-03). Lo usa el aviso de C-14 después de reemplazar la disponibilidad (decisión de Maxi en #15).
- **Errores**:
  - el 409 `TURNO_SOLAPADO` incluye el turno con el que choca (su id, inicio y fin);
  - el resto de los códigos llegan de C-10 y C-11, con su estado HTTP.
- **Contrato**: OpenAPI actualizado y `api-types.ts` regenerado.

Fuera de alcance:
- el 403 `PLAN_VENCIDO` (C-20);
- el email al odontólogo de DD-30;
- la API pública de reserva (C-15).

## Capabilities

### New Capabilities
- `agenda-del-odontologo`: la consulta de la agenda, el detalle del turno, la marca de novedad y los turnos que quedaron fuera de la disponibilidad.
- `api-de-turnos-y-bloqueos`: los endpoints que crean y modifican turnos y bloqueos, con sus errores.
- `busqueda-de-pacientes`: buscar pacientes del consultorio por nombre, apellido, DNI o teléfono.

### Modified Capabilities
- Ninguna. Las capabilities de C-10 y C-11 todavía no están archivadas; este change las usa sin cambiar sus requisitos.

## Impact

- **Código**:
  - `backend/.../agenda/web/**`: controladores y DTOs;
  - `agenda.application`: consultas de lectura, como `ConsultarAgenda`, `DetalleDeTurno`, `BuscarPacientes`, `ListarBloqueos` y `TurnosFueraDeDisponibilidad`, más el caso de uso `MarcarVisto`;
  - `agenda.infrastructure`: las consultas JDBC.
- **Base**: si C-15 todavía no la creó, la migración `V{n}__turno_novedad_paciente.sql` agrega `turno.novedad_paciente_at TIMESTAMPTZ NULL`.
- **API**: 13 operaciones nuevas en `/api/v1`, todas privadas con JWT. Cambia `frontend/src/shared/api-types.ts`.
- **Logs**: el parámetro `q` de la búsqueda puede ser un DNI o un teléfono, así que se enmascara en los logs de acceso (RN-GL-01, D7).
- **Preguntas abiertas**: no toca ninguna Alta. Las decisiones para la revisión están en design.md (D3, D5 y D6).
- **Changes desbloqueados**: el backend real para C-13 y C-14, y la limpieza de la marca que escribe C-15.
