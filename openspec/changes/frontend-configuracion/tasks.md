# Tasks

> Strict TDD: cada tarea de código arranca con un test que falla (red → green → triangulate → refactor).
> No se escribe código de producción hasta que C-07 y C-08 estén `[x]` y Maxi apruebe los mockups.
> Criterio de aceptación visual: los mockups aprobados de `mockups/` y sus capturas en `mockups/previews/`.

## 0. Mockups (trabajo anticipado)

- [x] 0.1 Mockups de Bienvenida, Consultorio, Prestaciones y Horarios con sus estados, sobre el sistema de diseño rev. 1
- [x] 0.2 Capturas a 390×844 y 1440×900 en `mockups/previews/`
- [x] 0.3 Aprobación de Maxi registrada en `design.md` y `mockups/README.md` (2026-11-20, #23)

## 1. Base

- [x] 1.1 Rebasar sobre `main` con C-07 y C-08; regenerar `api-types.ts` y verificar los 9 endpoints y los códigos de error de C-08 (incluido el de anticipación de reserva ≥ horizonte, para completar D8)
- [x] 1.2 Guarda de navegación en el router de C-06 si no existe (D9), con test
- [x] 1.3 `features/configuracion/api.ts` tipado con `api-types.ts`, con tests sobre dobles de `fetch`

## 2. Lógica pura

- [x] 2.1 `unidades.ts`: horas y días ⇄ minutos, y minutos no enteros mostrados como minutos (D4)
- [x] 2.2 `validaciones.ts`: slug (normalización y RN-TE-03), duración (5–480, múltiplo de 5), teléfono obligatorio (D4b) y reglas de reserva (rangos de D4, anticipación de reserva menor que el horizonte)
- [x] 2.3 `validaciones.ts`: grilla semanal (fin > inicio, múltiplos de 5, solapadas vs. contiguas por día)
- [x] 2.4 `errores.ts`: mapeo de cada código de D8 a campo, prestación o día, incluidas `sugerencias` y `dias`

## 3. Pantallas de Configuración

- [x] 3.1 Pestañas con rutas y `aria-current` (D2), reemplazando el placeholder de C-06
- [x] 3.2 Consultorio: formulario, link público copiable, slug con vista previa y sugerencias, interruptor de reserva pública, zona horaria, resumen de errores y conflicto de concurrencia
- [ ] 3.3 Prestaciones: listado, estado vacío, diálogo de alta y edición, subir/bajar, desactivar con confirmación, inactivas y activar
- [ ] 3.4 Horarios: tarjetas por día, agregar y quitar franjas, copiar a otros días, barra de cambios sin guardar, errores por día y confirmación de semana vacía
- [ ] 3.5 Cambios sin guardar: guarda de navegación y `beforeunload` (D9)

## 4. Asistente de primer ingreso

- [ ] 4.1 Redirección a `/app/bienvenida` sin prestaciones activas, después del login y al restaurar la sesión (D3)
- [ ] 4.2 Pasos 1 a 3 reutilizando los formularios de la sección 3, con guardado por paso y atajos (D6, D7)
- [ ] 4.3 Paso 4: copiar link y Compartir solo con Web Share API
- [ ] 4.4 "Terminar después" y retomar

## 5. Verificación

- [ ] 5.1 `npm run typecheck`, `lint`, `test` y `build` en verde
- [ ] 5.2 Verificación visual contra los mockups aprobados a 390×844 y 1440×900 (capturas en `mockups/verify/`), consola sin errores y accesibilidad (foco, contraste AA, objetivos ≥ 44 px, `prefers-reduced-motion`)
- [ ] 5.3 Flujo FL-02 completo contra el backend local de C-08 con el seed `demo`
