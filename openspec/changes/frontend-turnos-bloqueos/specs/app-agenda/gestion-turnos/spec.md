# Spec Delta

## Purpose

Acciones del odontólogo sobre los turnos desde la app: alta de turnos y sobreturnos, cambios de estado, reprogramación, ajuste de duración y nota interna, con cada rechazo del backend explicado y con su salida (US-021 a US-025, FL-03).

## ADDED Requirements

### Requirement: Alta de turno
El odontólogo SHALL poder crear un turno eligiendo paciente, prestación, día y hora de inicio. La hora MUST elegirse en pasos de 5 minutos, y el fin MUST mostrarse calculado como inicio más la duración de la prestación, sin poder editarse.

#### Scenario: Turno desde un hueco libre
- **GIVEN** el hueco "Libre · 12:00 a 13:00" del viernes 20
- **WHEN** el odontólogo lo toca, elige un paciente y "Consulta · 30 min" y guarda
- **THEN** el formulario ya tenía viernes 20 a las 12:00, mostraba "Termina 12:30", y el turno aparece en la agenda como Reservado

### Requirement: Paciente existente o nuevo
El formulario SHALL buscar pacientes del consultorio por nombre, DNI o teléfono. Si el paciente no existe, SHALL permitir el alta rápida con nombre, apellido, DNI (7 u 8 dígitos), celular y email opcional, validados antes de enviar.

#### Scenario: Buscar por DNI
- **WHEN** el odontólogo escribe "30000003" en el buscador
- **THEN** aparece "Carla Ficticia · DNI 30.000.003" para elegir

#### Scenario: Alta rápida con DNI inválido
- **WHEN** el odontólogo carga un DNI de 5 dígitos
- **THEN** el campo dice que el DNI tiene 7 u 8 números y no se envía

### Requirement: Sobreturno
La casilla "Sobreturno" SHALL permitir crear un turno superpuesto o fuera del horario de atención, y MUST explicar que un sobreturno nunca puede caer en un bloqueo ni se ofrece al público (RN-AG-04).

#### Scenario: Urgencia superpuesta
- **GIVEN** un turno de 10:30 a 11:30
- **WHEN** el odontólogo crea un sobreturno a las 10:45 marcando la casilla
- **THEN** se crea y aparece en la agenda con la marca de sobreturno

### Requirement: Rechazos al crear o mover un turno
Ante 409 `TURNO_SOLAPADO` o `FUERA_DE_DISPONIBILIDAD`, el formulario SHALL quedarse abierto con lo cargado, explicar el motivo y ofrecer "Crear como sobreturno". Ante 409 `HORARIO_BLOQUEADO`, MUST explicar que el horario está bloqueado y no ofrecer el sobreturno.

#### Scenario: Turno solapado
- **WHEN** el odontólogo crea un turno a las 10:15 que choca con el de 10:30
- **THEN** ve "Se superpone con el turno de 10:30 de Carla Ficticia" y el botón "Crear como sobreturno"

#### Scenario: Dentro de un bloqueo
- **WHEN** la respuesta es 409 `HORARIO_BLOQUEADO`
- **THEN** ve que el horario está bloqueado y no aparece la opción de sobreturno

### Requirement: Cambios de estado válidos
El detalle SHALL ofrecer solo las transiciones válidas (RN-AG-06). Confirmar MUST ser directo, con un aviso al terminar. Cancelar MUST pedir confirmación, con un motivo opcional de hasta 200 caracteres. Marcar ausente MUST pedir confirmación y solo ofrecerse si el turno ya empezó.

#### Scenario: Cancelar con motivo
- **WHEN** el odontólogo cancela un turno escribiendo "Lo pidió por teléfono"
- **THEN** el turno queda Cancelado y el detalle muestra "Cancelado por vos" con el motivo

#### Scenario: Transición rechazada
- **WHEN** la respuesta a un cambio de estado es 409 `TRANSICION_INVALIDA`
- **THEN** se avisa que el turno cambió de estado, se recarga el detalle y no se pierde nada

### Requirement: Reprogramar
Reprogramar SHALL permitir elegir día y hora de inicio, MUST mostrar los huecos libres de ese día que alcanzan para la duración del turno, y MUST conservar la duración y mostrar el fin nuevo (RN-AG-11, RN-AG-12).

#### Scenario: Elegir un hueco
- **GIVEN** un turno de 30 minutos
- **WHEN** el odontólogo abre Reprogramar y toca el hueco de las 16:30 del viernes
- **THEN** el formulario muestra "16:30 a 17:00" y al guardar el turno se mueve con su mismo detalle

### Requirement: Ajustar duración
Ajustar duración SHALL cambiar la duración en pasos de 5 minutos, entre 5 y 480, con botones −5 y +5 y atajos, y MUST mostrar el fin nuevo antes de guardar. Ante 409 `TURNO_SOLAPADO` o `HORARIO_BLOQUEADO`, MUST mantener la duración anterior y explicar con qué choca.

#### Scenario: Alargar a 45 minutos
- **GIVEN** un turno de 10:00 a 10:30
- **WHEN** el odontólogo toca +5 tres veces
- **THEN** ve "45 min · termina 10:45" y al guardar la agenda lo muestra hasta las 10:45

### Requirement: Nota interna
El editor de nota SHALL mostrar un contador de caracteres sobre 500, MUST impedir guardar por encima del límite y MUST recordar que el paciente no la ve.

#### Scenario: Nota demasiado larga
- **WHEN** la nota llega a 501 caracteres
- **THEN** el contador queda marcado con "1 carácter de más" y "Guardar" no envía

### Requirement: Guardado concurrente
Ante 409 `CONFLICTO_CONCURRENCIA`, la acción SHALL avisar que el turno cambió mientras se editaba y ofrecer ver la versión actual, sin aplicar el cambio.

#### Scenario: Turno editado en otra pestaña
- **WHEN** la respuesta es 409 `CONFLICTO_CONCURRENCIA`
- **THEN** se ve el aviso con "Ver el turno actualizado"

### Requirement: Sin conexión o plan vencido
Sin conexión, las acciones SHALL estar deshabilitadas (C-13). Ante 403 `PLAN_VENCIDO`, MUST explicar que la agenda está en solo lectura hasta activar el plan.

#### Scenario: Plan vencido
- **WHEN** la respuesta a crear un turno es 403 `PLAN_VENCIDO`
- **THEN** el formulario dice que la agenda está en solo lectura y no muestra un error genérico
