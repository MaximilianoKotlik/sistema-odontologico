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
5. Cerrar el change en el mismo PR de implementación, **antes de pedir la revisión**: con todo en verde, el autor suma un commit que marca el `[x]` en este archivo (`[x]` completado (PR #N, AAAA-MM-DD)) y archiva el change de OpenSpec (`openspec archive <change>`). El `[x]` llega a `main` solo con el merge, que exige la aprobación del líder. Si el líder pide cambios, se hacen en commits nuevos y el `[x]` queda puesto. No hace falta un PR aparte para el cierre.

Reglas del equipo:
- **Governance**: CRITICO = solo análisis hasta aprobación humana explícita; ALTO = proponer y esperar revisión antes de escribir; MEDIO = implementar con checkpoints; BAJO = autonomía si los tests pasan.
- **Mockups**: el sistema de diseño está en modo `system-and-mockups` (rev. 1, aprobado). Todo change con pantallas (C-06, C-07, C-09, C-13, C-14, C-16, C-17, C-18, C-19, C-20, C-21, C-22) exige un mockup revisado y aprobado **antes** de implementar la pantalla; el mockup se adjunta al PR.
- Conservá los IDs de las fichas al editar o reordenar. (Única excepción: la renumeración completa de la revisión del 2026-11-20, decidida por el usuario; el mapa de IDs anteriores está al final.) Si el proyecto usa ClickUp, este roadmap conserva el alcance y las dependencias; el responsable y el estado operativo se consultan en ClickUp (último dato en `.active-stack/roadmap-state.json`).
- Fuera de alcance v1 (no crear changes): WhatsApp, historia clínica/odontograma/presupuestos, Mercado Pago/cobros, obras sociales, ARCA, multi-profesional/multi-sillón, roles y recepción. Ver nota post-v1 al final.

---

## Árbol de dependencias

```
C-01 foundation-setup
  ├── C-02 core-compartido-tenant                       ← núcleo: tenant, errores, reloj, puerto de auditoría
  │     ├── C-03 auditoria-base                         ← paralelo con C-04
  │     ├── C-04 auth-sesion-tokens
  │     │     ├── C-05 auth-recuperacion-hardening      ← + C-03
  │     │     │     └── C-07 frontend-auth              ← + C-06
  │     │     │           └── C-09 frontend-configuracion ← + C-08
  │     │     │                 └── C-17 pacientes-revision ← + C-11
  │     │     └── C-08 consultorio-config-backend       ← + C-03
  │     │           └── C-10 agenda-esquema-dominio
  │     │                 └── C-11 agenda-casos-de-uso
  │     │                       ├── C-12 agenda-api-odontologo
  │     │                       │     └── C-13 frontend-agenda              ← + C-07
  │     │                       │           ├── C-14 frontend-turnos-bloqueos
  │     │                       │           └── C-18 historial-auditoria    ← + C-12
  │     │                       └── C-15 reserva-publica-backend           ← + C-08; paralelo con C-12
  │     │                             ├── C-16 frontend-reserva-publica    ← + C-06 (nada más)
  │     │                             ├── C-19 exportacion-datos           ← + C-09
  │     │                             │     └── C-22 pagina-seguridad      ← + C-03, C-06
  │     │                             └── C-20 plan-ciclo-de-vida          ← + C-09, C-12
  │     └── C-21 pagina-precios                         ← + C-06 (arranca temprano)
  └── C-06 frontend-shell-pwa                            ← paralelo con C-02

C-23 e2e-hardening-release  ← depende de C-14, C-16, C-17, C-18, C-19, C-20, C-21, C-22
```

### Paralelismo por fase

> Cada gate es un punto de sincronización. Los changes dentro de un gate pueden ejecutarse en paralelo (un PR por change). Un change arranca apenas sus dependencias reales están `[x]`; no espera al resto del gate.

```
GATE 0: ninguna
  → C-01 foundation-setup                         [Maxi]
  (propuestas de C-02/C-03 [Fran, Mati]; mockups de C-06 y C-21 [Nacho, Octi])

GATE 1: C-01 ✓
  → C-02 core-compartido-tenant                   [Fran]
  → C-06 frontend-shell-pwa                       [Nacho]

GATE 2: C-02 ✓                                    ← FORK
  → C-04 auth-sesion-tokens                       [Fran]
  → C-03 auditoria-base                           [Mati]
  → C-21 pagina-precios                           [Octi — si C-06 ✓]

GATE 3: C-03 ✓ + C-04 ✓
  → C-08 consultorio-config-backend               [Fran]
  → C-05 auth-recuperacion-hardening              [Mati]

GATE 4: C-05 ✓ + C-06 ✓ / C-08 ✓
  → C-07 frontend-auth                            [Nacho — requiere C-05 ✓ y C-06 ✓]
  → C-10 agenda-esquema-dominio                   [Fran — requiere C-08 ✓]

GATE 5: C-07 ✓ + C-10 ✓
  → C-09 frontend-configuracion                   [Nacho — si C-08 ✓]
  → C-11 agenda-casos-de-uso                      [Fran]

GATE 6: C-11 ✓                                    ← FORK
  → C-12 agenda-api-odontologo                    [Fran]
  → C-15 reserva-publica-backend                  [Mati]
  → C-17 pacientes-revision                       [Octi — si C-09 ✓]

GATE 7: C-12 ✓ / C-15 ✓                           ← FORK
  → C-13 frontend-agenda                          [Nacho — requiere C-07 ✓ y C-12 ✓]
  → C-16 frontend-reserva-publica                 [Octi — solo requiere C-06 ✓ y C-15 ✓]
  → C-19 exportacion-datos                        [Mati — si C-09 ✓]
  → C-20 plan-ciclo-de-vida                       [Fran — si C-09 ✓, C-12 ✓ y C-15 ✓]

GATE 8: C-13 ✓ / C-19 ✓                           ← FORK
  → C-14 frontend-turnos-bloqueos                 [Nacho — requiere C-13 ✓]
  → C-18 historial-auditoria                      [Mati — requiere C-12 ✓ y C-13 ✓]
  → C-22 pagina-seguridad                         [Octi — requiere C-19 ✓ (y C-03, C-06 ✓)]

GATE 9: C-14, C-16, C-17, C-18, C-19, C-20, C-21, C-22 ✓
  → C-23 e2e-hardening-release                    [Maxi; Fran, Mati, Nacho y Octi corrigen hallazgos]
```

### Camino crítico (10 changes — mínimo irreducible)

```
C-01 → C-02 → C-04 → C-08 → C-10 → C-11 → C-12 → C-13 → C-14* / C-18* → C-23
```

`*` C-14 y C-18 son ambos "penúltimos" (se pueden hacer en paralelo); C-22 también puede cerrar en ese mismo nivel (C-11 → C-15 → C-19 → C-22). Quedan fuera del camino crítico: C-03 y C-05 (paralelos a C-04/C-08), C-06/C-07/C-09 (frontend de base, absorbidos por la holgura del backend), C-21 (arranca en el GATE 2), C-15/C-16/C-17/C-19/C-20/C-22 (ramas paralelas a C-12 → C-13). Como el camino crítico tiene 10 changes, ningún plan cierra en menos de 10 pasos; el ancho máximo es 4 changes simultáneos (paso 8), así que con 4 personas programando y el líder revisando se alcanza ese mínimo.

### Plan del equipo (5 personas, 10 pasos)

Roles (los nombres se pueden reasignar sin tocar las dependencias):

| Persona | Rol | Changes |
|---------|-----|---------|
| Maxi | Líder e integrador: dueño del repo, aprueba propuestas CRITICO/ALTO, mockups y todos los PRs | C-01, C-23 |
| Fran | Backend núcleo (camino crítico de la agenda) | C-02, C-04, C-08, C-10, C-11, C-12, C-20 |
| Mati | Backend seguridad y datos; además revisa técnicamente los PRs de Fran para cubrir el camino crítico | C-03, C-05, C-15, C-19, C-18 |
| Nacho | Frontend de la app del odontólogo | C-06, C-07, C-09, C-13, C-14 |
| Octi | Frontend público y fullstack; responsable de preparar los mockups | C-21, C-17, C-16, C-22 |

| Paso | Maxi | Fran | Mati | Nacho | Octi |
|------|------|------|------|-------|------|
| 1 | **C-01** | propuesta C-02 | propuesta C-03 | mockup del app-shell (C-06) | mockup C-21 |
| 2 | revisión | **C-02** | propuesta C-05 | **C-06** | mockups C-07/C-09 |
| 3 | revisión | **C-04** | **C-03** | mockups C-13/C-14 | **C-21** |
| 4 | revisión | **C-08** | **C-05** | apoyo y tests | mockups C-16/C-17 |
| 5 | revisión | **C-10** | propuesta C-15 | **C-07** | mockups C-18/C-19/C-22 |
| 6 | revisión | **C-11** | propuesta C-19 | **C-09** | mockup C-20 |
| 7 | revisión | **C-12** | **C-15** | prepara C-13 sobre el contrato OpenAPI | **C-17** |
| 8 | revisión | **C-20** | **C-19** | **C-13** | **C-16** |
| 9 | prepara Playwright para C-23 | apoyo C-23 | **C-18** | **C-14** | **C-22** |
| 10 | **C-23** | corrige hallazgos | corrige hallazgos | corrige hallazgos | corrige hallazgos |

Reglas de coordinación del paralelismo:
- **Propuestas un paso antes**: los changes CRITICO y ALTO preparan su propuesta en el paso previo, para que la aprobación del líder no frene el change.
- **Prioridad de revisión**: los PRs del camino crítico (C-02, C-04, C-08, C-10, C-11, C-12, C-13, C-14/C-18) se revisan antes que el resto.
- **Migraciones Flyway**: cada change numera su migración provisoriamente y, al rebasar sobre `main` antes del merge, la pasa al siguiente número libre (una migración no mergeada todavía se puede renumerar; una mergeada nunca se edita).
- **Archivos compartidos**: `api-types.ts` se regenera tras cada rebase (no se resuelve a mano); el checkbox `[x]` de este archivo lo marca el autor del change en el commit de cierre de su PR, antes de pedir la revisión (paso 5); llega a `main` solo con el merge aprobado. Los PRs de solo propuesta o mockups no marcan `[x]`.
- **Trabajo anticipado**: se pueden preparar mockups, tests y propuestas antes de que se habilite un change, pero su PR se abre recién con las dependencias en `[x]`.

---

## FASE 1 — Fundación, núcleo y autenticación

### [C-01] `foundation-setup`
- **Estado**: `[x]` completado (PR #6, 2026-11-20)
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
- **Estado**: `[x]` completado (PR #12, 2026-11-20)
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
- **Estado**: `[x]` completado (PR #21, 2026-11-20)
- **Scope**:
  - Migración: `evento_auditoria` append-only (actor_tipo, entidad, acción, `valor_anterior/nuevo` JSONB, ip) con trigger `BEFORE UPDATE OR DELETE` que falla y rol `app_rw` solo con `INSERT, SELECT`.
  - Listener `@TransactionalEventListener(BEFORE_COMMIT)` que persiste en la misma transacción los eventos del puerto de C-02; enmascarado de PII en logs.
  - Tests de integración (Testcontainers): `UPDATE`/`DELETE` fallan aun con el rol de la app; el evento se revierte junto con la transacción; aislamiento por tenant.
  - La consulta (endpoint e UI, con filtros) queda en C-18.
- **Dependencias**: `C-02`
- **Governance**: CRITICO
- **Leer antes**:
  - `knowledge-base/05_reglas_de_negocio.md` §Auditoría (RN-AD)
  - `knowledge-base/04_modelo_de_datos.md` §EventoAuditoria
  - `knowledge-base/08_arquitectura_propuesta.md` §Persistencia y migraciones, §Patrones aplicados
  - `knowledge-base/11_marco_legal_argentino.md`

### [C-04] `auth-sesion-tokens`
- **Estado**: `[x]` completado (PR #16, 2026-11-20)
- **Scope**:
  - Migración: `usuario`, `refresh_token`.
  - `POST /api/v1/auth/registro` (crea usuario + consultorio en `PRUEBA`, `prueba_hasta = hoy + 30 d`, slug sugerido, consentimiento de términos; 409 `EMAIL_EN_USO`, 400 `PASSWORD_DEBIL`), `login`, `refresh` (rotación con detección de reuso que revoca toda la familia), `logout`.
  - Spring Security stateless: JWT HS256 15 min (`sub`, `cid`, `iat`, `exp`, `ver`), refresh opaco de 14 días en cookie `HttpOnly; Secure; SameSite=Strict; Path=/api/v1/auth`, BCrypt costo 12; filtro que resuelve `TenantContext` desde `cid`.
  - Cabeceras: CSP estricta, HSTS fuera de local, `X-Content-Type-Options`, `Referrer-Policy: no-referrer`; CORS solo `APP_FRONTEND_ORIGIN`.
  - Fuera de este change (van en C-05): recuperación, cambio de contraseña, bloqueo por fallos, rate limiting y eventos de auditoría de auth.
  - Tests: US-001; US-002 (login con access + cookie, reuso de refresh revoca todo); 401 sin token, 404 ante recurso de otro tenant; atributos de cookie; cabeceras de seguridad.
- **Dependencias**: `C-02`
- **Governance**: CRITICO
- **Leer antes**:
  - `knowledge-base/05_reglas_de_negocio.md` §Autenticación (RN-AU-01, RN-AU-02, RN-AU-06), RN-TE-02
  - `knowledge-base/06_funcionalidades.md` §US-001, US-002
  - `knowledge-base/07_flujos_principales.md` §FL-01
  - `knowledge-base/08_arquitectura_propuesta.md` §Seguridad
  - `knowledge-base/09_decisiones_y_supuestos.md` §DD-06, DD-15

### [C-05] `auth-recuperacion-hardening`
- **Estado**: `[x]` completado (PR #29, 2026-11-20)
- **Scope**:
  - Migración: `token_recuperacion` (hash, 30 min, un solo uso).
  - `POST /api/v1/auth/recuperacion` (202 idéntica exista o no el email) y `POST /api/v1/auth/recuperacion/confirmar` (400 `TOKEN_INVALIDO` si está usado o vencido; revoca todos los refresh tokens); email por SMTP (Mailpit en local).
  - Cambio de contraseña con sesión iniciada (US-005, RN-AU-05): `POST /api/v1/auth/cambiar-password` con la contraseña actual; valida RN-AU-01; revoca **todos** los refresh tokens e incrementa `ver` (invalida los access tokens emitidos); 400 `PASSWORD_ACTUAL_INVALIDA` / `PASSWORD_DEBIL`.
  - Bloqueo de cuenta tras 5 fallos consecutivos (423 `CUENTA_BLOQUEADA` 15 min, mensaje genérico) y rate limiting Bucket4j sobre `/api/v1/auth/**`.
  - Eventos de auditoría de auth vía el puerto de C-02 (RN-AD-04): `CUENTA_CREADA`, `LOGIN_EXITOSO`, `LOGIN_FALLIDO`, `PASSWORD_CAMBIADA`, `PASSWORD_RECUPERADA`; nunca contraseñas, hashes ni tokens (RN-AD-05).
  - Tests: US-002 (bloqueo tras 5 fallos), US-003, US-005, rate limit, eventos persistidos en la misma transacción y sin secretos.
- **Dependencias**: `C-03`, `C-04`
- **Governance**: CRITICO
- **Leer antes**:
  - `knowledge-base/05_reglas_de_negocio.md` §Autenticación (RN-AU-03 a RN-AU-06), RN-AD-04, RN-AD-05
  - `knowledge-base/06_funcionalidades.md` §US-002, US-003, US-005
  - `knowledge-base/07_flujos_principales.md` §FL-01
  - `knowledge-base/08_arquitectura_propuesta.md` §Seguridad, §Variables de entorno
  - `knowledge-base/10_preguntas_abiertas.md` §IN-01 (resuelta → DD-19 en `09_decisiones_y_supuestos.md`), PA-09

### [C-06] `frontend-shell-pwa`
- **Estado**: `[x]` completado (PR #17, 2026-11-20)
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

### [C-07] `frontend-auth`
- **Estado**: `[ ]` pendiente
- **Scope**:
  - Pantallas: registro (con aceptación de términos), login, recuperación y confirmación de recuperación, cambio de contraseña con sesión iniciada (US-005; al terminar fuerza nuevo login), cierre de sesión; manejo de `CUENTA_BLOQUEADA`, `EMAIL_EN_USO`, `PASSWORD_DEBIL`, `PASSWORD_ACTUAL_INVALIDA`, `TOKEN_INVALIDO`.
  - Guardas de ruta y restauración de sesión vía `/auth/refresh` al recargar.
  - Mockup revisado de todas las pantallas antes de implementar.
  - Tests Vitest de validaciones de formularios, flujo de refresh y cambio de contraseña; revisión de que el access token nunca toca el storage.
- **Dependencias**: `C-05`, `C-06`
- **Governance**: CRITICO (RN-AU-06: todo cambio en el dominio `auth`, incluido su frontend, requiere aprobación humana explícita)
- **Leer antes**:
  - `design-system/README.md`, `design-system/components.html`
  - `knowledge-base/06_funcionalidades.md` §US-001 a US-003, US-005
  - `knowledge-base/05_reglas_de_negocio.md` §Autenticación (RN-AU)
  - `knowledge-base/07_flujos_principales.md` §FL-01
  - `knowledge-base/08_arquitectura_propuesta.md` §PWA y frontend mobile-first, §Seguridad

---

## FASE 2 — Configuración del consultorio

### [C-08] `consultorio-config-backend`
- **Estado**: `[x]` completado (PR #15, 2026-11-20)
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

### [C-09] `frontend-configuracion`
- **Estado**: `[ ]` pendiente
- **Scope**:
  - Feature `configuracion/`: datos del consultorio y slug (con link público copiable `/c/{slug}`), prestaciones con duración, disponibilidad semanal, anticipaciones; onboarding guiado FL-02 tras el registro.
  - Mockup revisado de las pantallas y del onboarding antes de implementar.
  - Tests Vitest: validaciones (múltiplo de 5, franjas solapadas, slug).
- **Dependencias**: `C-07`, `C-08`
- **Governance**: MEDIO
- **Leer antes**:
  - `design-system/README.md`, `design-system/components.html`
  - `knowledge-base/06_funcionalidades.md` §US-004, US-010, US-011
  - `knowledge-base/07_flujos_principales.md` §FL-02
  - `knowledge-base/08_arquitectura_propuesta.md` §PWA y frontend mobile-first

---

## FASE 3 — Agenda del odontólogo

### [C-10] `agenda-esquema-dominio`
- **Estado**: `[x]` completado (PR #18, 2026-11-21)
- **Scope**:
  - Migración: `paciente` (único `(consultorio_id, dni)` por DD-24, consentimiento, `requiere_revision`), `turno` (`duracion_min`, `es_sobreturno`, `nota_interna VARCHAR(500)`, `version`, restricción `turno_sin_solapamiento` EXCLUDE GiST, FKs compuestas), `bloqueo` (índice GiST).
  - Dominio puro (sin Spring/JPA): entidades y value objects (`Intervalo` semiabierto, `Duracion` múltiplo de 5), `CalculadoraDeHuecos` (disponibilidad, bloqueos, paso, anticipación mínima, horizonte), `ValidadorDeSolapamiento`, `MaquinaDeEstadosTurno` (RESERVADO/CONFIRMADO/CANCELADO/AUSENTE), derivación `fin = inicio + duracion_min` (RN-AG-12), reglas de bloqueo (rechazo con lista de conflictos), invariantes de sobreturno.
  - Tests: unitarios de dominio con `Clock` inyectado (la mayoría) + integración de la exclusión GiST (la base rechaza el solapamiento aun salteando el servicio); ArchUnit "dominio sin Spring/JPA".
  - **Requisito del `proposal.md`**: justificar explícitamente la granularidad de la prevención de solapamientos. La práctica de referencia es "por profesional y por sillón/box"; la v1 la evalúa **por consultorio** porque tiene un único profesional y un único sillón implícito (RN-AG-13, DD-17). Citar [PA-14](knowledge-base/10_preguntas_abiertas.md) como la pregunta abierta que decide cuándo adelantar multi-sillón, y el punto de extensión de RN-AG-13 (dos restricciones de exclusión por `profesional_id` y `sillon_id`, migración aditiva).
- **Dependencias**: `C-08`
- **Governance**: ALTO
- **Leer antes**:
  - `knowledge-base/04_modelo_de_datos.md` §Paciente, §Turno, §Bloqueo
  - `knowledge-base/05_reglas_de_negocio.md` §Agenda (RN-AG-01 a RN-AG-13)
  - `knowledge-base/09_decisiones_y_supuestos.md` §DD-09, DD-10, DD-12, DD-17
  - `knowledge-base/10_preguntas_abiertas.md` §PA-14
  - `knowledge-base/08_arquitectura_propuesta.md` §Patrones aplicados, §Estrategia de tests

### [C-11] `agenda-casos-de-uso`
- **Estado**: `[x]` completado (PR #38, 2026-11-21)
- **Scope**:
  - Casos de uso de aplicación sobre el dominio de C-10: crear turno (manual, sobreturno y desde reserva pública, como puerto que consume C-15), cambiar estado, reprogramar (RN-AG-11), **ajustar duración** (RN-AG-12: recalcula `fin`, revalida RN-AG-01/03/04, múltiplos de 5), **editar nota interna** (`nota_interna`, máx. 500), crear y **eliminar** bloqueo.
  - Deduplicación de paciente por DNI (RN-RP-03, DD-24): reutiliza sin sobrescribir y marca `requiere_revision` si nombre/apellido difieren (sin mayúsculas, acentos ni espacios), guardando lo informado y el turno de origen (DD-38).
  - Optimistic locking (409 `CONFLICTO_CONCURRENCIA`, RN-GL-02).
  - Eventos de auditoría en la misma transacción, con valor anterior y nuevo: `TURNO_CREADO`, `TURNO_ESTADO_CAMBIADO`, `TURNO_REPROGRAMADO`, `TURNO_DURACION_CAMBIADA`, `TURNO_NOTA_CAMBIADA`, `BLOQUEO_CREADO`, `BLOQUEO_ELIMINADO`, `PACIENTE_CREADO`, `PACIENTE_MARCADO_PARA_REVISION` (RN-AD-01, RN-AD-04, RN-AD-05).
  - Tests de aplicación (Testcontainers): US-012, US-021 a US-025 a nivel caso de uso, dedupe con `requiere_revision`, concurrencia, rollback del cambio si falla la auditoría.
- **Dependencias**: `C-10`
- **Governance**: ALTO (concentra las invariantes de solapamiento y la auditoría de turnos que consumen la API, la reserva pública y la exportación)
- **Leer antes**:
  - `knowledge-base/05_reglas_de_negocio.md` §Agenda (RN-AG), RN-RP-03, §Auditoría (RN-AD-01, RN-AD-04, RN-AD-05), RN-GL-02
  - `knowledge-base/06_funcionalidades.md` §US-012, US-021 a US-025
  - `knowledge-base/07_flujos_principales.md` §FL-03
  - `knowledge-base/09_decisiones_y_supuestos.md` §DD-17, DD-24 (paciente por DNI; DD-09 quedó reemplazada)
  - `openspec/changes/archive/*-agenda-esquema-dominio/design.md` §Risks: **serializar las escrituras de agenda por consultorio** con el mismo bloqueo que C-08 usa en la grilla (`SELECT 1 FROM consultorio WHERE id = :id FOR UPDATE`), no con un lock consultivo aparte. Así turnos, bloqueos, reservas (C-15) y cambios de grilla quedan en una sola cola (revisión de Maxi en #18). La exclusión GiST no cruza tablas: sin ese bloqueo, un turno y un bloqueo concurrentes podrían pasar las dos validaciones.

### [C-12] `agenda-api-odontologo`
- **Estado**: `[ ]` pendiente
- **Scope**:
  - `GET /api/v1/agenda?desde&hasta` (turnos, sobreturnos marcados, bloqueos, cancelados filtrables; indicador de "nuevos" por reservas/cambios de pacientes).
  - `POST /api/v1/turnos` (manual y `esSobreturno`), `PATCH /api/v1/turnos/{id}/estado`, `POST /api/v1/turnos/{id}/reprogramar` (sin anticipación mínima para el odontólogo; conserva el token de gestión), `PATCH /api/v1/turnos/{id}/duracion` (RN-AG-12), `PATCH /api/v1/turnos/{id}/nota` (RN-AD-01), `POST/DELETE /api/v1/bloqueos` (el `DELETE` audita `BLOQUEO_ELIMINADO`).
  - `GET /api/v1/turnos/{id}`: detalle completo del turno para la pantalla de C-13 (paciente con DNI, teléfono y email, nota interna, motivo y autor de la cancelación, horario anterior si el paciente lo reprogramó). `GET /agenda` sigue liviano (sin DNI, teléfono, email ni texto de la nota) para no cargar datos personales de más en la caché offline. Decisión de Maxi en la revisión de #27.
  - `POST /api/v1/turnos/{id}/visto`: marca como visto el cambio hecho por el paciente desde el link (DD-30). Es idempotente. La marca es `turno.novedad_paciente_at TIMESTAMPTZ NULL`: la escribe C-15 y este endpoint la limpia. La crea la migración del primero que se mergee entre C-12 y C-15 (decisión de Maxi en #30). Si genera evento de auditoría (regla dura 7) lo define la propuesta de C-12.
  - Para los diálogos de C-14 (decisión de Maxi en #31):
    - `GET /api/v1/pacientes?q=`: busca por nombre, DNI o teléfono, para el buscador del turno manual.
    - `GET /api/v1/bloqueos?desde=`: lista los bloqueos futuros.
    - El 409 `TURNO_SOLAPADO` incluye el turno con el que choca.
  - `GET /api/v1/turnos/fuera-de-disponibilidad`: lista los turnos futuros activos que no sean sobreturnos y queden fuera de la grilla vigente (RN-AG-03). Reemplazar la disponibilidad (C-08) no los mueve ni los cancela; este endpoint alimenta el aviso de C-14 (decisión de Maxi en #15).
  - Errores con código estable: 409 `TURNO_SOLAPADO`, `HORARIO_BLOQUEADO`, `FUERA_DE_DISPONIBILIDAD`, `BLOQUEO_CON_TURNOS` (con lista), `TRANSICION_INVALIDA`, `CONFLICTO_CONCURRENCIA`; 400 por duración no múltiplo de 5 o nota > 500.
  - OpenAPI actualizado y `api-types.ts` regenerado.
  - Tests API/seguridad (MockMvc + Testcontainers) de US-012, US-021 a US-025 y aislamiento por tenant, incluidos el detalle y el visto (turno de otro consultorio = 404).
- **Dependencias**: `C-11`
- **Governance**: MEDIO
- **Leer antes**:
  - `knowledge-base/06_funcionalidades.md` §Épica 3 (US-020 a US-025), US-012
  - `knowledge-base/05_reglas_de_negocio.md` §Agenda (RN-AG), RN-AD-01, RN-AD-04
  - `knowledge-base/07_flujos_principales.md` §FL-03
  - `knowledge-base/08_arquitectura_propuesta.md` §OpenAPI

### [C-13] `frontend-agenda`
- **Estado**: `[ ]` pendiente
- **Scope**:
  - Feature `agenda/`: vista diaria por defecto en mobile y semanal desde `md`; tarjetas de turno con color + ícono + texto, sobreturno con borde punteado, bloqueo rayado, cancelados atenuados y ocultables; lectura sin scroll horizontal a 360 px; indicador de "nuevos"; caché offline de la última agenda (solo lectura).
  - Mockup revisado de vista diaria, semanal y detalle de turno antes de implementar.
  - Tests Vitest de render de componentes de agenda y fechas/TZ.
- **Dependencias**: `C-07`, `C-12`
- **Governance**: MEDIO
- **Leer antes**:
  - `design-system/README.md`, `design-system/tokens.css`, `design-system/components.html`
  - `knowledge-base/06_funcionalidades.md` §US-020
  - `knowledge-base/08_arquitectura_propuesta.md` §PWA y frontend mobile-first
  - `knowledge-base/07_flujos_principales.md` §FL-03

### [C-14] `frontend-turnos-bloqueos`
- **Estado**: `[ ]` pendiente
- **Scope**:
  - Features `turnos/` y `bloqueos/`: crear turno manual (prestación, hueco, paciente buscado por nombre, DNI o teléfono, con alta rápida por DNI, DD-24), agregar sobreturno, confirmar/cancelar/marcar ausente, reprogramar, **ajustar la duración** (selector en pasos de 5 min, muestra el nuevo fin), **agregar/editar la nota interna** (contador de 500), crear y eliminar bloqueos; manejo de errores 409 con mensajes y lista de conflictos de bloqueo.
  - **Aviso de turnos fuera de horario** (decisión de Maxi en #15):
    - Al guardar los horarios de atención en Configuración (pantalla de C-09), si quedan turnos futuros fuera de la grilla nueva, se avisa con la lista y un link a cada turno para reprogramarlo o cancelarlo.
    - Los datos salen de `GET /api/v1/turnos/fuera-de-disponibilidad` (C-12).
    - El guardado no se bloquea.
    - Va en C-14 y no en C-09 porque necesita la API de turnos de C-12. Se suma un estado al mockup de C-14.
  - Mockup revisado de formularios y diálogos antes de implementar.
  - Tests Vitest de validaciones y mapeo de errores.
- **Dependencias**: `C-13`
- **Governance**: MEDIO
- **Leer antes**:
  - `design-system/README.md`, `design-system/components.html`
  - `knowledge-base/06_funcionalidades.md` §US-012, US-021 a US-025
  - `knowledge-base/07_flujos_principales.md` §FL-03
  - `knowledge-base/05_reglas_de_negocio.md` §Agenda (RN-AG)

---

## FASE 4 — Reserva pública

> C-15 se ejecuta en paralelo con C-12 apenas termina C-11. C-16 arranca apenas terminan C-06 y C-15; no espera a la agenda del odontólogo.

### [C-15] `reserva-publica-backend`
- **Estado**: `[ ]` pendiente
- **Scope**:
  - Migración: `token_gestion_turno` (hash SHA-256, `expira_at = fin`, `revocado_at`).
  - Endpoints públicos sin cuenta: `GET /api/v1/public/consultorios/{slug}` (nombre, dirección, teléfono, prestaciones visibles), `GET .../huecos`, `POST .../turnos` (crea turno `origen = LINK` vía el caso de uso de C-11, devuelve el token una sola vez; consentimiento de privacidad; 409 `HUECO_NO_DISPONIBLE`, `LIMITE_TURNOS`), `GET/POST /api/v1/public/turnos/{token}` para ver, reprogramar y cancelar (409 `FUERA_DE_PLAZO` con teléfono del consultorio; 404 uniforme ante token inválido). La vista por token nunca expone `nota_interna`.
  - Token de 256 bits (`SecureRandom`, base64url), reserva concurrente (exactamente una prospera), dedupe por teléfono (con `requiere_revision`), límite 2 turnos futuros activos, body máx. 64 KB, rate limiting por IP/token, `.ics` descargable (P1).
  - Reserva desactivada si `reserva_publica_activa = false` o plan `VENCIDO`: la página informa que no toma turnos online y muestra el teléfono. **Criterio de aceptación explícito (RN-RP-11)**: en ese estado, los links de gestión existentes siguen permitiendo **cancelar** (dentro de RN-RP-04), pero no reservar ni reprogramar.
  - Auditoría con `actor_tipo = PACIENTE`.
  - Tests: US-030, US-031, US-032, RN-RP-11 (cancelar sí / reservar y reprogramar no, con reserva desactivada), concurrencia, enumeración de tokens, aislamiento por tenant.
- **Dependencias**: `C-08`, `C-11`
- **Governance**: ALTO
- **Leer antes**:
  - `knowledge-base/05_reglas_de_negocio.md` §Reserva pública (RN-RP)
  - `knowledge-base/06_funcionalidades.md` §Épica 4 (US-030 a US-032)
  - `knowledge-base/07_flujos_principales.md` §FL-04, FL-05
  - `knowledge-base/08_arquitectura_propuesta.md` §Seguridad (tokens del paciente, rate limiting)
  - `knowledge-base/09_decisiones_y_supuestos.md` §DD-09, DD-13 y `11_marco_legal_argentino.md`

### [C-16] `frontend-reserva-publica`
- **Estado**: `[ ]` pendiente
- **Scope**:
  - Features `reserva-publica/` (`/c/{slug}`: elegir prestación, hueco, datos, consentimiento, confirmación con link `/t/{token}` para guardar; estado "no toma turnos online" con teléfono) y `gestion-turno/` (`/t/{token}`: ver, reprogramar, cancelar; mensaje con teléfono si está fuera de plazo; con reserva desactivada solo ofrece cancelar).
  - Mobile-first a 360 px, áreas táctiles >= 44 px, selector de horarios del sistema de diseño; sin cachear respuestas públicas en el Service Worker; objetivo de reserva < 90 s.
  - Mockup revisado de todo el flujo del paciente antes de implementar.
  - Tests Vitest de validaciones, estados de error y manejo de zona horaria.
- **Dependencias**: `C-06`, `C-15`
- **Governance**: MEDIO
- **Leer antes**:
  - `design-system/README.md`, `design-system/components.html`
  - `knowledge-base/06_funcionalidades.md` §US-030 a US-032
  - `knowledge-base/07_flujos_principales.md` §FL-04, FL-05
  - `knowledge-base/08_arquitectura_propuesta.md` §PWA y frontend mobile-first

---

## FASE 5 — Pacientes, auditoría, exportación y plan

> C-21 no depende de la agenda: puede arrancar en el GATE 2 (solo requiere C-02 y C-06).

### [C-17] `pacientes-revision`
- **Estado**: `[ ]` pendiente
- **Scope**:
  - Backend: `GET /api/v1/pacientes?requiereRevision=true` (paginado, por tenant), `PATCH /api/v1/pacientes/{id}` (corrige nombre, apellido y email; el teléfono E.164 no se edita en v1) y `POST /api/v1/pacientes/{id}/confirmar-revision`; ambas acciones ponen `requiere_revision = false` y auditan `PACIENTE_REVISADO` con solo los campos modificados (RN-AD-05); 404 ante paciente de otro consultorio (RN-TE-04); PII enmascarada en logs (RN-GL-01).
  - Frontend: feature `pacientes/` con listado, filtro "requiere revisión" (con contador visible desde la agenda) y acción de confirmar o corregir; mockup revisado antes de implementar.
  - Tests: US-070, auditoría, aislamiento por tenant; Vitest del filtro y del formulario.
- **Dependencias**: `C-09`, `C-11`
- **Governance**: MEDIO
- **Leer antes**:
  - `design-system/README.md`, `design-system/components.html`
  - `knowledge-base/05_reglas_de_negocio.md` RN-RP-03, RN-AD-05, RN-TE-04, RN-GL-01
  - `knowledge-base/06_funcionalidades.md` §US-070
  - `knowledge-base/04_modelo_de_datos.md` §Paciente
  - `knowledge-base/10_preguntas_abiertas.md` §PA-01

### [C-18] `historial-auditoria`
- **Estado**: `[ ]` pendiente
- **Scope**:
  - `GET /api/v1/turnos/{id}/historial` y `GET /api/v1/auditoria` paginado con **filtros por turno, rango de fechas y tipo de acción** (RN-AD-06): actor, fecha, valor anterior y nuevo, orden cronológico; aislamiento por tenant.
  - Feature `auditoria/`: historial en el detalle del turno y listado del consultorio con los tres filtros; mockup revisado antes de implementar.
  - Tests: US-040 (turno reservado por link y luego reprogramado muestra 2 eventos en orden; filtros por turno, rango y acción, combinados y vacíos).
- **Dependencias**: `C-12`, `C-13`
- **Governance**: MEDIO
- **Leer antes**:
  - `knowledge-base/06_funcionalidades.md` §US-040
  - `knowledge-base/05_reglas_de_negocio.md` §Auditoría (RN-AD)
  - `knowledge-base/04_modelo_de_datos.md` §EventoAuditoria
  - `design-system/README.md`

### [C-19] `exportacion-datos`
- **Estado**: `[ ]` pendiente
- **Scope**:
  - `POST /api/v1/exportacion`: ZIP por streaming con JSON y CSV por entidad, un PDF legible (`resumen.pdf`: consultorio, prestaciones, pacientes, listado de turnos y resumen de auditoría; lo genera el backend, librería a decidir al proponer, p. ej. OpenPDF) y `manifest.json` (cantidades que coinciden con la base); sin hashes ni tokens; 429 si hubo otra hace < 5 min; funciona en plan `VENCIDO`; evento `EXPORTACION_GENERADA`.
  - Botón y pantalla de exportación en `configuracion/` (feature `exportacion/`); mockup revisado antes de implementar.
  - Tests: US-050, contenido del ZIP (incluido el PDF: se genera, tiene las secciones mínimas y no contiene hashes ni tokens), rate limit, aislamiento por tenant.
- **Dependencias**: `C-09`, `C-15`
- **Governance**: ALTO
- **Leer antes**:
  - `knowledge-base/05_reglas_de_negocio.md` §Exportación (RN-EX)
  - `knowledge-base/06_funcionalidades.md` §US-050
  - `knowledge-base/07_flujos_principales.md` §FL-06
  - `knowledge-base/09_decisiones_y_supuestos.md` §DD-14
  - `knowledge-base/11_marco_legal_argentino.md`

### [C-20] `plan-ciclo-de-vida`
- **Estado**: `[ ]` pendiente
- **Scope**:
  - Lógica de `plan` (`PRUEBA`/`ACTIVO`/`VENCIDO`); tarea diaria programada (FL-07, idempotente) que pasa a `VENCIDO` al vencer `prueba_hasta` y audita `PLAN_VENCIDO` con `actor_tipo = SISTEMA`; pase a `ACTIVO` manual como operación interna auditada (`SISTEMA`).
  - Bloqueo de escrituras en `VENCIDO`: guarda transversal en backend (RN-GL-04) que rechaza con 403 `PLAN_VENCIDO` toda escritura privada (agenda, bloqueos, pacientes, configuración) salvo una lista explícita de permitidas (lectura, exportación, login/logout/cambio de contraseña); como es genérica, alcanza también a los endpoints que se agreguen después. La reserva pública deja de tomar reservas.
  - **Criterio de aceptación explícito (RN-RP-11)**: con plan `VENCIDO`, los links de gestión `/t/{token}` existentes siguen permitiendo **cancelar** (dentro de RN-RP-04), pero no reprogramar ni reservar.
  - Aviso en la app con los días de prueba restantes (US-061) y estado `VENCIDO` en solo lectura; mockup revisado antes de implementar.
  - Tests con `Clock` inyectado: US-061, idempotencia de la tarea, matriz de endpoints permitidos/bloqueados en `VENCIDO`, exportación habilitada, RN-RP-11.
- **Dependencias**: `C-09`, `C-12`, `C-15`
- **Governance**: CRITICO (equivale a Billing: bloquea escrituras en toda la agenda)
- **Leer antes**:
  - `knowledge-base/05_reglas_de_negocio.md` §Plan y precios (RN-PL-02 a RN-PL-04), RN-RP-11, RN-EX-03, RN-GL-04
  - `knowledge-base/06_funcionalidades.md` §US-061
  - `knowledge-base/07_flujos_principales.md` §FL-07
  - `knowledge-base/10_preguntas_abiertas.md` §PA-03
  - `design-system/README.md`

### [C-21] `pagina-precios`
- **Estado**: `[x]` completado (PR #33, 2026-11-20)
- **Scope**:
  - `GET /api/v1/public/planes`: planes en ARS con impuestos incluidos y fecha de actualización, desde una fuente de configuración versionada (sin cobro integrado, RN-PL-04).
  - Página pública `/precios` (sin auth): planes, qué incluye cada uno y la prueba gratuita de 30 días que **no pide tarjeta ni medio de pago**, dicho explícitamente (RN-PL-02); fecha de actualización visible.
  - Mockup revisado de `/precios` antes de implementar.
  - Tests: US-060 (endpoint público sin auth, contenido en ARS, fecha visible, leyenda "sin tarjeta"); Vitest de render.
- **Dependencias**: `C-02`, `C-06`
- **Governance**: MEDIO
- **Leer antes**:
  - `design-system/README.md`, `design-system/components.html`
  - `knowledge-base/05_reglas_de_negocio.md` §Plan y precios (RN-PL-01, RN-PL-02, RN-PL-04)
  - `knowledge-base/06_funcionalidades.md` §US-060
  - `knowledge-base/10_preguntas_abiertas.md` §PA-03
  - `knowledge-base/09_decisiones_y_supuestos.md` §DD-18

### [C-22] `pagina-seguridad`
- **Estado**: `[ ]` pendiente
- **Scope**:
  - Página pública estática `/seguridad` (sin auth, US-062): tratamiento de datos, auditoría inmutable, exportación completa, hosting/tratamiento y lo que aún no está (sin 2FA en v1, sin revisión legal profesional todavía, PA-06); sin declarar cumplimiento no respaldado; fecha de última actualización visible; contenido en una fuente editable versionada (no hardcodeado en componentes).
  - Cada afirmación se contrasta con la capacidad implementada (RN-PL-06): auditoría append-only (C-03), exportación (C-19), aislamiento por tenant (C-02).
  - Mockup revisado de `/seguridad` antes de implementar.
  - Tests: US-062 (página pública, muestra fecha y la sección "lo que aún no está", contenido coincide con la fuente editable).
- **Dependencias**: `C-03`, `C-06`, `C-19`
- **Governance**: ALTO (riesgo legal: afirmaciones públicas sobre tratamiento de datos de salud)
- **Leer antes**:
  - `knowledge-base/05_reglas_de_negocio.md` §Plan y precios (RN-PL-05, RN-PL-06), RN-AD-02, RN-EX-01
  - `knowledge-base/06_funcionalidades.md` §US-062
  - `knowledge-base/10_preguntas_abiertas.md` §PA-06, PA-13
  - `knowledge-base/11_marco_legal_argentino.md`
  - `knowledge-base/09_decisiones_y_supuestos.md` §DD-18
  - `design-system/README.md`

---

## FASE 6 — Cierre de v1

### [C-23] `e2e-hardening-release`
- **Estado**: `[ ]` pendiente
- **Scope**:
  - Playwright en viewport mobile: reserva por link de punta a punta y login -> crear turno; integrado al CI como humo previo a cada release.
  - Revisión de seguridad: CSP/cabeceras, rate limits, enmascarado de PII en logs, ausencia de tokens en `Referer`/caché, secretos solo por entorno; aislamiento entre tenants en toda la API; matriz completa de escrituras bloqueadas en plan `VENCIDO` (incluidos los endpoints de C-17).
  - Revisión de `/seguridad` contra el código (RN-PL-06) antes del release.
  - Seed local completo para demo; README de arranque (`docker compose up --build`); textos de privacidad y términos como borrador marcado "pendiente de revisión legal" (PA-06), sin declarar cumplimiento no verificado.
  - Checklist previo al lanzamiento documentado, sin resolverlo en código: revisión legal de DD-28 y validación de valores por defecto con odontólogos.
  - **Criterio de salida de la v1 (DD-40)**: no se cargan pacientes reales ni se publica `/seguridad` hasta cumplir:
    - VPS contratado con los datos en Argentina;
    - backup diario cifrado de Postgres en Argentina, retención de 30 días y una restauración de prueba hecha;
    - TLS activo en el dominio de producción;
    - proveedor SMTP elegido y declarado en la política de privacidad;
    - email de seguridad de `/seguridad` definido.
  - Checklist de despliegue, IP real detrás del reverse proxy (viene de C-05): el rate limit por IP usa `getRemoteAddr()`, y detrás del proxy del VPS todos los consultorios compartirían un mismo cupo (10 logins por minuto y 5 registros por hora). Configurar `server.forward-headers-strategy` (`native` o `framework`, según el proxy) y un proxy que **reemplace** `X-Forwarded-For` en vez de concatenarlo, y verificar con un test de humo que dos IPs distintas tengan cupos separados. Detalle en `backend/README.md`.
- **Dependencias**: `C-14`, `C-16`, `C-17`, `C-18`, `C-19`, `C-20`, `C-21`, `C-22`
- **Governance**: MEDIO
- **Leer antes**:
  - `knowledge-base/08_arquitectura_propuesta.md` §Seguridad, §Estrategia de tests
  - `knowledge-base/10_preguntas_abiertas.md` §PA-05, PA-06
  - `knowledge-base/09_decisiones_y_supuestos.md` §DD-40 (requisitos de infraestructura antes del lanzamiento)
  - `knowledge-base/11_marco_legal_argentino.md`
  - `knowledge-base/01_vision_y_objetivos.md` (métricas del piloto)

---

## Resumen

| ID | Change | Fase | Dependencias | Governance |
|----|--------|------|--------------|------------|
| C-01 | foundation-setup | 1 | — | BAJO |
| C-02 | core-compartido-tenant | 1 | C-01 | CRITICO |
| C-03 | auditoria-base | 1 | C-02 | CRITICO |
| C-04 | auth-sesion-tokens | 1 | C-02 | CRITICO |
| C-05 | auth-recuperacion-hardening | 1 | C-03, C-04 | CRITICO |
| C-06 | frontend-shell-pwa | 1 | C-01 | BAJO |
| C-07 | frontend-auth | 1 | C-05, C-06 | CRITICO |
| C-08 | consultorio-config-backend | 2 | C-03, C-04 | MEDIO |
| C-09 | frontend-configuracion | 2 | C-07, C-08 | MEDIO |
| C-10 | agenda-esquema-dominio | 3 | C-08 | ALTO |
| C-11 | agenda-casos-de-uso | 3 | C-10 | ALTO |
| C-12 | agenda-api-odontologo | 3 | C-11 | MEDIO |
| C-13 | frontend-agenda | 3 | C-07, C-12 | MEDIO |
| C-14 | frontend-turnos-bloqueos | 3 | C-13 | MEDIO |
| C-15 | reserva-publica-backend | 4 | C-08, C-11 | ALTO |
| C-16 | frontend-reserva-publica | 4 | C-06, C-15 | MEDIO |
| C-17 | pacientes-revision | 5 | C-09, C-11 | MEDIO |
| C-18 | historial-auditoria | 5 | C-12, C-13 | MEDIO |
| C-19 | exportacion-datos | 5 | C-09, C-15 | ALTO |
| C-20 | plan-ciclo-de-vida | 5 | C-09, C-12, C-15 | CRITICO |
| C-21 | pagina-precios | 5 | C-02, C-06 | MEDIO |
| C-22 | pagina-seguridad | 5 | C-03, C-06, C-19 | ALTO |
| C-23 | e2e-hardening-release | 6 | C-14, C-16, C-17, C-18, C-19, C-20, C-21, C-22 | MEDIO |

**Primer item elegible**: `C-01` (`foundation-setup`).

> Renumeración (revisión del 2026-11-20, decisión del usuario). IDs anteriores → actuales: C-01 → C-01; C-02 → C-02; C-03 → C-03; C-04 `auth-backend` → C-04 `auth-sesion-tokens` + C-05 `auth-recuperacion-hardening`; C-05 → C-06; C-06 → C-07; C-07 → C-08; C-08 → C-09; C-09 `agenda-dominio-y-esquema` → C-10 `agenda-esquema-dominio` + C-11 `agenda-casos-de-uso`; C-10 → C-12; C-11 → C-13; C-12 → C-14; C-13 → C-15; C-14 → C-16; C-15 → C-18; C-16 → C-19; C-17 `plan-precios-y-seguridad` → C-20 `plan-ciclo-de-vida` + C-21 `pagina-precios` + C-22 `pagina-seguridad`; C-18 → C-23. Nuevo: C-17 `pacientes-revision`.

> Nota post-v1 (no son changes de este roadmap): el backlog diferido (recordatorios/WhatsApp, incluido el botón de compartir el link de reserva por WhatsApp; 2FA del odontólogo (PA-13), ambos fuera de v1 por decisión del usuario, DD-18; historia clínica y odontograma, cobros, obras sociales, ARCA, multi-profesional/multi-sillón y roles) está en `knowledge-base/06_funcionalidades.md` §Backlog post-v1 y en `docs/discovery/informe-discovery.md` D.3, "Etapas posteriores", primera etapa posterior a la v1 (orden sugerido: WhatsApp recordatorios/confirmación, HC/odontograma, presupuestos, caja/MP, obras sociales base, multiprofesional/sillón y roles), y se planifica tras el piloto (PA-05).
