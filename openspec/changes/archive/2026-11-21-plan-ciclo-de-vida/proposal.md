# Proposal

## Why

Todo consultorio nuevo arranca en `PRUEBA` por 30 días sin tarjeta (RN-PL-02). Hoy nada hace que esa prueba termine: `prueba_hasta` se guarda al registrarse, pero ningún proceso pasa el plan a `VENCIDO` y ninguna escritura privada mira el plan. Tampoco hay forma de pasar a `ACTIVO` (RN-PL-04). Sin este change la v1 no tiene modelo de negocio: la prueba es gratis para siempre. C-20 cierra el ciclo con las reglas RN-PL-02 a RN-PL-04, RN-RP-11 y RN-EX-03, y con US-061 y FL-07.

Governance **CRITICO** (equivale a Billing: bloquea escrituras en toda la agenda). Esta propuesta y su mockup se aprueban antes de escribir código.

## What Changes

- **Estado efectivo del plan** (regla única, en `consultorio.domain`): un consultorio está vencido si su plan es `VENCIDO` **o** si está en `PRUEBA` y la fecha local del consultorio ya pasó `prueba_hasta`. El último día de prueba es `prueba_hasta` inclusive. Así la tarea diaria persiste y audita el pase, pero el bloqueo no depende de que la tarea haya corrido.
- **Tarea diaria de vencimiento** (FL-07): a las 03:00 de Buenos Aires pasa a `VENCIDO` los consultorios en `PRUEBA` con la prueba vencida. Audita `PLAN_VENCIDO` con `actor_tipo = SISTEMA` en la misma transacción. Es idempotente: correrla dos veces, o dos a la vez, no duplica eventos.
- **Pase a `ACTIVO` manual** (RN-PL-04): operación interna del equipo, sin endpoint HTTP, auditada como `PLAN_ACTIVADO` con `actor_tipo = SISTEMA`. Vale desde `PRUEBA` o desde `VENCIDO`.
- **Solo lectura con el plan vencido** (RN-PL-03, RN-GL-04): una guarda transversal en el backend rechaza con **403 `PLAN_VENCIDO`** toda escritura privada (`POST`, `PUT`, `PATCH`, `DELETE` bajo `/api/v1/`) salvo una lista explícita de permitidas:
  - sesión: login, refresh, logout y cambio de contraseña;
  - recuperación de contraseña;
  - `POST /api/v1/exportacion` (C-19, RN-EX-03);
  - `POST /api/v1/turnos/{id}/visto` (marca de lectura, DD-39).

  Como es genérica, alcanza a los endpoints que se agreguen después. Las lecturas siguen.
- **Consulta del plan**: `GET /api/v1/plan` devuelve el plan, `pruebaHasta`, los días de prueba restantes y si la app está en solo lectura. Los días se calculan en el backend (RN-GL-04).
- **Reserva pública** (RN-RP-11): C-15 ya deja de tomar reservas y de reprogramar con `VENCIDO`, y mantiene la cancelación por link. C-20 usa el estado efectivo también ahí y lo fija con tests.
- **Aviso en la app** (US-061): el shell muestra los días de prueba que quedan y, con el plan vencido, un aviso de solo lectura que ofrece exportar los datos. El cliente API traduce el 403 `PLAN_VENCIDO` a un mensaje propio. Lleva **mockup aprobado antes de implementar**.

## Capabilities

### New Capabilities
- `plan-del-consultorio`: estado efectivo del plan, vencimiento diario, pase manual a `ACTIVO` y la consulta `GET /api/v1/plan`.
- `solo-lectura-por-plan`: la guarda que bloquea las escrituras privadas con el plan vencido y su lista de permitidas.
- `aviso-de-plan`: el aviso de días de prueba y de solo lectura en la app, y el manejo del 403 `PLAN_VENCIDO` en el cliente.

### Modified Capabilities
- `reserva-publica`: "Consultorio que no toma turnos online" cuenta también la prueba vencida que la tarea todavía no pasó a `VENCIDO`.
- `gestion-turno-paciente`: la reprogramación deshabilitada y la cancelación por link suman el escenario del plan vencido.

## Impact

- **Backend**:
  - `consultorio.domain`: `EstadoDelPlan`.
  - `consultorio.application`: `VencerPruebas`, `ActivarPlan` y `ConsultarPlan`.
  - `consultorio.infrastructure`: la tarea `@Scheduled`.
  - La guarda en la cadena de seguridad (`auth.infrastructure.seguridad`), después de `FiltroDeConsultorioDeSesion`.
  - `reserva.application.ConsultorioPublico` pasa a usar el estado efectivo.
  - Sin migración: `consultorio.plan` y `prueba_hasta` existen desde C-01.
- **Frontend**: el aviso en el shell (C-06), el mensaje del 403 en el cliente API y `api-types.ts` regenerado. Las pantallas de C-09, C-13 y C-14 deshabilitan sus acciones con el estado del plan. C-20 expone ese estado y coordina con Nacho.
- **Operación**: el pase a `ACTIVO` es un comando que corre el equipo, con un runbook en el README del backend.
- **Dependencias**: C-09 (Nacho, pendiente), C-12 y C-15 (completos). No se implementa hasta que C-09 esté `[x]` y Maxi apruebe esta propuesta y el mockup.
