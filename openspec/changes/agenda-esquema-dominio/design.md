# Design

## Context

- Ver `proposal.md` §Why. Governance **ALTO**: se propone y se espera la revisión de Maxi antes de escribir código.
- **Base disponible (C-02, mergeado)**:
  - `ConsultorioId`, `GeneradorDeIds` (UUIDv7), el `Clock` inyectado (`Reloj`, UTC) y la regla ArchUnit `horaSoloDesdeElClock`.
  - `ErrorDeNegocio` con `ValidacionDeNegocio` (400) y `ConflictoDeNegocio` (409), y `ManejadorGlobalDeErrores` con su mapa de constraints a códigos.
  - `NormalizadorDeTelefono` (spec `telefono-e164`), `EnmascaradorDePii` y `CONVENCIONES.md` de migraciones.
  - Las reglas ArchUnit `dominioSinFramework` (todo `..domain..`) y `repositoriosSiempreConTenant`.
  - La V1 crea `btree_gist` y `citext`.
- **Lo que llega antes de implementar**:
  - C-08: `tipo_prestacion` con `UNIQUE (consultorio_id, id)`, `disponibilidad`, `GrillaSemanal` en `consultorio.domain`, `ErrorDeNegocio.propiedades()` (su D6) y `db/seed-local/R__demo.sql`.
  - C-03, C-04 y C-05 también numeran migraciones.
- **Quién lo consume**:
  - C-11 (casos de uso, JPA, auditoría y transacciones).
  - C-15 (huecos y reserva pública, deduplicación de pacientes).
  - C-17 (revisión de pacientes).

## Goals / Non-Goals

**Goals:**
- Que todas las reglas de RN-AG-01 a RN-AG-13 que no dependen de la persistencia vivan en funciones puras, testeables sin Spring ni base.
- Que la base rechace por sí sola lo que nunca debe existir: solapamiento de turnos activos, fin incoherente, referencias entre consultorios, horarios fuera de la grilla de 5 minutos y borrado de turnos o pacientes.
- Que `agenda.domain` no dependa del código de otros módulos de dominio, para poder desarrollarlo y testearlo en paralelo con la integración de C-08.

**Non-Goals:**
- Repositorios, entidades JPA y transacciones (C-11). Las pruebas de integración de este change escriben con SQL directo.
- La deduplicación al reservar y la marca automática de revisión (C-15, RN-RP-03). Acá solo están la clave única y el método para marcar.
- Optimistic locking en el dominio: la columna `version` existe, pero la gestiona JPA en C-11.
- Un límite al rango de fechas de la consulta de huecos: lo fija el endpoint de C-15.

## Decisions

### D1 — Alcance: migración y dominio puro, sin capa de infraestructura
- **C-10 entrega** la migración, `agenda.domain` y sus tests. Las pruebas de integración insertan con `JdbcClient` para demostrar que la base protege las invariantes *aunque se saltee el servicio*, que es justamente lo que pide CHANGES.md.
- **C-11 agrega** las entidades JPA y los repositorios. Con `ddl-auto=validate`, el mapeo se valida contra esta migración en ese change.
- *Alternativa*: incluir los repositorios acá → mezcla el modelo con los casos de uso, agranda un PR de nivel ALTO y obliga a decidir el mapeo antes de tener los casos de uso que lo usan.

### D2 — `agenda.domain` no importa `consultorio.domain`
- La disponibilidad entra al dominio de agenda como un value object propio: `DisponibilidadSemanal` (lista de `FranjaSemanal(DayOfWeek, LocalTime, LocalTime)`) más la `ZoneId` del consultorio. C-11 y C-15 la arman desde la `GrillaSemanal` de C-08, en `application`.
- La prestación entra como una `Duracion`. El turno guarda el `UUID` de la prestación, no el objeto.
- Se agrega la regla ArchUnit `agenda.domain` no depende de `..consultorio..`, con su fixture de prueba.
- *Alternativa*: usar `GrillaSemanal` directamente → acopla los dominios de dos módulos (08 dice que los módulos hablan por `application`) y ata este change al código de C-08 sin mergear.

