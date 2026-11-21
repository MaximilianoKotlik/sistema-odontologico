# Spec Delta

## Purpose

Permite que un paciente sin cuenta, desde el link `/c/{slug}` que comparte el consultorio, elija prestación, día y horario, deje sus datos y reciba una sola vez el link para gestionar su turno, desde el celular y en menos de 90 segundos (US-030, FL-04).

## ADDED Requirements

### Requirement: Página pública del consultorio

La página `/c/{slug}` SHALL mostrarse sin iniciar sesión y sin el shell del odontólogo, con el nombre, la dirección y el teléfono del consultorio (DD-33). MUST NOT mostrar datos del odontólogo, del plan ni de otros pacientes, y MUST NOT enviar credenciales en sus pedidos.

#### Scenario: Visita al link del consultorio

- **WHEN** un visitante sin sesión abre `/c/demo` de un consultorio que toma turnos
- **THEN** ve el nombre, la dirección y el teléfono del consultorio y la lista de prestaciones para elegir
- **AND** ningún pedido de la página lleva `Authorization` ni cookies de sesión

#### Scenario: Consultorio inexistente

- **WHEN** el slug no corresponde a ningún consultorio (404)
- **THEN** la página muestra "No encontramos este consultorio", sin encabezado de consultorio

### Requirement: Consultorio que no toma turnos online

Si la página pública responde `tomaTurnosOnline: false`, la página MUST mostrar que el consultorio no toma turnos online por ahora, con un botón para llamar al teléfono del consultorio, y MUST NOT mostrar prestaciones, días ni horarios (RN-RP-11). Un 409 `RESERVA_NO_DISPONIBLE` durante el flujo MUST llevar a ese mismo estado.

#### Scenario: Reserva desactivada

- **WHEN** la página pública responde `tomaTurnosOnline: false`
- **THEN** se ve "Por ahora este consultorio no toma turnos online" con el botón para llamar, sin selector de turnos

#### Scenario: Se desactivó mientras el paciente elegía

- **GIVEN** el paciente ya eligió prestación y horario
- **WHEN** la reserva responde 409 `RESERVA_NO_DISPONIBLE`
- **THEN** la página pasa al estado "no toma turnos online" con el teléfono

### Requirement: Elección de prestación

La página SHALL listar las prestaciones que devuelve la API, con su nombre y duración y sin precio. Elegir una prestación MUST cargar sus días y horarios.

#### Scenario: Elegir una prestación

- **WHEN** el paciente toca "Limpieza dental · 45 min"
- **THEN** queda marcada como elegida (no solo por color) y aparecen los días y horarios de esa prestación

### Requirement: Días según la disponibilidad

La tira de días SHALL mostrar cada fecha que devuelve la consulta de huecos. Un día con `atiende: false` o sin huecos MUST verse deshabilitado, con borde distinto y el texto "sin turnos", no solo con color. Si el paciente elige un día que se quedó sin huecos, la página MUST ofrecerle ir al próximo día con horarios.

#### Scenario: Sábado con franjas y domingo sin atención

- **GIVEN** un consultorio que atiende los sábados de 9 a 13 y no atiende los domingos
- **WHEN** se muestran los días de la semana
- **THEN** el sábado se puede elegir y el domingo se ve como "sin turnos", deshabilitado

#### Scenario: Día completo

- **GIVEN** un día con `atiende: true` y la lista de huecos vacía
- **WHEN** el paciente lo elige
- **THEN** ve que no quedan horarios ese día y un botón para ir al próximo día con horarios

### Requirement: Horarios en la zona del consultorio

Los horarios SHALL mostrarse en la zona horaria que devuelve la API (`zonaHoraria`), agrupados en mañana y tarde, y la página MUST aclarar la zona con el texto "Horarios en hora de …, la del consultorio". MUST NOT usarse la zona del dispositivo. Los inicios se envían a la API como el instante UTC recibido, sin reconstruirlo desde la hora local.

#### Scenario: Consultorio en otra zona

- **GIVEN** un consultorio con zona `America/Argentina/Cordoba` y un hueco a las `13:30Z`
- **WHEN** el paciente abre la página desde un dispositivo configurado en otra zona
- **THEN** ve el horario 10:30 y el aviso "Horarios en hora de Córdoba, la del consultorio"

#### Scenario: Envío del inicio elegido

- **WHEN** el paciente reserva el horario que la API devolvió como `2026-11-24T13:30:00Z`
- **THEN** la reserva se envía con ese mismo instante como inicio

