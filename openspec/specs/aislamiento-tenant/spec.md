# aislamiento-tenant Specification

## Purpose
Garantiza que cada dato de negocio pertenezca a exactamente un consultorio (tenant) y que ningún consultorio pueda leer, modificar ni descubrir datos de otro, con invariantes en la base y en la aplicación (RN-TE-01, RN-TE-04, DD-08).

## Requirements

### Requirement: El consultorio es la raíz del tenant
El sistema SHALL persistir cada consultorio con identificador UUID generado por la aplicación (UUIDv7), nombre, slug único, zona horaria (default `America/Argentina/Buenos_Aires`), anticipaciones de cancelación y reserva, horizonte de reserva, paso de huecos, `reserva_publica_activa` y plan (`PRUEBA`, `ACTIVO`, `VENCIDO`) con `prueba_hasta`.

#### Scenario: Alta con valores por defecto
- **WHEN** se inserta un consultorio indicando solo id, nombre, slug y plan `PRUEBA`
- **THEN** queda con zona horaria `America/Argentina/Buenos_Aires`, anticipación de cancelación 1440 min, anticipación de reserva 120 min, horizonte 60 días, paso de huecos 15 min y reserva pública activa

#### Scenario: Valores fuera de rango rechazados por la base
- **WHEN** se intenta guardar un consultorio con anticipación de cancelación mayor a 10080 min, paso de huecos distinto de 5, 10, 15, 20 o 30, o plan fuera de `PRUEBA`, `ACTIVO`, `VENCIDO`
- **THEN** la base rechaza la escritura por violación de restricción

#### Scenario: Identificadores ordenables y no secuenciales
- **WHEN** la aplicación genera dos identificadores consecutivos
- **THEN** ambos son UUID versión 7, distintos, y el segundo ordena después del primero

### Requirement: El consultorio activo se resuelve solo desde el contexto autenticado o la ruta pública
El sistema MUST obtener el consultorio activo de una operación únicamente del contexto de tenant (poblado desde la sesión o desde el slug o token de una ruta pública) y MUST NOT aceptarlo desde el body ni la query de la petición.

#### Scenario: Operación sin tenant resuelto
- **WHEN** un caso de uso que requiere tenant se ejecuta sin consultorio activo en el contexto
- **THEN** la operación falla con un error interno y no accede a datos

#### Scenario: Consultorio en el body ignorado
- **WHEN** una petición incluye un `consultorio_id` en el body o la query distinto del consultorio activo
- **THEN** el sistema opera solo sobre el consultorio activo y nunca sobre el indicado en la petición

### Requirement: Las relaciones entre datos de negocio no cruzan consultorios
Toda tabla de negocio MUST tener `consultorio_id` no nulo y sus referencias a otras tablas de negocio MUST ser compuestas `(consultorio_id, id)`, de modo que la base impida relacionar registros de consultorios distintos.

#### Scenario: Referencia a un registro de otro consultorio
- **GIVEN** un registro del consultorio A y un registro hijo del consultorio B
- **WHEN** se intenta guardar el hijo apuntando al registro del consultorio A
- **THEN** la base rechaza la escritura por violación de clave foránea

#### Scenario: Referencia dentro del mismo consultorio
- **GIVEN** un registro del consultorio A
- **WHEN** se guarda un hijo del consultorio A que lo referencia
- **THEN** la escritura se acepta

### Requirement: Toda consulta de datos de negocio filtra por consultorio
El acceso a datos de negocio MUST recibir el consultorio explícitamente en cada consulta; el build MUST fallar si un repositorio de negocio expone búsquedas por id o listados sin consultorio.

#### Scenario: Repositorio sin filtro de tenant
- **WHEN** se agrega a un repositorio de negocio un método de búsqueda que no recibe el consultorio
- **THEN** el test de arquitectura falla y el build no pasa

#### Scenario: Listado entre dos consultorios
- **GIVEN** registros de los consultorios A y B
- **WHEN** se listan los registros con el consultorio A activo
- **THEN** solo se devuelven los registros del consultorio A

### Requirement: Un recurso de otro consultorio responde como inexistente
El sistema MUST responder 404 con código `RECURSO_NO_ENCONTRADO` cuando se pide un recurso que existe pero pertenece a otro consultorio, de forma indistinguible de un recurso inexistente (RN-TE-04).

#### Scenario: Recurso ajeno
- **GIVEN** un recurso del consultorio B
- **WHEN** se lo pide por id con el consultorio A activo
- **THEN** la respuesta es 404 con código `RECURSO_NO_ENCONTRADO`, sin revelar que el recurso existe

#### Scenario: Ajeno e inexistente son indistinguibles
- **WHEN** se compara la respuesta a un id de otro consultorio con la respuesta a un id inexistente
- **THEN** ambas tienen el mismo estado, código y cuerpo, salvo el `instance`