### D3 — Modelo del dominio
- **Value objects**:
  - `Intervalo(Instant inicio, Instant fin)`: semiabierto, `fin > inicio`. Tiene `seSolapaCon`, `contiene` y `estaAlineadoA5Minutos`.
  - `Duracion(int minutos)`: múltiplo de 5, de 5 a 480 (mismo rango que `tipo_prestacion`). Si no, `DURACION_INVALIDA`.
  - `PacienteId`, `TurnoId` y `BloqueoId`: records sobre `UUID`.
- **`Turno`**: clase pura con `id`, `consultorioId`, `pacienteId`, `tipoPrestacionId`, `inicio`, `duracion`, `estado`, `esSobreturno`, `origen`, `canceladoPor`, `motivoCancelacion` y `notaInterna`.
  - `fin()` siempre se calcula (RN-AG-12) y no hay setter.
  - Fábricas: `Turno.nuevo(...)` y `Turno.nuevoSobreturno(...)`. El sobreturno siempre es `MANUAL`.
  - Comandos: `confirmar()`, `cancelar(por, motivo)`, `marcarAusente(Instant ahora)`, `reprogramar(Instant)`, `ajustarDuracion(Duracion)` y `editarNota(String)`.
  - Los comandos validan solo los invariantes propios del turno: estado, grilla y longitudes. El solapamiento lo valida `ValidadorDeSolapamiento`, porque necesita otros turnos.
- **`MaquinaDeEstadosTurno`**: función pura `(estadoActual, accion, inicio, ahora) → estadoNuevo | TRANSICION_INVALIDA`. La usa `Turno`. Reprogramar y ajustar la duración exigen un estado activo.
- **`Bloqueo`**: tiene un `Intervalo` alineado a 5 minutos y un `motivo` opcional de hasta 120 caracteres.
- **`Paciente`**:
  - Nombre y apellido recortados, de 1 a 80 caracteres.
  - DNI por el value object `Dni`: quita puntos y espacios, exige 7 u 8 dígitos y si no lanza `ValidacionDeNegocio` `DNI_INVALIDO`. Es la identidad del paciente dentro del consultorio (DD-24).
  - Teléfono por `NormalizadorDeTelefono`, obligatorio (DD-32) y sin unicidad.
  - `datosDifierenDe(nombre, apellido)`: compara sin distinguir mayúsculas ni espacios en los extremos. C-15 lo usa para reutilizar el paciente de un DNI existente sin sobrescribirlo y marcarlo con `requiere_revision` si los datos no coinciden.
  - Email opcional, con validación sintáctica simple y guardado en minúsculas.
  - `origen`, `requiereRevision` y `Consentimiento(Instant, String version)`, obligatorio si el origen es `LINK`.
  - `toString()` no expone PII: usa `EnmascaradorDePii` o solo el id.
- `ahora` siempre entra como parámetro: el dominio no lee el reloj (regla `horaSoloDesdeElClock`).

### D4 — Validadores como funciones puras con todos los conflictos
- **`ValidadorDeSolapamiento.validar(candidato, turnosDelAmbito, bloqueos, disponibilidad)`** aplica, en este orden:
  1. bloqueo (`HORARIO_BLOQUEADO`, también para sobreturnos);
  2. disponibilidad (`FUERA_DE_DISPONIBILIDAD`, solo si no es sobreturno);
  3. solapamiento con turnos activos no sobreturno, excluyendo al propio candidato por id (`TURNO_SOLAPADO`, solo si no es sobreturno).
  - El orden hace que el error sea estable cuando hay varios problemas a la vez. El bloqueo va primero porque es la única regla que también aplica al sobreturno.
