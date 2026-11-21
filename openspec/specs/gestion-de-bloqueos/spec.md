# gestion-de-bloqueos Specification

## Purpose
Define las operaciones sobre los bloqueos del consultorio: crearlos sin pisar turnos activos y eliminarlos, con auditoría (US-012, RN-AG-05, RN-AD-04).

## Requirements

### Requirement: Crear un bloqueo
El odontólogo SHALL poder crear un bloqueo con inicio, fin y un motivo opcional. Si el bloqueo intersecta turnos activos del consultorio, incluidos los sobreturnos, la creación MUST rechazarse con código `BLOQUEO_CON_TURNOS` y la lista de todos esos turnos (RN-AG-05). Un bloqueo MAY superponerse con otros bloqueos.

#### Scenario: Rango sin turnos activos
- **WHEN** el odontólogo bloquea el jueves de 15:00 a 19:00 con motivo "Curso"
- **THEN** el bloqueo se crea

#### Scenario: Rango con dos turnos activos
- **GIVEN** dos turnos activos el jueves a las 15:00 y a las 16:00
- **WHEN** el odontólogo bloquea el jueves de 15:00 a 19:00
- **THEN** se rechaza con código `BLOQUEO_CON_TURNOS` y los dos turnos, y no se crea el bloqueo

#### Scenario: Turno cancelado en el rango
- **GIVEN** un turno cancelado el jueves a las 15:00
- **WHEN** el odontólogo bloquea el jueves de 15:00 a 19:00
- **THEN** el bloqueo se crea

### Requirement: Eliminar un bloqueo
El odontólogo SHALL poder eliminar un bloqueo de su consultorio. Desde ese momento, su horario vuelve a estar disponible.

#### Scenario: Eliminar un bloqueo
- **GIVEN** un bloqueo el jueves de 15:00 a 19:00
- **WHEN** el odontólogo lo elimina
- **THEN** el bloqueo deja de existir

#### Scenario: Bloqueo de otro consultorio
- **GIVEN** un bloqueo del consultorio B
- **WHEN** el odontólogo del consultorio A intenta eliminarlo
- **THEN** se rechaza con código `RECURSO_NO_ENCONTRADO` y el bloqueo de B sigue existiendo

### Requirement: Auditoría de los bloqueos
Crear y eliminar un bloqueo MUST registrar en la misma transacción un evento `BLOQUEO_CREADO` o `BLOQUEO_ELIMINADO` con el inicio, el fin y el motivo del bloqueo (RN-AD-04).

#### Scenario: Eliminación auditada
- **GIVEN** un bloqueo el jueves de 15:00 a 19:00 con motivo "Curso"
- **WHEN** el odontólogo lo elimina
- **THEN** queda un evento `BLOQUEO_ELIMINADO` con ese inicio, fin y motivo como valor anterior
