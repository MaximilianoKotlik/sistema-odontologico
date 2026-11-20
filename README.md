# Agenda Odontológica SaaS (repo `sistema-odontologico`)

SaaS web para odontólogos independientes de Argentina. La v1 resuelve solo la agenda: agenda diaria y semanal, bloqueos, sobreturnos y reserva pública por link sin cuenta.

- Contexto del dominio: [knowledge-base/](knowledge-base/README.md)
- Plan de trabajo y responsables: [CHANGES.md](CHANGES.md)
- Instrucciones para agentes: [CLAUDE.md](CLAUDE.md) / [AGENTS.md](AGENTS.md)

## Requisitos

| Herramienta | Versión | Para qué |
|-------------|---------|----------|
| Docker Desktop (con Compose v2) | reciente, **corriendo** | Entorno local y tests de integración (Testcontainers) |
| JDK | 21 o superior | Backend. Gradle descarga el toolchain 21 si tenés otra versión |
| Node.js | 20.19 o superior | Frontend |

## Arranque local

```bash
cp .env.example .env        # y cambiá las contraseñas
docker compose up --build
```

| Servicio | URL |
|----------|-----|
| Frontend (Vite) | http://localhost:5173 |
| Backend | http://localhost:8080 (salud: `/actuator/health`) |
| Swagger UI (solo perfil `local`) | http://localhost:8080/swagger-ui.html |
| Mailpit (emails capturados) | http://localhost:8025 |

Los roles de la base (`app_owner` para migraciones, `app_rw` para la aplicación) los crea `docker/postgres/init/01-roles.sh` **solo cuando se crea el volumen**. Si cambiás ese script o las credenciales, recreá la base (borra los datos locales):

```bash
docker compose down -v
```

## Tests

```bash
cd backend && ./gradlew test        # unitarios, ArchUnit e integración con PostgreSQL 16 (necesita Docker)
cd frontend && npm ci && npm test   # Vitest
cd frontend && npm run typecheck && npm run lint
```

## Tipos de la API en el frontend

`frontend/src/shared/api-types.ts` se genera desde el contrato OpenAPI del backend y **no se edita a mano**. Después de cambiar un endpoint:

```bash
cd frontend
npm run api:types                                         # con el backend corriendo en :8080
npm run api:types -- ../backend/build/openapi/openapi.json  # o desde el archivo que exporta ./gradlew test
```

El CI falla si el archivo commiteado no coincide con el contrato.

## Flujo de trabajo

Una rama `feat/C-NN-nombre` por change, un PR por change, conventional commits en español y Strict TDD. El detalle está en [CHANGES.md](CHANGES.md) y en las reglas duras de [CLAUDE.md](CLAUDE.md).
