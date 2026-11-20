# Design

## Context

- Ver `proposal.md` §Why. Dominio `auth`: governance **CRITICO** (RN-AU-06). Maxi aprobó la propuesta el 2026-11-20 (#8), con la condición de aplicar los ajustes de su revisión, que ya están incorporados en este diseño. La implementación arranca con C-02 mergeado.
- C-04 se monta sobre C-01 (paquete raíz `ar.sistemaodontologico`, Spring Boot 3.5.16, Gradle 9.9.2, roles `app_owner`/`app_rw`, `IntegracionPostgresTest`) y sobre **C-02**: su propuesta está aprobada y mergeada (#4) y su implementación está en revisión en #12. Usa estas piezas tal como quedaron implementadas en #12:

  | Pieza de C-02 (#12) | Uso en C-04 |
  |---|---|
  | `V1__consultorio_y_extensiones.sql`, `db/migration/CONVENCIONES.md` y el placeholder de Flyway `${app-user}` | Alta del consultorio en el registro; FK compuesta desde `refresh_token`; `REVOKE DELETE` con el placeholder (D12) |
  | `compartido.domain.ConsultorioId`, `compartido.application.TenantContext.establecer()` | El filtro JWT establece el tenant desde `cid` |
  | `compartido.domain.GeneradorDeIds` (`UUID nuevo()`), bean `Clock` en UTC | Ids de usuario, consultorio y refresh; `iat`/`exp`, vencimientos, `prueba_hasta`. La regla ArchUnit `horaSoloDesdeElClock` prohíbe `now()` sin `Clock` |
  | `compartido.domain.slug.SugeridorDeSlug`, `ValidadorDeSlug` | Slug sugerido en el registro; la unicidad con sufijo se resuelve acá (D6) |
  | `ErrorDeNegocio` (`ValidacionDeNegocio` → 400, `ConflictoDeNegocio` → 409, `RecursoNoEncontrado` → 404) y `compartido.web.ManejadorGlobalDeErrores` | Códigos nuevos: `EMAIL_EN_USO`, `PASSWORD_DEBIL`, `TERMINOS_NO_ACEPTADOS`, `CREDENCIALES_INVALIDAS`, `SESION_EXPIRADA`, `NO_AUTENTICADO`, `ORIGEN_NO_PERMITIDO`. `uk_usuario_email` se suma al mapa de constraints del manejador → 409 `EMAIL_EN_USO` |
  | `compartido.domain.EnmascaradorDePii` | Nunca se loguean emails ni tokens en claro |
  | `ReglasDeArquitectura.repositoriosSiempreConTenant` (exime clases enteras por nombre) | Hay que ampliarla para eximir métodos puntuales (D9) |

  La tarea 1.1 vuelve a contrastar esta tabla contra lo que efectivamente se mergee de #12.
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
- `JWT_SECRET` en base64 de ≥ 32 bytes. El arranque **falla** si la clave es más corta, no es base64 válido o falta (en el perfil `test` se usa una fija de prueba).
  - `JWT_SECRET` se suma a `VariablesObligatorias` (C-01), que ya corta el arranque nombrando la variable faltante.
  - El valor de ejemplo de `.env.example` hoy no es base64 válido y haría que el backend no arranque en Docker Compose. Se reemplaza por un base64 de 48 bytes **marcado como solo para desarrollo local**, y se verifica que `docker compose up --build` levante el backend sano (revisión de #8, ajuste 2).
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
- Como toca **PA-06** (pregunta Alta), la regla dura 16 pide registrar la decisión: se agrega **`DD-23`** en `knowledge-base/09_decisiones_y_supuestos.md` ("aceptación de términos versionada sin texto definitivo hasta la asesoría legal"), en la misma tarea que actualiza 04.

### D8 — Errores de seguridad en Problem Details
- Un `AuthenticationEntryPoint` y un `AccessDeniedHandler` propios escriben `application/problem+json` con el mismo formato del manejador de C-02 (401 `NO_AUTENTICADO`, 403 `ORIGEN_NO_PERMITIDO`).
- `WWW-Authenticate: Bearer` sin `error_description`, para no explicar la causa.

### D9 — Excepciones a la regla ArchUnit de tenant
- `UsuarioRepositorio.buscarPorEmail(email)`: el login todavía no tiene tenant. El email es único global (RN-TE-02).
- `RefreshTokenRepositorio.buscarPorHashParaActualizar(hash)` (con `FOR UPDATE`, D4): el refresh llega sin access token. El hash de 256 bits es la credencial misma.
- La regla de C-02 (`repositoriosSiempreConTenant`) hoy solo exime **clases enteras** por nombre (`ConsultorioRepositorio`). Eximir `UsuarioRepositorio` completo dejaría pasar cualquier método nuevo sin tenant, así que la regla se amplía para aceptar excepciones por método (`"UsuarioRepositorio.buscarPorEmail"`, `"RefreshTokenRepositorio.buscarPorHashParaActualizar"`). La excepción vale para la interfaz nombrada y las clases que la implementan (por ejemplo `UsuarioRepositorioJdbc`), no para cualquier clase cuyo nombre empiece igual (revisión de Mati en #16). Un test con fixtures verifica que la excepción cubre solo ese método.
- Ambas quedan en la lista explícita `REPOSITORIOS_EXCEPTUADOS` de `ArchitectureTest`, con un comentario que cita esta decisión. Cualquier otra excepción necesita aprobación en un PR.

### D10 — Chequeo de origen en refresh y logout (defensa contra CSRF)
- La cookie ya es `SameSite=Strict` con `Path=/api/v1/auth`. Además, un filtro solo para esas dos rutas rechaza con 403 cuando hay un `Origin` presente y distinto de `APP_FRONTEND_ORIGIN`.
- No se usa el token CSRF de Spring: la API es stateless y con esta combinación alcanza. Login y registro no usan cookie de entrada.
- A través del proxy de Vite el navegador manda `Origin: http://localhost:5173`, que coincide con `APP_FRONTEND_ORIGIN` en local. Hay un test con ese `Origin` exacto, para que el chequeo no rompa el flujo de desarrollo (revisión de #8, ajuste 8).

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
  - Índice parcial `ix_refresh_token_vigentes (consultorio_id, usuario_id) WHERE revocado_at IS NULL`, que empieza por `consultorio_id` como pide `CONVENCIONES.md`.
- **Permisos**: `REVOKE DELETE ON usuario, refresh_token FROM "${app-user}"`.
  - Hace falta un `REVOKE` explícito: los privilegios por defecto de C-01 ya le dan `SELECT, INSERT, UPDATE, DELETE` a `app_rw` sobre toda tabla nueva, y un `GRANT` acotado no quitaría el `DELETE`.
  - `${app-user}` es el placeholder de Flyway (`spring.flyway.placeholders.app-user: ${DB_APP_USER}`) que ya define C-02 (#12) y usa C-03 (#10). El rol nunca va fijo en el SQL.
  - Un test de integración conectado como `app_rw` verifica que no puede borrar (tarea 2.1).

### D13 — Estructura del módulo
- `auth.domain`: `Usuario`, `PoliticaDeContrasena`, `RefreshToken` (reglas puras: vigente, revocado, vencido).
- `auth.application`: `RegistrarOdontologo`, `IniciarSesion`, `RenovarSesion`, `CerrarSesion`, puertos `EmisorDeAccessToken`, `HasheadorDeContrasena`, `GeneradorDeSecretos`.
- `auth.infrastructure`: persistencia con `JdbcClient` (no JPA: son inserciones y lecturas puntuales, sin mapear `CITEXT` ni las columnas de C-02 en una entidad), Nimbus, BCrypt, `SecurityConfig`.
- `auth.web`: controladores, DTOs y la cookie.
- **El consultorio no es de `auth`**: `Consultorio`, `ConsultorioRepositorio`, `AltaDeConsultorio` y `AsignadorDeSlugUnico` (D6) viven en el módulo `consultorio`. El registro los usa por su capa `application`, y C-08 los extiende (acordado en el D1 de C-08, #15).

### D14 — Rutas públicas explícitas
- `SecurityConfig` declara públicas **solo** estas rutas; todo lo demás bajo `/api/v1/**` exige access token (spec `seguridad-http`):
  - `/api/v1/auth/registro`, `/api/v1/auth/login`, `/api/v1/auth/refresh`, `/api/v1/auth/logout`;
  - **`/api/v1/public/**`**: la API del paciente (C-15) y `/public/planes` (C-21). Se libera desde ahora para que esos changes no tengan que tocar `SecurityConfig`, que es dominio CRITICO (revisión de #8, ajuste 4);
  - las rutas técnicas de C-01: `/actuator/health`, `/v3/api-docs/**` y, solo con el perfil `local`, `/swagger-ui.html` y `/swagger-ui/**`. Sin esto se rompen el healthcheck de Docker Compose, el contrato que consume el CI del frontend y los tests de C-01 (revisión de #8, ajuste 1).
- **Verificación**: `SaludDelBackendTest`, `ContratoOpenApiTest` y `SwaggerUiLocalTest` (C-01) siguen en verde **sin modificarlos**.
- **CSP y Swagger UI**: `default-src 'self'` puede bloquear los estilos y scripts inline de Swagger UI. Se verifica que `/swagger-ui.html` funcione con el perfil `local`; si no, se relaja la CSP **solo para `/swagger-ui/**` y solo en `local`**, nunca en el perfil por defecto (revisión de #8, ajuste 6).
  - **Resultado de la verificación (2026-11-20)**: con `default-src 'self'`, Chrome bloqueaba estilos inline e íconos `data:` de Swagger UI. Se agregó una cadena de seguridad aparte (`@Profile("local")`, `@Order(1)`) solo para `/swagger-ui.html` y `/swagger-ui/**`, con `style-src 'self' 'unsafe-inline'; img-src 'self' data:`. Los scripts siguen limitados a `'self'`. Con ese cambio, la consola del navegador queda sin errores.
  - Las rutas de Swagger son públicas en todos los perfiles: fuera de `local` springdoc las tiene apagadas y responden 404, que es lo que espera `ContratoOpenApiTest` (C-01).

## Risks / Trade-offs

- [La implementación de C-02 (#12) puede cambiar en la revisión] → la tabla de §Context lista exactamente qué se usa. La tarea 1.1 la revisa contra lo que se mergee.
- [Los tests de C-02 usan controladores de prueba bajo `/api/v1/pruebas/**`; con Spring Security van a recibir 401] → la tarea 5.4 los adapta sin cambiar lo que verifican. `CompartidoAislamientoTest` pasa de la cabecera de prueba a un **JWT real**, así el aislamiento queda probado de punta a punta con la autenticación. `ManejadorGlobalDeErroresTest` (`@WebMvcTest`) desactiva los filtros de seguridad, porque prueba solo el mapeo de errores. `FiltroDeTenantDePrueba` se elimina.
- [Sin rate limit ni bloqueo hasta C-05] → C-04 no se despliega en un entorno público solo. C-05 es el siguiente de Mati en el mismo gate.
- [Dos pestañas que renuevan a la vez cierran todas las sesiones] → un único refresh en vuelo en C-07 (D4).
- [Los refresh vencidos o revocados se acumulan] → índice parcial y volumen bajo en v1. La limpieza periódica queda en backlog (se puede sumar a la tarea programada de C-20).
- [Una consulta extra por request para `ver`] → aceptado (D3), y cacheable sin cambiar el contrato.
- [El registro revela si un email existe] → lo exige US-001. El rate limit de C-05 lo acota (D5).
- [Los términos se aceptan sobre un texto `(TBD)`] → la versión `2026-11-borrador` queda registrada, así que se puede pedir aceptación de nuevo cuando exista el texto revisado (PA-06).

## Migration Plan

- Migración nueva sobre la V1 de C-02, sin datos previos. Rollback = no mergear. Una vez mergeada, cualquier ajuste va en una migración nueva (regla dura 5).
- Al mergear, todas las rutas `/api/v1/**` que no estén en la lista de D14 pasan a exigir JWT. En producción todavía no hay ninguna ruta de negocio. En los tests, los controladores de prueba de C-02 se adaptan en la tarea 5.4. Las rutas técnicas de C-01 quedan públicas (D14).

## UI Mockups

- Decision: no (no UI), 2026-11-20. Change solo de backend; las pantallas de login y registro son de C-07.
