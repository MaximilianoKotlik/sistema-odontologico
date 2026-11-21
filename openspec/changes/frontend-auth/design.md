# Design

> **Borrador parcial.** Por ahora este documento solo registra el gate de mockups de C-07, que Octi prepara como trabajo anticipado. `proposal.md`, `specs/` y `tasks.md`, más el resto de este diseño (guardas de ruta, cliente de auth, refresh en memoria, tests), los completa Nacho cuando C-05 y C-06 estén `[x]`. El dominio `auth` es **CRITICO** (RN-AU-06): no se escribe código de producción sin aprobación explícita de Maxi.

## Context

- C-07 es el frontend de autenticación: registro con aceptación de términos, login, recuperación y confirmación, cambio de contraseña con sesión iniciada (US-005), cierre de sesión, guardas de ruta y restauración de sesión vía `POST /auth/refresh` (ver `CHANGES.md` §C-07).
- Contratos que consume:
  - C-04 (`auth-sesion-tokens`): `CREDENCIALES_INVALIDAS`, `EMAIL_EN_USO`, `PASSWORD_DEBIL`, `TERMINOS_NO_ACEPTADOS`, `SESION_EXPIRADA`, el logout con 204 y el registro sin tokens.
  - C-05 (`auth-recuperacion-hardening`, PR en borrador): 423 con `minutosRestantes`, token de recuperación en el fragmento `/recuperar#token=…`, `TOKEN_INVALIDO`, `PASSWORD_ACTUAL_INVALIDA` y 429 con `Retry-After`.
- Base visual: sistema de diseño rev. 1 y app-shell aprobado en C-06.

## Decisions

Las decisiones de UI que salen de los mockups están en `mockups/README.md` §Decisiones para revisar. Maxi resolvió las tres que afectaban a otros changes (revisión del PR #14, 2026-11-20):

- **Después del registro se entra directo**: el backend no cambia. Con el 201 de `POST /auth/registro`, el frontend llama a `POST /auth/login` con las mismas credenciales y entra a la app. Si ese login falla, lleva a `/login` con el email cargado y un aviso.
- **La ruta del login es `/login`**, igual que en el shell de C-06.
- **"Cambiar contraseña" va en el menú del shell**, arriba de "Cerrar sesión", en la barra lateral y en la hoja "Más". Toca el shell de C-06 (Nacho).

En la segunda revisión (PR #14, 2026-11-20) Maxi pidió además:

- **Tercer requisito de contraseña, solo cuando se pasa**: "Hasta 72 caracteres (las letras con tilde y la ñ cuentan doble)". C-04 (#16) rechaza con `PASSWORD_DEBIL` las contraseñas de más de 72 bytes en UTF-8, porque BCrypt no admite más. Estado `muy-larga` en registro, contraseña nueva y cambiar contraseña.
- **Nombre Muelas Felices** (DD-34) en todas las capturas, y links de términos y privacidad a `/terminos` y `/privacidad`.

## Open Questions

- Términos y condiciones y política de privacidad: ya hay borrador en `docs/legal/` (#24), marcado "pendiente de revisión legal" según DD-28. El registro no se publica con datos reales hasta que lo revise un abogado.

## UI Mockups

- Design system revision: 1
- Decision: yes (2026-11-20)
- Mockups: openspec/changes/frontend-auth/mockups/ (`login.html`, `registro.html`, `recuperar.html`, `recuperar-confirmar.html`, `cambiar-contrasena.html`, más `auth.css` y `mockup.js`)
- Approval: approved (2026-11-20, by Maxi), versión `3ffa2f7` (#14)
- Preview: rendered (2026-11-20); `mockups/previews/*-mobile-390x844.png`, `mockups/previews/*-desktop-1440x900.png`
- Visual verify: pending
