# Tasks

> Strict TDD: cada tarea de código arranca con un test que falla (red → green → triangulate → refactor).
> No se escribe código de producción hasta que C-13 esté `[x]` y Maxi apruebe los mockups.
> Criterio de aceptación visual: los mockups aprobados de `mockups/` y sus capturas en `mockups/previews/`.

## 0. Mockups (trabajo anticipado)

- [x] 0.1 Mockups de nuevo turno, acciones sobre el turno y bloqueos, con sus estados, sobre el sistema de diseño rev. 1
- [x] 0.2 Capturas a 390×844 y 1440×900 en `mockups/previews/`
- [x] 0.3 Aprobación de Maxi registrada en `design.md` y `mockups/README.md` (2026-11-20, #31)
- [x] 0.4 Acordado: Maxi sumó a C-12 la búsqueda de pacientes, el listado de bloqueos futuros, el turno con el que choca en `TURNO_SOLAPADO` y los turnos fuera de disponibilidad (#34)

## 1. Base

- [x] 1.1 Rebasar sobre `main` con C-13 y C-12; regenerar `api-types.ts` y verificar los endpoints y códigos de error (rama nueva desde `main` con C-13; `api-types.ts` ya traía todos los endpoints; códigos y propiedades verificados en el código de C-12: `turnoEnConflicto`, `turnosEnConflicto`, `bloqueosEnConflicto`)
- [x] 1.2 `features/turnos/api.ts` y `features/bloqueos/api.ts` tipados, con tests sobre dobles de `fetch`

## 2. Lógica pura

- [x] 2.1 `validaciones.ts`: fin derivado, múltiplos de 5, duración de 5 a 480, DNI, celular, nota ≤ 500, motivo ≤ 200 (turno) y ≤ 120 (bloqueo), rango del bloqueo y días completos en la zona del consultorio
- [x] 2.2 `transiciones.ts`: acciones válidas por estado y por hora actual (RN-AG-06) (se reutiliza `accionesDelTurno` de `agenda/modelo.ts` de C-13, ya probada, en vez de duplicarla)
- [x] 2.3 `errores.ts`: cada código de D5 a mensaje, campo y acción
- [x] 2.4 Huecos libres que alcanzan para una duración, reutilizando el modelo de C-13

## 3. Vistas

- [x] 3.1 Nuevo turno y sobreturno: buscador y alta rápida de paciente, prestación, hora, fin derivado, aviso de superposición y salidas de D5
- [x] 3.2 Confirmar, cancelar con motivo y marcar ausente
- [x] 3.3 Reprogramar con huecos libres
- [x] 3.4 Ajustar duración
- [x] 3.5 Nota interna con contador
- [x] 3.6 Bloqueos: listado, vacío, alta (rango o días completos), `BLOQUEO_CON_TURNOS` y eliminar
- [x] 3.7 Aviso de turnos fuera de horario en la pestaña Horarios de C-09, con `GET /api/v1/turnos/fuera-de-disponibilidad` (D10)

## 4. Verificación

- [x] 4.1 `npm run typecheck`, `lint`, `test` y `build` en verde (703 tests)
- [x] 4.2 Verificación visual contra los mockups aprobados a 360×780, 390×844 y 1440×900 (capturas en `mockups/verify/`), consola sin errores y accesibilidad (foco en diálogos, contraste AA, objetivos ≥ 44 px)
- [x] 4.3 FL-03 completo contra el backend local de C-12 (cuenta de prueba local con datos ficticios): turno desde un hueco, 409 `TURNO_SOLAPADO` → sobreturno, confirmar, duración (con 409 fuera de horario), reprogramar, nota, cancelar con motivo, bloqueo con 409 `BLOQUEO_CON_TURNOS`, alta y baja de bloqueo, y aviso de turnos fuera de horario
