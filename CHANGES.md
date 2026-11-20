# CHANGES — Secuencia de Implementación

> Índice canónico de todos los changes del proyecto **Turnos Odonto (nombre TBD)** — v1: agenda de un odontólogo + reserva pública por link sin cuenta.
> Cada change es atómico: un agente puede implementarlo en una sesión (~4-6 horas) y entra por un PR aprobado por el dueño del proyecto (`.github/CODEOWNERS`).
> **Leer este archivo antes de empezar un item.**

---

## Cómo usar este documento

1. Identificar el item a implementar y verificar que sus dependencias figuran completas (`[x]`) en este roadmap.
2. Leer los docs de la knowledge-base indicados en "Leer antes". En los changes de frontend, leer además el sistema de diseño aprobado (`design-system/README.md`, `tokens.css`, `components.html`).
3. Acordar el alcance y los criterios de aceptación del item (los Given/When/Then de `06_funcionalidades.md` son la base; Strict TDD: cada criterio se escribe primero como test en rojo).
4. Implementar, verificar los criterios acordados y registrar la evidencia en el PR.
5. Marcar el checkbox `[x]` en este archivo al completar la verificación técnica.

Reglas del equipo:
- **Governance**: CRITICO = solo análisis hasta aprobación humana explícita; ALTO = proponer y esperar revisión antes de escribir; MEDIO = implementar con checkpoints; BAJO = autonomía si los tests pasan.
- **Mockups**: el sistema de diseño está en modo `system-and-mockups` (rev. 1, aprobado). Todo change de frontend (C-05, C-06, C-08, C-11, C-12, C-14, C-15 y las pantallas de C-16/C-17) exige un mockup revisado y aprobado **antes** de implementar la pantalla; el mockup se adjunta al PR.
- Conservá los IDs de las fichas al editar o reordenar. Si el proyecto usa ClickUp, este roadmap conserva el alcance y las dependencias; el responsable y el estado operativo se consultan en ClickUp (último dato en `.active-stack/roadmap-state.json`).
- Fuera de alcance v1 (no crear changes): WhatsApp, historia clínica/odontograma/presupuestos, Mercado Pago/cobros, obras sociales, ARCA, multi-profesional/multi-sillón, roles y recepción. Ver nota post-v1 al final.

---

## Árbol de dependencias

```
C-01 foundation-setup
  ├── C-02 core-compartido-tenant                  ← núcleo: tenant, errores, reloj, puerto de auditoría
  │     ├── C-03 auditoria-base                    ← paralelo con C-04
  │     └── C-04 auth-backend
  │           ├── C-06 frontend-auth               ← + C-05
  │           └── C-07 consultorio-config-backend  ← + C-03
  │                 ├── C-08 frontend-configuracion ← + C-06
  │                 └── C-09 agenda-dominio-y-esquema
  │                       ├── C-10 agenda-api-odontologo
  │                       │     ├── C-11 frontend-agenda        ← + C-06
  │                       │     │     ├── C-12 frontend-turnos-bloqueos
  │                       │     │     └── C-15 historial-auditoria  ← + C-10
  │                       └── C-13 reserva-publica-backend       ← paralelo con C-10
  │                             ├── C-14 frontend-reserva-publica ← + C-05
  │                             └── C-16 exportacion-datos        ← + C-08
  │                                   └── C-17 plan-precios-y-seguridad ← + C-08, C-10 (describe la exportación y la auditoría)
  └── C-05 frontend-shell-pwa                       ← paralelo con C-02

C-18 e2e-hardening-release  ← depende de C-12, C-14, C-15, C-16, C-17
```

### Paralelismo por fase

> Cada gate es un punto de sincronización. Los changes dentro de un gate pueden ejecutarse en paralelo (un PR por change).

