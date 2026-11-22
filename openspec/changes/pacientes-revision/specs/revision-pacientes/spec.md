# Spec Delta

## Purpose

Permite que el odontólogo consulte los pacientes de su consultorio, encuentre los que la deduplicación por DNI marcó para revisión y resuelva cada uno confirmando o corrigiendo sus datos, con auditoría y sin conservar datos informados de más (US-070, RN-RP-03, DD-24, DD-38).

## ADDED Requirements

### Requirement: Lista de pacientes del consultorio

`GET /api/v1/pacientes` SHALL devolver, solo para el consultorio de la sesión, una página de pacientes ordenada por apellido y nombre, con el total de resultados. Cada paciente MUST incluir id, nombre, apellido, DNI, teléfono, email (si tiene) y `requiereRevision`. El parámetro `requiereRevision=true` MUST filtrar los marcados para revisión; sin el parámetro, MUST devolver todos.

#### Scenario: Filtrar los marcados

- **GIVEN** un consultorio con 6 pacientes, 3 marcados para revisión
- **WHEN** se pide `GET /api/v1/pacientes?requiereRevision=true`
- **THEN** la respuesta es 200 con esos 3 pacientes ordenados por apellido y `total: 3`

#### Scenario: Paginado

- **GIVEN** 30 pacientes
- **WHEN** se pide la primera página de 20
- **THEN** la respuesta trae 20 pacientes y `total: 30`

### Requirement: Lo informado en la reserva

Un paciente marcado para revisión MUST incluir, en la lista y en el detalle, lo que se escribió en la reserva que disparó la marca (nombre, apellido, email, instante y turno de origen), según DD-38. Un paciente sin revisión pendiente MUST NOT incluir esos datos.

#### Scenario: Paciente marcado

- **GIVEN** la paciente "Ana Ejemplo" marcada porque alguien reservó como "Anna Ejemplo" con su DNI
- **WHEN** se pide su detalle
- **THEN** la respuesta trae el nombre guardado "Ana" y el informado "Anna", con el instante y el turno de la reserva

#### Scenario: Paciente sin revisión

- **WHEN** se pide el detalle de un paciente sin revisión pendiente
- **THEN** la respuesta no trae datos informados

### Requirement: Detalle del paciente

`GET /api/v1/pacientes/{id}` SHALL devolver el paciente del consultorio de la sesión con los mismos datos que la lista. Un id inexistente o de otro consultorio MUST responder 404 `RECURSO_NO_ENCONTRADO` (RN-TE-04).

#### Scenario: Paciente de otro consultorio

- **GIVEN** un paciente del consultorio B
- **WHEN** el odontólogo del consultorio A pide su detalle
- **THEN** la respuesta es 404, igual que para un id inexistente

### Requirement: Confirmar la revisión

`POST /api/v1/pacientes/{id}/confirmar-revision` SHALL recibir el `informadoAt` que se mostró, mantener los datos guardados del paciente, poner `requiere_revision = false` y borrar lo informado, en una sola transacción que registra `PACIENTE_REVISADO`. Sobre un paciente sin revisión pendiente, MUST responder 409 `SIN_REVISION_PENDIENTE` sin cambiar nada.

#### Scenario: Mantener los datos guardados

- **GIVEN** la paciente "Ana Ejemplo" marcada para revisión con el nombre informado "Anna"
- **WHEN** el odontólogo confirma la revisión
- **THEN** su nombre sigue siendo "Ana", ya no tiene revisión pendiente y lo informado quedó vacío

#### Scenario: Confirmar dos veces

- **GIVEN** un paciente que ya se revisó
- **WHEN** se vuelve a confirmar su revisión
- **THEN** la respuesta es 409 `SIN_REVISION_PENDIENTE`

### Requirement: Corregir los datos

`PATCH /api/v1/pacientes/{id}` SHALL recibir el `informadoAt` que se mostró y corregir el nombre, el apellido y el email de un paciente con revisión pendiente, poner `requiere_revision = false` y borrar lo informado, en la misma transacción. El nombre y el apellido son obligatorios (hasta 80 caracteres) y el email, si viene, tiene formato válido; un dato inválido MUST responder 400 `VALIDACION` sin cambiar nada. El DNI y el teléfono MUST NOT modificarse: si vienen en el cuerpo, se ignoran.

#### Scenario: Usar lo escrito en la reserva

