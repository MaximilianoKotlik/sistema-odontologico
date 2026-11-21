# Spec Delta

## ADDED Requirements

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