```
GATE 0: ninguna
  → C-01 foundation-setup                         [Agente A]

GATE 1: C-01 ✓
  → C-02 core-compartido-tenant                   [Agente A]
  → C-05 frontend-shell-pwa                       [Agente C]

GATE 2: C-02 ✓                                    ← FORK
  → C-04 auth-backend                             [Agente A]
  → C-03 auditoria-base                           [Agente B]
  (C-05 sigue en curso por [Agente C] si no terminó)

GATE 3: C-04 ✓ + C-05 ✓
  → C-06 frontend-auth                            [Agente C]
  → C-07 consultorio-config-backend               [Agente A — si C-03 ✓]

GATE 4: C-07 ✓
  → C-09 agenda-dominio-y-esquema                 [Agente A]
  → C-08 frontend-configuracion                   [Agente C — si C-06 ✓]

GATE 5: C-09 ✓                                    ← FORK
  → C-10 agenda-api-odontologo                    [Agente A]
  → C-13 reserva-publica-backend                  [Agente B]
  (C-08 sigue en curso por [Agente C] si no terminó)

GATE 6: C-10 ✓ + C-13 ✓ + C-08 ✓                  ← FORK
  → C-11 frontend-agenda                          [Agente C — si C-06 ✓]
  → C-16 exportacion-datos                        [Agente A]
  → C-14 frontend-reserva-publica                 [Agente B — si C-05 ✓]

GATE 7: C-11 ✓ + C-16 ✓                           ← FORK
  → C-12 frontend-turnos-bloqueos                 [Agente C]
  → C-15 historial-auditoria                      [Agente A]
  → C-17 plan-precios-y-seguridad                 [Agente B — requiere C-16 ✓ y C-10 ✓]

GATE 8: C-12, C-14, C-15, C-16, C-17 ✓
  → C-18 e2e-hardening-release                    [Agente A; B y C revisan y corrigen hallazgos]
```

### Camino crítico (9 changes — mínimo irreducible)

```
C-01 → C-02 → C-04 → C-07 → C-09 → C-10 → C-11 → C-12* / C-15* → C-18
```

`*` C-12 y C-15 son ambos "penúltimos" (se pueden hacer en paralelo). Quedan fuera del camino crítico: C-03 (paralelo con C-04), C-05/C-06/C-08 (frontend de base, absorbidos por la holgura del backend), C-13/C-14/C-16/C-17 (C-17 espera a C-16 pero cae en el paso 8, en paralelo con C-12/C-15, sin alargar el camino).

### Plan óptimo con 3 agentes

| Paso | Agente A (Backend core) | Agente B (Backend aux) | Agente C (Frontend) |
|------|-------------------------|------------------------|---------------------|
| 1 | C-01 | — | — |
| 2 | C-02 | — | C-05 |
| 3 | C-04 | C-03 | mockups de C-06 (revisión) |
| 4 | C-07 | — | C-06 |
| 5 | C-09 | — | C-08 |
| 6 | C-10 | C-13 | mockups de C-11/C-14/C-16/C-17 (revisión) |
| 7 | C-16 | C-14 | C-11 |
| 8 | C-15 | C-17 | C-12 |
| 9 | C-18 | apoyo de revisión | apoyo de revisión |

---

## FASE 1 — Fundación y núcleo

### [C-01] `foundation-setup`
- **Estado**: `[ ]` pendiente
- **Scope**:
  - Estructura `backend/` (Gradle Kotlin DSL, Java 21, Spring Boot, springdoc, Flyway, Testcontainers, ArchUnit, JUnit 5/AssertJ/Mockito) y `frontend/` (Vite + TypeScript + Tailwind + Vitest); Dockerfiles multi-stage.
  - `docker-compose.yml` con `postgres:16-alpine` (init crea roles `app_owner` y `app_rw`), `backend`, `frontend`, `mailpit`; `.env.example` con las variables de 08 §Variables de entorno; `docker compose up --build` levanta todo.
  - `application.yml` / `-local` / `-test`; health check; endpoint `/v3/api-docs` (Swagger UI solo perfil `local`).
  - CI en GitHub Actions: `./gradlew test`, `npm test`, build; script `npm run api:types` y chequeo de que el tipo generado no difiera del commiteado.
  - `ArchitectureTest` base (reglas de capas de 08) y primer test de humo con Testcontainers.
- **Dependencias**: ninguna
- **Governance**: BAJO
- **Leer antes**:
  - `knowledge-base/08_arquitectura_propuesta.md` §Estructura de directorios, §Docker Compose local, §Variables de entorno, §Estrategia de tests
  - `knowledge-base/09_decisiones_y_supuestos.md` §DD-01, DD-02, DD-04, DD-05
  - `knowledge-base/02_descripcion_general.md`

