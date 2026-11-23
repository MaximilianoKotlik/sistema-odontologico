# Spec Delta

## MODIFIED Requirements

### Requirement: Navegación por rutas privadas
La app SHALL resolver las rutas `/app/agenda`, `/app/bloqueos`, `/app/pacientes`, `/app/pacientes/{id}`, `/app/configuracion`, `/app/auditoria` y `/app/exportar` sin recargar la página, y `/app` SHALL redirigir a `/app/agenda`.

#### Scenario: Navegar entre secciones
- **WHEN** el odontólogo toca "Bloqueos" en la navegación
- **THEN** la URL pasa a `/app/bloqueos`, se muestra esa sección y la página no se recarga

#### Scenario: Botones atrás y adelante
- **WHEN** el odontólogo usa "atrás" del navegador después de navegar de Agenda a Bloqueos
- **THEN** se vuelve a mostrar Agenda con la URL `/app/agenda`

#### Scenario: Ruta raíz de la app
- **WHEN** se abre `/app`
- **THEN** la app reemplaza la URL por `/app/agenda` sin agregar una entrada al historial

#### Scenario: Revisión de un paciente
- **WHEN** se abre `/app/pacientes/{id}` directamente o al recargar la página
- **THEN** se muestra la revisión de ese paciente dentro de la sección Pacientes

### Requirement: Navegación adaptada al ancho
Por debajo de 768 px la navegación SHALL ser una barra inferior fija con Agenda, Bloqueos, Configuración y "Más" (Pacientes, Auditoría, Exportar, Cambiar contraseña, Cerrar sesión); desde 768 px SHALL ser una barra lateral con todas las secciones, con Pacientes después de Bloqueos y, en el bloque de cuenta, "Cambiar contraseña" arriba de "Cerrar sesión". Si hay pacientes con revisión pendiente, "Pacientes" y "Más" SHALL mostrar la cantidad con un número y un texto para lectores de pantalla, no solo color.

#### Scenario: Mobile a 360 px
- **WHEN** la app se muestra en un viewport de 360 px de ancho
- **THEN** se ve la barra inferior con 4 destinos, cada uno con ícono y texto, y no hay scroll horizontal

#### Scenario: Desktop
- **WHEN** la app se muestra en un viewport de 1440 px
- **THEN** se ve la barra lateral con las seis secciones, "Cambiar contraseña" y "Cerrar sesión", y no se muestra la barra inferior

#### Scenario: Cambiar contraseña desde la hoja "Más"
- **WHEN** el odontólogo abre "Más" en mobile
- **THEN** ve "Cambiar contraseña" arriba de "Cerrar sesión" y al tocarlo llega a `/app/cuenta/contrasena`

#### Scenario: Contador de pacientes para revisar
- **GIVEN** 3 pacientes con revisión pendiente
- **WHEN** la app se muestra en mobile y en desktop
- **THEN** "Pacientes" (barra lateral y hoja "Más") y el botón "Más" muestran el número 3, anunciado como "3 para revisar"

#### Scenario: Sin pacientes para revisar
- **WHEN** no hay pacientes con revisión pendiente
- **THEN** la navegación no muestra el contador
