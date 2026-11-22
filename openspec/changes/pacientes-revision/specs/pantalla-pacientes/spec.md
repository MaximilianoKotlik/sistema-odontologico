# Spec Delta

## Purpose

Permite que el odontólogo se entere de que hay pacientes para revisar, vea qué difiere entre su ficha y lo que se escribió al reservar, y lo resuelva en un toque, sin que la ficha cambie hasta que él elija (US-070, RN-RP-03, DD-38).

## ADDED Requirements

### Requirement: Lista de pacientes con filtro de revisión

La pantalla `/app/pacientes` SHALL listar los pacientes ordenados por apellido, con DNI, teléfono y email, y SHALL ofrecer el filtro "Requieren revisión" (con la cantidad) y "Todos". Con `?requiereRevision=true` la pantalla MUST abrir con ese filtro activo. Los marcados MUST mostrar la etiqueta "Revisar" (con ícono y texto) y una línea con lo que se escribió en la reserva. La lista SHALL paginarse con "Mostrar más pacientes".

#### Scenario: Desde el aviso de la agenda

- **WHEN** el odontólogo abre `/app/pacientes?requiereRevision=true` con 3 pacientes marcados
- **THEN** ve el filtro "Requieren revisión" activo con el número 3, los 3 pacientes con "Revisar" y la línea "Escribió «Anna Ejemplo» al reservar el 18 de noviembre"

#### Scenario: Nada para revisar

- **WHEN** el filtro "Requieren revisión" no trae pacientes
- **THEN** se ve "No hay pacientes para revisar" con el botón para ver todos

### Requirement: Comparación con lo escrito en la reserva

La pantalla `/app/pacientes/{id}` de un paciente marcado SHALL mostrar solo los datos que difieren entre lo guardado y lo informado en la reserva, marcando el dato informado con fondo, ícono y la palabra "Distinto" (no solo color), y resumir en una línea los que coinciden. MUST aclarar que la ficha no cambió y mostrar el turno de la reserva.

#### Scenario: Solo difiere el nombre

- **GIVEN** la paciente "Ana Ejemplo" con el nombre informado "Anna" y el resto igual
- **WHEN** el odontólogo abre su revisión
- **THEN** ve la fila Nombre con "Ana" guardado y "Anna" en la reserva marcado como "Distinto", y la línea "Coinciden el apellido, el email y el teléfono"

### Requirement: Tres salidas sin confirmación

La revisión SHALL ofrecer tres acciones: "Mantener «…»" (confirmar la revisión), "Usar «…»" (corregir con lo informado) y "Corregir a mano" (formulario). Ninguna MUST pedir confirmación. Al terminar cualquiera, la app SHALL volver a la lista con el filtro de revisión, el aviso "Listo: … quedó revisada" y el contador actualizado.

#### Scenario: Usar lo de la reserva

- **WHEN** el odontólogo toca "Usar «Anna Ejemplo»"
- **THEN** se corrige el nombre a "Anna", vuelve a la lista con "Listo: Ejemplo, Anna quedó revisada" y el contador baja de 3 a 2

#### Scenario: Mantener lo guardado

- **WHEN** el odontólogo toca "Mantener «Ana Ejemplo»"
- **THEN** se confirma la revisión sin cambiar la ficha y vuelve a la lista con el contador en 2

### Requirement: Corrección a mano

"Corregir a mano" SHALL mostrar el formulario con nombre, apellido y email editables y precargados, y el DNI y el teléfono de solo lectura con su motivo. La pantalla MUST validar antes de enviar (nombre y apellido obligatorios, email con formato válido) y mostrar un resumen de errores con links a cada campo, más el error debajo de cada uno.

#### Scenario: Errores antes de enviar

- **WHEN** el odontólogo borra el nombre, escribe un email sin dominio y toca "Guardar y marcar revisado"
- **THEN** no se envía nada y se ve "Revisá 2 datos" con links a Nombre y Email, y el error debajo de cada uno

### Requirement: Errores y estados de la revisión

Un 404 MUST mostrar "No encontramos este paciente", igual para un id inexistente que para uno de otro consultorio. Un 409 `SIN_REVISION_PENDIENTE` MUST recargar el paciente y mostrar que ya no tiene revisión pendiente. Si guardar falla por otro motivo, la pantalla MUST mostrar que no se pudo guardar y que la ficha no cambió, sin perder lo escrito. Sin conexión, la lista y la revisión MUST mostrar el aviso y deshabilitar las acciones, sin datos guardados en el dispositivo.

#### Scenario: Otro consultorio

- **WHEN** el detalle responde 404
- **THEN** se ve "No encontramos este paciente" con el botón "Ir a pacientes"

#### Scenario: Ya lo revisó en otra pestaña

- **WHEN** la confirmación responde 409 `SIN_REVISION_PENDIENTE`
- **THEN** la pantalla muestra el paciente como revisado, sin las tres acciones

### Requirement: Otra reserva mientras se revisa

Las tres acciones SHALL enviar el `informadoAt` del detalle que se muestra. Un 409 `CONFLICTO_CONCURRENCIA` MUST recargar el paciente y mostrar la comparación con lo nuevo, con un aviso de que llegó otra reserva con datos distintos, sin perder lo escrito en "Corregir a mano".

#### Scenario: Llegó otra reserva mientras revisaba

- **GIVEN** la revisión abierta con el nombre informado "Anna"
- **WHEN** "Usar «Anna Ejemplo»" responde 409 `CONFLICTO_CONCURRENCIA` porque otra reserva informó "Ana María"
- **THEN** la pantalla recarga el paciente, muestra "Ana María" en la columna de la reserva con el aviso de que llegó otra reserva, y sigue con las tres acciones, ahora con "Usar «Ana María Ejemplo»"

### Requirement: Aviso de pacientes para revisar en la agenda

Si hay pacientes con revisión pendiente, la agenda SHALL mostrar un aviso compacto con la cantidad ("3 pacientes para revisar") y el link "Revisar pacientes" a la lista filtrada, pegado al aviso de cambios de los pacientes (DD-30) y con la misma jerarquía. El link MUST tener un área táctil de al menos 44 px. Sin pacientes para revisar, el aviso MUST NOT aparecer.

#### Scenario: Agenda con revisiones pendientes

- **GIVEN** 3 pacientes marcados para revisión
- **WHEN** el odontólogo abre la agenda
- **THEN** ve el aviso "3 pacientes para revisar" junto al de cambios, y "Revisar pacientes" lleva a `/app/pacientes?requiereRevision=true`

#### Scenario: Sin revisiones

- **WHEN** no hay pacientes marcados
- **THEN** la agenda no muestra el aviso

### Requirement: Accesibilidad de las pantallas de pacientes

Las pantallas de pacientes MUST funcionar sin scroll horizontal desde 360 px, con objetivos táctiles de al menos 44 px, foco visible, un solo `h1` y estados con ícono y texto. En mobile, la comparación se apila por dato.

#### Scenario: Revisión a 360 px

- **WHEN** la revisión se abre en un viewport de 360 px
- **THEN** no hay scroll horizontal y cada dato muestra "Guardado" y "En la reserva" uno debajo del otro
