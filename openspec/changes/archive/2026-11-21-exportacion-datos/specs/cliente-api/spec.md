# Spec Delta

## ADDED Requirements

### Requirement: Descarga de archivos binarios
El cliente SHALL permitir pedidos privados cuya respuesta exitosa es un archivo, y devolver su contenido binario y el nombre de `Content-Disposition`. Aplica la misma autorización, renovación ante 401 y errores Problem Details tipados que los pedidos JSON, y expone el `Retry-After` de un 429.

#### Scenario: Descarga exitosa
- **GIVEN** el servidor responde 200 con `Content-Type: application/zip` y `Content-Disposition: attachment; filename="export-demo-2026-11-20.zip"`
- **WHEN** se pide la descarga
- **THEN** el cliente devuelve el contenido binario y el nombre `export-demo-2026-11-20.zip`

#### Scenario: Error en una descarga
- **GIVEN** el servidor responde 429 en Problem Details con código `EXPORTACION_RECIENTE` y `Retry-After: 180`
- **WHEN** se pide la descarga
- **THEN** el cliente rechaza con un error tipado con ese código y 180 segundos para reintentar

#### Scenario: Access token vencido
- **GIVEN** un access token vencido y un refresh válido
- **WHEN** se pide la descarga
- **THEN** el cliente renueva la sesión una vez y repite el pedido
