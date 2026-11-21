# Design

## Context

- Ver `proposal.md` §Why. Governance **ALTO**: se propone y se espera la revisión de Maxi antes de escribir código.
- **Base disponible en `main`**:
  - C-02: `ConsultorioId`, `GeneradorDeIds`, `Reloj`, `ErrorDeNegocio` (400/409), `ManejadorGlobalDeErrores`, `NormalizadorDeTelefono` y `EnmascaradorDePii`, más las reglas ArchUnit `dominioSinFramework`, `repositoriosSiempreConTenant` y `horaSoloDesdeElClock`. `/api/v1/public/**` ya es pública en `ConfiguracionDeSeguridad`.
  - C-03: `PublicadorDeAuditoria`, con `CambioAuditado` y `ActorTipo.PACIENTE`.
  - C-05, todo en `auth`:
    - Bucket4j con Caffeine: `LimitadorDeSolicitudes`, `LimiteDeSolicitudes` y `LimitadorBucket4j`.
    - `GeneradorDeSecretos` y `EnviadorDeEmails`.
    - El patrón de email `AFTER_COMMIT` en un ejecutor acotado, de `EnvioDeRecuperacion`.
    - Repositorios con `JdbcClient`.
  - El paquete `reserva` existe y está vacío.
- **Lo que llega antes de implementar**:
  - C-08: `tipo_prestacion` (`activa`, `visible_publica`, `orden`), `disponibilidad`, las columnas de reglas de reserva del consultorio y `ErrorDeNegocio.propiedades()`.
  - C-10: `paciente` (único por DNI, DD-24), `turno` con `ex_turno_sin_solapamiento`, el VO `Dni` y la `CalculadoraDeHuecos` con `LimitesDeReserva`.
  - C-11: los casos de uso de turnos, los repositorios y la serialización de escrituras por consultorio que pide C-10.
- **Quién lo consume**: C-16 (frontend público), C-19 (exportación: excluye `token_gestion_turno`, RN-EX-02), C-20 (plan `VENCIDO`) y C-12 (marca de novedad, DD-30).

## Goals / Non-Goals

**Goals:**
- Que la API pública aplique todas las reglas RN-RP del lado del servidor (RN-GL-04), con la agenda de C-10 y C-11 como única fuente de verdad del solapamiento.
- Que un token de gestión no se pueda adivinar, filtrar ni distinguir: 256 bits, solo el hash en la base, 404 uniforme y fuera de logs y cachés.
- Que las carreras (mismo hueco, mismo DNI, doble cancelación) terminen en un estado válido y con un error estable.

**Non-Goals:**
- Pantallas, `.ics` y Service Worker (C-16).
- Email al odontólogo (DD-30): queda fuera de C-15 (D14).
- Captcha o verificación del teléfono: la v1 se apoya en el rate limit y en el límite de turnos (SU de [09](../../../knowledge-base/09_decisiones_y_supuestos.md)).
- El pase de `PRUEBA` a `VENCIDO` (C-20): C-15 solo lee `plan`.

## Decisions

### D1 — Módulo `reserva` con sus capas, que habla con `agenda` y `consultorio` por `application`
- `reserva.web`: `ControladorDeReservaPublica` y `ControladorDeGestionDeTurno`, con DTOs propios. Ningún DTO expone entidades de otros módulos.
- `reserva.application`: casos de uso `ConsultarPaginaPublica`, `ConsultarHuecosPublicos`, `ReservarTurnoPublico`, `VerTurnoPorToken`, `ConsultarHuecosParaReprogramar`, `ReprogramarPorToken` y `CancelarPorToken`.
- `reserva.domain`: `TokenDeGestion` (generar, hashear y validar el formato), `PlazoDeModificacion` (RN-RP-04, puro, con `ahora` como parámetro). La comparación de nombres para `requiere_revision` es `Paciente.datosDifierenDe` de C-10.
- `reserva.infrastructure`: `TokenDeGestionRepositorioJdbc`.
- Los datos de consultorio y agenda se piden por puertos de `application` de esos módulos (08: los módulos hablan por `application`). Se agrega la regla ArchUnit `reserva` no depende de `..agenda.domain..` ni de `..consultorio.infrastructure..`.

