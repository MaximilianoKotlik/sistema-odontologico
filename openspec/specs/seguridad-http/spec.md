# seguridad-http Specification

## Purpose
Protege la superficie HTTP de la API: solo las rutas explícitamente públicas se pueden usar sin sesión, y todas las respuestas llevan cabeceras de seguridad y una política de origen restringida al frontend propio.

## Requirements

### Requirement: Rutas privadas por defecto
Toda ruta bajo `/api/v1/` MUST exigir un access token válido, salvo las declaradas públicas: registro, login, refresh y logout de `/api/v1/auth`, y todo `/api/v1/public/**`. El health check y el contrato OpenAPI MUST ser públicos; Swagger UI, solo con el perfil `local`.

#### Scenario: Sin token
- **WHEN** se llama a una ruta privada sin cabecera `Authorization`
- **THEN** la respuesta es 401 en Problem Details con código `NO_AUTENTICADO`

#### Scenario: Token vencido, mal firmado o malformado
- **WHEN** se llama a una ruta privada con un token vencido, firmado con otra clave o con un algoritmo distinto de HS256
- **THEN** la respuesta es 401 con código `NO_AUTENTICADO` y el cuerpo no explica cuál fue la causa

#### Scenario: Ruta pública sin token
- **WHEN** se llama a login sin `Authorization`
- **THEN** la petición se procesa normalmente

#### Scenario: API pública del paciente sin token
- **WHEN** se llama sin `Authorization` a una ruta bajo `/api/v1/public/`
- **THEN** la seguridad no la rechaza con 401 (si la ruta no existe, la respuesta es 404 `RECURSO_NO_ENCONTRADO`)

#### Scenario: Rutas técnicas sin token
- **WHEN** se llama sin `Authorization` a `/actuator/health` o a `/v3/api-docs`
- **THEN** la respuesta es 200

#### Scenario: Swagger UI solo en local
- **WHEN** se pide `/swagger-ui.html` sin `Authorization` con el perfil `local`
- **THEN** la página se sirve, y con el perfil por defecto no está disponible

### Requirement: Cabeceras de seguridad
Toda respuesta de la API MUST incluir `Content-Security-Policy` con `default-src 'self'`, `X-Content-Type-Options: nosniff` y `Referrer-Policy: no-referrer`; fuera del perfil `local` MUST incluir también `Strict-Transport-Security`.

#### Scenario: Cabeceras presentes
- **WHEN** se hace cualquier petición a la API con el perfil por defecto
- **THEN** la respuesta incluye las cuatro cabeceras con esos valores

#### Scenario: Sin HSTS en local
- **WHEN** se hace una petición con el perfil `local`
- **THEN** la respuesta no incluye `Strict-Transport-Security`

### Requirement: CORS restringido al frontend
La API MUST aceptar peticiones de otro origen solo desde el origen configurado del frontend, con credenciales permitidas; cualquier otro origen MUST quedar sin cabeceras CORS de permiso.

#### Scenario: Origen del frontend
- **GIVEN** el origen configurado es `http://localhost:5173`
- **WHEN** llega un preflight desde `http://localhost:5173`
- **THEN** la respuesta permite ese origen y `Access-Control-Allow-Credentials: true`

#### Scenario: Origen ajeno
- **WHEN** llega un preflight desde `https://otro.example.com`
- **THEN** la respuesta no incluye `Access-Control-Allow-Origin`

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
