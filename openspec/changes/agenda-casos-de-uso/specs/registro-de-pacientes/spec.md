# Spec Delta

## Purpose

Define cómo se da de alta un paciente dentro del consultorio, desde el alta rápida del odontólogo o desde la reserva pública: de forma idempotente por DNI, sin sobrescribir datos existentes y marcando para revisión cuando no coinciden (RN-RP-03, DD-24).

## ADDED Requirements

### Requirement: Alta idempotente por DNI
Registrar un paciente con un DNI que no existe en el consultorio SHALL crearlo. Registrarlo con un DNI que ya existe MUST reutilizar el paciente existente sin sobrescribir ninguno de sus datos, e informar que se reutilizó. Dos registros simultáneos con el mismo DNI MUST terminar en un único paciente.

#### Scenario: DNI nuevo
- **WHEN** se registra "Ana Prueba" con DNI `90000001` en un consultorio donde ese DNI no existe
- **THEN** se crea el paciente

#### Scenario: DNI existente
- **GIVEN** el paciente "Ana Prueba" con DNI `90000001` y teléfono `+5491100000001`
- **WHEN** se registra "Ana Prueba" con DNI `90.000.001` y teléfono `+5491100000099`
- **THEN** se reutiliza el mismo paciente, su teléfono sigue siendo `+5491100000001` y el resultado indica que se reutilizó

#### Scenario: Registro simultáneo
- **WHEN** se registra dos veces a la vez el DNI `90000002` en el mismo consultorio
- **THEN** queda un único paciente con ese DNI

### Requirement: Revisión cuando los datos no coinciden
Si el DNI ya existe y el nombre o el apellido recibidos difieren de los guardados, el paciente MUST quedar marcado para revisión, una sola vez, para que el odontólogo lo revise.

#### Scenario: Otro nombre para el mismo DNI
- **GIVEN** el paciente "Ana Prueba" con DNI `90000001`, sin revisión pendiente
- **WHEN** se registra "Ana María Prueba" con el mismo DNI
- **THEN** el paciente queda con revisión pendiente y su nombre sigue siendo "Ana"

#### Scenario: Mismo nombre con otras mayúsculas
- **GIVEN** el paciente "Ana Prueba" con DNI `90000001`
- **WHEN** se registra "ana prueba" con el mismo DNI
- **THEN** el paciente no queda con revisión pendiente

### Requirement: Auditoría de los pacientes
Crear un paciente y marcarlo para revisión MUST registrar en la misma transacción un evento `PACIENTE_CREADO` o `PACIENTE_MARCADO_PARA_REVISION`. El evento identifica al paciente solo por su id y MUST NOT incluir su nombre, DNI, teléfono ni email (RN-AD-05).

#### Scenario: Alta auditada sin datos personales
- **WHEN** se crea un paciente
- **THEN** queda un evento `PACIENTE_CREADO` con el id del paciente y su origen, sin nombre, DNI, teléfono ni email

### Requirement: Pacientes aislados por consultorio
El mismo DNI en otro consultorio MUST crear un paciente distinto.

#### Scenario: Mismo DNI en otro consultorio
- **GIVEN** el paciente con DNI `90000001` del consultorio A
- **WHEN** se registra el DNI `90000001` en el consultorio B
- **THEN** se crea un paciente nuevo en el consultorio B
