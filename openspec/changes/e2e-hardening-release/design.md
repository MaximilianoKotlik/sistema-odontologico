# Design

## Context

- **Aprobación**: Maxi aprobó la propuesta el 2026-11-21 en #65, con las decisiones D1 a D10, y decidió que no se implementa nada hasta que C-13, C-14, C-16, C-17, C-18 y C-22 estén `[x]`.
- Ver `proposal.md` §Why. Governance **MEDIO**: se implementa con puntos de control y las decisiones se le muestran a Maxi. C-23 no tiene pantallas propias: `Decision: no (no UI)` en §UI Mockups.
- **Lo que ya existe en `main`**:
  - **CI** (`.github/workflows/ci.yml`): jobs `backend` (`./gradlew build` con Testcontainers), `frontend` (typecheck, lint, Vitest, build y chequeo de `api-types.ts`) y `docker` (`docker compose build` con `.env.example`). No hay Playwright.
  - **Compose**: `postgres`, `backend` (perfil `local`, Flyway al arrancar), `frontend` (`vite --host` con proxy `/api`) y `mailpit`.
  - **Seed local** (`db/seed-local/R__demo.sql`, migración repetible que solo carga el perfil `local`, verificada por `SeedLocalTest`): consultorio `demo`, 4 prestaciones, disponibilidad, 5 pacientes, 10 turnos de la semana en curso y un bloqueo.
  - **Seguridad** (`ConfiguracionDeSeguridad`): CSP `default-src 'self'`, `Referrer-Policy: no-referrer`, HSTS fuera de `local`, `nosniff`. No se desactiva el `Cache-Control` por defecto de Spring Security (`no-cache, no-store, max-age=0, must-revalidate`). Las rutas por token ya tienen tests de `no-store` (`GestionPorTokenTest`, `LimitesPublicosTest`).
  - **Rate limit**: Bucket4j en memoria por IP con `getRemoteAddr()`. `server.forward-headers-strategy` no está configurado (queda en `none`). `backend/README.md` §IP de origen detrás de un proxy describe el problema.
  - **PII en logs**: `EnmascaradorDePii` (RN-GL-01) y el enmascarado de tokens en las rutas por token.
  - **Plan vencido**: `MatrizDeEscriturasTest` (C-20) recorre `RequestMappingHandlerMapping` y espera 403 en cada escritura no permitida. Es el modelo para la matriz de aislamiento.
  - **Textos legales**: `docs/legal/` con términos y política de privacidad, marcados `BORRADOR · PENDIENTE DE REVISIÓN LEGAL`, versión `2026-11-borrador`.
- **Restricciones**: Strict TDD (regla dura 1), Testcontainers con PostgreSQL 16 (regla 8), datos ficticios (regla 17), sin `any` (regla 9). Una sola instancia del backend en v1.

## Goals / Non-Goals

**Goals:**
- Que el humo corra igual en una máquina del equipo y en CI, con un solo comando.
- Que cada punto de la revisión de seguridad quede atado a evidencia que se vuelve a correr (un test o una configuración), no a una afirmación.
- Que las verificaciones automáticas (aislamiento, plan vencido) cierren por defecto ante endpoints nuevos.

**Non-Goals:**
- Probar en varios navegadores: el humo corre solo en Chromium con viewport mobile.
- Servir el frontend de producción: hoy no hay imagen de producción del frontend (DD-25 lo deja para el VPS). El humo corre contra el dev server de Compose, y el checklist de lanzamiento anota servir el build.
- Elegir el reverse proxy del VPS: se documenta qué tiene que hacer, no cuál.

## Decisions

### D1 — El humo vive en `e2e/`, con su propio `package.json`
- Carpeta `e2e/` en la raíz, con `@playwright/test`, `playwright.config.ts` y los recorridos en `e2e/tests/`. TypeScript strict, como el frontend.
- **Por qué no dentro de `frontend/`**: el humo prueba el stack entero (backend, base y frontend) y no debe sumar dependencias ni tiempo al `npm ci` y a los tests del frontend.
- Un solo proyecto de Playwright: Chromium, viewport 390×844, `isMobile` y `hasTouch`, idioma `es-AR` y zona `America/Argentina/Buenos_Aires`.
- *Alternativa*: Playwright dentro de `frontend/` con `webServer` de Vite. Descartada: no levanta el backend ni la base.

### D2 — Stack del humo: Docker Compose con el seed de demo
- El humo corre contra `docker compose up -d --build --wait`, con `.env.example` y el perfil `local`, para usar el seed de demo. `baseURL` es `http://localhost:5173`.
- El link de gestión de la reserva se lee de la pantalla de confirmación. El humo no necesita leer Mailpit.
- **Comando local**: `npm run humo` en `e2e/`, documentado en el README, con el stack ya levantado.
- *Alternativa*: levantar backend y frontend sin Docker. Descartada: el CI ya construye las imágenes de Compose y así el humo prueba lo mismo que el equipo usa.

