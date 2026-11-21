# Spec Delta

## Purpose

Con el plan vencido, el paciente sigue cancelando por link pero no reprograma (C-20, criterio de aceptación de RN-RP-11).

## MODIFIED Requirements

### Requirement: Cancelación con la reserva online desactivada
Si el consultorio desactivó la reserva pública o está vencido (plan `VENCIDO`, o `PRUEBA` con `prueba_hasta` pasada), la cancelación por link MUST seguir funcionando dentro del plazo (criterio de aceptación de RN-RP-11).

#### Scenario: Cancelar con plan vencido
- **GIVEN** un consultorio con plan `VENCIDO` y un turno dentro del plazo
- **WHEN** el paciente cancela por link
- **THEN** la respuesta es 200 y el turno queda `CANCELADO`

#### Scenario: Cancelar con la prueba vencida antes de la tarea
- **GIVEN** un consultorio en `PRUEBA` con `prueba_hasta` de ayer y un turno dentro del plazo
- **WHEN** el paciente cancela por link
- **THEN** la respuesta es 200, el turno queda `CANCELADO` y el odontólogo ve la novedad

### Requirement: Reprogramación deshabilitada sin reserva online
Si el consultorio no toma turnos online, la reprogramación y sus huecos MUST responder 409 `RESERVA_NO_DISPONIBLE` con el teléfono del consultorio, y la vista por token MUST devolver `puedeReprogramar: false`.

#### Scenario: Reprogramar con la reserva desactivada
- **GIVEN** un consultorio con la reserva pública desactivada y un turno dentro del plazo
- **WHEN** el paciente intenta reprogramar
- **THEN** la respuesta es 409 `RESERVA_NO_DISPONIBLE` y el turno no cambia

#### Scenario: Reprogramar con el plan vencido
- **GIVEN** un consultorio vencido y un turno dentro del plazo
- **WHEN** el paciente abre su link e intenta reprogramar
- **THEN** la vista trae `puedeReprogramar: false` y `puedeModificar: true` (puede cancelar), y la reprogramación responde 409 `RESERVA_NO_DISPONIBLE`
