# Spec Delta

## Purpose

Define los datos mínimos que el consultorio guarda de cada paciente y su identidad dentro del consultorio: el celular normalizado a E.164 (DD-09, por defecto, a confirmar en PA-01). Sin DNI ni datos de salud (minimización, Ley 25.326).

## ADDED Requirements

### Requirement: Datos mínimos del paciente
Un paciente SHALL tener nombre y apellido obligatorios (de 1 a 80 caracteres cada uno, sin espacios sobrantes en los extremos) y teléfono obligatorio normalizado a E.164. El email es opcional y no distingue mayúsculas. El paciente MUST NOT tener campos de DNI ni datos clínicos.

#### Scenario: Paciente válido
- **WHEN** se crea un paciente "Ana Prueba" con teléfono `011 15-0000-0001`
- **THEN** el paciente queda con teléfono `+5491100000001`

#### Scenario: Teléfono inválido
- **WHEN** se crea un paciente con teléfono `123`
- **THEN** se rechaza con código `TELEFONO_INVALIDO`

#### Scenario: Nombre vacío
- **WHEN** se crea un paciente con nombre de solo espacios
- **THEN** se rechaza con código `VALIDACION`

#### Scenario: Email inválido
- **WHEN** se crea un paciente con email `ana@`
- **THEN** se rechaza con código `VALIDACION`

### Requirement: Teléfono único por consultorio
Dentro de un consultorio, dos pacientes MUST NOT tener el mismo teléfono E.164, y la base MUST impedirlo. El mismo teléfono MAY existir en consultorios distintos.

#### Scenario: Teléfono repetido en el mismo consultorio
- **GIVEN** un paciente del consultorio A con teléfono `+5491100000001`
- **WHEN** se inserta otro paciente del consultorio A con el mismo teléfono
- **THEN** la base rechaza la fila

#### Scenario: Mismo teléfono en otro consultorio
- **GIVEN** un paciente del consultorio A con teléfono `+5491100000001`
- **WHEN** se inserta un paciente del consultorio B con el mismo teléfono
- **THEN** la base lo acepta

### Requirement: Origen, revisión y consentimiento
Un paciente SHALL registrar su origen (`LINK` si se dio de alta al reservar por el link público, `MANUAL` si lo cargó el odontólogo) y una marca de revisión pendiente, falsa por defecto. Un paciente de origen `LINK` MUST tener la fecha y la versión del consentimiento de la política de privacidad.

#### Scenario: Paciente por link sin consentimiento
- **WHEN** se crea un paciente de origen `LINK` sin fecha o sin versión de consentimiento
- **THEN** se rechaza con código `VALIDACION`

#### Scenario: Paciente manual
- **WHEN** el odontólogo carga un paciente de origen `MANUAL` sin consentimiento
- **THEN** el paciente es válido y no requiere revisión

#### Scenario: Marcar para revisión
- **GIVEN** un paciente sin revisión pendiente
- **WHEN** se lo marca para revisión
- **THEN** el paciente queda con revisión pendiente y sus datos no cambian

### Requirement: Los pacientes no se borran
La aplicación MUST NOT poder borrar pacientes de la base (RN-AG-07).

#### Scenario: Borrado con el rol de la aplicación
- **WHEN** el rol de la aplicación intenta un `DELETE` sobre un paciente
- **THEN** la base lo rechaza por falta de permiso

### Requirement: Datos del paciente fuera de los logs y errores
El nombre, el teléfono y el email de un paciente MUST NOT aparecer en los mensajes de error ni en la representación textual del paciente (RN-GL-01).

#### Scenario: Representación textual
- **WHEN** se convierte un paciente a texto (por ejemplo, al loguearlo)
- **THEN** el resultado no contiene su nombre, teléfono ni email
