# Spec Delta

## ADDED Requirements

### Requirement: Límite de solicitudes en la API pública
Las rutas de `/api/v1/public/**` MUST limitar las solicitudes por IP (reservas por hora y consultas por minuto) y las operaciones de modificación por token de gestión (por hora), con valores configurables (por defecto 10 reservas por hora, 60 consultas por minuto y 10 operaciones por token por hora). Al superarlo, la respuesta MUST ser 429 `DEMASIADAS_SOLICITUDES` con `Retry-After`, sin ejecutar la operación.

#### Scenario: Ráfaga de reservas desde una IP
- **WHEN** una misma IP supera las 10 reservas en una hora
- **THEN** la siguiente reserva responde 429 `DEMASIADAS_SOLICITUDES` con `Retry-After` y no crea nada

#### Scenario: Ráfaga de consultas de huecos
- **WHEN** una misma IP supera las 60 consultas de huecos en un minuto
- **THEN** la siguiente consulta responde 429

#### Scenario: Operaciones repetidas con un token
- **WHEN** se superan las 10 reprogramaciones o cancelaciones con el mismo token en una hora, aun desde IPs distintas
- **THEN** la siguiente responde 429

#### Scenario: Tokens inexistentes desde una IP
- **WHEN** una IP prueba muchos tokens inexistentes
- **THEN** después del límite por IP recibe 429, y antes de eso cada intento recibe el mismo 404

### Requirement: Tamaño máximo del cuerpo en la API pública
Las solicitudes a `/api/v1/public/**` con un cuerpo de más de 64 KB MUST rechazarse con 413 `CUERPO_DEMASIADO_GRANDE` en Problem Details, sin ejecutar la operación.

#### Scenario: Cuerpo enorme
- **WHEN** se envía una reserva con un cuerpo de 100 KB
- **THEN** la respuesta es 413 `CUERPO_DEMASIADO_GRANDE` y no se crea nada

### Requirement: Tokens de gestión fuera de los logs
Los logs de la aplicación y de acceso MUST NOT contener el token de gestión en claro. Las rutas `/api/v1/public/turnos/{token}/**` MUST registrarse con el token enmascarado.

#### Scenario: Log de una vista por token
- **WHEN** se pide `GET /api/v1/public/turnos/{token}`
- **THEN** ninguna línea de log contiene el token, y la ruta aparece con el token enmascarado
