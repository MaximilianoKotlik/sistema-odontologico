# Tasks

> Strict TDD en cada tarea: test que falla primero, implementación mínima, triangulación y refactor dentro del ciclo. El código se escribe recién cuando C-15 esté `[x]`.

## 1. Compuertas previas (sin código de producción)

- [x] 1.1 Obtener la aprobación de Maxi para los mockups de `/c/{slug}` y `/t/{token}`; verificar `Approval: approved` en `design.md` y en `mockups/README.md` (#25, versión `461d39f`)
- [ ] 1.2 Confirmar que C-06 y C-15 están mergeados y `[x]` en `CHANGES.md`; verificar con `git log origin/main --oneline`
- [ ] 1.3 Regenerar `frontend/src/shared/api-types.ts` desde el OpenAPI de C-15 y comparar los nombres con los specs de este change; verificar que `npm run typecheck` pasa y, si algún nombre cambió, ajustar los specs en el mismo PR

## 2. Rutas públicas y base compartida

- [ ] 2.1 Sumar a `publico.ts` las rutas `/c/:slug` y `/t/:token` reutilizando la compilación de patrones del router de C-06; verificar con tests Vitest que resuelven el parámetro, que `/c/` sin slug no coincide y que no montan el shell ni envían `Authorization`
- [ ] 2.2 Agregar a `nginx.conf` `Referrer-Policy: no-referrer` para `/c/` y `/t/` (design D7); verificar con un test o `curl -I` contra la imagen del frontend
- [ ] 2.3 Implementar el nombre legible de la zona horaria ("Buenos Aires", "Córdoba", con el identificador IANA como respaldo) y verificar con tests Vitest las zonas argentinas y una desconocida
- [ ] 2.4 Implementar la traducción de `ApiError` a estados de pantalla por `codigo` (design D8); verificar con tests Vitest cada código de los specs, 404, 429 con `Retry-After` y `SIN_CONEXION`

## 3. Reserva `/c/{slug}`

- [ ] 3.1 Cargar la página pública del consultorio y mostrar encabezado, prestaciones, el estado "no toma turnos online" y el 404 del slug; verificar con tests Vitest los tres casos y que no aparecen datos del odontólogo
- [ ] 3.2 Implementar la tira de días por ventanas de 14 (design D3) con días "sin turnos" según `atiende` y huecos, y el ofrecimiento del próximo día con horarios; verificar con tests Vitest sábado elegible, domingo "sin turnos", día completo y "Ver más días"
- [ ] 3.3 Mostrar los horarios en la zona del consultorio, agrupados en mañana y tarde, con el aviso de la zona, y guardar el instante UTC elegido (design D4); verificar con tests Vitest en un entorno con otra zona del dispositivo
- [ ] 3.4 Implementar el paso de datos con validación en el cliente (DNI con o sin puntos, celular, email opcional, consentimiento sin marcar con el texto del anexo legal) y el resumen de errores enlazado; verificar con tests Vitest cada regla y que el DNI se envía solo con dígitos
- [ ] 3.5 Enviar la reserva y manejar 201, `HUECO_NO_DISPONIBLE` (vuelve a horarios con los huecos del 409 y conserva los datos), `LIMITE_TURNOS`, `RESERVA_NO_DISPONIBLE`, 400 por campo y 429; verificar con tests Vitest con el cliente HTTP falso
- [ ] 3.6 Implementar la confirmación con el link destacado, "Copiar link" y el `.ics` generado en el cliente (design D6); verificar con tests Vitest el contenido del `.ics` (UTC, CRLF, link en la descripción) y que el token no queda en `localStorage`, `sessionStorage` ni cookies

## 4. Gestión `/t/{token}`

- [ ] 4.1 Cargar la vista del turno y mostrar estado con chip, nombre del paciente, consultorio y `modificableHasta`; verificar con tests Vitest que no aparecen DNI, teléfono, email ni nota interna
- [ ] 4.2 Mostrar las acciones según `puedeModificar` y `puedeReprogramar`: fuera de plazo con botón para llamar, reserva desactivada con solo cancelar y turno cancelado según `canceladoPor`; verificar con tests Vitest cada combinación
- [ ] 4.3 Implementar la reprogramación con la tira de días y horarios de `/turnos/{token}/huecos`, el diálogo de confirmación y el manejo de `HUECO_NO_DISPONIBLE` (conserva el horario original), `FUERA_DE_PLAZO`, `RESERVA_NO_DISPONIBLE` y `CONFLICTO_CONCURRENCIA`; verificar con tests Vitest
- [ ] 4.4 Implementar la cancelación con diálogo que atrapa el foco y lo devuelve al cerrarse, y el estado "Cancelaste tu turno"; verificar con tests Vitest del foco con teclado y de la respuesta 200
- [ ] 4.5 Mostrar "Este link no funciona" ante cualquier 404, al cargar o durante una acción, sin datos del consultorio; verificar con tests Vitest

## 5. Verificación integrada

- [ ] 5.1 Recorrer `/c/{slug}` y `/t/{token}` contra los mockups aprobados (versión `461d39f`) a 360, 390 y 1440 px con la API simulada; guardar capturas en `mockups/verify/` y verificar sin scroll horizontal, objetivos de al menos 44 px, foco visible, sin errores de consola y la barra fija sin tapar el último horario
- [ ] 5.2 Cronometrar una reserva completa en mobile y verificar que se completa en menos de 90 segundos; dejar la evidencia en el PR
- [ ] 5.3 Pasar `npm run typecheck`, `npm run lint`, `npm test` y `npm run build`, y abrir el PR desde `feat/C-16-frontend-reserva-publica` con commits conventional en español que referencien `(C-16)`; verificar el PR listo para revisión de Maxi

## Workflow follow-up

- Cuando Maxi apruebe el PR, el autor suma en el mismo PR el commit de cierre: `[x]` en `CHANGES.md` y `openspec archive frontend-reserva-publica` (CLAUDE.md, ciclo del PR).
- Al cerrar, actualizar en `design.md` `Visual verify` con el resultado de 5.1.
