# gestion-de-turnos Specification

## Purpose
Define las operaciones que modifican la agenda de turnos del consultorio: crear, cambiar de estado, reprogramar, ajustar la duración y anotar. Cada una valida contra la agenda, se serializa por consultorio, se audita en la misma transacción y detecta ediciones concurrentes (US-021 a US-025, RN-AG-01 a 12, RN-AD-01, RN-GL-02).

## Requirements

### Requirement: Crear un turno
El sistema SHALL crear un turno `RESERVADO` con la duración vigente de su prestación, salvo que el odontólogo pida otra en múltiplos de 5 minutos. El origen es `MANUAL` si lo crea el odontólogo y `LINK` si lo crea el paciente. La creación MUST rechazarse con el error del dominio si el turno pisa un bloqueo, queda fuera de la disponibilidad o se solapa con un turno activo (RN-AG-01, RN-AG-03). Una prestación inactiva MUST NOT usarse para un turno nuevo.

#### Scenario: Turno manual en un hueco libre
- **GIVEN** una prestación "Consulta" de 30 minutos y un hueco libre el lunes de 10:00 a 10:30 dentro de la disponibilidad
- **WHEN** el odontólogo crea un turno el lunes a las 10:00 para esa prestación
- **THEN** el turno queda `RESERVADO`, con origen `MANUAL`, 30 minutos de duración y fin a las 10:30

#### Scenario: Turno que se solapa
- **GIVEN** un turno activo de 10:00 a 10:30
- **WHEN** el odontólogo crea otro turno, no sobreturno, de 10:15 a 10:45
- **THEN** se rechaza con código `TURNO_SOLAPADO` y el id del turno en conflicto, y no se crea nada

#### Scenario: Turnos contiguos
- **GIVEN** un turno activo de 10:00 a 10:30
- **WHEN** el odontólogo crea un turno de 10:30 a 11:00
- **THEN** el turno se crea

#### Scenario: Prestación inactiva
- **GIVEN** la prestación "Blanqueamiento" desactivada
- **WHEN** se crea un turno con esa prestación
- **THEN** se rechaza con código `PRESTACION_INACTIVA`

#### Scenario: Turno creado por el paciente
- **WHEN** el paciente crea un turno desde la reserva pública
- **THEN** el turno queda con origen `LINK` y la duración de la prestación

### Requirement: Sobreturno solo del odontólogo
Solo el odontólogo SHALL poder crear un sobreturno. Un sobreturno MAY solaparse con otros turnos y quedar fuera de la disponibilidad, pero MUST NOT caer dentro de un bloqueo (RN-AG-04). Si un paciente pide un sobreturno, la operación MUST rechazarse con código `ACCION_NO_PERMITIDA`.

#### Scenario: Sobreturno sobre un turno ocupado
- **GIVEN** un turno activo de 10:00 a 10:30
- **WHEN** el odontólogo crea un sobreturno de 10:00 a 10:20
- **THEN** el sobreturno se crea marcado como sobreturno y con origen `MANUAL`

#### Scenario: Sobreturno dentro de un bloqueo
- **GIVEN** un bloqueo de 14:00 a 18:00
- **WHEN** el odontólogo crea un sobreturno a las 15:00
- **THEN** se rechaza con código `HORARIO_BLOQUEADO`

#### Scenario: Paciente que pide un sobreturno
- **WHEN** un paciente pide crear un sobreturno
- **THEN** se rechaza con código `ACCION_NO_PERMITIDA`

### Requirement: Cambiar el estado de un turno
El sistema SHALL confirmar, cancelar o marcar como ausente un turno según la máquina de estados de RN-AG-06. Cancelar MUST registrar quién canceló: el odontólogo o el paciente, según el actor. Una transición inválida MUST rechazarse con código `TRANSICION_INVALIDA` sin cambiar el turno.

#### Scenario: Confirmar un turno reservado
- **GIVEN** un turno `RESERVADO`
- **WHEN** el odontólogo lo confirma
- **THEN** el turno queda `CONFIRMADO`

#### Scenario: El paciente cancela
- **GIVEN** un turno `CONFIRMADO`
- **WHEN** el paciente lo cancela desde su link
- **THEN** el turno queda `CANCELADO`, con cancelado por `PACIENTE`, y su horario queda libre (RN-AG-09)

#### Scenario: Ausente antes de tiempo
- **GIVEN** un turno que empieza dentro de una hora
- **WHEN** el odontólogo lo marca como ausente
- **THEN** se rechaza con código `TRANSICION_INVALIDA`

### Requirement: Reprogramar un turno
El sistema SHALL mover un turno activo a otro inicio conservando su id y su duración (RN-AG-11, RN-AG-12), y validando el nuevo horario como al crearlo, sin contar al propio turno. El odontólogo no tiene anticipación mínima.

#### Scenario: Reprogramar a un hueco libre
- **GIVEN** un turno de 10:00 a 10:45 que empieza dentro de dos horas
- **WHEN** el odontólogo lo reprograma a las 16:00, que está libre
- **THEN** el turno queda de 16:00 a 16:45, con el mismo id

#### Scenario: Correrse dentro de su propio horario
- **GIVEN** un turno de 10:00 a 10:30 y libre hasta las 11:00
- **WHEN** el odontólogo lo reprograma a las 10:15
- **THEN** el turno queda de 10:15 a 10:45, porque no choca consigo mismo

### Requirement: Ajustar la duración de un turno
El sistema SHALL cambiar la duración de un turno activo en múltiplos de 5 minutos y recalcular su fin, validando el horario nuevo (RN-AG-12). Si la validación falla, la duración MUST NOT cambiar.

