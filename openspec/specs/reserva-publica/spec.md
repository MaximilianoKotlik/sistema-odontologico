# reserva-publica Specification

## Purpose

Permite que un paciente sin cuenta vea la página pública de un consultorio, consulte los horarios libres y reserve un turno que nace `RESERVADO` con `origen = LINK`, recibiendo una sola vez el link para gestionarlo (US-030, FL-04, RN-RP-01 a RN-RP-11).

## Requirements

### Requirement: Página pública del consultorio
`GET /api/v1/public/consultorios/{slug}` SHALL devolver, sin sesión, el nombre, la dirección, el teléfono de contacto, la zona horaria, `tomaTurnosOnline` y las prestaciones activas y visibles al público (id, nombre y duración), ordenadas por su orden configurado. MUST NOT devolver datos de configuración interna, del plan ni del odontólogo.

#### Scenario: Consultorio que toma turnos
- **GIVEN** el consultorio `demo` con la reserva pública activa, plan `PRUEBA` y las prestaciones Consulta (visible) y Control (no visible)
- **WHEN** se pide `GET /api/v1/public/consultorios/demo`
- **THEN** la respuesta es 200 con `tomaTurnosOnline: true`, nombre, dirección, teléfono, zona horaria y solo la prestación Consulta

#### Scenario: Prestación desactivada
- **GIVEN** una prestación visible pero desactivada
- **WHEN** se pide la página pública
- **THEN** esa prestación no aparece

#### Scenario: Slug inexistente
- **WHEN** se pide un slug que no corresponde a ningún consultorio
- **THEN** la respuesta es 404 `RECURSO_NO_ENCONTRADO`

### Requirement: Consultorio que no toma turnos online
Si el consultorio desactivó la reserva pública o su plan está `VENCIDO`, la página pública MUST responder 200 con `tomaTurnosOnline: false`, el nombre, la dirección y el teléfono, y sin prestaciones (RN-RP-11).

#### Scenario: Reserva desactivada
- **GIVEN** un consultorio con `reserva_publica_activa = false`
- **WHEN** se pide su página pública
- **THEN** la respuesta es 200 con `tomaTurnosOnline: false`, su teléfono y la lista de prestaciones vacía

#### Scenario: Plan vencido
- **GIVEN** un consultorio con plan `VENCIDO` y la reserva pública activa
- **WHEN** se pide su página pública
- **THEN** la respuesta es 200 con `tomaTurnosOnline: false`

### Requirement: Consulta de huecos ofrecibles
`GET /api/v1/public/consultorios/{slug}/huecos?prestacionId&desde&hasta` SHALL devolver cada fecha local del rango, con `atiende` (si hay disponibilidad ese día de la semana) y los inicios ofrecibles para la duración de la prestación según RN-RP-01. Los inicios se devuelven como instantes UTC.

#### Scenario: Huecos de un día con disponibilidad
- **GIVEN** disponibilidad el lunes de 09:00 a 10:00, paso de 15 minutos y un turno activo de 09:00 a 09:30
- **WHEN** se piden los huecos de una prestación de 30 minutos para ese lunes
- **THEN** la fecha figura con `atiende: true` y los huecos 09:30 (en hora del consultorio, expresado en UTC)

#### Scenario: Día sin disponibilidad
- **GIVEN** un consultorio que no atiende los domingos
- **WHEN** el rango incluye un domingo
- **THEN** esa fecha figura con `atiende: false` y sin huecos

#### Scenario: Sobreturno ocupa el hueco
- **GIVEN** un sobreturno activo de 10:00 a 10:30
- **WHEN** se piden los huecos de ese día
- **THEN** ningún hueco ofrecido intersecta 10:00–10:30

#### Scenario: Anticipación y horizonte
- **GIVEN** anticipación de reserva de 2 h y horizonte de 60 días
- **WHEN** se piden huecos para hoy y para dentro de 61 días
- **THEN** no se ofrece ningún hueco que empiece antes de `ahora + 2 h` ni después de `ahora + 60 días`

### Requirement: Validación de la consulta de huecos
La consulta MUST rechazar con 400 `VALIDACION` un rango con `hasta` anterior a `desde` o de más de 31 días. Una prestación inexistente, de otro consultorio, inactiva o no visible MUST responder 404 `RECURSO_NO_ENCONTRADO`.

#### Scenario: Rango demasiado largo
- **WHEN** se piden huecos con un rango de 40 días
- **THEN** la respuesta es 400 `VALIDACION`

#### Scenario: Prestación de otro consultorio
- **GIVEN** una prestación del consultorio B
- **WHEN** se piden huecos en la página del consultorio A con esa prestación
- **THEN** la respuesta es 404 `RECURSO_NO_ENCONTRADO`

