# Design

## Context

- Ver `proposal.md` §Why. Dominio `auth`: governance **CRITICO** (RN-AU-06). No se escribe código de producción antes de que Maxi apruebe esta propuesta y de que C-03 y C-04 estén en `[x]`.
- Estado al escribir la propuesta (2026-11-20): en `main` estaba mergeado C-01 y las **propuestas** de C-02 (PR #4) y C-04 (PR #8); la de C-03 estaba aprobada (PR #10).
- Alineado con el código mergeado (tarea 0.1, 2026-11-20): C-02 (PR #12), C-03 (PR #21) y C-04 (PR #16) están en `main`. Lo que cambió respecto de la propuesta está marcado como **Ajuste 0.1** en cada decisión.
- Piezas que se consumen:

  | Pieza | De | Uso en C-05 |
  |---|---|---|
  | `usuario.intentos_fallidos`, `bloqueado_hasta`, `password_cambiado_at`, `credenciales_version` | C-04 D3/D12 | Bloqueo (D1), invalidación de access tokens (D6) |
  | `refresh_token` y su revocación masiva por usuario | C-04 D4 | Revocar todas las sesiones (D6) |
  | `GestionDeSesion.iniciar` (el login), `RegistrarOdontologo` | C-04 D13 | Se modifican para el bloqueo y la auditoría (D2, D9) |
  | `CredencialesDeUsuario`, `UsuarioRepositorio` (JDBC) | C-04 | El login lee la cuenta; C-05 agrega `Usuario` con el estado de acceso |
  | `RefreshTokenRepositorio.revocarTodos` | C-04 D4 | Revocar todas las sesiones (D6) |
  | `HasheadorDeContrasena`, `GeneradorDeSecretos`, `PoliticaDeContrasena` | C-04 D13 | Contraseña nueva y token de recuperación |
  | Filtro JWT, `TenantContext`, Problem Details, `ErrorDeNegocio` | C-02 / C-04 | Cambio de contraseña autenticado; códigos de error nuevos |
  | `PublicadorDeAuditoria`, `EventoDeAuditoria` | C-02 D10 | Eventos de auth (D9) |
  | Persistencia en la misma transacción (con flush previo) y conversores de PII en mensaje y stack trace | C-03 D-3/D-6 | Atomicidad de los eventos (incluido `CUENTA_CREADA`, que referencia el consultorio recién creado con JPA); logs sin email ni token |
  | Placeholder de Flyway `${app-user}` | C-03 D-1 | Permisos de `token_recuperacion` (D3) |
  | `GeneradorDeIds`, `Clock` | C-02 | Ids y vencimientos |

  La tarea 0.1 contrasta esta tabla con el código mergeado.

## Goals / Non-Goals

**Goals:**
- Que nada de lo que hace C-05 abra un oráculo nuevo para saber si una cuenta existe, salvo los que la KB exige explícitamente (ver D1 y Riesgos).
- Reusar las operaciones de C-04 (revocar refresh, subir la versión de credenciales) en vez de duplicarlas.
- Que los eventos de auth queden en la misma transacción que el cambio, incluido el login fallido, que responde con error.

**Non-Goals:**
- Pantallas (C-07). CAPTCHA, 2FA, verificación de email.
- Rate limit distribuido entre instancias (`08` §Seguridad lo deja para cuando se escale).
- Notificar al usuario por email cuando su cuenta se bloquea o cambia la contraseña. Sería útil, pero ninguna regla de la KB lo pide; queda como backlog.

## Decisions

### D1 Bloqueo con el estado de `usuario` y `SELECT … FOR UPDATE`
- El login (`GestionDeSesion.iniciar`) lee al usuario por email con lock de fila (`UsuarioRepositorio.buscarPorEmailParaActualizar`). Dos logins fallidos simultáneos no pueden contar como uno solo.
- **Ajuste 0.1**: C-04 no tenía una clase `Usuario`, solo `CredencialesDeUsuario` (lo que el login necesitaba). C-05 agrega el record inmutable `auth.domain.Usuario` con el estado de acceso y deja `CredencialesDeUsuario` para el registro.
- **Ajuste al implementar**: PostgreSQL guarda microsegundos y **redondea** lo que sobra. Un `bloqueado_hasta` calculado con nanosegundos podía quedar guardado hasta medio microsegundo después y responder "16 minutos". Los repositorios truncan los instantes a microsegundos al escribir.
- **Ajuste al implementar**: la búsqueda por email casteaba mal. Con el parámetro como `varchar`, PostgreSQL compara la columna `CITEXT` como `text` y distingue mayúsculas, así que el login con `Odontologa@Example.com` fallaba también en C-04. Las búsquedas usan `CAST(:email AS citext)`.
- Reglas puras en `auth.domain.Usuario`: `estaBloqueado(ahora)`, `registrarFallo(ahora)` y `registrarExito()`.
  - Con `bloqueado_hasta > ahora` se responde 423 **sin** evaluar BCrypt (evita gastar CPU en un login que igual se rechaza) y sin tocar el contador: los intentos durante el bloqueo no lo extienden.
  - Con contraseña incorrecta, `intentos_fallidos + 1`. Al llegar a 5 se fija `bloqueado_hasta = ahora + 15 min` y el contador vuelve a 0.
  - Con login exitoso, `intentos_fallidos = 0`.
- La respuesta 423 incluye `minutosRestantes`, redondeado hacia arriba (FL-01), con el mensaje genérico de RN-AU-03.
- *Trade-off aceptado*: el 423 revela que el email tiene cuenta, porque un email inexistente nunca llega a 423. Lo exigen US-002 y FL-01. Se acota con el rate limit por IP (D8).
- *Riesgo aceptado*: un atacante puede bloquear la cuenta de un odontólogo con 5 intentos cada 15 minutos. Se mitiga con el rate limit por IP y con la recuperación, que desbloquea (D6).

### D2 El login fallido confirma su transacción
- Un `LOGIN_FALLIDO` y el contador tienen que persistir aunque la respuesta sea 401 o 423. Si el caso de uso lanzara una excepción dentro de `@Transactional`, el rollback se los llevaría.
- `GestionDeSesion.iniciar` devuelve un resultado cerrado (`sealed interface ResultadoLogin` con `Exitoso`, `CredencialesInvalidas` y `Bloqueada`) y la transacción confirma siempre. `auth.web` lo traduce a 200, 401 o 423. Es el mismo patrón que C-04 ya usaba para la renovación del refresh.
- *Alternativa descartada*: registrar el fallo en una transacción `REQUIRES_NEW` y después lanzar la excepción. Son dos transacciones para un solo hecho, y es más fácil que el contador y el evento diverjan.
- Se modifica `GestionDeSesion.iniciar` de C-04 (Fran revisa).

### D3 Token de recuperación
- Tabla `token_recuperacion`:
  - `id`, `consultorio_id`, `usuario_id`, `token_hash CHAR(64) UNIQUE`, `expira_at`, `usado_at NULL`, `revocado_at NULL`, `created_at`.
  - FK compuesta `(consultorio_id, usuario_id) → usuario (consultorio_id, id)` e índice parcial `(consultorio_id, usuario_id) WHERE usado_at IS NULL AND revocado_at IS NULL` (**ajuste 0.1**: empieza por `consultorio_id`, regla 4 de `CONVENCIONES.md`). Migración `V4__token_recuperacion.sql` (tarea 0.2).
  - `GRANT SELECT, INSERT, UPDATE` y `REVOKE DELETE, TRUNCATE` al rol de la aplicación, por los privilegios por defecto de C-01. El rol se nombra con el placeholder de Flyway `"${app-user}"` (`DB_APP_USER`), la convención que fijó C-03 (D-1), y no con `app_rw` fijo.
- **Emisión**: 32 bytes de `GeneradorDeSecretos` en base64url. Se guarda el SHA-256 hex. En la misma transacción se marcan `revocado_at` los tokens anteriores no usados del usuario, así hay como máximo un token vigente por cuenta.
- **Confirmación**:
  1. `SELECT … FOR UPDATE` por hash.
  2. Si no existe, está usado o revocado, o `expira_at <= ahora` → 400 `TOKEN_INVALIDO`, siempre con el mismo cuerpo.
  3. Recién después se valida la política de la contraseña nueva: si es débil, 400 `PASSWORD_DEBIL` **sin** marcar el token como usado.
  4. Si todo es válido: `usado_at = ahora`, la operación de D6 y el evento `PASSWORD_RECUPERADA`.
- El orden token → política evita revelar si la contraseña es válida a quien no tiene un token vigente.

### D4 Solicitud: 202 inmediato y email después del commit
- `SolicitarRecuperacion` busca por email.
  - Si no existe, termina sin hacer nada más.
  - Si existe, emite el token (D3) y publica un evento interno `RecuperacionSolicitada` con el token en claro, solo en memoria.
- Un `@TransactionalEventListener(phase = AFTER_COMMIT)` con `@Async` sobre un executor acotado (2 hilos y cola de 100) envía el email por el puerto `EnviadorDeEmails`. **La respuesta no espera al SMTP**: el tiempo de un email existente no se distingue del de uno inexistente por el envío.
- Si el envío falla, se loguea en `WARN` con el usuario y el motivo. El conversor de C-03 enmascara email y token. No se reintenta: el odontólogo puede pedir otra recuperación.
- *Diferencia de tiempo residual*: un email existente hace un `INSERT` más. Son milisegundos frente a la variación de red; se acepta.
- *Alternativa descartada*: enviar el email dentro del request. Revela si la cuenta existe por el tiempo de respuesta y acopla el 202 a la disponibilidad del SMTP.

### D5 Link y contenido del email
- Link: `${APP_FRONTEND_ORIGIN}/recuperar#token=<token>`. Con el token en el **fragmento**, nunca llega a los logs de un servidor ni de un proxy. Además ya rige `Referrer-Policy: no-referrer` (C-04).
- C-07 (Nacho) lee el token del fragmento y lo manda en el body de `POST /auth/recuperacion/confirmar`. Se avisa en el PR.
- Email en texto plano (sin HTML en v1: menos superficie), en español rioplatense, remitente `MAIL_FROM`. Asunto: "Restablecé tu contraseña". El cuerpo dice que el link vence en 30 minutos y que, si no lo pediste, podés ignorarlo.
- Adaptador `EnviadorDeEmailsSmtp` con `JavaMailSender` (`spring-boot-starter-mail`), configurado con `SMTP_*`.

### D6 Una sola operación de "nueva contraseña"
- `Usuario.establecerContrasena(hashNuevo, ahora)`: guarda el hash, sube `credenciales_version`, fija `password_cambiado_at` y limpia `intentos_fallidos` y `bloqueado_hasta`.
- Después, `RefreshTokenRepositorio.revocarTodos(consultorioId, usuarioId, ahora)` (la operación de reuso de C-04 D4).
- La usan `ConfirmarRecuperacion` y `CambiarContrasena`. Subir la versión invalida los access tokens emitidos por el validador de C-04 D3.

### D7 Cambio de contraseña
- `POST /api/v1/auth/cambiar-password` exige JWT. El consultorio sale del `TenantContext` y el usuario del `sub`.
- Se verifica la contraseña actual con BCrypt (400 `PASSWORD_ACTUAL_INVALIDA`), después la política (400 `PASSWORD_DEBIL`), y después D6 y el evento `PASSWORD_CAMBIADA`. Responde 204 y borra la cookie de refresh, porque esa sesión también queda revocada (US-005).
- Una contraseña actual incorrecta **no** suma al contador de bloqueo del login. Quien la escribe mal en un formulario no debería quedar afuera de su cuenta. Contra alguien que robó un access token, ya hay un límite por usuario (D8) y el token vence en 15 minutos.
- Ruta `/api/v1/auth/cambiar-password`: queda bajo el `Path` de la cookie de refresh, que así viaja y se puede borrar desde ahí. **Ajuste 0.1**: C-04 no deja públicas todas las rutas de `auth`, sino solo las que lista (`ConfiguracionDeSeguridad.RUTAS_DE_SESION`). Por eso `cambiar-password` exige JWT sin ninguna excepción, y lo que se agrega a la lista son las dos rutas públicas de la recuperación (test "Sin sesión" de la spec).

### D8 Rate limiting con Bucket4j en dos niveles
- **Por IP, en un filtro** (`LimiteDeSolicitudesFilter`): va en la cadena de Spring Security, antes de la autenticación, solo para `/api/v1/auth/**`. Responde 429 en Problem Details con `Retry-After`. Al cortar antes del caso de uso, el 429 no cuenta como intento fallido.
- **Por email o por usuario, en la aplicación**: un puerto `LimitadorDeSolicitudes` que usan `SolicitarRecuperacion` (por email) y `CambiarContrasena` (por usuario). Así el filtro no tiene que leer el body. La clave por email cuenta igual exista o no la cuenta, para que el 429 tampoco revele nada.
- Buckets en Caffeine con `expireAfterAccess(1h)` y un máximo de 100.000 claves, para acotar la memoria.
- Valores por defecto (configurables en `app.rate-limit.*`):

  | Ruta | Clave | Límite |
  |---|---|---|
  | `POST /auth/login` | IP | 10 por minuto |
  | `POST /auth/registro` | IP | 5 por hora |
  | `POST /auth/refresh` | IP | 30 por minuto |
  | `POST /auth/recuperacion` | IP | 5 por hora |
  | `POST /auth/recuperacion` | email normalizado | 3 por hora |
  | `POST /auth/recuperacion/confirmar` | IP | 10 por hora |
  | `POST /auth/cambiar-password` | usuario | 5 cada 15 minutos |

- **IP de origen**: `request.getRemoteAddr()`. `server.forward-headers-strategy` queda en `none` salvo detrás de un proxy de confianza configurado al desplegar (PA-02). En local, detrás del proxy de Vite, todas las solicitudes comparten IP: se acepta en desarrollo y los tests usan IPs simuladas.
- *Alternativa descartada*: Resilience4j RateLimiter. No tiene buckets por clave sin código adicional. Bucket4j es lo que fija `08`.

### D9 Catálogo de eventos de auth
Todos con `entidad = "usuario"`, `entidadId = usuario.id`, el consultorio del usuario y la IP de la petición (`OrigenDeSolicitud`, que arma `auth.web` y pasa al caso de uso). Las claves de los valores no chocan con la lista prohibida de C-02 (`password`, `password_hash`, `token`, `*_token`, `*_hash`).

| Acción | Actor | `valorNuevo` | Dónde |
|---|---|---|---|
| `CUENTA_CREADA` | `ODONTOLOGO`, id del usuario | `{slug, plan, prueba_hasta}` | `RegistrarOdontologo` (C-04, se modifica) |
| `LOGIN_EXITOSO` | `ODONTOLOGO`, id del usuario | — | `GestionDeSesion.iniciar` |
| `LOGIN_FALLIDO` | `ODONTOLOGO`, **sin id** (no autenticado) | `{intentos_fallidos, bloqueado_hasta}` | `GestionDeSesion.iniciar` |
| `PASSWORD_CAMBIADA` | `ODONTOLOGO`, id del usuario | `{credenciales_version}` | `CambiarContrasena` |
| `PASSWORD_RECUPERADA` | `ODONTOLOGO`, id del usuario | `{credenciales_version}` | `ConfirmarRecuperacion` |

- Un login con email inexistente **no** genera evento: `evento_auditoria.consultorio_id` es `NOT NULL` y no hay a quién asociarlo. Queda en el log (email enmascarado) y lo frena el rate limit.
- Un `LOGIN_FALLIDO` durante el bloqueo se registra con el contador sin cambios. Así el consultorio ve en su historial (C-18) los intentos contra su cuenta.
- No se auditan `refresh` ni `logout`: RN-AD-04 no los pide y generarían mucho ruido.
- Cada caso de uso tiene un test que verifica el evento persistido y que ningún valor contiene contraseña, hash ni token.

### D10 Estructura
- `auth.domain`: `Usuario` (reglas de D1 y D6) y `TokenDeRecuperacion` (vigente, usado, revocado y vencido, como funciones puras).
- `auth.application`: `SolicitarRecuperacion`, `ConfirmarRecuperacion`, `CambiarContrasena` y `NuevaContrasena` (la operación de D6); cambios en `GestionDeSesion` y `RegistrarOdontologo`; puertos `EnviadorDeEmails`, `LimitadorDeSolicitudes` y `TokenDeRecuperacionRepositorio`; `LimitesPorCuenta` (límites por email y por usuario de D8) y la excepción `LimiteExcedido`.
- `auth.infrastructure`: JPA de `token_recuperacion`, `EnviadorDeEmailsSmtp`, `LimitadorBucket4j` y `LimiteDeSolicitudesFilter`.
- `auth.web`: tres endpoints nuevos, el mapeo de `ResultadoLogin` y los códigos nuevos `CUENTA_BLOQUEADA` (423), `TOKEN_INVALIDO` (400), `PASSWORD_ACTUAL_INVALIDA` (400) y `DEMASIADAS_SOLICITUDES` (429).
- Excepciones a la regla ArchUnit de tenant, que se agregan a la lista de C-04 D9 (`ArchitectureTest.REPOSITORIOS_EXCEPTUADOS`):
  - `TokenDeRecuperacionRepositorio.buscarPorHashParaActualizar(hash)`: la confirmación llega sin sesión y el hash de 256 bits es la credencial.
  - `UsuarioRepositorio.buscarPorEmailParaActualizar(email)`: la misma búsqueda del login que C-04 ya exceptuaba (`buscarPorEmail`), con lock de fila (D1).
- **Ajuste 0.1**: el origen del link (D5) sale de `PropiedadesDeAuth.frontendOrigin()` de C-04, que ya valida y normaliza `APP_FRONTEND_ORIGIN`. El remitente sale de `app.mail.from` (`MAIL_FROM`).
- **Ajuste 0.1**: el filtro de D8 vive en `auth.infrastructure.seguridad` y va antes que el `FiltroDeOrigenDeSesion` de C-04, así un 429 corta antes que todo. El 429 de los límites por cuenta lo traduce `auth.web.ManejadorDeLimites`.

### D11 Estrategia de tests (Strict TDD)
- **Unitarios**:
  - Reglas de `Usuario`: 5 fallos, bloqueo, no extensión, reinicio y `establecerContrasena`.
  - `TokenDeRecuperacion`.
  - Mapeo de `ResultadoLogin` a HTTP.
- **Integración** (sobre `IntegracionPostgresTest`, con `Clock` fijo y avanzable):
  - Todos los escenarios de las specs, incluidos los eventos persistidos y sin secretos.
- **Email**: los tests de casos de uso reemplazan `EnviadorDeEmails` por un fake que registra los envíos. El adaptador SMTP se prueba con un contenedor de Mailpit (`GenericContainer` de Testcontainers, sin dependencia nueva), consultando su API HTTP.
- **Rate limit**: con IPs simuladas (`MockHttpServletRequest.setRemoteAddr`) y límites bajos en el perfil `test`.
- Datos ficticios evidentes (`@example.com`).

## Risks / Trade-offs

- [El 423 revela que la cuenta existe] → Lo exige US-002. Se acota con el rate limit por IP (D1, D8).
- [Bloqueo malicioso de una cuenta ajena] → El rate limit por IP lo encarece y la recuperación desbloquea (D6).
- [Rate limit en memoria: se pierde al reiniciar y no se comparte entre instancias] → Alcanza con una instancia (`08`). Pasar a almacenamiento compartido si se escala.
- [Sin un proxy de confianza configurado, la IP es la del proxy] → `forward-headers-strategy` se define al desplegar (PA-02). Se documenta en el README del backend.
- [Se modifican casos de uso de C-04] → Se hace después de que C-04 esté mergeado, con Fran como revisor técnico.
- [Email en texto plano con un link: un cliente de correo puede precargarlo] → El link solo abre una pantalla. El token se consume recién al confirmar con la contraseña nueva (POST), así que la precarga no lo gasta.
- [Tokens de recuperación vencidos acumulados] → Volumen bajo e índice parcial. La limpieza queda en backlog, junto con la de refresh.

## Migration Plan

- Migración nueva, aditiva (`token_recuperacion`). El número se fija al hacer rebase, después de C-03 y C-04. Rollback = no mergear. Una vez mergeada, los ajustes van en una migración nueva (regla dura 5).
- Al mergear C-05 queda habilitado desplegar `auth` en un entorno público, que era la condición de C-04.

## Open Questions

- Los límites exactos de D8 son valores iniciales razonables. Se pueden ajustar por configuración después del piloto, sin cambiar las specs.

## UI Mockups

- Design system revision: 1
- Decision: no (no UI) — 2026-11-20. Change solo de backend; las pantallas de recuperación y cambio de contraseña son de C-07.
