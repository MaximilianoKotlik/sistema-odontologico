# Design

> Governance **CRITICO** (RN-AU-06): no se escribe código de producción sin aprobación explícita de Maxi. Las decisiones de seguridad (D3 a D7, D9 y D11) están aprobadas por Maxi en #37 (2026-11-21).
>
> **Segunda versión** (revisión de Maxi en #37, 2026-11-21): suma D11 (cierre de sesión entre pestañas), acota D3 a la navegación dentro de la app (opción a), arma el DOM en D9, valida `volver` después de normalizar en D5, saca el timeout del refresh en D6 y suma a Risks el límite de los access tokens ya emitidos.

## Context

- C-07 es el frontend de autenticación: registro con aceptación de términos, login, recuperación y confirmación, cambio de contraseña con sesión iniciada (US-005), cierre de sesión, guardas de ruta y restauración de sesión vía `POST /auth/refresh` (ver `CHANGES.md` §C-07).
- **Contratos que consume** (ya mergeados; specs en `openspec/specs/`):
  - `sesion-odontologo` (C-04 y C-05):
    - `POST /api/v1/auth/login` → `{accessToken, expiraEn}` más la cookie de refresh `HttpOnly; Secure; SameSite=Strict; Path=/api/v1/auth`;
    - `POST /api/v1/auth/refresh` rota la cookie, y un refresh reusado revoca todas las sesiones y devuelve 401 `SESION_EXPIRADA`;
    - `POST /api/v1/auth/logout` → 204;
    - `POST /api/v1/auth/cambiar-password` (`{passwordActual, passwordNueva}`) → 204 y la cookie borrada;
    - códigos: `CREDENCIALES_INVALIDAS` (401), `CUENTA_BLOQUEADA` (423, con los minutos restantes) y `PASSWORD_ACTUAL_INVALIDA`.
  - `registro-odontologo`: `POST /api/v1/auth/registro` (`{nombre, apellido, email, password, nombreConsultorio, aceptaTerminos}`) → 201 `{usuarioId, consultorioId, slug}`, sin tokens. Códigos `EMAIL_EN_USO`, `PASSWORD_DEBIL` (incluye más de 72 bytes), `TERMINOS_NO_ACEPTADOS` y `VALIDACION`.
  - `recuperacion-contrasena`: `POST /api/v1/auth/recuperacion` (`{email}`) → siempre 202, y `.../confirmar` (`{token, password}`) → 204 o `TOKEN_INVALIDO`.
  - `seguridad-http`: 429 `DEMASIADAS_SOLICITUDES` con `Retry-After`; refresh y logout solo desde el origen propio (403 `ORIGEN_NO_PERMITIDO`).
- **Base del frontend (C-06)**:
  - `crearClienteHttp` (token en memoria, un refresh en vuelo por pestaña, `alVencerSesion`);
  - `app.iniciarSesion(token)` y `app.cerrarSesion()`, que borran la caché `agenda-v1`;
  - el router por History API, hoy sin guardas ni rutas públicas;
  - el shell con "Cerrar sesión".
- **Base visual**: sistema de diseño rev. 1, app-shell de C-06 y mockups aprobados (`mockups/`).

## Goals / Non-Goals

**Goals:**
- Que ningún secreto (access token, contraseña, token de recuperación) quede en un lugar persistente o visible para otro script, para el historial o para el servidor de logs.
- Que el odontólogo no vea nunca contenido privado sin sesión, ni por un instante.
- Que una recarga, una segunda pestaña o un link directo no le cierren la sesión sin motivo.
- Que cada error explique qué hacer sin revelar si un email tiene cuenta, salvo `EMAIL_EN_USO` y el bloqueo, que pide la KB.

**Non-Goals:**
- Cambiar el backend: los contratos de C-04 y C-05 se consumen tal cual.
- 2FA, verificación de email y baja de cuenta (DD-18, DD-31, DD-29).
- Un "recordarme": la sesión dura lo que el refresh (14 días, RN-AU-02).

## Decisions

Las decisiones de UI que salen de los mockups están en `mockups/README.md` §Decisiones para revisar. Maxi resolvió las tres que afectaban a otros changes (revisión del PR #14, 2026-11-20):

- **Después del registro se entra directo**: el backend no cambia. Con el 201 de `POST /auth/registro`, el frontend llama a `POST /auth/login` con las mismas credenciales y entra a la app. Si ese login falla, lleva a `/login` con el email cargado y un aviso.
- **La ruta del login es `/login`**, igual que en el shell de C-06.
- **"Cambiar contraseña" va en el menú del shell**, arriba de "Cerrar sesión", en la barra lateral y en la hoja "Más". Toca el shell de C-06 (Nacho).

En la segunda revisión (PR #14, 2026-11-20) Maxi pidió además:

- **Tercer requisito de contraseña, solo cuando se pasa**: "Hasta 72 caracteres (las letras con tilde y la ñ cuentan doble)". C-04 (#16) rechaza con `PASSWORD_DEBIL` las contraseñas de más de 72 bytes en UTF-8, porque BCrypt no admite más. Estado `muy-larga` en registro, contraseña nueva y cambiar contraseña.
- **Nombre Muelas Felices** (DD-34) en todas las capturas, y links de términos y privacidad a `/terminos` y `/privacidad`.

Las decisiones técnicas de C-07:

### D1 — Estructura
- `features/auth/`:
  - `api.ts`: las seis llamadas de auth, tipadas con `api-types.ts`;
  - `validaciones.ts` (puras): email, requisitos de RN-AU-01, bytes en UTF-8 con `TextEncoder`, coincidencia y términos;
  - `errores.ts`: código → mensaje y campo, incluido el cálculo de espera desde `Retry-After`;
  - `sesion.ts`: arranque, guardas y destino después del login;
  - `volver.ts`: saneamiento del destino;
  - `coordinacion.ts`: refresh serializado (D6) y cierre de sesión entre pestañas (D11);
  - vistas de login, registro, recuperar, recuperar-confirmar y cambiar-contraseña con DOM vanilla y `ui/` de C-06.
- `pages/`: una página por ruta pública más `/app/cuenta/contrasena`.
- La lógica sensible está en funciones puras con tests propios. Las vistas se prueban con jsdom en los flujos clave.

### D2 — Rutas públicas y privadas con dos layouts
- El router de C-06 suma rutas públicas (`/login`, `/registro`, `/recuperar`, `/terminos`, `/privacidad`) con un **layout de auth sin shell**, y deja `/app/*` con el shell.
- El shell se monta recién cuando hay sesión. Al cerrarla se desmonta, así su contenido no queda en el DOM ni en la caché de la página para el botón atrás.
- Una ruta que no existe fuera de `/app` va a `/login` sin sesión y a `/app` con sesión.

### D3 — Arranque y restauración de sesión · aprobada por Maxi en #37
- Al cargar una ruta privada sin token en memoria, se muestra el splash "Abriendo tu agenda…" (`aria-busy`) y se llama una vez a `renovarSesion()` de C-06.
  - Con éxito: se monta el shell en la ruta pedida.
  - Con 401: va a `/login?volver=…` sin aviso. Es el caso normal de alguien que no tiene sesión, no un error.
  - **Sin red** (nota de implementación, para revisar en el PR): si el refresh no llega al servidor, se monta el shell sin token y con el aviso "Sin conexión", para conservar la lectura de la última agenda guardada (pwa-offline de C-06). Esa caché solo existe mientras hubo una sesión: se borra al cerrar, al vencer y al iniciar otra. Al volver la red, el primer pedido privado renueva la sesión o la vence y lleva al login.
- En una ruta pública **no se intenta el refresh**, ni siquiera en `/login` o `/registro`:
  - Si la pestaña ya tiene token en memoria (se llegó navegando dentro de la app), `/login` y `/registro` llevan a `/app` (spec "Usuario con sesión en rutas de ingreso").
  - Si se abren con una carga completa (link, marcador o recarga), no hay token en memoria y se muestra el formulario. No se sabe si hay cookie de refresh y no se pregunta al backend.
  - Si en ese caso el odontólogo tenía sesión y vuelve a ingresar, el backend emite un refresh nuevo y el anterior sigue valiendo hasta que vence (C-04 admite varias sesiones). No se pierde nada ni se revoca nada.
- *Alternativa*: refresh silencioso en `/login` y `/registro` antes de pintar el formulario → descartada (revisión de Maxi en #37): suma una llamada al backend en cada visita y demora el formulario para el caso común, que es alguien sin sesión.
- *Alternativa*: guardar una marca "tuvo sesión" en `localStorage` para no intentar el refresh sin sesión → descartada, porque deja una huella de uso en el dispositivo y el refresh sin cookie igual responde 401 rápido.

### D4 — Access token y contraseñas solo en memoria · aprobada por Maxi en #37
- El access token vive solo en el cierre de `crearClienteHttp` (C-06). Entra por `app.iniciarSesion(token)`, que antes borra la agenda guardada de una sesión anterior.
- **Prohibido** en `localStorage`, `sessionStorage`, IndexedDB, cookies legibles, la URL, el `state` del historial, logs y mensajes entre pestañas.
- La contraseña del registro se guarda en una variable local solo para el login automático, y se descarta al terminar el intento, salga bien o mal. Los inputs de contraseña se vacían al salir de la pantalla.
- **Test de no-persistencia**: un test recorre login, registro con login automático, refresh y cambio de contraseña con espías sobre `Storage.prototype.setItem`, `indexedDB.open`, `document.cookie`, `history.pushState` y `history.replaceState`, y falla si alguno recibe el token o una contraseña.

### D5 — Destino después del login (`volver`) · aprobada por Maxi en #37
- `sanearVolver(valor)` valida **después de normalizar**, en este orden:
  1. Exige que `valor` empiece con `/` y lo parsea con `url = new URL(valor, location.origin)`. Si falla el parseo, `/app`.
  2. Exige `url.origin === location.origin`. Así quedan afuera `//dominio`, `/\dominio` y `\\dominio` (el parser trata `\` como `/`), `javascript:` y cualquier URL absoluta.
  3. Chequea `url.pathname`, ya normalizado, y no el texto original: tiene que ser exactamente `/app` o empezar con `/app/`. El parser resuelve `..` y `%2e%2e` (en mayúsculas o minúsculas), así que `/app/../login` y `/app/%2e%2e/login` quedan como `/login` y se rechazan.
  4. Devuelve `url.pathname + url.search + url.hash` de la URL normalizada, nunca el `valor` original.
- Rechaza también las rutas públicas y el vacío. Ante la duda, `/app`.
- Si el destino es `/app` y el consultorio no tiene prestaciones activas, C-09 decide llevar a `/app/bienvenida`. C-07 no conoce esa regla.

### D6 — Refresh coordinado entre pestañas · aprobada por Maxi en #37
- **Problema** (D4 de C-04): la cookie de refresh es una sola para todas las pestañas. Si dos pestañas renuevan a la vez con la misma cookie, la segunda presenta un refresh ya rotado y el backend revoca todas las sesiones.
- **Solución**: `renovarSesion()` toma el lock `muelas-auth-refresh` con la **Web Locks API** (`navigator.locks.request`) antes de llamar al backend. La segunda pestaña espera, y cuando entra, el navegador ya le manda la cookie rotada, así que su refresh es válido. El refresh en vuelo dentro de una misma pestaña sigue siendo uno (C-06).
- **El refresh no se corta por timeout**: el `fetch` de `POST /auth/refresh` no usa `AbortController` ni ningún timeout propio, y el lock se libera recién cuando la respuesta terminó (cuerpo leído y token guardado, o error de red del navegador). Si se abortara el pedido después de que el backend rotó la cookie, el navegador podría descartar el `Set-Cookie`: el próximo refresh presentaría la cookie vieja, el backend lo vería como reuso y revocaría todas las sesiones. Un refresh colgado deja esperando a las otras pestañas hasta el timeout de red del propio navegador; es preferible a perder la sesión.
- No se comparte el access token entre pestañas: cada una tiene el suyo en memoria, así no viaja ningún secreto por mensajes.
- **Sin Web Locks** (navegadores viejos): se mantiene el comportamiento de C-06 y el riesgo queda acotado a dos pestañas que vencen en el mismo instante. Web Locks está en Chrome, Edge, Firefox y Safari actuales.
- *Alternativa*: `BroadcastChannel` para que una pestaña renueve y reparta el token → descartada, porque haría viajar el access token entre contextos.

### D7 — Token de recuperación · aprobada por Maxi en #37
- El link del email es `/recuperar#token=…` (C-05): el fragmento no se manda al servidor ni queda en los logs de acceso.
- Al cargar, se lee el token, se guarda en memoria y se hace `history.replaceState(null, '', '/recuperar')` antes de pintar el formulario. Así no queda en el historial, en un marcador ni en una captura de pantalla.
- Ante `PASSWORD_DEBIL`, el token se conserva para reintentar (el backend no lo consume). Ante `TOKEN_INVALIDO`, se descarta.

### D8 — Mensajes por código
| Código | Pantalla | Comportamiento |
|---|---|---|
| `CREDENCIALES_INVALIDAS` | Login | Aviso genérico arriba, contraseña vacía, email conservado (RN-AU-03) |
| `CUENTA_BLOQUEADA` (423) | Login | Minutos restantes que manda el backend y link a recuperar |
| `DEMASIADAS_SOLICITUDES` (429) | Todas | Espera calculada desde `Retry-After` y envío deshabilitado hasta entonces |
| `EMAIL_EN_USO` | Registro | En el email, con "Ingresá" y "restablecé tu contraseña" |
| `PASSWORD_DEBIL` | Registro, confirmar y cambiar | Requisito en ✕ y campo inválido (con más de 72 bytes, el requisito de 72) |
| `TERMINOS_NO_ACEPTADOS` / `VALIDACION` | Registro | Resumen de errores con links a cada campo |
| `TOKEN_INVALIDO` | Confirmar | Pantalla "Este link ya no sirve" con "Pedir un link nuevo" |
| `PASSWORD_ACTUAL_INVALIDA` | Cambiar | En el campo actual, que se vacía |
| `SESION_EXPIRADA` | Login | "Tu sesión venció", y después vuelve a la ruta donde estaba |
| `ORIGEN_NO_PERMITIDO` (403) | — | Error inesperado: no debería pasar desde el frontend propio |
| Red caída | Todas | Aviso sin conexión y envío deshabilitado |

### D9 — Términos y privacidad · aprobada por Maxi en #37
- `/terminos` y `/privacidad` muestran el borrador de `docs/legal/` (#24) con su aviso de "pendiente de revisión legal" (DD-28).
- El Markdown se importa en el build (`?raw` de Vite) y se convierte con un conversor mínimo propio, sin dependencias nuevas, para el subconjunto que usan esos archivos:
  - títulos, párrafos, listas con viñetas y numeradas, énfasis y citas (el aviso de borrador);
  - tablas (la política de privacidad tiene 3).
- **El conversor arma el DOM, no HTML**: crea cada nodo con `document.createElement` y pone el texto con `textContent` (o `document.createTextNode`). No arma strings de HTML ni usa `innerHTML`, `outerHTML`, `insertAdjacentHTML` ni `DOMParser`. Así un `<script>` o un `<img onerror>` escrito en el Markdown termina como texto visible, y la seguridad no depende de que el conversor escape bien.
- **Links**:
  - Los absolutos se parsean con `new URL(...)` y se aceptan solo con protocolo `https:` o `mailto:`. Cualquier otro (`javascript:`, `data:`, `http:`, etc.) se muestra como texto, sin link.
  - Los relativos al repo (por ejemplo, a `knowledge-base/`) se muestran como texto, sin link, porque esas rutas no existen en la app publicada.
  - Los aceptados se crean con `setAttribute('href', url.href)`; los `https:` llevan `rel="noopener noreferrer"`.
- Los marcadores `[COMPLETAR …]` del borrador se muestran tal cual: son parte de lo que falta revisar.
- *Alternativas*:
  - copiarlos a mano como HTML → dos fuentes que mantener;
  - sumar una librería de Markdown → dependencia nueva para dos páginas.

### D10 — Accesibilidad
- Un solo `h1` por pantalla.
- `autocomplete`: `username` y `email` en el email, `current-password` en el login y en la contraseña actual, `new-password` en las nuevas.
- Los requisitos van con ícono, texto y estado oculto para lectores de pantalla.
- El resumen de errores recibe el foco y los avisos de error usan `role="alert"`.
- Mostrar u ocultar la contraseña es un botón con `aria-pressed`.
- El splash usa `aria-busy` y el spinner queda quieto con `prefers-reduced-motion`.

### D11 — Cierre de sesión entre pestañas · aprobada por Maxi en #37
- **Problema**: si se cierra la sesión o se cambia la contraseña en una pestaña, las demás siguen mostrando la agenda y operando con su access token hasta que vence (15 minutos). Va contra el goal "nunca ver contenido privado sin sesión" (decisión de Maxi en #37).
- **Solución**: `coordinacion.ts` abre un `BroadcastChannel('muelas-auth')`.
  - Al cerrar sesión (logout, aunque falle por red) o al terminar un cambio de contraseña con 204, la pestaña publica `{ tipo: 'sesion-cerrada', motivo }`, con `motivo` de un conjunto cerrado: `'logout'` o `'contrasena-cambiada'`. **Nunca** viaja el token, el email ni otro dato del usuario: el motivo solo elige el aviso.
  - Al recibirlo, cada pestaña borra su token de memoria, desmonta el shell, borra la caché `agenda-v1` y va a `/login` con el aviso que corresponde ("Cerraste sesión" o "Cambiamos tu contraseña"), igual que la pestaña que lo originó. No llama a `/auth/logout`: la cookie ya no sirve.
  - Una pestaña en una ruta pública solo borra el token si lo tuviera y se queda donde está.
  - El mensaje entra como `unknown` y pasa por un type guard. Cualquier otro formato se ignora.
- **Sin `BroadcastChannel`**: se mantiene el comportamiento de hoy. Las otras pestañas se enteran recién cuando su access token vence y el refresh responde 401.
- *Alternativa*: evento `storage` de `localStorage` → descartada, porque deja una huella en el dispositivo (D3) y `BroadcastChannel` está en todos los navegadores actuales.

## Risks / Trade-offs

- [Los access token ya emitidos siguen valiendo después de cambiar la contraseña] → `cambiar-password` revoca los refresh (C-05), pero un access token emitido antes sigue siendo válido hasta 15 minutos en otros dispositivos. D11 lo resuelve solo para otras pestañas del mismo navegador. Es un límite conocido de v1 del backend (C-05), no de esta propuesta.
- [`BroadcastChannel` no disponible] → las otras pestañas siguen mostrando contenido hasta 15 minutos después de cerrar sesión, como sin D11.
- [Web Locks no disponible en un navegador viejo] → vuelve el riesgo de D4 de C-04 solo en ese caso. El costo es un login nuevo, no una fuga.
- [Un refresh colgado bloquea a las otras pestañas] → esperan hasta el timeout de red del navegador (D6). Se acepta para no perder la cookie rotada.
- [El login automático guarda la contraseña unos milisegundos en memoria] → es la misma memoria del formulario que ya la tenía, y se descarta apenas termina (D4).
- [Las pantallas de términos y privacidad muestran un borrador] → visible como tal (DD-28). El registro no se publica con datos reales sin la revisión legal.
- [Cambios en archivos de C-06 (router, http, shell, app)] → acotados a lo que nombra la propuesta, con los tests existentes de C-06 en verde.

## Migration Plan

- Solo frontend. Rollback = no mergear. Sin datos ni configuración nuevos.

## Open Questions

- Términos y condiciones y política de privacidad: ya hay borrador en `docs/legal/` (#24), marcado "pendiente de revisión legal" según DD-28. El registro no se publica con datos reales hasta que lo revise un abogado.

## UI Mockups

- Design system revision: 1
- Decision: yes (2026-11-20)
- Mockups: openspec/changes/frontend-auth/mockups/ (`login.html`, `registro.html`, `recuperar.html`, `recuperar-confirmar.html`, `cambiar-contrasena.html`, más `auth.css` y `mockup.js`)
- Approval: approved (2026-11-20, by Maxi), versión `3ffa2f7` (#14)
- Preview: rendered (2026-11-20); `mockups/previews/*-mobile-390x844.png`, `mockups/previews/*-desktop-1440x900.png`
- Visual verify: passed (2026-11-21); `mockups/verify/*-{360x780,390x844,1440x900}.png` de login, registro, recuperar, contraseña nueva, términos y cambiar contraseña, contra el dev server con el backend simulado. Sin scroll horizontal ni errores de consola (salvo los 401 esperados). Se corrigieron las viñetas del resumen de errores y el `aria-current` de "Cambiar contraseña".
