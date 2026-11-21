# Spec Delta

## Purpose

Da al odontólogo, en la sección `/app/exportar`, una forma clara de descargar todos sus datos: le explica qué incluye la descarga, muestra el progreso y traduce los errores a mensajes en español (US-050, FL-06).

## ADDED Requirements

### Requirement: Contenido de la sección Exportar
La sección `/app/exportar` SHALL explicar qué incluye la descarga (un archivo ZIP con los datos en JSON y CSV, un PDF legible y el detalle de cantidades), que no incluye contraseñas ni tokens y que funciona aunque el plan esté vencido. MUST ofrecer un botón "Exportar mis datos". Reemplaza la sección pendiente del app-shell.

#### Scenario: Entrar a la sección
- **WHEN** el odontólogo abre `/app/exportar`
- **THEN** ve la explicación del contenido y el botón "Exportar mis datos", y ya no ve "Esta sección todavía no está disponible"

### Requirement: Descarga al exportar
Al tocar el botón, la página SHALL pedir la exportación, deshabilitar el botón con un texto de progreso anunciado a lectores de pantalla (`aria-live`) y, al terminar, guardar el archivo con el nombre que indica el servidor y mostrar una confirmación.

#### Scenario: Exportación exitosa
- **GIVEN** el servidor responde un ZIP `export-demo-2026-11-20.zip`
- **WHEN** el odontólogo toca "Exportar mis datos"
- **THEN** el botón queda deshabilitado con "Preparando tu archivo…", se descarga `export-demo-2026-11-20.zip` y aparece una confirmación

#### Scenario: Doble toque
- **WHEN** el odontólogo toca el botón dos veces seguidas
- **THEN** se hace un solo pedido

### Requirement: Mensajes de error de la exportación
La página MUST traducir los errores a mensajes en español con voseo, sin perder el botón para reintentar cuando corresponda.

#### Scenario: Exportación reciente
- **GIVEN** el servidor responde 429 `EXPORTACION_RECIENTE` con `Retry-After: 180`
- **WHEN** el odontólogo exporta
- **THEN** ve "Ya generaste una exportación hace poco. Vas a poder pedir otra en 3 minutos." y el botón vuelve a habilitarse

#### Scenario: Sin conexión
- **GIVEN** no hay conexión
- **WHEN** el odontólogo exporta
- **THEN** ve el mensaje de falta de conexión del app-shell y el botón queda disponible para reintentar

#### Scenario: Corte a mitad de la descarga
- **WHEN** la descarga se interrumpe
- **THEN** ve "No pudimos completar la exportación. Probá de nuevo." y no se guarda un archivo incompleto

### Requirement: Accesibilidad de la sección
La sección MUST cumplir AA: el botón con objetivo táctil de al menos 44 px y foco visible, los estados de progreso, éxito y error anunciados con texto (no solo color) y animaciones que respeten `prefers-reduced-motion`.

#### Scenario: Navegación con teclado
- **WHEN** el odontólogo llega al botón con Tab y lo activa con Enter
- **THEN** el foco es visible y el resultado se anuncia en la región `aria-live`
