# Tasks

> **CRITICO (RN-AU-06)**: no se escribe código de producción hasta que Maxi apruebe esta propuesta.
> Strict TDD: cada tarea de código arranca con un test que falla (red → green → triangulate → refactor).
> Criterio de aceptación visual: los mockups aprobados de `mockups/` (versión `3ffa2f7`) y sus capturas.

## 0. Propuesta

- [x] 0.1 Mockups aprobados por Maxi (#14, preparados por Octi)
- [x] 0.2 Aprobación de Maxi de la propuesta, en especial D3 a D7, D9 y D11 (#37, 2026-11-21)

## 1. Lógica pura

- [x] 1.1 `validaciones.ts`: email, al menos 10 caracteres, distinta del email, hasta 72 bytes en UTF-8 (con tildes y ñ), coincidencia y términos
- [x] 1.2 `volver.ts`: `sanearVolver` con casos de `/app/...`, `/app`, `//otro`, `/\otro`, `https://otro`, `javascript:`, `\\`, rutas públicas y vacío, más las rutas que salen de `/app` al normalizarse: `/app/../login`, `/app/%2e%2e/login`, `/app/%2E%2E/login` y `/app\..\login` (D5)
- [x] 1.3 `errores.ts`: cada código de D8 a mensaje y campo, y la espera desde `Retry-After` en segundos y fecha HTTP

## 2. Sesión y cliente

- [ ] 2.1 `api.ts` de auth tipado, con tests sobre dobles de `fetch` (credenciales `include` solo en `/api/v1/auth/**`)
- [ ] 2.2 `coordinacion.ts`, con tests de dos clientes simulando dos pestañas:
  - refresh serializado con Web Locks y respaldo sin Web Locks; el refresh no usa `AbortController` y el lock se libera recién cuando terminó la respuesta (D6);
  - cierre de sesión entre pestañas con `BroadcastChannel('muelas-auth')` (D11): logout o cambio de contraseña en una pestaña → la otra borra su token, desmonta el shell, borra `agenda-v1` y va a `/login` con el aviso del motivo; un espía verifica que el mensaje no lleva el token; un mensaje con otro formato se ignora; sin `BroadcastChannel` no falla nada
- [ ] 2.3 `sesion.ts`: arranque con restauración, guardas de `/app/*`, redirección desde `/login` y `/registro` con token en memoria, sin refresh al cargar `/login` o `/registro` desde cero, y destino después del login (D3, D5)
- [ ] 2.4 Router de C-06: rutas públicas con layout de auth y montaje o desmontaje del shell según la sesión (D2)

## 3. Pantallas

- [ ] 3.1 Login con todos los estados de los mockups
- [ ] 3.2 Registro con requisitos en vivo, resumen de errores, `EMAIL_EN_USO` y login automático con fallback
- [ ] 3.3 Recuperar: pedido y contraseña nueva, con el token quitado de la URL (D7) y `TOKEN_INVALIDO`
- [ ] 3.4 Cambiar contraseña en `/app/cuenta/contrasena` y el ítem en el menú del shell (barra lateral y hoja "Más")
- [ ] 3.5 Cerrar sesión: desmonta el shell y lleva al login con el aviso
- [ ] 3.6 `/terminos` y `/privacidad` desde `docs/legal/` con el conversor mínimo que arma el DOM con `createElement` y `textContent` (D9). Test con `<script>`, `<img src=x onerror=alert(1)>` y `[x](javascript:alert(1))` en el Markdown: se ven como texto, no se crea ningún `script` ni `img`, ningún `href` usa `javascript:`, y solo quedan links `https:` y `mailto:`

## 4. Seguridad y verificación

- [ ] 4.1 Test de no-persistencia de D4: ni el token ni las contraseñas en storage, IndexedDB, cookies legibles, URL ni historial
- [ ] 4.2 `npm run typecheck`, `lint`, `test` y `build` en verde, con los tests de C-06 intactos
- [ ] 4.3 Verificación visual contra los mockups aprobados a 360×780, 390×844 y 1440×900 (capturas en `mockups/verify/`), consola sin errores y accesibilidad
- [ ] 4.4 Flujo FL-01 completo contra el backend local (registro, login automático, recarga con restauración, dos pestañas, cambio de contraseña, recuperación con Mailpit, logout), incluido el cierre entre pestañas de D11: con la agenda abierta en dos pestañas, cerrar sesión en una lleva a la otra a `/login` con "Cerraste sesión", y cambiar la contraseña lleva a las dos a `/login` con "Cambiamos tu contraseña"
