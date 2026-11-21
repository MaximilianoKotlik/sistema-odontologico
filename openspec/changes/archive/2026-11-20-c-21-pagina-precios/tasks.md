# Tasks

## 1. Compuertas previas (sin código de producción)

- [x] 1.1 Resolver PA-03 y registrar la decisión como `DD-NN` en `knowledge-base/09_decisiones_y_supuestos.md` con modelo de plan, duración de prueba e importes; verificar que la KB ya no marca PA-03 como bloqueante y que el mockup usa los importes aprobados
- [x] 1.2 Confirmar que C-02 y C-06 están mergeados en `origin/main` y `[x]` en `CHANGES.md`; verificar con `git log origin/main --oneline` que aparecen ambos merges
- [x] 1.3 Obtener aprobación de Maxi para el mockup de `/precios`; verificar que el PR en borrador contiene `mockups/precios.html` y el comentario de aprobación

## 2. Catálogo versionado y contrato público

- [x] 2.1 Agregar la fuente versionada de planes con importes como texto decimal, moneda, impuestos incluidos y `actualizadoEn`, con validación que rechaza valores inválidos; verificar con un test que falla primero y luego `./gradlew test` en verde
- [x] 2.2 Exponer `GET /api/v1/public/planes` sin autenticación con el contrato de `design.md`; verificar con test MockMvc que responde 200 sin token y que `/v3/api-docs` incluye el esquema
- [x] 2.3 Responder Problem Details con `codigo = PLANES_NO_DISPONIBLES` ante configuración inválida o faltante, sin importes parciales; verificar con test de error y ausencia de credenciales

## 3. Página pública `/precios`

- [x] 3.1 Regenerar `frontend/src/shared/api-types.ts` desde el OpenAPI del backend; verificar que `npm run api:types` no produce diff en CI
- [x] 3.2 Renderizar planes, inclusiones, importe en ARS, leyenda de prueba de 30 días sin tarjeta y fecha en `America/Argentina/Buenos_Aires`; verificar con tests Vitest y comparación visual contra el mockup a 360 px
- [x] 3.3 Implementar estados de carga y error reintentable sin mostrar importes inventados; verificar con tests Vitest del estado de error y del formateo `Intl.NumberFormat('es-AR', ...)`
- [x] 3.4 Aplicar tokens del sistema de diseño, voseo, foco visible, objetivos táctiles ≥ 44 px y `prefers-reduced-motion`; verificar con checklist AA en el PR y test de atributos de accesibilidad

## 4. Verificación de integración y entrega

- [x] 4.1 Recorrer `/precios` sin sesión en viewport de 360 px y confirmar que no se exponen datos de consultorios ni de pacientes; verificar con captura o nota de evidencia en el PR
- [x] 4.2 Abrir PR desde `feat/C-21-pagina-precios` creada desde `main` actualizado, con commits conventional en español que referencien `(C-21)` y sin líneas `Co-authored-by`; verificar el PR en estado listo para revisión de Maxi

## Workflow follow-up

- Cuando Maxi apruebe el PR, el autor suma en el mismo PR el commit de cierre: `[x]` en `CHANGES.md` y `openspec archive` (CLAUDE.md, ciclo del PR).