### Requirement: Datos del paciente

El paso de datos SHALL pedir nombre, apellido, DNI, celular, email opcional y la aceptación de la política de privacidad, y MUST NOT pedir datos de salud. El DNI se acepta con o sin puntos y se envía solo con dígitos (7 u 8). La casilla de consentimiento MUST empezar sin marcar y su texto MUST ser el de la política de privacidad vigente. La página MUST validar antes de enviar y mostrar un resumen de errores con links a cada campo, más el error debajo de cada uno.

#### Scenario: DNI con puntos

- **WHEN** el paciente escribe el DNI `30.000.001`
- **THEN** la reserva se envía con el DNI `30000001`

#### Scenario: Datos inválidos antes de enviar

- **WHEN** el paciente envía con el DNI `12.34`, un celular de 3 dígitos y la casilla sin marcar
- **THEN** no se hace el pedido, arriba aparece un resumen con 3 errores enlazados a sus campos y cada campo muestra su error

#### Scenario: El backend rechaza el DNI

- **WHEN** la reserva responde 400 `DNI_INVALIDO`
- **THEN** el campo DNI se marca con el error correspondiente y el resto de los datos se conserva

### Requirement: Confirmación con el link de gestión

Al crearse el turno (201), la página SHALL mostrar el día, la hora, la prestación y el consultorio, que el turno queda reservado hasta que el consultorio lo confirme (RN-RP-10), y el link `linkGestion` destacado, avisando que no se vuelve a mostrar. MUST ofrecer copiar el link y descargar un archivo de calendario (`.ics`) que incluya el link. El token MUST NOT guardarse en `localStorage`, `sessionStorage`, IndexedDB ni cookies.

#### Scenario: Reserva exitosa

- **WHEN** la reserva responde 201 con un `linkGestion` `/t/{token}`
- **THEN** se ve "Guardá este link", el link completo y los botones "Copiar link" y "Agregar al calendario"
- **AND** el token no queda guardado en ningún almacenamiento del navegador

#### Scenario: Archivo de calendario

- **WHEN** el paciente toca "Agregar al calendario"
- **THEN** descarga un `.ics` con el inicio y el fin del turno en UTC, el nombre y la dirección del consultorio y el link de gestión en la descripción

### Requirement: Horario ocupado mientras el paciente elegía

Si la reserva responde 409 `HUECO_NO_DISPONIBLE`, la página SHALL volver al paso de horarios con los huecos actualizados que trae la respuesta, mostrar el horario elegido como no disponible (tachado, deshabilitado y con texto para lectores de pantalla) y conservar los datos que el paciente ya escribió.

#### Scenario: Alguien reservó las 10:30

- **GIVEN** el paciente eligió las 10:30 y completó sus datos
- **WHEN** la reserva responde 409 `HUECO_NO_DISPONIBLE` con una lista de huecos sin las 10:30
- **THEN** vuelve a los horarios con el aviso "Alguien reservó las 10:30 hace un instante", las 10:30 tachadas y sus datos intactos

### Requirement: Límite de turnos y demasiados pedidos

Un 409 `LIMITE_TURNOS` MUST mostrarse en el paso de datos con el mensaje de que ya tiene 2 turnos próximos y cómo cancelar uno o llamar, sin revelar fechas ni horarios de esos turnos. Un 429 MUST mostrar cuánto esperar según `Retry-After`.

#### Scenario: Tercer turno

- **WHEN** la reserva responde 409 `LIMITE_TURNOS`
- **THEN** se ve "Ya tenés 2 turnos próximos en este consultorio…" con el teléfono, sin datos de esos turnos

#### Scenario: Demasiadas consultas

- **WHEN** una consulta responde 429 con `Retry-After: 60`
- **THEN** se ve que hay que esperar un minuto y volver a intentar

### Requirement: Uso desde el celular y accesibilidad

La página MUST funcionar sin scroll horizontal desde 360 px de ancho, con objetivos táctiles de al menos 44 px, foco visible y estados que no dependan solo del color. En mobile, la acción principal SHALL quedar fija abajo sin tapar el último horario. Con `prefers-reduced-motion` no hay animaciones.

#### Scenario: Pantalla de 360 px

- **WHEN** la página se abre en un viewport de 360 px
- **THEN** no hay scroll horizontal, todos los botones miden al menos 44 px y al llegar al final de los horarios el último se ve completo arriba de la barra
