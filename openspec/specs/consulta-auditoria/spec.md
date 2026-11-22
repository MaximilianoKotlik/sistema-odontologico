# consulta-auditoria Specification

## Purpose

Permite que el odontólogo lea el registro de auditoría de su consultorio, por turno o completo, con filtros por rango de fechas y tipo de acción, para resolver dudas sobre quién cambió qué y cuándo (US-040, RN-AD-01, RN-AD-06).

## Requirements

### Requirement: Historial de un turno
`GET /api/v1/turnos/{id}/historial` SHALL devolver todos los eventos cuya entidad es ese turno, en orden cronológico (del más viejo al más nuevo). Cada evento trae id, instante en UTC, actor (tipo, id y nombre), acción, valor anterior, valor nuevo e IP.

#### Scenario: Turno reservado por link y reprogramado
- **GIVEN** un turno que un paciente reservó por link y que después el odontólogo reprogramó
- **WHEN** el odontólogo pide su historial
- **THEN** la respuesta es 200 con 2 eventos: primero `TURNO_CREADO` con actor `PACIENTE` y el nombre del paciente, y después `TURNO_REPROGRAMADO` con actor `ODONTOLOGO`, `inicio` y `fin` anteriores y nuevos

#### Scenario: Turno de otro consultorio
- **GIVEN** un turno del consultorio B
- **WHEN** el odontólogo del consultorio A pide su historial
- **THEN** la respuesta es 404 `RECURSO_NO_ENCONTRADO`

#### Scenario: Turno inexistente
- **WHEN** se pide el historial de un id que no es un turno
- **THEN** la respuesta es 404 `RECURSO_NO_ENCONTRADO`

### Requirement: Registro del consultorio
`GET /api/v1/auditoria` SHALL devolver los eventos del consultorio de la sesión, de todas las entidades, del más nuevo al más viejo, con los mismos campos que el historial más la entidad y su id.

#### Scenario: Registro sin filtros
- **GIVEN** un consultorio con eventos de turnos, bloqueos, configuración y accesos
- **WHEN** el odontólogo pide el registro sin filtros
- **THEN** recibe todos esos eventos, del más nuevo al más viejo

#### Scenario: Consultorio sin eventos de otro
- **GIVEN** los consultorios A y B con eventos
- **WHEN** el odontólogo de A pide el registro
- **THEN** no recibe ningún evento de B

### Requirement: Filtros combinables
El registro SHALL aceptar los filtros `turnoId`, `desde` y `hasta` (fechas locales del consultorio, inclusivas) y `accion` (repetible). Combinados, MUST devolver solo los eventos que cumplen todos. Un `turnoId` de otro consultorio MUST devolver una lista vacía, sin revelar si existe.

#### Scenario: Filtro por turno
- **GIVEN** eventos de los turnos T1 y T2
- **WHEN** se filtra por `turnoId` T1
- **THEN** solo aparecen los eventos de T1

#### Scenario: Filtro por rango de fechas en la zona del consultorio
- **GIVEN** un consultorio en `America/Argentina/Buenos_Aires` y un evento ocurrido el 10/11 a las 23:30 hora local (02:30 UTC del 11/11)
- **WHEN** se filtra con `desde = hasta = 2026-11-10`
- **THEN** el evento aparece

#### Scenario: Filtro por acción
- **GIVEN** eventos `TURNO_CREADO`, `TURNO_REPROGRAMADO` y `LOGIN_FALLIDO`
- **WHEN** se filtra con `accion=TURNO_REPROGRAMADO&accion=LOGIN_FALLIDO`
- **THEN** solo aparecen esos dos tipos

#### Scenario: Filtros combinados sin coincidencias
- **WHEN** se combina un turno, un rango y una acción que no coinciden en ningún evento
- **THEN** la respuesta es 200 con la lista vacía

#### Scenario: Turno de otro consultorio en el filtro
- **WHEN** el odontólogo de A filtra por el `turnoId` de un turno de B
- **THEN** la respuesta es 200 con la lista vacía

### Requirement: Validación de los filtros
Un rango con `hasta` anterior a `desde`, una fecha mal formada, un `turnoId` que no es UUID o una `accion` que no tiene el formato `MAYUSCULAS_CON_GUIONES_BAJOS` MUST responder 400 `VALIDACION` con el campo en error.

