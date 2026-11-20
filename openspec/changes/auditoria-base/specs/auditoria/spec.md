# Spec Delta

## ADDED Requirements

### Requirement: Persistencia del evento publicado
El sistema SHALL persistir cada evento de auditoría publicado con un id único y todos los datos del evento: consultorio, instante, tipo de actor, id de actor, entidad, id de entidad, acción, valor anterior, valor nuevo e IP.

#### Scenario: Evento completo persistido
- **WHEN** se publica un evento `TURNO_CREADO` del consultorio A con actor `ODONTOLOGO`, valor nuevo e IP de origen, dentro de una transacción que confirma
- **THEN** el registro contiene un evento con un id nuevo y exactamente esos datos, incluidos el instante del evento y los valores con su anidamiento

#### Scenario: Evento de sistema sin actor ni IP
- **WHEN** se publica un evento con actor `SISTEMA`, sin id de actor, sin valor anterior y sin IP
- **THEN** el evento se persiste con esos campos vacíos

### Requirement: Atomicidad entre el evento y el cambio auditado
El evento SHALL persistirse en la misma transacción que el cambio que lo origina: si la transacción se revierte el evento no queda, y si la persistencia del evento falla el cambio se revierte.

#### Scenario: Cambio revertido no deja evento
- **WHEN** un caso de uso publica un evento y luego su transacción se revierte
- **THEN** el registro no contiene ese evento

#### Scenario: Fallo de auditoría revierte el cambio
- **WHEN** un caso de uso modifica datos y publica un evento cuya persistencia falla
- **THEN** la transacción se revierte y la modificación de datos no queda guardada

### Requirement: Inmutabilidad del registro
El registro SHALL ser append-only: ningún evento persistido puede modificarse, borrarse ni vaciarse, ni siquiera con las credenciales de base de la aplicación. La garantía MUST vivir en la base de datos, no solo en el código.

#### Scenario: Modificar un evento falla
- **WHEN** la aplicación, con su rol de base, ejecuta un `UPDATE` sobre un evento existente
- **THEN** la base rechaza la operación y el evento queda sin cambios

#### Scenario: Borrar un evento falla
- **WHEN** la aplicación, con su rol de base, ejecuta un `DELETE` sobre un evento existente
- **THEN** la base rechaza la operación y el evento sigue existiendo

#### Scenario: Vaciar el registro falla
- **WHEN** la aplicación, con su rol de base, ejecuta un `TRUNCATE` sobre el registro
- **THEN** la base rechaza la operación y los eventos siguen existiendo

#### Scenario: Defensa aunque se otorguen permisos
- **WHEN** un rol con permiso de `UPDATE` o `DELETE` sobre el registro intenta modificar o borrar un evento
- **THEN** la base igualmente rechaza la operación

### Requirement: Pertenencia a un consultorio
Todo evento persistido SHALL pertenecer a un consultorio existente y quedar asociado únicamente al consultorio indicado en el evento.

#### Scenario: Eventos de consultorios distintos
- **WHEN** se publican un evento del consultorio A y otro del consultorio B
- **THEN** cada evento queda asociado solo a su consultorio y los eventos de A no incluyen el de B

#### Scenario: Consultorio inexistente
- **WHEN** se publica un evento con un consultorio que no existe en la base
- **THEN** la persistencia falla y la transacción se revierte

### Requirement: Datos personales enmascarados en logs
Los logs de la aplicación MUST NOT exponer emails, teléfonos ni tokens en claro, ni el contenido de los valores de los eventos de auditoría.

#### Scenario: Email y teléfono en un mensaje de log
- **WHEN** la aplicación registra un mensaje que contiene `paciente@example.com` y `+54 9 11 0000-0001`
- **THEN** la salida del log muestra ambos datos enmascarados

#### Scenario: Fallo al persistir un evento
- **WHEN** la persistencia de un evento falla y se registra el error
- **THEN** el log identifica la acción y la entidad pero no incluye los valores anterior ni nuevo
