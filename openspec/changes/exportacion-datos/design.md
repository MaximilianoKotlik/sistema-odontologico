# Design

## Context

- Ver `proposal.md` §Why. Governance **ALTO**: se propone y se espera la revisión de Maxi antes de escribir código.
- **Base disponible en `main`**:
  - C-02: `TenantContext`, `ConsultorioId`, `Reloj`, `ErrorDeNegocio` y la regla `repositoriosSiempreConTenant`.
  - C-03: `PublicadorDeAuditoria` y `evento_auditoria`, con `SELECT` e `INSERT` para `app_rw`.
  - C-04 y C-05: el usuario autenticado (`sub`, `cid`).
  - C-06:
    - la ruta `/app/exportar` con `seccionPendiente`;
    - el ítem "Exportar mis datos" en la hoja "Más" del app-shell;
    - `shared/http.ts` (solo JSON) y `estadoVacio`.
  - El paquete `exportacion` existe y está vacío.
- **Tablas que se exportan y quién las crea**:
  - `consultorio` y `usuario` (C-02 y C-04);
  - `tipo_prestacion` y `disponibilidad` (C-08);
  - `paciente`, `turno` y `bloqueo` (C-10);
  - `evento_auditoria` (C-03);
  - y `token_gestion_turno` (C-15), que se excluye.
- **Mockup**: el sistema de diseño está en `system-and-mockups` (rev. 1 aprobada). Ver §UI Mockups.

## Goals / Non-Goals

**Goals:**
- Que el ZIP sea completo, consistente consigo mismo y con la base, y legible sin el sistema: JSON y CSV para máquinas, PDF para personas.
- Que sea imposible que se filtre un secreto: los campos se eligen uno por uno y un test busca los secretos en todo el ZIP.
- Que la exportación siga funcionando cuando el resto está bloqueado (plan `VENCIDO`).

**Non-Goals:**
- Generación asíncrona, colas o almacenamiento de archivos (DD-14).
- Exportación por paciente o por rango de fechas.
- Formatos FHIR o Excel.

## Decisions

### D1 — `POST /api/v1/exportacion`, y no `GET`
- CHANGES.md dice `POST`, y FL-06 y DD-14 dicen `GET`. Se elige **POST**, por tres razones:
  - la operación tiene efectos: consume el límite y registra un evento;
  - un `GET` puede dispararse por prefetch o reintentos del navegador;
  - el access token vive en memoria (spec `cliente-api`), así que la descarga se hace con `fetch` y no con un link directo.
- Al cerrar el change se actualizan FL-06 y DD-14.

### D2 — Foto consistente con una transacción de solo lectura
- Todo se lee en una transacción `@Transactional(readOnly = true, isolation = REPEATABLE_READ)`. Todas las consultas ven la misma foto, así que las cantidades del manifest coinciden con los archivos aunque haya escrituras concurrentes.
- Cada consulta filtra por el `ConsultorioId` de `TenantContext` y usa `JdbcClient` con `fetchSize` para no cargar todo en memoria.
- **El streaming ocurre dentro de la transacción**: el controlador devuelve un `StreamingResponseBody` que corre el caso de uso. Se usa `TransactionTemplate` explícito, porque el streaming corre en otro hilo, fuera del proxy del controlador.

### D3 — Estructura del ZIP
- **Archivos**:
  - `consultorio.json/.csv` (1 registro);
  - `odontologo.json/.csv` (1 registro);
  - `prestaciones`, `disponibilidad`, `bloqueos`, `pacientes`, `turnos` y `auditoria`;
  - `resumen.pdf`;
  - `manifest.json`, al final, porque necesita las cantidades y los hashes.
- **Columnas por lista blanca**: cada entidad tiene un `ExportadorDe{Entidad}` que declara explícitamente sus columnas. Nunca `SELECT *`: una columna nueva con un secreto no entra sola.
  - `odontologo`: email, nombre, apellido, matrícula, términos aceptados (versión y fecha) y `created_at`. **Sin** `password_hash`, `intentos_fallidos`, `bloqueado_hasta` ni `credenciales_version`.
  - `consultorio`: todo lo configurable, más `plan` y `prueba_hasta`.
  - `turnos`: incluye `nota_interna` y los datos de cancelación. Son del odontólogo.
  - `auditoria`: `valor_anterior` y `valor_nuevo` como objetos JSON en el `.json`, y como texto JSON en el `.csv`.
  - **Tablas excluidas** (RN-EX-02): `refresh_token`, `token_recuperacion` y `token_gestion_turno`.
- **Formato**:
  - JSON escrito con `JsonGenerator` de Jackson, fila por fila.
  - CSV RFC 4180 (comillas dobles cuando hace falta), UTF-8 **sin BOM**, fin de línea `\r\n` y el escape de fórmulas de D5.
  - Instantes ISO-8601 UTC, decimales con punto e ids en texto.