#### Scenario: Rango invertido
- **WHEN** se filtra con `desde=2026-11-20&hasta=2026-11-10`
- **THEN** la respuesta es 400 `VALIDACION` con un error para `hasta`

### Requirement: Paginación por cursor
El registro SHALL devolver páginas de `tamano` eventos (por defecto 50, máximo 100) y un `siguienteCursor` opaco mientras queden eventos. Pedir con ese cursor MUST devolver los eventos siguientes sin repetir ni saltear ninguno, aunque se hayan registrado eventos nuevos entre una página y otra.

#### Scenario: Dos páginas
- **GIVEN** 120 eventos en el consultorio
- **WHEN** se pide la primera página con `tamano=100` y después la siguiente con el cursor
- **THEN** la primera trae 100 eventos y un cursor, la segunda trae los 20 restantes sin cursor, y ningún evento se repite

#### Scenario: Evento nuevo entre páginas
- **GIVEN** la primera página ya pedida
- **WHEN** se registra un evento nuevo y se pide la segunda página con el cursor
- **THEN** la segunda página no incluye el evento nuevo ni repite eventos de la primera

#### Scenario: Cursor inválido o de otro consultorio
- **WHEN** se pide una página con un cursor alterado o emitido para otro consultorio
- **THEN** la respuesta es 400 `VALIDACION` con un error para `cursor`

### Requirement: Actor y contexto legibles
Cada evento SHALL traer el nombre del actor: nombre y apellido del odontólogo si es `ODONTOLOGO`, nombre y apellido del paciente si es `PACIENTE`, y sin nombre si es `SISTEMA`. Los eventos de turnos SHALL traer además el contexto del turno: inicio y nombre del paciente.

#### Scenario: Evento de un paciente
- **GIVEN** un turno que reservó por link el paciente `Paciente Uno Prueba`
- **WHEN** se lee su evento `TURNO_CREADO`
- **THEN** el actor es `PACIENTE` con nombre `Paciente Uno Prueba`, y el contexto trae el inicio del turno y el paciente

#### Scenario: Intento de inicio de sesión fallido
- **GIVEN** un `LOGIN_FALLIDO` registrado con actor `ODONTOLOGO` sin `actor_id`
- **WHEN** se lee el evento
- **THEN** el actor es `ODONTOLOGO` sin id ni nombre, y el evento trae la IP

#### Scenario: Evento del sistema
- **WHEN** se lee un evento con actor `SISTEMA`
- **THEN** el actor no trae nombre

### Requirement: Actor de una reserva con identidad en revisión
Mientras un paciente tenga `requiere_revision = true`, los eventos con actor `PACIENTE` cuya entidad sea su turno `informado_turno_id` SHALL traer `actor.identidadEnRevision: true` y MUST NOT traer el nombre del paciente como actor. En cualquier otro evento, `actor.identidadEnRevision` MUST ser `false`.

#### Scenario: Revisión pendiente
- **GIVEN** un paciente `Paciente Uno Prueba` con la revisión pendiente por una reserva hecha con su DNI y otro nombre
- **WHEN** se lee el `TURNO_CREADO` de ese turno de origen
- **THEN** el actor es `PACIENTE`, con `identidadEnRevision: true` y sin nombre, y el contexto sigue indicando el turno y el paciente asignado

#### Scenario: Revisión resuelta
- **GIVEN** la revisión de ese paciente ya resuelta
- **WHEN** se lee el mismo evento
- **THEN** el actor trae `identidadEnRevision: false` y el nombre que quedó en la ficha

#### Scenario: Otro turno del mismo paciente
- **GIVEN** un paciente con la revisión pendiente y otro turno que no es el de origen
- **WHEN** se lee un evento de ese otro turno
- **THEN** el actor trae `identidadEnRevision: false` y el nombre de la ficha

### Requirement: Solo lectura
La consulta del registro MUST NOT modificar ni agregar eventos, y MUST funcionar en cualquier estado del plan, incluido `VENCIDO`.

#### Scenario: Plan vencido
- **GIVEN** un consultorio con plan `VENCIDO`
- **WHEN** el odontólogo pide el registro
- **THEN** la respuesta es 200 y la cantidad de eventos del consultorio no cambia