- **`ValidadorDeBloqueo.validar(bloqueo, turnosDelAmbito)`** devuelve `BLOQUEO_CON_TURNOS` con **todos** los turnos activos que intersecta, sobreturnos incluidos (RN-AG-05).
- **Errores con datos**: los conflictos llevan `turnosEnConflicto` (lista de ids) en `ErrorDeNegocio.propiedades()`, que llega con C-08 (su D6). Si C-08 se mergea sin `propiedades()`, la tarea 1.1 de este change la agrega con su test.
- **Tipos y estados HTTP** (los aplica C-12, que ya los usa en sus criterios):
  - `ValidacionDeNegocio` → 400 para `DURACION_INVALIDA`, `HORARIO_INVALIDO` y `VALIDACION`.
  - `ConflictoDeNegocio` → 409 para `TURNO_SOLAPADO`, `HORARIO_BLOQUEADO`, `FUERA_DE_DISPONIBILIDAD`, `BLOQUEO_CON_TURNOS` y `TRANSICION_INVALIDA`.

### D5 — Franjas contiguas como una franja continua
- Al materializar la disponibilidad de una fecha, las franjas del mismo día que se tocan (`fin == inicio`) se fusionan. Así, un turno de 12:30 a 13:30 entra en `09:00–13:00 + 13:00–17:00`, y el paso de los huecos sigue corriendo desde las 09:00.
- C-08 permite franjas contiguas, y partirlas no tiene un significado de negocio.
- *Alternativa*: la lectura literal de RN-AG-03 ("dentro de una franja") → rechazaría un turno que cabe en un horario continuo de atención.
- **Aprobada por Maxi en #18 (2026-11-20)**: las franjas contiguas se toman como un horario continuo.

### D6 — El ámbito del solapamiento lo decide quien llama (punto de extensión de RN-AG-13)
- Los validadores y la calculadora reciben la colección de turnos y bloqueos del ámbito ya filtrada. No saben qué es un consultorio, un profesional ni un sillón.
- En v1, C-11 y C-15 les pasan los del consultorio.
- Con multi-sillón (PA-14), el caso de uso pide dos colecciones, la del profesional y la del sillón, y valida contra ambas. El dominio no cambia. En la base, la restricción única se reemplaza por dos exclusiones (`proposal.md` §Granularidad).
- *Alternativa*: un `AmbitoDeAgenda` con `profesional_id` y `sillon_id` desde ya → agrega columnas y conceptos que la v1 no usa (DD-17).

### D7 — `CalculadoraDeHuecos`
- **Entrada**: `DisponibilidadSemanal`, `ZoneId`, el rango de fechas locales `[desde, hasta]`, la `Duracion`, el paso (5, 10, 15, 20 o 30), los intervalos ocupados (bloqueos más turnos activos, sobreturnos incluidos) y `Optional<LimitesDeReserva(anticipacionMin, horizonteDias, ahora)>`.
  - El odontólogo pide sin límites. C-15 pide con los del consultorio.
- **Algoritmo**: para cada fecha del rango toma las franjas de su `DayOfWeek` y fusiona las contiguas (D5). Desde el inicio de cada franja avanza de a `paso` mientras `t + duracion ≤ fin`, convierte con `ZonedDateTime.of(fecha, t, zona).toInstant()` y descarta los candidatos que intersectan algo ocupado o quedan fuera de los límites.
  - Los ocupados van ordenados por inicio, y la búsqueda avanza con un puntero. Con 60 días y pocas decenas de turnos por día es lineal y suficiente.
- **Límites**: se excluyen los huecos con `inicio < ahora + anticipacion` y los que tienen `inicio > ahora + horizonteDias × 24 h`. El horizonte se mide como duración desde `ahora`, como dice RN-RP-01.
- **Horario de verano**: hoy Argentina no tiene. Si una zona lo tuviera, `ZonedDateTime.of` corre las horas inexistentes y elige el offset anterior en las repetidas. Se documenta con un test con una zona con DST, para que el comportamiento quede fijado.

