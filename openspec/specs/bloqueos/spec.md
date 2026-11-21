# bloqueos Specification

## Purpose
Define los bloqueos: períodos en los que el consultorio no atiende (vacaciones, trámites, un curso), que ningún turno puede ocupar y que no pueden pisar turnos activos (RN-AG-03, 04, 05 y 08).

## Requirements

### Requirement: Bloqueo con inicio y fin explícitos
Un bloqueo SHALL definirse por un inicio y un fin explícitos, con el fin posterior al inicio y ambos en múltiplos de 5 minutos, y un motivo opcional de hasta 120 caracteres. Un bloqueo MAY abarcar varios días. Si no cumple esto, se rechaza con código `HORARIO_INVALIDO` o `VALIDACION`.

#### Scenario: Bloqueo de una tarde
- **WHEN** se crea un bloqueo de 14:00 a 18:00 con motivo "Curso"
- **THEN** el bloqueo es válido

#### Scenario: Bloqueo de varios días
- **WHEN** se crea un bloqueo del lunes a las 00:00 al sábado a las 00:00
- **THEN** el bloqueo es válido

#### Scenario: Fin no posterior al inicio
- **WHEN** se crea un bloqueo de 18:00 a 14:00
- **THEN** se rechaza con código `HORARIO_INVALIDO`

#### Scenario: Horario fuera de la grilla de 5 minutos
- **WHEN** se crea un bloqueo de 14:02 a 18:00
- **THEN** se rechaza con código `HORARIO_INVALIDO`

#### Scenario: La base rechaza un bloqueo invertido
- **WHEN** se inserta directamente en la base un bloqueo cuyo fin no es posterior a su inicio
- **THEN** la base rechaza la fila

### Requirement: Un bloqueo no pisa turnos activos
Un bloqueo MUST NOT intersectar turnos activos (`RESERVADO` o `CONFIRMADO`) del consultorio, incluidos los sobreturnos. Si los intersecta, se rechaza con código `BLOQUEO_CON_TURNOS` y la lista de **todos** los turnos en conflicto, para que el odontólogo los cancele o los reprograme primero. Los intervalos son semiabiertos.

#### Scenario: Bloqueo sobre un turno confirmado
- **GIVEN** un turno confirmado de 15:00 a 15:30 y un sobreturno reservado de 16:00 a 16:20
- **WHEN** se valida un bloqueo de 14:00 a 18:00
- **THEN** se rechaza con código `BLOQUEO_CON_TURNOS` y los dos turnos en conflicto

#### Scenario: Turnos cancelados no impiden el bloqueo
- **GIVEN** un turno cancelado de 15:00 a 15:30
- **WHEN** se valida un bloqueo de 14:00 a 18:00
- **THEN** no hay conflicto

#### Scenario: Turno contiguo al bloqueo
- **GIVEN** un turno activo de 13:30 a 14:00
- **WHEN** se valida un bloqueo de 14:00 a 18:00
- **THEN** no hay conflicto

### Requirement: Bloqueos superpuestos permitidos
Dos bloqueos del mismo consultorio MAY superponerse, y un horario cubierto por cualquiera de ellos SHALL quedar bloqueado.

#### Scenario: Bloqueo dentro de otro
- **GIVEN** un bloqueo de 09:00 a 18:00
- **WHEN** se valida un bloqueo de 10:00 a 11:00
- **THEN** el bloqueo es válido

### Requirement: Bloqueos aislados por consultorio
Un bloqueo SHALL pertenecer a un único consultorio y afectar solo a los turnos y huecos de ese consultorio.

#### Scenario: Bloqueo de otro consultorio
- **GIVEN** un bloqueo de 14:00 a 18:00 en el consultorio A
- **WHEN** se valida un turno de 15:00 a 15:30 en el consultorio B
- **THEN** el bloqueo de A no genera conflicto
