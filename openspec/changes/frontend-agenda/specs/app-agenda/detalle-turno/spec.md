# Spec Delta

## Purpose

Pantalla de detalle de un turno, donde el odontólogo ve los datos del paciente y del turno y desde donde parten las acciones de C-14.

## ADDED Requirements

### Requirement: Datos del turno y del paciente
`/app/agenda/turnos/{id}` SHALL mostrar el nombre y apellido del paciente, su DNI, su teléfono como link para llamar, su email si lo tiene, la prestación, el día, el horario de inicio y fin, la duración, el estado, si es sobreturno y su origen ("Reservó por tu link" o "Lo cargaste vos").

#### Scenario: Turno confirmado
- **WHEN** el odontólogo abre un turno confirmado de Extracción de 10:30 a 11:30
- **THEN** ve el paciente con DNI y teléfono, "Extracción · 60 min", "viernes 20 de noviembre · 10:30 a 11:30" y el estado "Confirmado"

### Requirement: Nota interna
Si el turno tiene nota interna, el detalle SHALL mostrarla. La nota MUST NOT mostrarse en ningún lugar visible para el paciente (US-025).

#### Scenario: Con nota
- **GIVEN** un turno con la nota "Trae radiografía"
- **WHEN** se abre el detalle
- **THEN** se ve la sección "Nota interna" con ese texto

### Requirement: Turno cancelado
Un turno cancelado SHALL indicar quién lo canceló (el paciente o el odontólogo) y el motivo si lo hay.

#### Scenario: Cancelado por el paciente
- **WHEN** se abre un turno que el paciente canceló por el link
- **THEN** se ve "Cancelado por el paciente" y el motivo

### Requirement: Cambio del paciente
Si el turno tiene un cambio del paciente sin ver, el detalle SHALL explicar qué hizo y cuándo, y MUST marcarlo como visto al abrirse.

#### Scenario: Reprogramado por el paciente
- **WHEN** se abre un turno que el paciente reprogramó desde el link
- **THEN** se ve "El paciente lo reprogramó desde el link" con el horario anterior

### Requirement: Acciones según el estado
El detalle SHALL mostrar solo las acciones válidas para el estado del turno: Confirmar (Reservado), Reprogramar, Ajustar duración y Cancelar (Reservado o Confirmado), Marcar ausente (Reservado o Confirmado ya empezado) y Editar nota (cualquier estado). Sin conexión, MUST mostrarlas deshabilitadas.

#### Scenario: Turno cancelado
- **WHEN** se abre un turno cancelado
- **THEN** solo está disponible "Editar nota"

#### Scenario: Turno futuro
- **WHEN** se abre un turno reservado para mañana
- **THEN** no aparece "Marcar ausente"

### Requirement: Turno inexistente o ajeno
Si el turno no existe o es de otro consultorio (404), la pantalla SHALL decir "No encontramos este turno" con un acceso a la agenda.

#### Scenario: Link viejo
- **WHEN** se abre `/app/agenda/turnos/{id}` y la API responde 404
- **THEN** se ve "No encontramos este turno" y el botón "Volver a la agenda"
