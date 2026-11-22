# app-agenda/vista-agenda Specification

## Purpose
Agenda diaria y semanal del odontólogo: muestra turnos, sobreturnos, bloqueos y horarios de atención en la zona del consultorio, legible en el celular y con los cambios de los pacientes marcados (US-020, DD-30).

## Requirements

### Requirement: Vista por defecto según el ancho
`/app/agenda` SHALL abrir la vista diaria de hoy por debajo de 768 px y la vista semanal de la semana actual desde 768 px. El odontólogo SHALL poder cambiar entre "Día" y "Semana", y la vista elegida MUST reflejarse en la URL.

#### Scenario: Celular
- **WHEN** el odontólogo abre la agenda en un viewport de 390 px
- **THEN** ve la vista diaria de hoy

#### Scenario: Escritorio
- **WHEN** el odontólogo abre la agenda en un viewport de 1440 px
- **THEN** ve la vista semanal de lunes a domingo de la semana actual

### Requirement: Línea de tiempo del día
La vista diaria SHALL listar en orden cronológico los turnos, sobreturnos y bloqueos del día, los huecos libres dentro del horario de atención y los tramos fuera de horario entre franjas, y MUST leerse sin scroll horizontal a 360 px.

#### Scenario: Día con dos franjas
- **GIVEN** horario de 09:00 a 13:00 y de 15:00 a 19:00, y un hueco libre de 11:30 a 13:00
- **WHEN** se muestra el día
- **THEN** aparece "Libre · 11:30 a 13:00" y después "No atendés · 13:00 a 15:00"

#### Scenario: 360 px
- **WHEN** la vista diaria se muestra en un viewport de 360 px
- **THEN** no hay scroll horizontal y cada tarjeta muestra hora, paciente, prestación y estado

### Requirement: Estado visible sin depender del color
Cada turno SHALL mostrar su estado con color, ícono y texto: Reservado, Confirmado, Cancelado o Ausente. Un sobreturno MUST tener borde punteado y la etiqueta "Sobreturno", y un bloqueo MUST tener fondo rayado y la etiqueta "Bloqueo" con su motivo.

#### Scenario: Sobreturno superpuesto
- **GIVEN** un turno de 10:30 a 11:30 y un sobreturno de 10:45 a 11:05
- **WHEN** se muestra la agenda
- **THEN** el sobreturno aparece con borde punteado, la etiqueta "Sobreturno" y la indicación de con qué turno se superpone

### Requirement: Cancelados atenuados y ocultables
Los turnos cancelados SHALL mostrarse atenuados. El interruptor "Mostrar cancelados" MUST ocultarlos o mostrarlos, y la elección MUST conservarse en el dispositivo.

#### Scenario: Ocultar cancelados
- **WHEN** el odontólogo apaga "Mostrar cancelados"
- **THEN** los cancelados desaparecen de la vista y el resumen del día no los cuenta

### Requirement: Marca de turnos nuevos
Los turnos que un paciente creó, reprogramó o canceló desde el link y que el odontólogo todavía no vio MUST llevar la etiqueta "Nuevo" con el tipo de cambio, y la agenda MUST mostrar un aviso con la cantidad. Abrir el detalle del turno MUST dejarlo como visto.

#### Scenario: Reserva desde el link
- **GIVEN** un paciente reservó un turno por el link
- **WHEN** el odontólogo abre la agenda
- **THEN** el turno tiene la etiqueta "Nuevo · reservó por el link" y el aviso dice "1 cambio de tus pacientes"

#### Scenario: Visto
- **WHEN** el odontólogo abre el detalle de ese turno y vuelve a la agenda
- **THEN** el turno ya no tiene la etiqueta "Nuevo"

### Requirement: Navegación entre días y semanas
La agenda SHALL permitir ir al día o la semana anterior y siguiente, volver a "Hoy" y, en la vista diaria, elegir un día de la semana desde una tira de días con la cantidad de turnos de cada uno.

#### Scenario: Día siguiente
- **WHEN** el odontólogo toca "Día siguiente" desde el viernes 20
- **THEN** se muestra el sábado 21 y la URL incluye esa fecha

### Requirement: Marca de ahora
En el día de hoy, la agenda SHALL mostrar una marca con la hora actual en la zona del consultorio, en su lugar dentro de la línea de tiempo o la grilla.

#### Scenario: Mediodía
- **GIVEN** son las 12:10 en la zona del consultorio
- **WHEN** se muestra hoy
- **THEN** la marca "Ahora · 12:10" aparece entre el último turno anterior y el siguiente

### Requirement: Horario local del consultorio
Las horas y las fechas MUST mostrarse en la zona horaria del consultorio, aunque el dispositivo esté en otra zona (RN-TE-05).

#### Scenario: Dispositivo en otra zona
- **GIVEN** un turno a las 13:00 UTC, el consultorio en `America/Argentina/Buenos_Aires` y el dispositivo en `Europe/Madrid`
- **WHEN** se muestra la agenda
- **THEN** el turno dice 10:00

### Requirement: Días sin turnos o sin horario
Un día con horario de atención y sin turnos SHALL decir "No tenés turnos" y mostrar sus franjas libres. Un día sin horario de atención SHALL decir "No atendés este día" con un acceso a Configuración › Horarios.

#### Scenario: Domingo
- **GIVEN** el domingo no tiene franjas
- **WHEN** el odontólogo abre el domingo
- **THEN** ve "No atendés los domingos" y el link "Cambiar mis horarios"

### Requirement: Sin conexión
Sin conexión, la agenda SHALL mostrar la última agenda guardada con el aviso de C-06 y la hora en que se guardó, con las acciones deshabilitadas. Si no hay agenda guardada para ese rango, MUST decirlo y ofrecer reintentar.

#### Scenario: Agenda guardada
- **GIVEN** la agenda de hoy se vio con conexión a las 10:32
- **WHEN** el odontólogo la abre sin conexión
- **THEN** ve esos turnos, el aviso "Mostrando tu agenda de las 10:32" y "Nuevo turno" deshabilitado

### Requirement: Error al cargar
Si la agenda no se puede cargar por un error del servidor, la pantalla SHALL mostrar un error recuperable con "Reintentar", sin perder la fecha elegida.

#### Scenario: Error 500
- **WHEN** `GET /api/v1/agenda` responde 500
- **THEN** se ve "No pudimos cargar tu agenda" con "Reintentar"
