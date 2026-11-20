# Spec Delta

## Purpose

Define cómo el backend publica su contrato HTTP y cómo el frontend lo consume tipado, para que haya un único contrato y nunca tipos duplicados a mano.

## ADDED Requirements

### Requirement: Contrato OpenAPI publicado
El backend SHALL publicar su contrato OpenAPI 3 en formato JSON en `GET /v3/api-docs` en todos los perfiles, describiendo todos los endpoints bajo `/api/v1`.

#### Scenario: Contrato disponible
- **WHEN** se consulta `GET /v3/api-docs`
- **THEN** la respuesta es 200 con un documento OpenAPI 3 válido en JSON

### Requirement: Swagger UI solo en desarrollo local
La interfaz Swagger UI MUST estar disponible únicamente con el perfil `local` y MUST NOT estar disponible en ningún otro perfil.

#### Scenario: Perfil local
- **WHEN** el backend corre con el perfil `local` y se abre `/swagger-ui.html`
- **THEN** se muestra la interfaz de Swagger UI

#### Scenario: Otro perfil
- **WHEN** el backend corre sin el perfil `local` y se abre `/swagger-ui.html`
- **THEN** la respuesta es 404

### Requirement: Tipos del cliente generados desde el contrato
El frontend SHALL obtener los tipos de la API exclusivamente de un archivo generado desde el contrato OpenAPI del backend, mediante un comando documentado. Ese archivo se versiona en el repositorio y MUST NOT editarse a mano.

#### Scenario: Regenerar tipos
- **WHEN** un integrante ejecuta `npm run api:types` con el contrato del backend disponible
- **THEN** se reescribe `frontend/src/shared/api-types.ts` a partir del contrato, sin otros cambios en el repositorio

#### Scenario: Tipos desactualizados
- **WHEN** el contrato del backend cambia y el archivo generado commiteado no se regeneró
- **THEN** la integración continua falla e indica que hay que ejecutar `npm run api:types`
