# Spec Delta

## Purpose

Define los datos ficticios que carga el perfil `local`, para mostrar y probar la v1 completa sin cargar datos reales y sin que lleguen a otro entorno.

## ADDED Requirements

### Requirement: Datos de demo solo en local
Los datos de demo MUST cargarse solo con el perfil `local`. Con cualquier otro perfil, incluidos los tests y producción, MUST NOT cargarse.

#### Scenario: Arranque local
- **WHEN** el backend arranca con el perfil `local` sobre una base vacía
- **THEN** quedan cargados los consultorios de demo con sus datos

#### Scenario: Otro perfil
- **WHEN** el backend arranca con el perfil por defecto
- **THEN** no existe ningún consultorio de demo

### Requirement: Agenda de demo completa
El consultorio de demo MUST tener turnos de la semana en curso en todos los estados (reservado, confirmado, cancelado y ausente) y con los dos orígenes (manual y link), al menos un sobreturno, un bloqueo, una novedad del paciente sin ver y eventos de auditoría de esos cambios.

#### Scenario: Recorrer la agenda de demo
- **WHEN** se inicia sesión con la cuenta de demo y se abre la agenda de la semana
- **THEN** se ven turnos de cada estado y de cada origen, un sobreturno, un bloqueo y la marca de novedad en un turno reservado por link

#### Scenario: Historial de un turno de demo
- **WHEN** se abre el historial de un turno de demo que fue reprogramado
- **THEN** el historial muestra sus eventos en orden

### Requirement: Casos especiales en la demo
Los datos de demo MUST incluir un paciente para revisar porque informó datos distintos al reservar, un turno con un link de gestión que se puede abrir en local, y un segundo consultorio con el plan vencido.

#### Scenario: Paciente para revisar
- **WHEN** se abre la lista de pacientes del consultorio de demo
- **THEN** hay un paciente marcado para revisar, con los datos que informó

#### Scenario: Link de gestión de demo
- **WHEN** se abre en local el link de gestión documentado para la demo
- **THEN** se ve el turno y se lo puede cancelar o reprogramar

#### Scenario: Consultorio vencido
- **WHEN** se inicia sesión con la cuenta del consultorio vencido
- **THEN** la app está en solo lectura y permite exportar los datos

### Requirement: Demo sin datos reales
Todos los datos de demo MUST ser ficticios y evidentes: teléfonos `+54 9 11 0000-00xx`, emails `@example.com` o `@consultorio.test`, y nombres que digan que son de prueba. Las contraseñas de demo MUST valer solo en local.

#### Scenario: Revisión del seed
- **WHEN** se revisan los datos de demo
- **THEN** ningún nombre, teléfono, email ni DNI corresponde a una persona real
