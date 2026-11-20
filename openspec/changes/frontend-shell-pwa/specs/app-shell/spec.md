# Spec Delta

## Purpose

Estructura común de la app privada del odontólogo: rutas `/app/*`, navegación mobile y desktop, y estados de carga, vacío, error y sin conexión que reutilizan todas las páginas.

## ADDED Requirements

### Requirement: Navegación por rutas privadas
La app SHALL resolver las rutas `/app/agenda`, `/app/bloqueos`, `/app/configuracion`, `/app/auditoria` y `/app/exportar` sin recargar la página, y `/app` SHALL redirigir a `/app/agenda`.

#### Scenario: Navegar entre secciones
- **WHEN** el odontólogo toca "Bloqueos" en la navegación
- **THEN** la URL pasa a `/app/bloqueos`, se muestra esa sección y la página no se recarga

#### Scenario: Botones atrás y adelante
- **WHEN** el odontólogo usa "atrás" del navegador después de navegar de Agenda a Bloqueos
- **THEN** se vuelve a mostrar Agenda con la URL `/app/agenda`

#### Scenario: Ruta raíz de la app
- **WHEN** se abre `/app`
- **THEN** la app reemplaza la URL por `/app/agenda` sin agregar una entrada al historial

### Requirement: Ruta inexistente
La app SHALL mostrar una pantalla "No encontramos esta página" con un acceso a la agenda cuando la ruta `/app/*` no existe.

#### Scenario: URL desconocida
- **WHEN** se abre `/app/no-existe`
- **THEN** se muestra el mensaje de página no encontrada y un botón "Ir a mi agenda"

### Requirement: Navegación adaptada al ancho
Por debajo de 768 px la navegación SHALL ser una barra inferior fija con Agenda, Bloqueos, Configuración y "Más" (Auditoría, Exportar, Cerrar sesión); desde 768 px SHALL ser una barra lateral con todas las secciones.

#### Scenario: Mobile a 360 px
- **WHEN** la app se muestra en un viewport de 360 px de ancho
- **THEN** se ve la barra inferior con 4 destinos, cada uno con ícono y texto, y no hay scroll horizontal

#### Scenario: Desktop
- **WHEN** la app se muestra en un viewport de 1440 px
- **THEN** se ve la barra lateral con las cinco secciones y "Cerrar sesión", y no se muestra la barra inferior

### Requirement: Sección activa visible
La navegación SHALL marcar la sección actual con `aria-current="page"` y con una señal visual que no dependa solo del color.

#### Scenario: Sección activa
- **WHEN** la ruta actual es `/app/configuracion`
- **THEN** el destino "Configuración" tiene `aria-current="page"`, fondo destacado e indicador de forma, y los demás no

### Requirement: Accesibilidad de la navegación
Cada destino de navegación SHALL tener un área táctil de al menos 44×44 px, foco visible y nombre accesible en español; la app SHALL incluir un enlace "Saltar al contenido".

#### Scenario: Navegación con teclado
- **WHEN** el odontólogo recorre la app con Tab desde el inicio
- **THEN** el primer foco es "Saltar al contenido" y cada destino muestra el anillo de foco del sistema de diseño

### Requirement: Estado de carga
Mientras una página espera datos, el shell SHALL mostrar un esqueleto del contenido anunciado como ocupado (`aria-busy="true"`) y SHALL mantener visible la navegación.

#### Scenario: Carga de la agenda
- **WHEN** la agenda todavía no recibió respuesta del servidor
- **THEN** se ven bloques esqueleto en lugar de turnos y la navegación sigue operable

#### Scenario: Movimiento reducido
- **WHEN** el sistema tiene `prefers-reduced-motion: reduce`
- **THEN** el esqueleto se muestra sin animación de brillo

### Requirement: Estado vacío
Cuando una página no tiene datos, el shell SHALL mostrar el componente de estado vacío con ícono, título, explicación y una acción principal.

#### Scenario: Día sin turnos
- **WHEN** la agenda del día no tiene turnos
- **THEN** se muestra "Todavía no tenés turnos hoy" con la acción "Copiar mi link"

### Requirement: Estado de error recuperable
Cuando falla la carga de una página, el shell SHALL mostrar un mensaje de error en español con un botón "Reintentar" que repite la carga, sin perder la navegación.

#### Scenario: Error del servidor
- **WHEN** el pedido de datos de la página responde 500
- **THEN** se muestra "No pudimos cargar tu agenda" con "Reintentar", y al tocarlo se repite el pedido

### Requirement: Aviso sin conexión
Cuando el dispositivo está sin conexión, el shell SHALL mostrar un aviso persistente que indique que se muestra la última información guardada y su hora, y SHALL deshabilitar las acciones que modifican datos.

#### Scenario: Se pierde la conexión
- **WHEN** el navegador pasa a `offline` con la agenda abierta
- **THEN** aparece el aviso "Sin conexión · Mostrando tu agenda de las HH:MM" y los botones de crear o editar quedan deshabilitados con su motivo

#### Scenario: Vuelve la conexión
- **WHEN** el navegador vuelve a `online`
- **THEN** el aviso desaparece y la página vuelve a pedir los datos

### Requirement: Nombre de producto provisorio
El shell SHALL mostrar el nombre del producto tomado de una única constante de marca, sin repetirlo en el código de las páginas.

#### Scenario: Cambio de nombre
- **WHEN** se cambia la constante de marca
- **THEN** la barra superior y el título del documento muestran el nombre nuevo sin otros cambios
