# prestaciones Specification

## Purpose
Permite que el odontólogo defina los tipos de prestación que atiende, con su duración, para que cada turno ocupe el tiempo real y la página pública ofrezca solo lo que corresponde (US-010, RN-AG-02, RN-AG-07, RN-AG-10).

## Requirements

### Requirement: Alta de prestación
El odontólogo SHALL poder crear una prestación con nombre, duración en minutos, visibilidad pública y orden. Una prestación nueva MUST quedar activa y, si no se indica otra cosa, visible en la página pública.

#### Scenario: Limpieza de 45 minutos
- **WHEN** el odontólogo crea la prestación "Limpieza" de 45 min
- **THEN** la respuesta es 201 con la prestación activa y visible públicamente

### Requirement: Duración en múltiplos de 5
La duración MUST estar entre 5 y 480 minutos y ser múltiplo de 5; si no, la respuesta MUST ser 400 `DURACION_INVALIDA`.

#### Scenario: 47 minutos
- **WHEN** se crea una prestación de 47 min
- **THEN** la respuesta es 400 con código `DURACION_INVALIDA`

#### Scenario: Fuera de rango
- **WHEN** se crea una prestación de 0 o de 485 min
- **THEN** la respuesta es 400 con código `DURACION_INVALIDA`

### Requirement: Nombre único por consultorio
El nombre de la prestación MUST ser único dentro del consultorio sin distinguir mayúsculas; un nombre repetido MUST responder 409 `PRESTACION_DUPLICADA`. Otro consultorio MAY usar el mismo nombre.

#### Scenario: Nombre repetido con otras mayúsculas
- **GIVEN** existe la prestación "Limpieza"
- **WHEN** se crea "LIMPIEZA" en el mismo consultorio
- **THEN** la respuesta es 409 con código `PRESTACION_DUPLICADA`

#### Scenario: Mismo nombre en otro consultorio
- **GIVEN** el consultorio A tiene "Limpieza"
- **WHEN** el consultorio B crea "Limpieza"
- **THEN** la respuesta es 201

### Requirement: Edición de prestación
El odontólogo SHALL poder cambiar nombre, duración, visibilidad pública y orden de una prestación, con las mismas validaciones del alta. Cambiar la duración MUST NOT alterar turnos existentes (RN-AG-02).

#### Scenario: Cambio de duración
- **WHEN** el odontólogo cambia "Limpieza" de 45 a 60 min
- **THEN** la respuesta es 200 con la duración nueva

### Requirement: Desactivación en lugar de borrado
Una prestación MUST NOT poder borrarse: se desactiva y se puede volver a activar. Una prestación inactiva MUST dejar de ofrecerse, sin afectar sus turnos existentes (RN-AG-10).

#### Scenario: Desactivar
- **WHEN** el odontólogo desactiva "Limpieza"
- **THEN** la respuesta es 200, la prestación queda inactiva y no aparece en el listado por defecto

#### Scenario: Reactivar
- **GIVEN** "Limpieza" inactiva
- **WHEN** el odontólogo la activa
- **THEN** vuelve a aparecer en el listado por defecto

#### Scenario: Sin borrado
- **WHEN** se pide `DELETE` sobre una prestación
- **THEN** la respuesta es 405 con código `METODO_NO_PERMITIDO` y la prestación sigue existiendo

### Requirement: Listado ordenado
El listado SHALL devolver las prestaciones activas del consultorio ordenadas por `orden` y nombre; con `incluirInactivas=true` MUST incluir también las inactivas.

#### Scenario: Listado por defecto
- **GIVEN** "Consulta" activa con orden 1, "Limpieza" activa con orden 2 y "Blanqueamiento" inactiva
- **WHEN** se pide el listado
- **THEN** devuelve "Consulta" y "Limpieza", en ese orden

### Requirement: Auditoría de prestaciones
Cada alta, edición, desactivación y activación MUST registrar en la misma transacción un evento (`PRESTACION_CREADA`, `PRESTACION_CAMBIADA`, `PRESTACION_DESACTIVADA`, `PRESTACION_ACTIVADA`) con la prestación como entidad y los campos que cambiaron.

#### Scenario: Alta auditada
- **WHEN** se crea "Limpieza" de 45 min
- **THEN** queda un evento `PRESTACION_CREADA` con valor nuevo `{nombre: Limpieza, duracion_min: 45, visible_publica: true, orden: 1, activa: true}` y sin valor anterior

### Requirement: Aislamiento de prestaciones
Una prestación de otro consultorio MUST responder 404 `RECURSO_NO_ENCONTRADO` al consultarla, editarla, desactivarla o activarla, sin modificarla.

#### Scenario: Desactivar una prestación ajena
- **GIVEN** una prestación del consultorio B
- **WHEN** el odontólogo de A intenta desactivarla
- **THEN** la respuesta es 404 y la prestación de B sigue activa
