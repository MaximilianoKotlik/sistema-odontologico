# humo-e2e Specification

## Purpose
Prueba los recorridos principales de la v1 en un navegador real, en viewport mobile y contra el stack completo, para que ningún release salga con un recorrido roto.

## Requirements

### Requirement: Humo del odontólogo
El humo MUST recorrer en un navegador mobile (390×844) el login con la cuenta de demo y la creación de un turno desde la agenda, y verificar que el turno nuevo aparece en la agenda del día.

#### Scenario: Login y turno nuevo
- **GIVEN** el stack local levantado con el seed de demo
- **WHEN** el humo inicia sesión con la cuenta de demo y crea un turno en un hueco libre
- **THEN** la agenda muestra el turno nuevo con su paciente, su prestación y su horario en la zona del consultorio

#### Scenario: Credenciales inválidas
- **WHEN** el humo intenta iniciar sesión con una contraseña incorrecta
- **THEN** la pantalla de login muestra el error y no entra a la agenda

### Requirement: Humo del paciente
El humo MUST recorrer en un navegador mobile, sin sesión, la reserva desde la página pública del consultorio de demo y la cancelación desde el link de gestión del turno reservado.

#### Scenario: Reserva y cancelación por link
- **GIVEN** el stack local levantado con el seed de demo
- **WHEN** el humo elige una prestación y un hueco en la página pública, completa sus datos y acepta la política de privacidad
- **THEN** ve la confirmación con el link de gestión, y al abrir ese link puede cancelar el turno

#### Scenario: Consultorio con el plan vencido
- **WHEN** el humo abre la página pública del consultorio de demo con el plan vencido
- **THEN** la página no ofrece reservar y muestra el teléfono de contacto

### Requirement: Humo en CI
El CI MUST correr el humo en cada PR a `main` y en cada push a `main`, y el resultado MUST bloquear el merge si falla. Ante una falla MUST guardar las capturas, el video y la traza del recorrido que falló.

#### Scenario: Recorrido roto
- **WHEN** un PR rompe uno de los recorridos del humo
- **THEN** el job del humo falla, el PR no se puede mergear y el CI publica las capturas y la traza del recorrido

#### Scenario: Recorridos sanos
- **WHEN** un PR no rompe ningún recorrido
- **THEN** el job del humo pasa

### Requirement: Humo sin datos reales
El humo MUST usar solo datos ficticios del seed de demo o generados por el propio humo, y MUST NOT dejar datos que alteren otra corrida.

#### Scenario: Dos corridas seguidas
- **WHEN** el humo corre dos veces sobre el mismo stack
- **THEN** las dos corridas pasan, porque cada una usa horarios o pacientes propios que no chocan con la anterior
