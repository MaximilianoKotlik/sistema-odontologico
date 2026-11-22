# app-agenda/bloqueos Specification

## Purpose
Pantalla donde el odontólogo bloquea rangos de horario (vacaciones, congresos, trámites) para que nadie reserve en ese período, y los quita cuando ya no hacen falta (US-012, RN-AG-05).

## Requirements

### Requirement: Listado de bloqueos
`/app/bloqueos` SHALL listar los bloqueos actuales y futuros en orden de inicio, cada uno con su rango de días y horas en la zona del consultorio y su motivo. Sin bloqueos, MUST mostrar un estado vacío con "Bloquear horario".

#### Scenario: Vacaciones cargadas
- **GIVEN** un bloqueo del lunes 4 al viernes 15 de enero por "Vacaciones"
- **WHEN** se abre `/app/bloqueos`
- **THEN** se ve "Vacaciones · lun 4 de enero a vie 15 de enero, todo el día"

### Requirement: Crear un bloqueo
El odontólogo SHALL poder crear un bloqueo por rango de horas en un día o por días completos, con motivo opcional de hasta 120 caracteres. Las horas MUST elegirse en pasos de 5 minutos y el fin MUST ser posterior al inicio.

#### Scenario: Días completos
- **WHEN** el odontólogo marca "Todo el día" del 4 al 15 de enero y guarda
- **THEN** el bloqueo cubre desde el inicio del 4 hasta el fin del 15 en la zona del consultorio y aparece en la agenda rayado

#### Scenario: Fin antes del inicio
- **WHEN** el rango termina antes de empezar
- **THEN** el campo de fin queda marcado y no se envía

### Requirement: Bloqueo con turnos
Ante 409 `BLOQUEO_CON_TURNOS`, el formulario SHALL quedarse abierto, explicar que hay turnos activos en ese rango y listar cada uno (día, hora y paciente) con un acceso a su detalle para cancelarlo o reprogramarlo (RN-AG-05).

#### Scenario: Dos turnos en el rango
- **WHEN** el bloqueo del viernes de 15:00 a 19:00 choca con dos turnos
- **THEN** se ven "16:00 · Federico Ejemplo" y "17:00 · Gabriela Prueba" con "Ver turno" cada uno

### Requirement: Eliminar un bloqueo
El odontólogo SHALL poder eliminar un bloqueo previa confirmación que diga que ese horario vuelve a quedar disponible para reservar.

#### Scenario: Quitar un bloqueo
- **WHEN** el odontólogo confirma eliminar el bloqueo "Reunión con el laboratorio"
- **THEN** desaparece del listado y de la agenda, y se anuncia "Quitamos el bloqueo"