### [C-02] `core-compartido-tenant`
- **Estado**: `[ ]` pendiente
- **Scope**:
  - Migración V1: tabla `consultorio` (slug, zona horaria, anticipaciones, plan/`prueba_hasta`, `reserva_publica_activa`) con constraints; extensiones `btree_gist`, `citext`; generador UUIDv7.
  - Paquete `compartido`: `TenantContext` explícito, `Clock` inyectado, Problem Details RFC 7807 con `codigo` estable (`@RestControllerAdvice`), normalizador de teléfono E.164 (`libphonenumber`), validador de slug y palabras reservadas.
  - Puerto de publicación de auditoría (interfaz + `ApplicationEvent`; la persistencia llega en C-03).
  - Convención de FKs compuestas `(consultorio_id, id)` y tests de aislamiento entre dos tenants; ArchUnit "repositorios siempre con tenant".
  - Tests: slug, E.164, mapeo de errores, aislamiento multi-tenant.
- **Dependencias**: `C-01`
- **Governance**: CRITICO
- **Leer antes**:
  - `knowledge-base/04_modelo_de_datos.md` §Convenciones, §Consultorio, §Aislamiento por tenant
  - `knowledge-base/05_reglas_de_negocio.md` §Tenant (RN-TE), §Excepciones globales (RN-GL)
  - `knowledge-base/08_arquitectura_propuesta.md` §Patrones aplicados, §Persistencia y migraciones
  - `knowledge-base/09_decisiones_y_supuestos.md` §DD-08

### [C-03] `auditoria-base`
- **Estado**: `[ ]` pendiente
- **Scope**:
  - Migración: `evento_auditoria` append-only (actor_tipo, entidad, acción, `valor_anterior/nuevo` JSONB, ip) con trigger `BEFORE UPDATE OR DELETE` que falla y rol `app_rw` solo con `INSERT, SELECT`.
  - Listener `@TransactionalEventListener(BEFORE_COMMIT)` que persiste en la misma transacción los eventos del puerto de C-02; enmascarado de PII en logs.
  - Tests de integración (Testcontainers): `UPDATE`/`DELETE` fallan aun con el rol de la app; el evento se revierte junto con la transacción; aislamiento por tenant.
  - La consulta (endpoint e UI) queda en C-15.
- **Dependencias**: `C-02`
- **Governance**: CRITICO
- **Leer antes**:
  - `knowledge-base/05_reglas_de_negocio.md` §Auditoría (RN-AD)
  - `knowledge-base/04_modelo_de_datos.md` §EventoAuditoria
  - `knowledge-base/08_arquitectura_propuesta.md` §Persistencia y migraciones, §Patrones aplicados
  - `knowledge-base/11_marco_legal_argentino.md`

### [C-04] `auth-backend`
- **Estado**: `[ ]` pendiente
- **Scope**:
  - Migración: `usuario`, `refresh_token`, `token_recuperacion`.
  - `POST /api/v1/auth/registro` (crea usuario + consultorio en `PRUEBA`, `prueba_hasta = hoy + 30 d`, slug sugerido, consentimiento de términos; 409 `EMAIL_EN_USO`, 400 `PASSWORD_DEBIL`), `login`, `refresh` (rotación con detección de reuso que revoca todos), `logout`, `recuperacion` (202 idéntica) y `confirmar-recuperacion` (token 30 min, un solo uso).
  - Spring Security stateless: JWT HS256 15 min (`sub`, `cid`, `iat`, `exp`, `ver`), refresh en cookie `HttpOnly; Secure; SameSite=Strict; Path=/api/v1/auth`, BCrypt costo 12, bloqueo tras 5 fallos (423 `CUENTA_BLOQUEADA` 15 min), rate limiting Bucket4j; filtro que resuelve `TenantContext` desde `cid`.
  - Cabeceras: CSP estricta, HSTS fuera de local, `X-Content-Type-Options`, `Referrer-Policy: no-referrer`; CORS solo `APP_FRONTEND_ORIGIN`.
  - Email de recuperación por SMTP (Mailpit en local); eventos de auditoría vía el puerto de C-02 (`LOGIN_FALLIDO`, etc.).
  - Tests: cada Given/When/Then de US-001, US-002, US-003, 401/403 por tenant, cookies, rate limit.
- **Dependencias**: `C-02`
- **Governance**: CRITICO
- **Leer antes**:
  - `knowledge-base/05_reglas_de_negocio.md` §Autenticación (RN-AU)
  - `knowledge-base/06_funcionalidades.md` §Épica 1 (US-001 a US-003)
  - `knowledge-base/07_flujos_principales.md` §FL-01
  - `knowledge-base/08_arquitectura_propuesta.md` §Seguridad
  - `knowledge-base/09_decisiones_y_supuestos.md` §DD-06, DD-15 (y `10_preguntas_abiertas.md` §IN-01, PA-09)

