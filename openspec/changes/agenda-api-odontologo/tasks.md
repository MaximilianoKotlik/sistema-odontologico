# Tasks

> Strict TDD en cada tarea: test en rojo → verde → triangular → refactor dentro del ciclo. **No arrancar** hasta que se cumplan dos condiciones:
> - C-11 está `[x]`.
> - Maxi aprobó esta propuesta.
>
> Verificación base de cada grupo: `./gradlew test` en verde. Los tests de API usan MockMvc con JWT y Testcontainers (`IntegracionPostgresTest`), cada endpoint lleva su test de aislamiento (otro consultorio = 404, regla dura 6) y los datos son ficticios (regla dura 17).

## 1. Preparación

- [ ] 1.1 Rebasar sobre `main` con C-11 mergeado y ajustar los nombres de los casos de uso. Aplicar las decisiones de Maxi sobre D3, D5 y D6 en los specs y en esta lista (verificación: la suite existente en verde)
- [ ] 1.2 `turno.novedad_paciente_at`: si C-15 ya la creó, verificar que exista. Si no, test de integración y migración `V{n}__turno_novedad_paciente.sql` con `ADD COLUMN IF NOT EXISTS` (verificación: test de la columna)

## 2. Agenda y detalle

- [ ] 2.1 `GET /api/v1/agenda`: rango de hasta 42 días, turnos y bloqueos en orden, cancelados incluidos y sin DNI, teléfono, email ni texto de la nota (verificación: escenarios de "Agenda de un rango")
- [ ] 2.2 `cambioPaciente` y el horario anterior desde la auditoría (D3), en una sola consulta para toda la agenda (verificación: escenarios "Reserva por link sin ver" y "Turno reprogramado por el paciente")
- [ ] 2.3 `GET /api/v1/turnos/{id}` con el detalle completo y el 404 para un turno ajeno (verificación: escenarios de "Detalle de un turno")
- [ ] 2.4 `POST /api/v1/turnos/{id}/visto`: idempotente, 204, 404 para un turno ajeno, y con o sin evento según la decisión de D5 (verificación: escenarios de "Cambios del paciente sin ver")
- [ ] 2.5 `GET /api/v1/turnos/fuera-de-disponibilidad` (verificación: escenarios de "Turnos fuera de la disponibilidad")

## 3. Escrituras

- [ ] 3.1 `POST /api/v1/turnos`, con paciente existente, alta rápida y `pacienteReutilizado` (verificación: escenarios de "Crear un turno por la API")
- [ ] 3.2 `PATCH /estado`, `POST /reprogramar`, `PATCH /duracion` y `PATCH /nota`, con `version` (verificación: escenarios de "Cambiar un turno por la API")
- [ ] 3.3 `TURNO_SOLAPADO` con `turnoEnConflicto` y sin datos personales (verificación: escenario "Solapamiento con un turno")
- [ ] 3.4 La tabla de errores → estado HTTP para todos los códigos de C-10 y C-11 (verificación: escenarios de "Errores de la agenda con su estado HTTP")
- [ ] 3.5 `POST`, `DELETE` y `GET /api/v1/bloqueos?desde=` (verificación: escenarios de "Bloqueos por la API")

## 4. Pacientes

- [ ] 4.1 `GET /api/v1/pacientes?q=`: por nombre y apellido, por DNI y por teléfono, un tope de 20 y el mínimo de 3 caracteres (verificación: escenarios de "Buscar pacientes")
- [ ] 4.2 `q` enmascarado en el log de acceso y en el MDC (verificación: escenario "DNI fuera del log", con la salida capturada)

## 5. Cierre del change

- [ ] 5.1 OpenAPI con las 13 operaciones y `npm run api:types` regenerado y commiteado (verificación: `ContratoOpenApiTest` y el CI de frontend)
- [ ] 5.2 `./gradlew test` completo en verde, `ArchitectureTest` en verde y cobertura ≥ 80 % en `agenda.application` y `agenda.web` (verificación: CI del PR)
- [ ] 5.3 PR listo con la evidencia y la revisión de Maxi. Avisar a Nacho (C-13 y C-14) del contrato final y de los puntos "a acordar con C-12" que se cerraron (verificación: PR con revisor asignado)

## Workflow follow-up

- Con el PR aprobado, el autor suma el commit de cierre en el mismo PR: `[x]` en `CHANGES.md` (PR #N, fecha) y `openspec archive agenda-api-odontologo`. Maxi vuelve a aprobar ese commit y mergea.
