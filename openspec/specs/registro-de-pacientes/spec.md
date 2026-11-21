# registro-de-pacientes Specification

## Purpose
Define cómo se da de alta un paciente dentro del consultorio, desde el alta rápida del odontólogo o desde la reserva pública: de forma idempotente por DNI, sin sobrescribir datos existentes y marcando para revisión cuando no coinciden, con lo informado en la reserva (RN-RP-03, DD-24, DD-38).

## Requirements

### Requirement: Alta idempotente por DNI
Registrar un paciente con un DNI que no existe en el consultorio SHALL crearlo. Registrarlo con un DNI que ya existe MUST reutilizar el paciente existente sin sobrescribir ninguno de sus datos, e informar que se reutilizó junto con los datos guardados. Esto vale igual para el alta manual del odontólogo y para la reserva pública. Dos registros simultáneos con el mismo DNI MUST terminar en un único paciente.

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
Si el DNI ya existe y el nombre o el apellido recibidos difieren de los guardados (sin distinguir mayúsculas, acentos ni espacios en los extremos, con `Paciente.datosDifierenDe` de C-10), el paciente MUST quedar marcado para revisión y MUST guardar lo informado: nombre, apellido, email e instante (DD-38). Si ya estaba marcado, lo informado MUST reemplazarse por lo de este registro. Si los datos coinciden, MUST NOT cambiar nada.

#### Scenario: Primera marca
- **GIVEN** el paciente "Ana Prueba" con DNI `90000001`, sin revisión pendiente
- **WHEN** se registra "Ana María Prueba" con el mismo DNI y el email `ana.maria@example.com`
- **THEN** el paciente queda con revisión pendiente, su nombre sigue siendo "Ana" y lo informado es "Ana María Prueba" con `ana.maria@example.com` y el instante del registro

#### Scenario: Segunda reserva distinta sin revisar
- **GIVEN** el paciente "Ana Prueba" con DNI `90000001`, con revisión pendiente y lo informado "Ana María Prueba"
- **WHEN** se registra "Anita Prueba" con el mismo DNI
- **THEN** el paciente sigue con revisión pendiente, su nombre sigue siendo "Ana" y lo informado es "Anita Prueba"

#### Scenario: DNI existente con el mismo nombre
- **GIVEN** el paciente "José Pérez" con DNI `90000001`, sin revisión pendiente
- **WHEN** se registra "jose perez" con el mismo DNI
- **THEN** el paciente no queda con revisión pendiente, no guarda nada informado y no se registra ningún evento

### Requirement: Turno de origen de lo informado
Cuando un registro marca al paciente para revisión y después se crea un turno en la misma operación, el turno MUST quedar como turno de origen de lo informado, en la misma transacción. Si el registro no marcó al paciente, MUST NOT cambiar nada.

#### Scenario: Reserva que marca al paciente
- **GIVEN** un registro que marcó al paciente con DNI `90000001` para revisión
- **WHEN** se crea su turno y se vincula como turno de origen
- **THEN** el turno de origen de lo informado es ese turno

#### Scenario: Reserva sin diferencias
- **GIVEN** un registro que reutilizó al paciente sin marcarlo
- **WHEN** se crea su turno y se pide vincularlo
- **THEN** el paciente no cambia

### Requirement: Auditoría de los pacientes
Crear un paciente y marcarlo para revisión MUST registrar en la misma transacción un evento `PACIENTE_CREADO` o `PACIENTE_MARCADO_PARA_REVISION`, también cuando una segunda reserva reemplaza lo informado. El evento identifica al paciente solo por su id y MUST NOT incluir su nombre, DNI, teléfono, email ni lo informado en la reserva (RN-AD-05).

#### Scenario: Alta auditada sin datos personales
- **WHEN** se crea un paciente
- **THEN** queda un evento `PACIENTE_CREADO` con el id del paciente y su origen, sin nombre, DNI, teléfono ni email

#### Scenario: Marca auditada sin lo informado
- **WHEN** un registro marca al paciente para revisión
- **THEN** queda un evento `PACIENTE_MARCADO_PARA_REVISION` con `requiere_revision` anterior y nuevo, sin el nombre, el apellido ni el email informados

### Requirement: Pacientes aislados por consultorio
El mismo DNI en otro consultorio MUST crear un paciente distinto.

#### Scenario: Mismo DNI en otro consultorio
- **GIVEN** el paciente con DNI `90000001` del consultorio A
- **WHEN** se registra el DNI `90000001` en el consultorio B
- **THEN** se crea un paciente nuevo en el consultorio B
