# Tasks

> Strict TDD en cada tarea: test en rojo → verde → triangular → refactor dentro del ciclo. **No arrancar** hasta que se cumplan dos condiciones:
> - C-08 está `[x]`.
> - Maxi aprobó esta propuesta (ALTO).
>
> PA-01 ya está resuelta por DD-24 (paciente por DNI).
>
> Verificación base de cada grupo: `./gradlew test` en verde. Los tests de integración extienden `IntegracionPostgresTest`, escriben con `JdbcClient` (design D1) y usan solo datos ficticios (regla dura 17).

## 1. Preparación

- [ ] 1.1 Rebasar sobre `main` con C-03, C-04, C-05 y C-08 mergeados. Fijar el número de la migración (`V6` si C-08 entra con `V5`) y verificar que existan `uk_tipo_prestacion_consultorio_id` y `ErrorDeNegocio.propiedades()`; si falta `propiedades()`, agregarla con su test en `ManejadorGlobalDeErroresTest` (verificación: la suite existente en verde después del rebase)
- [x] 1.2 PA-01 registrada como [DD-24](../../../knowledge-base/09_decisiones_y_supuestos.md) (paciente por DNI). El spec `pacientes`, design D3, D8 y D9 y esta lista ya están actualizados (verificación: revisión de Maxi en #18)
- [ ] 1.3 Regla ArchUnit `agenda.domain` no depende de `..consultorio..`, con un fixture que la viole (verificación: `ReglasDeArquitecturaTest` detecta el fixture y `ArchitectureTest` pasa)

## 2. Migración

- [ ] 2.1 Test de integración de `paciente`:
  - DNI repetido en el mismo consultorio falla y en otro consultorio pasa;
  - DNI con letras o con un largo distinto de 7 u 8 falla por `ck_paciente_dni`;
  - teléfono repetido en el mismo consultorio con otro DNI pasa;
  - origen `LINK` sin consentimiento falla;
  - email sin distinguir mayúsculas;
  - `app_rw` sin `DELETE`.

  Después, crear la tabla en `V{n}__agenda_paciente_turno_bloqueo.sql` (verificación: escenarios "DNI único por consultorio" y "Los pacientes no se borran")
- [ ] 2.2 Test de integración de `turno`:
  - CHECKs de duración, fin (`ck_turno_fin`), grilla de 5 minutos (incluido un inicio con fracción de segundo, como 09:00:00.6, que falla), estado, origen, sobreturno `MANUAL` y coherencia de la cancelación;
  - FKs compuestas a `paciente` y `tipo_prestacion` de otro consultorio fallan;
  - `app_rw` sin `DELETE`.

  Después, crear la tabla (verificación: escenarios "La base rechaza un fin incoherente", "La base exige coherencia de la cancelación", "Paciente de otro consultorio" y "Borrado con el rol de la aplicación")
- [ ] 2.3 Test de integración de `ex_turno_sin_solapamiento`:
  - el solapamiento activo falla;
  - pasan los turnos contiguos, el sobreturno, el turno cancelado, el turno ausente y los de otro consultorio;
  - reactivar un cancelado sobre un horario ocupado falla;
  - `ck_turno_fin` da el mismo resultado con dos `TimeZone` de sesión distintas.

  Después, crear la restricción (verificación: escenarios "La base rechaza el solapamiento aunque se saltee la validación" y "Reactivar un horario ocupado en la base")
- [ ] 2.4 Test de integración de `bloqueo`: fin no posterior al inicio falla, horario fuera de la grilla falla, dos bloqueos superpuestos pasan, `app_rw` conserva `DELETE` y el índice GiST existe. Después, crear la tabla (verificación: escenario "La base rechaza un bloqueo invertido")

## 3. Value objects y entidades

- [ ] 3.1 `Intervalo` y `Duracion`: semiabierto, solapamiento y contigüidad, alineación a 5 minutos, rango de 5 a 480 y `DURACION_INVALIDA` (verificación: tests unitarios de cada borde)
- [ ] 3.2 `MaquinaDeEstadosTurno`: las transiciones válidas, `AUSENTE` solo después del inicio con `ahora` inyectado, estados terminales y `TRANSICION_INVALIDA` para el resto (verificación: test parametrizado sobre la matriz completa de estado × acción)
- [ ] 3.3 `Turno`:
  - `fin` derivado;
  - `reprogramar` conserva la duración y el id;
  - `ajustarDuracion` recalcula `fin`;
  - copia de la duración de la prestación;
  - sobreturno siempre `MANUAL` que conserva la marca al cancelarse;
  - nota de hasta 500 caracteres y motivo de hasta 200;
  - inicio fuera de la grilla da `HORARIO_INVALIDO`.

  (verificación: escenarios del spec `turnos` sobre duración, estados, sobreturno y datos acotados)
- [ ] 3.4 `Bloqueo`: rango válido, varios días, grilla de 5 minutos y motivo de hasta 120 caracteres (verificación: escenarios de "Bloqueo con inicio y fin explícitos")
- [ ] 3.5 Value object `Dni`: quita puntos y espacios, exige 7 u 8 dígitos y rechaza con `DNI_INVALIDO`; `toString()` sin el número (verificación: escenarios de "DNI normalizado")
- [ ] 3.6 `Paciente`: recorte y largo de nombre y apellido, `Dni`, teléfono obligatorio por `NormalizadorDeTelefono`, email opcional, consentimiento obligatorio con origen `LINK`, `datosDifierenDe` para un DNI existente, marcar para revisión y `toString()` sin PII (verificación: escenarios del spec `pacientes`)

## 4. Validadores

- [ ] 4.1 `DisponibilidadSemanal`: materializa las franjas de una fecha en la zona del consultorio y fusiona las contiguas (design D5) (verificación: test con `America/Argentina/Buenos_Aires` y escenario "Turno sobre franjas contiguas")
- [ ] 4.2 `ValidadorDeSolapamiento`:
  - el orden bloqueo → disponibilidad → solapamiento;
  - `TURNO_SOLAPADO` con `turnosEnConflicto`;
  - los turnos contiguos, cancelados y ausentes no cuentan;
  - el sobreturno solo se valida contra bloqueos;
  - un turno no choca consigo mismo al reprogramarse.

  (verificación: escenarios de "Sin solapamiento entre turnos activos" y "Turno dentro de la disponibilidad y fuera de los bloqueos")
- [ ] 4.3 `ValidadorDeBloqueo`: `BLOQUEO_CON_TURNOS` con **todos** los turnos activos en conflicto (sobreturnos incluidos); los cancelados y los contiguos no cuentan; los bloqueos superpuestos son válidos (verificación: escenarios del spec `bloqueos`)

## 5. Cálculo de huecos

- [ ] 5.1 `CalculadoraDeHuecos` sin límites: paso desde el inicio de la franja, duración mayor que la franja, día sin disponibilidad, turnos, sobreturnos, cancelados y bloqueos, resultado en orden cronológico y en UTC (verificación: escenarios de "Huecos dentro de la disponibilidad", "Paso desde el inicio de la franja" y "Huecos libres de turnos y bloqueos")
- [ ] 5.2 Límites de reserva: anticipación mínima, horizonte en días y pedido sin límites (verificación: escenarios de "Anticipación mínima y horizonte", con `Clock.fixed`)
- [ ] 5.3 Comportamiento con una zona con horario de verano, fijado por test (design D7) (verificación: test con una zona con DST documentado en el propio test)

## 6. Seed local

- [ ] 6.1 Extender `db/seed-local/R__demo.sql`: 5 pacientes ficticios, 10 turnos de la semana en curso (con 1 sobreturno solapado) y 1 bloqueo sin turnos activos (design D9). Documentar en el README local que las fechas se fijan en la primera carga (verificación: `docker compose up --build` con el perfil `local` carga sin violar `ex_turno_sin_solapamiento`, y una consulta SQL cuenta 5 pacientes, 10 turnos y 1 bloqueo en el consultorio `demo`)
- [ ] 6.2 El test de C-08 de que el seed no se carga con el perfil por defecto ni en `test` sigue verde con los datos nuevos (verificación: ese test en verde)

## 7. Cierre del change

- [ ] 7.1 `./gradlew test` completo en verde, `ArchitectureTest` en verde y cobertura ≥ 80 % en `agenda.domain` (verificación: CI del PR)
- [ ] 7.2 Actualizar `knowledge-base/04_modelo_de_datos.md` §Turno con el nombre `ex_turno_sin_solapamiento` y los CHECKs agregados, y dejar en el "Leer antes" de C-11 en `CHANGES.md` el requisito de serializar las escrituras de agenda por consultorio (design, Risks) (verificación: diff de 04 y de CHANGES.md en el PR)
- [ ] 7.3 PR listo con la evidencia y la revisión de Maxi. Avisar a Mati, que en C-15 usa la `CalculadoraDeHuecos`, la clave de `paciente` por DNI y `datosDifierenDe` (verificación: PR con revisor asignado)

## Workflow follow-up

- Con el PR aprobado, el autor suma el commit de cierre en el mismo PR: `[x]` en `CHANGES.md` (PR #N, fecha) y `openspec archive agenda-esquema-dominio`. Maxi vuelve a aprobar ese commit y mergea.
