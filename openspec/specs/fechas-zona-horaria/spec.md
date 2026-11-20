# fechas-zona-horaria Specification

## Purpose
Garantiza que toda fecha y hora que ve el odontólogo se muestre en español rioplatense y en la zona horaria de su consultorio, sin importar la zona del dispositivo.

## Requirements

### Requirement: Presentación en la zona del consultorio
El frontend SHALL presentar los instantes UTC recibidos de la API convertidos a la zona horaria configurada del consultorio, y MUST NOT usar la zona horaria del dispositivo.

#### Scenario: Dispositivo en otra zona
- **WHEN** la API devuelve `2026-11-24T13:30:00Z`, el consultorio está en `America/Argentina/Buenos_Aires` y el dispositivo en `Europe/Madrid`
- **THEN** la hora se muestra como `10:30`

### Requirement: Formato es-AR
Las fechas SHALL mostrarse con el formato de `es-AR` y horas de 24 h, por ejemplo "martes 24 de noviembre" y "10:30".

#### Scenario: Encabezado de la agenda
- **WHEN** se formatea el día `2026-11-24` para el encabezado
- **THEN** el texto es "martes 24 de noviembre"

#### Scenario: Hora de un turno
- **WHEN** se formatea la hora de inicio de un turno a las 9 de la mañana
- **THEN** el texto es "9:00", sin "a. m."

### Requirement: Día calendario según el consultorio
El frontend SHALL calcular "hoy" y los límites de un día con la zona del consultorio.

#### Scenario: Cerca de la medianoche UTC
- **WHEN** son las `2026-11-25T01:00:00Z` y el consultorio está en `America/Argentina/Buenos_Aires`
- **THEN** "hoy" es el martes 24 de noviembre
