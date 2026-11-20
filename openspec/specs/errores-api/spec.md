# errores-api Specification

## Purpose
Fija el contrato de errores de toda la API: Problem Details (RFC 7807) con un `codigo` estable que el frontend traduce a mensajes en español rioplatense (RN-GL-03).

## Requirements

### Requirement: Formato Problem Details con código estable
Toda respuesta de error de la API MUST usar `Content-Type: application/problem+json` con `type`, `title`, `status`, `instance` y una propiedad `codigo` en `MAYUSCULAS_CON_GUIONES_BAJOS` que no cambia entre versiones.

#### Scenario: Error de negocio
- **WHEN** un caso de uso rechaza una operación con un error de negocio de código `SLUG_EN_USO`
- **THEN** la respuesta es 409, `application/problem+json`, con `codigo` `SLUG_EN_USO` y `status` 409

### Requirement: Errores de validación detallados por campo
Una petición con datos inválidos MUST responder 400 con código `VALIDACION` y una lista `errores` con, por cada campo, el nombre del campo y un código de error del campo.

#### Scenario: Dos campos inválidos
- **WHEN** se envía un body con dos campos que no cumplen sus restricciones
- **THEN** la respuesta es 400 con `codigo` `VALIDACION` y `errores` con dos entradas, una por campo

#### Scenario: Body ilegible
- **WHEN** se envía un body que no es JSON válido
- **THEN** la respuesta es 400 con `codigo` `CUERPO_INVALIDO`

### Requirement: Recurso inexistente
Pedir un recurso inexistente o de otro consultorio MUST responder 404 con código `RECURSO_NO_ENCONTRADO`.

#### Scenario: Id desconocido
- **WHEN** se pide un recurso con un id que no existe para el consultorio activo
- **THEN** la respuesta es 404 con `codigo` `RECURSO_NO_ENCONTRADO`

### Requirement: Conflicto de concurrencia
Una modificación que pierde contra otra concurrente (versión desactualizada) MUST responder 409 con código `CONFLICTO_CONCURRENCIA` (RN-GL-02).

#### Scenario: Versión desactualizada
- **WHEN** se guarda un recurso con una versión anterior a la persistida
- **THEN** la respuesta es 409 con `codigo` `CONFLICTO_CONCURRENCIA`

### Requirement: Errores inesperados sin filtrar detalles internos
Un error no previsto MUST responder 500 con código `ERROR_INTERNO` sin stack trace, mensajes de excepción, SQL ni datos personales en el cuerpo; el detalle MUST quedar solo en el log del servidor, con datos de pacientes enmascarados (RN-GL-01).

#### Scenario: Excepción inesperada
- **WHEN** un caso de uso lanza una excepción no controlada cuyo mensaje contiene un teléfono
- **THEN** la respuesta es 500 con `codigo` `ERROR_INTERNO` y el cuerpo no contiene el mensaje de la excepción ni el teléfono
