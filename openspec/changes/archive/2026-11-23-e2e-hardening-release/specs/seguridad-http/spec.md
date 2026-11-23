# Spec Delta

## ADDED Requirements

### Requirement: IP real detrás de un proxy de confianza
Por defecto la IP del cliente MUST ser la de la conexión, y `X-Forwarded-For` MUST ignorarse. Si se configura que el backend corre detrás de un proxy de confianza, la IP MUST tomarse de la cabecera que ese proxy reemplaza, para que el límite por IP cuente cada cliente por separado.

#### Scenario: Sin proxy configurado
- **GIVEN** el backend sin proxy de confianza configurado
- **WHEN** llegan logins con distintos valores de `X-Forwarded-For` desde la misma conexión
- **THEN** todos comparten el cupo de esa conexión

#### Scenario: Dos clientes detrás del proxy
- **GIVEN** el backend configurado detrás de un proxy de confianza
- **WHEN** dos clientes con IPs distintas hacen logins a través del proxy
- **THEN** cada uno tiene su propio cupo, y agotar el de uno no frena al otro

#### Scenario: Cabecera falseada a través del proxy
- **GIVEN** el backend detrás de un proxy de confianza que reemplaza `X-Forwarded-For`
- **WHEN** un cliente manda su propio `X-Forwarded-For` con otra IP
- **THEN** el límite cuenta la IP que puso el proxy, no la que mandó el cliente

### Requirement: Respuestas sin caché
Las respuestas de la API privada (`/api/v1/**` salvo `/api/v1/public/**`) y las de las rutas por token de gestión MUST incluir `Cache-Control: no-store`, porque llevan datos personales o tokens.

#### Scenario: Respuesta privada
- **WHEN** el odontólogo pide su agenda
- **THEN** la respuesta incluye `Cache-Control: no-store`

#### Scenario: Respuesta por token
- **WHEN** un paciente abre su turno con el token de gestión
- **THEN** la respuesta incluye `Cache-Control: no-store`

#### Scenario: Error de la API privada
- **WHEN** una petición privada responde con un error
- **THEN** la respuesta de error también incluye `Cache-Control: no-store`