### Requirement: Reserva y huecos deshabilitados sin reserva online
Si el consultorio no toma turnos online, la consulta de huecos y la reserva MUST responder 409 `RESERVA_NO_DISPONIBLE` con el teléfono de contacto, sin crear nada.

#### Scenario: Reservar con la reserva desactivada
- **GIVEN** un consultorio con la reserva pública desactivada
- **WHEN** se envía una reserva válida
- **THEN** la respuesta es 409 `RESERVA_NO_DISPONIBLE` con el teléfono del consultorio y no se crea paciente, turno ni evento

### Requirement: Datos obligatorios de la reserva
`POST /api/v1/public/consultorios/{slug}/turnos` MUST exigir prestación, inicio, nombre, apellido, DNI, teléfono celular argentino y `aceptaPrivacidad: true`; el email es opcional. Cualquier dato faltante o inválido MUST responder 400 con `VALIDACION`, `DNI_INVALIDO` o `TELEFONO_INVALIDO` según corresponda, sin crear nada. MUST NOT pedirse ningún dato de salud.

#### Scenario: Sin aceptar la privacidad
- **WHEN** se envía una reserva con `aceptaPrivacidad: false`
- **THEN** la respuesta es 400 `VALIDACION` con un error para `aceptaPrivacidad`

#### Scenario: DNI inválido
- **WHEN** se envía el DNI `12.34`
- **THEN** la respuesta es 400 `DNI_INVALIDO`

#### Scenario: Teléfono inválido
- **WHEN** se envía el teléfono `123`
- **THEN** la respuesta es 400 `TELEFONO_INVALIDO`

### Requirement: Alta del turno reservado
Una reserva válida SHALL crear, en una sola transacción, el turno `RESERVADO` con `origen = LINK`, la duración copiada de la prestación, y el consentimiento del paciente con la fecha y la versión vigente de la política de privacidad. La respuesta MUST ser 201 con el turno (id, inicio, fin, prestación, estado) y el link de gestión.

#### Scenario: Reserva exitosa
- **GIVEN** una prestación de 30 minutos y un hueco libre ofrecible
- **WHEN** se envía nombre, apellido, DNI, teléfono y `aceptaPrivacidad: true`
- **THEN** la respuesta es 201 con un turno `RESERVADO` de 30 minutos y `origen = LINK`, y un `linkGestion` de la forma `/t/{token}`
- **AND** el paciente queda con `consentimiento_version` igual a la versión vigente de la política

#### Scenario: Hueco no ofrecible
- **GIVEN** un horario que empieza a menos de la anticipación de reserva, fuera de la disponibilidad, fuera del paso o dentro de un bloqueo
- **WHEN** se intenta reservarlo
- **THEN** la respuesta es 409 `HUECO_NO_DISPONIBLE` y no se crea nada

### Requirement: Token de gestión entregado una sola vez
Al reservar, el sistema SHALL emitir un token aleatorio de 256 bits en base64url y devolverlo en claro solo en la respuesta de la reserva. MUST persistir únicamente su hash SHA-256, y ninguna otra respuesta, log ni evento de auditoría MUST contener el token en claro.

#### Scenario: Solo el hash en la base
- **WHEN** se completa una reserva
- **THEN** `token_gestion_turno` guarda un hash de 64 caracteres hex que coincide con el SHA-256 del token devuelto, y el token en claro no está en la base

#### Scenario: Tokens distintos
- **WHEN** se hacen dos reservas
- **THEN** los tokens devueltos son distintos y cada uno decodifica a 32 bytes

### Requirement: Deduplicación del paciente por DNI
El DNI SHALL normalizarse a solo dígitos (7 u 8) y ser la clave del paciente dentro del consultorio (DD-24). Si ya existe, se reutiliza el paciente sin sobrescribir sus datos; si el nombre o el apellido difieren (sin distinguir mayúsculas, tildes ni espacios extra), el paciente MUST quedar con `requiere_revision = true`.

#### Scenario: DNI con puntos ya registrado
- **GIVEN** un paciente con DNI `30000001` en el consultorio
- **WHEN** se reserva con el DNI `30.000.001` y el mismo nombre
- **THEN** el turno queda asociado a ese paciente, no se crea otro y no se marca revisión

#### Scenario: Mismo DNI con otro nombre
- **GIVEN** un paciente con DNI `30000001` llamado `Paciente Uno Prueba`
- **WHEN** se reserva con ese DNI y el nombre `Otra Persona`
- **THEN** se reutiliza el paciente, su nombre no cambia y queda `requiere_revision = true`

