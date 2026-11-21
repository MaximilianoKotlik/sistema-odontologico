# Spec Delta

## Purpose

Permite que el odontólogo descargue en cualquier momento todos los datos de su consultorio en un formato abierto y legible, sin secretos, para no quedar atado al sistema (US-050, FL-06, RN-EX-01 a RN-EX-05, LG-06).

## ADDED Requirements

### Requirement: Descarga del ZIP de exportación
`POST /api/v1/exportacion` SHALL responder 200 con un archivo ZIP (`Content-Type: application/zip`, `Content-Disposition: attachment; filename="export-{slug}-{fecha}.zip"` con la fecha local del consultorio y `Cache-Control: no-store`), generado a medida que se descarga.

#### Scenario: Exportación con datos
- **GIVEN** un consultorio con prestaciones, disponibilidad, pacientes, turnos, bloqueos y eventos de auditoría
- **WHEN** su odontólogo pide la exportación
- **THEN** la respuesta es 200 con un ZIP llamado `export-{slug}-{fecha}.zip`

#### Scenario: Sin sesión
- **WHEN** se pide la exportación sin access token
- **THEN** la respuesta es 401 `NO_AUTENTICADO`

### Requirement: JSON y CSV por entidad
El ZIP SHALL incluir, para cada entidad (`consultorio`, `odontologo`, `prestaciones`, `disponibilidad`, `bloqueos`, `pacientes`, `turnos` y `auditoria`), un `{entidad}.json` con un arreglo de objetos y un `{entidad}.csv` en UTF-8, separador `,` y una fila de encabezado. Los instantes MUST expresarse en ISO-8601 UTC.

#### Scenario: Archivos presentes
- **WHEN** se abre el ZIP
- **THEN** contiene `turnos.json` y `turnos.csv`, y lo mismo para cada una de las ocho entidades

#### Scenario: Entidad vacía
- **GIVEN** un consultorio sin bloqueos
- **WHEN** se exporta
- **THEN** `bloqueos.json` es `[]` y `bloqueos.csv` tiene solo el encabezado

#### Scenario: Datos que no son del presente
- **GIVEN** prestaciones desactivadas y turnos cancelados o pasados
- **WHEN** se exporta
- **THEN** también figuran en el ZIP, con su estado

### Requirement: Manifest con cantidades consistentes
El ZIP SHALL incluir un `manifest.json` con la versión del esquema de exportación, el instante de generación, la zona horaria del consultorio, la cantidad de registros por entidad y el SHA-256 de cada archivo. Las cantidades MUST coincidir con las filas de cada archivo y con la base en el instante de la foto, aunque haya escrituras concurrentes.

#### Scenario: Cantidades que coinciden
- **GIVEN** un consultorio con 5 pacientes y 10 turnos
- **WHEN** se exporta
- **THEN** el manifest indica `pacientes: 5` y `turnos: 10`, y `pacientes.json` y `pacientes.csv` tienen 5 registros

#### Scenario: Turno creado durante la exportación
- **WHEN** se crea un turno mientras se genera la exportación
- **THEN** el manifest, `turnos.json` y `turnos.csv` coinciden entre sí (con o sin el turno nuevo, pero los tres iguales)

#### Scenario: Integridad de los archivos
- **WHEN** se calcula el SHA-256 de `turnos.csv`
- **THEN** coincide con el que figura en el manifest

### Requirement: PDF legible
El ZIP SHALL incluir un `resumen.pdf` legible, generado por el backend, con al menos estas secciones: datos del consultorio, prestaciones, pacientes, listado de turnos con fecha y hora en la zona del consultorio, y resumen de auditoría (cantidad de eventos por acción y rango de fechas). Sus cantidades MUST coincidir con el manifest.

#### Scenario: Secciones mínimas
- **WHEN** se extrae el texto de `resumen.pdf`
- **THEN** contiene las secciones Consultorio, Prestaciones, Pacientes, Turnos y Auditoría, y el total de turnos coincide con el manifest

#### Scenario: Caracteres en español
- **GIVEN** un paciente llamado `Pacienta Ñandú Pérez`
- **WHEN** se exporta
- **THEN** el texto extraído del PDF contiene `Ñandú Pérez` sin caracteres reemplazados

