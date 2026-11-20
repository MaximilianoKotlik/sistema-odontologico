# Proposal

> Change **C-05** de [CHANGES.md](../../../CHANGES.md) · Governance **CRITICO** (dominio `auth`, RN-AU-06) · Depende de `C-03` (`auditoria-base`) y `C-04` (`auth-sesion-tokens`) · Responsable: Mati · Aprobación: Maxi.
>
> **Se apoya en propuestas, no en código.** De C-04 usa su propuesta mergeada (PR #8): tablas `usuario` (`intentos_fallidos`, `bloqueado_hasta`, `password_cambiado_at`, `credenciales_version`) y `refresh_token`, los casos de uso `RegistrarOdontologo` e `IniciarSesion`, los puertos `HasheadorDeContrasena` y `GeneradorDeSecretos`, y `PoliticaDeContrasena`. De C-03 usa su propuesta (PR #10, aprobada): la persistencia de eventos en la misma transacción. De C-02 usa el puerto `PublicadorDeAuditoria`. Si alguna de esas piezas cambia al implementarse, esta propuesta se ajusta (tarea 0.1).

## Why

C-04 deja un login que funciona pero que **no se puede exponer en público**: no hay freno contra la fuerza bruta, quien olvida la contraseña pierde el acceso a su agenda y nada de lo que pasa en `auth` queda auditado. C-05 cierra esos huecos (RN-AU-03..05, RN-AD-04/05, US-002, US-003 y US-005). Es la condición para desplegar la autenticación y para que C-07 (Nacho) arme las pantallas de login completas.

## What Changes

- **Bloqueo por fallos** (RN-AU-03, US-002):
  - Tras 5 contraseñas incorrectas seguidas, la cuenta queda bloqueada 15 minutos.
  - Mientras dura el bloqueo, el login responde 423 `CUENTA_BLOQUEADA`, aun con la contraseña correcta. El mensaje es genérico y la respuesta incluye los minutos restantes.
  - Un login exitoso pone el contador en cero.
- **Recuperación de contraseña** (RN-AU-04/05, US-003):
  - Migración nueva: `token_recuperacion`, que guarda solo el hash, vence a los 30 minutos y se usa una sola vez.
  - `POST /api/v1/auth/recuperacion` responde siempre 202, idéntica exista o no el email. Si existe, se envía un email por SMTP con el link (Mailpit en local, DD-15/DD-19).
  - `POST /api/v1/auth/recuperacion/confirmar` responde 204. Con un token usado, vencido o desconocido devuelve 400 `TOKEN_INVALIDO`, y con una contraseña nueva débil, 400 `PASSWORD_DEBIL`.
  - Restablecer la contraseña revoca **todos** los refresh, incrementa la versión de credenciales (invalida los access tokens emitidos) y desbloquea la cuenta.
- **Cambio de contraseña con sesión iniciada** (RN-AU-01/05, US-005):
  - `POST /api/v1/auth/cambiar-password` recibe la contraseña actual y la nueva, y responde 204.
  - Revoca todos los refresh, incluido el de la sesión actual, e incrementa la versión de credenciales.
  - Errores: 400 `PASSWORD_ACTUAL_INVALIDA` y 400 `PASSWORD_DEBIL`.
- **Rate limiting** (Bucket4j en memoria, `08` §Seguridad): límites por IP, por email o por usuario sobre `/api/v1/auth/**`. Al superarlos se responde 429 `DEMASIADAS_SOLICITUDES` con `Retry-After`.
- **Eventos de auditoría de auth** (RN-AD-04/05) vía el puerto de C-02, persistidos por C-03 en la misma transacción:
  - Eventos: `CUENTA_CREADA`, `LOGIN_EXITOSO`, `LOGIN_FALLIDO`, `PASSWORD_CAMBIADA` y `PASSWORD_RECUPERADA`.
  - Nunca incluyen contraseñas, hashes ni tokens.
  - `LOGIN_FALLIDO` queda registrado aunque la respuesta sea 401 o 423.
- **Tests**: US-002 (bloqueo), US-003, US-005, rate limit, y eventos persistidos en la misma transacción y sin secretos.
- **Fuera de alcance**:
  - Pantallas de login, recuperación y cambio de contraseña: van en C-07.
  - Rate limit compartido entre instancias: backlog, para cuando se escale horizontalmente.
  - 2FA (DD-18), verificación de email (PA-09) y CAPTCHA.
  - Limpieza periódica de tokens vencidos: backlog, junto con la de refresh de C-04.

## Capabilities

### New Capabilities
- `recuperacion-contrasena`: restablecer la contraseña por email con un token de un solo uso, sin revelar si la cuenta existe.

### Modified Capabilities
- `sesion-odontologo` (de C-04): se agregan el bloqueo tras 5 fallos, el cambio de contraseña con sesión iniciada y la auditoría de los logins.
- `registro-odontologo` (de C-04): se agrega el evento `CUENTA_CREADA`.
- `seguridad-http` (de C-04): se agrega el límite de solicitudes sobre las rutas de autenticación.

## Impact

- **Código**:
  - `backend/.../auth/**`: casos de uso nuevos `SolicitarRecuperacion`, `ConfirmarRecuperacion` y `CambiarContrasena`; ajustes en `IniciarSesion` y `RegistrarOdontologo` de C-04.
  - Filtro de rate limit.
  - Adaptador SMTP.
  - Una migración nueva, numerada al hacer rebase.
- **Dependencias nuevas**:
  - `spring-boot-starter-mail`.
  - `com.bucket4j:bucket4j_jdk17-core` y `com.github.ben-manes.caffeine:caffeine`, para guardar los buckets con vencimiento.
- **API**:
  - 3 endpoints nuevos.
  - `POST /auth/login` suma 423 `CUENTA_BLOQUEADA`.
  - Todo `/auth/**` suma 429 `DEMASIADAS_SOLICITUDES`.
  - Se regenera `api-types.ts`.
- **Configuración**: usa `SMTP_*`, `MAIL_FROM` y `APP_FRONTEND_ORIGIN` de `08` §Variables de entorno. Agrega los umbrales de rate limit con valores por defecto (`app.rate-limit.*`), sin variables obligatorias nuevas.
- **Knowledge base**:
  - FL-01 (`07`) nombra el evento `LOGIN_OK` y CHANGES.md `LOGIN_EXITOSO`: se usa `LOGIN_EXITOSO` y se corrige `07`.
  - CHANGES.md nombra la ruta `confirmar-recuperacion` y FL-01 `/recuperacion/confirmar`: se usa la de FL-01 y se corrige CHANGES.md.
- **Preguntas abiertas**:
  - **PA-09** (verificación de email): se mantiene la decisión por defecto, "no en v1".
  - **PA-02** (proveedor SMTP de producción): no bloquea, porque el SMTP es configurable (DD-19).
  - No toca ninguna pregunta Alta.
- **Frontend (C-07)**: el link del email lleva el token en el fragmento (`/recuperar#token=…`); C-07 lo tiene que leer de ahí (design D5).
