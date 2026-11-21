# huecos-disponibles Specification

## Purpose
Calcula los horarios en los que cabe un turno de cierta duración, en un rango de fechas. Lo consume la página pública (C-15). También sirve para sugerir horarios en la agenda del odontólogo (RN-RP-01, RN-AG-03, RN-TE-05).

## Requirements

### Requirement: Huecos dentro de la disponibilidad
El cálculo SHALL devolver, en orden cronológico, los inicios en los que un turno de la duración pedida cabe completo dentro de la disponibilidad semanal del consultorio, para cada fecha del rango pedido. Las fechas y las franjas MUST interpretarse en la zona horaria del consultorio, y los inicios se devuelven como instantes. Las franjas contiguas del mismo día cuentan como una sola franja continua.

#### Scenario: Mañana libre
- **GIVEN** una disponibilidad de lunes de 09:00 a 10:00, un paso de 15 minutos y ningún turno ni bloqueo
- **WHEN** se calculan los huecos de 30 minutos para un lunes
- **THEN** los inicios son 09:00, 09:15 y 09:30 en hora local

#### Scenario: Día sin disponibilidad
- **GIVEN** una disponibilidad solo de lunes a viernes
- **WHEN** se calculan los huecos para un domingo
- **THEN** no hay huecos

#### Scenario: Duración mayor que la franja
- **GIVEN** una disponibilidad de lunes de 09:00 a 09:30
- **WHEN** se calculan los huecos de 45 minutos para un lunes
- **THEN** no hay huecos

#### Scenario: Instantes en UTC
- **GIVEN** un consultorio en `America/Argentina/Buenos_Aires` con disponibilidad de lunes de 09:00 a 09:30 y un paso de 30 minutos
- **WHEN** se calculan los huecos de 30 minutos para un lunes
- **THEN** el único hueco empieza a las 12:00 UTC de ese lunes

### Requirement: Paso desde el inicio de la franja
Los inicios de los huecos SHALL ser múltiplos del paso configurado del consultorio, contados desde el inicio de la franja, aunque haya turnos que dejen horarios intermedios libres.

#### Scenario: Franja que no empieza en hora exacta
- **GIVEN** una disponibilidad de lunes de 09:10 a 10:00 y un paso de 20 minutos
- **WHEN** se calculan los huecos de 20 minutos para un lunes
- **THEN** los inicios son 09:10 y 09:30 (09:50 no entra porque terminaría a las 10:10)

### Requirement: Huecos libres de turnos y bloqueos
Un hueco MUST NOT intersectar un bloqueo ni un turno activo (`RESERVADO` o `CONFIRMADO`). Los sobreturnos activos **sí** ocupan su horario. Los turnos cancelados o ausentes MUST NOT ocupar su horario. Los intervalos son semiabiertos.

#### Scenario: Turno en el medio
- **GIVEN** una disponibilidad de lunes de 09:00 a 10:30, un paso de 30 minutos y un turno confirmado de 09:30 a 10:00
- **WHEN** se calculan los huecos de 30 minutos para ese lunes
- **THEN** los inicios son 09:00 y 10:00

#### Scenario: Sobreturno ocupa el hueco
- **GIVEN** una disponibilidad de lunes de 09:00 a 10:00, un paso de 30 minutos y un sobreturno reservado de 09:00 a 09:20
- **WHEN** se calculan los huecos de 30 minutos para ese lunes
- **THEN** el único inicio es 09:30

#### Scenario: Cancelado libera el hueco
- **GIVEN** una disponibilidad de lunes de 09:00 a 09:30 y un turno cancelado de 09:00 a 09:30
- **WHEN** se calculan los huecos de 30 minutos para ese lunes
- **THEN** el único inicio es 09:00

#### Scenario: Bloqueo de toda la tarde
- **GIVEN** una disponibilidad de lunes de 15:00 a 19:00 y un bloqueo de 14:00 a 18:00
- **WHEN** se calculan los huecos de 60 minutos con paso de 60 para ese lunes
- **THEN** el único inicio es 18:00

### Requirement: Anticipación mínima y horizonte
Cuando se pide con límites de reserva, el cálculo MUST excluir los huecos que empiezan antes de `ahora + anticipación mínima` y los que empiezan después de `ahora + horizonte en días`. "Ahora" se toma del reloj del sistema, para que el resultado sea reproducible en los tests.

#### Scenario: Anticipación de 2 horas
- **GIVEN** son las 08:30 de un lunes, con anticipación mínima de 120 minutos, disponibilidad de 09:00 a 12:00 y un paso de 30 minutos
- **WHEN** se calculan los huecos de 30 minutos para ese lunes
- **THEN** el primer inicio es 10:30

#### Scenario: Más allá del horizonte
- **GIVEN** un horizonte de 60 días
- **WHEN** se calculan los huecos para una fecha a 61 días de hoy
- **THEN** no hay huecos

#### Scenario: Sin límites para el odontólogo
- **GIVEN** son las 08:30 de un lunes y se pide sin límites de reserva
- **WHEN** se calculan los huecos de 30 minutos para ese lunes con disponibilidad desde las 09:00
- **THEN** el primer inicio es 09:00