- **GIVEN** la paciente "Ana Ejemplo" marcada con el nombre informado "Anna"
- **WHEN** se corrige el nombre a "Anna"
- **THEN** su nombre pasa a "Anna", ya no tiene revisión pendiente y lo informado quedó vacío

#### Scenario: Intentar cambiar el DNI

- **WHEN** se envía una corrección con otro DNI y otro teléfono en el cuerpo
- **THEN** el DNI y el teléfono del paciente no cambian

#### Scenario: Nombre vacío

- **WHEN** se corrige con el nombre vacío
- **THEN** la respuesta es 400 `VALIDACION` con un error para `nombre` y el paciente no cambia

#### Scenario: Paciente de otro consultorio

- **WHEN** el odontólogo del consultorio A corrige un paciente del consultorio B
- **THEN** la respuesta es 404 y el paciente de B no cambia

### Requirement: Solo se corrigen pacientes marcados

En v1, la corrección SHALL aplicarse solo a pacientes con revisión pendiente. Sobre un paciente sin revisión pendiente, el `PATCH` MUST responder 409 `SIN_REVISION_PENDIENTE` sin cambiar nada.

#### Scenario: Corregir un paciente sin revisión pendiente

- **GIVEN** un paciente sin revisión pendiente
- **WHEN** se intenta corregir su nombre
- **THEN** la respuesta es 409 `SIN_REVISION_PENDIENTE` y el paciente no cambia

### Requirement: La revisión se aplica sobre lo que se vio

Confirmar y corregir MUST recibir el `informadoAt` que se mostró y tomar el `LockDeAgenda` del consultorio antes de leer el paciente. Si el paciente tiene revisión pendiente y su `informadoAt` no es igual al recibido, MUST responder 409 `CONFLICTO_CONCURRENCIA` sin cambiar nada ni auditar.

#### Scenario: Llega otra reserva mientras se revisa

- **GIVEN** la paciente "Ana Ejemplo", marcada con el nombre informado "Anna" a las 10:00, y el odontólogo abrió su revisión
- **AND** a las 10:01 otra reserva con su DNI y el nombre "Ana María" pisó lo informado y dejó la marca
- **WHEN** el odontólogo confirma la revisión, o corrige con "Anna", enviando el `informadoAt` de las 10:00
- **THEN** la respuesta es 409 `CONFLICTO_CONCURRENCIA`, el nombre sigue siendo "Ana", la revisión sigue pendiente con lo informado "Ana María" y no hay evento `PACIENTE_REVISADO`

#### Scenario: Sin el instante de lo informado

- **WHEN** se confirma la revisión sin `informadoAt` en el cuerpo
- **THEN** la respuesta es 400 `VALIDACION` con un error para `informadoAt` y el paciente no cambia

#### Scenario: Ya revisado con un instante viejo

- **GIVEN** un paciente que ya se revisó desde otra pestaña
- **WHEN** se confirma su revisión con el `informadoAt` que se había mostrado
- **THEN** la respuesta es 409 `SIN_REVISION_PENDIENTE`, no `CONFLICTO_CONCURRENCIA`

### Requirement: Auditoría de la revisión

Confirmar o corregir MUST registrar, en la misma transacción, un evento `PACIENTE_REVISADO` con el actor `ODONTOLOGO`, el id del paciente y solo los campos que cambiaron (valor anterior y nuevo), además de `requiere_revision` si cambió. El evento MUST NOT incluir lo informado ni el DNI, el teléfono o datos que no cambiaron (RN-AD-05). Si la auditoría falla, el cambio MUST revertirse.

#### Scenario: Corrección de un solo campo

- **WHEN** se corrige el nombre de "Ana" a "Anna" en un paciente marcado
- **THEN** el evento `PACIENTE_REVISADO` trae `nombre` ("Ana" → "Anna") y `requiere_revision` (true → false), y nada más

#### Scenario: Confirmación sin cambios de datos

- **WHEN** se confirma la revisión
- **THEN** el evento `PACIENTE_REVISADO` trae solo `requiere_revision` (true → false)

### Requirement: Datos de pacientes fuera de los logs

Los endpoints de pacientes MUST NOT escribir nombre, apellido, DNI, teléfono, email ni lo informado en los logs de aplicación; se enmascaran (RN-GL-01).

#### Scenario: Error inesperado al corregir

- **WHEN** una corrección falla con un error inesperado
- **THEN** el log tiene la referencia del error sin los datos del paciente
