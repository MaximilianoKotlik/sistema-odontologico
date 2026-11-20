# sesion-odontologo Specification

## Purpose
Gestiona la sesión del odontólogo con credenciales de vida corta y renovación segura (access token + refresh rotado con detección de reuso) y fija desde la sesión el consultorio activo de cada petición privada (US-002, RN-AU-02, RN-TE-01).

## Requirements

### Requirement: Login con credenciales válidas
Con email y contraseña correctos, el login SHALL responder 200 con un access token firmado de 15 minutos de vigencia y SHALL fijar una cookie de refresh opaca de 14 días.

#### Scenario: Login exitoso
- **GIVEN** una cuenta `odontologa@example.com` con contraseña válida
- **WHEN** hace login con esas credenciales
- **THEN** la respuesta es 200 con `accessToken` y `expiraEn` de 900 segundos
- **AND** el token identifica al usuario y a su consultorio y vence 15 minutos después del instante del reloj del sistema
- **AND** la respuesta fija la cookie de refresh

### Requirement: Fallo de login genérico
Un email inexistente o una contraseña incorrecta MUST responder 401 `CREDENCIALES_INVALIDAS` con el mismo cuerpo, sin revelar cuál de los dos falló, y con un tiempo de respuesta comparable.

#### Scenario: Contraseña incorrecta y email inexistente
- **WHEN** se compara el login con contraseña incorrecta y el login con un email no registrado
- **THEN** ambos responden 401 con código `CREDENCIALES_INVALIDAS` y el mismo `title` y `detail`

#### Scenario: Contraseña de más de 72 bytes en el login
- **WHEN** se hace login con una contraseña de más de 72 bytes, para un email existente o inexistente
- **THEN** la respuesta es 401 con código `CREDENCIALES_INVALIDAS`, nunca 500

### Requirement: Cookie de refresh protegida
La cookie de refresh MUST ser `HttpOnly`, `Secure`, `SameSite=Strict`, con `Path=/api/v1/auth` y `Max-Age` de 14 días; su valor MUST ser aleatorio de al menos 256 bits y la base MUST guardar solo su hash SHA-256.

#### Scenario: Atributos de la cookie
- **WHEN** un login es exitoso
- **THEN** el `Set-Cookie` de refresh incluye `HttpOnly`, `Secure`, `SameSite=Strict`, `Path=/api/v1/auth` y `Max-Age=1209600`

#### Scenario: Refresh solo como hash
- **WHEN** se emite un refresh
- **THEN** la base contiene su hash SHA-256 y no el valor enviado en la cookie

### Requirement: Rotación del refresh
Cada uso válido del refresh SHALL emitir un access token nuevo y un refresh nuevo, y MUST revocar el refresh usado.

#### Scenario: Refresh válido
- **GIVEN** un refresh vigente y no revocado
- **WHEN** se llama a refresh con esa cookie
- **THEN** la respuesta es 200 con un access token nuevo y una cookie con un refresh distinto
- **AND** el refresh anterior queda revocado

#### Scenario: Refresh vencido o desconocido
- **WHEN** se llama a refresh con una cookie vencida, desconocida o ausente
- **THEN** la respuesta es 401 con código `SESION_EXPIRADA` y la cookie se borra

### Requirement: Detección de reuso del refresh
Presentar un refresh ya revocado MUST revocar todos los refresh vigentes del mismo usuario y responder 401 `SESION_EXPIRADA`, obligando a un nuevo login en todos los dispositivos.

#### Scenario: Reuso de un refresh rotado
- **GIVEN** un usuario con sesiones en dos dispositivos y un refresh ya rotado
- **WHEN** alguien presenta el refresh ya rotado
- **THEN** la respuesta es 401 con código `SESION_EXPIRADA`
- **AND** los refresh de ambos dispositivos quedan revocados y ya no sirven para renovar

### Requirement: Logout
El logout SHALL revocar el refresh presentado, borrar la cookie y responder 204, aun si la cookie falta o ya no es válida.

#### Scenario: Logout con sesión vigente
- **WHEN** se hace logout con un refresh vigente
- **THEN** la respuesta es 204, la cookie se borra y ese refresh ya no sirve para renovar

#### Scenario: Logout sin cookie
- **WHEN** se hace logout sin cookie
- **THEN** la respuesta es 204

### Requirement: Refresh y logout solo desde el frontend propio
Como refresh y logout se autentican con una cookie, MUST rechazar con 403 `ORIGEN_NO_PERMITIDO` toda petición cuya cabecera `Origin` esté presente y sea distinta del origen configurado del frontend, sin rotar ni revocar nada.

#### Scenario: Refresh desde otro sitio
- **WHEN** llega un refresh con cookie válida y `Origin: https://otro.example.com`
- **THEN** la respuesta es 403 con código `ORIGEN_NO_PERMITIDO` y el refresh sigue vigente

### Requirement: El consultorio activo sale de la sesión
En cada petición privada con un access token válido, el sistema MUST establecer como consultorio activo el que figura en el token, y nunca uno que venga en el body, la query o una cabecera de la petición.

#### Scenario: Recurso de otro consultorio con token válido
- **GIVEN** un recurso del consultorio B y un access token válido del consultorio A
- **WHEN** se pide ese recurso con el token de A
- **THEN** la respuesta es 404 con código `RECURSO_NO_ENCONTRADO`

### Requirement: Invalidación por versión de credenciales
El access token MUST llevar la versión de credenciales del usuario; un token cuya versión no coincide con la vigente MUST rechazarse con 401 `NO_AUTENTICADO`.

#### Scenario: Credenciales cambiadas
- **GIVEN** un access token emitido con versión de credenciales 1
- **WHEN** la versión vigente del usuario pasa a 2 y se usa ese token
- **THEN** la respuesta es 401 con código `NO_AUTENTICADO`
