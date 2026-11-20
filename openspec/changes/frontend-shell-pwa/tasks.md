# Tasks

> Strict TDD: en cada tarea de código el test se escribe primero y se ve fallar (rojo → verde → triangular → refactor).
> Las tareas 2 en adelante requieren C-01 `[x]` en `CHANGES.md` y el mockup aprobado (grupo 1).
> Criterio visual de aceptación: los mockups aprobados en `mockups/` y sus capturas en `mockups/previews/`.

## 1. Mockup del app-shell (gate de diseño)

- [x] 1.1 Crear `mockups/` con el app-shell en mobile (360/390 px) y desktop (1440 px): navegación, estados carga, vacío, error y sin conexión, y "Más"; verificar que importa `design-system/tokens.css` y `brand.js` sin valores visuales sueltos
- [x] 1.2 Renderizar los mockups a 390×844 y 1440×900, guardar capturas en `mockups/previews/` y registrar `Preview: rendered` en `design.md`
- [x] 1.3 Obtener la aprobación explícita de Maxi en el PR borrador y registrar `Approval: approved (<fecha>, by user)` en `design.md` y `mockups/README.md`

## 2. Base de estilos y dependencias

- [ ] 2.1 Mapear los tokens en `tailwind.config.ts` (sin paleta por defecto) e importar `design-system/tokens.css` en el CSS global; verificar con `npm run build` que una clase de color por defecto (`bg-red-500`) no genera CSS
- [ ] 2.2 Agregar `vite-plugin-pwa` y verificar que `npm run build` termina sin errores

## 3. Fechas y zona horaria

- [ ] 3.1 Implementar `shared/fechas` (`formatearDia`, `formatearHora`, `hoyEn`) con tests Vitest corriendo con `TZ=Europe/Madrid` que cubran los escenarios de `specs/fechas-zona-horaria`

## 4. Cliente API

- [ ] 4.1 Implementar `ApiError` y la normalización de Problem Details y errores de red (`SIN_CONEXION`); verificar con tests de los escenarios "Error de dominio" y "Sin red"
- [ ] 4.2 Implementar token en memoria y encabezado `Authorization` solo en rutas privadas; verificar con tests que no se escribe en `localStorage`/`sessionStorage` y que `/public/**` va sin encabezado
- [ ] 4.3 Implementar refresh ante 401 con reintento único y promesa compartida; verificar con tests "Token vencido", "El reintento vuelve a fallar" y "Tres pedidos simultáneos" (exactamente un refresh)
- [ ] 4.4 Emitir `sesion-vencida` al fallar el refresh y borrar el token; verificar con test que la ruta resultante es `/login?volver=<ruta>`
- [ ] 4.5 Tipar el cliente con `api-types.ts` generado; verificar que `npm run api:types` + `tsc --noEmit` pasan

## 5. Router y store

- [ ] 5.1 Implementar `crearStore` con tests de `get`/`set`/`suscribir`
- [ ] 5.2 Implementar el router con tabla de rutas, parámetros, `popstate`, redirección `/app` → `/app/agenda` (replace) y página no encontrada; verificar con tests de los escenarios de navegación de `specs/app-shell`

## 6. Componentes `ui/`

- [ ] 6.1 Implementar botón, campo, chip de estado, diálogo, aviso y estado vacío según el mockup y `components.html`; verificar con tests de accesibilidad (roles, `aria-*`, nombres en español) y revisión visual contra el mockup
- [ ] 6.2 Implementar `esqueleto` (con `aria-busy` y sin animación bajo `prefers-reduced-motion`) y `errorRecuperable` con "Reintentar"; verificar con tests

## 7. App-shell

- [ ] 7.1 Implementar barra superior con marca desde `brand.js`, navegación inferior (< 768 px) y lateral (≥ 768 px), "Más", "Saltar al contenido" y `aria-current`; verificar con tests de DOM y comparación visual a 360 y 1440 px contra el mockup aprobado
- [ ] 7.2 Implementar aviso sin conexión ligado al store de conectividad (eventos `online`/`offline`) que deshabilita acciones de escritura; verificar con tests de los escenarios "Se pierde la conexión" y "Vuelve la conexión"

## 8. PWA

- [ ] 8.1 Crear `manifest.webmanifest` e íconos 192/512 (provisorios rotulados TBD); verificar en DevTools > Application que el manifiesto es válido y `start_url` es `/app/agenda`
- [ ] 8.2 Configurar Workbox (`generateSW`): precache del shell, `NetworkFirst` solo para `GET /api/v1/agenda`, denylist de `/api/`, `/c/`, `/t/`; verificar con test sobre la config generada que no hay reglas para `/auth/**` ni `/public/**`
- [ ] 8.3 Borrar la caché `agenda-v1` al cerrar sesión; verificar con test sobre `caches.delete`

## 9. Verificación integral

- [ ] 9.1 Correr `npm test`, `tsc --noEmit` y `npm run build` en verde
- [ ] 9.2 Verificación visual: levantar el dev server, capturar 390×844 y 1440×900 en `mockups/verify/`, revisar consola y red, comparar con el mockup aprobado y registrar `Visual verify` en `design.md`
- [ ] 9.3 Verificación offline manual: visitar agenda con conexión, cortar red en DevTools, recargar y confirmar shell + aviso; confirmar que la caché no tiene entradas de `/auth/` ni `/public/`

## Workflow follow-up

- Marcar el PR como listo y pedir revisión a Maxi; Maxi marca `[x]` en `CHANGES.md` al mergear.
- Archivar el change con `openspec archive` después del merge.
