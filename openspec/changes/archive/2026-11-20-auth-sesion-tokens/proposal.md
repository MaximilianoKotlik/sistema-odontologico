# Proposal

> Change **C-04** de [CHANGES.md](../../../CHANGES.md) · Governance **CRITICO** (dominio `auth`, RN-AU-06) · Depende de `C-02` (`core-compartido-tenant`) · Responsable: Fran · Aprobación: Maxi.
>
> **Se apoya en la propuesta de C-02 tal como está en el PR #4, todavía sin aprobar.** Usa estas piezas de C-02: tabla `consultorio`, `ConsultorioId`, `TenantContext.establecer()`, `GeneradorDeIds` (UUIDv7), `Clock`, `SugeridorDeSlug` y `ValidadorDeSlug`, el contrato de errores Problem Details y el puerto `PublicadorDeAuditoria`. Si la revisión de C-02 cambia alguna de ellas, esta propuesta se ajusta.

## Why

Sin cuenta ni sesión, el odontólogo no puede usar nada de la v1, y ninguna ruta privada puede saber de qué consultorio es quien llama. C-04 crea la cuenta y el consultorio en un solo paso y emite las credenciales: el access token JWT, cuyo claim `cid` alimenta el `TenantContext` de C-02 y desde ahí el aislamiento de todos los changes siguientes. También agrega la protección base de la API (cabeceras y CORS). Está en el camino crítico: C-05, C-08 y todo lo que viene después dependen de él.

## What Changes

- **Migración**: tablas `usuario` (1:1 con `consultorio`, `email CITEXT UNIQUE`, hash BCrypt, versión de credenciales, aceptación de términos) y `refresh_token` (solo hash SHA-256, vencimiento, revocación).
- **`POST /api/v1/auth/registro`**: crea consultorio (`PRUEBA`, `prueba_hasta = hoy + 30 días` en la zona del consultorio, slug sugerido único) y usuario en una sola transacción. Registra la versión de términos aceptada. Errores: 409 `EMAIL_EN_USO`, 400 `PASSWORD_DEBIL`, 400 `TERMINOS_NO_ACEPTADOS`. Responde 201 **sin iniciar sesión**: el frontend hace login a continuación.
- **`POST /api/v1/auth/login`**: valida credenciales y devuelve el access token JWT HS256 de 15 min (`sub`, `cid`, `iat`, `exp`, `ver`) más una cookie de refresh opaco de 14 días (`HttpOnly; Secure; SameSite=Strict; Path=/api/v1/auth`). Ante cualquier fallo responde 401 `CREDENCIALES_INVALIDAS`, con un mensaje genérico.
- **`POST /api/v1/auth/refresh`**: rota el refresh en cada uso. Si se presenta uno ya rotado o revocado, revoca **todos** los refresh del usuario y responde 401 (RN-AU-02).
- **`POST /api/v1/auth/logout`**: revoca el refresh presentado y borra la cookie (204).
- **Refresh y logout** rechazan con 403 `ORIGEN_NO_PERMITIDO` las peticiones de otro origen, como defensa contra CSRF sobre la cookie.
- **Spring Security stateless**:
  - Filtro JWT que valida firma, vencimiento y `ver`, y establece el `TenantContext` de C-02 desde `cid`.
  - Las rutas `/api/v1/**` exigen autenticación, salvo las de `auth` y las públicas.
  - Sin token o con token inválido: 401 `NO_AUTENTICADO` en Problem Details.
- **Cabeceras**: CSP `default-src 'self'`, HSTS fuera del perfil `local`, `X-Content-Type-Options: nosniff`, `Referrer-Policy: no-referrer`. **CORS** solo para `APP_FRONTEND_ORIGIN`, con credenciales.
- **Tests**:
  - US-001 completo.
  - US-002: login con access y cookie, y reuso de refresh que revoca todo. El criterio de bloqueo tras 5 fallos va en C-05.
  - 401 sin token y 404 ante un recurso de otro consultorio con un JWT válido.
  - Atributos de la cookie y cabeceras de seguridad.

**Fuera de alcance (van en C-05)**: recuperación y cambio de contraseña, bloqueo tras 5 fallos (423 `CUENTA_BLOQUEADA`), rate limiting y eventos de auditoría de auth. **No entran en v1**: verificación de email (PA-09, decidido "no en v1"), 2FA (DD-18) y login social.

## Capabilities

### New Capabilities

- `registro-odontologo`: alta autoservicio del odontólogo con su consultorio en prueba, slug sugerido y aceptación de términos versionada.
- `sesion-odontologo`: login, access token, refresh rotado con detección de reuso, logout y resolución del consultorio activo desde la sesión. C-05 le agregará el bloqueo por fallos y el cambio de contraseña como delta.
- `seguridad-http`: autenticación obligatoria en rutas privadas, cabeceras de seguridad y CORS restringido.

### Modified Capabilities

_Ninguna_. `aislamiento-tenant` y `errores-api` vienen de C-02 y todavía no son specs archivadas. C-04 las **usa** sin cambiar sus requisitos: los códigos nuevos se definen en las specs propias de este change.

## Impact

- **Código**: `backend/.../auth/**` (web, application, domain, infrastructure), configuración de Spring Security, `V3__usuario_y_refresh_token.sql` (V2 es de C-03), tests espejo.
- **Dependencias nuevas**: `spring-boot-starter-security` y `spring-boot-starter-oauth2-resource-server` (Nimbus JOSE para firmar y validar JWT HS256; ver design D2). No se agrega ninguna librería JWT de terceros.
- **API**: 4 endpoints nuevos en `/api/v1/auth`; **todas** las rutas `/api/v1/**` pasan a exigir JWT; contrato regenerado en `api-types.ts`.
- **Configuración**: usa `JWT_SECRET`, `JWT_ACCESS_TTL`, `JWT_REFRESH_TTL` y `APP_FRONTEND_ORIGIN` de 08 §Variables de entorno; sin variables nuevas salvo `TERMINOS_VERSION_VIGENTE` (ver design D7).
- **Knowledge base**: agrega `terminos_version` y `terminos_aceptados_at` a `usuario` (04 §Usuario no las lista; las pide LG-04 de 11). Se actualiza 04 en este mismo PR.
- **Preguntas abiertas**:
  - **PA-06** (asesoría legal sobre términos): se resuelve registrando qué versión se aceptó, sin fijar el texto (design D7).
  - **PA-09** (verificación de email): se aplica la decisión por defecto, "no en v1".
  - **PA-03** (precios): solo se usa la duración de prueba de 30 días de RN-PL-02.
- **Changes desbloqueados**: C-05 (con C-03), C-08 (con C-03) y, a través de ellos, el camino crítico de la agenda.
