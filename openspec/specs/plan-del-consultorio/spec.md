# plan-del-consultorio Specification

## Purpose
Define el ciclo de vida del plan del consultorio: cuándo una prueba está vencida, cómo pasa a `VENCIDO`, cómo pasa a `ACTIVO` y cómo lo consulta la app (RN-PL-02 a RN-PL-04, US-061, FL-07).

## Requirements

### Requirement: Prueba vencida
Un consultorio SHALL considerarse vencido si su plan es `VENCIDO`, o si su plan es `PRUEBA` y la fecha actual en la zona horaria del consultorio es posterior a `prueba_hasta`. El día `prueba_hasta` MUST seguir siendo de prueba. Un consultorio en `ACTIVO` MUST NOT considerarse vencido.

#### Scenario: Último día de prueba
- **GIVEN** un consultorio en `PRUEBA` con `prueba_hasta` = 2026-12-20, en Buenos Aires
- **WHEN** son las 23:59 del 2026-12-20 en Buenos Aires
- **THEN** el consultorio no está vencido

#### Scenario: Prueba vencida antes de que corra la tarea
- **GIVEN** un consultorio en `PRUEBA` con `prueba_hasta` = 2026-12-20, en Buenos Aires
- **WHEN** son las 00:01 del 2026-12-21 en Buenos Aires y la tarea diaria todavía no corrió
- **THEN** el consultorio está vencido

### Requirement: Vencimiento diario de las pruebas
Una tarea del sistema SHALL correr todos los días a las 03:00 de Buenos Aires y pasar a `VENCIDO` cada consultorio en `PRUEBA` con la prueba vencida, registrando por cada uno un evento `PLAN_VENCIDO` con actor `SISTEMA`, con el plan anterior y el nuevo, en la misma transacción. La tarea MUST ser idempotente: correrla de nuevo, o dos veces a la vez, MUST NOT generar eventos duplicados. Ningún dato del consultorio MUST borrarse (RN-PL-03).

#### Scenario: Prueba vencida
- **GIVEN** un consultorio en `PRUEBA` con `prueba_hasta` de ayer
- **WHEN** corre la tarea diaria
- **THEN** su plan queda `VENCIDO` y hay un evento `PLAN_VENCIDO` con actor `SISTEMA`

#### Scenario: Tarea repetida
- **GIVEN** un consultorio que la tarea ya pasó a `VENCIDO`
- **WHEN** la tarea corre otra vez
- **THEN** sigue `VENCIDO` y sigue habiendo un solo evento `PLAN_VENCIDO`

#### Scenario: Prueba vigente y plan activo
- **GIVEN** un consultorio en `PRUEBA` con `prueba_hasta` de hoy y otro en `ACTIVO` con `prueba_hasta` del mes pasado
- **WHEN** corre la tarea diaria
- **THEN** ninguno cambia de plan ni registra eventos

### Requirement: Pase manual a ACTIVO
El equipo SHALL poder pasar un consultorio en `PRUEBA` o `VENCIDO` a `ACTIVO` con una operación interna, sin endpoint HTTP, que registra `PLAN_ACTIVADO` con actor `SISTEMA` en la misma transacción. Activar un consultorio que ya está en `ACTIVO` MUST NOT registrar otro evento. Un consultorio inexistente MUST hacer fallar la operación sin cambios.

#### Scenario: Activar un consultorio vencido
- **GIVEN** un consultorio en `VENCIDO`
- **WHEN** el equipo lo activa
- **THEN** su plan queda `ACTIVO`, hay un evento `PLAN_ACTIVADO` con actor `SISTEMA` y vuelve a aceptar escrituras

#### Scenario: Ninguna ruta HTTP activa el plan
- **WHEN** alguien pide por HTTP cambiar el plan de un consultorio, con o sin sesión
- **THEN** no existe una ruta que lo haga, y la configuración ignora `plan` y `pruebaHasta` si vienen en el cuerpo

### Requirement: Consulta del plan
El odontólogo SHALL obtener con `GET /api/v1/plan` el plan de su consultorio, `pruebaHasta`, los días de prueba restantes y si la app está en solo lectura. Los días restantes MUST contarse en la zona horaria del consultorio, con el día `prueba_hasta` incluido, y MUST ser `null` fuera de `PRUEBA`. La consulta MUST responder también con el plan vencido.

#### Scenario: Cinco días de prueba
- **GIVEN** un consultorio en `PRUEBA` cuyo último día de prueba es dentro de 4 días
- **WHEN** el odontólogo consulta el plan
- **THEN** la respuesta es 200 con `plan: PRUEBA`, `diasDePruebaRestantes: 5` y `soloLectura: false`

#### Scenario: Plan vencido
- **GIVEN** un consultorio con la prueba vencida
- **WHEN** el odontólogo consulta el plan
- **THEN** la respuesta es 200 con `plan: VENCIDO`, `diasDePruebaRestantes: null` y `soloLectura: true`, aunque la tarea todavía no haya corrido

#### Scenario: Plan de otro consultorio
- **WHEN** el odontólogo del consultorio A consulta el plan
- **THEN** la respuesta trae solo el plan de A