- **`manifest.json`**: `{ versionEsquema: "1", generadoAt, consultorio: {slug}, zonaHoraria, entidades: {nombre: cantidad}, archivos: [{nombre, sha256, bytes}] }`. Los hashes se calculan con un `DigestOutputStream` mientras se escribe cada entrada.
- **`LEEME.txt`**: explica el contenido, el formato de fechas en UTC y que la importación a otros sistemas no está garantizada.

### D4 — Orden de escritura y volumen
- Cada entidad se recorre dos veces dentro de la misma foto: primero para JSON y CSV, después para el PDF.
- Volumen esperado (un sillón): miles de turnos y cientos de pacientes por año. El costo es lineal y la memoria acotada, salvo por el PDF, que OpenPDF arma en memoria (D6). Con 10 000 turnos se estima en pocos MB. La tarea 6.4 lo mide.

### D5 — Escape de fórmulas en CSV
- Toda celda de texto que empiece con `=`, `+`, `-`, `@`, `\t` o `\r` se prefija con `'` (OWASP CSV Injection).
- **Por qué importa acá**: nombres, apellidos y emails de pacientes llegan de un formulario público anónimo (C-15), y el odontólogo abrirá el CSV en una planilla.
- Los números y fechas no se escapan, porque los genera el sistema. Un teléfono E.164 empieza con `+`, pero es texto del sistema normalizado: **también se escapa**, para mantener una sola regla simple. El JSON conserva el valor exacto.

### D6 — PDF con OpenPDF y una fuente embebida
- **Librería**: `com.github.librepdf:openpdf`, con versión fijada al implementar (la última estable con al menos 2 semanas).
  - API de alto nivel con tablas (`PdfPTable`), mantenida, y licencia LGPL 2.1 / MPL 2.0, compatible con un SaaS sin distribuir binarios.
  - *Alternativas*:
    - Apache PDFBox (Apache 2.0) → hay que hacer el layout de tablas a mano.
    - OpenHTMLtoPDF → más dependencias y un motor HTML para un documento simple.
- **Fuente**: se embebe una TTF con licencia OFL, Noto Sans Regular y Bold (~1 MB en `resources/fonts/`, con su licencia). Así se ven bien la ñ, las tildes y cualquier nombre. Las fuentes estándar del PDF cubren solo WinAnsi.
- **Contenido**:
  - **Portada**: nombre, dirección, teléfono, zona y fecha de generación.
  - **Prestaciones**: nombre, duración, activa y visible.
  - **Pacientes**: apellido y nombre, DNI, teléfono, email y si requiere revisión.
  - **Turnos**: fecha y hora local, paciente, prestación, estado, origen y sobreturno, ordenados por inicio.
  - **Auditoría**: cantidad por acción y primer y último evento. No lista los eventos uno por uno, que ya están en `auditoria.csv`.
  - Pie con la versión del esquema y el número de página.
- Fechas en la zona del consultorio, con formato es-AR (`dd/MM/yyyy HH:mm`).
- **Tests**: se extrae el texto con PDFBox (solo en `testImplementation`) para verificar las secciones, las cantidades, los caracteres y la ausencia de secretos.

### D7 — Límite: 1 cada 5 minutos, durable y sin carreras
- **"Última exportación completada"** = el último `EXPORTACION_GENERADA` del consultorio. Se consulta en `evento_auditoria` por el índice `(consultorio_id, ocurrido_at DESC)`. Así sobrevive a reinicios y no consume cupo si la exportación falla (spec "Falla durante la generación").
  - *Alternativa*: Bucket4j en memoria (C-05) → se pierde al reiniciar y consume cupo aunque la descarga falle.
- **Exportaciones simultáneas**: `pg_try_advisory_xact_lock(hash('exportacion', consultorio_id))` al abrir la transacción de lectura. Si no se obtiene → 429. Los locks consultivos se permiten en transacciones de solo lectura.
- **Orden**: primero el lock y después el último evento, para que dos pedidos no pasen los dos por la consulta.
- `Retry-After` = segundos hasta `último + 5 min`, redondeados hacia arriba. Con un pedido simultáneo: 30 s.
- Código `EXPORTACION_RECIENTE` (429), en vez del `DEMASIADAS_SOLICITUDES` genérico, para que la pantalla muestre un mensaje específico.

### D8 — Evento al final, en otra transacción
- Cuando el ZIP terminó de escribirse (`ZipOutputStream.finish()` sin excepción), se registra `EXPORTACION_GENERADA` con `REQUIRES_NEW`:
  - `entidad = consultorio` y `entidad_id = consultorio_id`;
  - actor `ODONTOLOGO` con su `usuario_id` y la IP;
  - `valor_nuevo = {versionEsquema, entidades: {…cantidades}}`, sin datos de pacientes.