### [C-05] `frontend-shell-pwa`
- **Estado**: `[ ]` pendiente
- **Scope**:
  - `tokens.css` del sistema de diseño importado y mapeado en `tailwind.config.ts`; `BRAND_NAME` desde `design-system/brand.js`.
  - `shared/`: router, cliente HTTP (fetch + refresh por cookie, token solo en memoria), store, `ui/` (botón, campo, chip de estado, diálogo, aviso, estado vacío según `components.html`), formateo de fechas con `Intl.DateTimeFormat('es-AR', { timeZone })`, `api-types.ts` generado.
  - PWA: `manifest.webmanifest` (íconos 192/512, `display: standalone`, `start_url: /app/agenda`), Service Worker Workbox (precache del shell; `NetworkFirst` para `GET /api/v1/agenda`; nunca cachear `/public/turnos/**` ni `/auth/**`).
  - Mockup revisado del app-shell (navegación mobile 360 px, estados vacío/error/carga) antes de implementar.
  - Tests Vitest: router, cliente HTTP con refresh, fechas/TZ.
- **Dependencias**: `C-01`
- **Governance**: BAJO
- **Leer antes**:
  - `design-system/README.md`, `design-system/tokens.css`, `design-system/components.html`
  - `knowledge-base/08_arquitectura_propuesta.md` §PWA y frontend mobile-first, §Estructura de directorios (frontend)
  - `knowledge-base/09_decisiones_y_supuestos.md` §DD-02, DD-11

### [C-06] `frontend-auth`
- **Estado**: `[ ]` pendiente
- **Scope**:
  - Pantallas: registro (con aceptación de términos), login, recuperación y confirmación de recuperación, cierre de sesión; manejo de `CUENTA_BLOQUEADA`, `EMAIL_EN_USO`, `PASSWORD_DEBIL`, `TOKEN_INVALIDO`.
  - Guardas de ruta y restauración de sesión vía `/auth/refresh` al recargar.
  - Mockup revisado de todas las pantallas antes de implementar.
  - Tests Vitest de validaciones de formularios y flujo de refresh; revisión de que el access token nunca toca el storage.
- **Dependencias**: `C-04`, `C-05`
- **Governance**: ALTO
- **Leer antes**:
  - `design-system/README.md`, `design-system/components.html`
  - `knowledge-base/06_funcionalidades.md` §US-001 a US-003
  - `knowledge-base/07_flujos_principales.md` §FL-01
  - `knowledge-base/08_arquitectura_propuesta.md` §PWA y frontend mobile-first, §Seguridad

---

## FASE 2 — Configuración del consultorio

### [C-07] `consultorio-config-backend`
- **Estado**: `[ ]` pendiente
- **Scope**:
  - Migración: `tipo_prestacion` (duración múltiplo de 5, `activa`, `visible_publica`, único por nombre) y `disponibilidad` (franjas semanales con exclusión GiST para no solaparse).
  - Endpoints: `GET/PUT /api/v1/consultorio` (nombre, slug, teléfono de contacto, anticipaciones, paso de huecos, `reserva_publica_activa`; 400/409 por slug reservado o en uso), CRUD de `/api/v1/prestaciones` (desactivar en vez de borrar), `PUT /api/v1/disponibilidad` (400 `FRANJAS_SOLAPADAS`).
  - Auditoría de cambios de configuración con valor anterior y nuevo (vía C-03).
  - Seed local mínimo (`db/seed-local`) con un consultorio demo.
  - Tests: US-004, US-010, US-011 y aislamiento por tenant.
- **Dependencias**: `C-03`, `C-04`
- **Governance**: MEDIO
- **Leer antes**:
  - `knowledge-base/04_modelo_de_datos.md` §TipoDePrestacion, §Disponibilidad, §Seed data inicial
  - `knowledge-base/05_reglas_de_negocio.md` §Tenant (RN-TE), RN-AG-02, RN-AG-08, RN-AG-10
  - `knowledge-base/06_funcionalidades.md` §US-004, US-010, US-011
  - `knowledge-base/07_flujos_principales.md` §FL-02

### [C-08] `frontend-configuracion`
- **Estado**: `[ ]` pendiente
- **Scope**:
  - Feature `configuracion/`: datos del consultorio y slug (con link público copiable `/c/{slug}`), prestaciones con duración, disponibilidad semanal, anticipaciones; onboarding guiado FL-02 tras el registro.
  - Mockup revisado de las pantallas y del onboarding antes de implementar.
  - Tests Vitest: validaciones (múltiplo de 5, franjas solapadas, slug).
