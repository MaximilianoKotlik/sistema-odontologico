# api-de-turnos-y-bloqueos Specification

## Purpose
Define los endpoints con los que el odontólogo crea y modifica turnos y bloqueos, y cómo llegan los errores de la agenda al cliente (US-012, US-021 a US-025, FL-03).

## Requirements

### Requirement: Crear un turno por la API
El odontólogo SHALL crear un turno, o un sobreturno, para un paciente existente o para uno nuevo cargado en el mismo pedido. La respuesta MUST ser 201 con el turno creado e indicar si se reutilizó un paciente que ya tenía ese DNI.

#### Scenario: Turno con alta rápida
- **WHEN** el odontólogo crea un turno con un paciente nuevo "Ana Prueba" con DNI `90000009`
- **THEN** la respuesta es 201 con el turno `RESERVADO` y el paciente creado

#### Scenario: Alta rápida con un DNI existente
- **GIVEN** un paciente con DNI `90000001`
- **WHEN** el odontólogo crea un turno con un paciente nuevo con ese DNI
- **THEN** la respuesta es 201, el turno usa el paciente existente y la respuesta indica que se reutilizó, con los datos guardados del paciente y no los escritos

#### Scenario: Alta rápida con un DNI existente y otro nombre
- **GIVEN** el paciente "Ana Prueba" con DNI `90000001`
- **WHEN** el odontólogo crea un turno con un paciente nuevo "Anna Prueba" con ese DNI
- **THEN** la respuesta es 201 con `pacienteReutilizado` y el paciente "Ana Prueba", que queda con revisión pendiente y con ese turno como turno de origen de lo informado

### Requirement: Cambiar un turno por la API
El odontólogo SHALL confirmar, cancelar, marcar ausente, reprogramar, ajustar la duración y editar la nota de un turno indicando la versión que leyó. La respuesta MUST ser el turno actualizado con su versión nueva.

#### Scenario: Reprogramar
- **GIVEN** un turno de 10:00 a 10:30 en su versión 1
- **WHEN** el odontólogo lo reprograma a las 16:00 con la versión 1
- **THEN** la respuesta es 200 con el turno de 16:00 a 16:30 y la versión 2

#### Scenario: Versión vieja
- **GIVEN** un turno en su versión 2
- **WHEN** el odontólogo edita su nota con la versión 1
- **THEN** la respuesta es 409 con código `CONFLICTO_CONCURRENCIA`

### Requirement: Turno con el que choca
Un 409 `TURNO_SOLAPADO` causado por un turno existente MUST incluir el id, el inicio y el fin del turno con el que choca, el primero por inicio si son varios. El error MUST NOT incluir datos personales del paciente.

#### Scenario: Solapamiento con un turno
- **GIVEN** un turno activo de 10:00 a 10:30
- **WHEN** el odontólogo crea un turno de 10:15 a 10:45
- **THEN** la respuesta es 409 `TURNO_SOLAPADO` con el id, el inicio y el fin del turno de las 10:00, y sin el nombre del paciente

### Requirement: Errores de la agenda con su estado HTTP
Los errores de la agenda MUST llegar en Problem Details con su código estable: 400 para `VALIDACION`, `DURACION_INVALIDA` y `HORARIO_INVALIDO`; 404 para `RECURSO_NO_ENCONTRADO`; 409 para `TURNO_SOLAPADO`, `HORARIO_BLOQUEADO`, `FUERA_DE_DISPONIBILIDAD`, `BLOQUEO_CON_TURNOS`, `TRANSICION_INVALIDA`, `PRESTACION_INACTIVA`, `ACCION_NO_PERMITIDA` y `CONFLICTO_CONCURRENCIA`.

#### Scenario: Duración no múltiplo de 5
- **WHEN** el odontólogo ajusta la duración de un turno a 47 minutos
- **THEN** la respuesta es 400 con código `DURACION_INVALIDA`

#### Scenario: Nota de 501 caracteres
- **WHEN** el odontólogo guarda una nota de 501 caracteres
- **THEN** la respuesta es 400 con código `VALIDACION`

### Requirement: Bloqueos por la API
El odontólogo SHALL crear un bloqueo (201), eliminarlo (204) y listar los bloqueos que siguen vigentes desde un instante, ordenados por inicio. Un bloqueo que pisa turnos activos MUST responder 409 `BLOQUEO_CON_TURNOS` con la lista de esos turnos.

#### Scenario: Bloqueos futuros
- **GIVEN** un bloqueo que terminó ayer y dos bloqueos la semana que viene
- **WHEN** el odontólogo lista los bloqueos desde ahora
- **THEN** la respuesta trae los dos bloqueos de la semana que viene

#### Scenario: Bloqueo sobre turnos
- **GIVEN** dos turnos activos el jueves por la tarde
- **WHEN** el odontólogo bloquea el jueves de 15:00 a 19:00
- **THEN** la respuesta es 409 `BLOQUEO_CON_TURNOS` con los dos turnos

### Requirement: Endpoints aislados por consultorio
Cada endpoint de turnos y bloqueos MUST responder 404 a un turno o bloqueo de otro consultorio, sin modificarlo.

#### Scenario: Cancelar un turno ajeno
- **GIVEN** un turno del consultorio B
- **WHEN** el odontólogo del consultorio A lo cancela
- **THEN** la respuesta es 404 y el turno de B sigue activo
