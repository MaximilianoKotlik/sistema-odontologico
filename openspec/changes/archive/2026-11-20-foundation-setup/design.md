# Design

## Context

El repositorio no tiene código: solo `knowledge-base/`, `design-system/`, `docs/` y `openspec/`. La forma objetivo está en `knowledge-base/08_arquitectura_propuesta.md` (estructura de directorios, Compose, variables de entorno y estrategia de tests). Los requisitos de este change están en `specs/plataforma/*`.

Restricciones del entorno observadas:
- Las máquinas del equipo pueden tener un JDK más nuevo que 21. La del líder tiene JDK 25.
- El repositorio se edita desde Windows (CRLF por defecto) y los scripts corren en contenedores Linux.
- `main` está protegida: un PR solo se mergea con la aprobación del code owner.

## Goals / Non-Goals

**Goals:**
- Cerrar ya las convenciones que todo el equipo va a copiar: paquete raíz, perfiles, roles de base de datos, comandos de test y pipeline de CI.
- Hacer que el primer test de cada change posterior sea fácil de escribir: una clase base de integración con Testcontainers lista para usar.

**Non-Goals:**
- No se agrega ningún endpoint de negocio ni migración de dominio.
- No se configura seguridad HTTP. Sin Spring Security en el classpath, el backend no exige autenticación hasta C-04.
- No se hace deploy ni se elige hosting (PA-02).

## Decisions

### D1 — Versiones: Spring Boot 3.5, Gradle 9, Java 21 por toolchain
Spring Boot 3.5.x (último parche, hoy 3.5.16), por decisión del líder: respeta DD-01 y la tabla del stack.
- springdoc-openapi 2.x, la línea compatible con Boot 3.
- Flyway, el driver de PostgreSQL y Testcontainers 1.x con las versiones que fija el BOM de Boot.
- ArchUnit 1.x.

Se usa el wrapper de Gradle 9.x en vez de 8.8 porque Gradle 8 no corre sobre JDK 25. El código se compila con un toolchain Java 21 declarado; el plugin `foojay-resolver` lo descarga si falta.
- *Alternativa descartada*: Spring Boot 4.x. Tiene soporte más largo, pero cambia la KB y la API de varias dependencias. Queda como migración futura (ver Riesgos).

### D2 — Paquete raíz `ar.sistemaodontologico`
El nombre comercial está pendiente, así que el paquete toma el nombre del repo, que es neutral y no cambia con la marca. Cada módulo de `08` (`compartido`, `auth`, `consultorio`, `agenda`, `reserva`, `auditoria`, `exportacion`, `plan`) se crea con un `package-info.java` que documenta su responsabilidad. Así las reglas de ArchUnit tienen dónde aplicarse y nadie inventa una estructura distinta.
- *Alternativa descartada*: `ar.com.<marca>`. Habría que renombrarlo cuando se decida la marca.

### D3 — Dependencias mínimas en C-01
Entran Web, Validation, Data JPA, Actuator, Flyway (con `flyway-database-postgresql`), el driver de PostgreSQL y springdoc. Spring Security entra en C-04 y Spring Mail en C-05. Si Security entrara ahora, su configuración por defecto protegería todo con un usuario generado y obligaría a escribir en C-01 una configuración de seguridad que es dominio CRITICO de C-04.

### D4 — Roles de base de datos con un script de inicio compartido
`docker/postgres/init/01-roles.sh` se ejecuta una sola vez al crear el volumen. Lee las contraseñas del entorno y hace lo siguiente:
- Crea `app_owner` y `app_rw`.
- Le da a `app_owner` la propiedad de la base y del esquema `public`. Así puede crear las extensiones confiables (`btree_gist`, `citext`) que pide C-02.
- Le da a `app_rw` `USAGE` sobre el esquema.
- Configura `ALTER DEFAULT PRIVILEGES FOR ROLE app_owner` para que `app_rw` reciba `SELECT, INSERT, UPDATE, DELETE` en las tablas futuras y `USAGE, SELECT` en las secuencias.

Las restricciones particulares, como sacarle `UPDATE` y `DELETE` sobre `evento_auditoria`, las agrega la migración de cada change (C-03).

Spring usa `spring.datasource.*` con `app_rw` y `spring.flyway.user/password` con `app_owner`. Los tests montan el mismo script en el contenedor de Testcontainers, así que local y CI se comportan igual.
- *Alternativa descartada*: un solo usuario. Rompería la garantía append-only de C-03.

### D5 — Clase base de integración
`IntegracionPostgresTest` (en `src/test`, paquete `compartido`) declara un `PostgreSQLContainer` estático `postgres:16-alpine` reutilizado por toda la suite. Monta el script de roles y conecta datasource y Flyway con `@DynamicPropertySource`. Los changes siguientes extienden esta clase.

