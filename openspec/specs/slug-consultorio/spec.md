# slug-consultorio Specification

## Purpose
Define las reglas del slug que identifica al consultorio en su link público de reservas, para que sea legible, único y no choque con rutas del producto (RN-TE-03).

## Requirements

### Requirement: Formato del slug
El slug MUST tener de 3 a 40 caracteres en `[a-z0-9-]`, sin guion al inicio ni al final y sin guiones dobles. La aplicación y la base MUST rechazar un slug que no cumpla el formato.

#### Scenario: Slug válido
- **WHEN** se valida el slug `consultorio-perez-2`
- **THEN** se acepta

#### Scenario: Slugs inválidos
- **WHEN** se valida alguno de `ab`, `-perez`, `perez-`, `perez--sur`, `Perez`, `pérez`, `perez_sur` o un slug de 41 caracteres
- **THEN** se rechaza con código `SLUG_INVALIDO`

### Requirement: Palabras reservadas
El slug MUST NOT ser una palabra reservada del producto. La lista incluye al menos `app`, `api`, `login`, `registro`, `precios`, `admin`, `c` y `t`, y MUST compararse sin distinguir mayúsculas.

#### Scenario: Slug reservado
- **WHEN** se valida el slug `admin`
- **THEN** se rechaza con código `SLUG_RESERVADO`

### Requirement: Unicidad global del slug
El slug MUST ser único entre todos los consultorios; la base MUST impedir dos consultorios con el mismo slug.

#### Scenario: Slug en uso
- **GIVEN** un consultorio con slug `demo`
- **WHEN** se intenta guardar otro consultorio con slug `demo`
- **THEN** la escritura se rechaza y la aplicación lo informa con código `SLUG_EN_USO` (409)

### Requirement: Sugerencia de slug a partir del nombre
El sistema SHALL sugerir un slug válido a partir del nombre del consultorio, pasando a minúsculas, quitando tildes y diéresis, reemplazando todo lo que no sea letra o número por un guion, colapsando guiones y recortando a 40 caracteres sin guion final.

#### Scenario: Nombre con tildes y espacios
- **WHEN** se sugiere un slug para `Consultorio Odontológico  Peña & Asociados`
- **THEN** el resultado es `consultorio-odontologico-pena-asociados`

#### Scenario: Sugerencia que cae en palabra reservada o es muy corta
- **WHEN** se sugiere un slug para `Admin` o para `Té`
- **THEN** el resultado es un slug válido y no reservado, con sufijo `-consultorio` (`admin-consultorio`, `te-consultorio`)
