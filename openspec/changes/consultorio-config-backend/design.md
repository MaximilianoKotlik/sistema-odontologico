# Design

## Context

- Ver `proposal.md` §Why. Governance **MEDIO**: se implementa con checkpoints, pero la propuesta se aprueba antes de codear.
- **Base disponible**:
  - C-02 está mergeado (#12): `TenantContext`, `ConsultorioId`, `GeneradorDeIds`, `Clock`, `ErrorDeNegocio` y `ManejadorGlobalDeErrores` (con su mapa de constraints a códigos), `ValidadorDeSlug` y `SugeridorDeSlug`, `NormalizadorDeTelefono`, `PublicadorDeAuditoria.publicar(CambioAuditado)`, la regla ArchUnit `repositoriosSiempreConTenant` y `CONVENCIONES.md`.
  - La V1 creó `consultorio` con `version` y `REVOKE DELETE`.
- **Lo que llega antes de implementar**:
  - C-03 (#10, aprobado): persiste los eventos con un listener `BEFORE_COMMIT` que hace `flush()` antes de insertar.
  - C-04 (#8, aprobado): `SecurityConfig`, el filtro que establece el tenant desde `cid`, el registro que crea el consultorio y el asignador de slug único (su D6).
  - C-05: bloqueo y recuperación. No lo usa C-08, pero también numera migraciones.
- **Frontend que consume esto**: C-09 (Nacho), por `api-types.ts`.

## Goals / Non-Goals

**Goals:**
- Que la configuración sea la fuente única de lo que la agenda y la reserva pública necesitan: duración por prestación, franjas, anticipaciones y paso de huecos.
- Que ninguna franja solapada ni prestación duplicada pueda existir, aunque falle la validación de la aplicación (invariantes en la base).
- Que cada cambio quede auditado con solo los campos que cambiaron.

**Non-Goals:**
- Usar la configuración para calcular huecos o validar turnos (C-10, C-11 y C-15).
- Cambiar el plan o `prueba_hasta` (C-20, operación interna).
- Historial de versiones de la grilla: el evento de auditoría guarda la anterior y la nueva, y alcanza para v1.

## Decisions

### D1 — Módulo `consultorio` dueño del consultorio, compartido con el registro de C-04
- `consultorio.domain`: `Consultorio`, `ReglasDeReserva` (anticipaciones, horizonte, paso), `TipoDePrestacion`, `Franja` y `GrillaSemanal`, todas con reglas puras.
- `consultorio.application`: `ConsultarConfiguracion`, `EditarConfiguracion`, `GestionarPrestaciones`, `ReemplazarDisponibilidad` y `AsignadorDeSlugUnico`.
- `consultorio.infrastructure`: JPA y JDBC. `consultorio.web`: controladores y DTOs.
- **Coordinación con C-04 (también de Fran)**: C-04 se implementa antes, así que crea la entidad `Consultorio`, `ConsultorioRepositorio` y el `AsignadorDeSlugUnico` **dentro del módulo `consultorio`** (no en `auth`). El registro los usa por `consultorio.application`, y C-08 los extiende. Se ajusta D13 de C-04 en su tarea 1.1.
- `ConsultorioRepositorio` ya está exceptuado de la regla de tenant (`REPOSITORIOS_EXCEPTUADOS`): es la raíz y se busca por su id, que es el tenant. `PrestacionRepositorio` y `DisponibilidadRepositorio` reciben `ConsultorioId` en todo.

### D2 — Persistencia
- **Ajuste en la implementación (2026-11-20)**: todo el módulo usa `JdbcClient`, no JPA. C-04 ya había creado `ConsultorioRepositorioJdbc`, y mezclar dos estilos en el mismo módulo no aportaba nada. La versión del consultorio se controla en el `UPDATE … WHERE version = :leida`: si no actualiza ninguna fila, lanza `OptimisticLockingFailureException` (409 `CONFLICTO_CONCURRENCIA`).
- Plan original: `consultorio` y `tipo_prestacion` con JPA. `@Version` en `consultorio` usa la columna que ya existe: dos guardados concurrentes dan 409 `CONFLICTO_CONCURRENCIA` (RN-GL-02), y el cliente no manda la versión.
- `disponibilidad` con `JdbcClient`: el reemplazo completo es `DELETE … WHERE consultorio_id = ?` más un `INSERT` por franja. Es más simple que sincronizar una colección JPA, y la restricción de exclusión actúa fila por fila.
- **Flush y auditoría**: el listener de C-03 hace `flush()` antes de insertar el evento, así que el evento de `PRESTACION_CREADA` encuentra la fila aunque la haya creado JPA en la misma transacción.

### D3 — Actor de la auditoría
- El evento necesita el `actor_id` (el usuario, `sub` del JWT) y la IP. C-02 solo expone el tenant.
- Se agrega en `compartido.application` el puerto `SesionActual` (`UUID usuarioId()`, `Optional<String> ip()`). Lo implementa C-04 en `auth.infrastructure`, leyendo el `Jwt` del `SecurityContext` y la IP de la petición. Si C-04 se mergea sin él, lo agrega C-08 como primera tarea.
- *Alternativa*: leer el `SecurityContext` desde `consultorio.application` → acopla la aplicación a Spring Security y rompe la regla de capas.

### D4 — Diff de auditoría y "sin cambios, sin evento"
- Los casos de uso arman un mapa `snake_case → valor` del estado anterior y del nuevo, y publican solo las claves que difieren (RN-AD-05). Si no difiere ninguna, no se publica nada.
- **Disponibilidad**: las grillas se comparan normalizadas (ordenadas por día e inicio), y el evento lleva ambas completas como `{franjas: [...]}`. La entidad es `disponibilidad` y su id es el del consultorio, porque la grilla no tiene id propio.

### D5 — Franjas sin solapamiento, en dos capas
- **Aplicación**: `GrillaSemanal` ordena por día e inicio y detecta solapamientos con intervalos semiabiertos `[inicio, fin)`, así las franjas contiguas son válidas. Devuelve 400 `FRANJAS_SOLAPADAS` con la propiedad `dias` (por ejemplo `[1, 3]`).
- **Base**: constraint `ex_disponibilidad_sin_solape EXCLUDE USING gist (consultorio_id WITH =, dia_semana WITH =, tsrange(DATE '2000-01-03' + hora_inicio, DATE '2000-01-03' + hora_fin, '[)') WITH &&)`.
  - `date + time` es inmutable, así que sirve en una restricción.
  - `btree_gist` (V1) da el `=` sobre `uuid` y `smallint`.
  - La fecha ancla da igual porque solo se comparan horas del mismo día.
  - Se suma al mapa de constraints del manejador → 400 `FRANJAS_SOLAPADAS`, para el caso de una carrera.
- **Validez**: `CHECK (dia_semana BETWEEN 1 AND 7)`, `CHECK (hora_fin > hora_inicio)` y `CHECK` de minutos múltiplos de 5 en la base, más `FRANJA_INVALIDA` en la aplicación. La regla de 5 min extiende RN-AG-08 a las franjas: si no, una franja podría empezar a las 09:03 y ningún turno alineado a 5 min encajaría en su borde.

### D6 — Errores de negocio con propiedades extra
- `SLUG_EN_USO` tiene que llevar `sugerencias` y `FRANJAS_SOLAPADAS` tiene que llevar `dias`.
- `ErrorDeNegocio` (C-02) gana un `Map<String, Object> propiedades()`, vacío por defecto. `ManejadorGlobalDeErrores` las agrega al Problem Details.
- Es un cambio chico y compatible en `compartido`, con su test en `ManejadorGlobalDeErroresTest`.

### D7 — Cambio de slug con sugerencias
- Se valida con `ValidadorDeSlug` (C-02). Si el slug es el mismo que ya tiene el consultorio, no hay conflicto.
- Si está en uso, `AsignadorDeSlugUnico.sugerencias(slugPedido, 3)` devuelve los primeros `-N` libres, recortando la base para no pasar de 40.
- Para una carrera, se mapea `uk_consultorio_slug` (ya en el manejador de C-02). En ese caso, la respuesta sale sin sugerencias.

### D8 — Prestaciones
- **Nombre único sin distinguir mayúsculas**: `uk_tipo_prestacion_nombre` es un índice único `(consultorio_id, lower(nombre))` y se mapea a 409 `PRESTACION_DUPLICADA`. También se valida antes en la aplicación, para dar el error sin depender de la carrera.
- **Duración**: `CHECK (duracion_min BETWEEN 5 AND 480 AND duracion_min % 5 = 0)` en la base y `DURACION_INVALIDA` en el dominio.
- **Sin borrado**: no hay `DELETE` en la API (405 por Spring, porque la ruta existe con otros métodos) y la migración hace `REVOKE DELETE ON tipo_prestacion FROM "${app-user}"` (RN-AG-07).
- `orden` es opcional. Si no se informa, va al final (`max(orden) + 1` del consultorio).

### D9 — Validación de la configuración
- Bean Validation en el DTO para los rangos, y reglas de dominio en `ReglasDeReserva`.
- **Horizonte de 1 a 365 días**: la base solo exige `> 0`. El tope de 365 es una regla de aplicación nueva, para que el cálculo de huecos de C-15 no recorra años. Aprobada por Maxi en #15 y registrada en 04 y en RN-TE-06.
- **Zona horaria**: `ZoneId.of`. Si es inválida, `ZONA_HORARIA_INVALIDA`.
- **Teléfono**: `NormalizadorDeTelefono`. **Obligatorio** al guardar (decisión de Maxi en #15, por los mockups de C-09): vacío o `null` → 400 `VALIDACION` en `telefonoContacto`.
- **Anticipación menor que el horizonte** (decisión de Maxi en #15): `ReglasDeReserva` rechaza `anticipacion_reserva_min >= horizonte_reserva_dias × 1440` con `ANTICIPACION_FUERA_DE_HORIZONTE` y la propiedad `campo: anticipacionReservaMin`, para que C-09 lo muestre en el campo.
- `plan` y `pruebaHasta` no están en el DTO de entrada, así que se ignoran por construcción.

### D10 — API
- JSON en `camelCase` y horas en `"HH:mm"`, locales a la zona del consultorio (RN-TE-05).
- Las rutas son las de 02 §API REST: `/api/v1/consultorio`, `/api/v1/prestaciones[/{id}[/desactivar|/activar]]` y `/api/v1/disponibilidad`.
- Todas privadas: exigen JWT por la `SecurityConfig` de C-04.

### D11 — Seed local
- Migración repetible `db/seed-local/R__demo.sql`, que solo se carga con el perfil `local` (`spring.flyway.locations` en `application-local.yml`).
- Ids fijos e idempotente (`ON CONFLICT DO NOTHING`).
- Contenido de 04 §Seed:
  - "Consultorio Demo" (slug `demo`);
  - usuario `demo@consultorio.test`, con el hash BCrypt de costo 12 de una contraseña solo para desarrollo, documentada en el README local;
  - 4 prestaciones;
  - lunes a viernes de 09:00 a 13:00 y de 15:00 a 19:00.
- No genera eventos de auditoría (es un dato de desarrollo). Los pacientes y turnos de ejemplo los suma C-10 o C-11.
- Depende de las tablas de C-04 (`usuario`), que es dependencia de este change.

### D12 — Migración
- `V4__prestaciones_y_disponibilidad.sql` (V2 es la auditoría de C-03 y V3 la de C-04). Si C-05 entra antes, se renumera al siguiente libre.
- Convención de `CONVENCIONES.md`: `consultorio_id` con FK, `UNIQUE (consultorio_id, id)`, índices que empiezan por `consultorio_id` y constraints con nombre.
- **Permisos**: `REVOKE DELETE` solo en `tipo_prestacion`. `disponibilidad` conserva `DELETE`, porque el reemplazo completo lo necesita y la tabla es de configuración auditada.

## Risks / Trade-offs

- [C-04 todavía no está implementado: la entidad `Consultorio`, el asignador de slug y `SesionActual` pueden quedar en otro lugar] → como los dos changes son de Fran, se fija en la tarea 1.1 de C-04 que vivan en `consultorio` y `compartido` (D1, D3). La tarea 1.1 de C-08 lo verifica.
- [Choque de números de migración con C-03, C-04 y C-05] → número provisorio, fijado al rebasar (CHANGES.md).
- [El reemplazo completo de la grilla pierde el id de cada franja] → no se referencian desde otras tablas en v1. Si C-15 las necesitara, se pasa a una sincronización por diferencias.
- [Last-write-wins entre dos pestañas del mismo odontólogo] → `@Version` detecta guardados concurrentes, pero no un guardado basado en datos viejos. Se acepta en v1 para la configuración, que edita una sola persona.
- [El horizonte de 365 días es una regla nueva] → queda visible en este diseño para que Maxi la apruebe o la cambie.
- [El seed incluye un hash de una contraseña de desarrollo] → es un dato ficticio, solo con el perfil `local` (regla dura 17), y nunca entra en `db/migration`.

## Migration Plan

- Migración aditiva: dos tablas nuevas, sin datos. Rollback = no mergear. Una vez mergeada, los cambios van en una migración nueva.
- El seed local es repetible: cambiarlo no afecta otros entornos.

## UI Mockups

- Decision: no (no UI), 2026-11-20. Change solo de backend; las pantallas del asistente de configuración son de C-09 (Nacho), que lleva su propio mockup.
