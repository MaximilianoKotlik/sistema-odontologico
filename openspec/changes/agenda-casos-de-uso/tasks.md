# Tasks

> Strict TDD en cada tarea: test en rojo → verde → triangular → refactor dentro del ciclo. **No arrancar** hasta que se cumplan dos condiciones:
> - C-10 está `[x]`.
> - Maxi aprobó esta propuesta (ALTO).
>
> Verificación base de cada grupo: `./gradlew test` en verde. Los tests de aplicación extienden `IntegracionPostgresTest` (Testcontainers con PostgreSQL 16, regla dura 8) y usan solo datos ficticios (regla dura 17).

## 1. Preparación

- [x] 1.1 Rebasar sobre `main` con C-10 mergeado y ajustar los nombres a lo que quedó en `agenda.domain`. Confirmar que `Paciente.marcadoParaRevision(DatosInformados)` y `datosDifierenDe` (NFD) quedaron como en #18 (verificación: la suite existente en verde)
- [x] 1.2 Corregir en `CHANGES.md` el alcance de C-11: deduplicación por DNI (DD-24), no por teléfono (verificación: diff de CHANGES.md en el PR)

## 2. Lock y lecturas

- [ ] 2.1 `LockDeAgenda` con `SELECT 1 FROM consultorio WHERE id = :id FOR UPDATE`. Tests con dos transacciones:
  - el lock de agenda bloquea a `ReemplazarDisponibilidad` y viceversa;
  - repetirlo en la misma transacción no espera.

  (verificación: escenario "Un turno mientras se reemplaza la grilla")
- [ ] 2.2 `TurnoRepositorio` y `BloqueoRepositorio` JDBC:
  - buscar por id con el consultorio;
  - los turnos activos y los bloqueos que intersectan un intervalo;
  - insertar;
  - actualizar con la `version` esperada (`OptimisticLockingFailureException` si no coincide);
  - eliminar un bloqueo.

  Cada consulta lleva `consultorio_id` y tiene su test de aislamiento (verificación: `ArchitectureTest` en verde y tests de integración de cada método)
- [ ] 2.3 `ConsultaDeDisponibilidad`: arma la `DisponibilidadSemanal` con la grilla y la zona del consultorio por los puertos de `consultorio.application` (verificación: test con la grilla demo y `America/Argentina/Buenos_Aires`)

## 3. Pacientes

- [ ] 3.1 `PacienteRepositorio` JDBC: alta con `ON CONFLICT (consultorio_id, dni) DO NOTHING`, búsqueda por id y DNI con `FOR UPDATE`, la marca de revisión con las columnas `*_informado` y el turno de origen (verificación: escenario "Registro simultáneo" y test de aislamiento)
- [ ] 3.2 `RegistrarPaciente.registrar`: crea, reutiliza sin sobrescribir con los datos guardados, y con un nombre distinto marca y guarda lo informado, pisándolo si ya estaba marcado (DD-38). Con el mismo nombre no cambia nada (verificación: escenarios "Primera marca", "Segunda reserva distinta sin revisar" y "DNI existente con el mismo nombre")
- [ ] 3.3 `RegistrarPaciente.vincularTurnoDeOrigen`: completa `informado_turno_id` en la misma transacción solo si el registro marcó al paciente (verificación: escenarios de "Turno de origen de lo informado")
- [ ] 3.4 Auditoría de pacientes: `PACIENTE_CREADO` y `PACIENTE_MARCADO_PARA_REVISION` (también en la segunda variante) sin PII ni lo informado (verificación: escenarios de "Auditoría de los pacientes")

## 4. Turnos

- [ ] 4.1 `CrearTurno`:
  - manual, sobreturno y desde un paciente;
  - prestación inactiva, ajena o inexistente;
  - el orden de errores del dominio;
  - `TURNO_CREADO`.

  (verificación: escenarios "Crear un turno" y "Sobreturno solo del odontólogo")
- [ ] 4.2 `CambiarEstadoTurno`: confirmar, cancelar con `cancelado_por` según el actor y marcar ausente con el `Clock`. Las transiciones inválidas no cambian el turno (verificación: escenarios de "Cambiar el estado de un turno")
- [ ] 4.3 `ReprogramarTurno` y `AjustarDuracion`: se validan sin contar al propio turno y, si fallan, no cambian nada (verificación: escenarios de "Reprogramar un turno" y "Ajustar la duración de un turno")
- [ ] 4.4 `EditarNota`: hasta 500 caracteres, solo el odontólogo, y sin evento si la nota es la misma (verificación: escenarios de "Nota interna del odontólogo")
- [ ] 4.5 Auditoría de cada operación con los valores de D8, y rollback si falla la inserción del evento (verificación: escenarios de "Auditoría de los turnos")
- [ ] 4.6 Concurrencia:
  - versión vieja → `CONFLICTO_CONCURRENCIA`;
  - dos `CrearTurno` simultáneos al mismo horario → uno `TURNO_SOLAPADO`;
  - `ex_turno_sin_solapamiento` mapeado en el manejador.

  (verificación: escenarios de "Ediciones concurrentes" y "Escrituras de agenda serializadas por consultorio")

## 5. Bloqueos

- [ ] 5.1 `CrearBloqueo` y `EliminarBloqueo`: `BLOQUEO_CON_TURNOS` con todos los turnos, los cancelados no cuentan, los bloqueos superpuestos se permiten, la auditoría y el aislamiento (verificación: escenarios de `gestion-de-bloqueos`)
- [ ] 5.2 Un turno y un bloqueo creados a la vez no se pisan (verificación: escenario "Un turno y un bloqueo a la vez")

## 6. Cierre del change

- [ ] 6.1 `./gradlew test` completo en verde, `ArchitectureTest` en verde y cobertura ≥ 80 % en `agenda.application` (verificación: CI del PR)
- [ ] 6.2 PR listo con la evidencia y la revisión de Maxi. Avisar a Mati (C-15) y a quien haga C-12 de los nombres finales de los casos de uso y de los errores nuevos (`PRESTACION_INACTIVA` y `ACCION_NO_PERMITIDA`) (verificación: PR con revisor asignado)

## Workflow follow-up

- Con el PR aprobado, el autor suma el commit de cierre en el mismo PR: `[x]` en `CHANGES.md` (PR #N, fecha) y `openspec archive agenda-casos-de-uso`. Maxi vuelve a aprobar ese commit y mergea.