- **Dependencias**: `C-06`, `C-07`
- **Governance**: MEDIO
- **Leer antes**:
  - `design-system/README.md`, `design-system/components.html`
  - `knowledge-base/06_funcionalidades.md` §US-004, US-010, US-011
  - `knowledge-base/07_flujos_principales.md` §FL-02
  - `knowledge-base/08_arquitectura_propuesta.md` §PWA y frontend mobile-first

---

## FASE 3 — Agenda del odontólogo

### [C-09] `agenda-dominio-y-esquema`
- **Estado**: `[ ]` pendiente
- **Scope**:
  - Migración: `paciente` (único `(consultorio_id, telefono_e164)`, consentimiento, `requiere_revision`), `turno` (`es_sobreturno`, `version`, restricción `turno_sin_solapamiento` EXCLUDE GiST, FKs compuestas), `bloqueo` (índice GiST).
  - Dominio puro (sin Spring/JPA): `CalculadoraDeHuecos` (disponibilidad, bloqueos, paso, anticipación mínima, horizonte), `ValidadorDeSolapamiento` (intervalos semiabiertos), `MaquinaDeEstadosTurno` (RESERVADO/CONFIRMADO/CANCELADO/AUSENTE), reglas de bloqueo (rechazo con lista de conflictos), múltiplos de 5 min.
  - Casos de uso de aplicación: crear turno (manual, sobreturno, desde reserva pública), cambiar estado, reprogramar, crear bloqueo; deduplicación de paciente por teléfono; optimistic locking; eventos de auditoría (`TURNO_CREADO`, `TURNO_ESTADO_CAMBIADO`, `TURNO_REPROGRAMADO`, `BLOQUEO_CREADO`).
  - Tests: la mayoría unitarios de dominio con `Clock` inyectado + integración de la exclusión GiST y de concurrencia; ArchUnit.
- **Dependencias**: `C-07`
- **Governance**: ALTO
- **Leer antes**:
  - `knowledge-base/04_modelo_de_datos.md` §Paciente, §Turno, §Bloqueo
  - `knowledge-base/05_reglas_de_negocio.md` §Agenda (RN-AG-01 a RN-AG-13), RN-RP-03
  - `knowledge-base/09_decisiones_y_supuestos.md` §DD-09, DD-10, DD-12, DD-17
  - `knowledge-base/08_arquitectura_propuesta.md` §Patrones aplicados, §Estrategia de tests
  - `knowledge-base/07_flujos_principales.md` §FL-03

### [C-10] `agenda-api-odontologo`
- **Estado**: `[ ]` pendiente
- **Scope**:
  - `GET /api/v1/agenda?desde&hasta` (turnos, sobreturnos marcados, bloqueos, cancelados filtrables; indicador de "nuevos" por reservas/cambios de pacientes).
  - `POST /api/v1/turnos` (manual y `esSobreturno`), `PATCH /api/v1/turnos/{id}/estado`, `POST /api/v1/turnos/{id}/reprogramar` (sin anticipación mínima para el odontólogo; conserva el token de gestión), `POST/DELETE /api/v1/bloqueos`.
  - Errores con código estable: 409 `TURNO_SOLAPADO`, `HORARIO_BLOQUEADO`, `BLOQUEO_CON_TURNOS` (con lista), `TRANSICION_INVALIDA`.
  - OpenAPI actualizado y `api-types.ts` regenerado.
  - Tests API/seguridad (MockMvc + Testcontainers) de US-012, US-021, US-022, US-023 y aislamiento por tenant.
- **Dependencias**: `C-09`
- **Governance**: MEDIO
- **Leer antes**:
  - `knowledge-base/06_funcionalidades.md` §Épica 3 (US-020 a US-023), US-012
  - `knowledge-base/05_reglas_de_negocio.md` §Agenda (RN-AG)
  - `knowledge-base/07_flujos_principales.md` §FL-03
  - `knowledge-base/08_arquitectura_propuesta.md` §OpenAPI

### [C-11] `frontend-agenda`
- **Estado**: `[ ]` pendiente
- **Scope**:
  - Feature `agenda/`: vista diaria por defecto en mobile y semanal desde `md`; tarjetas de turno con color + ícono + texto, sobreturno con borde punteado, bloqueo rayado, cancelados atenuados y ocultables; lectura sin scroll horizontal a 360 px; indicador de "nuevos"; caché offline de la última agenda (solo lectura).
  - Mockup revisado de vista diaria, semanal y detalle de turno antes de implementar.
  - Tests Vitest de render de componentes de agenda y fechas/TZ.
