# Design

## Context

Ver `proposal.md` (Why). El frontend es TypeScript strict sin framework (DD-02), Vite 5 + Tailwind 3.4 + Vitest/jsdom, con la estructura de `knowledge-base/08_arquitectura_propuesta.md` §Estructura de directorios. El esqueleto `frontend/` lo crea C-01; este change trabaja sobre él. La API de auth (C-04/C-05) y la de agenda (C-12) todavía no existen: el contrato está descrito en la KB (`07_flujos_principales.md` §Login, `03_actores_y_roles.md` §Rutas). Reglas duras que condicionan el diseño: sin `any`, sin valores visuales sueltos (solo tokens), voseo, accesibilidad AA.

## Goals / Non-Goals

**Goals:**
- Infraestructura de UI chica, testeable y sin dependencias de runtime fuera de `vite-plugin-pwa`.
- Un shell que las páginas de C-07/C-09/C-13/C-14/C-17/C-18/C-19/C-20 usen solo montando contenido.

**Non-Goals:**
- Motor de templates o virtual DOM: se renderiza con DOM directo y helpers pequeños.
- Guardas de ruta y pantalla de login (C-07); acá solo se expone el evento de sesión vencida.
- Tema oscuro: los tokens existen pero no se revisaron visualmente (TBD en el sistema de diseño).

## Decisions

### D1 — Router por History API con tabla declarativa
Tabla `{ patron, cargar }` con parámetros (`/app/turnos/:id`), interceptación de clics en `<a data-link>` y `popstate`. `cargar` devuelve la página con `import()` para dividir el bundle por ruta.
*Alternativas*: hash router (URLs feas, choca con `start_url: /app/agenda`); librería tipo Navigo (dependencia para ~80 líneas).

### D2 — Cliente HTTP con refresh de una sola vuelta
`http.request<T>()` envuelve `fetch`; el token vive en un módulo cerrado (`tokenEnMemoria`). Ante 401 privado comparte una única promesa de refresh (`refreshEnCurso`); si falla, emite `sesion-vencida` en el store y el router navega a `/login?volver=…`. Los errores se normalizan a `ApiError { status, codigo, detail }`; los fallos de red usan `codigo: 'SIN_CONEXION'`.
*Alternativas*: interceptores con axios (dependencia innecesaria); refresh proactivo por temporizador (más estado, no cubre la suspensión del dispositivo).

### D3 — Store observable mínimo
`crearStore<S>(inicial)` con `get`, `set(parcial)` y `suscribir(fn)`; un store de sesión (`autenticado`, `consultorio.zonaHoraria`, `nombre`) y uno de conectividad (`online`, `ultimaSincronizacion`).
*Alternativas*: señales o Redux-like: más API que la que piden ~10 pantallas.

### D4 — Componentes `ui/` como funciones que devuelven elementos
`boton({ variante, texto, icono })`, `campo(...)`, `chipEstado(estado)`, `dialogo(...)`, `aviso(...)`, `estadoVacio(...)`, más `esqueleto()` y `errorRecuperable()`. Clases Tailwind mapeadas a tokens; las variantes reproducen `design-system/components.html`.
*Alternativas*: Web Components (Shadow DOM complica Tailwind y los tests en jsdom).

### D5 — Tailwind leyendo tokens
`tailwind.config.ts` define `colors`, `spacing`, `borderRadius`, `boxShadow`, `fontFamily` como `var(--…)`; `tokens.css` se importa desde `design-system/` en el CSS global (sin copiarlo) para que un cambio de revisión impacte en un solo lugar. Se desactiva la paleta por defecto de Tailwind para que un color suelto falle en revisión.

### D6 — Workbox vía `vite-plugin-pwa` en modo `generateSW`
`globPatterns` del shell; `runtimeCaching` con `NetworkFirst` solo para `GET` a `/api/v1/agenda` (caché `agenda-v1`, `networkTimeoutSeconds: 4`, máx. 30 entradas, 7 días); `navigateFallback: /index.html` con `navigateFallbackDenylist` para `/api/`, `/c/`, `/t/`. Nada más se cachea en runtime. Al cerrar sesión se ejecuta `caches.delete('agenda-v1')`. La hora de "última sincronización" se toma del encabezado `Date` de la respuesta cacheada.
*Alternativas*: `injectManifest` con SW propio (más control, más código para mantener); `StaleWhileRevalidate` (mostraría datos viejos con conexión).

### D7 — Fechas con `Intl` y zona explícita
`formatearDia(instante, zona)`, `formatearHora(instante, zona)` y `hoyEn(zona)` crean formateadores `Intl.DateTimeFormat('es-AR', { timeZone })` cacheados por zona. Nunca se usa `Date#getHours()` ni la zona del dispositivo. Tests fijan `TZ=Europe/Madrid` en Vitest para detectar fugas.

### D8 — Navegación: barra inferior en mobile, lateral desde `md`
Destinos principales (Agenda, Bloqueos, Configuración) + "Más" en mobile; en desktop la barra lateral lista todo. "Nuevo turno" queda como botón principal en la barra superior de Agenda (lo implementa C-14). Indicador activo: fondo tinte + barra de 3 px + ícono relleno (no solo color).

## UI Mockups

- Design system revision: 1
- Decision: yes (2026-11-20)
- Mockups: `openspec/changes/frontend-shell-pwa/mockups/` (`app-shell.html`, brief en `mockups/README.md`)
- Approval: pending
- Preview: rendered (2026-11-20, `mockups/previews/` a 390×844, 360×780 y 1440×900)
- Visual verify: pending

## Risks / Trade-offs

- [El contrato de `/auth/refresh` y `/agenda` puede cambiar en C-04/C-12] → el cliente depende solo de `api-types.ts` generado; los tests usan dobles de `fetch` basados en la KB y se ajustan al regenerar tipos.
- [El SW sirve una agenda vieja y el odontólogo cree que está al día] → aviso persistente con la hora de guardado y acciones de escritura deshabilitadas.
- [Datos de pacientes en caché en un equipo compartido] → solo se cachea la agenda privada, se borra al cerrar sesión, y nunca la API pública.
- [Infraestructura propia (router/store) crece desordenada] → APIs mínimas cubiertas por tests; feature folders.
- [C-01 no está mergeado] → la implementación espera a C-01 `[x]`; mientras tanto solo se preparan mockups y tests.
