# Proposal

> Change **C-11** de [CHANGES.md](../../../CHANGES.md) · Governance **ALTO** · Depende de `C-10` (`agenda-esquema-dominio`, #18) · Responsable: Fran · Aprobación: Maxi.
>
> Propuesta anticipada: C-10 está listo para revisar en #18, pero todavía no está `[x]`. El código no se escribe hasta que se cumplan dos condiciones:
> - C-10 está `[x]`.
> - Maxi aprobó esta propuesta (ALTO: se propone y se espera la revisión).

## Why

C-10 deja el esquema y las reglas puras de la agenda, pero nada las usa todavía: no hay operaciones que creen, muevan o cancelen turnos y bloqueos, ni que den de alta pacientes. C-11 es la única puerta de escritura de la agenda. La consumen la API del odontólogo (C-12) y la reserva pública (C-15).

Por eso concentra lo que no puede quedar repartido:
- validar contra los turnos, bloqueos y la grilla del consultorio;
- serializar las escrituras por consultorio;
- auditar cada cambio en la misma transacción;
- detectar ediciones concurrentes.

## What Changes

- **Casos de uso de turnos** en `agenda.application`, sobre el dominio de C-10:
  - `CrearTurno`: manual, sobreturno o desde la reserva pública. Valida con `ValidadorDeSolapamiento`, copia la duración de la prestación y emite `TURNO_CREADO`.
  - `CambiarEstadoTurno`: confirmar, cancelar o marcar ausente, con `TURNO_ESTADO_CAMBIADO`.
  - `ReprogramarTurno`: mismo id y misma duración, sin anticipación mínima para el odontólogo (RN-AG-11), con `TURNO_REPROGRAMADO`.
  - `AjustarDuracion`: recalcula el fin y revalida RN-AG-01/03/04, con `TURNO_DURACION_CAMBIADA`.
  - `EditarNota`: hasta 500 caracteres, con `TURNO_NOTA_CAMBIADA`.
- **Casos de uso de bloqueos**: `CrearBloqueo` (`BLOQUEO_CON_TURNOS` con la lista de turnos, RN-AG-05, y `BLOQUEO_CREADO`) y `EliminarBloqueo` (`BLOQUEO_ELIMINADO`).
- **Pacientes**: `RegistrarPaciente` busca o crea el paciente por DNI, de forma idempotente (RN-RP-03, DD-24). Si el DNI ya existe, lo reutiliza sin sobrescribirlo y lo marca para revisión si el nombre difiere, guardando lo informado en la reserva (DD-38; queda la última) y, después de crear el turno, el turno de origen. Lo usan el alta rápida del turno manual (FL-03) y la reserva pública (C-15).
- **`LockDeAgenda`**: toda escritura de agenda toma primero `SELECT 1 FROM consultorio WHERE id = :id FOR UPDATE`. Es el mismo bloqueo que ya usa la grilla de C-08 (`ReemplazarDisponibilidad`). Así, turnos, bloqueos, reservas y cambios de grilla quedan en **una sola cola por consultorio** (pedido de Maxi en #18, y C-15 D2).
- **Optimistic locking**: los cambios sobre un turno reciben la `version` que leyó el cliente. Si no coincide, devuelven 409 `CONFLICTO_CONCURRENCIA` (RN-GL-02).
- **Actor explícito**: cada caso de uso recibe quién actúa:
  - el odontólogo, desde `SesionActual`;
  - el paciente, que pasa C-15 al reservar, reprogramar o cancelar por link.
- **Red de seguridad**: el manejador mapea `ex_turno_sin_solapamiento` a 409 `TURNO_SOLAPADO`.
- **Persistencia**: repositorios JDBC en `agenda.infrastructure`, con el mismo estilo que C-04 y C-08, y `consultorio_id` en toda consulta (regla dura 6).
- **CHANGES.md**: el alcance de C-11 decía "deduplicación de paciente por teléfono E.164". Desde DD-24 es por DNI y se corrige.

Fuera de alcance:
- los endpoints HTTP (C-12);
- el token de gestión, los límites de la reserva pública y la marca de novedad (C-15, C-12);
- el bloqueo por plan `VENCIDO` (C-20).

## Capabilities

### New Capabilities
- `gestion-de-turnos`: crear, cambiar de estado, reprogramar, ajustar la duración y anotar turnos, con validación contra la agenda del consultorio, auditoría y control de concurrencia.
- `gestion-de-bloqueos`: crear y eliminar bloqueos sin pisar turnos activos, con auditoría.
- `registro-de-pacientes`: alta idempotente por DNI, marca de revisión cuando los datos no coinciden y lo informado en la reserva (DD-38).

### Modified Capabilities
- Ninguna. Las capabilities de C-10 (`turnos`, `bloqueos`, `pacientes` y `huecos-disponibles`) todavía no están archivadas. C-11 las usa sin cambiar sus requisitos.

## Impact

- **Código**:
  - `backend/.../agenda/application/**`: los casos de uso, `LockDeAgenda`, los puertos de repositorio y el puerto de disponibilidad, que arma `DisponibilidadSemanal` desde la grilla de C-08 por `consultorio.application` (C-10 D2).
  - `backend/.../agenda/infrastructure/**`: los repositorios JDBC y la implementación del lock.
  - `ManejadorGlobalDeErrores`: suma `ex_turno_sin_solapamiento` → 409 `TURNO_SOLAPADO`.
  - Tests espejo de aplicación con Testcontainers.
- **Base**: sin migración. C-10 ya trae las tablas, la columna `version` y los permisos.
- **API**: ninguna. Los endpoints son de C-12.
- **Dependencias**: ninguna nueva.
- **Preguntas abiertas**: no toca ninguna Alta. Las tres decisiones que quedaban abiertas (D6 y D8) las tomó Maxi en #38 y están en design.md.
- **Changes desbloqueados**: C-12 y C-15, que ya cuenta con estos casos de uso (su D2).
