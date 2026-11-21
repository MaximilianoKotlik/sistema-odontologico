# Design

## Context

- Ver `proposal.md` §Why. Governance **MEDIO**: se implementa con checkpoints y las decisiones se le muestran a Maxi.
- **Base disponible en `main`**:
  - C-03: `evento_auditoria` con `id` UUIDv7, `ocurrido_at`, `actor_tipo`, `actor_id`, `entidad`, `entidad_id`, `accion`, `valor_anterior`/`valor_nuevo` JSONB e `ip`.
    - Índices `ix_evento_auditoria_consultorio_ocurrido (consultorio_id, ocurrido_at DESC)` e `ix_evento_auditoria_consultorio_entidad (consultorio_id, entidad, entidad_id)`.
    - `app_rw` con `INSERT, SELECT`.
    - `RepositorioDeAuditoria` solo inserta, y su Javadoc dice que "la consulta, con tenant, llega en C-18".
  - C-02: `TenantContext`, `ErrorDeNegocio` y la regla `repositoriosSiempreConTenant`.
  - C-06: la ruta `/app/auditoria` ("Registro de cambios") con `seccionPendiente`, y `shared/fechas.ts`.
  - C-13 (propuesta y mockups mergeados): la página de detalle `/app/agenda/turnos/{id}` con tarjetas (Paciente, Turno, Nota, Acciones) y `features/agenda/detalle.ts`.
- **Lo que llega antes de implementar**:
  - C-12: `GET /api/v1/turnos/{id}` y su puerto de lectura del turno por consultorio.
  - C-10 y C-11: `turno` y `paciente`, para el contexto y el nombre del actor paciente.
  - C-15 y C-19: eventos `PACIENTE_*`, `TURNO_*` con actor `PACIENTE` y `EXPORTACION_GENERADA`.

## Goals / Non-Goals

**Goals:**
- Que el odontólogo entienda cada evento sin conocer el modelo: quién, qué, sobre qué turno y qué cambió.
- Que las consultas sean baratas con los índices de C-03 y que la paginación sea estable en un registro que crece.
- Que no haya forma de leer eventos de otro consultorio, ni de inferir que un turno de otro consultorio existe.

**Non-Goals:**
- Escribir en el registro o cambiar su esquema.
- Auditar lecturas (v2).
- Búsqueda de texto libre o filtro por actor.

## Decisions

### D1 — Módulo `auditoria`: puerto de lectura separado del de escritura
- `auditoria.application.ConsultarAuditoria`, con `historialDeTurno(TurnoId)` y `registro(FiltrosDeAuditoria, Cursor, tamano)`. El consultorio sale de `TenantContext`, en el hilo de la petición, sin streaming.
- `auditoria.application.LecturaDeAuditoria` (puerto) y su `LecturaDeAuditoriaJdbc` (infraestructura). Recibe siempre `ConsultorioId` como primer parámetro (regla `repositoriosSiempreConTenant`).
- **Se separa del `RepositorioDeAuditoria`** de C-03, que solo inserta: así se puede revisar el append-only mirando un solo puerto, y la lectura no puede escribir.
- El controlador `ControladorDeAuditoria` expone los dos endpoints. El historial vive en `/api/v1/turnos/{id}/historial` por su URL, pero lo atiende `auditoria`.

### D2 — 404 del historial con el puerto de C-12
- Antes de leer los eventos, `ConsultarAuditoria` pregunta si el turno existe en el consultorio, con el puerto de lectura de turnos que usa `GET /api/v1/turnos/{id}` (C-12). Si no existe → 404 `RECURSO_NO_ENCONTRADO`, igual que el detalle.
- Así se distingue "turno de otro consultorio" (404) de "turno sin eventos", que en la práctica no ocurre porque todo turno nace con `TURNO_CREADO`.
- En el registro, `turnoId` es solo un filtro. Con un turno de otro consultorio no aparecen eventos, porque la consulta siempre filtra por `consultorio_id`, y no se consulta el turno: lista vacía sin revelar nada.

