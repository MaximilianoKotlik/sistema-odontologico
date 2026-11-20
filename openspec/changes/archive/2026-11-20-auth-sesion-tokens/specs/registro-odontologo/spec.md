# Spec Delta

## Purpose

Permite que un odontólogo independiente cree su cuenta y su consultorio por su cuenta, sin intervención del equipo, y empiece la prueba gratuita (US-001, RN-TE-02, RN-AU-01, RN-PL-02).

## ADDED Requirements

### Requirement: Alta de cuenta y consultorio en una sola operación
Al registrarse con nombre, apellido, email, contraseña válida, nombre del consultorio y aceptación de términos, el sistema SHALL crear un usuario y un consultorio vinculados 1:1 en una única transacción, con el consultorio en plan `PRUEBA` y `prueba_hasta` igual a la fecha actual en la zona del consultorio más 30 días.

#### Scenario: Registro exitoso
- **GIVEN** el email `odontologa@example.com` no está registrado y hoy es 2026-12-01 en `America/Argentina/Buenos_Aires`
- **WHEN** se registra con datos válidos y consultorio `Consultorio Demo Sur`
- **THEN** la respuesta es 201 con los ids de usuario y consultorio y el slug asignado
- **AND** el consultorio queda en `PRUEBA` con `prueba_hasta` 2026-12-31
- **AND** la respuesta no incluye tokens ni cookie de sesión

#### Scenario: Falla parcial no deja datos
- **WHEN** la creación del usuario falla después de crear el consultorio
- **THEN** no queda ni consultorio ni usuario persistidos

### Requirement: Email único en todo el sistema
El email MUST ser único sin distinguir mayúsculas; registrarse con un email existente MUST responder 409 `EMAIL_EN_USO` sin crear nada.

#### Scenario: Email repetido con otras mayúsculas
- **GIVEN** existe una cuenta con `odontologa@example.com`
- **WHEN** alguien se registra con `Odontologa@Example.com`
- **THEN** la respuesta es 409 con código `EMAIL_EN_USO` y la cantidad de consultorios no cambia

### Requirement: Política de contraseña
La contraseña MUST tener al menos 10 caracteres, como máximo 72 bytes en UTF-8 (el límite de BCrypt) y no ser igual al email (sin distinguir mayúsculas); si no cumple, el registro MUST responder 400 `PASSWORD_DEBIL`. La contraseña MUST guardarse solo como hash BCrypt de costo 12.

#### Scenario: Contraseña de más de 72 bytes
- **WHEN** se registra con una contraseña de 73 bytes, o de 37 letras `ñ` (74 bytes en UTF-8)
- **THEN** la respuesta es 400 con código `PASSWORD_DEBIL`, nunca 500

#### Scenario: Contraseña corta
- **WHEN** se registra con una contraseña de 9 caracteres
- **THEN** la respuesta es 400 con código `PASSWORD_DEBIL`

#### Scenario: Contraseña igual al email
- **WHEN** se registra con email `a.perez@example.com` y la misma cadena como contraseña
- **THEN** la respuesta es 400 con código `PASSWORD_DEBIL`

#### Scenario: Contraseña nunca en claro
- **WHEN** se registra una cuenta
- **THEN** la base guarda un hash BCrypt de costo 12 y ni la base, ni la respuesta, ni los logs contienen la contraseña

### Requirement: Slug sugerido único
El sistema SHALL asignar al consultorio un slug sugerido a partir de su nombre y, si ya está en uso, MUST agregar el sufijo numérico libre más bajo (`-2`, `-3`, …) respetando el máximo de 40 caracteres.

#### Scenario: Slug ya tomado
- **GIVEN** existen consultorios con slug `consultorio-demo-sur` y `consultorio-demo-sur-2`
- **WHEN** se registra otro consultorio llamado `Consultorio Demo Sur`
- **THEN** su slug es `consultorio-demo-sur-3`

### Requirement: Aceptación de términos versionada
El registro MUST exigir la aceptación explícita de los términos vigentes y MUST guardar la versión aceptada y el instante de aceptación; sin aceptación MUST responder 400 `TERMINOS_NO_ACEPTADOS`.

#### Scenario: Sin aceptar términos
- **WHEN** se registra con `aceptaTerminos` en `false` o ausente
- **THEN** la respuesta es 400 con código `TERMINOS_NO_ACEPTADOS` y no se crea nada

#### Scenario: Versión registrada
- **GIVEN** la versión vigente de términos es `2026-11-borrador`
- **WHEN** se registra aceptando términos
- **THEN** el usuario queda con `terminos_version` `2026-11-borrador` y el instante de aceptación del reloj del sistema

### Requirement: Datos obligatorios del registro
Nombre, apellido, email con formato válido y nombre del consultorio MUST ser obligatorios; si falta alguno o es inválido, la respuesta MUST ser 400 `VALIDACION` con el detalle por campo.

#### Scenario: Email mal formado
- **WHEN** se registra con email `no-es-un-email`
- **THEN** la respuesta es 400 con código `VALIDACION` y un error para el campo `email`
