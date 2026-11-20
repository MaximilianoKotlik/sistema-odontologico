# Spec Delta

## Purpose

Permite instalar la app del odontólogo como PWA y define qué guarda el Service Worker para leer la última agenda sin conexión, y qué no guarda nunca por privacidad y seguridad.

## ADDED Requirements

### Requirement: App instalable
La app SHALL publicar un manifiesto con el nombre del producto, íconos de 192 y 512 px, `display: standalone` y `start_url: /app/agenda`.

#### Scenario: Instalación desde el navegador
- **WHEN** el odontólogo instala la app desde un navegador compatible
- **THEN** la app abre en una ventana independiente directamente en `/app/agenda`

### Requirement: Shell disponible sin conexión
El Service Worker SHALL guardar de antemano los recursos estáticos del shell para que la app abra sin conexión después de una primera visita.

#### Scenario: Abrir la app offline
- **WHEN** el odontólogo ya visitó la app y la abre sin conexión
- **THEN** se muestra el shell con la navegación y el aviso de sin conexión, no la página de error del navegador

### Requirement: Última agenda legible sin conexión
Para `GET /api/v1/agenda` el Service Worker SHALL intentar primero la red y, si no hay conexión, SHALL responder con la última respuesta exitosa guardada para esa misma URL.

#### Scenario: Agenda ya vista
- **WHEN** el odontólogo vio la agenda del día con conexión y después la abre sin conexión
- **THEN** ve esos mismos turnos con el aviso de la hora en que se guardaron

#### Scenario: Agenda nunca vista
- **WHEN** el odontólogo pide sin conexión un día que nunca abrió con conexión
- **THEN** se muestra el estado de error sin conexión, no datos de otro día

### Requirement: Datos que nunca se guardan
El Service Worker MUST NOT guardar respuestas de `/api/v1/auth/**`, de `/api/v1/public/**` ni de la gestión de turnos por token, y MUST NOT guardar respuestas de pedidos que no sean `GET`.

#### Scenario: Refresh de sesión
- **WHEN** la app llama a `POST /api/v1/auth/refresh`
- **THEN** la respuesta no queda en ninguna caché del Service Worker

#### Scenario: Página pública de un paciente
- **WHEN** un paciente abre `/t/{token}` en el mismo navegador
- **THEN** ninguna respuesta de la API pública queda guardada en la caché

### Requirement: Sin escritura offline
La app MUST NOT encolar ni reintentar más tarde cambios hechos sin conexión.

#### Scenario: Intento de crear un turno offline
- **WHEN** el dispositivo está sin conexión
- **THEN** las acciones que modifican datos están deshabilitadas y no se guarda ningún pedido pendiente

### Requirement: Caché borrada al cerrar sesión
Al cerrar sesión, la app SHALL borrar la caché de la agenda del Service Worker.

#### Scenario: Cierre de sesión en un equipo compartido
- **WHEN** el odontólogo cierra sesión y otra persona abre la app sin conexión
- **THEN** no se muestra ningún turno guardado
