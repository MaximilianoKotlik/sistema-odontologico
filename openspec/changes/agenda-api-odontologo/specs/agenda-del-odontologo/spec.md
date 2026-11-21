# Spec Delta

## Purpose

Define cómo el odontólogo consulta su agenda: la vista de un rango, el detalle de un turno, la marca de los cambios hechos por pacientes y los turnos que quedaron fuera de su horario de atención (US-020, DD-30, RN-AG-03, RN-TE-01).

## ADDED Requirements

### Requirement: Agenda de un rango
El odontólogo SHALL obtener los turnos y bloqueos de su consultorio que intersectan un rango de instantes de hasta 42 días, ordenados por inicio, incluidos los turnos cancelados y ausentes. Cada turno MUST indicar su horario, estado, si es sobreturno, su prestación, el nombre del paciente, si tiene nota, quién lo canceló, el cambio del paciente sin ver con el instante de esa novedad y su versión.

#### Scenario: Semana con turnos, un sobreturno y un bloqueo
- **GIVEN** en la semana un turno confirmado, un sobreturno, un turno cancelado y un bloqueo
- **WHEN** el odontólogo pide la agenda de esa semana
- **THEN** la respuesta es 200 con los tres turnos y el bloqueo en orden cronológico, y el sobreturno viene marcado

#### Scenario: Rango inválido
- **WHEN** el odontólogo pide la agenda con un rango de 60 días o con `desde` posterior a `hasta`
- **THEN** la respuesta es 400 con código `VALIDACION`

### Requirement: Agenda sin datos personales de más
La agenda MUST NOT incluir el DNI, el teléfono ni el email del paciente, ni el texto de la nota o del motivo de cancelación: esos datos salen solo del detalle del turno.

#### Scenario: Sin datos personales de más
- **WHEN** el odontólogo pide la agenda
- **THEN** ningún turno trae el DNI, el teléfono, el email ni el texto de la nota del paciente

### Requirement: Detalle de un turno
El odontólogo SHALL obtener el detalle completo de un turno de su consultorio: los datos de la agenda más el DNI, el teléfono y el email del paciente, si requiere revisión, la nota interna, el motivo de cancelación y el horario anterior si el paciente lo reprogramó.

#### Scenario: Turno reprogramado por el paciente
- **GIVEN** un turno que el paciente reprogramó de las 10:00 a las 16:00
- **WHEN** el odontólogo pide su detalle
- **THEN** la respuesta trae el horario de las 16:00 y el horario anterior de las 10:00

#### Scenario: Turno de otro consultorio
- **GIVEN** un turno del consultorio B
- **WHEN** el odontólogo del consultorio A pide su detalle
- **THEN** la respuesta es 404 con código `RECURSO_NO_ENCONTRADO`

### Requirement: Cambios del paciente sin ver
Un turno que el paciente reservó, reprogramó o canceló por el link, y que el odontólogo todavía no vio, SHALL aparecer en la agenda y en el detalle con el tipo de cambio (`RESERVO`, `REPROGRAMO` o `CANCELO`) y el instante de la novedad.

#### Scenario: Reserva por link sin ver
- **GIVEN** un turno que el paciente reservó por el link
- **WHEN** el odontólogo pide la agenda
- **THEN** el turno viene con cambio del paciente `RESERVO` y el instante de la novedad

### Requirement: Marcar una novedad como vista
Marcar como visto un turno MUST indicar el instante de la novedad que se vio, y MUST quitar la marca solo si no llegó una novedad más nueva. Un turno sin cambios o con una novedad más nueva MUST responder igual, sin error. Marcar como visto MUST NOT registrar un evento de auditoría (DD-39).

#### Scenario: Marcar como visto
- **GIVEN** un turno con un cambio del paciente sin ver, con novedad de las 10:00
- **WHEN** el odontólogo lo marca como visto indicando la novedad de las 10:00
- **THEN** la respuesta es 204, en la agenda el turno viene sin cambio del paciente y no se registra ningún evento de auditoría

#### Scenario: Novedad nueva mientras se marcaba como visto
- **GIVEN** un turno con una novedad de las 10:00 que el odontólogo abrió
- **AND** a las 10:01 el paciente lo reprogramó por el link y quedó una novedad nueva
- **WHEN** el odontólogo lo marca como visto indicando la novedad de las 10:00
- **THEN** la respuesta es 204 y en la agenda el turno sigue con cambio del paciente `REPROGRAMO` y la novedad de las 10:01

#### Scenario: Visto sin indicar la novedad
- **WHEN** el odontólogo marca un turno como visto sin indicar el instante de la novedad
- **THEN** la respuesta es 400 con código `VALIDACION`

#### Scenario: Visto dos veces
- **GIVEN** un turno ya marcado como visto
- **WHEN** el odontólogo lo marca como visto otra vez
- **THEN** la respuesta es 204

#### Scenario: Visto de un turno ajeno
- **GIVEN** un turno del consultorio B con un cambio del paciente sin ver
- **WHEN** el odontólogo del consultorio A lo marca como visto
- **THEN** la respuesta es 404 y el turno de B conserva su marca

### Requirement: Turnos fuera de la disponibilidad
El odontólogo SHALL obtener los turnos futuros activos de su consultorio, no sobreturnos, que no caen dentro de su disponibilidad vigente. La consulta MUST NOT modificar ningún turno.

#### Scenario: Grilla achicada
- **GIVEN** un turno confirmado el próximo martes a las 18:00 y una disponibilidad reemplazada que el martes termina a las 17:00
- **WHEN** el odontólogo pide los turnos fuera de la disponibilidad
- **THEN** la respuesta trae ese turno, que sigue confirmado

#### Scenario: Sobreturnos y turnos pasados no cuentan
- **GIVEN** un sobreturno futuro fuera de horario y un turno de ayer fuera de horario
- **WHEN** el odontólogo pide los turnos fuera de la disponibilidad
- **THEN** la respuesta no trae ninguno de los dos

### Requirement: Agenda aislada por consultorio
Todas las consultas de agenda MUST devolver solo datos del consultorio de la sesión.

#### Scenario: Agenda de otro consultorio
- **GIVEN** turnos en los consultorios A y B en la misma semana
- **WHEN** el odontólogo del consultorio A pide la agenda de esa semana
- **THEN** la respuesta trae solo los turnos de A
