# Arquitectura Propuesta

> Stack y vista de alto nivel en [02](02_descripcion_general.md). Decisiones y alternativas en
> [09](09_decisiones_y_supuestos.md). Prioridad de calidad: **velocidad de entrega con deuda técnica
> controlada**; Strict TDD activado.

## Patrones aplicados

| Patrón | Dónde se usa | Por qué |
|--------|--------------|---------|
| Monolito modular (paquete por dominio) | Backend | Un deploy, transacciones locales, límites claros para crecer |
| Capas por módulo (`web` → `application` → `domain` ← `infrastructure`) | Cada paquete de dominio | El dominio no depende de Spring ni de JPA: tests unitarios rápidos |
| Reglas como funciones puras | `agenda.domain` (`CalculadoraDeHuecos`, `ValidadorDeSolapamiento`, `MaquinaDeEstadosTurno`) | Núcleo testeable sin base, ideal para TDD |
| Invariantes en la base | Exclusión GiST, FKs compuestas, trigger de auditoría | Defensa en profundidad ante carreras y bugs |
| Optimistic locking | `Turno.version` | Concurrencia odontólogo/paciente sin bloqueos |
| Problem Details (RFC 7807) | `@RestControllerAdvice` global | Errores con `codigo` estable |
| Contract-first liviano | springdoc genera OpenAPI; el frontend tipa su cliente desde ese JSON (`openapi-typescript`) | Un solo contrato, sin tipos duplicados a mano |
| Feature folders + componentes vanilla | Frontend | Sin framework pero con estructura predecible |

## Estructura de directorios

```
global-desarrollo/
├── docker-compose.yml
├── .env.example
├── backend/
│   ├── build.gradle.kts  settings.gradle.kts  gradlew
│   ├── Dockerfile                         # multi-stage: gradle build → eclipse-temurin:21-jre
│   └── src/
│       ├── main/java/ar/.../agenda/
│       │   ├── Application.java
│       │   ├── compartido/                # TenantContext, Problem Details, reloj (Clock), auditoría API
│       │   ├── auth/                      # CRITICAL — web/, application/, domain/, infrastructure/ (JWT, BCrypt, SecurityConfig)
│       │   ├── consultorio/               # consultorio, prestaciones, disponibilidad
│       │   ├── agenda/                    # turnos, bloqueos, pacientes, vista de agenda
│       │   ├── reserva/                   # endpoints públicos, huecos, tokens de gestión
│       │   ├── auditoria/                 # EventoAuditoria, publicador, consulta
│       │   ├── exportacion/               # generador ZIP JSON + CSV
│       │   └── plan/                      # estado de plan, tarea de vencimiento, planes públicos
│       ├── main/resources/
│       │   ├── application.yml  application-local.yml  application-test.yml
│       │   └── db/migration/  db/seed-local/
│       └── test/java/...                  # espejo de paquetes + ArchitectureTest (ArchUnit)
└── frontend/
    ├── package.json  vite.config.ts  tailwind.config.ts  tsconfig.json
    ├── Dockerfile                         # dev: vite; build: nginx sirviendo dist/
    ├── public/  manifest.webmanifest  icons/
    └── src/
        ├── main.ts                        # bootstrap + router
        ├── shared/                        # router, http (fetch + refresh), api-types (generado), store, ui/, fechas (Intl, TZ)
        ├── features/
        │   ├── auth/  agenda/  turnos/  bloqueos/  configuracion/
        │   ├── reserva-publica/  gestion-turno/  auditoria/  exportacion/  precios/
        └── pages/                         # composición de features por ruta
```

Cada módulo backend sigue la misma forma interna:

| Capa | Contenido | Depende de |
|------|-----------|------------|
| `web` | Controladores REST, DTOs, mapeo a Problem Details | `application` |
| `application` | Casos de uso (`CrearTurno`, `ReservarTurnoPublico`), transacciones, autorización por tenant | `domain`, puertos |
| `domain` | Entidades y reglas puras, sin anotaciones de Spring | nada |
| `infrastructure` | Repositorios JPA/JDBC, adaptadores (SMTP, JWT) | `domain` |