#### Scenario: Mismo DNI en otro consultorio
- **GIVEN** un paciente con DNI `30000001` en el consultorio B
- **WHEN** se reserva con ese DNI en el consultorio A
- **THEN** se crea un paciente nuevo en A y el de B no cambia

### Requirement: Límite de turnos futuros por paciente
Un paciente (identificado por DNI) MUST tener como máximo 2 turnos futuros activos con `origen = LINK` por consultorio. Una reserva que lo supere MUST responder 409 `LIMITE_TURNOS` sin crear el turno (RN-RP-08).

#### Scenario: Tercer turno
- **GIVEN** un paciente con 2 turnos futuros `RESERVADO` reservados por link
- **WHEN** reserva un tercero
- **THEN** la respuesta es 409 `LIMITE_TURNOS`

#### Scenario: Turnos que no cuentan
- **GIVEN** un paciente con un turno futuro cancelado, un turno pasado y un turno futuro cargado a mano por el odontólogo
- **WHEN** reserva por link
- **THEN** la reserva se crea

#### Scenario: Reservas simultáneas del mismo DNI
- **GIVEN** un paciente con 1 turno futuro por link
- **WHEN** llegan a la vez dos reservas del mismo DNI para horarios distintos
- **THEN** prospera exactamente una y la otra recibe 409 `LIMITE_TURNOS`

### Requirement: Reserva concurrente del mismo hueco
Si dos reservas compiten por el mismo hueco, MUST prosperar exactamente una; la otra MUST recibir 409 `HUECO_NO_DISPONIBLE` con los huecos actualizados de esa fecha en la propiedad `huecos` (RN-RP-07).

#### Scenario: Dos pacientes al mismo tiempo
- **GIVEN** un hueco libre a las 10:00
- **WHEN** dos pacientes distintos lo reservan en paralelo
- **THEN** una respuesta es 201 y la otra es 409 `HUECO_NO_DISPONIBLE` con una lista de huecos que ya no incluye las 10:00
- **AND** existe un solo turno activo a las 10:00

### Requirement: Marca de novedad para el odontólogo
Reservar, reprogramar o cancelar por link MUST registrar en el turno, en la misma transacción, el instante del cambio como novedad pendiente de ver por el odontólogo (DD-30). La marca la limpia el odontólogo desde la agenda.

#### Scenario: Reserva nueva marcada
- **WHEN** un paciente reserva por link
- **THEN** el turno queda con la novedad registrada en el instante de la reserva

#### Scenario: Cancelación por link sobre un turno ya visto
- **GIVEN** un turno cuya novedad el odontólogo ya marcó como vista
- **WHEN** el paciente lo cancela por link
- **THEN** el turno vuelve a tener una novedad pendiente

### Requirement: Auditoría de la reserva
La reserva MUST registrar, en su misma transacción y con `actor_tipo = PACIENTE` y la IP de origen, `TURNO_CREADO` y, si corresponde, `PACIENTE_CREADO` y `PACIENTE_MARCADO_PARA_REVISION`. Los eventos MUST NOT contener el token, el DNI, el teléfono, el email ni el nombre del paciente; lo identifican por `paciente_id`.

#### Scenario: Eventos de una reserva de paciente nuevo
- **WHEN** un paciente nuevo reserva
- **THEN** quedan los eventos `PACIENTE_CREADO` y `TURNO_CREADO` con actor `PACIENTE`, el `actor_id` del paciente y sin datos personales ni token

#### Scenario: Falla la auditoría
- **WHEN** la escritura del evento falla
- **THEN** la reserva se revierte y no queda turno, paciente ni token

### Requirement: Email de confirmación opcional
Si el paciente informó un email y hay SMTP configurado, el sistema SHALL enviarle la confirmación del turno con el link de gestión recién después de confirmar la transacción. Un fallo del envío MUST NOT revertir la reserva ni cambiar la respuesta.

#### Scenario: Reserva con email
- **GIVEN** SMTP configurado
- **WHEN** un paciente reserva con email `paciente1@example.com`
- **THEN** ese email recibe la fecha, el consultorio y el link `/t/{token}`

#### Scenario: SMTP caído
- **GIVEN** el servidor SMTP no responde
- **WHEN** un paciente reserva con email
- **THEN** la respuesta es igualmente 201 y el turno queda creado

### Requirement: Aislamiento por consultorio en la API pública
El consultorio de toda operación pública MUST resolverse solo desde el slug de la ruta; cualquier id de consultorio en el body o la query MUST ignorarse. Un recurso de otro consultorio MUST responder como inexistente.

#### Scenario: Body con otro consultorio
- **WHEN** se reserva en el slug de A con un body que incluye `consultorioId` de B
- **THEN** el turno se crea en A y B no cambia
