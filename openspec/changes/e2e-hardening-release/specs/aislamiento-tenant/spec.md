# Spec Delta

## ADDED Requirements

### Requirement: Toda la API privada aísla consultorios
Todo endpoint privado que reciba el id de un recurso en la ruta MUST responder 404 `RECURSO_NO_ENCONTRADO` cuando el id pertenece a otro consultorio, y MUST NOT cambiar datos ni emitir eventos de auditoría. Un endpoint privado nuevo que la verificación automática no cubra MUST hacerla fallar.

#### Scenario: Matriz de endpoints privados
- **GIVEN** dos consultorios con datos propios
- **WHEN** se recorre cada endpoint privado con ids de recursos del otro consultorio
- **THEN** cada respuesta es 404 `RECURSO_NO_ENCONTRADO`, y los datos y la auditoría de los dos consultorios no cambian

#### Scenario: Endpoint nuevo sin cubrir
- **WHEN** se agrega un endpoint privado y nadie lo suma a la verificación
- **THEN** la verificación automática falla y nombra ese endpoint

#### Scenario: Endpoint nuevo sin aislar
- **WHEN** un endpoint privado cubierto devuelve un recurso de otro consultorio
- **THEN** la verificación automática falla y nombra ese endpoint

#### Scenario: Endpoint sin id en la ruta
- **WHEN** un endpoint privado no recibe ids en la ruta, como un listado
- **THEN** la verificación comprueba que la respuesta no incluye datos del otro consultorio