#### Scenario: Alargar dentro de un hueco libre
- **GIVEN** un turno de 10:00 a 10:30 y libre hasta las 11:00
- **WHEN** el odontólogo ajusta su duración a 45 minutos
- **THEN** el turno termina a las 10:45

#### Scenario: Alargar sobre otro turno
- **GIVEN** un turno de 10:00 a 10:30 y otro turno activo de 10:30 a 11:00
- **WHEN** el odontólogo ajusta el primero a 45 minutos
- **THEN** se rechaza con código `TURNO_SOLAPADO` y la duración sigue en 30 minutos

#### Scenario: Alargar sobre un bloqueo
- **GIVEN** un turno de 10:00 a 10:30 y un bloqueo desde las 10:40
- **WHEN** el odontólogo ajusta su duración a 45 minutos
- **THEN** se rechaza con código `HORARIO_BLOQUEADO`

### Requirement: Nota interna del odontólogo
El odontólogo SHALL poder guardar en un turno una nota interna de hasta 500 caracteres. Una nota más larga MUST rechazarse con código `VALIDACION` sin cambiar la nota. Un paciente MUST NOT poder editarla.

#### Scenario: Nota guardada
- **WHEN** el odontólogo guarda la nota "Trae radiografía"
- **THEN** el turno queda con esa nota

#### Scenario: Nota demasiado larga
- **WHEN** el odontólogo guarda una nota de 501 caracteres
- **THEN** se rechaza con código `VALIDACION` y la nota anterior se conserva

### Requirement: Auditoría de los turnos
Cada creación, cambio de estado, reprogramación, ajuste de duración y cambio de nota MUST registrar en la misma transacción un evento (`TURNO_CREADO`, `TURNO_ESTADO_CAMBIADO`, `TURNO_REPROGRAMADO`, `TURNO_DURACION_CAMBIADA` o `TURNO_NOTA_CAMBIADA`). El evento lleva el actor y el valor anterior y nuevo de los campos que cambiaron; del paciente, solo su id (RN-AD-01, RN-AD-05). Una operación que no cambia nada MUST NOT registrar evento.

#### Scenario: Confirmación auditada
- **GIVEN** un turno `RESERVADO`
- **WHEN** el odontólogo lo confirma
- **THEN** queda un evento `TURNO_ESTADO_CAMBIADO` del odontólogo con estado anterior `RESERVADO` y nuevo `CONFIRMADO`

#### Scenario: Reprogramación por el paciente auditada
- **WHEN** el paciente reprograma su turno de 10:00 a las 16:00
- **THEN** queda un evento `TURNO_REPROGRAMADO` con actor `PACIENTE` y los inicios y fines anterior y nuevo

#### Scenario: Sin datos del paciente en el evento
- **WHEN** se crea un turno
- **THEN** el evento `TURNO_CREADO` tiene el id del paciente y no su nombre, DNI, teléfono ni email

### Requirement: Sin cambio si falla la auditoría
Si el registro del evento de auditoría falla, el cambio sobre el turno MUST revertirse (RN-AD-03).

#### Scenario: Falla de la auditoría
- **GIVEN** que el registro del evento falla
- **WHEN** el odontólogo crea un turno
- **THEN** el turno no queda creado

### Requirement: Ediciones concurrentes
Todo cambio sobre un turno existente MUST indicar la versión del turno que se leyó. Si el turno cambió desde entonces, la operación MUST rechazarse con código `CONFLICTO_CONCURRENCIA` sin aplicar el cambio (RN-GL-02).

#### Scenario: Versión vieja
- **GIVEN** un turno en su versión 3
- **WHEN** el odontólogo lo confirma indicando la versión 2
- **THEN** se rechaza con código `CONFLICTO_CONCURRENCIA` y el turno no cambia

### Requirement: Escrituras de agenda serializadas por consultorio
Toda escritura de turnos, bloqueos o pacientes MUST tomar primero el mismo bloqueo de fila sobre el consultorio que usa el reemplazo de la disponibilidad. Así, las escrituras de agenda y los cambios de grilla de un consultorio se aplican de a una, y ninguna se valida contra datos que otra está cambiando.

#### Scenario: Dos turnos al mismo horario a la vez
- **GIVEN** un hueco libre de 10:00 a 10:30
- **WHEN** se crean al mismo tiempo dos turnos, no sobreturno, en ese horario
- **THEN** uno se crea y el otro se rechaza con código `TURNO_SOLAPADO`

#### Scenario: Un turno y un bloqueo a la vez
- **WHEN** se crean al mismo tiempo un turno de 10:00 a 10:30 y un bloqueo de 09:00 a 12:00
- **THEN** uno de los dos se rechaza, y no quedan un turno activo y un bloqueo que se pisen

#### Scenario: Un turno mientras se reemplaza la grilla
- **GIVEN** una disponibilidad de lunes de 09:00 a 13:00
- **WHEN** a la vez se reemplaza la grilla por una que deja el lunes sin atención y se crea un turno, no sobreturno, el lunes a las 10:00
- **THEN** el turno no queda creado fuera de la grilla que quedó vigente

### Requirement: Aislamiento de los turnos
Un turno, paciente o prestación de otro consultorio MUST responder `RECURSO_NO_ENCONTRADO` en cualquier operación, sin modificarse.

#### Scenario: Confirmar un turno ajeno
- **GIVEN** un turno del consultorio B
- **WHEN** el odontólogo del consultorio A intenta confirmarlo
- **THEN** se rechaza con código `RECURSO_NO_ENCONTRADO` y el turno de B no cambia
