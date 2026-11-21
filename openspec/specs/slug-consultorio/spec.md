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

### Requirement: Cambio de slug por el odontólogo
El odontólogo SHALL poder cambiar el slug de su consultorio, con las reglas de formato, palabras reservadas y unicidad. Si el slug pedido ya está en uso, la respuesta MUST ser 409 `SLUG_EN_USO` e incluir hasta 3 slugs libres sugeridos a partir del pedido. Al cambiarlo, el slug anterior MUST quedar libre (RN-TE-03).

#### Scenario: Slug libre
- **WHEN** el odontólogo cambia su slug a `dra-gomez`, que está libre
- **THEN** la respuesta es 200 y el consultorio queda con slug `dra-gomez`

#### Scenario: Slug reservado
- **WHEN** el odontólogo cambia su slug a `admin`
- **THEN** la respuesta es 400 con código `SLUG_RESERVADO`

#### Scenario: Slug en uso con sugerencias
- **GIVEN** otro consultorio usa `dra-gomez`
- **WHEN** el odontólogo pide `dra-gomez`
- **THEN** la respuesta es 409 con código `SLUG_EN_USO` y sugerencias libres como `dra-gomez-2`

#### Scenario: El slug anterior queda libre
- **GIVEN** el consultorio A cambió su slug de `demo-sur` a `demo-norte`
- **WHEN** el consultorio B pide `demo-sur`
- **THEN** la respuesta es 200

#### Scenario: Mismo slug que ya tenía
- **WHEN** el odontólogo guarda la configuración con el slug que ya tiene
- **THEN** la respuesta es 200 y no hay conflicto consigo mismo
