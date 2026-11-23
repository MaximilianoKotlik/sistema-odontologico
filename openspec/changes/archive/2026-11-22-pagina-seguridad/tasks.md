# Tasks

> Strict TDD en cada tarea: test que falla primero, implementación mínima, triangulación y refactor dentro del ciclo. Governance **ALTO**: el código se escribe recién cuando C-19 esté `[x]` y Maxi apruebe esta propuesta.

## 1. Compuertas previas (sin código de producción)

- [x] 1.1 Obtener la aprobación de Maxi para el mockup de `/seguridad`; verificar `Approval: approved` en `design.md` y en `mockups/README.md` (#40, versión `1609d16`)
- [x] 1.2 Confirmar que C-19 está mergeado y `[x]` en `CHANGES.md` (C-03 y C-06 ya lo están); verificar con `git log origin/main --oneline` (C-19 #61, 2026-11-21)
- [x] 1.3 Confirmar que no queda ninguna pregunta Alta abierta: PA-02 cerró con DD-40 (#42) y Maxi lo confirmó en la revisión de #44; verificar `design.md` §Open Questions

## 2. Fuente versionada

- [x] 2.1 Crear `frontend/src/contenido/seguridad.json` con el contenido aprobado del mockup (versión `1609d16`, con el texto de hosting actualizado por DD-40 en #44), el respaldo y `requiere` de cada afirmación (D1); verificar con un test Vitest que el type guard lo acepta y rechaza una afirmación sin `texto`
- [x] 2.2 Escribir el test de que cada afirmación declara respaldo y de que la fuente no tiene frases prohibidas (D4); verificar que falla con una afirmación sin respaldo y con "certificado" agregados a propósito, y pasa con la fuente real

## 3. Página

- [x] 3.1 Sumar `/seguridad` a la tabla de páginas de `publico.ts` (D5); verificar con tests Vitest que no monta el shell, no envía `Authorization` y pone el título "Seguridad y datos · {marca}"
- [x] 3.2 Armar la página desde la fuente (D2): aviso de "sin revisión legal" primero, índice, ocho temas, tabla de roles, "Lo que todavía no está" con ícono y texto, fecha en es-AR y pie; verificar con tests Vitest que el texto coincide con la fuente, que el respaldo no se muestra y que hay un solo `h1`
- [x] 3.3 Aplicar los tokens del sistema de diseño, la tabla de roles apilada en mobile y los links del índice y del pie con área táctil de al menos 44 px; verificar con un test de atributos de accesibilidad y el checklist AA en el PR (checklist AA en el PR)

## 4. Revisión antes de publicar

- [x] 4.1 Implementar `npm run seguridad:revisar` (D3): falla si una afirmación requiere un change sin `[x]`, si no tiene respaldo o si hay `(TBD)`, y lista cada problema; verificar con tests del script usando un `CHANGES.md` y una fuente de prueba para cada caso
- [x] 4.2 Documentar el comando en el README del frontend; verificar el texto en el README y que `CHANGES.md` §C-23 lo incluye en su criterio de salida (el repo documenta los comandos del frontend en el README raíz, que no tiene uno propio en `frontend/`: sección "Página `/seguridad` antes de publicar")

## 5. Verificación integrada

- [x] 5.1 Recorrer `/seguridad` contra el mockup aprobado (versión `1609d16`) a 360, 390 y 1440 px; guardar capturas en `mockups/verify/` y verificar sin scroll horizontal, objetivos de al menos 44 px, foco visible y sin errores de consola (2026-11-22: 3 capturas en `mockups/verify/`, sin problemas; foco verificado con Tab real)
- [x] 5.2 Revisar cada afirmación de la fuente contra el código mergeado (RN-PL-06) y dejar la tabla afirmación → respaldo → evidencia en el PR (tabla en el PR, revisada contra `origin/main` el 2026-11-22)
- [x] 5.3 Pasar `npm run typecheck`, `npm run lint`, `npm test` y `npm run build`, correr `npm run seguridad:revisar` (se espera que falle por los `(TBD)` hasta cumplir el criterio de salida de C-23) y abrir el PR con commits conventional en español que referencien `(C-22)`; verificar el PR listo para revisión de Maxi (#71; `seguridad:revisar` falla como se espera por C-16, C-17, C-23 y los `(TBD)`)

## Workflow follow-up

- Antes de pedir la revisión del PR de implementación, con todo en verde, el autor suma en el mismo PR el commit de cierre: `[x]` en `CHANGES.md` y `openspec archive pagina-seguridad` (CLAUDE.md, ciclo del PR, #45).
- La publicación de `/seguridad` espera a que `npm run seguridad:revisar` pase y se cumpla el criterio de salida de C-23 (DD-40).
