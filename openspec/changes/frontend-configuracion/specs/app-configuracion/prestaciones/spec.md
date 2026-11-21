# Spec Delta

## Purpose

Pantalla donde el odontólogo carga, edita, ordena, desactiva y reactiva los tipos de prestación que atiende con su duración (US-010).

## ADDED Requirements

### Requirement: Listado de prestaciones
La pestaña Prestaciones SHALL listar las prestaciones activas en su orden, cada una con nombre, duración en minutos y si se ofrece en la página pública, expresado con texto e ícono.

#### Scenario: Listado
- **GIVEN** "Consulta" de 30 min visible y "Control" de 20 min solo interna
- **WHEN** se abre la pestaña
- **THEN** se ven las dos en orden, "Control" con la etiqueta "Solo para vos"

### Requirement: Listado vacío
Sin prestaciones activas, la pestaña SHALL mostrar un estado vacío que explique que sin prestaciones no se pueden dar turnos, con el botón "Agregar prestación".

#### Scenario: Sin prestaciones
- **WHEN** el consultorio no tiene prestaciones activas
- **THEN** se ve el estado vacío con "Agregar prestación"

### Requirement: Alta y edición
El odontólogo SHALL poder agregar y editar una prestación en un diálogo con nombre, duración y "Mostrar en mi página pública". La duración MUST validarse antes de enviar: entre 5 y 480 minutos y múltiplo de 5.

#### Scenario: Alta
- **WHEN** el odontólogo agrega "Limpieza" de 45 min
- **THEN** se cierra el diálogo, la prestación aparece al final de la lista y se anuncia "Agregamos Limpieza"

#### Scenario: Duración no múltiplo de 5
- **WHEN** el odontólogo escribe 47 minutos
- **THEN** el campo queda marcado con "Usá múltiplos de 5 minutos (por ejemplo 45 o 50)" y no se envía

### Requirement: Nombre repetido
Ante 409 `PRESTACION_DUPLICADA`, el diálogo SHALL quedarse abierto y marcar el nombre con "Ya tenés una prestación con ese nombre".

#### Scenario: Duplicada
- **GIVEN** existe "Limpieza"
- **WHEN** el odontólogo agrega "LIMPIEZA"
- **THEN** el diálogo sigue abierto con el error en el nombre

### Requirement: Cambio de duración sin efecto en turnos
Al editar la duración, el diálogo SHALL avisar que el cambio aplica a los turnos nuevos y no a los que ya están dados (RN-AG-02).

#### Scenario: Aviso al cambiar duración
- **WHEN** el odontólogo cambia "Limpieza" de 45 a 60 min en el diálogo
- **THEN** ve el aviso de que los turnos ya dados conservan su duración

### Requirement: Orden
El odontólogo SHALL poder mover una prestación una posición arriba o abajo con botones, sin depender de arrastrar. El nuevo orden MUST guardarse y anunciarse.

#### Scenario: Subir
- **WHEN** el odontólogo toca "Subir" en la segunda prestación
- **THEN** pasa a ser la primera y se anuncia la nueva posición

### Requirement: Desactivar y reactivar
El odontólogo SHALL poder desactivar una prestación previa confirmación que explique que deja de ofrecerse y que sus turnos siguen vigentes (RN-AG-10). Las inactivas MUST verse en un grupo aparte con "Activar". La pantalla MUST NOT ofrecer una acción de borrar.

#### Scenario: Desactivar
- **WHEN** el odontólogo confirma desactivar "Blanqueamiento"
- **THEN** sale de la lista de activas y aparece en "Inactivas" con el botón "Activar"

#### Scenario: Reactivar
- **WHEN** el odontólogo toca "Activar" en "Blanqueamiento"
- **THEN** vuelve al final de la lista de activas

#### Scenario: Desactivar la última activa
- **GIVEN** una sola prestación activa
- **WHEN** el odontólogo pide desactivarla
- **THEN** la confirmación avisa que sin prestaciones activas no se pueden dar turnos nuevos
