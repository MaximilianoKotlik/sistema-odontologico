# Proposal

## Why

El repositorio todavía no tiene código: solo la base de conocimiento, el sistema de diseño y el roadmap. Los otros cuatro integrantes del equipo necesitan una estructura común sobre la cual trabajar en paralelo desde el paso 2 del plan. Esa estructura es un backend y un frontend que compilen, un entorno local que levante con un comando y un CI que bloquee los merges rotos. C-01 es el primer eslabón del camino crítico: cada día que se demora corre el proyecto entero.

## What Changes

- Esqueleto de `backend/`: Gradle con Kotlin DSL y wrapper versionado, Java 21 por toolchain, Spring Boot 3 (Web, Validation, Data JPA, Actuator), springdoc-openapi, Flyway con PostgreSQL y dependencias de test (JUnit 5, AssertJ, Mockito, Testcontainers, ArchUnit). Paquete raíz con los módulos vacíos descriptos en `08_arquitectura_propuesta.md`.
- Esqueleto de `frontend/`: Vite + TypeScript strict + Tailwind 3.4 + Vitest (jsdom) y ESLint con `no-explicit-any`. Un `main.ts` mínimo y el script `npm run api:types` (openapi-typescript) que genera `src/shared/api-types.ts`.
- Perfiles `application.yml`, `application-local.yml` y `application-test.yml`. El health check en `/actuator/health` y el contrato en `/v3/api-docs` quedan disponibles en todos los perfiles; Swagger UI solo en el perfil `local`.
- `docker-compose.yml` con `postgres:16-alpine`, `backend`, `frontend` y `mailpit`. Un script de inicio de Postgres crea los roles `app_owner` (Flyway) y `app_rw` (aplicación). `.env.example` incluye las variables de `08 §Variables de entorno`. Dockerfiles multi-stage para backend y frontend.
- CI en GitHub Actions para cada PR y cada push a `main`: tests del backend, typecheck, lint, tests y build del frontend, y verificación de que `api-types.ts` coincide con el contrato que expone el backend.
- `ArchitectureTest` base con las reglas de capas de `08` y un test de humo con Testcontainers que levanta la aplicación contra PostgreSQL 16 real y consulta el health check.
- Sin cambios incompatibles: no existe código previo.

**Fuera de alcance** (lo cubren changes posteriores):
- Spring Security y la configuración de autenticación (C-04).
- Spring Mail y SMTP de la aplicación (C-05). Mailpit queda levantado desde ahora.
- Migraciones de dominio, incluidas las extensiones `btree_gist` y `citext` (C-02).
- Router, cliente HTTP, PWA y tokens del sistema de diseño en Tailwind (C-06).
- Seed de datos de demo (C-08 / C-23).

## Capabilities

### New Capabilities

- `plataforma/entorno-local`: el entorno de desarrollo reproducible. Abarca el arranque con un solo comando, los servicios de Compose, los roles de base de datos separados para migraciones y aplicación, la configuración por variables de entorno y el health check del backend.
- `plataforma/contrato-api`: la publicación del contrato OpenAPI del backend, la visibilidad de Swagger UI por perfil y la generación de los tipos del cliente frontend a partir de ese contrato.
- `plataforma/integracion-continua`: las verificaciones automáticas que todo cambio debe pasar antes de entrar a `main`. Incluye tests de backend y frontend, build, reglas de arquitectura y sincronía del contrato tipado.

### Modified Capabilities

(ninguna: es el primer change del proyecto)

## Impact

- **Código nuevo**: `backend/`, `frontend/`, `docker/postgres/`, `docker-compose.yml`, `.env.example`, `.github/workflows/ci.yml`, `.gitignore` y `.gitattributes`.
- **Dependencias nuevas**: Spring Boot 3.x, springdoc-openapi 2.x, Flyway, el driver de PostgreSQL, Testcontainers, ArchUnit, Vite, TypeScript, Tailwind 3.4, Vitest, ESLint y openapi-typescript.
- **Equipo**: al mergear, Fran (C-02) y Nacho (C-06) pueden empezar a implementar. Las convenciones que fija este change (paquete raíz, perfiles, roles de base de datos, comandos de CI) pasan a ser la base de todos los PRs siguientes.
- **Requisitos locales**: Docker con Compose v2, JDK 21 o superior (Gradle descarga el toolchain 21 si hace falta) y Node 20 o superior.
