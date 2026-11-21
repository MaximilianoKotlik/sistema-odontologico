# Proposal

> Change **C-23** de [CHANGES.md](../../../CHANGES.md) · Governance **MEDIO** · Depende de `C-14`, `C-16`, `C-17`, `C-18`, `C-19`, `C-20`, `C-21` y `C-22` · Responsable: Maxi · Aprobación: Maxi.
>
> Trabajo anticipado: esta propuesta se prepara con C-13, C-14, C-16, C-17, C-18 y C-22 todavía abiertos. `tasks.md` separa lo que se puede implementar ya, sobre lo que está en `main` (C-01 a C-12, C-15, C-19, C-20 y C-21), de lo que espera a cada dependencia. El change se cierra recién cuando todas estén `[x]`.

## Why

La v1 tiene sus funcionalidades repartidas en 22 changes, cada uno probado por su cuenta, pero nada prueba hoy el recorrido completo en un navegador real ni revisa la seguridad de la API entera de una vez. Antes de cargar pacientes reales hace falta un humo E2E que corra en CI antes de cada release, una revisión de seguridad con evidencia, la configuración para correr detrás del reverse proxy del VPS sin que todos los consultorios compartan el cupo del rate limit, y una lista escrita de lo que falta para lanzar (DD-40, DD-28). Sin eso no hay forma honesta de decir que la v1 está lista.

## What Changes

- **Humo E2E con Playwright** en viewport mobile (390×844), contra el stack de Docker Compose con el seed de demo:
  - login → crear turno en la agenda (necesita C-13 y C-14);
  - reserva por link de punta a punta: página pública → reserva → link de gestión → cancelar (necesita C-16).
  - Un job de CI nuevo lo corre en cada PR a `main` y antes de cada release. Mientras faltan las pantallas, el humo arranca con login → agenda visible.
- **IP real detrás del reverse proxy**: `server.forward-headers-strategy` configurable por entorno (`none` por defecto, como hoy), y un test que prueba que con la estrategia activa dos IPs distintas tienen cupos separados y que un `X-Forwarded-For` no se puede usar para esquivar el límite cuando el proxy lo reemplaza.
- **Revisión de seguridad con evidencia**, documentada en `docs/release/revision-seguridad.md`: CSP y cabeceras, rate limits, enmascarado de PII en logs, tokens fuera del `Referer` y de la caché, secretos solo por entorno, aislamiento entre consultorios en toda la API y escrituras bloqueadas con el plan `VENCIDO`. Cada punto indica el test o la configuración que lo cubre, o el arreglo hecho.
- **Matriz de aislamiento de toda la API**: un test que recorre todos los endpoints privados con ids de otro consultorio y espera 404, como ya hace `MatrizDeEscriturasTest` con el plan vencido. Cierra por defecto: un endpoint privado que nadie sumó a la matriz hace fallar el test. Así quedan cubiertos los de C-17 y C-18 al rebasar, y los que se agreguen después de la v1.
- **Respuestas sin caché**: las respuestas de la API privada y de las rutas por token llevan `Cache-Control: no-store`.
- **Revisión de `/seguridad` contra el código** (RN-PL-06) cuando C-22 esté en `main`: cada afirmación de la página debe tener un test, una configuración o un documento que la respalde.
- **Seed de demo completo** (perfil `local`): agenda con turnos de todos los estados y orígenes, una reserva hecha por link con su token de gestión, un paciente para revisar (C-17), novedades sin ver, historial de auditoría (C-18) y un segundo consultorio con el plan vencido para mostrar el modo de solo lectura. Todo ficticio (regla dura 17).
- **README de arranque** con `docker compose up --build`, las cuentas de demo, un recorrido de prueba y cómo correr el humo E2E.
- **Textos legales**: revisar que los borradores de `docs/legal/` sigan marcados "pendiente de revisión legal" (DD-28, PA-06) y no declaren ningún cumplimiento sin verificar.
- **Checklist de lanzamiento** en `docs/release/checklist-lanzamiento.md`, sin resolverlo en código: los cinco requisitos de DD-40 (VPS y backups en Argentina con restauración probada, TLS, SMTP declarado, email de seguridad), la revisión legal de DD-28, la validación de los valores por defecto con odontólogos, el despliegue detrás del proxy y cómo medir las métricas del piloto.

**Fuera de alcance**:
- Contratar el VPS, el dominio, el proveedor de SMTP o el abogado: el checklist los deja anotados y bloquea el lanzamiento, pero no los resuelve (DD-40).
- Escribir los textos legales definitivos (DD-28).
- Pruebas de carga, pentest externo y monitoreo de producción.
- Funcionalidades nuevas: cualquier defecto que encuentre la revisión se corrige acá solo si es chico; si no, va a un issue del change dueño.

## Capabilities

### New Capabilities

- `humo-e2e`: humo de punta a punta en navegador mobile contra el stack local, y su ejecución en CI antes de cada release.
- `datos-de-demo`: datos ficticios que carga el perfil `local` para demostrar y probar la v1 completa.

### Modified Capabilities

- `seguridad-http`: se suman dos requisitos: la IP real del cliente detrás de un proxy de confianza para el rate limit, y respuestas privadas y por token sin caché.
- `aislamiento-tenant`: se suma el requisito de que toda la API privada, incluidos los endpoints futuros, responda 404 ante ids de otro consultorio, verificado por una matriz automática.

## Impact

- **Backend**: `application.yml` (estrategia de forwarded headers), `ConfiguracionDeSeguridad` (`Cache-Control`), tests nuevos de matriz de aislamiento y de IP detrás del proxy, y `db/seed-local/R__demo.sql`.
- **Frontend / E2E**: carpeta nueva `e2e/` con su propio `package.json` y la configuración de Playwright, sin tocar el bundle de la app.
- **CI**: job nuevo `E2E (humo)` en `.github/workflows/ci.yml`, que levanta Docker Compose.
- **Documentación**: `README.md`, `backend/README.md`, `docs/release/` (nueva) y, si hace falta, `docs/legal/`.
- **Dependencias nuevas**: `@playwright/test` (solo en `e2e/`, desarrollo y CI).
- **Coordinación**: depende de que C-13, C-14 y C-16 dejen selectores estables (roles y nombres accesibles) para el humo, y de que C-17, C-18 y C-22 entren a `main` para la matriz completa, el seed y la revisión de `/seguridad`.