- **Dependencias**: `C-06`, `C-10`
- **Governance**: MEDIO
- **Leer antes**:
  - `design-system/README.md`, `design-system/tokens.css`, `design-system/components.html`
  - `knowledge-base/06_funcionalidades.md` §US-020
  - `knowledge-base/08_arquitectura_propuesta.md` §PWA y frontend mobile-first
  - `knowledge-base/07_flujos_principales.md` §FL-03

### [C-12] `frontend-turnos-bloqueos`
- **Estado**: `[ ]` pendiente
- **Scope**:
  - Features `turnos/` y `bloqueos/`: crear turno manual (prestación, hueco, paciente por teléfono), agregar sobreturno, confirmar/cancelar/marcar ausente, reprogramar, crear y eliminar bloqueos; manejo de errores 409 con mensajes y lista de conflictos de bloqueo.
  - Mockup revisado de formularios y diálogos antes de implementar.
  - Tests Vitest de validaciones y mapeo de errores.
- **Dependencias**: `C-11`
- **Governance**: MEDIO
- **Leer antes**:
  - `design-system/README.md`, `design-system/components.html`
  - `knowledge-base/06_funcionalidades.md` §US-012, US-021 a US-023
  - `knowledge-base/07_flujos_principales.md` §FL-03
  - `knowledge-base/05_reglas_de_negocio.md` §Agenda (RN-AG)

---

## FASE 4 — Reserva pública

> C-13 se ejecuta en paralelo con C-10 apenas termina C-09.

### [C-13] `reserva-publica-backend`
- **Estado**: `[ ]` pendiente
- **Scope**:
  - Migración: `token_gestion_turno` (hash SHA-256, `expira_at = fin`, `revocado_at`).
  - Endpoints públicos sin cuenta: `GET /api/v1/public/consultorios/{slug}` (nombre, dirección, teléfono, prestaciones visibles), `GET .../huecos`, `POST .../turnos` (crea turno `origen = LINK`, devuelve el token una sola vez; consentimiento de privacidad; 409 `HUECO_NO_DISPONIBLE`, `LIMITE_TURNOS`), `GET/POST /api/v1/public/turnos/{token}` para ver, reprogramar y cancelar (409 `FUERA_DE_PLAZO` con teléfono del consultorio; 404 uniforme ante token inválido).
  - Token de 256 bits (`SecureRandom`, base64url), reserva concurrente (exactamente una prospera), reserva desactivada si `reserva_publica_activa = false` o plan `VENCIDO`, dedupe por teléfono, límite 2 turnos futuros activos, body máx. 64 KB, rate limiting por IP/token, `.ics` descargable (P1).
  - Auditoría con `actor_tipo = PACIENTE`.
  - Tests: US-030, US-031, US-032, concurrencia, enumeración de tokens, aislamiento por tenant.
- **Dependencias**: `C-07`, `C-09`
- **Governance**: ALTO
- **Leer antes**:
  - `knowledge-base/05_reglas_de_negocio.md` §Reserva pública (RN-RP)
  - `knowledge-base/06_funcionalidades.md` §Épica 4 (US-030 a US-032)
  - `knowledge-base/07_flujos_principales.md` §FL-04, FL-05
  - `knowledge-base/08_arquitectura_propuesta.md` §Seguridad (tokens del paciente, rate limiting)
  - `knowledge-base/09_decisiones_y_supuestos.md` §DD-09, DD-13 y `11_marco_legal_argentino.md`

### [C-14] `frontend-reserva-publica`
- **Estado**: `[ ]` pendiente
- **Scope**:
  - Features `reserva-publica/` (`/c/{slug}`: elegir prestación, hueco, datos, consentimiento, confirmación con link `/t/{token}` para guardar) y `gestion-turno/` (`/t/{token}`: ver, reprogramar, cancelar; mensaje con teléfono si está fuera de plazo).
  - Mobile-first a 360 px, áreas táctiles >= 44 px, selector de horarios del sistema de diseño; sin cachear respuestas públicas en el Service Worker; objetivo de reserva < 90 s.
  - Mockup revisado de todo el flujo del paciente antes de implementar.
  - Tests Vitest de validaciones, estados de error y manejo de zona horaria.
