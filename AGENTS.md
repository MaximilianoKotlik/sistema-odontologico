<!-- active-stack:agent-instruction -->
# Muelas Felices — Agenda Odontológica SaaS (repo `sistema-odontologico`) — Instrucciones para Agentes

> Este archivo (y su copia `CLAUDE.md`) es lo PRIMERO que todo agente lee al entrar al repo.
> Generado a partir de `knowledge-base/` y `CHANGES.md`. No editar a mano sin re-sincronizar ambos archivos.

SaaS web multi-consultorio para odontólogos independientes de Argentina. Cada consultorio es un tenant con un único usuario (el odontólogo). La v1 resuelve **solo la agenda**: agenda diaria y semanal, bloqueos, sobreturnos marcados, duración por prestación, prevención de solapamientos y una página pública donde el paciente, sin cuenta, reserva, reprograma o cancela su turno. Diferenciador: transparencia (precios públicos en ARS, exportación de datos, auditoría, cumplimiento legal declarado con honestidad). Idioma de producto y documentación: español rioplatense (es-AR).

**Equipo**: dueño + 4 colaboradores vía GitHub (`MaximilianoKotlik/sistema-odontologico`). `main` está protegida; todo PR requiere aprobación del dueño (`.github/CODEOWNERS`).

---

## Stack Tecnológico

| Capa | Tecnologías | Versión mínima |
|------|-------------|----------------|
| Lenguaje backend | Java | 21 (LTS) |
| Framework backend | Spring Boot (Web, Validation, Data JPA, Security, Actuator, Mail) | 3.5 |
| Build backend | Gradle (Kotlin DSL, wrapper versionado) | 9.9 |
| Documentación de API | springdoc-openapi (OpenAPI 3 + Swagger UI) | 2.8 |
| Base de datos | PostgreSQL (extensión `btree_gist` para exclusión de solapamientos) | 16 |
| Migraciones | Flyway (versión del BOM de Spring Boot) | 11 |
| Autenticación | Spring Security + JWT propio (access + refresh), BCrypt | — |
| Lenguaje frontend | TypeScript (strict), **sin framework** | 5.9 |
| Bundler / dev server | Vite | 7 |
| Estilos | Tailwind CSS | 3.4 |
| PWA | Web App Manifest + Service Worker (`vite-plugin-pwa` / Workbox) | — |
| Tests backend | JUnit 5, Mockito, AssertJ, Spring Boot Test, Testcontainers (PostgreSQL) | — |
| Tests frontend | Vitest + jsdom | 4 |
| Contenedores | Docker + Docker Compose (postgres + backend + frontend + mailpit) | Compose v2 |

Detalle completo: [knowledge-base/02_descripcion_general.md](knowledge-base/02_descripcion_general.md)

---

## Base de Conocimiento

La fuente de verdad del dominio vive en `knowledge-base/` ([README](knowledge-base/README.md)). **Leé el archivo relevante ANTES de implementar.**

| Archivo | Cuándo leerlo |
|---------|---------------|
| [01_vision_y_objetivos.md](knowledge-base/01_vision_y_objetivos.md) | Entender propósito, alcance y posicionamiento |
| [03_actores_y_roles.md](knowledge-base/03_actores_y_roles.md) | Auth, permisos, actores |
| [04_modelo_de_datos.md](knowledge-base/04_modelo_de_datos.md) | Entidades, ERD, migraciones |
| [05_reglas_de_negocio.md](knowledge-base/05_reglas_de_negocio.md) | Reglas codificadas (RN-XX) |
| [06_funcionalidades.md](knowledge-base/06_funcionalidades.md) | Historias de usuario y criterios Given/When/Then |
| [07_flujos_principales.md](knowledge-base/07_flujos_principales.md) | Flujos E2E |
| [08_arquitectura_propuesta.md](knowledge-base/08_arquitectura_propuesta.md) | Patrones, estructura, variables de entorno |
| [09_decisiones_y_supuestos.md](knowledge-base/09_decisiones_y_supuestos.md) | Decisiones de diseño (DD-XX) y supuestos (SU-XX) |
| [10_preguntas_abiertas.md](knowledge-base/10_preguntas_abiertas.md) | Inconsistencias y preguntas a resolver ANTES de codear |
| [11_marco_legal_argentino.md](knowledge-base/11_marco_legal_argentino.md) | Datos personales, Leyes 25.326 y 26.529 |

**Sistema de diseño**: [design-system/README.md](design-system/README.md) (modo `system-and-mockups`, rev. 1 aprobada). Todo change con pantallas necesita un mockup revisado y aprobado antes de implementar; el sistema de diseño aprobado es la base visual.

> Antes de arrancar los changes afectados, resolver las preguntas **Alta** abiertas: hoy solo queda **PA-02** acotada a país del VPS y backups (DD-25). PA-01 → DD-24, PA-03 → DD-22, PA-04..PA-06 → DD-26..DD-28 de [10_preguntas_abiertas.md](knowledge-base/10_preguntas_abiertas.md). Las inconsistencias IN-01..IN-03 están **resueltas** (DD-19, DD-20, DD-21 en [09_decisiones_y_supuestos.md](knowledge-base/09_decisiones_y_supuestos.md)).

