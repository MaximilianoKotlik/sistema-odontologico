# turnos Specification

## Purpose
Define qué es un turno válido en la agenda del consultorio: su duración y su fin derivado, sus estados y transiciones, la marca de sobreturno y la garantía de que dos turnos activos no se solapan (RN-AG-01, 02, 04, 06, 08, 11, 12 y 13; DD-10, DD-12 y DD-17).

## Requirements

### Requirement: Duración y fin derivado
Un turno SHALL tener una duración entre 5 y 480 minutos, en múltiplos de 5, y su fin MUST ser siempre `inicio + duración`: el fin no se ingresa. Reprogramar MUST conservar la duración y recalcular el fin. Ajustar la duración MUST recalcular el fin. El inicio MUST caer en un múltiplo de 5 minutos.

#### Scenario: Fin calculado
- **WHEN** se crea un turno que empieza a las 10:00 con una duración de 45 minutos
- **THEN** el turno termina a las 10:45

#### Scenario: Reprogramar conserva la duración
- **GIVEN** un turno de 10:00 a 10:45
- **WHEN** se lo reprograma para las 16:00
- **THEN** el turno queda de 16:00 a 16:45, con el mismo identificador

#### Scenario: Ajustar la duración
- **GIVEN** un turno de 10:00 a 10:30
- **WHEN** se ajusta su duración a 50 minutos
- **THEN** el turno termina a las 10:50

#### Scenario: Duración inválida
- **WHEN** se crea o se ajusta un turno con una duración de 42 minutos, de 0 o de 485
- **THEN** se rechaza con código `DURACION_INVALIDA`

#### Scenario: Inicio fuera de la grilla de 5 minutos
- **WHEN** se crea un turno que empieza a las 10:03
- **THEN** se rechaza con código `HORARIO_INVALIDO`

#### Scenario: La base rechaza un fin incoherente
- **WHEN** se inserta directamente en la base un turno cuyo fin no es igual a inicio más duración
- **THEN** la base rechaza la fila

### Requirement: La duración se copia de la prestación
Al crear un turno, su duración SHALL tomarse de la duración vigente de su tipo de prestación, como copia. Cambiar después la duración de la prestación MUST NOT alterar los turnos existentes.

#### Scenario: Cambio posterior de la prestación
- **GIVEN** un turno de "Limpieza" creado cuando la prestación duraba 45 minutos
- **WHEN** la prestación pasa a durar 60 minutos
- **THEN** el turno sigue durando 45 minutos

### Requirement: Estados y transiciones
Un turno SHALL tener uno de cuatro estados: `RESERVADO`, `CONFIRMADO`, `CANCELADO` o `AUSENTE`. Las únicas transiciones válidas MUST ser:
- `RESERVADO → CONFIRMADO`;
- `RESERVADO | CONFIRMADO → CANCELADO`, con quién canceló (`ODONTOLOGO` o `PACIENTE`) y un motivo opcional;
- `RESERVADO | CONFIRMADO → AUSENTE`, solo cuando el inicio ya pasó.

`CANCELADO` y `AUSENTE` MUST ser terminales. Cualquier otra transición MUST rechazarse con código `TRANSICION_INVALIDA`.

#### Scenario: Confirmar un turno reservado
- **GIVEN** un turno `RESERVADO`
- **WHEN** se lo confirma
- **THEN** el turno queda `CONFIRMADO`

#### Scenario: Cancelar registra quién canceló
- **GIVEN** un turno `CONFIRMADO`
- **WHEN** el paciente lo cancela
- **THEN** el turno queda `CANCELADO`, con cancelado por `PACIENTE`

#### Scenario: Ausente antes de tiempo
- **GIVEN** un turno `CONFIRMADO` que empieza dentro de una hora
- **WHEN** se lo marca como ausente
- **THEN** se rechaza con código `TRANSICION_INVALIDA`

#### Scenario: Ausente después del inicio
- **GIVEN** un turno `RESERVADO` cuyo inicio pasó hace 10 minutos
- **WHEN** se lo marca como ausente
- **THEN** el turno queda `AUSENTE`

#### Scenario: Estado terminal
- **GIVEN** un turno `CANCELADO`
- **WHEN** se intenta confirmarlo, cancelarlo o marcarlo como ausente
- **THEN** se rechaza con código `TRANSICION_INVALIDA`

#### Scenario: Reprogramar un turno terminal
- **GIVEN** un turno `AUSENTE`
- **WHEN** se intenta reprogramarlo o ajustar su duración
- **THEN** se rechaza con código `TRANSICION_INVALIDA`

#### Scenario: La base exige coherencia de la cancelación
- **WHEN** se inserta directamente en la base un turno `CONFIRMADO` con cancelado por `ODONTOLOGO`, o un turno `CANCELADO` sin cancelado por
- **THEN** la base rechaza la fila

### Requirement: Sobreturno como marca
El sobreturno SHALL ser una marca del turno, independiente del estado: un sobreturno se confirma, se cancela o queda ausente igual que cualquier turno y conserva la marca.

#### Scenario: Sobreturno cancelado conserva la marca
- **GIVEN** un sobreturno `RESERVADO`
- **WHEN** se lo cancela
- **THEN** el turno queda `CANCELADO` y sigue marcado como sobreturno

### Requirement: Sin solapamiento entre turnos activos
Un turno activo (`RESERVADO` o `CONFIRMADO`) que no es sobreturno MUST NOT solaparse con otro turno activo que no es sobreturno del mismo consultorio. Los intervalos son semiabiertos `[inicio, fin)`: un turno que termina a las 10:30 no se solapa con uno que empieza a las 10:30. Un sobreturno MAY solaparse con cualquier turno. Un turno cancelado o ausente MUST NOT ocupar su horario. En v1 el ámbito del solapamiento es el consultorio, porque hay un único profesional y un único sillón implícito.

