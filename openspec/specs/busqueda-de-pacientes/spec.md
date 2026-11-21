# busqueda-de-pacientes Specification

## Purpose
Define cómo el odontólogo encuentra a un paciente de su consultorio por nombre, apellido, DNI o teléfono, para cargarle un turno (FL-03, C-14 D4).

## Requirements

### Requirement: Buscar pacientes
El odontólogo SHALL buscar los pacientes de su consultorio con un texto de al menos 3 caracteres. Un texto de solo dígitos (sin contar puntos, espacios, `+` ni guiones) MUST buscar el DNI que empieza con esos dígitos o el teléfono que los contiene. Un texto con letras MUST buscar el nombre o el apellido que empiezan con alguna de sus palabras, sin distinguir mayúsculas ni tildes. La búsqueda MUST devolver como máximo 20 pacientes.

#### Scenario: Sin tildes
- **GIVEN** el paciente "José Pérez"
- **WHEN** el odontólogo busca "perez"
- **THEN** la respuesta trae a "José Pérez"

#### Scenario: Por apellido
- **GIVEN** los pacientes "Ana Prueba" y "Carla Ficticia"
- **WHEN** el odontólogo busca "fict"
- **THEN** la respuesta trae solo a "Carla Ficticia"

#### Scenario: Por DNI con puntos
- **GIVEN** un paciente con DNI `90000001`
- **WHEN** el odontólogo busca "90.000"
- **THEN** la respuesta trae a ese paciente

#### Scenario: Por teléfono
- **GIVEN** un paciente con teléfono `+5491100000001`
- **WHEN** el odontólogo busca "0000001"
- **THEN** la respuesta trae a ese paciente

#### Scenario: Texto demasiado corto
- **WHEN** el odontólogo busca "an"
- **THEN** la respuesta es 400 con código `VALIDACION`

### Requirement: Búsqueda aislada y sin datos en los logs
La búsqueda MUST devolver solo pacientes del consultorio de la sesión. El texto buscado MUST NOT quedar en los logs, porque puede ser un DNI o un teléfono (RN-GL-01).

#### Scenario: Paciente de otro consultorio
- **GIVEN** un paciente "Ana Prueba" solo en el consultorio B
- **WHEN** el odontólogo del consultorio A busca "prueba"
- **THEN** la respuesta no trae a ese paciente

#### Scenario: DNI fuera del log
- **WHEN** el odontólogo busca "90000001"
- **THEN** el log de acceso de la petición no contiene "90000001"