- **Dependencias**: `C-05`, `C-13`
- **Governance**: MEDIO
- **Leer antes**:
  - `design-system/README.md`, `design-system/components.html`
  - `knowledge-base/06_funcionalidades.md` §US-030 a US-032
  - `knowledge-base/07_flujos_principales.md` §FL-04, FL-05
  - `knowledge-base/08_arquitectura_propuesta.md` §PWA y frontend mobile-first

---

## FASE 5 — Auditoría, exportación y plan

### [C-15] `historial-auditoria`
- **Estado**: `[ ]` pendiente
- **Scope**:
  - `GET /api/v1/turnos/{id}/historial` y listado paginado de eventos del consultorio (actor, fecha, valor anterior y nuevo, orden cronológico); aislamiento por tenant.
  - Feature `auditoria/` en el detalle del turno; mockup revisado antes de implementar.
  - Tests: US-040 (turno reservado por link y luego reprogramado muestra 2 eventos en orden).
- **Dependencias**: `C-10`, `C-11`
- **Governance**: MEDIO
- **Leer antes**:
  - `knowledge-base/06_funcionalidades.md` §US-040
  - `knowledge-base/05_reglas_de_negocio.md` §Auditoría (RN-AD)
  - `knowledge-base/04_modelo_de_datos.md` §EventoAuditoria
  - `design-system/README.md`

### [C-16] `exportacion-datos`
- **Estado**: `[ ]` pendiente
- **Scope**:
  - `POST /api/v1/exportacion`: ZIP por streaming con JSON y CSV por entidad, un PDF legible (`resumen.pdf`: consultorio, prestaciones, pacientes, listado de turnos y resumen de auditoría; lo genera el backend, librería a decidir al proponer, p. ej. OpenPDF) y `manifest.json` (cantidades que coinciden con la base); sin hashes ni tokens; 429 si hubo otra hace < 5 min; funciona en plan `VENCIDO`; evento `EXPORTACION_GENERADA`.
  - Botón y pantalla de exportación en `configuracion/` (feature `exportacion/`); mockup revisado antes de implementar.
  - Tests: US-050, contenido del ZIP (incluido el PDF: se genera, tiene las secciones mínimas y no contiene hashes ni tokens), rate limit, aislamiento por tenant.
- **Dependencias**: `C-08`, `C-13`
- **Governance**: ALTO
- **Leer antes**:
  - `knowledge-base/05_reglas_de_negocio.md` §Exportación (RN-EX)
  - `knowledge-base/06_funcionalidades.md` §US-050
  - `knowledge-base/07_flujos_principales.md` §FL-06
  - `knowledge-base/09_decisiones_y_supuestos.md` §DD-14
  - `knowledge-base/11_marco_legal_argentino.md`

### [C-17] `plan-precios-y-seguridad`
- **Estado**: `[ ]` pendiente
- **Scope**:
  - Lógica de `plan` (`PRUEBA`/`ACTIVO`/`VENCIDO`); tarea diaria programada que pasa a `VENCIDO` al vencer `prueba_hasta`; en `VENCIDO` la agenda queda en solo lectura (escrituras rechazadas), la reserva pública deja de tomar reservas y la exportación sigue habilitada; pase a `ACTIVO` manual con evento `actor_tipo = SISTEMA`.
  - Planes públicos: `GET /api/v1/public/planes` y página `/precios` en ARS con impuestos incluidos y fecha de actualización visible (sin cobro integrado); la prueba gratuita **no pide tarjeta ni medio de pago** y la página lo dice explícitamente; aviso en la app con los días de prueba restantes.
  - Página pública estática `/seguridad` (sin auth, US-062): tratamiento de datos, auditoría inmutable, exportación completa, hosting/tratamiento y lo que aún no está (sin 2FA en v1, sin revisión legal profesional todavía, PA-06); sin declarar cumplimiento no respaldado; fecha de última actualización visible; contenido en una fuente editable versionada (no hardcodeado en componentes).
  - Mockups revisados de `/precios`, `/seguridad` y del aviso antes de implementar.
  - Tests: US-060, US-061 con `Clock` inyectado; US-062 (página pública, muestra fecha y la sección "lo que aún no está", contenido coincide con la fuente editable).
