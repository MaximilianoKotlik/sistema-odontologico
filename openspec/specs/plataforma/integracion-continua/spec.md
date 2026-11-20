# plataforma/integracion-continua Specification

## Purpose
Establece las verificaciones automáticas que todo cambio debe pasar antes de entrar a `main`, para que cinco personas trabajando en paralelo no rompan el trabajo de las demás.

## Requirements

### Requirement: Verificación automática de cada PR
La integración continua SHALL ejecutarse en cada pull request hacia `main` y en cada push a `main`. Corre los tests del backend, el chequeo de tipos, el lint, los tests y el build del frontend, y el build de ambas imágenes Docker. Un PR con cualquier verificación fallida MUST quedar marcado como fallido.

#### Scenario: PR con tests en verde
- **WHEN** se abre un pull request cuyo código pasa todas las verificaciones
- **THEN** el estado del PR en GitHub muestra todas las verificaciones exitosas

#### Scenario: PR con un test roto
- **WHEN** se abre un pull request con un test de backend o de frontend que falla
- **THEN** el estado del PR muestra la verificación correspondiente como fallida

### Requirement: Tests de persistencia contra PostgreSQL real
Los tests que tocan la base de datos MUST correr contra PostgreSQL 16 en un contenedor, con las mismas migraciones y roles que el entorno local. MUST NOT usar bases en memoria.

#### Scenario: Arranque de la aplicación en tests
- **WHEN** corre el test de humo del backend
- **THEN** la aplicación arranca contra un PostgreSQL 16 en contenedor y su endpoint de salud responde `UP`

### Requirement: Reglas de arquitectura verificadas
Los tests del backend SHALL verificar las reglas de capas de la arquitectura: la capa de dominio no depende del framework web ni de la persistencia, y ningún módulo accede a la capa de infraestructura de otro módulo. Una violación MUST hacer fallar los tests.

#### Scenario: Dominio que importa el framework
- **WHEN** una clase de la capa de dominio importa una clase del framework o de la API de persistencia
- **THEN** el test de arquitectura falla nombrando la clase infractora

#### Scenario: Acceso a infraestructura ajena
- **WHEN** una clase de un módulo usa una clase de la capa de infraestructura de otro módulo
- **THEN** el test de arquitectura falla nombrando ambas clases

### Requirement: Prohibición de tipos laxos en el frontend
La verificación del frontend MUST rechazar código TypeScript que use el tipo `any` explícito o que no compile en modo strict.

#### Scenario: Uso de any
- **WHEN** un archivo del frontend declara una variable de tipo `any`
- **THEN** el lint falla y la integración continua marca el PR como fallido
