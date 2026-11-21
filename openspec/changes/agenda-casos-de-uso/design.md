# Design

## Context

- Ver `proposal.md` §Why. Governance **ALTO**: se propone y se espera la revisión de Maxi antes de escribir código.
- **Base disponible**:
  - C-02: `TenantContext`, `ConsultorioId`, `GeneradorDeIds`, `Clock`, `ErrorDeNegocio` con propiedades (C-08 D6), `PublicadorDeAuditoria` y `CambioAuditado`.
  - C-03: persiste los eventos en `BEFORE_COMMIT`. Si la inserción falla, el cambio se revierte (RN-AD-03).
  - C-04: `SesionActual` (`usuarioId`, `ip`) desde el access token (C-08 D3).
  - C-08 (#15, mergeado):
    - `ConsultorioRepositorio.buscarConfiguracion`, con la zona horaria;
    - `PrestacionRepositorio.buscar`, con la duración y si está activa;
    - `DisponibilidadRepositorio.consultar` y `bloquear`;
    - `ReemplazarDisponibilidad`, que ya toma el `FOR UPDATE` sobre `consultorio`.
- **Lo que llega con C-10 (#18)**:
  - Las tablas `paciente`, `turno` y `bloqueo` (`V6`), con `version`, `ex_turno_sin_solapamiento` y los permisos.
  - `agenda.domain`: `Turno`, `Paciente`, `Dni`, `Bloqueo`, `Intervalo`, `Duracion`, `DisponibilidadSemanal`, `ValidadorDeSolapamiento`, `ValidadorDeBloqueo` y `CalculadoraDeHuecos`.
- **Quién lo consume**:
  - C-12, la API del odontólogo.
  - C-15, la reserva pública. Su D2 lista lo que espera de este change y este design lo cubre.

## Goals / Non-Goals

**Goals:**
- Que toda escritura de turnos, bloqueos y pacientes pase por un caso de uso. Cada caso de uso valida contra la agenda del consultorio, toma el lock de agenda, audita en la misma transacción y detecta las ediciones concurrentes.
- Que C-12 y C-15 compartan las mismas operaciones y solo decidan el actor y el contexto: el odontólogo con sesión, o el paciente por el link.

**Non-Goals:**
- Endpoints HTTP, DTOs y OpenAPI (C-12).
- El token de gestión, los huecos públicos, el límite de turnos por DNI y la anticipación del paciente (C-15).
- La marca `novedad_paciente_at` (la escribe C-15 y la limpia C-12).
- El bloqueo por plan `VENCIDO` (C-20).
- Las lecturas de la agenda para la vista (C-12): este change solo lee lo que necesita para validar.

## Decisions

### D1 — Módulos y capas
- `agenda.application`:
  - los casos de uso;
  - `LockDeAgenda`;
  - los puertos `TurnoRepositorio`, `BloqueoRepositorio` y `PacienteRepositorio`;
  - `ConsultaDeDisponibilidad`, el puerto que arma la `DisponibilidadSemanal` del consultorio.
- `agenda.infrastructure`: los adaptadores JDBC y la implementación del lock.
- **Datos de C-08**: `agenda.application` los pide por los puertos de `consultorio.application`, porque los módulos hablan por `application` (08). `agenda.domain` sigue sin depender de `consultorio`, por la regla ArchUnit de C-10.
- *Alternativa*: leer `disponibilidad` y `tipo_prestacion` con SQL propio desde `agenda.infrastructure` → duplica el mapeo de C-08 y ata dos módulos a la misma tabla.

### D2 — Persistencia con JDBC, como C-04 y C-08
- C-10 D1 decía "C-11 agrega las entidades JPA". Se cambia a `JdbcClient`, porque C-08 ya mostró que el módulo `consultorio` funciona así y mezclar dos estilos en el backend no aporta.
- **Optimistic locking (RN-GL-02)**: el `UPDATE … SET …, version = version + 1 WHERE consultorio_id = :c AND id = :id AND version = :esperada`. Si no actualiza ninguna fila, lanza `OptimisticLockingFailureException`, y el manejador de C-02 ya la traduce a 409 `CONFLICTO_CONCURRENCIA`.
- `Turno.restaurar` (C-10) reconstruye el turno validando los mismos invariantes que la base.
- *Alternativa*: entidades JPA con `@Version` → hace falta un mapeo paralelo al dominio puro de C-10, que no tiene anotaciones (`dominioSinFramework`).

### D3 — `LockDeAgenda`: una sola cola por consultorio
- `LockDeAgenda.tomar(ConsultorioId)` ejecuta `SELECT 1 FROM consultorio WHERE id = :id FOR UPDATE`. Es **la misma fila y el mismo modo** que usa `ReemplazarDisponibilidad` de C-08 (`DisponibilidadRepositorio.bloquear`).
- **Por qué importa**: con dos mecanismos distintos, una reserva podría validar un hueco contra la grilla vieja mientras alguien la reemplaza (revisión de Maxi en #18, y C-15 D2).
- **Orden fijo dentro de la transacción**:
  1. tomar el lock;
  2. leer el turno o los turnos y bloqueos del rango;
  3. validar;
  4. escribir;
  5. publicar el evento de auditoría.
- **Lo toman todos los casos de uso que escriben**, también `EditarNota` y `EliminarBloqueo`. El costo es bajo, porque hay un sillón y poco volumen, y la regla queda simple: toda escritura de agenda pasa por la cola.
- **Sin deadlocks**: cada transacción toma una sola fila, siempre la del consultorio y siempre primero. `EditarConfiguracion` de C-08 hace `UPDATE` sobre esa fila, así que también espera en la cola.
- C-15 llama a `LockDeAgenda.tomar` antes de su propio paso 5 (su D6) y después usa estos casos de uso en la misma transacción. Como el lock ya está tomado en esa transacción, repetirlo no espera.
- *Alternativa*: `pg_advisory_xact_lock(consultorio_id)` → es una segunda cola que no ve la de la grilla. Descartada.

### D4 — Actor explícito
- Cada caso de uso recibe un `Autor(Actor actor, Optional<String> ip)`, con el `Actor` de C-02.
  - **C-12** arma el autor con `ODONTOLOGO` y el usuario de `SesionActual`.
  - **C-15** arma el autor con `PACIENTE` y el `paciente_id`.
- **Reglas que dependen del actor**:
  - El sobreturno, `MANUAL` y la nota interna son solo del odontólogo. Con un paciente → 409 `ACCION_NO_PERMITIDA`, como una red de seguridad: C-15 nunca los pide.
  - `CrearTurno` con un paciente tiene `origen = LINK`; con el odontólogo, `MANUAL`.
  - Cancelar guarda `cancelado_por` según el actor.
  - La anticipación mínima del paciente no la valida C-11: es una regla de la reserva pública y la valida C-15 (su D8). El odontólogo no tiene anticipación (RN-AG-11).
- *Alternativa*: que C-11 lea siempre `SesionActual` → no sirve en las rutas públicas, que no tienen sesión de odontólogo.

### D5 — Validación contra la agenda
- **`CrearTurno`, `ReprogramarTurno` y `AjustarDuracion`**, en este orden:
  1. lock;
  2. prestación del consultorio: si no existe, 404; si está inactiva, 409 `PRESTACION_INACTIVA`, solo al crear (RN-AG-10: los turnos existentes siguen vigentes);
  3. turnos activos y bloqueos que intersectan el intervalo nuevo (`tstzrange && tstzrange`, con los índices de C-10);
  4. la `DisponibilidadSemanal` del consultorio;
  5. `ValidadorDeSolapamiento.validar` (C-10 D4), con el mismo orden de errores.
- **La duración del turno nuevo** es la de la prestación (RN-AG-02). El odontólogo puede pedir otra (`duracionMin`) al crear; el paciente no.
- **`ex_turno_sin_solapamiento`** queda como red de seguridad. El manejador la traduce a 409 `TURNO_SOLAPADO` sin `turnosEnConflicto`, porque una carrera no dice con quién chocó.
- **Paciente y prestación de otro consultorio** → 404 `RECURSO_NO_ENCONTRADO`, antes de llegar a la FK compuesta.

### D6 — Pacientes: alta idempotente por DNI
- `RegistrarPaciente.registrar(datos, origen, consentimiento, autor)`:
  1. `INSERT … ON CONFLICT (consultorio_id, dni) DO NOTHING RETURNING id`;
  2. si no insertó, `SELECT` del existente.
- **Si ya existía**:
  - no se sobrescribe ningún dato (RN-RP-03, DD-24);
  - si `datosDifierenDe(nombre, apellido)` y no estaba marcado, se marca `requiere_revision` y se audita `PACIENTE_MARCADO_PARA_REVISION`;
  - el resultado dice `reutilizado = true`.
- **Si es nuevo**: `PACIENTE_CREADO`.
- **El mismo flujo para los dos orígenes**: el alta rápida del odontólogo con un DNI existente reutiliza el paciente y la UI de C-14 lo muestra. **Para la revisión de Maxi**: la alternativa es devolver 409 `PACIENTE_EXISTENTE` en el alta manual, para que el odontólogo elija.
- **Comparación de nombres, para alinear con C-15**: C-15 D7 normaliza con NFD sin diacríticos ("José Pérez" = "jose perez"), y `Paciente.datosDifierenDe` de C-10 compara solo mayúsculas y espacios. Se propone llevar la normalización NFD a `datosDifierenDe` en C-10, que sigue en revisión (#18), para que haya **una sola** regla.

### D7 — Comandos y concurrencia
- Los comandos sobre un turno existente llevan la `versionEsperada` que leyó el cliente: estado, reprogramar, duración y nota. Devuelven el turno con su versión nueva.
- **Un turno inexistente o de otro consultorio** → 404 (RN-TE-04).
- **Un turno terminal**: `TRANSICION_INVALIDA`, desde la máquina de estados de C-10.

### D8 — Auditoría (RN-AD-01, RN-AD-04, RN-AD-05)
| Acción | Entidad | Valores |
|--------|---------|---------|
| `TURNO_CREADO` | `turno` | nuevo `{inicio, fin, duracion_min, tipo_prestacion_id, paciente_id, estado, origen, es_sobreturno}` |
| `TURNO_ESTADO_CAMBIADO` | `turno` | `{estado, cancelado_por}` anterior y nuevo |
| `TURNO_REPROGRAMADO` | `turno` | `{inicio, fin}` anterior y nuevo |
| `TURNO_DURACION_CAMBIADA` | `turno` | `{duracion_min, fin}` anterior y nuevo |
| `TURNO_NOTA_CAMBIADA` | `turno` | `{nota_interna}` anterior y nuevo (US-025) |
| `BLOQUEO_CREADO` | `bloqueo` | nuevo `{inicio, fin, motivo}` |
| `BLOQUEO_ELIMINADO` | `bloqueo` | anterior `{inicio, fin, motivo}` |
| `PACIENTE_CREADO` | `paciente` | nuevo `{origen}` |
| `PACIENTE_MARCADO_PARA_REVISION` | `paciente` | `{requiere_revision}` anterior y nuevo |

- **Sin datos personales del paciente**: nunca nombre, DNI, teléfono ni email. El paciente aparece solo como `paciente_id` (RN-AD-05).
- **El motivo de cancelación no va al evento**: es texto libre y puede traer datos de salud.
- **La nota sí va**, porque US-025 pide su valor anterior y nuevo. **Para la revisión de Maxi**: la exportación (C-19) y la vista de auditoría (C-18) la muestran solo al odontólogo dueño.
- **Sin cambios, sin evento**: una nota idéntica, o reprogramar al mismo inicio, devuelve el turno sin escribir, como en C-08 D4.
- Coincide con la tabla de C-15 D11. C-15 no duplica los `TURNO_*`: solo pasa el actor.

### D9 — Errores
- 400:
  - `DURACION_INVALIDA` y `HORARIO_INVALIDO`, del dominio;
  - `VALIDACION` para una nota de más de 500 caracteres o un motivo de más de 200.
- 404: `RECURSO_NO_ENCONTRADO` para un turno, bloqueo, paciente o prestación ajenos o inexistentes.
- 409:
  - `TURNO_SOLAPADO`, `HORARIO_BLOQUEADO`, `FUERA_DE_DISPONIBILIDAD` y `BLOQUEO_CON_TURNOS`, cada uno con sus propiedades de C-10;
  - `TRANSICION_INVALIDA`, `PRESTACION_INACTIVA`, `ACCION_NO_PERMITIDA` y `CONFLICTO_CONCURRENCIA`.
- **Nuevos para C-12**: `PRESTACION_INACTIVA` y `ACCION_NO_PERMITIDA`. El resto ya está en su alcance.
- **Detalle del turno que choca**: C-12 decide cómo agrega el detalle de `TURNO_SOLAPADO` (#34). Este change le pasa los ids en `turnosEnConflicto`.

## Risks / Trade-offs

- **[C-10 todavía en revisión]** → este design usa sus nombres (#18). Si Maxi pide cambios en el dominio, se ajustan acá antes de aprobar.
- **[El lock serializa todas las escrituras del consultorio]** → con un sillón y poco volumen es aceptable. Si con multi-sillón (PA-14) hiciera falta paralelizar, la fila del lock pasa a ser la del sillón y el contrato de `LockDeAgenda` no cambia.
- **[Validación contra datos leídos después del lock]** → el lock garantiza que nadie más escribe en la agenda del consultorio entre la lectura y la escritura. La exclusión de la base sigue como red.
- **[Cambio de D1 de C-10, de JPA a JDBC]** → queda visible acá para la revisión. Revertirlo agrega un mapeo, pero no cambia los contratos.
- **[La nota en la auditoría]** → la pide US-025 y queda marcada para la revisión (D8).

## Migration Plan

- Sin migración: usa `V6` de C-10. El rollback es no mergear.

## Open Questions

- D6: ¿el alta manual con un DNI existente reutiliza el paciente o responde 409 `PACIENTE_EXISTENTE`?
- D6: ¿se pasa la normalización NFD de C-15 a `Paciente.datosDifierenDe` en C-10?
- D8: ¿la nota interna va completa en `TURNO_NOTA_CAMBIADA`, como pide US-025?

## UI Mockups

- Decision: no (no UI), 2026-11-20. Change de backend sin pantallas. Los diálogos son de C-14 (Nacho), que ya tiene su propuesta y sus mockups.