#### Scenario: Turno solapado
- **GIVEN** un turno activo de 10:00 a 10:30
- **WHEN** se valida un turno nuevo, no sobreturno, de 10:15 a 10:45
- **THEN** se rechaza con código `TURNO_SOLAPADO` e identifica el turno en conflicto

#### Scenario: Turnos contiguos
- **GIVEN** un turno activo de 10:00 a 10:30
- **WHEN** se valida un turno de 10:30 a 11:00
- **THEN** no hay conflicto de solapamiento

#### Scenario: Sobreturno superpuesto
- **GIVEN** un turno activo de 10:00 a 10:30
- **WHEN** se valida un sobreturno de 10:00 a 10:20
- **THEN** no hay conflicto de solapamiento

#### Scenario: El horario de un turno cancelado queda libre
- **GIVEN** un turno cancelado de 10:00 a 10:30
- **WHEN** se valida un turno nuevo de 10:00 a 10:30
- **THEN** no hay conflicto de solapamiento

#### Scenario: Otro consultorio
- **GIVEN** un turno activo de 10:00 a 10:30 en el consultorio A
- **WHEN** se inserta un turno de 10:00 a 10:30 en el consultorio B
- **THEN** la base lo acepta

#### Scenario: La base rechaza el solapamiento aunque se saltee la validación
- **GIVEN** un turno activo, no sobreturno, de 10:00 a 10:30
- **WHEN** se inserta directamente en la base otro turno activo, no sobreturno, de 10:15 a 10:45 en el mismo consultorio
- **THEN** la base rechaza la fila por la restricción de exclusión

#### Scenario: Reactivar un horario ocupado en la base
- **GIVEN** un turno cancelado de 10:00 a 10:30 y un turno activo de 10:00 a 10:30
- **WHEN** se cambia directamente en la base el estado del cancelado a `RESERVADO`
- **THEN** la base rechaza la actualización

### Requirement: Turno dentro de la disponibilidad y fuera de los bloqueos
Un turno que no es sobreturno MUST caer completo dentro de la disponibilidad del consultorio y MUST NOT intersectar un bloqueo; si no, se rechaza con código `FUERA_DE_DISPONIBILIDAD` o `HORARIO_BLOQUEADO`. Un sobreturno MAY quedar fuera de la disponibilidad, pero MUST NOT intersectar un bloqueo. Las franjas contiguas del mismo día cuentan como una sola franja continua.

#### Scenario: Turno que excede la franja
- **GIVEN** una disponibilidad de lunes de 09:00 a 13:00
- **WHEN** se valida un turno el lunes de 12:30 a 13:15
- **THEN** se rechaza con código `FUERA_DE_DISPONIBILIDAD`

#### Scenario: Turno sobre franjas contiguas
- **GIVEN** una disponibilidad de lunes de 09:00 a 13:00 y de 13:00 a 17:00
- **WHEN** se valida un turno el lunes de 12:30 a 13:30
- **THEN** no hay conflicto de disponibilidad

#### Scenario: Sobreturno fuera de horario
- **GIVEN** una disponibilidad de lunes de 09:00 a 13:00
- **WHEN** se valida un sobreturno el lunes de 13:00 a 13:20
- **THEN** no hay conflicto

#### Scenario: Sobreturno dentro de un bloqueo
- **GIVEN** un bloqueo el lunes de 11:00 a 12:00
- **WHEN** se valida un sobreturno de 11:30 a 11:50
- **THEN** se rechaza con código `HORARIO_BLOQUEADO`

#### Scenario: Disponibilidad en la zona del consultorio
- **GIVEN** un consultorio en `America/Argentina/Buenos_Aires` con disponibilidad de lunes de 09:00 a 13:00
- **WHEN** se valida un turno que empieza el lunes a las 12:00 UTC (09:00 hora local)
- **THEN** no hay conflicto de disponibilidad

### Requirement: Datos del turno acotados
La nota interna de un turno SHALL ser opcional y de hasta 500 caracteres. El motivo de cancelación SHALL ser opcional y de hasta 200 caracteres. El origen del turno MUST ser `LINK` o `MANUAL`. Un sobreturno MUST tener origen `MANUAL`: solo lo crea el odontólogo.

#### Scenario: Nota demasiado larga
- **WHEN** se edita la nota interna de un turno con 501 caracteres
- **THEN** se rechaza con código `VALIDACION`

#### Scenario: Sobreturno por link
- **WHEN** se crea un sobreturno con origen `LINK`
- **THEN** se rechaza con código `VALIDACION`

### Requirement: Los turnos no se borran
La aplicación MUST NOT poder borrar turnos de la base: un turno se cancela (RN-AG-07).

#### Scenario: Borrado con el rol de la aplicación
- **WHEN** el rol de la aplicación intenta un `DELETE` sobre un turno
- **THEN** la base lo rechaza por falta de permiso

### Requirement: Un turno no referencia datos de otro consultorio
El paciente y el tipo de prestación de un turno MUST pertenecer al mismo consultorio que el turno, y la base MUST impedirlo aunque falle la aplicación.

#### Scenario: Paciente de otro consultorio
- **GIVEN** un paciente del consultorio A
- **WHEN** se inserta directamente en la base un turno del consultorio B que referencia a ese paciente
- **THEN** la base rechaza la fila por la clave foránea compuesta
