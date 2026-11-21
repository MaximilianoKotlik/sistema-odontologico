# Spec Delta

## ADDED Requirements

### Requirement: Límite de solicitudes en autenticación
Las rutas de autenticación MUST limitar la cantidad de solicitudes por origen y por cuenta. Al superar un límite, la respuesta MUST ser 429 `DEMASIADAS_SOLICITUDES` en Problem Details, con la cabecera `Retry-After` en segundos, sin ejecutar la operación.

#### Scenario: Ráfaga de logins desde una IP
- **WHEN** una misma IP supera el límite de intentos de login por minuto
- **THEN** la siguiente solicitud responde 429 con código `DEMASIADAS_SOLICITUDES` y `Retry-After`
- **AND** esa solicitud no cuenta como intento fallido de ninguna cuenta

#### Scenario: Recuperación repetida para un mismo email
- **WHEN** se supera el límite de solicitudes de recuperación por hora para un mismo email, aun desde IPs distintas
- **THEN** la siguiente solicitud responde 429 y no se envía otro email

#### Scenario: Cambio de contraseña repetido
- **WHEN** un mismo usuario supera el límite de intentos de cambio de contraseña
- **THEN** la siguiente solicitud responde 429

#### Scenario: Recuperación del límite
- **GIVEN** una IP que recibió 429
- **WHEN** pasa el tiempo indicado en `Retry-After`
- **THEN** la siguiente solicitud se procesa normalmente

#### Scenario: Rutas fuera de autenticación
- **WHEN** se hacen muchas solicitudes a rutas privadas que no son de autenticación
- **THEN** este límite no se aplica