- Es lo que pide FL-06 paso 4. La regla de "misma transacción" de RN-AD-03 aplica a cambios de datos, y la exportación no modifica nada.
- **Si la escritura del evento falla después de enviar el ZIP**: se loguea un error sin PII. El archivo ya se entregó, y el límite queda sin aplicar para ese pedido. Riesgo aceptado (Risks).
- **Si falla el streaming**: se aborta la respuesta sin `finish()` (el cliente recibe un ZIP truncado e inválido) y no se registra el evento.

### D9 — Plan `VENCIDO`
- El endpoint no consulta el plan. C-20 implementará el bloqueo de escrituras con el plan vencido, y tiene que excluir de forma explícita `POST /api/v1/exportacion` y el evento `EXPORTACION_GENERADA`.
- Se agrega un test acá con el plan `VENCIDO` seteado por SQL, que queda como guarda cuando llegue C-20.

### D10 — Frontend
- **Cliente** (`shared/http.ts`): se agrega `descargar(ruta, opciones)`, que devuelve `{ contenido: Blob, nombre: string }`.
  - Reusa la autorización, el refresh único y la conversión de Problem Details.
  - Lee `Content-Disposition` con un parser acotado a `filename="…"`.
  - Expone `Retry-After` en `ApiError` (`segundosParaReintentar`).
- **Guardado**: `URL.createObjectURL` y un `<a download>` temporal, y después `revokeObjectURL`. El ZIP se arma en memoria del navegador; para los volúmenes de D4 alcanza.
- **Feature `exportacion/`**: `pagina-exportar.ts` monta el contenido del spec `pantalla-exportacion`, con los tokens del sistema de diseño, la región `aria-live` y un único pedido en vuelo. `pages/exportar.ts` deja de usar `seccionPendiente`.
- Se corrige el comentario de `seccion-pendiente.ts`, que tiene cruzados C-18 y C-19.

## Risks / Trade-offs

- **[Falla el registro del evento después de entregar el ZIP]** → no queda auditada esa exportación y el límite no la cuenta. Es poco probable (la base ya respondió toda la lectura) y se loguea como error. *Alternativa*: registrar el evento antes de enviar → no cumple "si falla la generación, no hay evento".
- **[Transacción larga de solo lectura]** → retiene una foto MVCC mientras dura la descarga. Con los volúmenes de D4 son segundos. Si el cliente es muy lento, el `StreamingResponseBody` corre con el timeout asíncrono de Spring, que se configura en 5 minutos.
- **[PDF en memoria]** → acotado por D4. Si crece, la alternativa es paginar la sección de turnos o sacarla del PDF (DD-14 ya prevé migrar a asíncrono).
- **[Nueva dependencia y fuente embebida]** → +~1,5 MB en el JAR. Se suma la licencia OFL de la fuente a `backend/src/main/resources/fonts/`.
- **[Escapar el `+` del teléfono en el CSV]** → el CSV muestra `'+549…`. Se prioriza una regla simple y segura. El JSON lo conserva.
- **[Mockup pendiente]** → la pantalla no se implementa sin mockup aprobado. El backend puede avanzar antes (tareas 1 a 7).

## Migration Plan

- Sin migraciones. Rollback = no mergear.
- Si en el futuro cambia el formato, sube `versionEsquema` del manifest. Las exportaciones viejas siguen siendo legibles con su versión.

## Open Questions

Para la revisión de Maxi:

1. **Método HTTP** (D1): `POST`, contra el `GET` de FL-06 y DD-14. ¿Se confirma?
2. **Entidad `odontologo`**: no está en la lista de RN-EX-01. Propongo incluirla sin secretos, por la portabilidad de los datos del propio titular (11 §Derechos del titular). ¿Se confirma, o se actualiza RN-EX-01?
3. **Dependencias**: OpenPDF y la fuente Noto Sans (OFL) embebida (D6).
4. **Escape del `+` en teléfonos del CSV** (D5): ¿regla simple para todo, como propongo, o una excepción para los teléfonos E.164 que genera el sistema?
5. **Mockup de `/app/exportar`**: según el plan del equipo, lo hace Octi (paso 5). ¿Lo hace él o lo hago yo en este mismo PR?
6. **Correcciones de documentación al cerrar**: CHANGES.md §C-19 (pantalla en `configuracion/` → `/app/exportar`), FL-06, DD-14 y 03 (método), y RN-EX-01 si se confirma la pregunta 2.

## UI Mockups

- Design system revision: 1
- Decision: yes (2026-11-20). La sección `/app/exportar` es una pantalla nueva.
- Mockups: `openspec/changes/exportacion-datos/mockups/` (pendiente; responsable según la pregunta 5). Estados: inicial, generando, éxito, exportación reciente (429), sin conexión y error en la descarga, en 390x844 y 1440x900.
- Approval: pending
- Preview: pendiente
