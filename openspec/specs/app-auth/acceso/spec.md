# app-auth/acceso Specification

## Purpose
Cómo entra y sale el odontólogo de la app: login, guardas de las rutas privadas, restauración de la sesión al recargar, vuelta a la ruta pedida, cierre de sesión (también en las otras pestañas) y coordinación del refresh entre pestañas (US-002, FL-01, RN-AU-02, RN-AU-03).

## Requirements

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

#### Scenario: Ruta que sale de /app al normalizarse
- **WHEN** el odontólogo ingresa desde `/login?volver=/app/%2e%2e/login` o `/login?volver=/app/../login`
- **THEN** la app abre `/app`, porque la ruta normalizada es `/login` y no empieza con `/app/`

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
- **THEN** se ve "Pausamos el ingreso por seguridad" con "12 minutos" y el link "restablecela"

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
Al navegar dentro de la app con una sesión cargada en la pestaña (access token en memoria), ir a `/login` o `/registro` SHALL llevar a `/app`. Al abrir esas rutas con una carga completa de la página, la app MUST NOT pedir `POST /api/v1/auth/refresh` y SHALL mostrar el formulario.

#### Scenario: Login con sesión, navegando dentro de la app
- **GIVEN** una sesión iniciada en la pestaña
- **WHEN** el odontólogo navega a `/login` sin recargar
- **THEN** la app abre `/app`

#### Scenario: Login abierto desde cero
- **GIVEN** un navegador con cookie de refresh y la pestaña recién cargada en `/login`
- **WHEN** se pinta la página
- **THEN** se ve el formulario de login y no se hace ningún `POST /api/v1/auth/refresh`

### Requirement: Aviso de sesión cerrada entre pestañas
Cuando una pestaña cierra la sesión o termina un cambio de contraseña, la app SHALL publicarlo en un `BroadcastChannel` llamado `muelas-auth`. El mensaje MUST llevar solo el tipo `sesion-cerrada` y el motivo (`logout` o `contrasena-cambiada`), y MUST NOT llevar el token ni datos del usuario. Sin `BroadcastChannel`, la app MUST seguir funcionando sin el aviso.

#### Scenario: Contenido del aviso
- **WHEN** el odontólogo cierra sesión
- **THEN** se publica en `muelas-auth` exactamente `{ tipo: 'sesion-cerrada', motivo: 'logout' }`, sin token ni email

#### Scenario: Navegador sin BroadcastChannel
- **GIVEN** un navegador sin `BroadcastChannel`
- **WHEN** el odontólogo cierra sesión
- **THEN** la pestaña llega a `/login` con "Cerraste sesión" sin errores en la consola

### Requirement: Cierre de sesión en todas las pestañas
Cada pestaña que recibe el aviso `sesion-cerrada` SHALL borrar su token de memoria, desmontar el shell, borrar la caché `agenda-v1` e ir a `/login` con el aviso de su motivo.

#### Scenario: Cerrar sesión con dos pestañas abiertas
- **GIVEN** la agenda abierta en dos pestañas
- **WHEN** el odontólogo toca "Cerrar sesión" en una
- **THEN** la otra pestaña deja de mostrar la agenda y llega a `/login` con "Cerraste sesión"

#### Scenario: Cambiar la contraseña con dos pestañas abiertas
- **GIVEN** la agenda abierta en una pestaña y `/app/cuenta/contrasena` en otra
- **WHEN** el cambio de contraseña responde 204
- **THEN** las dos pestañas llegan a `/login` con "Cambiaste tu contraseña."

#### Scenario: Mensaje con otro formato
- **WHEN** llega por `muelas-auth` un mensaje que no es `{ tipo: 'sesion-cerrada', motivo }` con un motivo conocido
- **THEN** la pestaña lo ignora y sigue con su sesión
