# Design

## Context

- Ver `proposal.md` para la motivación. C-17 resuelve las revisiones que deja la deduplicación por DNI (US-070, RN-RP-03, DD-24): lista con filtro, revisión que compara lo guardado con lo escrito en la reserva (DD-38), y confirmar o corregir.
- **Lo que ya existe en `main`**: el `TenantContext` y la regla ArchUnit de repositorios con `ConsultorioId` (C-02), el puerto `PublicadorDeAuditoria` con `CambioAuditado` (C-03), el manejador global de errores con Problem Details (C-02), y el shell, el router y el cliente HTTP del frontend (C-06). El Service Worker solo cachea `GET /api/v1/agenda`, así que `/api/v1/pacientes` nunca queda guardado en el dispositivo.
- **Lo que dejaron C-10 y C-11 en `main`**:
  - C-10 (#18): tabla `paciente` con `requiere_revision` y, por DD-38, `nombre_informado`, `apellido_informado`, `email_informado`, `informado_at` e `informado_turno_id` en la `V6`, con `ck_paciente_informado` (las cinco en `NULL` sin revisión pendiente). En el dominio, `DatosInformados` y `Paciente.datosDifierenDe`.
  - C-11 (#46): `RegistrarPaciente` marca para revisión al deduplicar, guarda lo informado (pisa lo de una reserva anterior, así que queda la última) y publica `PACIENTE_MARCADO_PARA_REVISION`. C-15 usa ese caso de uso. Toda escritura de agenda y de pacientes toma primero el `LockDeAgenda`.
- **Lo que viene de otros changes**: C-13, la agenda donde va el aviso. C-07 también toca la navegación del shell.
- Base visual: sistema de diseño rev. 1 y mockups aprobados (#36, versión `dcb700b`).

## Goals / Non-Goals

**Goals:**

- Que el odontólogo resuelva una revisión en un toque, viendo exactamente qué difiere.
- Que la ficha no cambie hasta que él elija, y que lo informado no quede guardado después de revisar.

**Non-Goals:**

- Buscar pacientes, editarlos fuera de una revisión, editar DNI o teléfono, borrar pacientes.
- Ver el historial de variantes que escribieron los pacientes: queda solo la última (DD-38).

## Decisions

Las decisiones de UI están en `mockups/README.md` §Decisiones, aprobadas por Maxi en la revisión de #36 (2026-11-20). La que excede la UI quedó registrada:

- **Guardar lo informado en la reserva** ([DD-38](../../../knowledge-base/09_decisiones_y_supuestos.md)): la deduplicación guarda nombre, apellido y email escritos en la reserva que disparó la marca, más el instante y el turno de origen, y C-17 los borra al revisar. Columnas en la `V6` de C-10; las llena `RegistrarPaciente` de C-11, que también usa C-15.

Decisiones de implementación:

### D1 — Backend en el módulo `agenda`

El paciente vive en `agenda` (`08_arquitectura_propuesta.md`), así que C-17 suma ahí un controlador `web` con los cuatro endpoints, un caso de uso de aplicación para revisar (confirmar y corregir) y una consulta de pacientes paginada, sobre el repositorio de pacientes de C-10. Todas las consultas reciben el `ConsultorioId` del `TenantContext`; el id del body o de la query nunca define el consultorio.

- **Alternativa descartada**: un módulo `pacientes` nuevo. Partiría un agregado que C-10 y C-11 ya ubican en `agenda`.

### D2 — "Usar lo de la reserva" es un `PATCH` con lo informado

No hay un endpoint aparte: el frontend manda en el `PATCH` el nombre, el apellido y el email informados que recibió en el detalle. Así hay una sola forma de corregir, con la misma validación y la misma auditoría.

- **Trade-off**: el `PATCH` no distingue "usar lo de la reserva" de "corregir a mano"; en la auditoría los dos se ven como una corrección. A cambio, no se aplica nada que el odontólogo no haya visto: si entre que abrió la revisión y tocó "Usar «…»" llegó otra reserva con otro nombre, el `PATCH` responde 409 `CONFLICTO_CONCURRENCIA` (D9) y la pantalla le muestra la variante nueva para que elija de nuevo.

### D3 — El contador sale del total de la lista

La cantidad de pacientes para revisar se obtiene con `GET /api/v1/pacientes?requiereRevision=true&size=1`, leyendo `total`. El shell la pide al iniciar la sesión y después de cada revisión, y la comparte con la agenda (aviso) y la navegación (contador). Sin endpoint nuevo.

- **Alternativa descartada**: sumar el contador a la respuesta de la agenda (C-12). Acoplaría dos changes y la caché offline de la agenda guardaría un dato de pacientes.

### D4 — Paginado y orden estable

Parámetros `page` (desde 0) y `size` (20 por defecto, máximo 50), orden por apellido, nombre e id para que el paginado no repita ni saltee pacientes. La respuesta trae `items` y `total`. "Mostrar más pacientes" pide la página siguiente y la agrega.

### D5 — Código de error `SIN_REVISION_PENDIENTE`

Confirmar o corregir un paciente sin revisión pendiente responde 409 `SIN_REVISION_PENDIENTE` (tipo `CONFLICTO` de C-02). Cubre la doble confirmación y la revisión desde dos pestañas; la pantalla recarga el paciente y lo muestra revisado. Se evalúa antes que el `informadoAt` de D9: un paciente ya revisado responde `SIN_REVISION_PENDIENTE` aunque el `informadoAt` enviado no coincida.

### D6 — Auditoría con solo lo que cambió

`PACIENTE_REVISADO` se publica por `PublicadorDeAuditoria` en la misma transacción, con un `CambioAuditado` por campo que cambió (`nombre`, `apellido`, `email`) más `requiere_revision`. Lo informado no se audita: se borra (DD-38, RN-AD-05). Una confirmación audita solo `requiere_revision`.

### D7 — La comparación usa la misma regla que la deduplicación

La pantalla decide qué datos "difieren" con la misma regla que usa la deduplicación para marcar: `Paciente.datosDifierenDe` de C-10 (#18, `8cb7449`), que compara nombre y apellido sin distinguir mayúsculas, acentos (NFD sin diacríticos) ni espacios extra. El email se compara sin distinguir mayúsculas, porque el dominio lo guarda en minúsculas. Así nunca muestra "Coinciden todos los datos" en un paciente marcado por una diferencia que el backend sí vio, ni marca "Distinto" por una mayúscula o una tilde.

- El frontend replica esa regla en una función pura (`normalize('NFD')`, sin diacríticos, espacios colapsados y minúsculas), con los mismos casos de prueba que el test de dominio de `datosDifierenDe`.

### D8 — Navegación y rutas

`/app/pacientes` y `/app/pacientes/:id` se suman a la tabla de rutas del shell (`app.ts`). "Pacientes" entra en la barra lateral después de "Bloqueos" y en la hoja "Más" en mobile, con el contador de D3. Es el delta de la spec `app-shell`.

### D9 — La revisión se aplica sobre lo que se vio (`informadoAt`)

Mientras el odontólogo mira la revisión puede llegar otra reserva con el mismo DNI y otro nombre. `RegistrarPaciente` (C-11) pisa lo informado y deja la marca, así que, sin control, "Mantener" o "Usar «…»" borrarían una variante que nadie vio. Por eso:

1. El `PATCH` y la confirmación reciben en el cuerpo el `informadoAt` del detalle que se mostró (obligatorio; si falta, 400 `VALIDACION`).
2. El caso de uso toma primero el `LockDeAgenda` de C-11, como toda escritura de pacientes, y recién después lee el paciente. Así queda en la misma cola que `RegistrarPaciente`, y una reserva no puede colarse entre la comparación y la escritura.
3. Si el paciente no tiene revisión pendiente → 409 `SIN_REVISION_PENDIENTE` (D5). Si tiene, y el `informadoAt` enviado no es igual al guardado → 409 `CONFLICTO_CONCURRENCIA`, sin cambiar nada ni auditar. El caso de uso lanza la misma `OptimisticLockingFailureException` que ya traduce el manejador global de C-02, así que no hace falta otro código de error.
4. La pantalla, ante `CONFLICTO_CONCURRENCIA`, recarga el paciente y muestra la comparación con lo nuevo, con un aviso de que llegó otra reserva con datos distintos. En el formulario de "Corregir a mano" no se pierde lo escrito.

- **Alternativa descartada**: una columna `version` en `paciente` con `@Version`. Necesitaría una migración nueva y detectaría también cambios que no afectan la revisión. `informado_at` ya identifica la variante que se vio y cambia con cada reserva que pisa lo informado.
- **Alternativa descartada**: aceptar lo que vio el odontólogo y perder la variante nueva (el trade-off original de D2). La variante nueva puede ser justamente el error que había que ver.

## Risks / Trade-offs

- **[Precisión de `informadoAt`]** → `RegistrarPaciente` lo toma de `reloj.instant()` (nanosegundos) y Postgres lo guarda con microsegundos. La comparación de D9 se hace siempre contra el valor leído de la base, y el detalle devuelve ese mismo valor, así que el redondeo no da falsos conflictos. Las marcas del mismo consultorio se serializan con el `LockDeAgenda`, así que en la práctica cada reserva deja un valor distinto.
- **[Conflicto con C-07 en la navegación]** → los dos modifican "Navegación adaptada al ancho" de `app-shell`. El que archive segundo integra el texto del otro.
- **[Plan `VENCIDO`]** → C-20 define el bloqueo de escrituras; los endpoints de escritura de C-17 lo respetan y C-23 lo verifica.

## Migration Plan

Sin migración propia: las columnas de DD-38 ya están en la `V6` de C-10. C-09, C-10 y C-11 ya están en `main`. Sin datos que migrar.

## Open Questions

- Ninguna. El texto de §C-11 en `CHANGES.md` (deduplicación por teléfono) lo corrige Fran en la propuesta de C-11.

## UI Mockups

- Design system revision: 1
- Decision: yes (2026-11-20)
- Mockups: openspec/changes/pacientes-revision/mockups/ (`agenda-aviso.html`, `pacientes.html`, `paciente.html`, más `base.css`, `agenda.css`, `pacientes.css` y `mockup.js`)
- Approval: approved (2026-11-20, by Maxi), versión dcb700b (#36)
- Preview: rendered (2026-11-20, segunda versión con la revisión de #36); `mockups/previews/*-mobile-390x844.png`, `mockups/previews/*-desktop-1440x900.png`
- Visual verify: pending
