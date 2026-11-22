# Tasks

> Strict TDD en cada tarea: test que falla primero, implementación mínima, triangulación y refactor dentro del ciclo. Persistencia con Testcontainers y PostgreSQL 16 (sin H2 ni repositorios mockeados). El código se escribe cuando Maxi apruebe esta propuesta (C-09 y C-11 ya están `[x]`).

## 1. Compuertas previas (sin código de producción)

- [x] 1.1 Obtener la aprobación de Maxi para los mockups de la lista, la revisión y el aviso de la agenda; verificar `Approval: approved` en `design.md` y en `mockups/README.md` (#36, versión `dcb700b`)
- [x] 1.2 Confirmar que C-09, C-10 y C-11 están `[x]`, que la migración de C-10 tiene las columnas de DD-38 y que C-11 y C-15 guardan lo informado al marcar con una regla de comparación unificada; verificado en `origin/main` (2026-11-21): C-09 #58, C-10 #18, C-11 #46; `V6` con `ck_paciente_informado`; regla única `Paciente.datosDifierenDe` (`8cb7449`); C-15 usa `RegistrarPaciente` de C-11
- [x] 1.3 Confirmar el nombre unificado del evento de marca para revisión; `PACIENTE_MARCADO_PARA_REVISION` (`RegistrarPaciente.ACCION_MARCADO`, C-11 #38), confirmado por Maxi en la revisión de #43

## 2. Consulta de pacientes (backend)

- [ ] 2.1 Implementar `GET /api/v1/pacientes/listado` paginado (D4, D10), con el filtro `requiereRevision`, orden estable y `total`; verificar con tests de integración (Testcontainers) del filtro, el orden, el paginado y lo informado solo en los marcados
- [ ] 2.2 Implementar `GET /api/v1/pacientes/{id}` con lo informado y el turno de origen; verificar con tests de integración del detalle y del 404 para un id inexistente y para uno de otro consultorio
- [ ] 2.3 Verificar el aislamiento por consultorio en los dos endpoints con el test de otro consultorio = 404, y que la regla ArchUnit de repositorios con `ConsultorioId` sigue pasando

## 3. Revisión (backend)

- [ ] 3.1 Implementar `POST /api/v1/pacientes/{id}/confirmar-revision`: mantiene los datos, pone `requiere_revision = false` y borra lo informado en una transacción; verificar con tests de integración, incluido el 409 `SIN_REVISION_PENDIENTE` de la doble confirmación (D5)
- [ ] 3.2 Implementar `PATCH /api/v1/pacientes/{id}` con validación (nombre y apellido obligatorios hasta 80, email válido), DNI y teléfono ignorados, borrado de lo informado y 409 `SIN_REVISION_PENDIENTE` sobre un paciente sin revisión; verificar con tests de integración de cada caso
- [ ] 3.3 Exigir el `informadoAt` visto en la confirmación y en el `PATCH`, tomar el `LockDeAgenda` antes de leer el paciente y responder 409 `CONFLICTO_CONCURRENCIA` si no coincide con el guardado (D9); verificar con un test de integración (Testcontainers) en el que `RegistrarPaciente` pisa lo informado entre el detalle y la confirmación o la corrección, y el paciente sigue marcado con la variante nueva y sin `PACIENTE_REVISADO`, más el 400 sin `informadoAt` y la precedencia de `SIN_REVISION_PENDIENTE`
- [ ] 3.4 Publicar `PACIENTE_REVISADO` con solo los campos que cambiaron más `requiere_revision`, sin lo informado ni datos sin cambios (D6), y revertir el cambio si la auditoría falla; verificar con tests de integración de la corrección de un campo, la confirmación y el rollback
- [ ] 3.5 Verificar que los endpoints no escriben datos de pacientes en los logs (RN-GL-01) con el test de logs sin PII de C-03 extendido a un error en la corrección
- [ ] 3.6 Regenerar `frontend/src/shared/api-types.ts` desde el OpenAPI; verificar que `npm run api:types` no deja diferencias en CI

## 4. Pantallas (frontend)

- [ ] 4.1 Sumar las rutas `/app/pacientes` y `/app/pacientes/:id`, "Pacientes" en la barra lateral y en "Más", y el contador compartido (D3, D8); verificar con tests Vitest del router, la navegación en 360 y 1440 px y el contador con y sin revisiones
- [ ] 4.2 Implementar la lista con el filtro "Requieren revisión" / "Todos", la etiqueta "Revisar" con la línea de lo informado, "Mostrar más pacientes" y los estados vacío, cargando, error y sin conexión; verificar con tests Vitest del filtro y de cada estado
- [ ] 4.3 Implementar la comparación que muestra solo lo que difiere con la regla de D7 y la línea de lo que coincide; verificar con tests Vitest de un solo dato distinto, varios distintos y diferencias solo de mayúsculas, de tildes o de espacios
- [ ] 4.4 Implementar las tres salidas (mantener, usar lo de la reserva, corregir a mano), enviando el `informadoAt` del detalle, con la vuelta a la lista, el aviso de listo y el contador actualizado; verificar con tests Vitest de cada salida con el cliente HTTP falso
- [ ] 4.5 Implementar el formulario de corrección con validación antes de enviar, resumen de errores enlazado, DNI y teléfono de solo lectura, y los estados 404, 409 `SIN_REVISION_PENDIENTE`, 409 `CONFLICTO_CONCURRENCIA` (recarga con lo nuevo y el aviso, sin perder lo escrito, D9) y error al guardar; verificar con tests Vitest
- [ ] 4.6 Sumar a la agenda (C-13) el aviso compacto pegado al de cambios de DD-30, con el link de al menos 44 px de área táctil y sin aviso cuando no hay revisiones; verificar con tests Vitest de la agenda

## 5. Verificación integrada

- [ ] 5.1 Recorrer la agenda con el aviso, la lista y la revisión contra los mockups aprobados (versión `dcb700b`) a 360, 390 y 1440 px; guardar capturas en `mockups/verify/` y verificar sin scroll horizontal, objetivos de al menos 44 px, foco visible y sin errores de consola
- [ ] 5.2 Recorrer US-070 de punta a punta con la base real: reserva por link con un DNI existente y otro nombre, el paciente aparece marcado, se revisa y el Registro de cambios muestra `PACIENTE_REVISADO` solo con lo modificado; dejar la evidencia en el PR
- [ ] 5.3 Pasar `./gradlew build`, `npm run typecheck`, `npm run lint`, `npm test` y `npm run build`, y abrir el PR con commits conventional en español que referencien `(C-17)`; verificar el PR listo para revisión de Maxi

## Workflow follow-up

- Antes de pedir la revisión del PR de implementación, con todo en verde, el autor suma en el mismo PR el commit de cierre: `[x]` en `CHANGES.md` y `openspec archive pacientes-revision` (CLAUDE.md, ciclo del PR, #45).
- Al cerrar, actualizar en `design.md` `Visual verify` con el resultado de 5.1.