Reglas de dependencia verificadas con **ArchUnit**: `domain` no importa `org.springframework..` ni
`jakarta.persistence..`; ningún módulo accede a `infrastructure` de otro; los módulos se comunican por
servicios de `application` públicos o por eventos de dominio (`auditoria` escucha
`ApplicationEvent`s publicados en la misma transacción con `@TransactionalEventListener(BEFORE_COMMIT)`).

## Seguridad

- **Autenticación** (dominio **CRITICAL**: todo cambio requiere aprobación humana): Spring Security
  stateless. Access token JWT firmado HS256 con secreto de ≥ 256 bits (`JWT_SECRET`), 15 min, claims
  `sub`, `cid`, `iat`, `exp`, `ver` (versión de credenciales para invalidar tras cambio de contraseña).
  Refresh opaco rotado de 14 días en cookie `HttpOnly; Secure; SameSite=Strict; Path=/api/v1/auth`.
  BCrypt costo 12. Bloqueo tras 5 fallos ([RN-AU-*](05_reglas_de_negocio.md)).
- **Autorización**: sin RBAC. Un filtro resuelve `TenantContext` desde el claim `cid`; los casos de
  uso lo reciben explícito. Rutas públicas resuelven tenant por `slug` o token de gestión.
- **Tokens del paciente**: 256 bits de `SecureRandom`, base64url, hash SHA-256 en DB, comparación por
  hash; respuesta 404 uniforme ante cualquier token inválido.
- **Validación de input**: Bean Validation en DTOs + validaciones de dominio; teléfonos normalizados
  con `libphonenumber`; tamaño máximo de body 64 KB en públicos.
- **Rate limiting**: Bucket4j en memoria por IP/email/token (suficiente para una instancia; pasar a
  almacenamiento compartido si se escala horizontalmente).
- **Cabeceras**: CSP estricta (`default-src 'self'`), HSTS fuera de local, `X-Content-Type-Options`,
  `Referrer-Policy: no-referrer` (el token viaja en la URL de `/t/{token}`; evita filtrarlo por Referer).
- **CORS**: solo el origen del frontend (`APP_FRONTEND_ORIGIN`).
- **Secrets management**: variables de entorno vía `.env` (no versionado; se versiona `.env.example`).
  En cloud, el gestor de secretos del proveedor elegido ([PA-02](10_preguntas_abiertas.md)).
- **Datos sensibles** ([11](11_marco_legal_argentino.md)): minimización (sin DNI ni datos clínicos en
  v1), enmascarado de PII en logs, TLS en tránsito fuera de local, cifrado en reposo delegado al
  Postgres como contenedor del mismo Compose en un VPS ([DD-25](09_decisiones_y_supuestos.md)); país del VPS y backups pendientes ([PA-02](10_preguntas_abiertas.md)).

## Persistencia y migraciones

- Flyway con migraciones versionadas `V{n}__{descripcion}.sql` en SQL plano (no generación desde
  JPA: `spring.jpa.hibernate.ddl-auto=validate`).