### D8 — Migración `V{n}__agenda_paciente_turno_bloqueo.sql`
- Sigue `CONVENCIONES.md`: `consultorio_id` con FK, `UNIQUE (consultorio_id, id)` en `paciente`, `turno` y `bloqueo`, FKs compuestas, índices que empiezan por `consultorio_id` y constraints con nombre.
- **`paciente`**:
  - `dni VARCHAR(8) NOT NULL` con `ck_paciente_dni CHECK (dni ~ '^[0-9]{7,8}$')` y `uk_paciente_dni UNIQUE (consultorio_id, dni)` (DD-24). Sirve también de índice para buscar por DNI.
  - `telefono_e164 VARCHAR(16) NOT NULL`, sin unicidad: los familiares pueden compartir el número.
  - `ck_paciente_origen`.
  - `ck_paciente_consentimiento`: `origen <> 'LINK' OR (consentimiento_at IS NOT NULL AND consentimiento_version IS NOT NULL)`.
  - `email CITEXT`, `updated_at` y `REVOKE DELETE`.
- **`turno`**:
  - FKs compuestas `fk_turno_paciente` y `fk_turno_tipo_prestacion`.
  - `ck_turno_duracion`: de 5 a 480 y `% 5 = 0`.
  - `ck_turno_fin`: `extract(epoch FROM fin) - extract(epoch FROM inicio) = duracion_min * 60`.
  - `ck_turno_grilla`: `extract(epoch FROM inicio) % 300 = 0`, en `numeric` (en PG16 `extract` devuelve `numeric`). Sin el cast a `bigint`, que redondea: un inicio de 09:00:00.6 pasaría (revisión de Maxi en #18).
  - `ck_turno_estado`, `ck_turno_origen` y `ck_turno_sobreturno_manual` (`NOT es_sobreturno OR origen = 'MANUAL'`).
  - `ck_turno_cancelacion`: `(estado = 'CANCELADO') = (cancelado_por IS NOT NULL)`, con `cancelado_por IN ('ODONTOLOGO','PACIENTE')` y `motivo_cancelacion` solo si está cancelado.
  - `nota_interna VARCHAR(500)`, `motivo_cancelacion VARCHAR(200)`, `version BIGINT NOT NULL DEFAULT 0` y `REVOKE DELETE`.
  - **`ex_turno_sin_solapamiento`**: `EXCLUDE USING gist (consultorio_id WITH =, tstzrange(inicio, fin, '[)') WITH &&) WHERE (NOT es_sobreturno AND estado IN ('RESERVADO','CONFIRMADO'))`. Es la restricción `turno_sin_solapamiento` de 04, con el prefijo de la convención (como `ex_disponibilidad_sin_solape` de C-08). Al cerrar el change se actualiza el nombre en 04.
  - Índices `ix_turno_consultorio_inicio (consultorio_id, inicio)` e `ix_turno_consultorio_paciente_inicio (consultorio_id, paciente_id, inicio)`.
- **`bloqueo`**:
  - `ck_bloqueo_rango (fin > inicio)` y `ck_bloqueo_grilla`: `extract(epoch FROM inicio) % 300 = 0 AND extract(epoch FROM fin) % 300 = 0`, también en `numeric`.
  - `motivo VARCHAR(120)`.
  - `ix_bloqueo_rango USING gist (consultorio_id, tstzrange(inicio, fin, '[)'))`.
  - Conserva `DELETE`, porque C-11 elimina bloqueos con evento `BLOQUEO_ELIMINADO`. Sin exclusión entre bloqueos (D10).
- **Por qué `fin` es una columna con CHECK y no una columna generada**: `timestamptz + interval` es `STABLE`, no `IMMUTABLE`, así que Postgres no la acepta en `GENERATED ALWAYS AS`.
  - El CHECK compara epochs, que no dependen de la zona de la sesión. `tstzrange(inicio, fin)` sí es inmutable y sirve en la exclusión.
  - Tener `fin` materializado es lo que permite indexar el rango.
- **Por qué la grilla de 5 minutos va en epoch UTC**: es equivalente a la grilla local para cualquier zona cuyo offset sea múltiplo de 5 minutos, que son todas las vigentes (Argentina usa −03:00).
- **Mapa del manejador**: este change no agrega códigos HTTP. C-11 mapea `ex_turno_sin_solapamiento` → 409 `TURNO_SOLAPADO` para las carreras, porque es quien escribe.

### D9 — Seed local
- Se extiende `db/seed-local/R__demo.sql` (de C-08) con lo que 04 §Seed pide:
  - 5 pacientes ficticios (`Paciente Uno Prueba` … `Paciente Cinco Prueba`, DNI `90000001` a `90000005`, teléfonos `+5491100000001` a `+5491100000005`, emails `@example.com`);
  - 10 turnos de la semana en curso dentro de la disponibilidad demo, con 1 sobreturno solapado;
  - 1 bloqueo sin turnos activos.
- **Fechas**: relativas a `date_trunc('week', now() AT TIME ZONE 'America/Argentina/Buenos_Aires')` en el momento de la carga. Como Flyway reaplica `R__` solo cuando cambia su checksum, las fechas quedan fijas hasta recrear el volumen (`docker compose down -v`). Se documenta en el README local.
- Ids fijos y `ON CONFLICT DO NOTHING`. Sin eventos de auditoría, porque es un dato de desarrollo (igual que en C-08 D11).

### D10 — Bloqueos que se superponen
- La KB no lo prohíbe y no hay un riesgo de negocio: un horario bloqueado dos veces sigue bloqueado. Por eso no se agrega una exclusión entre bloqueos.
- *Alternativa*: rechazarlos → obliga a la UI de C-14 a resolver un conflicto sin valor para el usuario.
- **Aprobada por Maxi en #18 (2026-11-20)**: se permiten bloqueos superpuestos, sin exclusión. Se mantiene que ningún turno, tampoco un sobreturno, cae dentro de un bloqueo (RN-AG-03/04), y que no se crea un bloqueo sobre turnos activos (RN-AG-05).

## Risks / Trade-offs

- **[El DNI no se verifica]** → DD-24 acepta que alguien reserve con un DNI ajeno o con un error de tipeo. La marca `requiere_revision` deja visibles las diferencias de nombre para que el odontólogo las revise. Juntar el DNI se aparta de la minimización y se declara en la política de privacidad (DD-28).
- **[La base no puede impedir un turno dentro de un bloqueo ni un bloqueo sobre un turno]** → la exclusión GiST no cruza tablas. Dos operaciones concurrentes (crear un turno y crear un bloqueo) podrían pasar las dos validaciones. C-11 tiene que serializar las escrituras de agenda por consultorio (por ejemplo, `pg_advisory_xact_lock` sobre el `consultorio_id`). Queda como requisito explícito para C-11 en su "Leer antes".
- **[C-08 todavía no está mergeado]** → la FK a `tipo_prestacion`, `propiedades()` y el seed dependen de él. C-08 trae `V5`, así que esta migración sería `V6`; el número se confirma en la tarea 1.1.
- **[Fusión de franjas contiguas (D5) y bloqueos superpuestos (D10)]** → la KB no las fijaba; Maxi las aprobó en #18.
- **[Fechas del seed congeladas en la primera carga]** → es aceptable en desarrollo y está documentado. *Alternativa*: un `ApplicationRunner` del perfil `local` → agrega código de producción solo para la demo.
- **[CHECKs con `extract(epoch …)`, que es `STABLE`]** → Postgres lo admite en un CHECK y el valor no depende de la sesión. El test de integración lo cubre con dos `TimeZone` de sesión distintas.

## Migration Plan

- Migración aditiva: tres tablas nuevas, sin datos en producción. Rollback = no mergear. Una vez mergeada, los ajustes van en una migración nueva.
- Orden de aplicación: después de las migraciones de C-03, C-04, C-05 y C-08 (FK a `tipo_prestacion`).

## Open Questions

- RN-AG-07 dice que los pacientes "se desactivan", pero 04 no tiene una columna `activo` en `paciente`, y ningún change de v1 tiene un caso de uso para desactivarlos. Este change no la agrega (solo impide el `DELETE`). Si C-17 la necesita, va en una migración aditiva de ese change.

## UI Mockups

- Decision: no (no UI), 2026-11-20. Change de backend sin pantallas. La agenda visual es de C-13 y C-14 (Nacho), que llevan su propio mockup.
