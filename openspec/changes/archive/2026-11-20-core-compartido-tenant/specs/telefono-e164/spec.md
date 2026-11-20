# Spec Delta

## Purpose

Normaliza los teléfonos que ingresan al sistema (contacto del consultorio, celular del paciente) a un formato único E.164 para validarlos, compararlos y deduplicarlos de forma consistente.

## ADDED Requirements

### Requirement: Normalización a E.164 con Argentina como región por defecto
El sistema SHALL convertir un teléfono ingresado en formato local o internacional a E.164, interpretando los números sin prefijo internacional como argentinos e incluyendo el `9` de celular cuando corresponde.

#### Scenario: Celular de AMBA en formato local
- **WHEN** se normaliza `011 15-0000-0001`
- **THEN** el resultado es `+5491100000001`

#### Scenario: Celular ya internacional con separadores
- **WHEN** se normaliza `+54 9 11 0000-0001`
- **THEN** el resultado es `+5491100000001`

#### Scenario: Mismo celular en dos formatos
- **WHEN** se normalizan `011 15 0000-0001` y `+54 9 11 0000 0001`
- **THEN** ambos dan el mismo valor E.164

#### Scenario: Fijo sin el 9 de celular
- **WHEN** se normaliza `011 0000-0001`
- **THEN** el resultado es `+541100000001`

### Requirement: Rechazo de teléfonos inválidos
Un teléfono que no tiene una longitud y estructura posibles para su región MUST rechazarse con código `TELEFONO_INVALIDO`, sin incluir el valor ingresado en el mensaje de error ni en los logs.

#### Scenario: Número imposible
- **WHEN** se normaliza `123`
- **THEN** se rechaza con código `TELEFONO_INVALIDO`

#### Scenario: Texto no numérico
- **WHEN** se normaliza `no tengo`
- **THEN** se rechaza con código `TELEFONO_INVALIDO`
