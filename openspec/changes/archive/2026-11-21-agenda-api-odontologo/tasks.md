# Tasks

> Strict TDD en cada tarea: test en rojo → verde → triangular → refactor dentro del ciclo. **No arrancar** hasta que se cumplan dos condiciones:
> - C-11 está `[x]`.
> - Maxi aprobó esta propuesta.
>
> Verificación base de cada grupo: `./gradlew test` en verde. Los tests de API usan MockMvc con JWT y Testcontainers (`IntegracionPostgresTest`), cada endpoint lleva su test de aislamiento (otro consultorio = 404, regla dura 6) y los datos son ficticios (regla dura 17).

## 1. Preparación

- [x] 1.1 Rebasar sobre `main` con C-11 mergeado (#46) y ajustar los nombres de los casos de uso. Aplicar las decisiones de Maxi sobre D3, D5 y D6 en los specs y en esta lista, y registrar DD-39 (verificación: la suite existente en verde)
- [x] 1.2 `turno.novedad_paciente_at`: si C-15 ya la creó, verificar que exista. Si no, test de integración y migración `V{n}__turno_novedad_paciente.sql` con `ADD COLUMN IF NOT EXISTS` (verificación: test de la columna)

## 2. Agenda y detalle

- [x] 2.1 `GET /api/v1/agenda`: rango de hasta 42 días, turnos y bloqueos en orden, cancelados incluidos, con `novedadPacienteAt` y sin DNI, teléfono, email ni texto de la nota (verificación: escenarios de "Agenda de un rango")
- [x] 2.2 `cambioPaciente` y el horario anterior desde la auditoría (D3), en una sola consulta para toda la agenda (verificación: escenarios "Reserva por link sin ver" y "Turno reprogramado por el paciente")
- [x] 2.3 `GET /api/v1/turnos/{id}` con el detalle completo y el 404 para un turno ajeno (verificación: escenarios de "Detalle de un turno")
- [x] 2.4 `POST /api/v1/turnos/{id}/visto {novedadAt}` (D5, DD-39):
  - idempotente y 204, también cuando hay una novedad más nueva, que no se borra;
  - test de la carrera: novedad de las 10:00, otra de las 10:01 y visto con la de las 10:00, que deja la de las 10:01;
  - 400 sin `novedadAt`, 404 para un turno ajeno y sin evento de auditoría.

  (verificación: escenarios de "Cambios del paciente sin ver", incluido "Novedad nueva mientras se marcaba como visto")
- [x] 2.5 `GET /api/v1/turnos/fuera-de-disponibilidad` (verificación: escenarios de "Turnos fuera de la disponibilidad")

## 3. Escrituras

- [x] 3.1 `POST /api/v1/turnos`, con paciente existente, alta rápida y `pacienteReutilizado` con los datos guardados. Con otro nombre, el paciente queda marcado y el turno se vincula como turno de origen en la misma transacción (verificación: escenarios de "Crear un turno por la API")
- [x] 3.2 `PATCH /estado`, `POST /reprogramar`, `PATCH /duracion` y `PATCH /nota`, con `version` (verificación: escenarios de "Cambiar un turno por la API")
- [x] 3.3 `TURNO_SOLAPADO` con `turnoEnConflicto` y sin datos personales (verificación: escenario "Solapamiento con un turno")
- [x] 3.4 La tabla de errores → estado HTTP para todos los códigos de C-10 y C-11 (verificación: escenarios de "Errores de la agenda con su estado HTTP")
- [x] 3.5 `POST`, `DELETE` y `GET /api/v1/bloqueos?desde=` (verificación: escenarios de "Bloqueos por la API")

## 4. Pacientes

- [x] 4.1 `GET /api/v1/pacientes?q=`: por nombre y apellido, por DNI y por teléfono, un tope de 20 y el mínimo de 3 caracteres (verificación: escenarios de "Buscar pacientes")
- [x] 4.2 `q` enmascarado en el log de acceso y en el MDC (verificación: escenario "DNI fuera del log", con la salida capturada)
- [x] 4.3 Caché offline sin datos personales de más: un test en `frontend/src/pwa/opciones.test.ts` fija que el runtime caching de `agenda-v1` no guarda `GET /api/v1/pacientes?q=` ni `GET /api/v1/turnos/{id}`, que traen DNI y teléfono. Solo guarda `GET /api/v1/agenda` (revisión de Maxi en #39) (verificación: el test y el CI de frontend)

## 5. Cierre del change

- [x] 5.1 OpenAPI con las 13 operaciones y `npm run api:types` regenerado y commiteado (verificación: `ContratoOpenApiTest` y el CI de frontend)
- [x] 5.2 `./gradlew test` completo en verde, `ArchitectureTest` en verde y cobertura ≥ 80 % en `agenda.application` y `agenda.web` (verificación: CI del PR)
- [x] 5.3 PR listo con la evidencia y la revisión de Maxi. Avisar a Nacho (C-13 y C-14) del contrato final y de los puntos "a acordar con C-12" que se cerraron: `novedadPacienteAt` en el visto, y que los huecos sugeridos no cuentan los sobreturnos ni el pasado (verificación: PR con revisor asignado)

## Workflow follow-up

- Antes de pedir la revisión, con todo en verde, el autor suma el commit de cierre en el PR de implementación: `[x]` en `CHANGES.md` (PR #N, fecha) y `openspec archive agenda-api-odontologo` (ciclo de #45). Si #39 se mergea como PR de solo propuesta, la implementación va en un PR nuevo y el `[x]` lleva ese número.
