# app-configuracion/consultorio Specification

## Purpose
Pantalla donde el odontólogo consulta y edita los datos de su consultorio, su link público y las reglas con las que se toman reservas (US-004).

## Requirements

### Requirement: Secciones de Configuración
`/app/configuracion` SHALL mostrar tres pestañas: Consultorio (`/app/configuracion`), Prestaciones (`/app/configuracion/prestaciones`) y Horarios (`/app/configuracion/horarios`). La pestaña activa MUST marcarse con algo más que color y cambiar de pestaña MUST cambiar la URL sin recargar la página.

#### Scenario: Ir a Horarios
- **WHEN** el odontólogo toca la pestaña "Horarios"
- **THEN** la URL pasa a `/app/configuracion/horarios`, se muestra el editor de horarios y la pestaña queda marcada como actual

### Requirement: Formulario del consultorio
La pestaña Consultorio SHALL mostrar los valores actuales de nombre, slug, dirección, teléfono de contacto, anticipación de cancelación, anticipación de reserva, horizonte de reserva, paso de huecos, reserva pública y zona horaria, y guardarlos con un solo botón.

#### Scenario: Guardado correcto
- **WHEN** el odontólogo cambia la anticipación de cancelación a 48 horas y guarda
- **THEN** se envía 2880 minutos, se muestra "Guardamos los cambios" y el formulario muestra el valor nuevo

### Requirement: Unidades legibles
Las anticipaciones SHALL editarse en horas, el horizonte en días y el paso de huecos con las opciones 5, 10, 15, 20 y 30 minutos. La pantalla MUST convertir a minutos al enviar y desde minutos al mostrar.

#### Scenario: Anticipación en horas
- **GIVEN** la API devuelve una anticipación de cancelación de 1440 minutos
- **WHEN** se muestra el formulario
- **THEN** el campo dice 24 horas

### Requirement: Link público
La pestaña SHALL mostrar el link público completo con el slug actual y un botón "Copiar link". Mientras el odontólogo edita el slug, la vista previa del link MUST actualizarse y avisar que el link anterior deja de funcionar al guardar (RN-TE-03).

#### Scenario: Cambio de slug pendiente
- **WHEN** el odontólogo escribe `dra-ejemplo` en el slug sin guardar
- **THEN** la vista previa muestra `/c/dra-ejemplo` y el aviso de que el link anterior va a dejar de funcionar

### Requirement: Slug en uso con sugerencias
Ante 409 `SLUG_EN_USO`, la pantalla SHALL marcar el campo del slug y mostrar las sugerencias de la respuesta como opciones que se eligen con un toque. `SLUG_RESERVADO` y `SLUG_INVALIDO` MUST mostrarse junto al campo con el motivo.

#### Scenario: Elegir una sugerencia
- **GIVEN** la respuesta 409 con sugerencias `dra-ejemplo-2` y `dra-ejemplo-3`
- **WHEN** el odontólogo toca `dra-ejemplo-2`
- **THEN** el campo del slug pasa a `dra-ejemplo-2` y se quita la marca de error

#### Scenario: Palabra reservada
- **WHEN** el guardado responde 400 `SLUG_RESERVADO`
- **THEN** el campo del slug dice que esa dirección está reservada y pide elegir otra

### Requirement: Validación antes de enviar
La pantalla MUST validar antes de enviar el formato del slug, el nombre obligatorio de hasta 120 caracteres, el teléfono obligatorio, la anticipación de cancelación de 0 a 168 horas, la de reserva de 0 o más y el horizonte de 1 a 365 días. Con errores, MUST mostrar un resumen arriba con links a cada campo y no enviar.

#### Scenario: Horizonte fuera de rango
- **WHEN** el odontólogo escribe 400 días en el horizonte y guarda
- **THEN** no se envía nada, el campo queda marcado y el resumen de errores lleva a ese campo

#### Scenario: Teléfono vacío
- **WHEN** el odontólogo borra el teléfono de contacto y guarda
- **THEN** no se envía nada y el campo del teléfono dice que es obligatorio

### Requirement: Anticipación de reserva menor que el horizonte
La anticipación de reserva MUST ser menor que el horizonte (días × 24 horas); si no, la pantalla MUST marcar el campo de anticipación de reserva y no enviar. El mismo error devuelto por la API MUST mostrarse en ese campo.

#### Scenario: Anticipación igual o mayor que el horizonte
- **GIVEN** un horizonte de 60 días
- **WHEN** el odontólogo pone 1500 horas de anticipación de reserva y guarda
- **THEN** no se envía nada y el campo de anticipación dice que tiene que ser menor que 1440 horas

### Requirement: Errores de la API por campo
Los errores 400 de la API (`VALIDACION` con detalle por campo, `TELEFONO_INVALIDO`, `ZONA_HORARIA_INVALIDA`) SHALL mostrarse junto al campo correspondiente, conservando lo que escribió el odontólogo.

#### Scenario: Teléfono inválido
- **WHEN** el guardado responde 400 `TELEFONO_INVALIDO`
- **THEN** el campo del teléfono queda marcado con un mensaje y un ejemplo de formato válido

### Requirement: Reserva pública pausada
La pantalla SHALL permitir activar o pausar la reserva pública con un interruptor que dice su estado en texto. Al pausarla, MUST explicar que los pacientes no van a poder reservar desde el link hasta reactivarla.

#### Scenario: Pausar
- **WHEN** el odontólogo apaga "Reserva pública" y guarda
- **THEN** se envía `reservaPublicaActiva: false` y el interruptor dice "Pausada"

### Requirement: Guardado concurrente
Ante 409 `CONFLICTO_CONCURRENCIA`, la pantalla SHALL avisar que la configuración cambió en otra pestaña o dispositivo y ofrecer recargar los valores actuales.

#### Scenario: Dos pestañas
- **WHEN** el guardado responde 409 `CONFLICTO_CONCURRENCIA`
- **THEN** se muestra el aviso con el botón "Ver los valores actuales"

### Requirement: Cambios sin guardar
Si el odontólogo tiene cambios sin guardar e intenta salir de la pestaña, la pantalla MUST pedir confirmación antes de descartarlos.

#### Scenario: Salir con cambios
- **GIVEN** el nombre del consultorio editado sin guardar
- **WHEN** el odontólogo toca "Agenda" en la navegación
- **THEN** se pregunta si quiere descartar los cambios y, si cancela, se queda en Configuración