### D3 — Una consulta con joins para el actor y el contexto
```sql
SELECT e.*, u.nombre AS u_nombre, u.apellido AS u_apellido,
       pa.nombre AS pa_nombre, pa.apellido AS pa_apellido,
       t.inicio AS t_inicio, pt.nombre AS pt_nombre, pt.apellido AS pt_apellido
FROM evento_auditoria e
LEFT JOIN usuario u   ON e.actor_tipo = 'ODONTOLOGO' AND u.id = e.actor_id AND u.consultorio_id = e.consultorio_id
LEFT JOIN paciente pa ON e.actor_tipo = 'PACIENTE'   AND pa.id = e.actor_id AND pa.consultorio_id = e.consultorio_id
LEFT JOIN turno t     ON e.entidad = 'turno' AND t.id = e.entidad_id AND t.consultorio_id = e.consultorio_id
LEFT JOIN paciente pt ON pt.id = t.paciente_id AND pt.consultorio_id = e.consultorio_id
WHERE e.consultorio_id = :consultorio AND …filtros…
ORDER BY e.ocurrido_at DESC, e.id DESC
LIMIT :tamano + 1
```
- Todos los joins repiten `consultorio_id`: un `actor_id` mal cargado no puede traer el nombre de otro consultorio.
- **El contexto del turno muestra su estado actual** (inicio y paciente de hoy). El horario de cada momento está en los valores anterior y nuevo del evento. Se documenta en la UI ("Turno del 12/11 10:00 · Paciente Uno Prueba").
- `LIMIT tamano + 1` sirve para saber si hay otra página sin un `COUNT`.

### D4 — Índices: los de C-03 alcanzan
- **Registro sin filtros o por rango**: `ix_evento_auditoria_consultorio_ocurrido` resuelve el `WHERE consultorio_id = ? AND ocurrido_at BETWEEN …` y el orden.
- **Historial y filtro por turno**: `ix_evento_auditoria_consultorio_entidad (consultorio_id, entidad, entidad_id)`.
- **Filtro por acción**: se aplica sobre el recorrido por fecha. Con volúmenes de un sillón (miles de eventos por año) es suficiente.
- La tarea 3.4 mide con 50 000 eventos. Si no alcanza, se agrega `(consultorio_id, accion, ocurrido_at DESC)` en una migración de este change.

### D5 — Paginación por cursor (keyset)
- **Orden total**: `(ocurrido_at DESC, id DESC)`. El `id` es UUIDv7 y desempata eventos del mismo instante.
- **Cursor**: `base64url(JSON {o: ocurridoAt, i: id, c: consultorioId})`, firmado con HMAC-SHA256 usando una clave derivada del secreto de la aplicación. Se rechaza (400) si la firma no coincide o si `c` no es el consultorio de la sesión.
  - *Por qué firmado*: un cursor alterado no puede saltear el filtro de tenant (que igual se aplica), pero firmarlo evita errores raros y deja la API cerrada a manipulaciones.
- **Siguiente página**: `WHERE (ocurrido_at, id) < (:o, :i)`. Los eventos nuevos tienen `ocurrido_at` mayor y no se cuelan en páginas siguientes. El registro es append-only, así que tampoco desaparecen eventos.
- **`tamano`**: por defecto 50, de 1 a 100. Fuera de rango → 400.
- *Alternativa*: `offset` con total → se corre si entra un evento entre páginas y necesita un `COUNT` por consulta.

### D6 — Catálogo de acciones y formato de valores
- **Backend**: no mantiene un catálogo cerrado. Acepta cualquier `accion` con el formato de `CambioAuditado` (`^[A-Z][A-Z0-9]*(_[A-Z0-9]+)*$`). Así, un módulo que agrega una acción no tiene que tocar `auditoria`.
- **Frontend**: `features/auditoria/catalogo.ts` tiene las etiquetas en español, el grupo del filtro y el verbo según el actor ("Reprogramaste" o "Reprogramó por el link"). Lo verifica un test que compara contra las acciones que publican los módulos:
  - turnos: `TURNO_CREADO`, `TURNO_REPROGRAMADO`, `TURNO_ESTADO_CAMBIADO`, `TURNO_DURACION_CAMBIADA` y `TURNO_NOTA_CAMBIADA`;
  - bloqueos: `BLOQUEO_CREADO` y `BLOQUEO_ELIMINADO`;
  - configuración: `CONSULTORIO_CONFIG_CAMBIADA`, `PRESTACION_*` y `DISPONIBILIDAD_CAMBIADA`;
  - pacientes: `PACIENTE_CREADO`, `PACIENTE_REQUIERE_REVISION`, `PACIENTE_CONSENTIMIENTO_ACTUALIZADO` y `PACIENTE_REVISADO`;
  - accesos: `CUENTA_CREADA`, `LOGIN_EXITOSO`, `LOGIN_FALLIDO`, `PASSWORD_CAMBIADA` y `PASSWORD_RECUPERADA`;
  - exportación: `EXPORTACION_GENERADA`.

  Una acción sin etiqueta se muestra con su código.
