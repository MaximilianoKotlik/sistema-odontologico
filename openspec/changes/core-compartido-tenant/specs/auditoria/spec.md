# Spec Delta

## Purpose

Define cómo los módulos de negocio publican eventos de auditoría (quién, qué entidad, qué acción, valor anterior y nuevo) dentro de la misma transacción del cambio, para que el registro append-only pueda persistirlos sin acoplarse a cada módulo (RN-AD-01, RN-AD-03, RN-AD-05).

## ADDED Requirements

### Requirement: Contenido de un evento de auditoría
Todo evento publicado MUST incluir consultorio, instante (del reloj del sistema, en UTC), tipo de actor (`ODONTOLOGO`, `PACIENTE`, `SISTEMA`), id de actor opcional, entidad, id de entidad, acción, valor anterior y valor nuevo opcionales, e IP opcional.

#### Scenario: Evento completo
- **WHEN** un caso de uso publica un evento con actor `ODONTOLOGO`, entidad `consultorio` y acción `CONSULTORIO_CONFIG_CAMBIADA`
- **THEN** el evento recibido por los suscriptores trae esos datos, el consultorio activo y el instante del reloj del sistema

#### Scenario: Evento sin consultorio
- **WHEN** se intenta publicar un evento sin consultorio o sin acción
- **THEN** la publicación falla antes de llegar a los suscriptores

### Requirement: Los eventos nunca contienen secretos
Un evento MUST NOT contener contraseñas, hashes ni tokens en sus valores anterior o nuevo; la publicación MUST fallar si alguna clave de esos valores es `password`, `password_hash`, `token` o termina en `_token` o `_hash`.

#### Scenario: Valor con contraseña
- **WHEN** se publica un evento cuyo valor nuevo contiene la clave `password`
- **THEN** la publicación falla y no llega a los suscriptores

### Requirement: Publicación dentro de la transacción del cambio
La publicación MUST ocurrir de forma síncrona dentro de la transacción del caso de uso, de modo que un suscriptor transaccional pueda persistir el evento junto con el cambio y que si la transacción se revierte el evento no quede registrado.

#### Scenario: Cambio revertido
- **GIVEN** un suscriptor que se ejecuta antes del commit
- **WHEN** el caso de uso publica un evento y luego su transacción se revierte
- **THEN** el suscriptor no confirma el evento

#### Scenario: Publicación fuera de transacción
- **WHEN** se publica un evento sin transacción activa
- **THEN** la publicación falla con un error interno
