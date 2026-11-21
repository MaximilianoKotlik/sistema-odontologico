# Spec Delta

## Purpose

Cómo el frontend se comunica con la API del backend: dónde vive el token de acceso, cómo se renueva la sesión con la cookie de refresh y cómo se presentan los errores Problem Details.

## ADDED Requirements

### Requirement: Token de acceso solo en memoria
El cliente SHALL guardar el access token únicamente en memoria y MUST NOT persistirlo en `localStorage`, `sessionStorage`, IndexedDB ni cookies accesibles por JavaScript.

#### Scenario: Recarga de la página
- **WHEN** el odontólogo recarga la app con sesión iniciada
- **THEN** no hay token guardado en el almacenamiento del navegador y el cliente obtiene uno nuevo con `POST /api/v1/auth/refresh`

### Requirement: Autorización de pedidos privados
El cliente SHALL enviar `Authorization: Bearer <token>` en todo pedido a `/api/v1/**` que no sea `/api/v1/auth/**` ni `/api/v1/public/**`, y SHALL enviar credenciales (cookies) en los pedidos a `/api/v1/auth/**`.

#### Scenario: Pedido a la agenda
- **WHEN** la app pide `GET /api/v1/agenda` con un token en memoria
- **THEN** el pedido lleva el encabezado `Authorization` con ese token

#### Scenario: Pedido público
- **WHEN** la app pide un recurso de `/api/v1/public/**`
- **THEN** el pedido no lleva encabezado `Authorization`

### Requirement: Renovación transparente ante 401
Ante un 401 en un pedido privado, el cliente SHALL llamar una sola vez a `POST /api/v1/auth/refresh` y, si responde 200, SHALL reintentar el pedido original una vez con el token nuevo.

#### Scenario: Token vencido
- **WHEN** `GET /api/v1/agenda` responde 401 y el refresh responde 200 con un token nuevo
- **THEN** el cliente reintenta `GET /api/v1/agenda` con el token nuevo y la página recibe la respuesta del reintento

#### Scenario: El reintento vuelve a fallar
- **WHEN** el reintento después de un refresh exitoso responde 401 otra vez
- **THEN** el cliente no vuelve a renovar y trata la sesión como vencida

### Requirement: Un solo refresh para pedidos concurrentes
Si varios pedidos reciben 401 mientras hay una renovación en curso, el cliente SHALL esperar esa misma renovación y MUST NOT iniciar otra.

#### Scenario: Tres pedidos simultáneos
- **WHEN** tres pedidos privados reciben 401 al mismo tiempo
- **THEN** se hace exactamente un `POST /api/v1/auth/refresh` y los tres se reintentan con el token resultante

### Requirement: Sesión vencida
Si el refresh falla, el cliente SHALL descartar el token en memoria y la app SHALL llevar al odontólogo a `/login` conservando la ruta a la que intentaba ir.

#### Scenario: Refresh rechazado
- **WHEN** `POST /api/v1/auth/refresh` responde 401
- **THEN** el token se borra de memoria y la app navega a `/login?volver=/app/agenda`

### Requirement: Errores Problem Details tipados
El cliente SHALL convertir toda respuesta de error con `application/problem+json` en un error con `status`, `codigo` y `detail`, y SHALL convertir errores sin cuerpo o de red en un error genérico con un `codigo` propio del cliente.

#### Scenario: Error de dominio
- **WHEN** la API responde 409 con `{"codigo": "EMAIL_EN_USO", "detail": "..."}`
- **THEN** la página recibe un error con `status` 409 y `codigo` `EMAIL_EN_USO`

#### Scenario: Sin red
- **WHEN** `fetch` falla porque no hay conexión
- **THEN** la página recibe un error con `codigo` `SIN_CONEXION` y puede mostrar el estado sin conexión

### Requirement: Tipos de la API generados
Los tipos de pedidos y respuestas que use el cliente SHALL provenir del archivo generado desde el contrato OpenAPI, y MUST NOT editarse a mano.

#### Scenario: Contrato actualizado
- **WHEN** cambia el contrato OpenAPI del backend y se regenera el archivo de tipos
- **THEN** un uso incompatible en el frontend falla la verificación de tipos de TypeScript