- **Valores**: `formatearCambio(clave, valor)` con etiquetas por clave (`inicio`, `fin`, `estado`, `duracion_min`, `nota_interna`, etc.).
  - Instantes en la zona del consultorio con `shared/fechas.ts`, estados con su nombre, minutos y Sí/No.
  - Para `nota_interna` se muestra el texto completo: es del odontólogo, no del paciente.
  - Una clave desconocida se muestra cruda.
- **IP**: viene en la respuesta y se muestra solo en los eventos de acceso (`LOGIN_*` y `PASSWORD_*`), donde sirve para detectar un acceso ajeno. En el resto es ruido.

### D7 — Fechas del filtro
- `desde` y `hasta` son `LocalDate` en la zona del consultorio (RN-TE-05). El intervalo es `[desde 00:00 local, (hasta + 1) 00:00 local)`, convertido a `Instant` con la zona actual del consultorio.
- Si cambió la zona, los eventos viejos se filtran con la zona de hoy. Se acepta: es la misma regla que usa la agenda.

### D8 — Frontend
- **`features/auditoria/api.ts`**: tipos de `api-types.ts` y validación con type guards (`unknown`, regla dura 9).
- **`features/auditoria/registro.ts`** (`/app/auditoria`):
  - **Filtros**: rango con dos `input type="date"`, tipo de acción con `select` agrupado por `optgroup` y una opción "Todas", y el chip del turno.
  - Los filtros se leen y escriben en `URLSearchParams` con `history.replaceState`. Con un solo `accion` en la UI alcanza, aunque la API acepta varios.
  - **Lista**: agrupada por día local con `h2`, y cada evento es un `li` con el actor, el verbo, el contexto y una tabla mínima de cambios (`dl`).
  - "Cargar más" con el cursor. El foco va al primer evento agregado.
- **`features/auditoria/historial-turno.ts`**: la tarjeta "Historial" de `features/agenda/detalle.ts`. Se carga en paralelo con el detalle, y si falla no rompe el resto de la página (C-13).
- **Sin conexión**: el registro no se cachea en el Service Worker, porque tiene datos personales y no hace falta offline (08). La sección muestra el aviso estándar de "sin conexión" del app-shell.

## Risks / Trade-offs

- **[C-12 sin propuesta]** → D2 depende de su puerto de lectura del turno. Si no lo expone, este change agrega uno mínimo (`existeTurno(consultorio, id)`) en `agenda.application`.
- **[El contexto muestra el turno de hoy, no el de ese momento]** → puede confundir ("Turno del 12/11 11:00" en un evento que lo movía de 10:00 a 11:00). Lo mitiga el "antes → después" de la misma fila.
- **[El catálogo del frontend se desfasa]** → lo cubre el test de D6, y el fallback muestra el código.
- **[La clave del cursor]** → si cambia el secreto, los cursores en vuelo dan 400. El cliente vuelve a la primera página. Es aceptable.

## Migration Plan

- Sin migraciones, salvo el índice opcional de D4 si la medición lo pide.
- Rollback = no mergear.

## Open Questions

Para la revisión de Maxi:

1. **Orden del registro**: propongo del más nuevo al más viejo, como cualquier registro de actividad. El historial de un turno va del más viejo al más nuevo, como dice US-040. CHANGES.md dice "orden cronológico" para los dos. ¿Se confirma?
2. **Eventos de acceso en el registro** (`LOGIN_EXITOSO` y `LOGIN_FALLIDO`): ¿se muestran junto con el resto (propuesta, con su grupo "Accesos" en el filtro) o se ocultan por defecto?
3. **IP visible solo en los accesos** (D6): ¿se confirma o se muestra en todos los eventos?
4. **Contexto del turno con su estado actual** (D3): ¿alcanza, o hace falta el horario de ese momento? Este último requiere reconstruirlo desde los eventos.

## UI Mockups

- Design system revision: 1
- Decision: yes (2026-11-20). Dos pantallas: `/app/auditoria` y la tarjeta "Historial" del detalle del turno.
- Mockups: [`mockups/registro.html`](mockups/registro.html) y [`mockups/turno-historial.html`](mockups/turno-historial.html), con su [README](mockups/README.md).
- Approval: pending
- Preview: rendered (2026-11-20), 24 capturas en [`mockups/previews/`](mockups/previews/) a 390x844 y 1440x900, sin desborde horizontal en 360, 390 y 1440 px y sin errores de consola.
- Visual verify: pendiente (tarea 6.1).