### Requirement: Exportación sin secretos
El ZIP MUST NOT contener el hash de la contraseña, refresh tokens, tokens de recuperación, tokens de gestión de turnos ni sus hashes, en ningún archivo, incluido el PDF (RN-EX-02).

#### Scenario: Búsqueda de secretos en el ZIP
- **GIVEN** un consultorio con un hash de contraseña, un refresh token, un token de recuperación y un turno con token de gestión conocidos
- **WHEN** se exporta y se busca cada uno de esos valores en todos los archivos y en el texto del PDF
- **THEN** ninguno aparece, y ningún archivo tiene columnas `password_hash` ni `token_hash`

### Requirement: CSV seguro para planillas
En los CSV, toda celda de texto que empiece con `=`, `+`, `-`, `@`, tabulación o retorno de carro MUST escaparse con una comilla simple inicial. El JSON MUST conservar el valor original.

#### Scenario: Nombre con fórmula
- **GIVEN** un paciente cuyo nombre es `=HIPERVINCULO("x")`
- **WHEN** se exporta
- **THEN** en `pacientes.csv` la celda es `'=HIPERVINCULO("x")` y en `pacientes.json` el nombre es `=HIPERVINCULO("x")`

### Requirement: Límite de frecuencia por consultorio
Un consultorio MUST poder completar como máximo una exportación cada 5 minutos, contados desde la última exportación completada. Si pide otra antes, o mientras otra del mismo consultorio está en curso, la respuesta MUST ser 429 `EXPORTACION_RECIENTE` con `Retry-After` en segundos (RN-EX-04).

#### Scenario: Otra exportación a los 2 minutos
- **GIVEN** una exportación completada hace 2 minutos
- **WHEN** se pide otra
- **THEN** la respuesta es 429 `EXPORTACION_RECIENTE` con `Retry-After` de alrededor de 180

#### Scenario: Pasados 5 minutos
- **GIVEN** una exportación completada hace 6 minutos
- **WHEN** se pide otra
- **THEN** la respuesta es 200

#### Scenario: Dos pedidos simultáneos
- **WHEN** el mismo consultorio pide dos exportaciones a la vez
- **THEN** una responde 200 y la otra 429

#### Scenario: Otro consultorio no comparte el límite
- **GIVEN** el consultorio A exportó hace 1 minuto
- **WHEN** el consultorio B pide su exportación
- **THEN** la respuesta es 200

### Requirement: Exportación en cualquier estado del plan
La exportación MUST funcionar igual en los planes `PRUEBA`, `ACTIVO` y `VENCIDO` (RN-EX-03).

#### Scenario: Plan vencido
- **GIVEN** un consultorio con plan `VENCIDO`
- **WHEN** su odontólogo exporta
- **THEN** la respuesta es 200 con el ZIP completo y queda el evento `EXPORTACION_GENERADA`

### Requirement: Auditoría de la exportación
Al completarse la exportación, el sistema MUST registrar un evento `EXPORTACION_GENERADA` con el odontólogo como actor, la IP y la cantidad de registros por entidad, sin datos de pacientes. Si la generación falla a mitad de camino, MUST NOT registrarse el evento y la descarga MUST cortarse sin un ZIP válido.

#### Scenario: Evento registrado
- **WHEN** se completa una exportación
- **THEN** queda un evento `EXPORTACION_GENERADA` con actor `ODONTOLOGO` y `valor_nuevo` con las cantidades por entidad

#### Scenario: Falla durante la generación
- **GIVEN** un error al leer los turnos en medio de la exportación
- **WHEN** se pide la exportación
- **THEN** el archivo descargado no es un ZIP válido, no queda evento `EXPORTACION_GENERADA` y una nueva exportación inmediata no recibe 429

### Requirement: Aislamiento de la exportación
La exportación MUST incluir solo datos del consultorio de la sesión.

#### Scenario: Dos consultorios con datos
- **GIVEN** los consultorios A y B con pacientes, turnos y eventos
- **WHEN** el odontólogo de A exporta
- **THEN** ningún archivo del ZIP contiene ids, nombres ni eventos de B
