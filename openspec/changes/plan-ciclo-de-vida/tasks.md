# Tasks

> Strict TDD en cada tarea: test en rojo → verde → triangular → refactor dentro del ciclo. Governance **CRITICO**: **no arrancar** hasta que se cumplan tres condiciones:
> - C-09 está `[x]`;
> - Maxi aprobó esta propuesta;
> - Maxi aprobó el mockup (`Approval: approved` en `design.md` §UI Mockups).
>
> Verificación base: `./gradlew test` en verde, con PostgreSQL de Testcontainers y el `RelojDePrueba` para toda fecha. Datos ficticios (regla dura 17).

## 1. Estado efectivo del plan

- [ ] 1.1 `EstadoDelPlan` puro (D1): vencido, días restantes y solo lectura. Casos: último día, día siguiente, `ACTIVO` con `prueba_hasta` pasada y una zona horaria distinta de Buenos Aires (verificación: escenarios de "Prueba vencida")
- [ ] 1.2 `ConsultarPlan` y `GET /api/v1/plan` (D5), con test de aislamiento por consultorio y la respuesta con el plan vencido antes de la tarea (verificación: escenarios de "Consulta del plan")

## 2. Vencimiento y activación

- [ ] 2.1 `VencerPruebas` (D2): pasa a `VENCIDO` y audita `PLAN_VENCIDO` con actor `SISTEMA`. Tests: corrida repetida, dos corridas a la vez con un solo evento, y que no toca pruebas vigentes ni `ACTIVO`. Si falla la auditoría, el plan no cambia (verificación: escenarios de "Vencimiento diario de las pruebas")
- [ ] 2.2 `TareaDeVencimiento` con el cron y la zona configurables, desactivada en `test`, y `@EnableScheduling` (verificación: test de que el bean existe con el cron por defecto)
- [ ] 2.3 `ActivarPlan` y el runner del perfil `operacion` (D3), con el runbook en `backend/README.md`. Tests: desde `PRUEBA` y desde `VENCIDO`, ya activo sin evento, inexistente falla (verificación: escenarios de "Pase manual a ACTIVO")
- [ ] 2.4 Ninguna ruta HTTP cambia el plan: `PUT /api/v1/consultorio` con `plan` en el cuerpo lo ignora (verificación: escenario "Ninguna ruta HTTP activa el plan")

## 3. Solo lectura

- [ ] 3.1 `FiltroDePlanVencido` (D4) con 403 `PLAN_VENCIDO` en Problem Details y sin evento (verificación: escenario "Crear un turno con el plan vencido")
- [ ] 3.2 Test de matriz: recorre todas las escrituras de `RequestMappingHandlerMapping` y espera 403 en cada una salvo la lista permitida, para que un endpoint nuevo no quede abierto sin querer (verificación: escenarios de "Escrituras bloqueadas" y "Rutas permitidas")
- [ ] 3.3 Rutas permitidas: sesión, cambio y recuperación de contraseña, `visto` y `POST /api/v1/exportacion` (si C-19 ya está mergeado; si no, el caso queda en la lista y el test lo cubre cuando llegue). Sin token sigue siendo 401 (verificación: escenarios de "Rutas permitidas" y "Sin sesión")
- [ ] 3.4 El 403 `PLAN_VENCIDO` en RN-PL-03 (`knowledge-base/05_reglas_de_negocio.md`), como RN-GL-02 nombra `CONFLICTO_CONCURRENCIA`, y en el contrato OpenAPI (verificación: `ContratoOpenApiTest`)

## 4. Reserva pública

- [ ] 4.1 `ConsultorioPublico.tomaTurnos` con `EstadoDelPlan` (D6). Tests: la página pública, la reprogramación y la cancelación por token con la prueba vencida antes de la tarea, y la suite de C-15 en verde (verificación: specs `reserva-publica` y `gestion-turno-paciente`)

## 5. Frontend

- [ ] 5.1 `features/plan/estado-del-plan.ts`: pide `GET /api/v1/plan`, expone `soloLectura()` y notifica los cambios. Test con Vitest y la API simulada (verificación: escenarios de "Días de prueba restantes")
- [ ] 5.2 `aviso-de-plan.ts` en el shell, según el mockup aprobado: estados de prueba, últimos 7 días, último día y vencido, con `role="status"`, tokens de rev. 1 y textos con voseo (verificación: escenarios de "Modo de solo lectura" y "Aviso accesible")
- [ ] 5.3 El cliente API trata el 403 `PLAN_VENCIDO`: mensaje propio, vuelve a pedir el plan y no reintenta (verificación: escenario "La prueba vence con la app abierta")
- [ ] 5.4 `npm run api:types` regenerado y commiteado (verificación: CI de frontend)

## 6. Cierre del change

- [ ] 6.1 Verificación visual contra el mockup aprobado, a 390×844 y 1440×900, con capturas en `mockups/verify/` (verificación: `Visual verify` en `design.md`)
- [ ] 6.2 `./gradlew test` completo, `ArchitectureTest` en verde y cobertura ≥ 80 % en el código nuevo de `consultorio` (verificación: CI del PR)
- [ ] 6.3 PR listo con la evidencia y la revisión de Maxi. Avisar a Nacho que las pantallas de C-13 y C-14 lean `soloLectura()` para deshabilitar sus acciones, y a Mati que `POST /api/v1/exportacion` está en la lista permitida (verificación: PR con revisor asignado)

## Workflow follow-up

- Antes de pedir la revisión, con todo en verde, el autor suma el commit de cierre en el PR de implementación: `[x]` en `CHANGES.md` (PR #N, fecha) y `openspec archive plan-ciclo-de-vida` (ciclo de #45). Esta propuesta va en un PR en borrador de solo propuesta y mockup: no lleva el cierre.
