# Proposal

> Change **C-07** de [CHANGES.md](../../../CHANGES.md) · Governance **CRITICO** (RN-AU-06) · Depende de `C-05` y `C-06` (las dos `[x]`) · Responsable: Nacho · Aprobación: Maxi.
>
> Los mockups ya están aprobados (Octi, #14, versión `3ffa2f7`). **No se escribe código de producción hasta que Maxi apruebe esta propuesta**, porque todo cambio en el dominio `auth`, incluido su frontend, requiere aprobación humana explícita.

## Why

El backend de autenticación ya está completo: registro, login, refresh rotado, logout, bloqueo, recuperación, cambio de contraseña y rate limit (C-04 y C-05). Pero la app no tiene cómo entrar. El shell de C-06 se monta sin sesión y `/login` no existe, así que nadie puede usar la agenda, la configuración (C-09) ni ninguna pantalla privada. C-07 conecta las pantallas aprobadas con esos contratos y decide lo más delicado del frontend: dónde vive el token, cómo se restaura la sesión al recargar, cómo se evita que dos pestañas cierren todas las sesiones y a qué rutas se puede volver después del login. Desbloquea C-09 y C-13.

## What Changes

- **Rutas públicas sin shell**: `/login`, `/registro`, `/recuperar` (pedido y confirmación con `#token=…`), `/terminos` y `/privacidad`. Comparten el layout de auth de los mockups.
- **Guardas de ruta**: toda ruta `/app/*` exige sesión. Sin sesión, la app lleva a `/login?volver=<ruta>`. Con sesión, las rutas de login y registro llevan a `/app`. Solo se vuelve a rutas internas `/app/*`: cualquier otro `volver` se descarta, para evitar redirecciones abiertas.
- **Restauración de sesión al recargar**: con una ruta privada y sin token en memoria, la app muestra "Abriendo tu agenda…" mientras pide `POST /api/v1/auth/refresh`. Si responde, monta el shell; si no, va al login sin mensaje de error.
- **Login**, con los mensajes de cada código: `CREDENCIALES_INVALIDAS` genérico (RN-AU-03), `CUENTA_BLOQUEADA` con los minutos restantes, 429 con el tiempo de `Retry-After`, sin conexión, sesión vencida, sesión cerrada y contraseña cambiada.
- **Registro**:
  - campos nombre, apellido, email, contraseña, nombre del consultorio y términos;
  - requisitos de contraseña en vivo (RN-AU-01), con el de 72 bytes solo cuando se pasa;
  - `EMAIL_EN_USO`;
  - **login automático** después del 201, con un fallback al login con el email cargado.
- **Recuperación**: pedido con la misma respuesta exista o no la cuenta (RN-AU-04), y contraseña nueva desde el link (`TOKEN_INVALIDO`, `PASSWORD_DEBIL`). El token se lee del fragmento y se borra de la URL apenas se lee.
- **Cambiar contraseña** dentro de la app (`/app/cuenta/contrasena`, US-005). Avisa que se cierra la sesión en todos los dispositivos y, al terminar, lleva a `/login`.
- **Cerrar sesión** desde el shell, que ya existe en C-06.
- **Menú del shell**: suma "Cambiar contraseña" arriba de "Cerrar sesión", en la barra lateral y en la hoja "Más" (decisión de Maxi en #14).
- **Refresh coordinado entre pestañas**: dos pestañas que renuevan a la vez se ven como reuso y revocan todas las sesiones (D4 de C-04). C-07 serializa el refresh entre pestañas, así la segunda usa la cookie ya rotada.
- **Tests Vitest**:
  - validaciones (email, requisitos, 72 bytes en UTF-8, términos);
  - guardas y saneamiento de `volver`;
  - restauración de sesión;
  - login automático y fallback;
  - mapeo de cada código a mensaje;
  - refresh serializado entre pestañas;
  - **que el access token nunca toca `localStorage`, `sessionStorage`, IndexedDB, cookies legibles ni la URL**.

**Fuera de alcance**:
- El asistente de primer ingreso (C-09): C-07 solo deja el punto de entrada después del login.
- 2FA (DD-18, DD-35).
- Verificación de email (DD-31).
- Baja de cuenta (DD-29).
- El contenido legal definitivo: `/terminos` y `/privacidad` muestran el borrador de `docs/legal/` (#24), marcado como pendiente de revisión legal (DD-28).

## Capabilities

### New Capabilities

- `app-auth/acceso`: login, guardas de ruta, restauración de sesión, vuelta a la ruta pedida, cierre de sesión y refresh coordinado entre pestañas.
- `app-auth/registro`: alta de cuenta y consultorio desde la app, con login automático.
- `app-auth/contrasena`: recuperación por email, contraseña nueva desde el link y cambio con la sesión iniciada.

### Modified Capabilities

- `app-shell`: la navegación suma "Cambiar contraseña" en el bloque de cuenta.
- `cliente-api`: el "solo un refresh" pasa a valer también entre pestañas del mismo navegador.

## Impact

- **Código**:
  - `frontend/src/features/auth/`: cliente de auth, validaciones, mensajes por código, sesión, guardas y coordinación entre pestañas;
  - `frontend/src/pages/` (login, registro, recuperar, recuperar-confirmar, cambiar-contraseña, términos y privacidad);
  - cambios acotados en `app.ts`, `router.ts` (rutas públicas y guarda), `http.ts` (refresh serializado) y `app/shell.ts` (ítem del menú).
- **API**: consume `POST /api/v1/auth/{registro,login,refresh,logout,cambiar-password}` y `POST /api/v1/auth/recuperacion[/confirmar]` (C-04 y C-05). No agrega endpoints ni cambia el backend.
- **Dependencias**: ninguna nueva. La coordinación entre pestañas usa Web Locks, una API nativa del navegador.
- **Seguridad (CRITICO)**: el access token sigue solo en memoria (FL-01), el refresh solo en cookie `HttpOnly` y el token de recuperación nunca queda en la URL ni en el historial. El design detalla cada decisión en D3 a D7 para la revisión de Maxi.
- **Coordinación**: C-09 se engancha después del login (D3 de C-09: sin prestaciones, el login lleva a `/app/bienvenida`).
