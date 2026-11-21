# Mockups de C-07 · `frontend-auth`

`Approval: approved (2026-11-20, by Maxi)`, versión `3ffa2f7` (#14)

- Design system revision: 1
- Preview: rendered (2026-11-20) · 390×844 y 1440×900, página completa
- Preparado por: Octi (trabajo anticipado). Implementa: Nacho, cuando C-05 y C-06 estén `[x]`.

Mockups de revisión de todas las pantallas de autenticación. **No es código de producción**: no hay cambios en `backend/` ni `frontend/`. El change es **CRITICO** (RN-AU-06), así que Maxi tiene que aprobarlo explícitamente antes de que se escriba código.

## Brief

- **Audiencia**: el odontólogo independiente. Entra sobre todo desde el celular, entre pacientes.
- **Tarea**: crear la cuenta y el consultorio, ingresar, recuperar el acceso si se olvida la contraseña, cambiarla con la sesión iniciada y cerrar sesión. Que cada error explique qué hacer sin revelar si un email tiene cuenta, salvo donde la KB lo exige (US-001 `EMAIL_EN_USO` y US-002 `CUENTA_BLOQUEADA`).
- **Responsive**: una columna en mobile. Desde 900 px, las pantallas de ingreso y registro suman un panel de marca a la izquierda con tres afirmaciones respaldadas por la KB (agenda, link público, prueba sin tarjeta) y el precio de DD-22. Recuperación y contraseña nueva quedan en una columna centrada, sin distracciones. Cambiar contraseña vive dentro del app-shell aprobado en C-06.
- **Tokens y assets**: importan `design-system/tokens.css` y `design-system/brand.js`. Los estilos compartidos están en `auth.css`, que solo usa variables del sistema de diseño (rev. 1, sin tokens nuevos).

## Pantallas y estados

Cada archivo elige el estado con `?estado=…`. Al pie de cada pantalla hay una franja "Estados del mockup" con links a todos los estados; esa franja existe solo en el mockup.

| Pantalla | Archivo | Estados (`?estado=`) |
|---|---|---|
| Login (`/login`) | `login.html` | `normal`, `enviando`, `credenciales` (401 genérico), `bloqueada` (423 con minutos restantes), `limite` (429), `sin-conexion`, `registro-sin-login` (fallback si falla el login automático), `sesion-cerrada`, `sesion-vencida` (refresh fallido), `contrasena-cambiada`, `restaurando` (refresh al recargar) |
| Crear cuenta (`/registro`) | `registro.html` | `normal`, `escribiendo` (requisitos en vivo), `validacion` (resumen de errores + campos), `email-en-uso` (409), `muy-larga` (más de 72 bytes), `enviando`, `entrando` (login automático después del 201), `limite` (429) |
| Pedir recuperación (`/recuperar`) | `recuperar.html` | `normal`, `enviando`, `enviado` (202, mismo mensaje exista o no la cuenta), `limite` (429) |
| Contraseña nueva (`/recuperar#token=…`) | `recuperar-confirmar.html` | `normal`, `no-coinciden`, `password-debil` (400), `muy-larga` (más de 72 bytes), `enviando`, `token-invalido` (400, un solo mensaje para vencido, usado o revocado) |
| Cambiar contraseña (dentro de la app) | `cambiar-contrasena.html` | `normal`, `actual-invalida` (400), `password-debil` (400), `muy-larga` (más de 72 bytes), `enviando`, `limite` (429), `mas` (hoja "Más" en mobile) |

**Cerrar sesión** no tiene pantalla propia: es el botón que ya está en el app-shell de C-06. Llama a `POST /auth/logout` y lleva a `/login` (`login.html?estado=sesion-cerrada`). No pide confirmación porque no se pierde nada y se vuelve a entrar en un paso.

## Decisiones para revisar

1. **Mensajes por código de error** (contratos de C-04 y C-05):

   | Código | HTTP | Dónde | Texto |
   |---|---|---|---|
   | `CREDENCIALES_INVALIDAS` | 401 | Login, aviso arriba | "Email o contraseña incorrectos." Nunca marca un campo puntual (RN-AU-03). La contraseña se vacía; el email se conserva. |
   | `CUENTA_BLOQUEADA` | 423 | Login, aviso arriba | "Pausamos el ingreso por seguridad", con los minutos restantes que devuelve el backend (`minutosRestantes`, C-05 D1) y un link para restablecer la contraseña. |
   | 429 + `Retry-After` | 429 | Todas las pantallas públicas y cambiar contraseña | Aviso con el tiempo de espera calculado desde `Retry-After`. |
   | `EMAIL_EN_USO` | 409 | Registro, debajo del email | Ofrece "Ingresá" o "restablecé tu contraseña". |
   | `PASSWORD_DEBIL` | 400 | Registro, contraseña nueva y cambiar contraseña | Requisito en rojo con ícono ✕ + campo inválido. |
   | `TERMINOS_NO_ACEPTADOS` | 400 | Registro | Se valida antes de enviar. Si igual llega del backend, se muestra igual que en `validacion`. |
   | `TOKEN_INVALIDO` | 400 | Contraseña nueva | Pantalla "Este link ya no sirve" con "Pedir un link nuevo". |
   | `PASSWORD_ACTUAL_INVALIDA` | 400 | Cambiar contraseña, debajo del campo actual | "La contraseña actual no es correcta." |
   | `SESION_EXPIRADA` | 401 | Login | Aviso "Tu sesión venció"; después del login vuelve a la ruta donde estaba. |

2. **Requisitos de contraseña en vivo** (RN-AU-01): ícono + texto (círculo pendiente, ✓ cumplido, ✕ falta) y texto oculto para lectores de pantalla; nunca solo color. En "contraseña nueva" desde el link de recuperación no se conoce el email, así que "Distinta del email de tu cuenta" queda como indicación fija y la valida el backend.
   **Tope de 72 bytes** (pedido por Maxi en la segunda revisión): C-04 rechaza con `PASSWORD_DEBIL` las contraseñas de más de 72 bytes en UTF-8, porque BCrypt no admite más. Para no sumar ruido, el tercer requisito, "Hasta 72 caracteres (las letras con tilde y la ñ cuentan doble)", aparece **solo cuando se pasa**, en rojo con ✕ y el campo inválido (estado `muy-larga`). El frontend lo calcula con `new TextEncoder().encode(valor).length > 72`.
3. **Después del registro se entra directo** (decidido por Maxi, 2026-11-20). El backend no cambia: apenas recibe el 201 de `POST /auth/registro`, el frontend llama a `POST /auth/login` con las mismas credenciales (estado `entrando` del registro) y entra a la app; como es el primer ingreso sin prestaciones, FL-01 lo lleva al onboarding de C-09. Si ese login falla, va a `/login` con el email cargado y el aviso del estado `registro-sin-login`.
4. **Cambiar contraseña en la navegación** (confirmado por Maxi, 2026-11-20): **"Cambiar contraseña"** va en el bloque de cuenta, arriba de "Cerrar sesión", en la barra lateral (desktop) y en la hoja "Más" (mobile). Toca el app-shell de C-06: se le avisó a Nacho en el PR.
5. **Al cambiar o restablecer la contraseña** se avisa antes de enviar que se cierra la sesión en todos los dispositivos, también en este (RN-AU-05, US-005). Al terminar, se lleva a `/login` (`login.html?estado=contrasena-cambiada`).
6. **Restauración de sesión**: al recargar, mientras corre `POST /auth/refresh`, se muestra "Abriendo tu agenda…" (`aria-busy`). Si falla, se va al login sin aviso de error. El access token vive solo en memoria (FL-01).
7. **Sin conexión**: el login necesita red. Se avisa y el botón queda deshabilitado, coherente con el aviso offline de C-06.

## (TBD) y datos ficticios

- Nombre: `BRAND_NAME` de `design-system/brand.js` ("Muelas Felices", DD-34). Logo provisorio: el isotipo de diente de C-06.
- Los links a **términos y condiciones** y **política de privacidad** apuntan a `/terminos` y `/privacidad`. El texto sale del borrador de `docs/legal/` (#24), marcado "pendiente de revisión legal"; según DD-28, después lo revisa un abogado. El registro no se publica con datos reales sin esa revisión (C-04 guarda la versión aceptada).
- Datos ficticios: "Ana Ejemplo", `odontologa@example.com`, "Consultorio Demo Sur", "Dra. Ejemplo · Consultorio Demo". Las contraseñas de ejemplo son cadenas sin uso real.
- Los minutos de espera (12, 1, 45, 40 y 15) son ejemplos; en producción salen de la respuesta del backend.

## Cómo verlo

Abrí cualquier `.html` directo en el navegador: funciona con `file://`. También podés servir el repo desde la raíz con cualquier servidor estático, por ejemplo:

```bash
npx --yes http-server -p 8765 -a 127.0.0.1
```

Después navegá a `http://127.0.0.1:8765/openspec/changes/frontend-auth/mockups/login.html` y usá la franja de estados.

## Capturas

`previews/<pantalla>-<estado>-mobile-390x844.png` y `previews/<pantalla>-<estado>-desktop-1440x900.png` (página completa, 71 capturas; el estado `mas` existe solo en mobile). Verificado con Playwright en cada estado:

- Sin scroll horizontal a 390 ni a 1440 px.
- Sin errores de consola ni recursos fallidos.
- Todos los botones, links con forma de botón y campos miden al menos 44 px de alto.
- Un solo `h1` visible por pantalla.

## Checklist de revisión para Maxi

- [ ] Textos en es-AR con voseo; ningún mensaje confirma si un email tiene cuenta, salvo `EMAIL_EN_USO` y el bloqueo (exigidos por US-001 y US-002).
- [ ] Tabla de mensajes por código de error (decisión 1).
- [ ] Login automático después del registro y fallback (decisión 3).
- [ ] Ruta `/login` en textos, links y README.
- [ ] Requisito "Hasta 72 caracteres" solo cuando se pasa (estado `muy-larga`).
- [ ] Capturas con el nombre Muelas Felices.
- [ ] Solo tokens del sistema de diseño rev. 1; foco visible; objetivos ≥ 44 px; estados con ícono + texto, no solo color; `prefers-reduced-motion` (el spinner queda quieto).
- [ ] `autocomplete` correcto: `username`, `current-password`, `new-password`.
- [ ] Sin datos reales ni afirmaciones de 2FA, certificaciones o cumplimiento legal.
