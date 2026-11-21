# Tasks

> Strict TDD en cada tarea: test en rojo → verde → triangular → refactor dentro del ciclo. **No arrancar** hasta que se cumplan tres condiciones:
> - C-09 y C-15 están `[x]`.
> - Maxi aprobó esta propuesta (ALTO), incluidas las dependencias nuevas (design D6).
> - Las preguntas de design §Open Questions están respondidas.
>
> El frontend (grupo 8) además necesita el **mockup aprobado** de `/app/exportar`.
>
> Verificación base: `./gradlew test` y `npm run typecheck && npm run lint && npm test` en verde. Los tests de integración extienden `IntegracionPostgresTest` (Testcontainers, PostgreSQL 16) y usan solo datos ficticios evidentes (regla dura 17).

## 1. Preparación

- [ ] 1.1 Rebasar sobre `main` con C-09 y C-15 (y con ellos C-08, C-10 y C-11) mergeados. Verificar los nombres reales de las tablas y columnas que se exportan contra las migraciones, y ajustar las listas blancas de design D3 si difieren (verificación: suite existente en verde y la lista de columnas por entidad en el PR)
- [ ] 1.2 Volcar al design las respuestas de Maxi a §Open Questions, ajustando specs y tareas si cambian (verificación: `openspec validate exportacion-datos --strict` en verde)
- [ ] 1.3 Agregar `openpdf` (versión fijada), `pdfbox` solo para tests y la fuente Noto Sans con su licencia OFL en `resources/fonts/` (verificación: `./gradlew dependencies` muestra las versiones fijadas y un test carga la fuente)

## 2. Formatos (unitarios, sin base)

- [ ] 2.1 `EscritorCsv`: RFC 4180, UTF-8 sin BOM, `\r\n`, comillas y saltos de línea dentro de celdas, y escape de fórmulas de design D5 (verificación: tests unitarios, incluido el escenario "Nombre con fórmula")
- [ ] 2.2 `EscritorJson` fila por fila con Jackson: arreglo vacío, instantes ISO-8601 UTC y JSONB como objeto (verificación: tests unitarios que parsean la salida)
- [ ] 2.3 `ManifestDeExportacion` con el SHA-256 y los bytes calculados mientras se escribe (verificación: test que recalcula el hash de cada entrada y compara)

## 3. Lectores por entidad con lista blanca

- [ ] 3.1 Un `ExportadorDe{Entidad}` por cada una de las 8 entidades, con columnas explícitas, filtro por `ConsultorioId` y lectura por streaming (verificación: test de integración por entidad con dos consultorios, en el que B no aparece; regla `repositoriosSiempreConTenant` en verde)
- [ ] 3.2 Test de arquitectura: ningún exportador lee `refresh_token`, `token_recuperacion` ni `token_gestion_turno`, y ninguna lista blanca contiene `password_hash` ni `token_hash` (verificación: test que inspecciona las listas declaradas)

## 4. PDF

- [ ] 4.1 `GeneradorDeResumenPdf` con las secciones de design D6, fechas en la zona del consultorio y la fuente embebida (verificación: escenarios "Secciones mínimas" y "Caracteres en español", con el texto extraído por PDFBox)

## 5. Caso de uso, límite y auditoría

