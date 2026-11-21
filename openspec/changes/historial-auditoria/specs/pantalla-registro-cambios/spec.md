# Spec Delta

## Purpose

Muestra al odontólogo, en lenguaje simple, quién cambió qué y cuándo: en el detalle de cada turno y en la sección "Registro de cambios" de todo el consultorio, con filtros y paginación (US-040, RN-AD-06).

## ADDED Requirements

### Requirement: Historial en el detalle del turno
El detalle del turno SHALL mostrar una tarjeta "Historial" con los eventos del turno en orden cronológico, como línea de tiempo: fecha y hora en la zona del consultorio, quién lo hizo, qué hizo y el "antes → después" de los campos que cambiaron. Debe incluir un link "Ver en el registro" filtrado por ese turno.

#### Scenario: Turno reservado y reprogramado
- **GIVEN** un turno que el paciente reservó por link y que después el odontólogo movió de las 10:00 a las 11:00
- **WHEN** el odontólogo abre el detalle
- **THEN** la tarjeta "Historial" muestra "Reservó por tu link" con el nombre del paciente, y debajo "Reprogramaste el turno · 10:00 → 11:00"

#### Scenario: Error al cargar el historial
- **WHEN** falla la carga del historial
- **THEN** el resto del detalle se ve igual y la tarjeta muestra "No pudimos cargar el historial" con un botón "Reintentar"

### Requirement: Sección Registro de cambios
La sección `/app/auditoria` SHALL listar los eventos del consultorio del más nuevo al más viejo, agrupados por día. Cada fila muestra la hora, el actor, la acción en español, el contexto (por ejemplo, el turno y el paciente) y el cambio. Reemplaza la sección pendiente del app-shell.

#### Scenario: Entrar a la sección
- **WHEN** el odontólogo abre `/app/auditoria`
- **THEN** ve los últimos eventos agrupados por día, y ya no ve "Esta sección todavía no está disponible"

### Requirement: Filtros en la URL
La sección SHALL ofrecer filtros por rango de fechas y por tipo de acción (agrupados por tema: turnos, bloqueos, configuración, pacientes, accesos y exportaciones). El filtro por turno llega desde el detalle y se muestra como un chip que se puede quitar. Los filtros MUST reflejarse en la URL, para que el botón Atrás y recargar la página los conserven.

#### Scenario: Llegar desde el detalle
- **WHEN** el odontólogo toca "Ver en el registro" en un turno
- **THEN** abre `/app/auditoria?turnoId=…` con el chip del turno y solo sus eventos

#### Scenario: Quitar un filtro
- **WHEN** quita el chip del turno
- **THEN** la URL deja de tener `turnoId` y se ven los eventos de todo el consultorio

#### Scenario: Rango invertido
- **WHEN** elige un "hasta" anterior al "desde"
- **THEN** ve el error "Tiene que ser igual o posterior a "Desde"" debajo del campo "Hasta" y no se hace el pedido

### Requirement: Paginación y estados de la lista
La lista SHALL cargar 50 eventos y ofrecer "Cargar más" mientras haya cursor. MUST mostrar estados distintos para cargando, sin eventos todavía, sin resultados para los filtros (con "Quitar filtros"), error con "Reintentar" y sin conexión.

#### Scenario: Cargar más
- **GIVEN** más de 50 eventos
- **WHEN** toca "Cargar más"
- **THEN** se agregan los siguientes al final y el foco queda en el primer evento nuevo

#### Scenario: Filtros sin resultados
- **WHEN** los filtros no coinciden con ningún evento
- **THEN** ve "No hay cambios con estos filtros" y un botón "Quitar filtros"

### Requirement: Cambios legibles
Los valores anterior y nuevo MUST mostrarse con etiquetas en español y en el formato de la UI: instantes en la zona del consultorio, estados con su nombre, duraciones en minutos y booleanos como "Sí" o "No". Un campo sin etiqueta conocida MUST mostrarse con su nombre técnico, sin romper la fila.

#### Scenario: Cambio de estado
- **WHEN** se muestra un `TURNO_ESTADO_CAMBIADO` de `RESERVADO` a `CANCELADO` por el paciente
- **THEN** la fila dice "Canceló por el link" con el paciente como actor, y "Reservado → Cancelado"

#### Scenario: Campo desconocido
- **GIVEN** un evento con un campo `campo_nuevo` que la UI no conoce
- **WHEN** se muestra
- **THEN** la fila muestra `campo_nuevo` con su valor anterior y nuevo

### Requirement: Accesibilidad del registro
La sección y la tarjeta MUST cumplir AA: la lista como lista semántica con encabezados por día, los "antes → después" leíbles por lectores de pantalla ("antes 10:00, después 11:00"), objetivos táctiles de al menos 44 px, foco visible, y estados de actor y acción con texto, no solo color.

#### Scenario: Lector de pantalla en un cambio
- **WHEN** un lector de pantalla lee una reprogramación
- **THEN** anuncia "Inicio: antes 10:00, después 11:00"
