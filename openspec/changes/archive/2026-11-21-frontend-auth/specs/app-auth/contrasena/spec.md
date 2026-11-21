# Spec Delta

## Purpose

Recuperación de la contraseña por email y cambio con la sesión iniciada, sin revelar si un email tiene cuenta y cerrando todas las sesiones al terminar (US-003, US-005, RN-AU-04, RN-AU-05).

## ADDED Requirements

### Requirement: Pedido de recuperación sin enumeración
`/recuperar` SHALL enviar el email a `POST /api/v1/auth/recuperacion` y, con 202, MUST mostrar siempre el mismo mensaje ("Revisá tu email" y "Si hay una cuenta con {email}, te llega un link para crear una contraseña nueva."), exista o no la cuenta.

#### Scenario: Email sin cuenta
- **WHEN** se pide recuperar un email que no tiene cuenta
- **THEN** se ve el mismo mensaje que para un email registrado

### Requirement: Token de recuperación fuera de la URL
Al abrir `/recuperar#token=…`, la app SHALL leer el token del fragmento y MUST quitarlo de la URL y del historial antes de mostrar el formulario. El token MUST quedar solo en memoria hasta enviarlo.

#### Scenario: Abrir el link del email
- **WHEN** se abre `/recuperar#token=abc`
- **THEN** la barra de direcciones queda en `/recuperar` y el formulario de contraseña nueva está listo

### Requirement: Contraseña nueva desde el link
El formulario SHALL pedir la contraseña nueva dos veces, con los requisitos en vivo del registro, y MUST enviarla con el token a `POST /api/v1/auth/recuperacion/confirmar`. Con 204, MUST llevar a `/login` con el aviso de contraseña cambiada.

#### Scenario: Restablecer
- **WHEN** la confirmación responde 204
- **THEN** la app abre `/login` con "Cambiaste tu contraseña." y "Por seguridad cerramos la sesión en todos tus dispositivos. Ingresá con la contraseña nueva."

#### Scenario: Las contraseñas no coinciden
- **WHEN** las dos contraseñas son distintas
- **THEN** no se envía y la repetición queda marcada con "Las dos contraseñas no coinciden."

### Requirement: Link vencido o usado
Ante 400 `TOKEN_INVALIDO`, la pantalla SHALL mostrar un único mensaje, "Este link ya no sirve" (sin distinguir vencido, usado o revocado), con el botón "Pedir un link nuevo". Sin token en la URL, MUST mostrar el pedido de recuperación.

#### Scenario: Token usado
- **WHEN** la confirmación responde 400 `TOKEN_INVALIDO`
- **THEN** se ve "Este link ya no sirve" y "Pedir un link nuevo"

### Requirement: Cambio de contraseña con sesión
`/app/cuenta/contrasena` SHALL pedir la contraseña actual, la nueva y su repetición, y avisar antes de enviar que se cierra la sesión en todos los dispositivos, también en este. Con 204 de `POST /api/v1/auth/cambiar-password`, MUST borrar el token y la agenda guardada y llevar a `/login` con el aviso de contraseña cambiada.

#### Scenario: Cambio exitoso
- **WHEN** el cambio responde 204
- **THEN** el odontólogo llega a `/login` con "Cambiaste tu contraseña." y tiene que ingresar de nuevo

#### Scenario: Contraseña actual incorrecta
- **WHEN** el cambio responde 400 `PASSWORD_ACTUAL_INVALIDA`
- **THEN** el campo de la contraseña actual dice "La contraseña actual no es correcta" y se vacía

### Requirement: Contraseña débil
Ante 400 `PASSWORD_DEBIL` en el registro, la recuperación o el cambio, la pantalla SHALL marcar el campo de la contraseña nueva y el requisito que no se cumple. En la recuperación, MUST conservar el token para reintentar.

#### Scenario: Igual al email
- **WHEN** el cambio responde 400 `PASSWORD_DEBIL` porque la contraseña nueva es igual al email
- **THEN** "Distinta de tu email" queda en rojo con ✕ y el campo inválido
