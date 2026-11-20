# Descripción General

> Decisiones de stack confirmadas por el usuario el 2026-11-16. Justificación y alternativas en
> [09_decisiones_y_supuestos.md](09_decisiones_y_supuestos.md). Detalle de implementación en
> [08_arquitectura_propuesta.md](08_arquitectura_propuesta.md).

## Contexto de producto y mercado

SaaS para odontólogos independientes de Argentina; v1 = solo la agenda de **un** profesional con reserva pública por link
sin cuenta. Competidores de referencia del Discovery: DentalSoft (3,65/5, sobreturnos y precios públicos por profesional),
AgendaPro AR (3,40, agenda por sillón y recordatorios), MedicAI (3,05, auditoría y exportación; no dental), Dentalink (2,95)
y ClinIA (2,85, todo afirmación comercial). El espacio que v1 apunta a ocupar es la combinación de agenda con
sobreturnos y solapamiento, autogestión del paciente, auditoría, exportación completa y precios públicos
([01 §Posicionamiento](01_vision_y_objetivos.md#posicionamiento-frente-a-la-competencia-y-espacio-libre-del-mercado)).
Un solo sillón implícito en v1 ([RN-AG-13](05_reglas_de_negocio.md)); WhatsApp, Mercado Pago, ARCA y Google Calendar
diferidos, como en el Discovery §8.

## Stack tecnológico

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

## Arquitectura general

Monolito modular en el backend (un único deployable Spring Boot, paquetes por dominio) y una SPA
liviana en TypeScript vanilla que **solo** habla con la API REST. No hay BaaS, ni colas, ni caché
externa, ni servicios de terceros en v1.

```
┌────────────────────────── Docker Compose (local) ──────────────────────────┐
│                                                                            │
│  ┌──────────────┐   HTTPS/JSON    ┌───────────────────────────────┐        │
│  │  frontend    │ ──────────────▶ │  backend (Spring Boot, :8080)  │        │
│  │  Vite / PWA  │  /api/v1/**     │  ┌─────────┐ ┌──────────────┐  │        │
│  │  (:5173)     │ ◀────────────── │  │  auth   │ │ consultorio  │  │        │
│  └──────────────┘                 │  ├─────────┤ ├──────────────┤  │  JDBC  │
│     ▲        ▲                    │  │ agenda  │ │   reserva    │  │ ──────▶│ postgres:5432
│     │        │                    │  ├─────────┤ ├──────────────┤  │        │
│  Odontólogo  Paciente             │  │auditoria│ │ exportacion  │  │  SMTP  │
│  (JWT)       (link público)       │  └─────────┘ └──────────────┘  │ ──────▶│ mailpit:1025
│                                   └───────────────────────────────┘        │
└────────────────────────────────────────────────────────────────────────────┘
```

Justificación de alto nivel:

- **Monolito modular**: equipo chico, un solo deploy, transacciones locales (la auditoría se escribe
  en la misma transacción que el cambio del turno). Los límites entre paquetes permiten extraer
  módulos más adelante si hiciera falta.
- **SPA vanilla**: superficie chica (≈10 pantallas), sin dependencia de framework, bundle liviano
  para mobile. Se compensa con una capa `shared/` propia (router, cliente HTTP, store mínimo).
- **PostgreSQL como guardián de invariantes**: el no-solapamiento se garantiza con una restricción de
  exclusión además de la validación en servicio ([RN-AG-01](05_reglas_de_negocio.md)).
- **Local-first**: todo levanta con `docker compose up`. El hosting cloud queda abierto
  ([PA-02](10_preguntas_abiertas.md)).

## Integraciones externas

| Servicio | Propósito | Tipo | Estado v1 |
|----------|-----------|------|-----------|
| — | — | — | **Ninguna integración externa en v1** (decisión explícita, [DD-07](09_decisiones_y_supuestos.md)) |
| Servidor SMTP | Email de recuperación de contraseña del odontólogo | SMTP | En local, **Mailpit** dentro de Compose (no sale a Internet). Proveedor real: abierto ([PA-02](10_preguntas_abiertas.md)); SMTP como infraestructura según [DD-19](09_decisiones_y_supuestos.md) (resuelve IN-01) |
| WhatsApp Business API | Recordatorios | REST | Diferido |
| Mercado Pago | Señas y cobro de suscripción | REST + webhook | Diferido |
| ARCA (ex AFIP) | Facturación electrónica | SOAP WS | Diferido |
| Google Calendar | Sincronización | REST | Diferido |

## API REST

Base: `/api/v1`. JSON UTF-8, fechas en ISO-8601 con offset (`2026-11-20T10:30:00-03:00`). Errores en
formato RFC 7807 (`application/problem+json`) con un campo `codigo` estable (p. ej. `TURNO_SOLAPADO`).
Contrato publicado en `/v3/api-docs` y Swagger UI en `/swagger-ui.html` (este último solo en perfil `local`).

### Privada (requiere `Authorization: Bearer <access_token>`)

| Recurso | Método y ruta | Descripción |
|---------|---------------|-------------|
| Consultorio | `GET /consultorio` · `PUT /consultorio` | Ver y editar configuración (nombre, slug, zona horaria, anticipación mínima, horizonte) |
| Prestaciones | `GET /prestaciones` · `POST /prestaciones` · `PUT /prestaciones/{id}` · `POST /prestaciones/{id}/desactivar` | Tipos de prestación con duración |
| Disponibilidad | `GET /disponibilidad` · `PUT /disponibilidad` | Franjas semanales (reemplazo completo) |
| Bloqueos | `GET /bloqueos?desde&hasta` · `POST /bloqueos` · `DELETE /bloqueos/{id}` | Bloqueos puntuales |
| Agenda | `GET /agenda?desde&hasta` | Vista consolidada: turnos + bloqueos + franjas para día o semana |
| Turnos | `POST /turnos` · `GET /turnos/{id}` · `PUT /turnos/{id}/horario` · `POST /turnos/{id}/estado` | Alta (normal o sobreturno), reprogramación y cambio de estado |
| Pacientes | `GET /pacientes?q=` · `PUT /pacientes/{id}` | Búsqueda por nombre/teléfono y corrección de datos |
| Auditoría | `GET /auditoria?turnoId&desde&hasta&page` | Consulta del registro append-only |
| Exportación | `GET /exportacion` | Descarga ZIP con JSON + CSV de todos los datos del consultorio |

### Autenticación (pública)

| Método y ruta | Descripción |
|---------------|-------------|
| `POST /auth/registro` | Crea consultorio + odontólogo, inicia prueba gratuita |
| `POST /auth/login` | Devuelve access token (15 min) y refresh token (cookie `HttpOnly`) |
| `POST /auth/refresh` · `POST /auth/logout` | Rota / revoca el refresh token |
| `POST /auth/recuperacion` · `POST /auth/recuperacion/confirmar` | Solicita y confirma el restablecimiento de contraseña |

### Pública del paciente (sin autenticación, con rate limit)

| Método y ruta | Descripción |
|---------------|-------------|
| `GET /public/consultorios/{slug}` | Datos públicos del consultorio y prestaciones activas |
| `GET /public/consultorios/{slug}/huecos?prestacionId&fecha` | Huecos libres calculados |
| `POST /public/consultorios/{slug}/turnos` | Reserva; devuelve el link de gestión (token, una sola vez) |
| `GET /public/turnos/{token}` | Ver el turno propio |
| `POST /public/turnos/{token}/reprogramacion` · `POST /public/turnos/{token}/cancelacion` | Reprogramar / cancelar con anticipación mínima |
| `GET /public/planes` | Planes y precios en ARS |
