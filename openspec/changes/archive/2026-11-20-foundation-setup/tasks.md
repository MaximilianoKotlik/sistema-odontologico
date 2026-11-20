# Tasks

> Strict TDD: en cada tarea con comportamiento, primero se escribe el test en rojo, después el código mínimo que lo pone en verde y por último el refactor.

## 1. Repositorio base

- [x] 1.1 Crear `.gitignore` (incluye `.env`, `build/`, `node_modules/`, `dist/`, `.gradle/`) y `.gitattributes` (D10). Verificar que `git check-ignore .env` lo reporta como ignorado y que `git ls-files --eol` muestra `lf` para `*.sh`.

## 2. Backend: build y estructura

- [x] 2.1 Generar el wrapper de Gradle 9.x y crear `settings.gradle.kts` y `build.gradle.kts` con Spring Boot 3.5.x, toolchain Java 21, foojay y las dependencias de D1/D3. Verificar que `./gradlew --version` y `./gradlew dependencies` resuelven sin errores. Si el plugin de Boot no es compatible con Gradle 9, usar Gradle 8.14 (Riesgos).
- [x] 2.2 Crear `ar.sistemaodontologico.Application` y un `package-info.java` por módulo de D2. Verificar que `./gradlew compileJava` compila.
- [x] 2.3 TDD de las reglas de arquitectura. Primero, `ArchitectureTest` (ArchUnit, `allowEmptyShould`) con dominio sin `org.springframework..` ni `jakarta.persistence..` y sin acceso a la `infrastructure` de otro módulo. Ponerlo en rojo con una clase de prueba infractora en un paquete de fixtures, comprobar que falla nombrándola, y después dejar las reglas aplicadas solo al código de producción. Verificar con `./gradlew test --tests '*ArchitectureTest'`.

## 3. Backend: base de datos, configuración y salud

- [x] 3.1 Escribir `docker/postgres/init/01-roles.sh` (D4).
- [x] 3.2 Crear `IntegracionPostgresTest` (D5) que monta el script en Testcontainers.
- [x] 3.3 TDD de los roles. Tests de integración en rojo para tres casos: (a) `app_rw` no puede ejecutar `CREATE TABLE`; (b) una tabla creada por `app_owner` es legible e insertable por `app_rw`; (c) Flyway corre como `app_owner` (lo confirma `flyway_schema_history.installed_by`). Llevarlos a verde.
- [x] 3.4 Crear `application.yml`, `application-local.yml` y `application-test.yml` (D6), con datasource `app_rw` y Flyway `app_owner`.
- [x] 3.5 TDD de salud y configuración. Test de humo en rojo: `GET /actuator/health` devuelve 200 `UP` sin detalles de componentes. Test de configuración: el contexto no arranca sin `DB_URL`. Llevarlos a verde con `./gradlew test`.

## 4. Backend: contrato OpenAPI

- [x] 4.1 TDD del contrato. Tests en rojo: `GET /v3/api-docs` devuelve un documento OpenAPI 3; `/swagger-ui.html` devuelve 404 sin perfil `local` y responde con el perfil `local`. Llevarlos a verde configurando springdoc.
- [x] 4.2 Agregar al test del contrato la escritura del JSON en `backend/build/openapi/openapi.json` (D7). Verificar que el archivo existe después de `./gradlew test`.
- [x] 4.3 Escribir `backend/Dockerfile` multi-stage con usuario no root (D9). Verificar que `docker build backend` termina bien.

## 5. Frontend

- [x] 5.1 Inicializar `frontend/`: `package.json` con los scripts `dev`, `build`, `typecheck`, `lint`, `test` y `api:types`; `tsconfig.json` strict; `vite.config.ts` con jsdom en Vitest y proxy `/api`; Tailwind 3.4 con PostCSS; ESLint flat con `no-explicit-any: error`. Verificar que `npm ci` instala sin errores.
- [x] 5.2 TDD del arranque. Test Vitest en rojo: `main.ts` monta el contenedor raíz en `#app`. Implementar `index.html`, `src/main.ts` y `src/styles.css` con las directivas de Tailwind. Verificar con `npm test`, `npm run typecheck` y `npm run build`.
- [x] 5.3 Verificar la regla de tipos: un archivo temporal con `any` explícito hace fallar `npm run lint`. Borrarlo y confirmar que el lint queda limpio.
- [x] 5.4 Escribir el script `api:types` (openapi-typescript; lee la URL local por defecto o una ruta pasada como argumento). Generar `src/shared/api-types.ts` desde `backend/build/openapi/openapi.json` y commitearlo. Verificar que regenerarlo no produce diff.
- [x] 5.5 Escribir `frontend/Dockerfile` con las etapas `dev`, `build` y `prod` (D9). Verificar que `docker build --target dev frontend` y `docker build --target prod frontend` terminan bien.

## 6. Entorno local

- [x] 6.1 Crear `docker-compose.yml` con `postgres` (healthcheck y volumen `pgdata`, monta el script de roles), `backend` (espera a `postgres` sano, healthcheck HTTP), `frontend` (target `dev`) y `mailpit`, más `.env.example` con todas las variables de `08 §Variables de entorno`. Verificar que `docker compose config` valida.
- [x] 6.2 Escribir `README.md` en la raíz con los requisitos (Docker Desktop, JDK 21+, Node 20+), el arranque (`cp .env.example .env` + `docker compose up --build`), los comandos de test y `api:types`, y el aviso de `docker compose down -v` para recrear los roles.

## 7. Integración continua

- [x] 7.1 Escribir `.github/workflows/ci.yml` con los jobs `backend`, `frontend` (descarga el contrato y compara `api-types.ts`) y `docker` (D7). Verificar la sintaxis con `actionlint` si está disponible, o con la primera ejecución en el PR.

## 8. Verificación integral

- [x] 8.1 Desde un clon limpio, copiar `.env.example` a `.env` y ejecutar `docker compose up --build`. Verificar que `http://localhost:8080/actuator/health` devuelve `UP`, que `http://localhost:5173` carga el frontend, que `http://localhost:5173/api/...` llega al backend a través del proxy, que `http://localhost:8025` muestra Mailpit y que `http://localhost:8080/swagger-ui.html` responde con el perfil `local`. Bajar el entorno al terminar.
- [x] 8.2 Abrir el PR de C-01 y verificar que los tres jobs de CI terminan en verde. Pegar la evidencia de 8.1 y del CI en la descripción del PR.

## Workflow follow-up

- Revisión y merge del PR por el líder (code owner); marcar `[x]` en C-01 de `CHANGES.md` al mergear.
- Archivar el change con `/opsx:archive` después del merge.
- Avisar a Fran (C-02) y a Nacho (C-06) que ya pueden implementar.
