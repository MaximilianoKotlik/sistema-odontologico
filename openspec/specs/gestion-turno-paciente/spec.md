# gestion-turno-paciente Specification

## Purpose

Permite que el paciente, sin cuenta y solo con el token de su link `/t/{token}`, vea su turno y lo reprograme o cancele dentro del plazo que fija el consultorio, sin revelar nada ante un token inválido (US-031, US-032, FL-05, RN-RP-04 a RN-RP-06, RN-RP-11).

## Requirements

### Requirement: Vigencia del token de gestión
La vista por token SHALL responder solo si el hash existe, el turno no terminó y está `RESERVADO`, `CONFIRMADO` o `CANCELADO`. Las demás operaciones exigen además un turno activo y un token no revocado. En cualquier otro caso, la respuesta MUST ser 404 `RECURSO_NO_ENCONTRADO` con el mismo cuerpo, sin indicar la causa.

#### Scenario: Token inexistente
- **WHEN** se pide `GET /api/v1/public/turnos/{token}` con un token que nunca se emitió
- **THEN** la respuesta es 404 `RECURSO_NO_ENCONTRADO`

#### Scenario: Token de un turno ya terminado
- **GIVEN** un turno cuyo fin ya pasó
- **WHEN** se usa su token
- **THEN** la respuesta es 404 con el mismo cuerpo que un token inexistente

#### Scenario: Turno marcado ausente
- **GIVEN** un turno que el odontólogo marcó `AUSENTE` y que todavía no terminó
- **WHEN** se usa su token
- **THEN** la respuesta es 404 con el mismo cuerpo que un token inexistente

#### Scenario: Token malformado
- **WHEN** se usa un token con caracteres fuera de base64url o de otra longitud
- **THEN** la respuesta es 404, no 400

### Requirement: Vista del turno por token
`GET /api/v1/public/turnos/{token}` SHALL devolver el inicio y el fin del turno, la prestación, el estado, el nombre y apellido del paciente, el nombre, la dirección, el teléfono y la zona horaria del consultorio, `puedeModificar`, `puedeReprogramar` y el instante límite para modificar. MUST NOT devolver la nota interna, el DNI, el teléfono ni el email del paciente, ni ids internos del paciente o del consultorio.

#### Scenario: Turno dentro del plazo
- **GIVEN** un turno dentro de 3 días y una anticipación de cancelación de 24 h
- **WHEN** se pide la vista por token
- **THEN** la respuesta es 200 con `puedeModificar: true`, `puedeReprogramar: true` y el límite en `inicio − 24 h`

#### Scenario: Turno fuera del plazo
- **GIVEN** un turno dentro de 20 h y una anticipación de 24 h
- **WHEN** se pide la vista por token
- **THEN** la respuesta es 200 con `puedeModificar: false` y el teléfono del consultorio

#### Scenario: Sin datos internos
- **GIVEN** un turno con nota interna
- **WHEN** se pide la vista por token
- **THEN** la respuesta no contiene la nota, el DNI, el teléfono ni el email del paciente

### Requirement: Vista de un turno cancelado
Si el turno está `CANCELADO` y todavía no terminó, la vista por token SHALL responder 200 con `estado: CANCELADO`, `canceladoPor` (`PACIENTE` u `ODONTOLOGO`), `puedeModificar: false`, `puedeReprogramar: false` y el teléfono del consultorio. MUST NOT devolver el motivo de la cancelación. Los huecos, la reprogramación y la cancelación con ese token MUST responder 404.

#### Scenario: El odontólogo canceló el turno
- **GIVEN** un turno reservado por link que el odontólogo canceló y que todavía no terminó
- **WHEN** el paciente abre su link
- **THEN** la respuesta es 200 con `estado: CANCELADO`, `canceladoPor: ODONTOLOGO`, sin acciones disponibles, con el teléfono del consultorio y sin el motivo

#### Scenario: Intentar reprogramar un turno cancelado
- **GIVEN** un turno cancelado por el odontólogo
- **WHEN** el paciente intenta reprogramarlo con su token
- **THEN** la respuesta es 404 y el turno sigue cancelado

#### Scenario: Turno cancelado que ya terminó
- **GIVEN** un turno cancelado cuyo fin ya pasó
- **WHEN** el paciente abre su link
- **THEN** la respuesta es 404

### Requirement: Respuestas por token no cacheables
Toda respuesta de `/api/v1/public/turnos/**` MUST incluir `Cache-Control: no-store`.

#### Scenario: Vista sin caché
- **WHEN** se pide la vista por token
- **THEN** la respuesta incluye `Cache-Control: no-store`

### Requirement: Plazo para modificar
El paciente SHALL poder cancelar o reprogramar solo si `ahora ≤ inicio − anticipacion_cancelacion_min` del horario actual del turno. Fuera de ese plazo, la operación MUST responder 409 `FUERA_DE_PLAZO` con el teléfono del consultorio en la propiedad `telefonoContacto`, sin cambiar el turno (RN-RP-04).

