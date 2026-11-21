# solo-lectura-por-plan Specification

## Purpose
Define qué puede hacer el odontólogo con el plan vencido: leer y exportar, pero no modificar su agenda, sus pacientes ni su configuración (RN-PL-03, RN-EX-03, RN-GL-04).

## Requirements

### Requirement: Escrituras bloqueadas con el plan vencido
Con el consultorio vencido, toda petición privada `POST`, `PUT`, `PATCH` o `DELETE` bajo `/api/v1/` MUST responder 403 con código `PLAN_VENCIDO` en Problem Details, sin aplicar ningún cambio ni registrar eventos, salvo las rutas permitidas. La regla MUST aplicarse también a los endpoints privados que se agreguen después, sin listarlos uno por uno.

#### Scenario: Crear un turno con el plan vencido
- **GIVEN** un consultorio vencido
- **WHEN** el odontólogo crea un turno
- **THEN** la respuesta es 403 `PLAN_VENCIDO` y no se crea el turno ni se registra un evento

#### Scenario: Matriz de escrituras bloqueadas
- **GIVEN** un consultorio vencido
- **WHEN** el odontólogo intenta cambiar el estado, reprogramar, ajustar la duración o editar la nota de un turno, crear o eliminar un bloqueo, editar la configuración, la disponibilidad o una prestación
- **THEN** cada pedido responde 403 `PLAN_VENCIDO` y nada cambia

#### Scenario: Prueba vencida que la tarea todavía no pasó
- **GIVEN** un consultorio en `PRUEBA` cuyo `prueba_hasta` fue ayer, antes de que corra la tarea diaria
- **WHEN** el odontólogo crea un bloqueo
- **THEN** la respuesta es 403 `PLAN_VENCIDO`

#### Scenario: Plan activo o prueba vigente
- **GIVEN** un consultorio en `ACTIVO` y otro en `PRUEBA` con la prueba vigente
- **WHEN** cada odontólogo crea un turno válido
- **THEN** las dos respuestas son 201

### Requirement: Rutas permitidas con el plan vencido
Con el consultorio vencido, las lecturas (`GET`) MUST seguir respondiendo igual, y MUST seguir permitidas estas escrituras: login, refresh, logout, cambio de contraseña, recuperación de contraseña, `POST /api/v1/exportacion` (RN-EX-03) y `POST /api/v1/turnos/{id}/visto`. Las rutas públicas de `/api/v1/public/` MUST NOT pasar por esta regla: las gobierna la reserva pública (RN-RP-11).

#### Scenario: Leer la agenda y exportar
- **GIVEN** un consultorio vencido
- **WHEN** el odontólogo pide su agenda y luego exporta sus datos
- **THEN** la agenda responde 200 y la exportación no responde `PLAN_VENCIDO`

#### Scenario: Sesión y contraseña
- **GIVEN** un consultorio vencido
- **WHEN** el odontólogo cierra sesión, vuelve a entrar y cambia su contraseña
- **THEN** ninguna de las respuestas es `PLAN_VENCIDO`

#### Scenario: Marcar una novedad como vista
- **GIVEN** un consultorio vencido con un turno que el paciente canceló por link
- **WHEN** el odontólogo marca la novedad como vista
- **THEN** la respuesta es 204

### Requirement: Error de plan vencido
El 403 `PLAN_VENCIDO` MUST tener un `detail` fijo, sin datos del consultorio ni del paciente, que el cliente pueda reemplazar por su propio texto. Una petición sin sesión válida MUST seguir respondiendo 401 antes de evaluar el plan.

#### Scenario: Sin sesión
- **GIVEN** un consultorio vencido
- **WHEN** llega una escritura privada sin token
- **THEN** la respuesta es 401, no 403