**Discovery**: [docs/discovery/](docs/discovery/) (`discovery.md` e `informe-discovery.md`).

---

## Skills Disponibles

Fuente de verdad: [.atl/skill-registry.md](.atl/skill-registry.md). Solo se listan skills presentes en el registro.

| Agente | Rol | Skills que carga |
|--------|-----|------------------|
| **Backend Core** | Spring Boot, dominio, JPA, Postgres/Flyway, tests | `springboot-patterns`, `springboot-tdd`, `springboot-security`, `tdd`, `supabase-postgres-best-practices` (solo Postgres plano, **sin RLS**), `jpa-patterns`, `java-springboot` |
| **Backend Aux** | Infraestructura, contenedores, seguridad | `docker-patterns`, `springboot-security` |
| **Frontend** | TypeScript vanilla, Vite, Tailwind, PWA, accesibilidad | `typescript-advanced-types`, `javascript-pro`, `responsive-design`, `accessibility`, `web-design-guidelines` |
| **QA / E2E** | Pruebas de navegador y E2E | `playwright-best-practices`, `e2e-testing-patterns`, `playwright-cli` |
| **Diseño** | Identidad visual, pantallas, pulido de UI | `design-system`, `design-taste-frontend`, `emil-design-eng`, `impeccable` |
| **Fundación** | Contexto e instrucciones del proyecto | `kb-creator`, `roadmap-generator`, `agent-instruction`, `skill-creator` |

Cargá la skill correspondiente al contexto ANTES de escribir código.

> Los compact rules de cada skill los resuelve el orquestador desde [.atl/skill-registry.md](.atl/skill-registry.md) (generado por `skill-registry` y versionado en el repo, con rutas `~` portables). Ahí también está la justificación de cada skill recomendada (instalada, reutilizada o descartada). Esta tabla solo mapea skill→rol.

---

## Roadmap de Changes

El plan de implementación completo está en [CHANGES.md](CHANGES.md). Resumen:

- **Total**: 23 changes (C-01..C-23) en 6 fases: 1 Fundación, núcleo y autenticación (C-01..C-07) · 2 Configuración del consultorio (C-08, C-09) · 3 Agenda del odontólogo (C-10..C-14) · 4 Reserva pública (C-15, C-16) · 5 Pacientes, auditoría, exportación y plan (C-17..C-22) · 6 Cierre de v1 (C-23).
- **Camino crítico**: `C-01 → C-02 → C-04 → C-08 → C-10 → C-11 → C-12 → C-13 → C-14 / C-18 → C-23`.
- **Primer change**: `C-01` (`foundation-setup`).
- **Governance**: cada change declara su nivel (BAJO, MEDIO, ALTO, CRITICO) en CHANGES.md; aplicar el comportamiento del global según ese nivel.

**Antes de iniciar un item**: leé [CHANGES.md](CHANGES.md), verificá que las dependencias estén `[x]` y leé los archivos de "Leer antes".

---

## Equipo e identificación del programador

Cada integrante tiene un rol fijo y una lista de changes en [CHANGES.md](CHANGES.md) §Plan del equipo. **Al iniciar una sesión, el agente identifica a la persona** por el nombre que dice ("soy Fran") o, si no lo dice, por `git config user.name` / `gh api user --jq .login`, y le responde con su situación actual.

| Nombre | GitHub | Rol | Changes |
|--------|--------|-----|---------|
| Maxi | `MaximilianoKotlik` | Líder e integrador, code owner | C-01, C-23 + aprobación de propuestas, mockups y PRs |
| Fran | `FrancisCMarsala` | Backend núcleo (camino crítico) | C-02, C-04, C-08, C-10, C-11, C-12, C-20 |
| Mati | `MatiBarfi` | Backend seguridad y datos | C-03, C-05, C-15, C-19, C-18 |
| Nacho | `nachitobbarrera` | Frontend de la app | C-06, C-07, C-09, C-13, C-14 |
| Octi | `Octavio221218` | Frontend público y fullstack | C-21, C-17, C-16, C-22 |

Qué responde el agente al identificar a alguien:

