# Proposal

## Why

Todos los changes de frontend de la app del odontólogo (C-07, C-09, C-13, C-14, C-17, C-18, C-19, C-20) necesitan la misma base: navegación, cliente HTTP con sesión, componentes de UI alineados al sistema de diseño y formateo de fechas en la zona del consultorio. Sin un shell común cada change reinventaría esa infraestructura (DD-02 elige TypeScript vanilla sin framework justamente a cambio de escribirla una vez y testearla). Además, la v1 promete una PWA instalable con lectura offline de la última agenda (DD-11). C-06 es la rama de frontend paralela a C-02 y conviene tenerla lista antes de que llegue la API de auth (C-05).

## What Changes

- Mapeo de `design-system/tokens.css` en `tailwind.config.ts` y nombre de producto leído de `design-system/brand.js` (sin colores ni tamaños sueltos).
- `frontend/src/shared/`:
  - Router propio por History API con rutas `/app/*` y fallback a "no encontrado".
  - Cliente HTTP sobre `fetch`: access token solo en memoria, refresh transparente vía cookie (`POST /api/v1/auth/refresh`) ante un 401 y un único refresh en vuelo para pedidos concurrentes; errores Problem Details (RFC 7807) tipados por `codigo`.
  - Store mínimo observable.
  - `ui/`: botón, campo, chip de estado, diálogo, aviso (toast) y estado vacío, según `design-system/components.html`.
  - Formateo de fechas con `Intl.DateTimeFormat('es-AR', { timeZone })` usando la zona del consultorio.
  - `api-types.ts` generado desde OpenAPI (script de C-01).
- App-shell: barra superior con marca, navegación inferior en mobile (360 px) y lateral desde `md`; estados de carga, vacío, error y sin conexión reutilizables por las páginas.
- PWA: `manifest.webmanifest` (íconos 192/512, `display: standalone`, `start_url: /app/agenda`) y Service Worker Workbox (precache del shell; `NetworkFirst` para `GET /api/v1/agenda`; nunca cachear `/public/turnos/**` ni `/auth/**`; sin escritura offline).
- Mockup del app-shell revisado y aprobado por Maxi **antes** de implementar pantallas.
- Tests Vitest: router, cliente HTTP con refresh y fechas/zona horaria.

Fuera de alcance: pantallas de auth (C-07), contenido de agenda (C-13), configuración (C-09), banner de plan/prueba (C-20) y página pública de reserva (C-16, que reutiliza `ui/` pero no el shell privado).

## Capabilities

### New Capabilities
- `app-shell`: navegación privada de la app del odontólogo (rutas `/app/*`, barra, navegación mobile/desktop) y estados de carga, vacío, error y sin conexión.
- `cliente-api`: comunicación del frontend con la API: token en memoria, refresh por cookie, manejo de Problem Details.
- `pwa-offline`: instalación como PWA y política de caché del Service Worker (qué se cachea y qué nunca).
- `fechas-zona-horaria`: presentación de fechas y horas en es-AR según la zona horaria del consultorio.

### Modified Capabilities
<!-- Ninguna: no hay specs existentes en openspec/specs/. -->

## Impact

- Código nuevo en `frontend/` (`src/shared/`, `src/main.ts`, `src/pages/`, `public/manifest.webmanifest`, `public/icons/`, `tailwind.config.ts`, `vite.config.ts`).
- Dependencias nuevas de frontend: `vite-plugin-pwa` (Workbox). Requiere el esqueleto `frontend/` de C-01.
- Consume `POST /api/v1/auth/refresh` (contrato de C-04) y `GET /api/v1/agenda` (C-12); hasta que existan, los tests usan dobles de `fetch`.
- Mockups en `openspec/changes/frontend-shell-pwa/mockups/`, que importan el sistema de diseño rev. 1.
