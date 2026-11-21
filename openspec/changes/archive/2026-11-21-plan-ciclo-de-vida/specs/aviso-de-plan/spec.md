# Spec Delta

## Purpose

Define cómo la app le muestra al odontólogo el estado de su plan: los días de prueba que quedan y el modo de solo lectura con el plan vencido (US-061, RN-PL-03, RN-EX-03).

## ADDED Requirements

### Requirement: Días de prueba restantes
Con el plan en `PRUEBA`, el shell de la app SHALL mostrar en todas las pantallas privadas los días de prueba que quedan, con el valor que da el backend. Con 7 días o menos el aviso MUST destacarse, y el último día MUST decir que la prueba termina hoy. Con el plan `ACTIVO` MUST NOT mostrarse.

#### Scenario: Cinco días de prueba
- **GIVEN** un consultorio en `PRUEBA` con 5 días restantes
- **WHEN** el odontólogo entra a la app
- **THEN** ve un aviso destacado "Te quedan 5 días de prueba"

#### Scenario: Último día
- **GIVEN** un consultorio en `PRUEBA` con 1 día restante
- **WHEN** el odontólogo entra a la app
- **THEN** el aviso dice que la prueba termina hoy

#### Scenario: Plan activo
- **GIVEN** un consultorio en `ACTIVO`
- **WHEN** el odontólogo entra a la app
- **THEN** no ve ningún aviso de plan

### Requirement: Modo de solo lectura
Con el plan vencido, la app SHALL mostrar un aviso persistente que explica que la agenda quedó en solo lectura, que los datos no se borran, que puede exportarlos y cómo pedir el pase a `ACTIVO`. Las acciones de escritura MUST mostrarse deshabilitadas, sin depender solo del color, y la exportación MUST seguir disponible.

#### Scenario: Entrar con el plan vencido
- **GIVEN** un consultorio vencido
- **WHEN** el odontólogo abre su agenda
- **THEN** ve sus turnos, el aviso de solo lectura con el acceso a exportar, y las acciones de crear o modificar deshabilitadas

### Requirement: Respuesta 403 PLAN_VENCIDO
Si una escritura responde 403 `PLAN_VENCIDO` (por ejemplo, porque la prueba venció con la app abierta), el cliente MUST mostrar un mensaje propio en español rioplatense, sin el `detail` del servidor, y pasar la app al modo de solo lectura sin recargar la página.

#### Scenario: La prueba vence con la app abierta
- **GIVEN** la app abierta en `PRUEBA` y la prueba vence
- **WHEN** el odontólogo intenta guardar un bloqueo y recibe 403 `PLAN_VENCIDO`
- **THEN** ve el mensaje de plan vencido y la app pasa al modo de solo lectura

### Requirement: Aviso accesible
El aviso de plan MUST cumplir AA: contraste, foco visible, objetivos táctiles de al menos 44 px, el estado dicho con texto y no solo con color, y anunciado a los lectores de pantalla sin robar el foco.

#### Scenario: Lector de pantalla
- **GIVEN** el modo de solo lectura
- **WHEN** un lector de pantalla recorre la página
- **THEN** anuncia el aviso de solo lectura como texto, una vez