### D6 — Configuración por perfil
- `application.yml` (base): `ddl-auto=validate`, `open-in-view=false`, zona horaria JDBC UTC, health sin detalles, solo `health` expuesto en Actuator, `springdoc.swagger-ui.enabled=false`.
- `application-local.yml`: habilita Swagger UI y logs más verbosos.
- `application-test.yml`: valores para los tests (sin secretos reales).

Las variables obligatorias (`DB_URL`, usuarios y contraseñas) se leen sin valor por defecto. Spring deja sin resolver un placeholder faltante y falla más tarde con un error ambiguo (`'url' must start with "jdbc"`). Por eso `compartido.infrastructure.configuracion.VariablesObligatorias` (un `BeanFactoryPostProcessor`) corta el arranque nombrando cada variable que falta. Se eligió ese punto, y no un `EnvironmentPostProcessor`, porque ahí ya están cargadas también las propiedades dinámicas de los tests.

### D7 — CI en un solo workflow con tres jobs
`.github/workflows/ci.yml` se dispara en `pull_request` a `main` y en `push` a `main`.
1. **backend** (ubuntu, Temurin 21, caché de Gradle): corre `./gradlew build`, que incluye los tests, ArchUnit y el test de humo con Testcontainers usando el Docker del runner. Un test escribe el contrato servido por `/v3/api-docs` en `backend/build/openapi/openapi.json`, que se sube como artefacto.
2. **frontend** (Node LTS, caché de npm, depende de *backend*): corre `npm ci`, `npm run typecheck`, `npm run lint`, `npm test` y `npm run build`. Después descarga el artefacto, ejecuta `npm run api:types -- <archivo>` y `git diff --exit-code src/shared/api-types.ts`. Si hay diferencias, falla con el mensaje "ejecutá npm run api:types".
3. **docker**: corre `docker compose build` para garantizar que los Dockerfiles siguen compilando.

Exportar el contrato desde un test evita levantar el backend como servicio en CI y usa exactamente la misma aplicación que se testea.
- *Alternativa descartada*: el plugin `springdoc-openapi-gradle-plugin`. Levanta la app en un proceso aparte y necesita su propia base de datos en CI.

Localmente, `npm run api:types` lee por defecto `http://localhost:8080/v3/api-docs`, o un archivo si se le pasa la ruta.

### D8 — Frontend
- TypeScript 5.x en modo strict con `noUncheckedIndexedAccess`. Se descarta TypeScript 7 porque typescript-eslint todavía no lo soporta.
- Vite y Vitest en el último par de versiones compatibles, con entorno jsdom.
- Tailwind 3.4 por PostCSS, porque `tailwind.config.ts` es el lugar donde C-06 mapea los tokens.
- ESLint con flat config y typescript-eslint, regla `@typescript-eslint/no-explicit-any` en `error`.
- `main.ts` solo monta un contenedor raíz. El router y el resto de la infraestructura llegan en C-06.

### D9 — Docker
- **Backend**: etapa `build` con `gradle` + JDK 21 y etapa final `eclipse-temurin:21-jre` con usuario no root.
- **Frontend**: etapa `dev` (`vite --host`, la que usa Compose), etapa `build` y etapa `prod` con nginx sirviendo `dist/`.
- El servicio `frontend` de Compose configura el proxy de Vite `/api` → `http://backend:8080`.

### D10 — Finales de línea
`.gitattributes` fuerza `eol=lf` en `*.sh`, `gradlew`, `Dockerfile` y `*.yml`, y `eol=crlf` en `*.bat`. Sin esto, el script de roles editado en Windows falla dentro del contenedor Linux con `bad interpreter`.

## Risks / Trade-offs

- **[Spring Boot 3.5 cerca del fin de soporte gratuito]** → Cuando termine la v1, abrir un change de migración a Boot 4. Hasta entonces, mantener el último parche de 3.5.x.
- **[Testcontainers en Windows requiere Docker Desktop corriendo]** → El README de C-01 lo indica. En CI no aplica, porque los runners de Ubuntu traen Docker.
- **[El job frontend depende del backend y alarga el CI]** → Es aceptable: el contrato es la fuente de verdad. Si se vuelve lento, se puede cachear el artefacto por hash del código del backend.
- **[El script de roles solo corre con un volumen nuevo]** → Si alguien ya tenía un volumen `pgdata` de pruebas, `docker compose down -v` lo recrea. Queda documentado en el README.
- **[Gradle 9 con Boot 3.5]** → Se verifica en la tarea de build. Si el plugin de Boot no fuera compatible, se fija la última versión 8.14 del wrapper y el toolchain 21 se usa también para correr Gradle.

## Migration Plan

No hay nada previo que migrar. Rollback: revertir el merge del PR de C-01. No toca datos persistidos fuera de volúmenes locales.

## Open Questions

- Cobertura mínima orientativa del 80 % en `domain` y `application`: se mide con JaCoCo cuando haya código de dominio (C-02 en adelante). Por ahora no se impone un umbral en CI.
