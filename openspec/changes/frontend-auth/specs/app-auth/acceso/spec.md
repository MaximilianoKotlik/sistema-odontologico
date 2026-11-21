# Spec Delta

## Purpose

Cómo entra y sale el odontólogo de la app: login, guardas de las rutas privadas, restauración de la sesión al recargar, vuelta a la ruta pedida, cierre de sesión y coordinación del refresh entre pestañas (US-002, FL-01, RN-AU-02, RN-AU-03).

## ADDED Requirements

### Requirement: Rutas privadas con sesión
Toda ruta `/app/*` MUST mostrarse solo con una sesión válida. Sin sesión, la app SHALL llevar a `/login?volver=<ruta pedida>` sin mostrar el contenido privado ni por un instante.

#### Scenario: Link directo sin sesión
- **GIVEN** un navegador sin cookie de refresh
- **WHEN** se abre `/app/configuracion`
- **THEN** la app reemplaza la URL por `/login?volver=/app/configuracion` y no muestra el shell

### Requirement: Restauración de sesión al recargar
Al abrir una ruta privada sin token en memoria, la app SHALL pedir `POST /api/v1/auth/refresh` mientras muestra "Abriendo tu agenda…" con `aria-busy`. Con 200, MUST guardar el token en memoria y mostrar la ruta pedida. Con 401, MUST ir al login sin mensaje de error.

#### Scenario: Recargar con sesión
- **GIVEN** una sesión iniciada en el navegador
- **WHEN** el odontólogo recarga `/app/agenda`
- **THEN** ve "Abriendo tu agenda…" y después la agenda, sin pasar por el login

#### Scenario: Recargar con la sesión vencida
- **WHEN** el refresh responde 401 `SESION_EXPIRADA`
- **THEN** la app va a `/login?volver=/app/agenda` sin aviso de error

### Requirement: Vuelta solo a rutas internas
Después del login, la app SHALL volver a la ruta de `volver` solo si es una ruta interna que empieza con `/app/`. Cualquier otro valor (otro dominio, `//`, `javascript:`, rutas públicas) MUST descartarse y llevar a `/app`.

#### Scenario: Volver a una ruta privada
- **WHEN** el odontólogo ingresa desde `/login?volver=/app/configuracion/horarios`
- **THEN** la app abre `/app/configuracion/horarios`

#### Scenario: Redirección abierta
- **WHEN** el odontólogo ingresa desde `/login?volver=https://otro.example.com`
- **THEN** la app abre `/app` y no navega a otro sitio

### Requirement: Login
El login SHALL enviar email y contraseña a `POST /api/v1/auth/login` y, con 200, MUST guardar el access token solo en memoria (con la función de sesión de C-06, que antes borra la agenda guardada) y entrar a la app.

#### Scenario: Credenciales válidas
- **WHEN** el odontólogo ingresa con su email y contraseña
- **THEN** entra a la app y el token no aparece en `localStorage`, `sessionStorage`, IndexedDB, cookies legibles ni en la URL

### Requirement: Errores del login sin enumeración
Ante 401 `CREDENCIALES_INVALIDAS`, el login SHALL mostrar "Email o contraseña incorrectos." sin marcar un campo puntual, MUST vaciar la contraseña y conservar el email. Ante 423 `CUENTA_BLOQUEADA`, MUST mostrar los minutos restantes que devuelve el backend y un link para restablecer la contraseña.

#### Scenario: Contraseña incorrecta
- **WHEN** el login responde 401 `CREDENCIALES_INVALIDAS`
- **THEN** se ve el aviso genérico, la contraseña queda vacía y el email sigue cargado

#### Scenario: Cuenta bloqueada
- **WHEN** el login responde 423 con 12 minutos restantes
- **THEN** se ve "Pausamos el ingreso por seguridad" con "12 minutos" y el link "Restablecé tu contraseña"

### Requirement: Límite de intentos
Ante 429 `DEMASIADAS_SOLICITUDES` en cualquier pantalla de auth, la app SHALL mostrar cuánto esperar, calculado desde `Retry-After`, y MUST deshabilitar el envío hasta entonces.

#### Scenario: Demasiados intentos
- **WHEN** una pantalla de auth recibe 429 con `Retry-After: 60`
- **THEN** se ve "Esperá 1 minuto y volvé a intentar" y el botón queda deshabilitado

### Requirement: Avisos al llegar al login
El login SHALL mostrar un aviso según por qué se llegó: sesión vencida, sesión cerrada, contraseña cambiada o registro sin login automático, sin confirmar nunca si un email tiene cuenta.

#### Scenario: Sesión vencida en uso
- **WHEN** un pedido privado no puede renovar la sesión y la app lleva al login
- **THEN** el login dice "Tu sesión venció" y, después de ingresar, vuelve a la ruta donde estaba

### Requirement: Sin conexión
Sin conexión, el login SHALL avisar que necesita red y MUST deshabilitar el envío.

#### Scenario: Offline
- **WHEN** el dispositivo está sin conexión en `/login`
- **THEN** se ve el aviso y "Ingresar" está deshabilitado

### Requirement: Cerrar sesión
"Cerrar sesión" SHALL llamar a `POST /api/v1/auth/logout`, borrar el token de memoria y la agenda guardada, y llevar a `/login` con el aviso de sesión cerrada, aunque el logout falle por falta de red.

#### Scenario: Cerrar sesión
- **WHEN** el odontólogo toca "Cerrar sesión"
- **THEN** llega a `/login` con "Cerraste sesión" y el botón atrás no vuelve a mostrar contenido privado

### Requirement: Usuario con sesión en rutas de ingreso
Con una sesión válida, abrir `/login` o `/registro` SHALL llevar a `/app`.

#### Scenario: Login con sesión
- **GIVEN** una sesión iniciada
- **WHEN** el odontólogo abre `/login`
- **THEN** la app abre `/app`
