# Spec Delta

## Purpose

Editor de la grilla semanal donde el odontólogo define en qué franjas de cada día atiende, para que solo se den turnos dentro de ese horario (US-011).

## ADDED Requirements

### Requirement: Grilla por día
La pestaña Horarios SHALL mostrar los siete días de lunes a domingo, cada uno con sus franjas ordenadas por hora de inicio, o "No atendés" si no tiene ninguna. Las horas MUST mostrarse en formato de 24 h según la zona horaria del consultorio.

#### Scenario: Grilla cargada
- **GIVEN** lunes 09:00–13:00 y 15:00–19:00
- **WHEN** se abre la pestaña
- **THEN** el lunes muestra las dos franjas y los días sin franjas dicen "No atendés"

### Requirement: Edición de franjas
El odontólogo SHALL poder agregar, cambiar y quitar franjas de un día. Las horas MUST elegirse en pasos de 5 minutos.

#### Scenario: Agregar franja
- **WHEN** el odontólogo toca "Agregar franja" en el martes
- **THEN** aparece una franja nueva con horas editables en pasos de 5 minutos

### Requirement: Copiar un día a otros
El odontólogo SHALL poder copiar las franjas de un día a otros días que elija, reemplazando las de esos días.

#### Scenario: Copiar lunes al resto de la semana
- **WHEN** el odontólogo copia el lunes al martes, miércoles, jueves y viernes
- **THEN** esos cuatro días quedan con las mismas franjas que el lunes, todavía sin guardar

### Requirement: Validación de franjas antes de guardar
Antes de enviar, la pantalla MUST marcar en su día cada franja con fin igual o anterior al inicio y cada par de franjas solapadas del mismo día, y no enviar. Franjas contiguas MUST aceptarse.

#### Scenario: Solapadas
- **WHEN** el lunes tiene 09:00–13:00 y 12:00–15:00 y el odontólogo guarda
- **THEN** no se envía nada, el lunes queda marcado con "Las franjas 09:00–13:00 y 12:00–15:00 se superponen" y el foco va al primer error

#### Scenario: Contiguas
- **WHEN** el lunes tiene 09:00–13:00 y 13:00–15:00 y el odontólogo guarda
- **THEN** se envía la grilla

### Requirement: Guardado de la grilla completa
El botón "Guardar horarios" SHALL enviar todas las franjas de la semana juntas, y la pantalla MUST indicar con texto cuándo hay cambios sin guardar.

#### Scenario: Guardar
- **WHEN** el odontólogo cambia el viernes y guarda
- **THEN** se envía la grilla de los siete días y se muestra "Guardamos tus horarios"

#### Scenario: Cambios pendientes
- **WHEN** el odontólogo quita una franja sin guardar
- **THEN** la barra de guardado dice "Tenés cambios sin guardar"

### Requirement: Errores de la API en su día
Ante 400 `FRANJAS_SOLAPADAS`, la pantalla SHALL marcar los días que informa la respuesta. Ante 400 `FRANJA_INVALIDA`, MUST mostrar un aviso general y conservar la grilla editada.

#### Scenario: Solapamiento detectado por la API
- **WHEN** el guardado responde 400 `FRANJAS_SOLAPADAS` con `dias: [3]`
- **THEN** el miércoles queda marcado con el mensaje de franjas superpuestas

### Requirement: Semana sin horarios
Si el odontólogo guarda la grilla sin ninguna franja, la pantalla MUST pedir confirmación avisando que los pacientes no van a poder reservar hasta cargar horarios.

#### Scenario: Vaciar la grilla
- **WHEN** el odontólogo quita todas las franjas y guarda
- **THEN** se pide confirmación antes de enviar la grilla vacía
