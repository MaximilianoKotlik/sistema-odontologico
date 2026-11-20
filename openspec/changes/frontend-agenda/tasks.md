# Tasks

> Strict TDD: cada tarea de código arranca con un test que falla (red → green → triangulate → refactor).
> No se escribe código de producción hasta que C-07 y C-12 estén `[x]` y Maxi apruebe los mockups.
> Criterio de aceptación visual: los mockups aprobados de `mockups/` y sus capturas en `mockups/previews/`.

## 0. Mockups (trabajo anticipado)

- [x] 0.1 Mockups de la agenda (día, semana y estados) y del detalle de turno, sobre el sistema de diseño rev. 1
- [x] 0.2 Capturas a 390×844 y 1440×900 en `mockups/previews/`
- [ ] 0.3 Aprobación de Maxi registrada en `design.md` y `mockups/README.md`
- [ ] 0.4 Acordar con Fran (C-12) los puntos abiertos de D2: datos del detalle y marca de visto

## 1. Base

- [ ] 1.1 Rebasar sobre `main` con C-07 y C-12; regenerar `api-types.ts` y ajustar D2 al contrato publicado
- [ ] 1.2 `features/agenda/api.ts` tipado, con tests sobre dobles de `fetch`, incluido el modo sin conexión con la caché

## 2. Lógica pura

- [ ] 2.1 `modelo.ts`: línea de tiempo del día (turnos, bloqueos, libres, fuera de horario, ahora) en la zona del consultorio, con tests en otra TZ
- [ ] 2.2 `modelo.ts`: distribución de superpuestos en columnas y rango de horas de la semana
- [ ] 2.3 `modelo.ts`: filtro de cancelados, conteos del resumen y de la tira de días, y nuevos

## 3. Vistas

- [ ] 3.1 Tarjeta de turno y bloqueo por estado y marca (D5), con nombre accesible (D9)
- [ ] 3.2 Vista diaria: línea de tiempo, tira de días, navegación y URL (D6)
- [ ] 3.3 Vista semanal: grilla en desktop y lista en mobile (D4)
- [ ] 3.4 Estados de pantalla: cargando, sin turnos, sin horario, error, sin conexión con y sin caché (D8)
- [ ] 3.5 Detalle de turno con acciones según el estado y 404 (D7)

## 4. Verificación

- [ ] 4.1 `npm run typecheck`, `lint`, `test` y `build` en verde
- [ ] 4.2 Verificación visual contra los mockups aprobados a 360×780, 390×844 y 1440×900 (capturas en `mockups/verify/`), consola sin errores y accesibilidad
- [ ] 4.3 Tarea 9.3 heredada de C-06: verificación offline con una agenda real (ver agenda con conexión, cortar la red, recargar)
