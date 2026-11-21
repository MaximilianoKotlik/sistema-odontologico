# Spec Delta

## MODIFIED Requirements

### Requirement: Un solo refresh para pedidos concurrentes
Si varios pedidos reciben 401 mientras hay una renovación en curso, el cliente SHALL esperar esa misma renovación y MUST NOT iniciar otra. Entre pestañas del mismo navegador, las renovaciones MUST serializarse, para que ninguna presente un refresh que otra ya rotó (la detección de reuso de C-04 cerraría todas las sesiones).

#### Scenario: Tres pedidos simultáneos
- **WHEN** tres pedidos privados reciben 401 al mismo tiempo
- **THEN** se hace exactamente un `POST /api/v1/auth/refresh` y los tres se reintentan con el token resultante

#### Scenario: Dos pestañas a la vez
- **GIVEN** la app abierta en dos pestañas con el access token vencido
- **WHEN** las dos necesitan renovar al mismo tiempo
- **THEN** los dos refresh se hacen uno después del otro, el segundo con la cookie ya rotada, y ninguna pestaña pierde la sesión
