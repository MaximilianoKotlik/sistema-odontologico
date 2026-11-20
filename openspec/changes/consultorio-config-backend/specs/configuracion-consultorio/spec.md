# Spec Delta

## Purpose

Permite que el odontólogo consulte y edite los datos de su consultorio y las reglas con las que se toman reservas (US-004, FL-02 paso 1), y deja auditado cada cambio (RN-AD-04).

## ADDED Requirements

### Requirement: Consulta de la configuración
El odontólogo SHALL poder obtener la configuración de su consultorio: nombre, slug, dirección, teléfono de contacto, zona horaria, anticipación de cancelación y de reserva, horizonte de reserva, paso de huecos, `reserva_publica_activa`, plan y `prueba_hasta`.

#### Scenario: Consulta con sesión
- **GIVEN** un consultorio recién registrado
- **WHEN** su odontólogo pide la configuración
- **THEN** la respuesta es 200 con sus datos y los valores por defecto (24 h de cancelación, 2 h de reserva, 60 días de horizonte, paso de 15 min, zona `America/Argentina/Buenos_Aires`)

### Requirement: Edición de la configuración
El odontólogo SHALL poder reemplazar los campos editables de su consultorio (nombre, slug, dirección, teléfono de contacto, zona horaria, anticipaciones, horizonte, paso de huecos y `reserva_publica_activa`). El plan y `prueba_hasta` MUST NOT poder modificarse por esta vía.

#### Scenario: Cambio válido
- **WHEN** el odontólogo guarda la anticipación de cancelación en 2880 min y el paso de huecos en 30
- **THEN** la respuesta es 200 con los valores nuevos y una nueva consulta los devuelve

#### Scenario: Intento de cambiar el plan
- **WHEN** el body incluye `plan: ACTIVO` o `pruebaHasta`
- **THEN** esos campos se ignoran y el plan no cambia

### Requirement: Validación de la configuración
Los valores MUST respetar los rangos del modelo: anticipación de cancelación entre 0 y 10080 min, anticipación de reserva de 0 o más, horizonte entre 1 y 365 días, paso de huecos en 5, 10, 15, 20 o 30, y nombre obligatorio de hasta 120 caracteres. Si algún valor no cumple, la respuesta MUST ser 400 `VALIDACION` con el detalle por campo.

#### Scenario: Paso de huecos no permitido
- **WHEN** se guarda el paso de huecos en 25
- **THEN** la respuesta es 400 con código `VALIDACION` y un error para `pasoHuecosMin`

### Requirement: Teléfono de contacto normalizado
El teléfono de contacto, cuando se informa, MUST guardarse normalizado a E.164; uno inválido MUST responder 400 `TELEFONO_INVALIDO`. Un valor vacío MUST quitar el teléfono.

#### Scenario: Teléfono local
- **WHEN** se guarda el teléfono `011 15-0000-0001`
- **THEN** queda guardado como `+5491100000001`

#### Scenario: Teléfono inválido
- **WHEN** se guarda el teléfono `123`
- **THEN** la respuesta es 400 con código `TELEFONO_INVALIDO`

### Requirement: Zona horaria válida
La zona horaria MUST ser un identificador IANA válido; si no lo es, la respuesta MUST ser 400 `ZONA_HORARIA_INVALIDA`.

#### Scenario: Zona inexistente
- **WHEN** se guarda la zona `America/Ciudad_Inventada`
- **THEN** la respuesta es 400 con código `ZONA_HORARIA_INVALIDA`

### Requirement: Auditoría del cambio de configuración
Todo cambio de configuración MUST registrar en la misma transacción un evento `CONSULTORIO_CONFIG_CAMBIADA` con el odontólogo como actor y solo los campos que cambiaron, con su valor anterior y nuevo. Un guardado sin cambios MUST NOT generar evento.

#### Scenario: Anticipación auditada
- **GIVEN** la anticipación de cancelación en 1440 min
- **WHEN** el odontólogo la cambia a 2880 min
- **THEN** queda un evento `CONSULTORIO_CONFIG_CAMBIADA` con valor anterior `{anticipacion_cancelacion_min: 1440}` y nuevo `{anticipacion_cancelacion_min: 2880}`

#### Scenario: Guardado idéntico
- **WHEN** el odontólogo guarda la configuración sin modificar ningún valor
- **THEN** la respuesta es 200 y no se registra ningún evento

### Requirement: Aislamiento de la configuración
La configuración que se lee y se edita MUST ser siempre la del consultorio de la sesión, y nunca la de un consultorio indicado en la petición.

#### Scenario: Body con otro consultorio
- **WHEN** el odontólogo del consultorio A envía un body con el id del consultorio B
- **THEN** solo cambia la configuración de A y la de B queda intacta
