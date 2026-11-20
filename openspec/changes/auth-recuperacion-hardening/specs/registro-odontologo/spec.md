# Spec Delta

## ADDED Requirements

### Requirement: Auditoría del alta de cuenta
Un registro exitoso SHALL registrar un evento `CUENTA_CREADA` del consultorio nuevo, en la misma transacción que el alta de consultorio y usuario, sin la contraseña ni su hash.

#### Scenario: Registro auditado
- **WHEN** se registra `odontologa@example.com` con datos válidos
- **THEN** el registro de auditoría contiene un evento `CUENTA_CREADA` del consultorio nuevo con el usuario como actor y la IP de origen
- **AND** ningún valor del evento contiene la contraseña ni su hash

#### Scenario: Registro rechazado no deja evento
- **WHEN** el registro falla con 409 `EMAIL_EN_USO`
- **THEN** no se registra ningún evento `CUENTA_CREADA`
