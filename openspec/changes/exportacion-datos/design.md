# Design

## Context

- Ver `proposal.md` §Why. Governance **ALTO**: se propone y se espera la revisión de Maxi antes de escribir código.
- **Base disponible en `main`**:
  - C-02: `TenantContext` (`@RequestScope`), `ConsultorioId`, `Reloj`, `ErrorDeNegocio` y la regla `repositoriosSiempreConTenant`.
  - C-08: `SesionActual` (usuario e IP desde `ThreadLocal`).
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
- **El streaming corre en otro hilo, sin petición ni sesión** (revisión de Maxi al PR #32). `StreamingResponseBody` se ejecuta en un hilo del ejecutor asíncrono de Spring. En ese hilo:
  - `TenantContext` (`TenantContextDePeticion`, `@RequestScope`) no se puede leer;
  - `SesionActual` (C-08) no encuentra usuario en el `SecurityContextHolder` ni IP en el `RequestContextHolder`, que son `ThreadLocal`.
- **Por eso el controlador arma un `PedidoDeExportacion(ConsultorioId, UUID usuarioId, String ip, Instant solicitadoAt)` en el hilo de la petición**, antes de devolver el `StreamingResponseBody`.
  - El caso de uso recibe ese pedido como parámetro y nunca lee `TenantContext` ni `SesionActual`.
  - Cada consulta filtra por `pedido.consultorio()` de forma explícita, con `JdbcClient` y `fetchSize` para no cargar todo en memoria.
  - Se agrega una regla ArchUnit: `exportacion.application` no depende de `TenantContext` ni de `SesionActual`.
- **La transacción se abre en el hilo del streaming**: `TransactionTemplate` explícito, de solo lectura y `REPEATABLE READ`. La anotación `@Transactional` del controlador no alcanza, porque el cuerpo corre después y en otro hilo.
- **Todo lo que decide el código de estado ocurre antes**, en el hilo de la petición: la autenticación y el límite de D7. Cuando el streaming empieza, la respuesta ya salió con 200. Un error posterior solo puede cortar la descarga, que es el caso "Falla durante la generación".

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
  - `turnos`: incluye `nota_interna` y los datos de cancelación. Son del odontólogo. **Sin** `novedad_paciente_at`: C-12 la define como marca de lectura de la interfaz, no como dato del turno.
  - `pacientes` (**pendiente de aprobación de Maxi**, ver §Open Questions 7): nombre, apellido, DNI, teléfono, email, origen, `requiere_revision`, `consentimiento_at`, `consentimiento_version`, `created_at` y las cinco columnas de lo informado de DD-38 (`nombre_informado`, `apellido_informado`, `email_informado`, `informado_at` e `informado_turno_id`). Lo informado llega de un formulario anónimo, así que pasa por el escape de D5.
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
> Ajustado por D2: el 429 se decide en el hilo de la petición, antes del streaming. El lock consultivo de la versión anterior vivía en la transacción del otro hilo y no podía producir un 429.

- **Pedidos simultáneos del mismo consultorio**: `GuardiaDeExportaciones`, un registro en memoria por consultorio (`ConcurrentHashMap`). Se toma en el hilo de la petición, y si ya está tomado → 429 con `Retry-After: 30`. Se libera en un `finally` del hilo del streaming, y también si la petición falla antes de empezarlo.
- **"Última exportación completada"**: después de tomar la guardia, en el hilo de la petición, una consulta corta al último `EXPORTACION_GENERADA` del consultorio, por el índice `(consultorio_id, ocurrido_at DESC)`. Si fue hace menos de 5 minutos → se libera la guardia y responde 429 con `Retry-After` = segundos hasta `último + 5 min`, redondeados hacia arriba. Es durable: sobrevive a reinicios y no consume cupo si la exportación falla (spec "Falla durante la generación").
- **Con varias instancias** (hoy hay una sola, 08): la guardia en memoria no las coordina. Como segunda defensa, la transacción del streaming toma `pg_try_advisory_xact_lock` sobre el consultorio. Si no lo obtiene, corta la descarga sin registrar el evento y loguea el caso. No es un 429, pero mantiene el invariante de una exportación a la vez.
- Código `EXPORTACION_RECIENTE` (429), en vez del `DEMASIADAS_SOLICITUDES` genérico, para que la pantalla muestre un mensaje específico.

### D8 — Evento al final, en otra transacción
- Cuando el ZIP terminó de escribirse (`ZipOutputStream.finish()` sin excepción), se registra `EXPORTACION_GENERADA` con `REQUIRES_NEW`:
  - `entidad = consultorio` y `entidad_id = consultorio_id`;
  - actor `ODONTOLOGO` con el `usuarioId` y la IP del `PedidoDeExportacion` (D2), no de `SesionActual`, que en el hilo del streaming no tiene datos;
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

## Migration Plan

- Sin migraciones. Rollback = no mergear.
- Si en el futuro cambia el formato, sube `versionEsquema` del manifest. Las exportaciones viejas siguen siendo legibles con su versión.

## Open Questions

Resueltas por Maxi en la revisión del PR #32 (2026-11-20):

1. **Método**: `POST` (D1). FL-06 y DD-14 se corrigen en el cierre.
2. **Entidad `odontologo`**: se incluye con las columnas de D3, sin secretos. RN-EX-01 se actualiza en el cierre.
3. **Dependencias**: aprobadas OpenPDF y Noto Sans (OFL), con la licencia en `resources/fonts/`. PDFBox solo en tests (D6).
4. **`+` de los teléfonos en el CSV**: se escapa también. El JSON conserva el valor exacto (D5).
5. **Mockup**: se usa el de este PR (versión `45847c9`), aprobado con sus 6 decisiones y sin "última exportación".
6. **Correcciones de documentación**: en el commit de cierre.

Pendiente de la revisión de Maxi (surgió al contrastar con C-15):

7. **Columnas de `pacientes`** (D3): se propone incluir el consentimiento y `requiere_revision`, que son evidencia legal y estado del paciente, y también lo informado (DD-38), porque RN-EX-01 pide una exportación completa y esos datos existen mientras la revisión está pendiente. *Alternativa*: excluir lo informado por minimización (Ley 25.326), con el costo de que el odontólogo no se lleve una revisión sin resolver.

Cambio pedido y aplicado: el pedido de exportación (consultorio, usuario e IP) se arma en el hilo de la petición, porque el streaming corre en otro hilo (D2, D7 y D8).

## UI Mockups

- Design system revision: 1
- Decision: yes (2026-11-20). La sección `/app/exportar` es una pantalla nueva.
- Mockups: [`mockups/exportar.html`](mockups/exportar.html), con su [README](mockups/README.md), brief y decisiones. Estados: `inicial`, `generando`, `listo`, `reciente` (429), `sin-conexion`, `error` y `vencido`.
- Approval: approved (2026-11-20, by Maxi), versión `45847c9`
- Preview: rendered (2026-11-20), 14 capturas en [`mockups/previews/`](mockups/previews/) a 390x844 y 1440x900, sin desborde horizontal en 360, 390 y 1440 px y sin errores de consola.
- Visual verify: pendiente (tarea 9.1).
