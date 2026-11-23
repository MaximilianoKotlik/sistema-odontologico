# Tasks

> Strict TDD: cada tarea de código arranca con un test que falla (red → green → triangulate → refactor).
> **Decisión de Maxi (2026-11-21)**: no se implementa ninguna tarea hasta que C-13, C-14, C-16, C-17, C-18 y C-22 estén `[x]`. Hasta entonces el PR tiene solo la propuesta.
> Cuando estén todas, el orden es el de los grupos: los grupos 1 a 6 no dependen de esos changes, el grupo 7 integra lo que trajo cada uno y el grupo 8 cierra.

## 0. Propuesta (trabajo anticipado)

- [x] 0.1 Aprobación de Maxi de esta propuesta, registrada en `design.md` con la fecha y el PR (2026-11-21, #65)

## 1. IP real detrás del proxy (D5)

- [x] 1.1 Test con servidor real (`RANDOM_PORT`) y la estrategia por defecto: logins con distintos `X-Forwarded-For` desde la misma conexión comparten el cupo. Verificar que pasa ya, sin cambios de código
- [x] 1.2 Test con `FORWARD_HEADERS_STRATEGY=native`: dos IPs detrás del proxy tienen cupos separados, y un `X-Forwarded-For` falseado por el cliente no cambia el cupo. Verificar rojo y después verde con `server.forward-headers-strategy: ${FORWARD_HEADERS_STRATEGY:none}` en `application.yml`
- [ ] 1.3 (KB y README hechos; falta `.env.example`, que el agente no puede leer por permisos: lo suma Maxi) Sumar `FORWARD_HEADERS_STRATEGY` a `.env.example`, a la tabla de variables de `knowledge-base/08_arquitectura_propuesta.md` y a `backend/README.md` §IP de origen detrás de un proxy, con `internal-proxies`. Verificar que los ejemplos coinciden con la configuración

## 2. Respuestas sin caché (D7)

- [x] 2.1 Tests de `Cache-Control: no-store` en una lectura privada, un 400 de validación, un 404 y el 403 `PLAN_VENCIDO`, y en la descarga de la exportación. Arreglar solo los casos que fallen y verificar que todos pasan

## 3. Matriz de aislamiento (D6)

- [x] 3.1 `MatrizDeAislamientoTest` con dos consultorios con datos, que recorre `RequestMappingHandlerMapping` y falla nombrando cada endpoint privado que no está en el registro. Verificar que falla con el registro vacío
- [x] 3.2 Registro de los endpoints privados de `main` (configuración, prestaciones, disponibilidad, turnos, bloqueos, pacientes, plan y exportación), con su recurso y un cuerpo válido. Verificar 404 `RECURSO_NO_ENCONTRADO` sin cambios en la base ni eventos para cada uno, y que los listados no tienen ids del otro consultorio
- [x] 3.3 (ningún endpoint falló: no hizo falta arreglo ni issue) Si algún endpoint no aísla, arreglarlo acá con su test si el arreglo es chico, o abrir un issue para el change dueño (D9). Verificar que la matriz queda en verde o con el issue anotado

## 4. Seed de demo (D8)

- [x] 4.1 Ampliar `SeedLocalTest` con los datos nuevos del consultorio `demo` (turnos `CONFIRMADO` y `AUSENTE`, origen `LINK`, novedad sin ver, paciente para revisar, eventos de auditoría y token de gestión) y verificar que falla
- [x] 4.2 Ampliar `R__demo.sql` con datos ficticios (regla 17) hasta que pase `SeedLocalTest`
- [x] 4.3 `R__demo_vencido.sql` con el consultorio `demo-vencido` en plan `VENCIDO`, con su test en `SeedLocalTest`, y verificar que el perfil por defecto no carga ningún consultorio de demo
- [x] 4.4 (verificado el 2026-11-23 sobre una base nueva en un proyecto de Compose aparte, para no borrar el volumen de nadie: agenda, link de gestión, página pública vencida, solo lectura con 403 y exportación 200) Actualizar `db/seed-local/README.md` con las cuentas, el token de gestión en claro y qué muestra cada dato. Verificar a mano con `docker compose down -v && docker compose up --build` que la agenda, el link de gestión y el modo de solo lectura se ven como dice el README

## 5. Humo E2E: base (D1 a D4)

- [x] 5.1 Crear `e2e/` con `package.json`, `@playwright/test`, `tsconfig.json` strict, lint y `playwright.config.ts` (Chromium, 390×844, `isMobile`, `hasTouch`, `es-AR`, zona de Buenos Aires, un reintento en CI). Verificar que `npm ci` y `npx playwright test --list` corren
- [x] 5.2 Recorrido login → agenda visible con la cuenta de demo, y login con contraseña incorrecta → error. Verificar que pasa contra `docker compose up` y que falla si se rompe el login
- [x] 5.3 Recorrido de la página pública del consultorio `demo-vencido`: no ofrece reservar y muestra el teléfono. Verificar que pasa contra el stack local
- [x] 5.4 Script `npm run humo` y sección "Humo E2E" en el `README.md` raíz. Verificar que el comando documentado corre tal cual

## 6. Humo E2E en CI (D3)

- [ ] 6.1 Job `E2E (humo)` en `.github/workflows/ci.yml`: levanta Compose con `.env.example`, espera `/actuator/health`, instala Chromium, corre el humo y baja el stack. Verificar que pasa en el PR
- [ ] 6.2 Subir `playwright-report/` y `test-results/` como artefacto si el job falla. Verificar rompiendo un recorrido a propósito en un commit temporal y revirtiéndolo
- [ ] 6.3 Medir la duración del job en el PR. Si pasa de 10 minutos, proponer a Maxi un ajuste de D3 antes de seguir
- [ ] 6.4 Maxi suma `E2E (humo)` a los checks requeridos de la protección de `main` (configuración de GitHub, a mano). Verificar en un PR que el check aparece como requerido

## 7. Integración con lo que trajo cada dependencia

- [x] 7.1 **C-13**: rebasar y cambiar el recorrido de 5.2 para que valide la agenda real del día de la semana de demo. Verificar que pasa en CI
- [x] 7.2 **C-14**: sumar al recorrido del odontólogo crear un turno en un hueco libre con un paciente único por corrida, verlo en la agenda y cancelarlo al final (D4). Verificar dos corridas seguidas sobre el mismo stack
- [x] 7.3 **C-16**: recorrido del paciente: página pública → prestación → hueco → datos y política → confirmación → link de gestión → cancelar. Verificar dos corridas seguidas sobre el mismo stack
- [x] 7.4 **C-17**: rebasar y sumar al registro de la matriz de aislamiento (3.2) y a `MatrizDeEscriturasTest` los endpoints nuevos de pacientes. Verificar que las dos matrices pasan y que el paciente para revisar del seed se ve en la pantalla de C-17
- [x] 7.5 **C-18**: rebasar y sumar al registro de la matriz los endpoints de historial y registro de cambios. Verificar que la matriz pasa y que el historial del turno reprogramado del seed se ve completo
- [x] 7.6 **C-22**: revisar `/seguridad` contra el código (D10): cada afirmación con su evidencia en `docs/release/revision-seguridad.md`, y corregir o marcar "pendiente" las que no la tengan. Verificar que no queda ninguna afirmación sin fila

## 8. Revisión, documentación y cierre

- [x] 8.1 `docs/release/revision-seguridad.md` (D9) con cabeceras, rate limits, PII en logs, tokens en `Referer` y caché, secretos solo por entorno, aislamiento y plan vencido, cada fila con su evidencia y estado. Verificar que cada test citado existe y pasa
- [x] 8.2 Revisar `docs/legal/`: siguen marcados "pendiente de revisión legal" (DD-28, PA-06) y no declaran ningún cumplimiento sin verificar. Verificar con una búsqueda de "cumple", "certific" y "garantiza" que cada aparición está justificada
- [x] 8.3 `docs/release/checklist-lanzamiento.md` (D9): los requisitos de DD-40, DD-28, la validación de los valores por defecto con odontólogos, el despliegue detrás del proxy, servir el build del frontend con el humo repetido contra él, y la medición de las métricas del piloto. Cada punto con dueño y casilla. Verificar que nombra los cinco requisitos de DD-40
- [x] 8.4 README raíz completo: arranque con `docker compose up --build`, cuentas de demo, recorrido de prueba y humo E2E. Verificar siguiendo el README en una base recreada
- [ ] 8.5 Corrida completa en verde: `./gradlew test`, `npm test` del frontend, el humo local y todos los jobs de CI. Verificar en el PR

## Workflow follow-up

- Con todo en verde y las dependencias `[x]`, el commit de cierre en el mismo PR: `[x]` completado (PR #N, fecha) en `CHANGES.md` y `openspec archive e2e-hardening-release`.
- El checklist de lanzamiento se resuelve fuera de C-23: bloquea cargar pacientes reales y publicar `/seguridad`, no el cierre del change.
