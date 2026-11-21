# Tasks

> **CRITICO (RN-AU-06)**: no se escribe código de producción hasta que Maxi apruebe esta propuesta.
> Strict TDD: cada tarea de código arranca con un test que falla (red → green → triangulate → refactor).
> Criterio de aceptación visual: los mockups aprobados de `mockups/` (versión `3ffa2f7`) y sus capturas.

## 0. Propuesta

- [x] 0.1 Mockups aprobados por Maxi (#14, preparados por Octi)
- [ ] 0.2 Aprobación de Maxi de la propuesta, en especial D3 a D7 y D9

## 1. Lógica pura

- [ ] 1.1 `validaciones.ts`: email, al menos 10 caracteres, distinta del email, hasta 72 bytes en UTF-8 (con tildes y ñ), coincidencia y términos
- [ ] 1.2 `volver.ts`: `sanearVolver` con casos de `/app/...`, `/app`, `//otro`, `https://otro`, `javascript:`, `\\`, rutas públicas y vacío (D5)
- [ ] 1.3 `errores.ts`: cada código de D8 a mensaje y campo, y la espera desde `Retry-After` en segundos y fecha HTTP

## 2. Sesión y cliente

- [ ] 2.1 `api.ts` de auth tipado, con tests sobre dobles de `fetch` (credenciales `include` solo en `/api/v1/auth/**`)
- [ ] 2.2 `coordinacion.ts`: refresh serializado con Web Locks y respaldo sin Web Locks (D6); test con dos clientes simulando dos pestañas
- [ ] 2.3 `sesion.ts`: arranque con restauración, guardas de `/app/*`, redirección desde `/login` y `/registro` con sesión, y destino después del login (D3, D5)
- [ ] 2.4 Router de C-06: rutas públicas con layout de auth y montaje o desmontaje del shell según la sesión (D2)

## 3. Pantallas

- [ ] 3.1 Login con todos los estados de los mockups
- [ ] 3.2 Registro con requisitos en vivo, resumen de errores, `EMAIL_EN_USO` y login automático con fallback
- [ ] 3.3 Recuperar: pedido y contraseña nueva, con el token quitado de la URL (D7) y `TOKEN_INVALIDO`
- [ ] 3.4 Cambiar contraseña en `/app/cuenta/contrasena` y el ítem en el menú del shell (barra lateral y hoja "Más")
- [ ] 3.5 Cerrar sesión: desmonta el shell y lleva al login con el aviso
- [ ] 3.6 `/terminos` y `/privacidad` desde `docs/legal/` con el conversor mínimo (D9)

## 4. Seguridad y verificación

- [ ] 4.1 Test de no-persistencia de D4: ni el token ni las contraseñas en storage, IndexedDB, cookies legibles, URL ni historial
- [ ] 4.2 `npm run typecheck`, `lint`, `test` y `build` en verde, con los tests de C-06 intactos
- [ ] 4.3 Verificación visual contra los mockups aprobados a 360×780, 390×844 y 1440×900 (capturas en `mockups/verify/`), consola sin errores y accesibilidad
- [ ] 4.4 Flujo FL-01 completo contra el backend local (registro, login automático, recarga con restauración, dos pestañas, cambio de contraseña, recuperación con Mailpit, logout)