### D3 — Job `E2E (humo)` en CI
- Job nuevo en `ci.yml`, en paralelo con los otros, que corre en cada `pull_request` y `push` a `main`: levanta Compose, espera `/actuator/health`, instala Chromium (`npx playwright install --with-deps chromium`), corre el humo y, si falla, sube `playwright-report/` y `test-results/` (capturas, video y traza) como artefacto.
- Al final baja el stack con `docker compose down -v`.
- **Merge bloqueado**: para que el job bloquee el merge hay que sumarlo a los checks requeridos de la protección de `main`. Eso es una configuración de GitHub que hace Maxi a mano (tarea 6.4).
- *Alternativa*: correrlo solo al etiquetar un release. Descartada: un recorrido roto se descubriría tarde, con varios PRs mezclados.

### D4 — Selectores accesibles y recorridos que se limpian solos
- El humo busca por rol, nombre accesible y etiqueta (`getByRole`, `getByLabel`), los mismos que exige la regla dura 12. `data-testid` solo si no hay nombre accesible estable. Eso se coordina con Nacho (C-13 y C-14) y Octi (C-16).
- **Corridas repetibles**: cada recorrido crea un paciente con un sufijo único por corrida, toma el primer hueco libre que ofrezca la app y, al terminar, cancela el turno que creó, así libera el hueco. Ningún recorrido depende de que otro haya corrido antes.
- **Hasta que estén las pantallas**: el primer recorrido es login → agenda visible (C-07 y el placeholder de agenda de C-06). Cuando entra C-13, el recorrido valida la agenda real, y cuando entra C-14 suma la creación del turno.

### D5 — IP real detrás del proxy
- `application.yml`: `server.forward-headers-strategy: ${FORWARD_HEADERS_STRATEGY:none}`. Con `none` (por defecto, en local y en tests) el comportamiento no cambia.
- En el VPS se usa `native`: Tomcat (`RemoteIpValve`) toma de `X-Forwarded-For` la IP más a la derecha que no sea un proxy de confianza. Así un `X-Forwarded-For` que mande el cliente queda a la izquierda y se ignora, aunque el proxy agregue en vez de reemplazar. Los proxies de confianza se fijan con `server.tomcat.remoteip.internal-proxies` si el proxy no está en una red privada.
- **Test**: `@SpringBootTest(webEnvironment = RANDOM_PORT)` con `FORWARD_HEADERS_STRATEGY=native`, porque la válvula es de Tomcat y MockMvc no pasa por ella. Prueba los tres escenarios de la spec contra el límite de logins.
- Se suma `FORWARD_HEADERS_STRATEGY` a `.env.example` y a la tabla de variables de la KB (08 §Variables de entorno).
- *Alternativa*: leer `X-Forwarded-For` a mano en el limitador. Descartada: duplica lo que Tomcat ya hace bien y abre la puerta a confiar en cualquier cabecera.

### D6 — Matriz de aislamiento, cerrada por defecto
- `MatrizDeAislamientoTest` recorre `RequestMappingHandlerMapping`, como `MatrizDeEscriturasTest`, y toma cada endpoint privado (bajo `/api/v1/` y fuera de `/api/v1/public/**` y `/api/v1/auth/**`).
- Un registro dentro del test dice, por cada endpoint, de qué recurso es cada variable de la ruta (`turnos/{id}` → turno) y qué cuerpo válido manda si es una escritura. Con un cuerpo válido, la respuesta llega a buscar el recurso y no se queda en un 400 de validación.
- **Cierra por defecto**: un endpoint privado que no está en el registro hace fallar el test con su método y su ruta. Uno sin variables de ruta (un listado) se verifica pidiendo como el consultorio A y comprobando que la respuesta no tiene ids del B.
- Cada caso verifica 404 `RECURSO_NO_ENCONTRADO`, sin cambios en las tablas del B ni eventos de auditoría nuevos.
- *Alternativa*: un test de aislamiento a mano por endpoint, como hasta ahora (regla dura 6). Se mantienen, pero no avisan si alguien se olvida de escribir uno.

### D7 — `Cache-Control` en la API privada
- Spring Security ya agrega `no-store` a las respuestas que pasan por su cadena. La tarea es probarlo para una lectura privada, un error de validación, un 404 y el 403 `PLAN_VENCIDO`, que escribe su respuesta en un filtro propio. Solo se toca la configuración si algún caso no lo tiene.
- La descarga de la exportación (C-19) define su propio `Cache-Control`. Se verifica que también incluya `no-store`.

