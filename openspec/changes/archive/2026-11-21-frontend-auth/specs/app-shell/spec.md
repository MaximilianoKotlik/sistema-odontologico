# Spec Delta

## MODIFIED Requirements

### Requirement: Navegación adaptada al ancho
Por debajo de 768 px la navegación SHALL ser una barra inferior fija con Agenda, Bloqueos, Configuración y "Más" (Auditoría, Exportar, Cambiar contraseña, Cerrar sesión); desde 768 px SHALL ser una barra lateral con todas las secciones y, en el bloque de cuenta, "Cambiar contraseña" arriba de "Cerrar sesión".

#### Scenario: Mobile a 360 px
- **WHEN** la app se muestra en un viewport de 360 px de ancho
- **THEN** se ve la barra inferior con 4 destinos, cada uno con ícono y texto, y no hay scroll horizontal

#### Scenario: Desktop
- **WHEN** la app se muestra en un viewport de 1440 px
- **THEN** se ve la barra lateral con las cinco secciones, "Cambiar contraseña" y "Cerrar sesión", y no se muestra la barra inferior

#### Scenario: Cambiar contraseña desde la hoja "Más"
- **WHEN** el odontólogo abre "Más" en mobile
- **THEN** ve "Cambiar contraseña" arriba de "Cerrar sesión" y al tocarlo llega a `/app/cuenta/contrasena`
