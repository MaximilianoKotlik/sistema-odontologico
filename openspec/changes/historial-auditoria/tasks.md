# Tasks

> Strict TDD en cada tarea: test en rojo → verde → triangular → refactor dentro del ciclo. **No arrancar** hasta que se cumplan tres condiciones:
> - C-12 y C-13 están `[x]`.
> - Maxi aprobó esta propuesta y los mockups.
> - Las preguntas de design §Open Questions están respondidas.
>
> Verificación base: `./gradlew test` y `npm run typecheck && npm run lint && npm test` en verde. Los tests de integración extienden `IntegracionPostgresTest` (Testcontainers, PostgreSQL 16) y usan solo datos ficticios evidentes (regla dura 17).

## 1. Preparación

- [ ] 1.1 Rebasar sobre `main` con C-12 y C-13 mergeados. Verificar el puerto de lectura del turno de C-12 (design D2), los nombres de columnas de `turno`, `paciente` y `usuario` que usa D3, y las acciones que publican los módulos (D6) (verificación: suite existente en verde y la lista de acciones en el PR)
- [ ] 1.2 Volcar al design las respuestas de Maxi a §Open Questions, ajustando specs y tareas si cambian (verificación: `openspec validate historial-auditoria --strict` en verde)

## 2. Dominio y cursor

- [ ] 2.1 `FiltrosDeAuditoria`: `turnoId`, `desde`, `hasta` y acciones, con la validación del spec ("Rango invertido", formato de acción) (verificación: tests unitarios)
- [ ] 2.2 `CursorDeAuditoria`: codifica `(ocurridoAt, id, consultorio)`, lo firma con HMAC y rechaza firmas alteradas o de otro consultorio (verificación: tests unitarios del escenario "Cursor inválido o de otro consultorio")
- [ ] 2.3 Conversión del rango de fechas locales a instantes con la zona del consultorio (design D7) (verificación: escenario "Filtro por rango de fechas en la zona del consultorio" a nivel unitario)

## 3. Lectura

- [ ] 3.1 `LecturaDeAuditoriaJdbc` con la consulta de design D3, filtros y keyset (verificación: escenarios "Registro sin filtros", "Filtro por turno", "Filtro por acción" y "Filtros combinados sin coincidencias" en integración, con eventos insertados por SQL)
- [ ] 3.2 Actor y contexto con joins que repiten el consultorio (verificación: escenarios "Evento de un paciente" y "Evento del sistema", más un caso con un `actor_id` que apunta a un usuario de otro consultorio y no trae nombre)
- [ ] 3.3 Paginación estable (verificación: escenarios "Dos páginas" y "Evento nuevo entre páginas")
- [ ] 3.4 Medición con 50 000 eventos y `EXPLAIN` de las tres variantes (sin filtros, por turno y por acción), con la salida en el PR. Si alguna supera 100 ms, se agrega el índice de design D4 (verificación: test etiquetado `@Tag("volumen")`)

## 4. Casos de uso y API

- [ ] 4.1 `ConsultarAuditoria.historialDeTurno` con el 404 de design D2 (verificación: escenarios "Turno reservado por link y reprogramado", "Turno de otro consultorio" y "Turno inexistente")
- [ ] 4.2 `GET /api/v1/turnos/{id}/historial` y `GET /api/v1/auditoria` en `ControladorDeAuditoria`, con 400 `VALIDACION` por campo y `tamano` de 1 a 100 (verificación: escenarios de "Validación de los filtros" y "Paginación por cursor" en MockMvc + Testcontainers)
- [ ] 4.3 Aislamiento por tenant en los dos endpoints (verificación: escenarios "Consultorio sin eventos de otro" y "Turno de otro consultorio en el filtro", regla dura 6)
- [ ] 4.4 Solo lectura y plan `VENCIDO` (verificación: escenario "Plan vencido", contando los eventos antes y después)
- [ ] 4.5 Anotar los endpoints con springdoc y regenerar `frontend/src/shared/api-types.ts` (verificación: job "api-types" de CI sin diferencias)

## 5. Frontend (requiere mockups aprobados)

- [ ] 5.1 `features/auditoria/catalogo.ts` y `formatearCambio` de design D6 (verificación: Vitest de etiquetas, verbos por actor, formato de instantes en la zona del consultorio, clave desconocida, y comparación con la lista de acciones de la tarea 1.1)
- [ ] 5.2 Tarjeta "Historial" en `features/agenda/detalle.ts`, según `mockups/turno-historial.html` (verificación: escenarios "Turno reservado y reprogramado" y "Error al cargar el historial" en Vitest + jsdom)
- [ ] 5.3 Sección `/app/auditoria` según `mockups/registro.html`: filtros en la URL, chip del turno, agrupación por día, "Cargar más" con foco y estados (verificación: escenarios de "Sección Registro de cambios", "Filtros en la URL" y "Paginación y estados de la lista")
- [ ] 5.4 Accesibilidad AA (verificación: escenario "Lector de pantalla en un cambio" y revisión con el checklist de `accessibility`)

## 6. Verificación integral

- [ ] 6.1 Con `docker compose up` y el seed `demo`: reservar por link, reprogramar desde la agenda, abrir el historial del turno y el registro con cada filtro. Comparar con los mockups en 390x844 y 1440x900 (verificación: capturas en `mockups/verify/` y nota de visual verify en `design.md`)

## Workflow follow-up

- Al aprobar el PR: commit de cierre con `[x]` en CHANGES.md y `openspec archive historial-auditoria`.
