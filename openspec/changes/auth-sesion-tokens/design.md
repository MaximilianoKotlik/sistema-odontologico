# Design

## Context

- Ver `proposal.md` §Why. Dominio `auth`: governance **CRITICO** (RN-AU-06). Ninguna línea de código de producción se escribe antes de que Maxi apruebe esta propuesta.
- C-04 se monta sobre C-01 (esqueleto, Spring Boot 3.3, roles `app_owner`/`app_rw`) y sobre **la propuesta de C-02 en el PR #4, todavía sin aprobar**. Usa estas piezas de C-02:

  | Pieza de C-02 (PR #4) | Uso en C-04 |
  |---|---|
  | Tabla `consultorio` (V1) y convención de FKs compuestas | Alta en el registro; FK desde `usuario` y `refresh_token` |
  | `ConsultorioId`, `TenantContext.establecer()` | El filtro JWT establece el tenant desde `cid` |
  | `GeneradorDeIds` (UUIDv7), `Clock` | Ids de usuario, consultorio y refresh; `iat`/`exp`, vencimientos, `prueba_hasta` |
  | `SugeridorDeSlug`, `ValidadorDeSlug` | Slug sugerido en el registro; la unicidad con sufijo se resuelve acá (D6) |
  | Problem Details + `ErrorDeNegocio` | Códigos nuevos: `EMAIL_EN_USO`, `PASSWORD_DEBIL`, `TERMINOS_NO_ACEPTADOS`, `CREDENCIALES_INVALIDAS`, `SESION_EXPIRADA`, `NO_AUTENTICADO`, `ORIGEN_NO_PERMITIDO` |
  | Reglas ArchUnit de repositorios con tenant | Dos excepciones documentadas (D9) |

  Si la revisión de C-02 cambia alguna de estas piezas, se actualiza esta tabla y las decisiones afectadas antes de implementar.
- El frontend que consume esto es C-07 (Nacho). El contrato llega por `api-types.ts`.

## Goals / Non-Goals

**Goals:**
- Escribir el mínimo código de seguridad propio: delegar la firma y validación de JWT, el hashing y la cadena de filtros en Spring Security.
- Que el `cid` del token sea **la única** fuente del tenant en las rutas privadas.
- Dejarle a C-05 puntos de extensión claros (`intentos_fallidos`, `bloqueado_hasta`, `credenciales_version`, auditoría) sin tener que reestructurar nada.

**Non-Goals:**
- Recuperación y cambio de contraseña, bloqueo por fallos, rate limiting y auditoría de auth: todo eso va en C-05. Hasta que C-05 esté mergeado, **C-04 no se despliega en un entorno público** (el login no tiene freno contra fuerza bruta).
- Limpieza periódica de refresh vencidos (backlog; ver Riesgos).
- Textos legales de los términos (PA-06).

## Decisions

### D1 — El registro responde 201 sin abrir sesión
- FL-01 deja abierto si después del registro se entra con login automático. Elegimos **no**: así hay un solo camino que emite tokens (el login) y menos superficie para auditar. C-07 llama a login inmediatamente después del registro. Para el usuario la diferencia es invisible.
- *Alternativa*: emitir tokens en el registro → duplica la emisión de tokens y su cookie, y obliga a probar dos caminos.

### D2 — JWT con Spring Security OAuth2 Resource Server (Nimbus), HS256
- **Validación**: `NimbusJwtDecoder.withSecretKey(key).macAlgorithm(HS256)` más validadores de `exp` (con el `Clock` de C-02) y de la versión de credenciales (D3). `BearerTokenAuthenticationFilter` hace la extracción estándar del `Authorization: Bearer`.
- **Emisión**: `NimbusJwtEncoder` con la misma clave, detrás del puerto `EmisorDeAccessToken` (en `auth.application`), así el dominio no conoce Nimbus.
- `JWT_SECRET` en base64 de ≥ 32 bytes. El arranque **falla** si la clave es más corta o falta (salvo en el perfil `test`, que usa una fija de prueba).
- *Alternativas*: `jjwt` + filtro propio (más código de seguridad escrito a mano para lo mismo); sesiones de servidor (DD-06 las descartó).

### D3 — `ver` = `usuario.credenciales_version`, verificado en cada petición
- Columna `credenciales_version INT NOT NULL DEFAULT 1`. C-05 la incrementa al cambiar o recuperar la contraseña, junto con `password_cambiado_at`.
- Un `OAuth2TokenValidator` busca la versión vigente con `(cid, sub)` (consulta por PK, con tenant) y rechaza si no coincide.
- *Costo*: una consulta indexada por request. Se acepta para v1 (una instancia, pocos usuarios por consultorio). Si molesta, se puede cachear con TTL corto, sin cambiar la spec.
- *Alternativa*: no verificar y esperar a que venza el access (hasta 15 min con credenciales viejas) → contradice la spec y el objetivo de RN-AU-05.

### D4 — Refresh opaco, rotación y detección de reuso
- **Valor**: 32 bytes de `SecureRandom` en base64url. En la base se guarda `token_hash CHAR(64)` (SHA-256 hex, `UNIQUE`). Se compara siempre por hash.
- **Rotación**: `SELECT … FOR UPDATE` sobre la fila del hash presentado.
  - Si está vigente: `revocado_at = now`, se inserta el nuevo y se emite access y cookie.
  - Si está revocado: se revocan todos los vigentes del usuario (`UPDATE … WHERE usuario_id = ? AND revocado_at IS NULL`) → 401 `SESION_EXPIRADA`.
  - Si no existe o está vencido: 401 `SESION_EXPIRADA`.
- **"Familia"** = todos los refresh del usuario (RN-AU-02 lo dice así). No hace falta una columna `familia_id`.
- El `FOR UPDATE` evita que dos rotaciones concurrentes del mismo token emitan dos refresh válidos.
- *Riesgo asumido*: dos pestañas que renuevan a la vez se ven como reuso y cierran todas las sesiones. Se mitiga en C-07 con un único refresh en vuelo compartido. Queda anotado en el PR de C-07.

### D5 — Login sin oráculo de enumeración
- Mismo 401 `CREDENCIALES_INVALIDAS` para email inexistente y contraseña incorrecta.
- Para igualar los tiempos, con un email inexistente se ejecuta igual un `BCrypt.matches` contra un hash ficticio precalculado de costo 12.
- El registro sí revela que un email existe (409 `EMAIL_EN_USO`): lo exige US-001 y es un trade-off conocido. El rate limit de C-05 lo acota.

### D6 — Slug único con sufijo y reintento
- `SugeridorDeSlug` (C-02) da la base. Se consultan los slugs `base` y `base-N` existentes y se elige el sufijo libre más bajo. Si con el sufijo se superan los 40 caracteres, se recorta la base.
- Para cubrir la carrera entre dos registros simultáneos: si el `INSERT` viola `uk_consultorio_slug`, se reintenta hasta 3 veces dentro del mismo caso de uso, cada vez en una transacción nueva. Si los 3 fallan, el error es 500.

### D7 — Términos versionados sin texto definitivo (PA-06)
- Columnas `terminos_version VARCHAR(40) NOT NULL` y `terminos_aceptados_at TIMESTAMPTZ NOT NULL` en `usuario`.
- La versión vigente sale de la configuración (`app.terminos.version-vigente`, variable `TERMINOS_VERSION_VIGENTE`, default `2026-11-borrador`). El cliente envía `aceptaTerminos: true` y el servidor registra la versión vigente, nunca una que mande el cliente.
- El texto de `/terminos` es `(TBD)` hasta la asesoría legal (PA-06). Cuando se publique la versión revisada, alcanza con cambiar la variable: no hace falta migración ni código.
- **Se actualiza `knowledge-base/04_modelo_de_datos.md` §Usuario** con estas dos columnas y `credenciales_version`, porque LG-04 de 11 exige la aceptación en el registro y 04 no la modelaba.

### D8 — Errores de seguridad en Problem Details
- Un `AuthenticationEntryPoint` y un `AccessDeniedHandler` propios escriben `application/problem+json` con el mismo formato del manejador de C-02 (401 `NO_AUTENTICADO`, 403 `ORIGEN_NO_PERMITIDO`).
- `WWW-Authenticate: Bearer` sin `error_description`, para no explicar la causa.

### D9 — Excepciones a la regla ArchUnit de tenant
- `UsuarioRepositorio.buscarPorEmail(email)`: el login todavía no tiene tenant. El email es único global (RN-TE-02).
- `RefreshTokenRepositorio.buscarPorHash(hash)`: el refresh llega sin access token. El hash de 256 bits es la credencial misma.
- Ambas quedan en una lista explícita de excepciones dentro de `ArchitectureTest`, con un comentario que cita esta decisión. Cualquier otra excepción necesita aprobación en un PR.

### D10 — Chequeo de origen en refresh y logout (defensa contra CSRF)
- La cookie ya es `SameSite=Strict` con `Path=/api/v1/auth`. Además, un filtro solo para esas dos rutas rechaza con 403 cuando hay un `Origin` presente y distinto de `APP_FRONTEND_ORIGIN`.
- No se usa el token CSRF de Spring: la API es stateless y con esta combinación alcanza. Login y registro no usan cookie de entrada.

### D11 — Cookie `Secure` también en local
- Chrome y Firefox aceptan cookies `Secure` en `http://localhost`. En local el frontend llega por el proxy de Vite al mismo origen.
- Si algún navegador de desarrollo (Safari) no la acepta, se documenta en el README de C-07. No se relaja el atributo por perfil, porque un relajamiento por configuración es el tipo de error que termina en producción.

### D12 — Migración y permisos
- `V2__usuario_y_refresh_token.sql` (número provisorio; se pasa al siguiente libre al rebasar, según CHANGES.md).
- **`usuario`**:
  - `consultorio_id UUID NOT NULL UNIQUE REFERENCES consultorio(id)` y `UNIQUE (consultorio_id, id)`.
  - `email CITEXT NOT NULL` con `uk_usuario_email`.
  - Campos de 04 §Usuario, más `credenciales_version` y los de términos (D7).
- **`refresh_token`**:
  - `FOREIGN KEY (consultorio_id, usuario_id) REFERENCES usuario (consultorio_id, id)`.
  - `token_hash CHAR(64) UNIQUE`, `expira_at`, `revocado_at`, `created_at`.
  - Índice parcial `(usuario_id) WHERE revocado_at IS NULL`.
- **Permisos**: `GRANT SELECT, INSERT, UPDATE` a `app_rw` sobre ambas tablas, sin `DELETE`.

### D13 — Estructura del módulo
- `auth.domain`: `Usuario`, `PoliticaDeContrasena`, `RefreshToken` (reglas puras: vigente, revocado, vencido).
- `auth.application`: `RegistrarOdontologo`, `IniciarSesion`, `RenovarSesion`, `CerrarSesion`, puertos `EmisorDeAccessToken`, `HasheadorDeContrasena`, `GeneradorDeSecretos`.
- `auth.infrastructure`: JPA, Nimbus, BCrypt, `SecurityConfig`.
- `auth.web`: controladores, DTOs y la cookie.

## Risks / Trade-offs

- [La propuesta de C-02 puede cambiar en la revisión] → la tabla de §Context lista exactamente qué se usa. La tarea 1.1 la revisa contra lo que se mergee.
- [Sin rate limit ni bloqueo hasta C-05] → C-04 no se despliega en un entorno público solo. C-05 es el siguiente de Mati en el mismo gate.
- [Dos pestañas que renuevan a la vez cierran todas las sesiones] → un único refresh en vuelo en C-07 (D4).
- [Los refresh vencidos o revocados se acumulan] → índice parcial y volumen bajo en v1. La limpieza periódica queda en backlog (se puede sumar a la tarea programada de C-20).
- [Una consulta extra por request para `ver`] → aceptado (D3), y cacheable sin cambiar el contrato.
- [El registro revela si un email existe] → lo exige US-001. El rate limit de C-05 lo acota (D5).
- [Los términos se aceptan sobre un texto `(TBD)`] → la versión `2026-11-borrador` queda registrada, así que se puede pedir aceptación de nuevo cuando exista el texto revisado (PA-06).

## Migration Plan

- Migración nueva sobre la V1 de C-02, sin datos previos. Rollback = no mergear. Una vez mergeada, cualquier ajuste va en una migración nueva (regla dura 5).
- Al mergear, todas las rutas `/api/v1/**` existentes pasan a exigir JWT. Hasta ese momento solo existirán los controladores de prueba de C-02 (en `src/test`), así que no se rompe nada.

## UI Mockups

- Decision: no (no UI), 2026-11-20. Change solo de backend; las pantallas de login y registro son de C-07.