#### Scenario: Cancelar fuera de plazo
- **GIVEN** un turno dentro de 20 h y anticipación de 24 h
- **WHEN** el paciente cancela
- **THEN** la respuesta es 409 `FUERA_DE_PLAZO` con `telefonoContacto` y el turno sigue activo

#### Scenario: Justo en el límite
- **GIVEN** un turno que empieza exactamente dentro de 24 h y anticipación de 24 h
- **WHEN** el paciente cancela
- **THEN** la cancelación se acepta

### Requirement: Cancelación por link
`POST /api/v1/public/turnos/{token}/cancelacion` SHALL pasar el turno a `CANCELADO` con `cancelado_por = PACIENTE`, revocar el token y liberar el horario, todo en una transacción que registra `TURNO_ESTADO_CAMBIADO` con actor `PACIENTE`, estado anterior y nuevo. La respuesta MUST ser 200 con el estado final.

#### Scenario: Cancelación dentro del plazo
- **GIVEN** un turno dentro del plazo
- **WHEN** el paciente cancela
- **THEN** el turno queda `CANCELADO` con `cancelado_por = PACIENTE`, el horario vuelve a ofrecerse y queda el evento con actor `PACIENTE`
- **AND** la vista por token muestra `estado: CANCELADO` con `canceladoPor: PACIENTE`, y una nueva cancelación con el mismo token responde 404

### Requirement: Cancelación con la reserva online desactivada
Si el consultorio desactivó la reserva pública o su plan está `VENCIDO`, la cancelación por link MUST seguir funcionando dentro del plazo (criterio de aceptación de RN-RP-11).

#### Scenario: Cancelar con plan vencido
- **GIVEN** un consultorio con plan `VENCIDO` y un turno dentro del plazo
- **WHEN** el paciente cancela por link
- **THEN** la respuesta es 200 y el turno queda `CANCELADO`

### Requirement: Huecos para reprogramar
`GET /api/v1/public/turnos/{token}/huecos?desde&hasta` SHALL devolver los huecos ofrecibles para la prestación y la duración del turno, con las mismas reglas y forma que la consulta pública de huecos. El propio turno MUST NOT contar como ocupado.

#### Scenario: Moverse media hora
- **GIVEN** un turno de 10:00 a 10:30 y el resto de la mañana libre
- **WHEN** se piden los huecos de ese día para reprogramar
- **THEN** se ofrece 10:15 aunque se superponga con el horario actual del turno

### Requirement: Reprogramación por link
`POST /api/v1/public/turnos/{token}/reprogramacion` con `nuevoInicio` SHALL mover el mismo turno (mismo id y mismo token) a un hueco ofrecible, conservando la duración, la prestación y el estado, en una transacción que registra `TURNO_REPROGRAMADO` con actor `PACIENTE` y el horario anterior y nuevo (RN-RP-05).

#### Scenario: Reprogramación exitosa
- **GIVEN** un turno dentro de 3 días y anticipación de 24 h
- **WHEN** el paciente elige un hueco libre
- **THEN** la respuesta es 200 con el nuevo horario, el token sigue sirviendo y queda `TURNO_REPROGRAMADO` con actor `PACIENTE`

#### Scenario: Hueco tomado mientras elegía
- **GIVEN** otro paciente reservó el hueco elegido
- **WHEN** el paciente confirma la reprogramación
- **THEN** la respuesta es 409 `HUECO_NO_DISPONIBLE` con los huecos actualizados, y el turno conserva su horario original

#### Scenario: Horario no ofrecible
- **WHEN** el paciente pide un `nuevoInicio` fuera de la disponibilidad o a menos de la anticipación de reserva
- **THEN** la respuesta es 409 `HUECO_NO_DISPONIBLE` y el turno no cambia

### Requirement: Reprogramación deshabilitada sin reserva online
Si el consultorio no toma turnos online, la reprogramación y sus huecos MUST responder 409 `RESERVA_NO_DISPONIBLE` con el teléfono del consultorio, y la vista por token MUST devolver `puedeReprogramar: false`.

#### Scenario: Reprogramar con la reserva desactivada
- **GIVEN** un consultorio con la reserva pública desactivada y un turno dentro del plazo
- **WHEN** el paciente intenta reprogramar
- **THEN** la respuesta es 409 `RESERVA_NO_DISPONIBLE` y el turno no cambia

### Requirement: Modificación concurrente
Si el turno cambió entre la lectura y la escritura (por el odontólogo o por otra pestaña del paciente), la operación MUST responder 409 `CONFLICTO_CONCURRENCIA` sin aplicar el cambio (RN-GL-02).

#### Scenario: Dos cancelaciones simultáneas
- **WHEN** llegan a la vez dos cancelaciones del mismo token
- **THEN** una responde 200 y la otra responde 409 `CONFLICTO_CONCURRENCIA` o 404, y queda un solo evento de cancelación