### D2 — Lo que C-15 necesita de C-11 (a incluir en su propuesta)
- **`CrearTurno`** con `origen = LINK` y actor `PACIENTE`. Valida RN-AG-01/03, nunca crea un sobreturno, publica `TURNO_CREADO`, mapea `ex_turno_sin_solapamiento` a `TURNO_SOLAPADO` y toma el lock por consultorio.
- **`ReprogramarTurno` y `CancelarTurno`** con un actor `PACIENTE` y la `version` esperada. Cancelar con `cancelado_por = PACIENTE`.
- **`RegistrarPaciente` / `BuscarPacientePorDni`**: alta idempotente por `(consultorio_id, dni)` y `marcarRevision()`, con sus eventos.
- **`LockDeAgenda.tomar(ConsultorioId)`**: `SELECT … FROM consultorio WHERE id = ? FOR UPDATE`, el mismo mecanismo que ya usa `ReemplazarDisponibilidad` de C-08 (mergeado en #15). Así, las reservas, los turnos manuales de C-11 y los cambios de grilla quedan en una sola cola por consultorio: una reserva nunca se valida contra una grilla que se está reemplazando. Es el pedido de Maxi a Fran en #18 y en la revisión de este PR. No se usa un lock consultivo aparte.
- Si C-11 no los trae, C-15 los agrega en `agenda.application` con sus tests. Eso agranda este change, y se avisa al rebasar.
- C-15 traduce los errores de agenda a errores públicos: `TURNO_SOLAPADO`, `FUERA_DE_DISPONIBILIDAD` y `HORARIO_BLOQUEADO` → 409 `HUECO_NO_DISPONIBLE`, para no revelar por qué un horario no está libre.

### D3 — Vigencia del token derivada del turno, sin `expira_at`
- `token_gestion_turno (id, consultorio_id, turno_id UNIQUE, token_hash CHAR(64) UNIQUE, revocado_at NULL, created_at)` con `fk_token_turno (consultorio_id, turno_id) → turno (consultorio_id, id)`.
- **Dos niveles de validez**, resueltos con un join en la misma consulta que busca por hash:
  - **Consultable** (solo la vista `GET /public/turnos/{token}`): `ahora < turno.fin` y turno `RESERVADO`, `CONFIRMADO` o `CANCELADO`. Así, el paciente cuyo turno canceló el odontólogo abre su link y ve que se lo cancelaron, en vez de un "link inválido" (revisión de Maxi al PR #30).
  - **Modificable** (huecos para reprogramar, reprogramación y cancelación): además, `revocado_at IS NULL` y turno `RESERVADO` o `CONFIRMADO`.
  - Fuera de eso (hash inexistente, turno terminado o `AUSENTE`): 404 uniforme.
- **Por qué mostrar el cancelado no revela nada nuevo**: solo lo ve quien tiene el token, que es el paciente del turno. La vista de un cancelado muestra el estado y quién canceló (`PACIENTE` u `ODONTOLOGO`), pero no el motivo de cancelación, que escribe el odontólogo y puede ser interno, igual que la nota.
- *Por qué no `expira_at = fin`* (como dice 04): el odontólogo puede reprogramar o ajustar la duración (C-11). Si no, el `expira_at` copiado quedaría desfasado, y C-11 tendría que conocer esta tabla. Derivarlo del turno elimina ese acoplamiento, y una cancelación del odontólogo deja de permitir modificaciones sin tocar `token_gestion_turno`.
- `revocado_at` se setea al cancelar por link (RN-RP-06): marca que el token ya no sirve para modificar, aunque la vista siga mostrando el turno cancelado hasta su fin.
- Al cerrar el change se actualiza 04 §TokenGestionTurno.
- Sin `DELETE` para `app_rw`. Índices: `ux_token_hash` y `ux_token_turno`.
- *Alternativa*: mantener `expira_at` y actualizarlo desde C-11 → acopla la agenda al módulo de reserva.

### D4 — Token y hash
- 32 bytes de `SecureRandom` (`GeneradorDeSecretos` de C-05) en base64url sin padding, 43 caracteres.
- Hash SHA-256 en hex. La búsqueda es por igualdad del hash, con índice único. La comparación ocurre en la base sobre el hash, así que no hay timing útil sobre el token.
- **Formato inválido** (no base64url o longitud ≠ 43): se responde 404 sin consultar la base, con el mismo cuerpo.
- **Una sola vez**: el token en claro solo vive en la respuesta 201 y en el email AFTER_COMMIT. `ReservaCreada.toString()` lo omite, y un test verifica que no aparece en los logs.

### D5 — Huecos públicos
- Se arma `DisponibilidadSemanal` desde la grilla de C-08 y se piden a C-10 los ocupados del rango: bloqueos y turnos activos, sobreturnos incluidos. La `CalculadoraDeHuecos` corre con `LimitesDeReserva(anticipacion, horizonte, ahora)` y el paso del consultorio.
- **Respuesta**: `{ zonaHoraria, dias: [{ fecha, atiende, huecos: [instantUTC] }] }`.
  - `atiende` = el día de la semana tiene franjas. Permite que C-16 muestre "sin turnos" en los días que atiende sin huecos (revisión de Maxi a #25).
- **Rango**: máximo 31 días, para acotar el costo por consulta. Con `desde`/`hasta` ausentes: de hoy a hoy + 13 días.
- **Para reprogramar**: la misma calculadora, excluyendo el propio turno de los ocupados.

### D6 — Reserva: una transacción, con el orden de validación fijo
1. Validar el formato (Bean Validation, `Dni`, teléfono) → 400.
2. Resolver el consultorio por slug → 404, y que tome turnos → 409 `RESERVA_NO_DISPONIBLE`.
3. Prestación activa y visible del consultorio → 404.
4. Abrir la transacción y tomar `LockDeAgenda` del consultorio.
5. Hueco ofrecible (D5, recalculado con `ahora`) → 409 `HUECO_NO_DISPONIBLE`.
6. Buscar o crear el paciente por DNI (`INSERT … ON CONFLICT (consultorio_id, dni) DO NOTHING` y `SELECT`), y marcar la revisión si el nombre difiere.
7. Contar los turnos futuros activos `LINK` del paciente → 409 `LIMITE_TURNOS`.
8. `CrearTurno` (C-11), insertar el token y publicar los eventos.
9. Commit. El email sale `AFTER_COMMIT`.
- **El lock es lo que hace correctos los pasos 5 a 7 con concurrencia**: la exclusión de la base protege el solapamiento, pero no el límite por DNI. Con el lock por consultorio, dos reservas del mismo DNI se serializan. El costo es serializar las reservas de un mismo consultorio, que tiene un solo sillón y bajo volumen.
- La exclusión sigue como red de seguridad. Si salta, se traduce a `HUECO_NO_DISPONIBLE`.
- **El 409 `HUECO_NO_DISPONIBLE`** lleva `huecos` con los huecos del mismo día (D5), calculados después del rollback.
- **Orden de los errores 409**: hueco antes que límite. Así, quien elige un horario ocupado no recibe `LIMITE_TURNOS` por un turno que no se habría podido crear.

### D7 — Deduplicación y nombres
- `Paciente.datosDifierenDe` (la regla única de C-10 y C-11) normaliza con trim, espacios simples, minúsculas y NFD sin marcas diacríticas, y compara `nombre` y `apellido` por separado. "José  Pérez" = "jose perez".
- Si el paciente existe, sus datos no se sobrescriben: ni el teléfono ni el email nuevos (DD-24). El email para la confirmación se toma del pedido, no del paciente guardado, y no se persiste si el paciente ya existía.
- El consentimiento (`consentimiento_at` y `consentimiento_version`) se actualiza en cada reserva por link, porque es la aceptación vigente de esa persona. Si cambia, se audita en `PACIENTE_CONSENTIMIENTO_ACTUALIZADO` con versión anterior y nueva.
- **Versión vigente**: propiedad `app.privacidad.version-vigente` (hoy `2026-11-borrador`, de `docs/legal/politica-de-privacidad.md`), análoga a `TERMINOS_VERSION_VIGENTE` de C-04.

### D8 — Gestión por token
- **Vista**: `puedeModificar = turno activo ∧ ahora ≤ inicio − anticipacion_cancelacion_min`, `puedeReprogramar = puedeModificar ∧ tomaTurnosOnline` y `modificableHasta = inicio − anticipación`. Para un turno `CANCELADO`: `estado`, `canceladoPor`, las dos banderas en `false` y el teléfono del consultorio.
- **Cancelar**:
  - lock de agenda, token válido y plazo → `CancelarTurno` (C-11) con la `version` leída;
  - `revocado_at = ahora` y evento.
- **Reprogramar**:
  - lock de agenda, token válido, toma turnos y plazo sobre el horario **actual**;
  - hueco ofrecible para `nuevoInicio` (D5, sin el propio turno);
  - `ReprogramarTurno` (C-11) con la `version` → `TURNO_REPROGRAMADO`.
- **El estado se conserva al reprogramar** (un `CONFIRMADO` sigue confirmado). RN-AG-06 no tiene la transición `CONFIRMADO → RESERVADO`, y la marca de novedad (D14) le avisa al odontólogo. Decidido por Maxi.
- **Doble cancelación**: el lock la serializa. La segunda ve el turno `CANCELADO` → no modificable → 404. Si llega a la base con una `version` vieja → 409 `CONFLICTO_CONCURRENCIA`.

### D9 — Rate limiting y tamaño del body
- **Mover a `compartido.application`** `LimitadorDeSolicitudes`, `LimiteDeSolicitudes` y `ResultadoDeLimite`, y a `compartido.infrastructure` `LimitadorBucket4j`. Sigue el mismo comportamiento. `auth` los importa desde ahí. Con `GeneradorDeSecretos` y `EnviadorDeEmails` pasa lo mismo, porque `reserva` también los usa.
  - Es un movimiento de paquetes en el dominio `auth` (RN-AU-06): sin cambios de lógica, con los tests de C-05 en verde antes y después, en un commit `refactor` propio.
- **`LimitesPublicosFilter`** sobre `/api/v1/public/**`, antes de los controladores. Mismo formato 429 y `Retry-After` que C-05.
  - **Por IP**: `public-reserva` (POST de turnos), 10 por hora, y `public-consulta` (todos los GET), 60 por minuto.
  - **Por token**: `public-token` (POST de reprogramación y cancelación), 10 por hora. La clave es el **hash** del token, para que el token no viva en la memoria del limitador.
  - Los GET por token cuentan en el cupo por IP de consulta. Las "10 operaciones/hora" por token de RN-RP-09 son solo de modificación (reprogramar y cancelar), para que recargar la página no agote el cupo. Decidido por Maxi.
- **Aprobado por Maxi** (RN-AU-06) en [este comentario del PR #30](https://github.com/MaximilianoKotlik/sistema-odontologico/pull/30#issuecomment-7054269867), con tres condiciones:
  - solo cambia el paquete;
  - va en un commit `refactor` propio;
  - los tests de C-05 tienen que estar en verde antes y después.

  Si hace falta tocar algo más que el paquete, se vuelve a pedir la aprobación.
- **Tamaño del body**: `TamanoMaximoDeCuerpoFilter` rechaza con 413 por `Content-Length` > 64 KB, y corta la lectura si un cuerpo chunked supera ese tamaño.
- **IP real detrás del proxy**: misma limitación que C-05, ya en la checklist de C-23.

### D10 — Logs y caché
- **Enmascarado del token**: un `PatronDeRutaSensible` reemplaza el segmento después de `/public/turnos/` por `***` en el log de acceso y en el MDC de la petición. El `ManejadorGlobalDeErrores` no incluye la URI cruda en `instance` para esas rutas.
  - Un test captura los logs de una vista y una cancelación y verifica que el token no aparece.
- **Caché**: `Cache-Control: no-store` en `/api/v1/public/turnos/**`. 08 ya pide que el Service Worker no las cachee (C-16).
- `Referrer-Policy: no-referrer` ya está desde C-02.

### D11 — Auditoría
- **Actor**: `Actor(PACIENTE, pacienteId)` y la IP de la petición.
- **Eventos**:
  | Acción | Entidad | Valores |
  |---|---|---|
  | `PACIENTE_CREADO` | `paciente` | nuevo `{origen: LINK}` |
  | `PACIENTE_MARCADO_PARA_REVISION` | `paciente` | `{requiere_revision: false → true}` |
  | `PACIENTE_CONSENTIMIENTO_ACTUALIZADO` | `paciente` | `{consentimiento_version}` |
  | `TURNO_CREADO` | `turno` | nuevo `{inicio, fin, duracion_min, tipo_prestacion_id, estado, origen}` |
  | `TURNO_REPROGRAMADO` | `turno` | `{inicio, fin}` anterior y nuevo |
  | `TURNO_ESTADO_CAMBIADO` | `turno` | `{estado, cancelado_por}` anterior y nuevo |
- Nunca nombre, DNI, teléfono, email ni token (RN-AD-05). `CambioAuditado` ya rechaza las claves de secretos.
- Si C-11 publica `TURNO_*` dentro de sus casos de uso, C-15 no los duplica: solo pasa el actor.

### D12 — Email de confirmación al paciente (DD-26)
- `ReservaConfirmada` (evento de aplicación con el email, la fecha local, el consultorio y el link) se envía con `@TransactionalEventListener(AFTER_COMMIT)` en el ejecutor acotado de C-05.
- Si no hay SMTP o falla el envío, queda un log sin PII ni token y la reserva sigue.
- El texto está en español con voseo. Sin datos de salud.

### D13 — Plan `VENCIDO` y reserva desactivada
- `tomaTurnosOnline = reserva_publica_activa ∧ plan ≠ VENCIDO`. C-15 no evalúa `prueba_hasta`: el pase a `VENCIDO` es de C-20 (RN-PL-03).
- **Bloquea**: huecos públicos, reserva, huecos para reprogramar y reprogramación (409 `RESERVA_NO_DISPONIBLE`, código aprobado por Maxi).
- **No bloquea**: la vista y la cancelación por token (RN-RP-11).

### D14 — Marca de novedad para el odontólogo (DD-30)
- Columna `turno.novedad_paciente_at TIMESTAMPTZ NULL` (decisión de Maxi en el PR #30). La crea la migración del primero que se mergee entre C-12 y C-15. Al rebasar, el otro verifica que exista y no la vuelve a crear.
- **C-15 la escribe** con `ahora` en la misma transacción que reservar, reprogramar o cancelar por link. **C-12 la limpia** con `POST /api/v1/turnos/{id}/visto`.
- No genera un evento propio: el cambio que la dispara ya está auditado (`TURNO_CREADO`, `TURNO_REPROGRAMADO` o `TURNO_ESTADO_CAMBIADO`).
- El email al odontólogo de DD-30 queda fuera de C-15. Se decide en qué change va (C-20 o después de la v1) al planificarlo.

## Risks / Trade-offs

- **[C-11 sin propuesta]** → D2 lista lo que C-15 necesita. Si C-11 no lo incluye, este change suma esos casos de uso y crece. Se coordina con Fran antes de su propuesta.
- **[Lock por consultorio]** → serializa las reservas de un consultorio, y también contra los cambios de grilla y los turnos manuales (es el `FOR UPDATE` sobre `consultorio` de C-08). Con un sillón y volúmenes bajos es aceptable. Una edición de la configuración (`EditarConfiguracion`, con optimistic locking) puede esperar a que termine una reserva en curso, y no falla.
- **[Revelación por `LIMITE_TURNOS`]** → quien escriba el DNI de otra persona puede deducir que tiene turnos en ese consultorio. Maxi aceptó el trade-off en la revisión de #25. Lo mitigan el rate limit por IP y que la respuesta no revela cuáles ni cuándo.
- **[Rate limit en memoria]** → con una instancia alcanza (08). Con varias, cada una cuenta aparte. Se migra con el despliegue si hace falta.
- **[DNI ajeno]** → sin verificación, alguien puede reservar con un DNI ajeno (trade-off de DD-24). El odontólogo ve el turno con la marca de revisión si el nombre no coincide.
- **[Desvío de 04 en `expira_at`]** → D3 lo justifica. Se actualiza 04 al cerrar.
- **[Mover clases de `auth`]** → riesgo bajo, porque solo cambia el paquete. Igual requiere aprobación (RN-AU-06), que se pide con esta propuesta.

## Migration Plan

- Migración aditiva `V{n}__token_gestion_turno.sql` con número provisorio. Se fija al rebasar después de C-08, C-10 y C-11 y queda posterior a la de `turno`.
- Sin datos en producción. Rollback = no mergear. Una vez mergeada, los ajustes van en una migración nueva (regla dura 5).

## Open Questions

Resueltas por Maxi en la revisión del PR #30 (2026-11-20):

1. **Marca de novedad (DD-30)**: `turno.novedad_paciente_at`, que escribe C-15 y limpia C-12 (D14).
2. **Reprogramar un `CONFIRMADO`**: conserva el estado, sin una transición nueva en RN-AG-06 (D8).
3. **"10 operaciones por hora por token"**: solo modificaciones. Las lecturas van en el cupo por IP (D9).
4. **Email al odontólogo (DD-30)**: fuera de C-15. Se decide el change al planificarlo (D14).
5. **`RESERVA_NO_DISPONIBLE` (409)**: aprobado. C-16 lo suma a sus mensajes (D13).
6. **Correcciones de documentación** (CHANGES.md §C-15, FL-04 y 04): en el commit de cierre de este change.

Cambio pedido y aplicado: el turno cancelado se sigue viendo por su link hasta que termina (D3, D8 y spec `gestion-turno-paciente`).

El movimiento de clases de `auth` (D9, RN-AU-06) lo aprobó Maxi en [este comentario](https://github.com/MaximilianoKotlik/sistema-odontologico/pull/30#issuecomment-7054269867). El `LockDeAgenda` es el `FOR UPDATE` sobre `consultorio` de C-08 (D2), según la revisión de Maxi en #18 y en este PR.

## UI Mockups

- Decision: no (no UI), 2026-11-20. Change de backend sin pantallas. Las pantallas públicas son de C-16 (Octi, mockups en el PR #25).
