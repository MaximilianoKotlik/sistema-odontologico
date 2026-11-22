# Spec Delta

## Purpose

La página pública deja de tomar turnos con la prueba vencida aunque la tarea diaria todavía no la haya pasado a `VENCIDO` (C-20, RN-RP-11).

## MODIFIED Requirements

### Requirement: Consultorio que no toma turnos online
Si el consultorio desactivó la reserva pública o está vencido (plan `VENCIDO`, o `PRUEBA` con `prueba_hasta` pasada en su zona horaria), la página pública MUST responder 200 con `tomaTurnosOnline: false`, el nombre, la dirección y el teléfono, y sin prestaciones (RN-RP-11).

#### Scenario: Reserva desactivada
- **GIVEN** un consultorio con `reserva_publica_activa = false`
- **WHEN** se pide su página pública
- **THEN** la respuesta es 200 con `tomaTurnosOnline: false`, su teléfono y la lista de prestaciones vacía

#### Scenario: Plan vencido
- **GIVEN** un consultorio con plan `VENCIDO` y la reserva pública activa
- **WHEN** se pide su página pública
- **THEN** la respuesta es 200 con `tomaTurnosOnline: false`

#### Scenario: Prueba vencida que la tarea todavía no pasó
- **GIVEN** un consultorio en `PRUEBA` con `prueba_hasta` de ayer y la reserva pública activa
- **WHEN** se pide su página pública antes de que corra la tarea diaria
- **THEN** la respuesta es 200 con `tomaTurnosOnline: false`
