# recuperacion-contrasena Specification

## Purpose

Permite que el odontólogo que olvidó su contraseña la restablezca por email con un token de un solo uso y de vida corta, sin que el sistema revele si una cuenta existe (US-003, RN-AU-04, RN-AU-05).

## Requirements

### Requirement: Solicitud de recuperación sin enumeración
La solicitud de recuperación SHALL responder 202 con el mismo cuerpo exista o no una cuenta con ese email, y el tiempo de respuesta MUST NOT depender del envío del email.

#### Scenario: Email registrado
- **GIVEN** una cuenta `odontologa@example.com`
- **WHEN** se solicita la recuperación para ese email
- **THEN** la respuesta es 202
- **AND** se envía a ese email un mensaje con un link de restablecimiento

#### Scenario: Email no registrado
- **WHEN** se solicita la recuperación para `nadie@example.com`, que no tiene cuenta
- **THEN** la respuesta es 202 con el mismo cuerpo que para un email registrado
- **AND** no se envía ningún email

#### Scenario: Falla el servidor de correo
- **GIVEN** una cuenta registrada y el servidor SMTP caído
- **WHEN** se solicita la recuperación
- **THEN** la respuesta igualmente es 202 y el fallo queda en el log sin el token ni el email en claro

### Requirement: Token de recuperación de un solo uso
Cada solicitud para una cuenta existente SHALL emitir un token aleatorio de al menos 256 bits, válido 30 minutos y de un solo uso. La base MUST guardar solo su hash SHA-256, y emitir uno nuevo MUST invalidar los anteriores no usados de esa cuenta.

#### Scenario: Token guardado como hash
- **WHEN** se emite un token de recuperación
- **THEN** la base contiene su hash SHA-256 y no el valor enviado por email

#### Scenario: Nueva solicitud invalida la anterior
- **GIVEN** un token de recuperación vigente sin usar
- **WHEN** se solicita otra recuperación para la misma cuenta y se confirma con el token anterior
- **THEN** la respuesta es 400 con código `TOKEN_INVALIDO`

### Requirement: Confirmación del restablecimiento
Con un token vigente y una contraseña nueva válida, la confirmación SHALL guardar la nueva contraseña con BCrypt, marcar el token como usado, revocar todos los refresh tokens del usuario, invalidar los access tokens emitidos, desbloquear la cuenta y responder 204.

#### Scenario: Restablecimiento exitoso
- **GIVEN** un token de recuperación vigente y un usuario con dos sesiones abiertas
- **WHEN** se confirma con ese token y una contraseña nueva válida
- **THEN** la respuesta es 204
- **AND** el login funciona con la contraseña nueva y no con la anterior
- **AND** los refresh de ambas sesiones ya no sirven para renovar y los access tokens emitidos antes responden 401 `NO_AUTENTICADO`

#### Scenario: Restablecer desbloquea la cuenta
- **GIVEN** una cuenta bloqueada por intentos fallidos y un token de recuperación vigente
- **WHEN** se confirma el restablecimiento
- **THEN** el login con la contraseña nueva responde 200 sin esperar el fin del bloqueo

### Requirement: Token inválido
Un token vencido, ya usado o desconocido MUST responder 400 `TOKEN_INVALIDO` con el mismo cuerpo en los tres casos y sin modificar la contraseña.

#### Scenario: Token vencido
- **GIVEN** un token emitido hace 31 minutos según el reloj del sistema
- **WHEN** se confirma con ese token
- **THEN** la respuesta es 400 con código `TOKEN_INVALIDO` y la contraseña no cambia

#### Scenario: Token ya usado
- **GIVEN** un token con el que ya se restableció la contraseña
- **WHEN** se confirma otra vez con ese token
- **THEN** la respuesta es 400 con código `TOKEN_INVALIDO`

#### Scenario: Token desconocido
- **WHEN** se confirma con un token que nunca se emitió
- **THEN** la respuesta es 400 con código `TOKEN_INVALIDO`

### Requirement: Contraseña nueva débil no consume el token
Si la contraseña nueva no cumple la política de contraseñas, la confirmación MUST responder 400 `PASSWORD_DEBIL` y el token MUST seguir vigente.

#### Scenario: Contraseña nueva de 9 caracteres
- **GIVEN** un token de recuperación vigente
- **WHEN** se confirma con una contraseña nueva de 9 caracteres
- **THEN** la respuesta es 400 con código `PASSWORD_DEBIL`
- **AND** una confirmación posterior con el mismo token y una contraseña válida responde 204

### Requirement: Auditoría del restablecimiento
Un restablecimiento exitoso SHALL registrar un evento `PASSWORD_RECUPERADA` del consultorio del usuario, en la misma transacción y sin la contraseña, su hash ni el token.

#### Scenario: Evento registrado sin secretos
- **WHEN** se restablece la contraseña con éxito
- **THEN** el registro de auditoría contiene un evento `PASSWORD_RECUPERADA` del usuario con la IP de origen
- **AND** ningún valor del evento contiene la contraseña, su hash ni el token
