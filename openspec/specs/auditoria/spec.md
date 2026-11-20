# auditoria Specification

## Purpose
Define cómo los módulos de negocio publican eventos de auditoría (quién, qué entidad, qué acción, valor anterior y nuevo) dentro de la misma transacción del cambio, para que el registro append-only pueda persistirlos sin acoplarse a cada módulo (RN-AD-01, RN-AD-03, RN-AD-05).

## Requirements

### Requirement: Contenido de un evento de auditoría
Todo evento publicado MUST incluir consultorio, instante (del reloj del sistema, en UTC), tipo de actor (`ODONTOLOGO`, `PACIENTE`, `SISTEMA`), id de actor opcional, entidad, id de entidad, acción, valor anterior y valor nuevo opcionales, e IP opcional.

#### Scenario: Evento completo
- **WHEN** un caso de uso publica un evento con actor `ODONTOLOGO`, entidad `consultorio` y acción `CONSULTORIO_CONFIG_CAMBIADA`
- **THEN** el evento recibido por los suscriptores trae esos datos, el consultorio activo y el instante del reloj del sistema

#### Scenario: Evento sin consultorio
- **WHEN** se intenta publicar un evento sin consultorio o sin acción
- **THEN** la publicación falla antes de llegar a los suscriptores

### Requirement: Los eventos nunca contienen secretos
Un evento MUST NOT contener contraseñas, hashes, tokens ni secretos en sus valores anterior o nuevo, incluidos los mapas anidados. La publicación MUST fallar si alguna clave, sin distinguir mayúsculas, es `password`, `contrasena`, `contraseña`, `token` o `secret`, o termina en `_token`, `_hash` o `_secret`.

#### Scenario: Valor con contraseña
- **WHEN** se publica un evento cuyo valor nuevo contiene la clave `password`
- **THEN** la publicación falla y no llega a los suscriptores

#### Scenario: Contraseña en español y secretos
- **WHEN** se publica un evento con alguna de las claves `contrasena`, `Contraseña`, `secret` o `jwt_secret`
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

### Requirement: Persistencia del evento publicado
El sistema SHALL persistir cada evento de auditoría publicado con un id único y todos los datos del evento: consultorio, instante, tipo de actor, id de actor, entidad, id de entidad, acción, valor anterior, valor nuevo e IP.

#### Scenario: Evento completo persistido
- **WHEN** se publica un evento `TURNO_CREADO` del consultorio A con actor `ODONTOLOGO`, valor nuevo e IP de origen, dentro de una transacción que confirma
- **THEN** el registro contiene un evento con un id nuevo y exactamente esos datos, incluidos el instante del evento y los valores con su anidamiento

#### Scenario: Evento de sistema sin actor ni IP
- **WHEN** se publica un evento con actor `SISTEMA`, sin id de actor, sin valor anterior y sin IP
- **THEN** el evento se persiste con esos campos vacíos

#### Scenario: IP con formato inválido
- **WHEN** se publica un evento cuya IP no es una dirección IPv4 ni IPv6 válida
- **THEN** el evento se persiste con la IP vacía y el cambio auditado se confirma

### Requirement: Atomicidad entre el evento y el cambio auditado
El evento SHALL persistirse en la misma transacción que el cambio que lo origina: si la transacción se revierte el evento no queda, y si la persistencia del evento falla el cambio se revierte.

#### Scenario: Cambio revertido no deja evento
- **WHEN** un caso de uso publica un evento y luego su transacción se revierte
- **THEN** el registro no contiene ese evento

#### Scenario: Evento que referencia una fila creada en la misma transacción
- **WHEN** un caso de uso crea un consultorio nuevo y publica un evento de ese consultorio en la misma transacción
- **THEN** al confirmar quedan persistidos el consultorio y el evento

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

#### Scenario: Excepción con datos personales en el detalle
- **WHEN** la aplicación registra una excepción de la base cuyo detalle contiene `odontologa@example.com`
- **THEN** ni el mensaje ni el stack trace del log muestran ese email en claro

#### Scenario: Fallo al persistir un evento
- **WHEN** la persistencia de un evento falla y se registra el error
- **THEN** el log identifica la acción y la entidad pero no incluye los valores anterior ni nuevo