1. Leer [CHANGES.md](CHANGES.md) (estados `[x]`) y `git log origin/main` para ver qué está mergeado; revisar con `gh pr list` qué PRs abiertos tiene esa persona.
2. Decirle **su tarea actual**: el primer change de su lista que no esté `[x]`. Si sus dependencias están `[x]` → puede implementarlo. Si no → indicarle qué trabajo anticipado le toca según la tabla de pasos (propuesta, mockup o tests) y de qué change o persona está esperando.
3. Recordarle el ciclo del PR:
   - Rama `feat/C-NN-nombre` creada desde `main` actualizado.
   - Si el change es CRITICO/ALTO o tiene pantallas: primero propuesta y/o mockup en un **PR en borrador**; no se escribe código de producción hasta que Maxi lo apruebe.
   - Implementar con Strict TDD, marcar el PR como listo y pedir la revisión a Maxi.
   - Cuando Maxi aprueba el PR (o confirma que no quedan cambios pedidos), el autor suma el commit de cierre en el mismo PR: marca `[x]` completado (PR #N, fecha) en CHANGES.md y archiva el change (`openspec archive <change>`), p. ej. `docs: cierre de C-NN (C-NN)`. Nunca antes de la aprobación ni en un PR aparte, y nunca en PRs de solo propuesta o mockup. Como `main` descarta las aprobaciones viejas al recibir commits nuevos, Maxi vuelve a aprobar ese último commit y mergea.
4. Si la persona pide un change que no es suyo, avisarle a quién está asignado y no empezarlo sin el OK de Maxi.

---

## Reglas Duras (específicas del proyecto)

> Reglas globales ya definidas en el archivo global de instrucciones del cliente activo (orquestador, governance, TDD, engram): el proyecto las hereda. Acá viven solo las reglas **específicas de este proyecto** + las universales que el global no cubra. Son contrato; romperlas es un defecto.

### Flujo

1. **NUNCA** escribir código de producción sin un test que falle primero → Strict TDD: red, green, triangulate, refactor, con el refactor DENTRO del ciclo (esto prevalece sobre la skill `tdd`, que lo deja afuera).
2. **NUNCA** commitear a `main` ni pushear sin pedido explícito → trabajar en rama `feat/C-NN-nombre`, un change por PR, merge solo con aprobación del dueño.
3. **NUNCA** commits con mensajes libres → conventional commits en español que referencien el change (ej. `feat(agenda): bloquear horarios (C-11)`).
4. **NUNCA** agregar líneas `Co-authored-by` de IA en los commits → los commits llevan solo autoría humana.

### Backend

5. **NUNCA** `ddl-auto=update` ni editar una migración ya mergeada → crear una nueva migración Flyway `V{n}__desc.sql`.
6. **NUNCA** una query sin filtrar por `consultorio_id` → tomar el tenant desde `TenantContext` y agregar un test de aislamiento (otro consultorio = 404) en cada endpoint.
7. **NUNCA** modificar turno, bloqueo, configuración o paciente sin evento de auditoría → emitirlo en la misma transacción; el log es append-only.
8. **NUNCA** H2 ni mocks de repositorio para probar persistencia → Testcontainers con PostgreSQL 16 (las restricciones `EXCLUDE` no existen en H2).

### Frontend

9. **NUNCA** `any` ni frameworks (React, Vue, etc.) → TypeScript strict, `unknown` + type guards, DOM vanilla.
10. **NUNCA** colores, tamaños o sombras sueltos → usar los tokens de [design-system/tokens.css](design-system/tokens.css) vía Tailwind; UI nueva solo con mockup aprobado.
11. **NUNCA** textos de UI en otro registro → toda la UI en español rioplatense con voseo; precios en ARS con formato local.
12. **NUNCA** UI que no cumpla accesibilidad AA → contraste AA, foco visible, objetivos táctiles ≥ 44 px, estados no solo por color, respetar `prefers-reduced-motion`.

### Dominio

13. **NUNCA** implementar algo fuera de la v1 (DD-16, DD-18: WhatsApp, historia clínica, cobros, obras sociales, roles, 2FA, etc.) → primero actualizar la KB y `CHANGES.md`.
14. **NUNCA** `LocalDateTime` para turnos → `timestamptz` / `Instant` en UTC; convertir a `America/Argentina/Buenos_Aires` solo en presentación.
15. **NUNCA** `float`/`double` para precios → `numeric` en Postgres y `BigDecimal` en Java, en ARS.
16. **NUNCA** implementar un change que toque una pregunta abierta Alta (PA-01..PA-06) o una inconsistencia IN-0x abierta sin resolverla → resolverla o documentar la decisión (`DD-NN`) antes de implementar. IN-01..IN-03 ya están resueltas (DD-19..DD-21).
17. **NUNCA** datos reales de pacientes en el repo ni en los ejemplos (seeds, fixtures, tests, mocks, capturas, mockups, logs, issues o PRs) → usar datos ficticios evidentes (p. ej. teléfonos `+54 9 11 0000-00xx`, emails `@example.com`); todo dato de paciente es sensible (Ley 25.326, [11_marco_legal_argentino.md](knowledge-base/11_marco_legal_argentino.md)).

---

## Flujo de Trabajo

```
1. Leer la KB relevante (knowledge-base/)        → entender el dominio
2. Identificar el change en CHANGES.md           → respetar dependencias
3. Acordar alcance y criterios de aceptación
4. Implementar y verificar respetando las reglas del proyecto
5. Registrar la evidencia y actualizar el estado en CHANGES.md
```

Aplicar TODAS las reglas duras en cada paso. Ante conflicto entre la KB y este archivo, las reglas duras prevalecen.
<!-- /active-stack:agent-instruction -->
