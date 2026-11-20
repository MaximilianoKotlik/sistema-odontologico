# Spec Delta

## Purpose

Garantiza que cualquier integrante del equipo pueda levantar el sistema completo en su máquina con un solo comando, con la misma base de datos, roles y configuración que se usan en los tests.

## ADDED Requirements

### Requirement: Arranque local con un solo comando
El sistema SHALL levantar PostgreSQL 16, el backend, el frontend y el servidor de correo de prueba con `docker compose up --build`, partiendo de un clon limpio y de un `.env` copiado de `.env.example`, sin pasos manuales adicionales.

#### Scenario: Primer arranque desde un clon limpio
- **WHEN** un integrante copia `.env.example` a `.env` y ejecuta `docker compose up --build`
- **THEN** los cuatro servicios quedan en ejecución y el backend responde en el puerto 8080, el frontend en el 5173 y la interfaz de correo en el 8025

#### Scenario: El backend espera a la base de datos
- **WHEN** PostgreSQL todavía no está listo para aceptar conexiones
- **THEN** el backend no arranca hasta que el chequeo de salud de PostgreSQL resulta exitoso

### Requirement: Roles de base de datos separados
La base de datos MUST tener dos roles: uno propietario del esquema, que solo usan las migraciones, y uno de aplicación con permisos de lectura y escritura sobre datos, sin permiso para crear o modificar objetos del esquema.

#### Scenario: Las migraciones corren con el rol propietario
- **WHEN** el backend arranca y aplica migraciones
- **THEN** las migraciones se ejecutan con el rol propietario y la aplicación atiende pedidos con el rol de aplicación

#### Scenario: El rol de aplicación no puede alterar el esquema
- **WHEN** el rol de aplicación intenta crear una tabla
- **THEN** PostgreSQL rechaza la operación por falta de permisos

#### Scenario: El rol de aplicación accede a tablas nuevas
- **WHEN** una migración crea una tabla con el rol propietario
- **THEN** el rol de aplicación puede leer e insertar filas en esa tabla sin una concesión explícita en la migración

### Requirement: Configuración por variables de entorno
Todo secreto y todo valor que cambie entre entornos MUST provenir de variables de entorno. El repositorio MUST versionar `.env.example` con todas las variables documentadas y valores de ejemplo no sensibles, y MUST NOT versionar `.env`.

#### Scenario: El archivo de secretos no se versiona
- **WHEN** un integrante crea `.env` en la raíz del repositorio
- **THEN** git lo ignora y no aparece como archivo a commitear

#### Scenario: Variable obligatoria ausente
- **WHEN** el backend arranca sin la URL o las credenciales de la base de datos
- **THEN** el arranque falla con un error que indica la configuración faltante

### Requirement: Estado de salud del backend
El backend SHALL exponer un endpoint de salud sin autenticación que informe si la aplicación y su conexión a la base de datos están operativas, sin revelar detalles internos de configuración.

#### Scenario: Backend saludable
- **WHEN** se consulta `GET /actuator/health` con la base de datos disponible
- **THEN** la respuesta es 200 con estado `UP`

#### Scenario: Sin detalles internos
- **WHEN** se consulta el endpoint de salud sin autenticación
- **THEN** la respuesta no incluye URLs, usuarios ni versiones de componentes
