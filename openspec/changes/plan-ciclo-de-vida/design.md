# Design

## Context

- Ver `proposal.md` §Why. Governance **CRITICO**: no se escribe código de producción hasta que Maxi apruebe esta propuesta y su mockup. Tiene pantallas: el modo es `system-and-mockups` (design system rev. 1 aprobado).
- **Lo que ya existe**:
  - `consultorio.plan VARCHAR(10) CHECK IN ('PRUEBA','ACTIVO','VENCIDO')` y `prueba_hasta DATE`, desde C-01. El registro (C-04) los deja en `PRUEBA` y hoy + 30 días (`NuevoConsultorio.DIAS_DE_PRUEBA`).
  - `GET /api/v1/consultorio` (C-08) ya devuelve `plan` y `pruebaHasta`. `EditarConfiguracion` no escribe `plan` en su `UPDATE`, así que una edición concurrente no puede deshacer un vencimiento.
  - C-15 (#47): `ConsultorioPublico.tomaTurnos` mira `plan = 'VENCIDO'`. Reservar, ver huecos y reprogramar por token responden 409 `RESERVA_NO_DISPONIBLE`, y la cancelación por token sigue.
  - C-15 registra filtros propios después de la cadena de Spring Security con `FilterRegistrationBean` (`ConfiguracionDeApiPublica`, órdenes `DEFAULT_FILTER_ORDER + 1` y `+ 2`).
  - C-19 (propuesta #32): `POST /api/v1/exportacion` no mira el plan y le pide a C-20 excluirlo de forma explícita.
  - No hay `@EnableScheduling` en el backend.
- **Restricciones**: `Clock` inyectado (regla ArchUnit `horaSoloDesdeElClock`). Se audita solo por `PublicadorDeAuditoria`, que exige una transacción activa. `TenantContext` tiene alcance de petición: la tarea y la operación interna reciben el consultorio por parámetro.

## Goals / Non-Goals

**Goals:**
- Una sola regla de "vencido", que usan la guarda, la consulta del plan y la reserva pública.
- La guarda de escrituras es genérica (por método y ruta), con una lista corta y explícita de excepciones.
- El vencimiento y la activación quedan auditados, son idempotentes y no tienen superficie HTTP.

**Non-Goals:**
- Cobro, facturación o vencimiento de un plan `ACTIVO` (RN-PL-04: no hay cobro en v1).
- Email de aviso antes de que venza la prueba (no está en US-061).
- Las pantallas de C-09, C-13 y C-14: C-20 expone el estado del plan y deshabilita las acciones que ya existan cuando se implemente.

## Decisions

### D1 — Estado efectivo: la fecha decide, la tarea persiste
- `consultorio.domain.EstadoDelPlan` es puro: recibe `plan`, `pruebaHasta` y `hoy` (la fecha local del consultorio, con su `zona_horaria` y el `Clock`).
  - **Vencido**: `plan = VENCIDO`, o `plan = PRUEBA` y `hoy > pruebaHasta`. `prueba_hasta` es el último día de prueba (FL-07: "`prueba_hasta < hoy`").
  - **Días restantes**: `pruebaHasta − hoy + 1` en `PRUEBA` sin vencer (5 el día que faltan 4 días más el de hoy); `null` en otro caso.
- **Por qué no depender solo de la tarea**: si el servidor está caído a las 03:00 o la tarea falla, el consultorio seguiría escribiendo hasta la próxima corrida. Con la fecha, el bloqueo empieza a las 00:00 del día siguiente en la zona del consultorio, haya corrido o no la tarea. La tarea sigue siendo la que cambia `plan` y deja el evento `PLAN_VENCIDO` (RN-AD).
- *Alternativa*: mirar solo `plan = VENCIDO`. Es más simple, pero el momento del bloqueo depende de una tarea programada. Descartada: el estado efectivo fue aprobado por Maxi en #53 (pregunta 1).

### D2 — Tarea diaria de vencimiento (FL-07)
- `consultorio.application.VencerPruebas.vencer(Instant ahora)`, `@Transactional`:
  ```sql
  UPDATE consultorio SET plan = 'VENCIDO', updated_at = now()
   WHERE plan = 'PRUEBA'
     AND prueba_hasta < (CAST(:ahora AS timestamptz) AT TIME ZONE zona_horaria)::date
  RETURNING id
  ```
  Por cada id publica `PLAN_VENCIDO` (`entidad = consultorio`, actor `SISTEMA`, anterior `{plan: PRUEBA}`, nuevo `{plan: VENCIDO}`) en la misma transacción.
- **Idempotencia**: el `WHERE plan = 'PRUEBA'` hace que una segunda corrida no encuentre filas. Si dos corren a la vez, la segunda espera el lock de fila y, en `READ COMMITTED`, vuelve a evaluar el `WHERE` y no la devuelve: un solo evento por consultorio.
- **No sube `version`**: `version` es el optimistic locking de la edición de la configuración, y el plan no es parte de ella.
- **Programación**: `consultorio.infrastructure.TareaDeVencimiento` con `@Scheduled(cron = "${app.plan.vencimiento.cron:0 0 3 * * *}", zone = "America/Argentina/Buenos_Aires")` y `@EnableScheduling` en una configuración propia. En el perfil `test` el cron es `-` (desactivado): los tests llaman a `VencerPruebas` con el `RelojDePrueba`.
- *Alternativa*: un job por consultorio a la medianoche de su zona. Es más preciso, pero D1 ya cubre el bloqueo puntual.

### D3 — Pase manual a ACTIVO, sin HTTP
- `consultorio.application.ActivarPlan.activar(ConsultorioId)`: `UPDATE … SET plan = 'ACTIVO' WHERE id = :id AND plan <> 'ACTIVO' RETURNING plan_anterior`. Publica `PLAN_ACTIVADO` (actor `SISTEMA`, anterior `{plan}`, nuevo `{plan: ACTIVO}`). Ya activo → sin cambios ni evento. Inexistente → falla.
- **Cómo lo corre el equipo**: un `ApplicationRunner` que solo existe con el perfil `operacion` (`consultorio.infrastructure.OperacionActivarPlan`). Se lanza con `--spring.profiles.active=operacion --operacion.consultorio=<uuid>` sobre la misma imagen, sin servidor web (`spring.main.web-application-type=none`), y termina. El runbook va al README del backend.
- **Por qué no un endpoint interno**: es la única forma de cambiar el plan. Un endpoint protegido por un secreto es superficie de ataque nueva en un dominio CRITICO, y el equipo ya tiene acceso al servidor.
- *Alternativa*: SQL a mano con el `INSERT` del evento. Es frágil, y no pasa por el registro de auditoría ni por sus reglas.

### D4 — Guarda de escrituras (RN-PL-03, RN-GL-04)
- `consultorio.infrastructure.FiltroDePlanVencido` (`OncePerRequestFilter`), registrado con `FilterRegistrationBean` en `/api/v1/*`, con orden `SecurityProperties.DEFAULT_FILTER_ORDER + 3`. Corre después de la autenticación y del `FiltroDeConsultorioDeSesion`, como los filtros de C-15. Sin sesión, Spring Security ya respondió 401.
- **Pasa sin mirar el plan**:
  - los métodos `GET`, `HEAD` y `OPTIONS`;
  - `/api/v1/public/**`, que gobierna C-15;
  - `/api/v1/auth/**`: login, refresh, logout, registro, recuperación y `cambiar-password`;
  - `POST /api/v1/exportacion` (C-19, RN-EX-03);
  - `POST /api/v1/turnos/{id}/visto` (DD-39);
  - toda petición sin tenant resuelto.
- **El resto**: lee el plan con `consultorio.application.ConsultarPlan` (una consulta por escritura, por la clave primaria) y, si está vencido (D1), responde 403 con `ManejadorGlobalDeErrores.problemaPara(FORBIDDEN, "PLAN_VENCIDO", "El plan del consultorio está vencido: la agenda está en solo lectura", ruta)`. El controlador no llega a ejecutarse: no hay cambio ni evento.
- **Por qué un filtro y no una anotación por endpoint**: el CHANGES pide que alcance a los endpoints que se agreguen después. Con una anotación, olvidarla deja la escritura abierta; con el filtro, lo nuevo nace bloqueado y lo permitido se lista. Un test de matriz recorre las escrituras registradas en `RequestMappingHandlerMapping` y verifica que cada una responde 403 salvo las permitidas, así no se olvida ninguna.
- **Por qué 403 y no 409**: no es un conflicto con el estado del recurso, sino una acción no autorizada para la cuenta en su estado actual. Así lo fija el CHANGES.
- *Alternativa*: chequear dentro de cada caso de uso, bajo el `LockDeAgenda`. Es más preciso frente a la carrera (ver Risks), pero obliga a tocar cada caso de uso y a cada change futuro.

### D5 — `GET /api/v1/plan`
- `consultorio.web.ControladorDePlan` → `{plan, pruebaHasta, diasDePruebaRestantes, soloLectura}`. `plan` es el **efectivo**: `VENCIDO` si D1 lo da por vencido aunque la tarea no corrió. Es un `GET`, así que la guarda no lo bloquea.
- **Por qué un endpoint aparte y no sumarlo a `GET /consultorio`**: el shell lo pide en cada arranque y después de un 403. `GET /consultorio` trae toda la configuración y su `version`, y lo usa la pantalla de C-09. Los días se calculan en el backend (RN-GL-04), con la zona del consultorio.

### D6 — Reserva pública con el estado efectivo
- `ConsultorioPublico.tomaTurnos` pasa a usar `EstadoDelPlan` (D1) en vez de comparar el texto `VENCIDO`. El resto del comportamiento de C-15 no cambia: los specs `reserva-publica` y `gestion-turno-paciente` solo suman el escenario de la prueba vencida antes de la tarea.

### D7 — Frontend
- **`features/plan/`**:
  - `estado-del-plan.ts`: pide `GET /api/v1/plan` al montar el shell, guarda el estado en memoria y avisa a quien se suscriba.
  - `aviso-de-plan.ts`: el aviso del shell, debajo del encabezado y arriba del contenido.
- **Aviso**:
  - En `PRUEBA` con 8 días o más: discreto.
  - Con 7 o menos: destacado.
  - El último día: "Tu prueba termina hoy".
  - En `VENCIDO`: persistente, con "Exportar mis datos" y cómo pedir el pase a `ACTIVO`.
  - Con `role="status"`, sin robar el foco.
- **Cliente API (C-06)**: un 403 con `codigo = PLAN_VENCIDO` muestra el mensaje propio y vuelve a pedir el plan, así el shell pasa a solo lectura sin recargar. No se reintenta.
- **Acciones deshabilitadas**: el estado expone `soloLectura()`. Las pantallas que existan al implementar (C-09; C-13 y C-14 si ya están) deshabilitan sus botones de escritura con un motivo visible ("Solo lectura: tu prueba terminó"), no solo con color. Se coordina con Nacho en la tarea 6.3.
- **Mockup**: `mockups/` con los estados `prueba`, `prueba-ultimos-dias`, `ultimo-dia`, `vencido` y `error-403`, a 390×844 y 1440×900, con los tokens de rev. 1.

### D8 — Errores y auditoría
- **Código nuevo**: 403 `PLAN_VENCIDO`. Se nombra en RN-PL-03 (`05_reglas_de_negocio.md`) y llega al `api-types` por el contrato.
- **Acciones nuevas**: `PLAN_VENCIDO` y `PLAN_ACTIVADO`, con `entidad = consultorio` y actor `SISTEMA` sin id. Los valores son solo `{plan}`: sin datos personales (RN-AD-05).

## Risks / Trade-offs

- **[Carrera con la guarda]**: una escritura que pasó la guarda a las 23:59:59.9 termina después de la medianoche. Es aceptable: una escritura de más en el borde del día no cambia el modelo de negocio, y la siguiente ya responde 403.
- **[Una consulta más por escritura]**: es una lectura por la clave primaria. Si pesara, se puede cachear unos segundos por consultorio, porque D1 no depende de la tarea.
- **[Lista de permitidas]**: un endpoint nuevo que debería estar permitido (por ejemplo, otra lectura hecha con `POST`) queda bloqueado hasta sumarlo. Es la dirección segura del error, y el test de matriz lo hace visible.
- **[Operación por perfil]**: depende de que el equipo tenga acceso al servidor. Lo documenta el runbook, y C-23 lo incluye en el checklist de lanzamiento.
- **[Pantallas de otros changes]**: si C-13 o C-14 se mergean después de C-20, tienen que leer `soloLectura()`. La guarda del backend igual las protege (RN-GL-04), y se le avisa a Nacho.

## Migration Plan

- Sin migración: las columnas existen. El cambio de `ConsultorioPublico` (D6) solo bloquea antes, en el borde de la fecha. El rollback es no mergear.

## Open Questions

Todas aprobadas por Maxi en #53:
1. **Estado efectivo (D1)**: sí. El bloqueo arranca a las 00:00 del día siguiente a `prueba_hasta`, en la zona del consultorio, haya corrido o no la tarea.
2. **Contacto para activar el plan**: `(TBD)` en una constante única hasta que se resuelva DD-34.
3. **`ACTIVO` → `VENCIDO` manual**: fuera de la v1.
4. **Aviso durante la prueba**: discreto durante toda la prueba y destacado en los últimos 7 días, como en el mockup.
5. **Colores**: `warn` para el vencido y `danger` solo para el 403 de una escritura que no se guardó.

Pautas de Maxi para la guarda (D4), aprobadas en #53:
- Lista de permitidas por método + ruta completa, anclada al inicio y al final (por ejemplo, `POST ^/api/v1/turnos/[^/]+/visto$`). Sin `startsWith` ni `contains`, salvo `/api/v1/public/` y `/api/v1/auth/`, que son prefijos a propósito.
- Se compara contra la ruta normalizada sin el context path, la misma que usa Spring para resolver el controlador.
- Tests de evasión en la matriz: barra final (`/visto/`), `;` (`/estado;/visto`), `//`, mayúsculas en la ruta y caracteres codificados (`%2F`, `%2e`). Todas dan 403 o no llegan a ningún controlador de escritura.

## UI Mockups

- Design system revision: 1
- Decision: yes (2026-11-21)
- Mockups: `openspec/changes/plan-ciclo-de-vida/mockups/`
- Approval: approved (2026-11-21, by Maxi), versión 1a8340b (#53)
- Preview: rendered (2026-11-21, `mockups/previews/`: cinco estados a 390×844 y 1440×900)
- Visual verify: pending (tarea 6.1)