- Las restricciones de exclusión, triggers y FKs compuestas viven en SQL, no en anotaciones.
- Seed de demo solo en perfil `local` (`db/seed-local`, ver [04](04_modelo_de_datos.md#seed-data-inicial)).
- La aplicación se conecta con un rol `app_rw` sin permisos de `UPDATE/DELETE` sobre `evento_auditoria`;
  Flyway usa un rol `app_owner` distinto.

## OpenAPI

- springdoc expone `/v3/api-docs` y Swagger UI (solo perfil `local`), con esquema de seguridad
  `bearerAuth` y ejemplos de errores Problem Details.
- `npm run api:types` genera `frontend/src/shared/api-types.ts` desde el JSON; un test de CI falla si
  el archivo generado difiere del commiteado.

## Docker Compose local

| Servicio | Imagen | Puerto | Notas |
|----------|--------|--------|-------|
| `postgres` | `postgres:16-alpine` | 5432 | Volumen `pgdata`; script init crea roles `app_owner` y `app_rw` |
| `backend` | build `./backend` | 8080 | Perfil `local`; espera healthcheck de postgres; corre Flyway al iniciar |
| `frontend` | build `./frontend` | 5173 | `vite --host`; proxy `/api` → `backend:8080` |
| `mailpit` | `axllent/mailpit` | 8025 (UI), 1025 (SMTP) | Captura emails de recuperación; nada sale a Internet |

Comando único: `docker compose up --build`. Datos de demo disponibles al primer arranque.

## Variables de entorno

| Variable | Descripción | Ejemplo | Sensible |
|----------|-------------|---------|----------|
| `POSTGRES_DB` | Base de datos | `agenda` | N |
| `POSTGRES_USER` / `POSTGRES_PASSWORD` | Superusuario del contenedor | `postgres` / `cambiar` | Y |
| `DB_URL` | JDBC URL | `jdbc:postgresql://postgres:5432/agenda` | N |
| `DB_APP_USER` / `DB_APP_PASSWORD` | Rol de la aplicación (`app_rw`) | `app_rw` / `cambiar` | Y |
| `DB_OWNER_USER` / `DB_OWNER_PASSWORD` | Rol de Flyway (`app_owner`) | `app_owner` / `cambiar` | Y |
| `JWT_SECRET` | Clave HS256 (≥ 32 bytes, base64) | `generar con openssl rand -base64 48` | Y |
| `JWT_ACCESS_TTL` / `JWT_REFRESH_TTL` | Vigencias | `PT15M` / `P14D` | N |
| `APP_FRONTEND_ORIGIN` | Origen permitido por CORS y base de links | `http://localhost:5173` | N |
| `SMTP_HOST` / `SMTP_PORT` | Servidor de correo | `mailpit` / `1025` | N |
| `SMTP_USER` / `SMTP_PASSWORD` | Credenciales SMTP (vacías en local) | — | Y |
| `MAIL_FROM` | Remitente | `no-responder@localhost` | N |
| `SPRING_PROFILES_ACTIVE` | Perfil | `local` | N |
| `VITE_API_BASE_URL` | Base de la API para el frontend | `/api/v1` | N |

## Estrategia de tests (Strict TDD)

Cada criterio Given/When/Then de [06](06_funcionalidades.md) se escribe primero como test en rojo.

| Nivel | Herramientas | Qué cubre | Objetivo |
|-------|--------------|-----------|----------|
| Unitario de dominio | JUnit 5 + AssertJ | Huecos, solapamiento, máquina de estados, anticipación, normalización de teléfono, slug | Mayoría de los tests; milisegundos; reloj inyectado (`Clock`) |
| Unitario de aplicación | JUnit 5 + Mockito | Casos de uso con puertos mockeados; publicación de eventos de auditoría | Orquestación y errores |
| Integración | Spring Boot Test + **Testcontainers (PostgreSQL 16)** | Migraciones Flyway reales, restricción de exclusión, FKs compuestas, trigger append-only, aislamiento entre dos tenants, concurrencia de reservas | Todo lo que depende de Postgres; **nunca H2** |
| API / seguridad | MockMvc + Testcontainers | 401/403/404 por tenant, rate limit, Problem Details, cookies de refresh | Contrato HTTP |
| Arquitectura | ArchUnit | Dependencias entre capas y módulos; repositorios siempre con tenant | Deuda controlada |
| Frontend | **Vitest** + jsdom | Router, cliente HTTP con refresh, formateo de fechas/TZ, validaciones de formularios, render de componentes de agenda | Lógica del cliente |
| E2E (P1) | Playwright | Reserva por link en viewport mobile; login → crear turno | Humo antes de cada release |

Convenciones: un contenedor Postgres reutilizado por suite (`@Testcontainers` + `static`), datos por
builders (`*Mother`), cobertura mínima orientativa 80 % en `domain` y `application`. `./gradlew test`
y `npm test` deben pasar antes de cualquier merge.

## PWA y frontend mobile-first

- Manifest con nombre, íconos 192/512, `display: standalone`, `start_url: /app/agenda`.
- Service Worker (Workbox): precache del shell; `NetworkFirst` para `GET /api/v1/agenda` con caché
  de la última agenda vista (lectura offline); **nunca** se cachean respuestas de `/public/turnos/**`
  ni de `/auth/**`. Sin escritura offline en v1.
- Tailwind con breakpoints mobile-first (`sm` 640, `md` 768); objetivo de diseño 360 px de ancho;
  áreas táctiles ≥ 44 px; vista diaria por defecto en mobile y semanal desde `md`.
- Fechas con `Intl.DateTimeFormat('es-AR', { timeZone })` usando la zona del consultorio.
- Access token solo en memoria; al recargar se obtiene vía `/auth/refresh` (cookie).
