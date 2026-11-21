# Spec Delta

## Purpose

Permite que el paciente, solo con el link `/t/{token}` que recibió al reservar, vea su turno y lo reprograme o cancele dentro del plazo del consultorio, y que un link inválido no revele nada (US-031, US-032, FL-05, RN-RP-04, RN-RP-05, RN-RP-11).

## ADDED Requirements

### Requirement: Vista del turno por link

La página `/t/{token}` SHALL mostrarse sin iniciar sesión y sin el shell del odontólogo, con el día y la hora del turno en la zona del consultorio, la prestación, el estado (chip con ícono y texto), el nombre y apellido del paciente, el consultorio con dirección y teléfono, y hasta cuándo se puede cambiar o cancelar (`modificableHasta`). MUST NOT mostrar DNI, teléfono, email del paciente ni la nota interna, y el token MUST NOT guardarse en el navegador.

#### Scenario: Turno reservado dentro del plazo

- **GIVEN** un turno `RESERVADO` dentro de 3 días con `puedeModificar: true` y `puedeReprogramar: true`
- **WHEN** el paciente abre su link
- **THEN** ve el día, la hora, la prestación, el estado "Reservado" con su explicación, "Podés cambiarlo o cancelarlo por acá hasta …" y los botones "Cambiar día u horario" y "Cancelar turno"

#### Scenario: Sin datos privados

- **WHEN** se muestra la vista del turno
- **THEN** la página no contiene el DNI, el teléfono ni el email del paciente, ni la nota interna

### Requirement: Link inválido

Ante cualquier 404 de la API por token, la página MUST mostrar un único mensaje, "Este link no funciona", sin datos del consultorio ni la causa (inexistente, vencido, terminado o malformado).

#### Scenario: Token desconocido

- **WHEN** la vista por token responde 404
- **THEN** se ve "Este link no funciona", sin nombre ni teléfono de ningún consultorio

#### Scenario: Acción sobre un link que dejó de servir

- **GIVEN** el paciente tiene la página abierta
- **WHEN** la cancelación responde 404
- **THEN** la página pasa al mismo estado "Este link no funciona"

### Requirement: Fuera de plazo

Si la vista responde `puedeModificar: false` en un turno activo, la página MUST ocultar las acciones antes de intentar y mostrar que ya no se puede cambiar ni cancelar por el link, con un botón para llamar al consultorio. Un 409 `FUERA_DE_PLAZO` durante una acción MUST llevar a ese mismo estado usando `telefonoContacto`.

#### Scenario: Faltan menos de 24 horas

- **GIVEN** un turno dentro de 20 horas con anticipación de 24 horas
- **WHEN** el paciente abre su link
- **THEN** no ve los botones de cambiar ni cancelar, y ve el mensaje con el botón para llamar

#### Scenario: El plazo venció mientras miraba

- **WHEN** la cancelación responde 409 `FUERA_DE_PLAZO` con `telefonoContacto`
- **THEN** se ve el mensaje de fuera de plazo con ese teléfono y el turno sigue igual

### Requirement: Reprogramación

Con `puedeReprogramar: true`, el paciente SHALL poder elegir un día y un horario nuevos con la misma tira de días y horarios de la reserva (huecos de `/turnos/{token}/huecos`, en la zona del consultorio) y confirmar el cambio en un diálogo que muestra el horario actual y el nuevo. Al confirmarse, la página MUST mostrar el turno con el horario nuevo y que el link sigue siendo el mismo.

#### Scenario: Mover el turno

- **WHEN** el paciente elige el jueves 26 a las 15:00 y confirma en el diálogo
- **THEN** la reprogramación se envía con ese instante como `nuevoInicio` y la página muestra el turno reprogramado con el mismo link

#### Scenario: Horario ocupado al confirmar

- **WHEN** la reprogramación responde 409 `HUECO_NO_DISPONIBLE` con huecos actualizados
- **THEN** se ve "Ese horario se ocupó hace un instante. Tu turno sigue el …" con el horario original, y la lista de horarios se actualiza con el elegido como no disponible

### Requirement: Cancelación

Con `puedeModificar: true`, el paciente SHALL poder cancelar el turno confirmándolo en un diálogo que avisa que no se puede deshacer y que el link deja de servir para cambios. Al confirmarse, la página MUST mostrar el turno cancelado, sin acciones.

#### Scenario: Cancelar dentro del plazo

- **WHEN** el paciente confirma la cancelación en el diálogo
- **THEN** la página muestra "Cancelaste tu turno" con el turno tachado y sin acciones

### Requirement: Reserva online desactivada

Si la vista responde `puedeModificar: true` y `puedeReprogramar: false`, la página MUST ofrecer solo cancelar, con el teléfono del consultorio para cambiar el turno (RN-RP-11). Un 409 `RESERVA_NO_DISPONIBLE` al reprogramar MUST llevar a ese mismo estado.

#### Scenario: Consultorio que dejó de tomar turnos online

- **GIVEN** un turno dentro del plazo de un consultorio con la reserva pública desactivada
- **WHEN** el paciente abre su link
- **THEN** ve "Cancelar turno" y el teléfono para cambiarlo, pero no "Cambiar día u horario"

### Requirement: Turno cancelado

Si la vista responde `estado: CANCELADO`, la página SHALL mostrar el turno cancelado, quién lo canceló según `canceladoPor` (vos o el consultorio) y el teléfono del consultorio, sin acciones y sin motivo.

#### Scenario: Lo canceló el consultorio

- **WHEN** la vista responde `estado: CANCELADO` y `canceladoPor: ODONTOLOGO`
- **THEN** se ve que el consultorio canceló el turno, con el botón para llamar y sin acciones

### Requirement: Uso desde el celular y accesibilidad

La página MUST funcionar sin scroll horizontal desde 360 px de ancho, con objetivos táctiles de al menos 44 px, foco visible, diálogos que atrapan el foco y lo devuelven al cerrarse, y estados que no dependan solo del color. Con `prefers-reduced-motion` no hay animaciones.

#### Scenario: Diálogo de cancelación con teclado

- **WHEN** el paciente abre "Cancelar turno" con el teclado y después cierra el diálogo con Escape
- **THEN** el foco queda dentro del diálogo mientras está abierto y vuelve al botón "Cancelar turno" al cerrarse