- [ ] 5.0 `PedidoDeExportacion` y regla ArchUnit: `exportacion.application` no depende de `TenantContext` ni de `SesionActual` (verificación: `ArchitectureTest` detecta un fixture que la viola)
- [ ] 5.1 `ExportarDatos(PedidoDeExportacion, OutputStream)` con `TransactionTemplate` de solo lectura y `REPEATABLE READ`, abierto en el hilo del streaming (design D2): arma el ZIP completo con el manifest al final y filtra por `pedido.consultorio()` (verificación: escenarios "Archivos presentes", "Entidad vacía", "Datos que no son del presente", "Cantidades que coinciden" e "Integridad de los archivos")
- [ ] 5.2 Consistencia con escrituras concurrentes: un turno creado durante la exportación queda igual en el manifest, el JSON y el CSV (verificación: escenario "Turno creado durante la exportación", con un latch en el exportador de turnos)
- [ ] 5.3 Límite de design D7 en el hilo de la petición: `GuardiaDeExportaciones`, último evento y `Retry-After`, más `pg_try_advisory_xact_lock` como segunda defensa en el streaming. La guardia se libera siempre, también ante errores (verificación: escenarios "Otra exportación a los 2 minutos", "Pasados 5 minutos", "Dos pedidos simultáneos" y "Otro consultorio no comparte el límite", con `Clock` fijo)
- [ ] 5.3b Liberación de la guardia (pedido de Maxi en el PR #32). Después de cada uno de estos casos, una exportación nueva del mismo consultorio no recibe 429 por la guardia:
  - el streaming termina bien;
  - el streaming falla a mitad de camino (excepción en un exportador);
  - el cliente corta la descarga;
  - el 429 por exportación reciente se decide en la petición, antes de empezar el streaming;
  - otro error en la petición antes de devolver el `StreamingResponseBody`.

  (verificación: test por caso que consulta `GuardiaDeExportaciones` vacía para el consultorio y repite el pedido; el de la falla también verifica que no se registró `EXPORTACION_GENERADA`)
- [ ] 5.4 Evento `EXPORTACION_GENERADA` al final, en `REQUIRES_NEW`, con el actor y la IP del pedido, y nada si falla el streaming (verificación: escenarios "Evento registrado", "Actor e IP del evento" y "Falla durante la generación")
- [ ] 5.5 Plan `VENCIDO` (verificación: escenario "Plan vencido")

## 6. Endpoint y seguridad

- [ ] 6.1 `POST /api/v1/exportacion`: el controlador arma el `PedidoDeExportacion` y decide el 429 antes de devolver el `StreamingResponseBody`, con cabeceras de descarga, `Cache-Control: no-store`, 401 sin sesión y 429 `EXPORTACION_RECIENTE` en Problem Details (verificación: escenarios "Exportación con datos" y "Sin sesión" en MockMvc + Testcontainers)
- [ ] 6.1b Test de integración que descarga el ZIP real por MockMvc con `asyncDispatch`, lo abre y verifica que el consultorio sea el de la sesión, y que el evento quede con el `usuario_id` y la IP de la petición (verificación: escenarios "Exportación con datos" y "Actor e IP del evento" corriendo el streaming en el hilo asíncrono real)
- [ ] 6.2 Búsqueda de secretos de punta a punta: hash de contraseña, refresh, recuperación y token de gestión conocidos, buscados en todas las entradas y en el texto del PDF (verificación: escenario "Búsqueda de secretos en el ZIP")
- [ ] 6.3 Aislamiento por tenant de punta a punta (verificación: escenario "Dos consultorios con datos")
- [ ] 6.4 Medición de volumen: 10 000 turnos y 1 000 pacientes, con el tiempo y el pico de memoria registrados en el PR (verificación: test etiquetado `@Tag("volumen")`, fuera de la suite por defecto, con la salida pegada en el PR)

## 7. Contrato

- [ ] 7.1 Anotar el endpoint con springdoc (respuesta `application/zip` y errores 401/429) y regenerar `frontend/src/shared/api-types.ts` (verificación: job "api-types" de CI sin diferencias)

## 8. Frontend (requiere mockup aprobado)

- [ ] 8.1 `descargar()` en `shared/http.ts` con autorización, refresh único, Problem Details, `Content-Disposition` y `Retry-After` (verificación: escenarios del spec `cliente-api` en `http.test.ts`)
- [ ] 8.2 Feature `exportacion/` y `pages/exportar.ts` según el mockup aprobado ([`mockups/exportar.html`](mockups/exportar.html) y sus capturas en `mockups/previews/`, criterio de aceptación): contenido, progreso, éxito, 429 con minutos restantes, sin conexión y corte de descarga, con un solo pedido en vuelo (verificación: escenarios del spec `pantalla-exportacion` en Vitest + jsdom)
- [ ] 8.3 Accesibilidad AA: foco, 44 px, `aria-live` y reduced motion (verificación: escenario "Navegación con teclado" y revisión con el checklist de `accessibility`)
- [ ] 8.4 Corregir el comentario de `pages/seccion-pendiente.ts` (C-18 y C-19 cruzados) y quitar `exportar` de las secciones pendientes (verificación: `shell.test.ts` en verde)

## 9. Verificación integral

- [ ] 9.1 Con `docker compose up` y el seed `demo`: exportar desde `/app/exportar`, abrir el ZIP, revisar el PDF y el CSV en una planilla y pedir otra antes de 5 minutos. Comparar con el mockup en 390x844 y 1440x900 (verificación: capturas en `mockups/verify/` y nota de visual verify en `design.md`)

## Workflow follow-up

- Al aprobar el PR: commit de cierre con `[x]` en CHANGES.md, las correcciones de documentación de design §Open Questions 6 y `openspec archive exportacion-datos`.