### D8 — Seed de demo
- `R__demo.sql` se amplía en el mismo archivo para el consultorio `demo`, y un archivo nuevo `R__demo_vencido.sql` carga el consultorio `demo-vencido` (plan `VENCIDO`, cuenta `vencido@consultorio.test`). Los dos se cargan solo con el perfil `local`, como hoy.
- Se suman al consultorio `demo`:
  - turnos `CONFIRMADO` y `AUSENTE`;
  - dos turnos con origen `LINK`, uno con la novedad sin ver;
  - un paciente con `requiere_revision` y los datos informados (DD-38);
  - eventos de auditoría de esos turnos, uno de ellos reprogramado;
  - un token de gestión con un valor en claro fijo y documentado en `db/seed-local/README.md`, cuyo hash es el que se guarda. Solo vale en local.
- Las tablas ya existen en `main` (C-10, C-15 y C-03), así que el seed se puede escribir ya. Lo que falta para verlo son las pantallas de C-17 y C-18.
- `SeedLocalTest` se amplía con los conteos nuevos, y con que el perfil por defecto no carga ninguno de los dos consultorios.
- *Alternativa*: un endpoint o un comando que genere datos de demo. Descartada: la migración repetible ya funciona y no expone nada fuera de local.

### D9 — Evidencia de la revisión en `docs/release/`
- `docs/release/revision-seguridad.md`: una tabla por tema (cabeceras, rate limits, PII en logs, tokens en `Referer` y caché, secretos, aislamiento, plan vencido, `/seguridad`) con columnas **Qué se verifica**, **Evidencia** (test, archivo de configuración o comando), **Estado** (ok, arreglado acá, issue #N) y **Fecha**.
- `docs/release/checklist-lanzamiento.md`: los requisitos de DD-40, la revisión legal de DD-28, la validación de los valores por defecto con odontólogos, el despliegue (proxy, `FORWARD_HEADERS_STRATEGY`, TLS y HSTS, `JWT_SECRET`, SMTP, servir el build del frontend, backups y restauración de prueba) y cómo medir las métricas del piloto (01 §Métricas de éxito). Cada punto tiene un dueño y una casilla. El checklist bloquea el lanzamiento, pero no el cierre de C-23.
- **Defectos que encuentre la revisión**: si el arreglo es chico y no cambia un contrato, se hace acá con su test. Si no, se abre un issue para el change dueño y la fila queda con el número del issue.

### D10 — Revisión de `/seguridad` (RN-PL-06)
- Cuando C-22 esté en `main`, cada afirmación de la página va a una fila de `revision-seguridad.md` con su evidencia. Una afirmación sin evidencia se corrige en el texto de la página o se reemplaza por "pendiente" antes de publicar. No se declara ningún cumplimiento que no esté verificado.

## Risks / Trade-offs

- [El humo corre contra el dev server de Vite, no contra el build de producción] → El job `frontend` ya prueba que el build compila, y el checklist anota servir el build en el VPS y repetir el humo contra él antes de lanzar.
- [El job de E2E suma minutos a cada PR (construir las imágenes y levantar Compose)] → Corre en paralelo con los otros jobs y reusa la caché de capas de Docker. Si pasa de 10 minutos se revisa la decisión D3.
- [Los recorridos dependen de pantallas que todavía no están (C-13, C-14 y C-16)] → No se implementa nada hasta que estén todas las dependencias (decisión de Maxi, 2026-11-21), así que los recorridos se escriben sobre las pantallas reales. El paso intermedio de D4 (login → agenda visible) queda como primer recorrido, no como espera.
- [Flakiness por esperas o por el orden de los datos] → Esperas por estado de la UI (nunca `waitForTimeout`), un reintento en CI y recorridos independientes (D4).
- [La matriz de aislamiento necesita un cuerpo válido por escritura, y eso hay que mantenerlo] → Es el precio de que cierre por defecto: el test dice exactamente qué endpoint falta.
- [`native` confía en las IPs de red privada como proxies] → En el VPS el proxy corre en el mismo host o en la red de Compose. Si no, se fija `internal-proxies`. Queda en el checklist de despliegue.

## Migration Plan

- Sin migraciones Flyway de esquema. El seed es una migración repetible que solo corre con el perfil `local`.
- `FORWARD_HEADERS_STRATEGY` vale `none` por defecto, así que nada cambia hasta configurarlo en el VPS.
- Rollback: revertir el PR. El job de E2E se puede sacar de los checks requeridos sin tocar el código.

## UI Mockups

- Design system revision: 1
- Decision: no (no UI) (2026-11-21). C-23 no agrega ni cambia pantallas: prueba las de otros changes.

## Open Questions

- Qué reverse proxy se usa en el VPS (Caddy, nginx u otro). No cambia el diseño: los dos pueden reemplazar `X-Forwarded-For` y terminar TLS. Se decide con el VPS (DD-40).
