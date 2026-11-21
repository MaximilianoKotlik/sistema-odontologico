# pacientes Specification

## Purpose
Define los datos mínimos que el consultorio guarda de cada paciente y su identidad dentro del consultorio: el **DNI**, único por consultorio (DD-24, resuelve PA-01). El teléfono celular es obligatorio como dato de contacto, pero no identifica al paciente. Sin datos de salud (minimización, Ley 25.326).

## Requirements

### Requirement: Datos mínimos del paciente
Un paciente SHALL tener nombre y apellido obligatorios (de 1 a 80 caracteres cada uno, sin espacios sobrantes en los extremos), DNI obligatorio y teléfono celular obligatorio normalizado a E.164, también en la carga manual (DD-32). El email es opcional y no distingue mayúsculas. El paciente MUST NOT tener datos clínicos.

#### Scenario: Paciente válido
- **WHEN** se crea un paciente "Ana Prueba" con DNI `90.000.001` y teléfono `011 15-0000-0001`
- **THEN** el paciente queda con DNI `90000001` y teléfono `+5491100000001`

#### Scenario: Teléfono inválido
- **WHEN** se crea un paciente con teléfono `123`
- **THEN** se rechaza con código `TELEFONO_INVALIDO`

#### Scenario: Nombre vacío
- **WHEN** se crea un paciente con nombre de solo espacios
- **THEN** se rechaza con código `VALIDACION`

#### Scenario: Email inválido
- **WHEN** se crea un paciente con email `ana@`
- **THEN** se rechaza con código `VALIDACION`

### Requirement: DNI normalizado
El DNI MUST guardarse solo con dígitos: se quitan los puntos y los espacios, y tiene que quedar con 7 u 8 dígitos. Si no, MUST rechazarse con código `DNI_INVALIDO`. Un paciente sin DNI argentino no reserva por el link: lo hace llamando al consultorio (DD-37).

#### Scenario: DNI con puntos
- **WHEN** se crea un paciente con DNI `9.000.001`
- **THEN** el paciente queda con DNI `9000001`

#### Scenario: DNI con letras
- **WHEN** se crea un paciente con DNI `90A00001`
- **THEN** se rechaza con código `DNI_INVALIDO`

#### Scenario: DNI de largo inválido
- **WHEN** se crea un paciente con DNI `123456` o `123456789`
- **THEN** se rechaza con código `DNI_INVALIDO`

### Requirement: DNI único por consultorio
Dentro de un consultorio, dos pacientes MUST NOT tener el mismo DNI, y la base MUST impedirlo. El mismo DNI MAY existir en consultorios distintos. El teléfono MAY repetirse, por ejemplo en familiares que comparten el número.

#### Scenario: DNI repetido en el mismo consultorio
- **GIVEN** un paciente del consultorio A con DNI `90000001`
- **WHEN** se inserta otro paciente del consultorio A con el mismo DNI
- **THEN** la base rechaza la fila

#### Scenario: Mismo DNI en otro consultorio
- **GIVEN** un paciente del consultorio A con DNI `90000001`
- **WHEN** se inserta un paciente del consultorio B con el mismo DNI
- **THEN** la base lo acepta

#### Scenario: Teléfono compartido
- **GIVEN** un paciente del consultorio A con teléfono `+5491100000001`
- **WHEN** se inserta otro paciente del consultorio A con otro DNI y el mismo teléfono
- **THEN** la base lo acepta

### Requirement: DNI existente con otros datos
Cuando llegan datos para un DNI que ya existe en el consultorio, el paciente existente MUST reutilizarse sin sobrescribir sus datos. Si el nombre o el apellido difieren (sin distinguir mayúsculas, acentos ni espacios en los extremos: se comparan en NFD sin diacríticos, la misma regla de C-15 D7), el paciente MUST quedar marcado para revisión. El límite de turnos futuros se cuenta por paciente, o sea por DNI (DD-24).

#### Scenario: Mismo DNI con otro nombre
- **GIVEN** el paciente "Ana Prueba" con DNI `90000001`
- **WHEN** llegan los datos "Ana María Prueba" con el mismo DNI
- **THEN** se reutiliza el mismo paciente, su nombre sigue siendo "Ana" y queda con revisión pendiente

#### Scenario: Mismo DNI con los mismos datos
- **GIVEN** el paciente "Ana Prueba" con DNI `90000001`
- **WHEN** llegan los datos "ana prueba" con el mismo DNI
- **THEN** se reutiliza el mismo paciente y no queda con revisión pendiente

#### Scenario: Mismo DNI con acentos distintos
- **GIVEN** el paciente "José Pérez" con DNI `90000001`
- **WHEN** llegan los datos "jose perez" con el mismo DNI
- **THEN** se reutiliza el mismo paciente y no queda con revisión pendiente

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
- **WHEN** se lo marca para revisión con lo informado en la reserva
- **THEN** el paciente queda con revisión pendiente, sus datos no cambian y guarda lo informado

### Requirement: Datos informados en la reserva
Mientras un paciente tiene la revisión pendiente, SHALL guardar lo escrito en la reserva que la disparó: nombre y apellido (de 1 a 80 caracteres), email opcional, el instante y el turno de origen, si lo hay (DD-38). Si llega otra reserva antes de revisar, MUST quedar la última. Sin revisión pendiente, las cinco columnas MUST ser `NULL`, y la base MUST impedirlo (minimización, Ley 25.326). El turno de origen MUST ser del mismo consultorio que el paciente.

#### Scenario: Marcar con lo informado
- **GIVEN** el paciente "Ana Prueba" con DNI `90000001`
- **WHEN** se lo marca para revisión con lo informado "Anna Prueba"
- **THEN** su nombre sigue siendo "Ana" y lo informado es "Anna Prueba"

#### Scenario: Otra reserva antes de revisar
- **GIVEN** un paciente con revisión pendiente y lo informado "Anna Prueba"
- **WHEN** se lo vuelve a marcar con lo informado "Anita Prueba"
- **THEN** lo informado es "Anita Prueba"

#### Scenario: Datos informados sin revisión pendiente
- **WHEN** se guarda un paciente sin revisión pendiente y con alguna de las columnas de lo informado
- **THEN** la base rechaza la fila por `ck_paciente_informado`

#### Scenario: Turno de origen de otro consultorio
- **GIVEN** un paciente del consultorio A con revisión pendiente
- **WHEN** se guarda como turno de origen un turno del consultorio B
- **THEN** la base rechaza la fila por `fk_paciente_informado_turno`

### Requirement: Los pacientes no se borran
La aplicación MUST NOT poder borrar pacientes de la base (RN-AG-07).

#### Scenario: Borrado con el rol de la aplicación
- **WHEN** el rol de la aplicación intenta un `DELETE` sobre un paciente
- **THEN** la base lo rechaza por falta de permiso

### Requirement: Datos del paciente fuera de los logs y errores
El nombre, el DNI, el teléfono y el email de un paciente MUST NOT aparecer en los mensajes de error ni en la representación textual del paciente (RN-GL-01).

#### Scenario: Representación textual
- **WHEN** se convierte un paciente a texto (por ejemplo, al loguearlo)
- **THEN** el resultado no contiene su nombre, DNI, teléfono ni email
