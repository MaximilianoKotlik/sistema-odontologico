# Spec Delta

## ADDED Requirements

### Requirement: Bloqueo tras intentos fallidos
Tras 5 logins consecutivos con contraseña incorrecta para una misma cuenta, la cuenta MUST quedar bloqueada 15 minutos. Mientras dure el bloqueo, todo login a esa cuenta MUST responder 423 `CUENTA_BLOQUEADA` con un mensaje genérico y los minutos restantes, aun con la contraseña correcta.

#### Scenario: Sexto intento con la contraseña correcta
- **GIVEN** 5 logins fallidos seguidos de `odontologa@example.com`
- **WHEN** hace login por sexta vez con la contraseña correcta
- **THEN** la respuesta es 423 con código `CUENTA_BLOQUEADA` y los minutos restantes del bloqueo
- **AND** no se emite access token ni cookie de refresh

#### Scenario: Fin del bloqueo
- **GIVEN** una cuenta bloqueada hace 15 minutos según el reloj del sistema
- **WHEN** hace login con la contraseña correcta
- **THEN** la respuesta es 200

#### Scenario: Intentos durante el bloqueo no lo extienden
- **GIVEN** una cuenta bloqueada hace 10 minutos
- **WHEN** se intenta un login con contraseña incorrecta
- **THEN** la respuesta es 423 y el bloqueo sigue terminando 15 minutos después de haber empezado

### Requirement: Login exitoso reinicia el contador
Un login exitoso SHALL poner en cero el contador de intentos fallidos de la cuenta.

#### Scenario: Fallos no consecutivos
- **GIVEN** 4 logins fallidos, uno exitoso y luego 4 fallidos más
- **WHEN** se hace login con la contraseña correcta
- **THEN** la respuesta es 200

### Requirement: Auditoría de logins
Cada login exitoso SHALL registrar un evento `LOGIN_EXITOSO` y cada login con contraseña incorrecta o con la cuenta bloqueada SHALL registrar un evento `LOGIN_FALLIDO` del consultorio de esa cuenta, con la IP de origen. El evento de fallo MUST quedar registrado aunque la respuesta sea 401 o 423, y ningún evento MUST contener la contraseña, su hash ni tokens.

#### Scenario: Login fallido auditado
- **WHEN** una cuenta existente hace login con contraseña incorrecta
- **THEN** la respuesta es 401 y el registro de auditoría contiene un evento `LOGIN_FALLIDO` de esa cuenta con la cantidad de intentos fallidos

#### Scenario: Login exitoso auditado
- **WHEN** una cuenta hace login con éxito
- **THEN** el registro de auditoría contiene un evento `LOGIN_EXITOSO` de esa cuenta sin el access token ni el refresh

#### Scenario: Email inexistente
- **WHEN** se hace login con un email que no tiene cuenta
- **THEN** la respuesta es 401 `CREDENCIALES_INVALIDAS` y no se registra ningún evento de auditoría, porque no hay consultorio al cual asociarlo

### Requirement: Cambio de contraseña con sesión iniciada
Con un access token válido, la contraseña actual correcta y una nueva que cumpla la política, el cambio SHALL guardar la nueva contraseña con BCrypt, revocar todos los refresh del usuario (incluido el de la sesión actual), invalidar los access tokens emitidos, borrar la cookie de refresh y responder 204.

#### Scenario: Cambio exitoso
- **GIVEN** un odontólogo logueado en dos dispositivos
- **WHEN** cambia su contraseña con la actual correcta y una nueva válida
- **THEN** la respuesta es 204 y la cookie de refresh se borra
- **AND** el access token usado para el cambio y los refresh de ambos dispositivos dejan de valer
- **AND** el login funciona con la contraseña nueva

#### Scenario: Contraseña actual incorrecta
- **WHEN** intenta cambiarla con una contraseña actual incorrecta
- **THEN** la respuesta es 400 con código `PASSWORD_ACTUAL_INVALIDA` y nada cambia

#### Scenario: Contraseña nueva débil
- **WHEN** intenta cambiarla por una de 9 caracteres o igual a su email
- **THEN** la respuesta es 400 con código `PASSWORD_DEBIL` y nada cambia

#### Scenario: Sin sesión
- **WHEN** se intenta cambiar la contraseña sin access token
- **THEN** la respuesta es 401 con código `NO_AUTENTICADO`

### Requirement: Auditoría del cambio de contraseña
Un cambio de contraseña exitoso SHALL registrar un evento `PASSWORD_CAMBIADA` en la misma transacción, sin la contraseña ni su hash.

#### Scenario: Evento sin secretos
- **WHEN** el odontólogo cambia su contraseña con éxito
- **THEN** el registro de auditoría contiene un evento `PASSWORD_CAMBIADA` con el odontólogo como actor
- **AND** ningún valor del evento contiene la contraseña anterior, la nueva ni sus hashes
