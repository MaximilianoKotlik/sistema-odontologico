# Design

## Context

- Ver `proposal.md` §Why. Governance **MEDIO**, sin pantallas. Se propone antes de codear porque fija el contrato que ya usan C-13 (#27) y C-14 (#31).
- **Base**:
  - C-10 (#18): las tablas y el dominio de agenda.
  - C-11 (propuesta #38, implementación #46, mergeadas): los casos de uso, `LockDeAgenda`, los repositorios JDBC y los errores `PRESTACION_INACTIVA` y `ACCION_NO_PERMITIDA`. `RegistrarPaciente` devuelve `PacienteRegistrado {paciente, reutilizado, marcadoParaRevision}` y tiene `vincularTurnoDeOrigen` (DD-38).
  - C-08: `ConsultaDeDisponibilidad` (C-11 D1) y las prestaciones.
  - C-03: el registro de auditoría.
  - C-04: `SesionActual` y la cadena de seguridad.
- **Quién lo consume**:
  - C-13: agenda diaria y semanal, detalle y visto (su D2).
  - C-14: los diálogos de turnos y bloqueos y el aviso de "fuera de disponibilidad" (sus D4 a D7).
  - C-06: la caché `agenda-v1`, que guarda `GET /agenda`.

## Goals / Non-Goals

**Goals:**
- Exponer las operaciones de C-11 con el odontólogo de la sesión como actor y el tenant del token (RN-TE-01), con un test de aislamiento por endpoint (regla dura 6).
- Que `GET /agenda` sea liviano, para no llevar a la caché offline más datos personales de los necesarios. El detalle completo sale de otro endpoint (decisión de Maxi en #27).
- Cerrar los cinco puntos "a acordar con C-12" de C-13 y C-14.

**Non-Goals:**
- Reglas de agenda nuevas: todas están en C-10 y C-11.
- El plan `VENCIDO` y el email al odontólogo de DD-30.
- La paginación de la agenda: el rango tiene un tope (D2).

## Decisions

### D1 — Capas
- **Controladores en `agenda.web`**: `ControladorDeAgenda`, `ControladorDeTurnos`, `ControladorDeBloqueos` y `ControladorDePacientes`. Arman el `Autor` de C-11 con `ODONTOLOGO`, el usuario de `SesionActual` y la IP.
- **Lecturas en `agenda.application`**: son consultas de solo lectura (`@Transactional(readOnly = true)`) que no toman el lock de agenda. Las escrituras son los casos de uso de C-11.
- **Respuestas**: JSON en `camelCase` e instantes en UTC (ISO-8601). La conversión a hora local es del cliente (RN-TE-05, `fechas.ts` de C-06).

### D2 — `GET /api/v1/agenda?desde&hasta`
- **Parámetros**: `desde` y `hasta` son instantes con `desde < hasta` y un rango de hasta **42 días**, que cubre la vista semanal y un mes con margen. Si no, 400 `VALIDACION`. **Aprobado por Maxi en #39.**
- **Turnos**: todos los que intersectan el rango, también los cancelados y ausentes. El filtro es del cliente (C-13 D2).
  - Cada turno trae `id`, `inicio`, `fin`, `duracionMin`, `estado`, `esSobreturno`, `origen`, `prestacion {id, nombre}`, `paciente {id, nombre, apellido}`, `tieneNota`, `canceladoPor`, `cambioPaciente`, `novedadPacienteAt` y `version`. `novedadPacienteAt` es el instante de la novedad sin ver (o `null`), y el cliente lo devuelve al marcarla como vista (D5).
  - **Sin** DNI, teléfono, email ni el texto de la nota ni del motivo.
- **Bloqueos**: los que intersectan el rango, con `id`, `inicio`, `fin` y `motivo`.
- **Orden**: por inicio. El resultado no se pagina.

### D3 — `cambioPaciente` y el horario anterior, desde la auditoría
- **`turno.novedad_paciente_at`** (C-15 D14) dice **que** hubo un cambio del paciente sin ver, pero no **cuál**. C-13 necesita el tipo, y el detalle necesita el horario anterior si el paciente reprogramó.
- **Se derivan del registro de auditoría**, por un puerto de lectura. *Ajuste de la implementación*: el puerto es `compartido.application.ConsultaDeAuditoria`, implementado en `auditoria.infrastructure`, como `PublicadorDeAuditoria`. La regla ArchUnit `auditoriaSoloPorElPuerto` (C-03) no deja que otro módulo dependa de `auditoria`:
  - si `novedad_paciente_at` no es nulo, se toma el último evento del turno con actor `PACIENTE`;
  - `TURNO_CREADO` → `RESERVO`, `TURNO_REPROGRAMADO` → `REPROGRAMO` y `TURNO_ESTADO_CAMBIADO` a `CANCELADO` → `CANCELO`;
  - el horario anterior es el `valor_anterior` del último `TURNO_REPROGRAMADO` con actor `PACIENTE`.
- **Una sola consulta** para toda la agenda: `DISTINCT ON (entidad_id)` sobre `evento_auditoria`, filtrada por consultorio y por los ids de los turnos con novedad, con el índice `ix_evento_auditoria_consultorio_entidad` de C-03.
- **Decisión de Maxi en #39**: se deriva de la auditoría, con una sola consulta `DISTINCT ON` y el índice de C-03. No se agrega una columna `turno.novedad_paciente_tipo` ni se toca el design de C-15.

### D4 — `GET /api/v1/turnos/{id}`
- Trae lo mismo que la agenda, más:
  - `paciente {dni, telefono, email, requiereRevision}`;
  - `notaInterna`;
  - `motivoCancelacion`;
  - `horarioAnterior {inicio, fin}`, si corresponde (D3).
- **Turno de otro consultorio** → 404 (RN-TE-04).

### D5 — `POST /api/v1/turnos/{id}/visto {novedadAt}`
- **El visto dice qué novedad vio** (cambio pedido por Maxi en #39). El body trae `novedadAt`, el `novedadPacienteAt` que el cliente recibió en la agenda o en el detalle. Es obligatorio: sin él, 400 `VALIDACION`.
- **Solo borra esa novedad o una anterior**:
  ```sql
  UPDATE turno SET novedad_paciente_at = NULL
   WHERE consultorio_id = :c AND id = :id AND novedad_paciente_at <= :novedadAt
  ```
- **Por qué**: el odontólogo abre un turno con una novedad de las 10:00, a las 10:01 el paciente lo reprograma por el link y C-15 escribe una novedad nueva. Si el visto llegara después sin condición, borraría la reprogramación sin que el odontólogo la haya visto.
- **Respuesta**: 204 aunque no actualice ninguna fila, porque ya estaba limpia o porque hay una novedad más nueva. En ese caso, el próximo `GET` la muestra. Primero se verifica que el turno sea del consultorio: si no, 404 (RN-TE-04).
- **No toma el lock de agenda ni usa `version`**: no cambia horarios ni estados, y no compite con las ediciones del turno. La condición sobre `novedad_paciente_at` alcanza para no pisar una novedad nueva.
- **Sin evento de auditoría** (decisión de Maxi en #39, registrada como **[DD-39](../../../knowledge-base/09_decisiones_y_supuestos.md)**, excepción a la regla dura 7):
  - `novedad_paciente_at` es una marca de lectura de la interfaz, no un dato del turno;
  - el cambio que la puso ya está auditado (`TURNO_CREADO`, `TURNO_REPROGRAMADO` o `TURNO_ESTADO_CAMBIADO`, C-15 D14);
  - ninguna otra columna de `turno` cambia sin evento.
- **La migración**: si al rebasar `turno.novedad_paciente_at` todavía no existe porque C-15 no se mergeó, este change la crea en `V{n}__turno_novedad_paciente.sql`, con `ADD COLUMN IF NOT EXISTS` para que C-15 no la duplique.
  - *Al mergear*: C-15 (#47) se mergeó antes y la creó en `V7__token_gestion_turno.sql`. Este change no agrega migración; `MigracionNovedadPacienteTest` verifica la columna (revisión de Maxi en #48).

### D6 — Errores y el turno con el que choca
- **Estados HTTP**:
  - 400: `VALIDACION`, `DURACION_INVALIDA` y `HORARIO_INVALIDO`;
  - 404: `RECURSO_NO_ENCONTRADO`;
  - 409: `TURNO_SOLAPADO`, `HORARIO_BLOQUEADO`, `FUERA_DE_DISPONIBILIDAD`, `BLOQUEO_CON_TURNOS`, `TRANSICION_INVALIDA`, `PRESTACION_INACTIVA`, `ACCION_NO_PERMITIDA` y `CONFLICTO_CONCURRENCIA`.
- **`TURNO_SOLAPADO`** suma `turnoEnConflicto {id, inicio, fin}`, el primero por inicio, además de `turnosEnConflicto` (los ids de C-10).
- **Sin el nombre del paciente en el error** (aprobado por Maxi en #39): `ErrorDeNegocio` no lleva datos personales en sus propiedades (C-02, RN-GL-01). C-14 resuelve "de Carla Ficticia" con el `id` y la agenda que ya tiene cargada.
- **Carrera frenada por la base** (`ex_turno_sin_solapamiento`): llega sin `turnoEnConflicto`, como dice C-11 D5.

### D7 — `GET /api/v1/pacientes?q=`
- **`q`**: al menos 3 caracteres, recortado, como el buscador de C-14 D4. Si no, 400 `VALIDACION`. Devuelve hasta 20 resultados.
  - **Solo dígitos** (sacando puntos, espacios, `+` y guiones), con 3 o más: busca el DNI que empieza con esos dígitos, o el teléfono que los contiene.
  - **Con letras**: busca el nombre o el apellido que empiezan con alguna de las palabras, sin distinguir mayúsculas. Usa el índice `ix_paciente_consultorio_apellido` de C-10.
- **Respuesta**: `id`, `nombre`, `apellido`, `dni`, `telefono` y `requiereRevision`. Son datos del propio consultorio, para elegir al paciente.
- **Logs**: el valor de `q` se reemplaza por `***` en el log de acceso y en el MDC, igual que el token de gestión de C-15 (`PatronDeRutaSensible`), porque puede ser un DNI o un teléfono (RN-GL-01).
  - *Ajuste de la implementación*: hoy el backend no tiene log de acceso ni pone la URL en el MDC, y `PatronDeRutaSensible` (C-15) enmascara el token en la ruta, no la query. Lo que sí escribe la query es el `DispatcherServlet` en DEBUG. `FiltroDeParametrosSensibles` (`compartido.infrastructure.logging`) le pasa al resto de la cadena un `getQueryString()` con `q=***`, y el parámetro se sigue leyendo sin cambios.
  - Un log de acceso de Tomcat no pasaría por el filtro. Si se habilita, su patrón va sin `%q`. C-15 puede sumar sus rutas al mismo filtro.
- *Alternativa*: `POST /pacientes/busqueda` con body → saca el dato de la URL, pero se aparta del alcance de #34 y del contrato de C-14.

### D8 — Bloqueos
- `GET /api/v1/bloqueos?desde=` lista los bloqueos con `fin > desde`, ordenados por inicio. Sin `desde`, se usa el ahora del `Clock`. Responde `id`, `inicio`, `fin` y `motivo`.
- `POST /api/v1/bloqueos {inicio, fin, motivo}` → 201. `DELETE /api/v1/bloqueos/{id}` → 204.

### D9 — `GET /api/v1/turnos/fuera-de-disponibilidad`
- **Qué lista**: los turnos `RESERVADO` o `CONFIRMADO`, no sobreturnos, con `inicio ≥ ahora`, que la `DisponibilidadSemanal` vigente no cubre (`cubre` de C-10). Cada uno con la misma forma que en la agenda.
- **Sin tope de fecha**: los turnos futuros de un consultorio son pocos. Se leen ordenados por inicio y se filtran en el dominio.
- No cambia nada: reemplazar la grilla no mueve ni cancela turnos (decisión de Maxi en #15). Este endpoint solo alimenta el aviso de C-14.

### D10 — Escrituras
| Endpoint | Body | Caso de uso de C-11 | Éxito |
|---|---|---|---|
| `POST /turnos` | `{pacienteId \| pacienteNuevo {nombre, apellido, dni, telefono, email?}, prestacionId, inicio, duracionMin?, esSobreturno}` | `RegistrarPaciente` (si viene `pacienteNuevo`) + `CrearTurno` + `RegistrarPaciente.vincularTurnoDeOrigen`, en una transacción | 201, con el turno y `pacienteReutilizado` |
| `PATCH /turnos/{id}/estado` | `{accion: CONFIRMAR \| CANCELAR \| MARCAR_AUSENTE, motivo?, version}` | `CambiarEstadoTurno` | 200 |
| `POST /turnos/{id}/reprogramar` | `{inicio, version}` | `ReprogramarTurno` | 200 |
| `PATCH /turnos/{id}/duracion` | `{duracionMin, version}` | `AjustarDuracion` | 200 |
| `PATCH /turnos/{id}/nota` | `{nota, version}` | `EditarNota` | 200 |

- **Respuesta**: el turno actualizado, con la forma del detalle (D4) y su `version` nueva.
- **`POST /turnos` con `pacienteNuevo` y un DNI que ya existe** (decisión de Maxi en #38, C-11 D6): no hay error. La respuesta trae `pacienteReutilizado = true` y el paciente con sus **datos guardados**, no los que se escribieron, para el aviso de C-14 ("Ya tenés a Ana Ejemplo con ese DNI"). Si el nombre difería, el paciente queda marcado para revisión con lo informado, y el turno creado queda como su turno de origen.
- **Body**: Bean Validation solo para lo estructural (requeridos y largos). Las reglas de dominio responden con sus códigos (como la duración en C-08).

## Risks / Trade-offs

- **[Nombres de C-11]** → este design usa los nombres finales de C-11 (#46). La tarea 1.1 lo confirma al rebasar.
- **[Derivar `cambioPaciente` de la auditoría (D3)]** → acopla la lectura de la agenda al módulo de auditoría, aunque sea por un puerto de `application`. Maxi lo aprobó en #39 frente a la columna.
- **[Sin evento por el visto (D5)]** → es una excepción a la regla dura 7, aprobada y acotada en DD-39: solo `novedad_paciente_at`.
- **[DNI y teléfono en la caché offline]** → `GET /pacientes?q=` y `GET /turnos/{id}` los traen. El runtime caching de `agenda-v1` (C-06) guarda solo `GET /api/v1/agenda`, y un test lo fija (tarea 4.3).
- **[DNI y teléfono en la query]** → se enmascaran en los logs (D7). El navegador y los proxies propios sí ven la URL, pero todo es HTTPS fuera de local y la ruta es privada.
- **[Huecos para el odontólogo]** → Maxi pidió en #18 que C-12 decidiera qué `ocupados` pasa a `CalculadoraDeHuecos` según el actor. C-12 no expone huecos: C-14 los sugiere con la agenda cargada (el `modelo.ts` de C-13). Para que coincidan con lo que el backend acepta, ese modelo **no cuenta los sobreturnos como ocupados** y no ofrece horarios pasados, como dice la Javadoc de `calcular` (C-11). Se le avisa a Nacho en la tarea 5.3.
- **[`novedad_paciente_at` entre dos changes]** → `ADD COLUMN IF NOT EXISTS` y la verificación al rebasar evitan que se cree dos veces.

## Migration Plan

- Sin migración propia: `novedad_paciente_at` llegó con C-15 (`V7__token_gestion_turno.sql`). El rollback es no mergear.

## Open Questions

- Ninguna. Las tres de la propuesta las decidió Maxi en #39: `cambioPaciente` desde la auditoría (D3), el visto sin evento con DD-39 (D5) y el 409 sin el nombre del paciente (D6).

## UI Mockups

- Decision: no (no UI), 2026-11-20. Las pantallas que consumen esta API son de C-13 y C-14, que ya tienen sus mockups.
