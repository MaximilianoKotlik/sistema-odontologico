# Spec Delta

## Purpose

Alta de la cuenta del odontólogo y de su consultorio desde la app, con los requisitos de contraseña visibles mientras escribe y la entrada automática al terminar (US-001, FL-01, RN-AU-01).

## ADDED Requirements

### Requirement: Formulario de registro
El registro SHALL pedir nombre, apellido, email, contraseña, nombre del consultorio y la aceptación de los términos y la política de privacidad, con links a `/terminos` y `/privacidad`, y MUST enviarlos a `POST /api/v1/auth/registro`.

#### Scenario: Alta válida
- **WHEN** el odontólogo completa todos los campos, acepta los términos y envía
- **THEN** se envía el registro y, con 201, empieza la entrada automática

### Requirement: Requisitos de contraseña en vivo
Mientras se escribe la contraseña, el registro SHALL mostrar los requisitos "Al menos 10 caracteres" y "Distinta de tu email", cada uno con ícono y texto según esté pendiente, cumplido o fallado, y nunca solo con color. El requisito "Hasta 72 caracteres (las letras con tilde y la ñ cuentan doble)" MUST aparecer solo cuando la contraseña pasa de 72 bytes en UTF-8.

#### Scenario: Contraseña corta
- **WHEN** el odontólogo escribe una contraseña de 6 caracteres
- **THEN** "Al menos 10 caracteres" se ve pendiente, con ícono y texto oculto para lectores de pantalla

#### Scenario: Más de 72 bytes
- **WHEN** la contraseña ocupa 73 bytes en UTF-8
- **THEN** aparece "Hasta 72 caracteres" en rojo con ✕ y el campo queda inválido

### Requirement: Validación antes de enviar
Con errores de formato (campos vacíos, email inválido, requisitos sin cumplir o términos sin aceptar), el registro MUST NOT enviar y SHALL mostrar un resumen arriba con links a cada campo, y el foco MUST ir al resumen.

#### Scenario: Términos sin aceptar
- **WHEN** el odontólogo envía sin marcar los términos
- **THEN** no se envía nada y el resumen lleva a la casilla de los términos

### Requirement: Email en uso
Ante 409 `EMAIL_EN_USO`, el registro SHALL marcar el campo email y ofrecer "Ingresá" y "restablecé tu contraseña", conservando lo cargado salvo la contraseña.

#### Scenario: Email ya registrado
- **WHEN** el registro responde 409 `EMAIL_EN_USO`
- **THEN** el email queda marcado con los links a ingresar y a recuperar la contraseña

### Requirement: Entrada automática después del registro
Con el 201 del registro, la app SHALL mostrar "¡Listo! Entrando a tu consultorio…" y llamar a `POST /api/v1/auth/login` con las mismas credenciales. Si el login falla, MUST llevar a `/login` con el email cargado y el aviso de que la cuenta está lista. La contraseña MUST descartarse de la memoria apenas termina el intento.

#### Scenario: Entrada directa
- **WHEN** el registro responde 201 y el login responde 200
- **THEN** el odontólogo entra a la app sin volver a escribir su contraseña

#### Scenario: Falla el login automático
- **WHEN** el registro responde 201 y el login falla
- **THEN** la app abre `/login` con el email cargado y "¡Tu consultorio está listo!" y "No pudimos abrir tu sesión automáticamente. Ingresá con el email y la contraseña que acabás de crear."
