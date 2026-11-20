# Spec Delta

## Purpose

Protege la superficie HTTP de la API: solo las rutas explícitamente públicas se pueden usar sin sesión, y todas las respuestas llevan cabeceras de seguridad y una política de origen restringida al frontend propio.

## ADDED Requirements

### Requirement: Rutas privadas por defecto
Toda ruta bajo `/api/v1/` MUST exigir un access token válido, salvo las declaradas públicas: registro, login, refresh y logout de `/api/v1/auth`, la documentación OpenAPI y el health check.

#### Scenario: Sin token
- **WHEN** se llama a una ruta privada sin cabecera `Authorization`
- **THEN** la respuesta es 401 en Problem Details con código `NO_AUTENTICADO`

#### Scenario: Token vencido, mal firmado o malformado
- **WHEN** se llama a una ruta privada con un token vencido, firmado con otra clave o con un algoritmo distinto de HS256
- **THEN** la respuesta es 401 con código `NO_AUTENTICADO` y el cuerpo no explica cuál fue la causa

#### Scenario: Ruta pública sin token
- **WHEN** se llama a login sin `Authorization`
- **THEN** la petición se procesa normalmente

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
