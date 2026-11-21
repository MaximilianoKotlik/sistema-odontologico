# disponibilidad-semanal Specification

## Purpose
Permite que el odontólogo defina en qué franjas de cada día de la semana atiende, para que solo se ofrezcan y acepten turnos dentro de ese horario (US-011, RN-AG-03, RN-TE-05).

## Requirements

### Requirement: Consulta de la grilla semanal
El odontólogo SHALL poder obtener sus franjas de atención, cada una con día de la semana (1 = lunes … 7 = domingo, ISO) y horas de inicio y fin locales a la zona horaria del consultorio, ordenadas por día y hora de inicio.

#### Scenario: Grilla vacía
- **GIVEN** un consultorio recién registrado
- **WHEN** se pide la disponibilidad
- **THEN** la respuesta es 200 con una lista vacía

### Requirement: Reemplazo completo de la grilla
Guardar la disponibilidad MUST reemplazar todas las franjas del consultorio por las enviadas, en una sola operación atómica: si alguna franja es inválida, ninguna cambia.

#### Scenario: Lunes en dos franjas
- **WHEN** el odontólogo guarda lunes 09:00–13:00 y 15:00–19:00
- **THEN** la respuesta es 200 y la grilla queda exactamente con esas dos franjas

#### Scenario: Grilla vacía guardada
- **GIVEN** una grilla con franjas
- **WHEN** se guarda una lista vacía
- **THEN** el consultorio queda sin disponibilidad

#### Scenario: Una franja inválida no deja cambios parciales
- **GIVEN** una grilla con lunes 09:00–13:00
- **WHEN** se guarda martes 09:00–13:00 junto con miércoles 10:00–09:00
- **THEN** la respuesta es 400 y la grilla sigue siendo solo lunes 09:00–13:00

### Requirement: Franjas válidas
Cada franja MUST tener día entre 1 y 7, hora de fin posterior a la de inicio, y horas en múltiplos de 5 minutos; si no, la respuesta MUST ser 400 `FRANJA_INVALIDA`.

#### Scenario: Fin antes del inicio
- **WHEN** se guarda una franja de 13:00 a 09:00
- **THEN** la respuesta es 400 con código `FRANJA_INVALIDA`

#### Scenario: Hora no múltiplo de 5
- **WHEN** se guarda una franja de 09:03 a 12:00
- **THEN** la respuesta es 400 con código `FRANJA_INVALIDA`

### Requirement: Sin franjas solapadas
Dos franjas del mismo día MUST NOT solaparse; si se solapan, la respuesta MUST ser 400 `FRANJAS_SOLAPADAS` e indicar los días en conflicto. Franjas contiguas (una termina cuando empieza la otra) MAY coexistir. La base MUST impedir el solapamiento aunque la validación de la aplicación falle.

#### Scenario: Solapadas el mismo día
- **WHEN** se guarda lunes 09:00–13:00 y lunes 12:00–15:00
- **THEN** la respuesta es 400 con código `FRANJAS_SOLAPADAS` y el lunes (1) entre los días en conflicto

#### Scenario: Contiguas
- **WHEN** se guarda lunes 09:00–13:00 y lunes 13:00–15:00
- **THEN** la respuesta es 200

#### Scenario: Mismo horario en días distintos
- **WHEN** se guarda lunes 09:00–13:00 y martes 09:00–13:00
- **THEN** la respuesta es 200

#### Scenario: Red de seguridad en la base
- **WHEN** se insertan directamente en la base dos franjas solapadas del mismo día y consultorio
- **THEN** la base rechaza la segunda por la restricción de exclusión

### Requirement: Auditoría de la disponibilidad
Todo guardado que cambie la grilla MUST registrar en la misma transacción un evento `DISPONIBILIDAD_CAMBIADA` con la grilla anterior y la nueva completas. Un guardado idéntico MUST NOT generar evento.

#### Scenario: Grilla cambiada
- **GIVEN** lunes 09:00–13:00
- **WHEN** se guarda lunes 09:00–12:00
- **THEN** queda un evento `DISPONIBILIDAD_CAMBIADA` con la franja anterior y la nueva

### Requirement: Aislamiento de la disponibilidad
Guardar la grilla MUST reemplazar solo las franjas del consultorio de la sesión.

#### Scenario: Otro consultorio intacto
- **GIVEN** los consultorios A y B con franjas
- **WHEN** el odontólogo de A guarda una grilla vacía
- **THEN** las franjas de B no cambian