- **Dependencias**: `C-08`, `C-10`, `C-13`, `C-16`
- **Governance**: MEDIO
- **Leer antes**:
  - `knowledge-base/05_reglas_de_negocio.md` §Plan y precios (RN-PL-01..06), RN-RP-11, RN-AD-02, RN-EX
  - `knowledge-base/06_funcionalidades.md` §US-060, US-061, US-062
  - `knowledge-base/07_flujos_principales.md` §FL-07
  - `knowledge-base/10_preguntas_abiertas.md` §PA-03, PA-06, PA-13, IN-03
  - `knowledge-base/11_marco_legal_argentino.md`
  - `knowledge-base/09_decisiones_y_supuestos.md` §DD-18
  - `design-system/README.md`

---

## FASE 6 — Cierre de v1

### [C-18] `e2e-hardening-release`
- **Estado**: `[ ]` pendiente
- **Scope**:
  - Playwright en viewport mobile: reserva por link de punta a punta y login -> crear turno; integrado al CI como humo previo a cada release.
  - Revisión de seguridad: CSP/cabeceras, rate limits, enmascarado de PII en logs, ausencia de tokens en `Referer`/caché, secretos solo por entorno; aislamiento entre tenants en toda la API.
  - Seed local completo para demo; README de arranque (`docker compose up --build`); textos de privacidad y términos como borrador marcado "pendiente de revisión legal" (PA-06), sin declarar cumplimiento no verificado.
  - Checklist previo al piloto (PA-02 hosting, PA-06 asesoría legal, validación de valores por defecto con odontólogos) documentado, sin resolverlo en código.
- **Dependencias**: `C-12`, `C-14`, `C-15`, `C-16`, `C-17`
- **Governance**: MEDIO
- **Leer antes**:
  - `knowledge-base/08_arquitectura_propuesta.md` §Seguridad, §Estrategia de tests
  - `knowledge-base/10_preguntas_abiertas.md` §PA-02, PA-05, PA-06
  - `knowledge-base/11_marco_legal_argentino.md`
  - `knowledge-base/01_vision_y_objetivos.md` (métricas del piloto)

---

## Resumen

| ID | Change | Fase | Dependencias | Governance |
|----|--------|------|--------------|------------|
| C-01 | foundation-setup | 1 | — | BAJO |
| C-02 | core-compartido-tenant | 1 | C-01 | CRITICO |
| C-03 | auditoria-base | 1 | C-02 | CRITICO |
| C-04 | auth-backend | 1 | C-02 | CRITICO |
| C-05 | frontend-shell-pwa | 1 | C-01 | BAJO |
| C-06 | frontend-auth | 1 | C-04, C-05 | ALTO |
| C-07 | consultorio-config-backend | 2 | C-03, C-04 | MEDIO |
| C-08 | frontend-configuracion | 2 | C-06, C-07 | MEDIO |
| C-09 | agenda-dominio-y-esquema | 3 | C-07 | ALTO |
| C-10 | agenda-api-odontologo | 3 | C-09 | MEDIO |
| C-11 | frontend-agenda | 3 | C-06, C-10 | MEDIO |
| C-12 | frontend-turnos-bloqueos | 3 | C-11 | MEDIO |
| C-13 | reserva-publica-backend | 4 | C-07, C-09 | ALTO |
| C-14 | frontend-reserva-publica | 4 | C-05, C-13 | MEDIO |
| C-15 | historial-auditoria | 5 | C-10, C-11 | MEDIO |
| C-16 | exportacion-datos | 5 | C-08, C-13 | ALTO |
| C-17 | plan-precios-y-seguridad | 5 | C-08, C-10, C-13, C-16 | MEDIO |
| C-18 | e2e-hardening-release | 6 | C-12, C-14, C-15, C-16, C-17 | MEDIO |

**Primer item elegible**: `C-01` (`foundation-setup`).

> Nota post-v1 (no son changes de este roadmap): el backlog diferido (recordatorios/WhatsApp, incluido el botón de compartir el link de reserva por WhatsApp; 2FA del odontólogo (PA-13), ambos fuera de v1 por decisión del usuario, DD-18; historia clínica y odontograma, cobros, obras sociales, ARCA, multi-profesional/multi-sillón y roles) está en `knowledge-base/06_funcionalidades.md` §Backlog post-v1 y en `discovery/informe-discovery.md` D.3, "Etapas posteriores", primera etapa posterior a la v1 (orden sugerido: WhatsApp recordatorios/confirmación, HC/odontograma, presupuestos, caja/MP, obras sociales base, multiprofesional/sillón y roles), y se planifica tras el piloto (PA-05).
