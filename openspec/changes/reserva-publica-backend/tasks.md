# Tasks

> Strict TDD en cada tarea: test en rojo → verde → triangular → refactor dentro del ciclo. **No arrancar** hasta que se cumplan dos condiciones:
> - C-08 y C-11 están `[x]`.
> - Propuesta y movimiento de clases de `auth` aprobados por Maxi en el PR #30 (design D9, RN-AU-06).
>
> Verificación base de cada grupo: `./gradlew test` en verde. Los tests de integración extienden `IntegracionPostgresTest` (Testcontainers, PostgreSQL 16) y usan solo datos ficticios evidentes (regla dura 17): DNI `90000001`…, teléfonos `+54 9 11 0000-00xx` y emails `@example.com`.

## 1. Preparación

- [x] 1.1 Rebasar sobre `main` con C-08, C-10 y C-11 mergeados. Fijar el número de la migración (posterior a la de `turno`) y verificar que existan las piezas de design D2 (`CrearTurno` con `LINK`, `ReprogramarTurno`/`CancelarTurno` con actor `PACIENTE`, alta por DNI y `LockDeAgenda` como `FOR UPDATE` sobre `consultorio`, design D2). Para cada pieza que falte, sumar una tarea en el grupo 4 con su test (verificación: suite existente en verde después del rebase y lista de piezas en el PR)
- [x] 1.2 Volcar al design las respuestas de Maxi a §Open Questions (revisión del PR #30) y ajustar specs y tareas (verificación: `openspec validate reserva-publica-backend --strict` en verde)
- [x] 1.3 Regla ArchUnit: `reserva` no depende de `..agenda.domain..` ni de `..consultorio.infrastructure..`, con un fixture que la viole (verificación: `ReglasDeArquitecturaTest` detecta el fixture y `ArchitectureTest` pasa)

## 2. Movimiento de piezas compartidas desde `auth` (refactor, RN-AU-06)

- [x] 2.1 Mover `LimitadorDeSolicitudes`, `LimiteDeSolicitudes` y `ResultadoDeLimite` a `compartido.application`, y `LimitadorBucket4j` a `compartido.infrastructure`, sin cambiar lógica (verificación: todos los tests de C-05 en verde, sin editar sus aserciones; commit `refactor(...)` propio)
- [x] 2.2 Mover `GeneradorDeSecretos` y `EnviadorDeEmails` (con su implementación SMTP) a `compartido`, sin cambiar lógica (verificación: tests de recuperación de contraseña en verde y `ArchitectureTest` sin dependencias de `reserva` hacia `auth`)

## 3. Dominio de `reserva`

- [x] 3.1 `TokenDeGestion`: genera 32 bytes en base64url de 43 caracteres, calcula el SHA-256 hex, rechaza formatos inválidos y su `toString()` no expone el valor (verificación: tests unitarios de longitud, alfabeto, hash conocido y formato inválido)
- [x] 3.2 `PlazoDeModificacion` con `ahora` como parámetro: dentro, fuera y justo en el límite (verificación: escenarios "Cancelar fuera de plazo" y "Justo en el límite" a nivel unitario)
- [x] 3.3 `ComparadorDeNombres`: mayúsculas, tildes y espacios extra iguales; un nombre distinto es distinto (verificación: tests unitarios con "José  Pérez" vs "jose perez" y "Otra Persona")

## 4. Migración y repositorio del token

- [x] 4.1 Test de integración de `token_gestion_turno`: hash único, un token por turno, FK compuesta a un turno de otro consultorio falla y `app_rw` sin `DELETE`. Después, crear `V{n}__token_gestion_turno.sql` según design D3 (verificación: test en verde y `MigracionesTest` aplica todas las versiones en orden)
- [x] 4.2 `TokenDeGestionRepositorioJdbc` con join al turno y los dos niveles de design D3: `buscarConsultable(hash, ahora)` (activo o cancelado, antes del fin) y `buscarModificable(hash, ahora)` (activo y no revocado). Siempre filtra por el consultorio del turno encontrado (verificación: test de integración con vigente, revocado, cancelado por el odontólogo, ausente y terminado en los dos métodos, y la regla `repositoriosSiempreConTenant` en verde)
- [x] 4.3 `turno.novedad_paciente_at` (design D14): si C-12 ya la creó, verificar que exista. Si no, agregarla en la migración de este change (verificación: test de integración que la lee después del rebase)

## 5. Página pública y huecos

- [x] 5.1 `ConsultarPaginaPublica` y `GET /api/v1/public/consultorios/{slug}`: prestaciones activas y visibles en orden, `tomaTurnosOnline` según D13 y 404 por slug inexistente (verificación: escenarios del requirement "Página pública del consultorio" y "Consultorio que no toma turnos online" en MockMvc + Testcontainers)
- [x] 5.2 `ConsultarHuecosPublicos` y `GET .../huecos`: días con `atiende`, sobreturnos ocupando, anticipación y horizonte, rango por defecto y máximo de 31 días, prestación de otro consultorio o no visible da 404 y la reserva desactivada da 409 `RESERVA_NO_DISPONIBLE` (verificación: escenarios de "Consulta de huecos ofrecibles", "Validación de la consulta de huecos" y "Reserva y huecos deshabilitados sin reserva online")

## 6. Reserva

- [x] 6.1 Validación del pedido: datos obligatorios, `aceptaPrivacidad`, `DNI_INVALIDO`, `TELEFONO_INVALIDO` y un body con `consultorioId` ignorado (verificación: escenarios de "Datos obligatorios de la reserva" y "Body con otro consultorio")
- [x] 6.2 `ReservarTurnoPublico` en el orden de design D6: alta del turno `RESERVADO`/`LINK` con `novedad_paciente_at`, consentimiento con la versión vigente (`app.privacidad.version-vigente`), token emitido una vez y respuesta 201 con `linkGestion` (verificación: escenarios "Reserva exitosa", "Hueco no ofrecible", "Solo el hash en la base" y "Tokens distintos")
- [x] 6.3 Deduplicación por DNI y marca de revisión, sin sobrescribir datos y por consultorio (verificación: escenarios "DNI con puntos ya registrado", "Mismo DNI con otro nombre" y "Mismo DNI en otro consultorio")
- [x] 6.4 Límite de 2 turnos futuros activos por link (verificación: escenarios "Tercer turno" y "Turnos que no cuentan")
- [x] 6.5a Concurrencia con la grilla: una reserva y un `PUT /api/v1/disponibilidad` simultáneos que quitan la franja del hueco. O la reserva entra antes y la grilla se reemplaza después, o la reserva ve la grilla nueva y responde 409 `HUECO_NO_DISPONIBLE`. Nunca queda un turno validado contra la grilla vieja (verificación: test de integración con dos hilos y un latch, repetido 20 veces)
- [x] 6.5 Concurrencia con dos hilos y un `CountDownLatch` contra Postgres real: mismo hueco con distintos pacientes (exactamente una 201 y un 409 con `huecos` sin ese horario) y mismo DNI en dos horarios con el límite al borde (verificación: escenarios "Dos pacientes al mismo tiempo" y "Reservas simultáneas del mismo DNI", repetidos 20 veces sin fallos)
- [x] 6.6 Auditoría de la reserva según design D11, sin PII ni token, y rollback completo si falla el evento (verificación: escenarios "Eventos de una reserva de paciente nuevo" y "Falla la auditoría")
- [x] 6.7 Email de confirmación `AFTER_COMMIT` con Mailpit/GreenMail en el test, y SMTP caído sin afectar la respuesta (verificación: escenarios "Reserva con email" y "SMTP caído")

## 7. Gestión por token

- [x] 7.1 `VerTurnoPorToken` y `GET /api/v1/public/turnos/{token}`: campos de la vista, `puedeModificar`, `puedeReprogramar`, `modificableHasta`, sin nota, DNI, teléfono ni email, y `Cache-Control: no-store` (verificación: escenarios de "Vista del turno por token" y "Vista sin caché")
- [x] 7.2 404 uniforme en la vista: token inexistente, malformado, de un turno terminado y de un turno ausente, con cuerpos idénticos byte a byte (verificación: escenarios de "Vigencia del token de gestión")
- [x] 7.2b Vista de un turno cancelado por el paciente y por el odontólogo: `estado: CANCELADO`, `canceladoPor`, sin acciones y sin el motivo; huecos, reprogramación y cancelación con ese token dan 404 (verificación: escenarios de "Vista de un turno cancelado")
- [x] 7.3 `CancelarPorToken`: plazo, `CANCELADO` con `cancelado_por = PACIENTE`, revocación, horario liberado, `novedad_paciente_at`, evento y cancelación con plan `VENCIDO` (verificación: escenarios "Cancelación dentro del plazo", "Cancelar fuera de plazo" y "Cancelar con plan vencido")
- [x] 7.4 `ConsultarHuecosParaReprogramar` y `GET .../huecos` por token, sin contar el propio turno (verificación: escenario "Moverse media hora")
- [x] 7.5 `ReprogramarPorToken`: mismo id y token, estado conservado (decisión de Maxi), `novedad_paciente_at`, evento, hueco tomado o no ofrecible con el horario original intacto, y `RESERVA_NO_DISPONIBLE` con la reserva desactivada (verificación: escenarios de "Reprogramación por link" y "Reprogramar con la reserva desactivada")
- [x] 7.6 Concurrencia de la gestión: dos cancelaciones simultáneas y una cancelación del paciente contra una reprogramación del odontólogo (verificación: escenario "Dos cancelaciones simultáneas", con un solo evento de cancelación)

## 8. Seguridad de la superficie pública

- [x] 8.1 `LimitesPublicosFilter` con los cupos de design D9, configurables por propiedades, con la clave por token sobre el hash (verificación: escenarios de "Límite de solicitudes en la API pública", con los límites bajados en el perfil de tests)
- [x] 8.2 `TamanoMaximoDeCuerpoFilter` de 64 KB, con `Content-Length` y con cuerpo chunked (verificación: escenario "Cuerpo enorme")
- [x] 8.3 Enmascarado del token en logs de acceso, MDC y Problem Details (verificación: escenario "Log de una vista por token", capturando la salida de Logback en una vista, una cancelación y un 404)
- [x] 8.4 Aislamiento por tenant en **cada** endpoint público: slug de A con prestación, turno o token de B da 404 (verificación: un test por endpoint, regla dura 6)

## 9. Contrato y documentación

- [ ] 9.1 Anotar los 6 endpoints con springdoc (respuestas 201/200/400/404/409/413/429 y el cuerpo de `HUECO_NO_DISPONIBLE`, `FUERA_DE_PLAZO` y `RESERVA_NO_DISPONIBLE`) y regenerar `frontend/src/shared/api-types.ts` (verificación: el job de CI "api-types" sin diferencias y `ContratoOpenApiTest` en verde)
- [ ] 9.2 Documentar en `backend/README.md` los cupos públicos y sus propiedades, y la versión vigente de la política de privacidad (verificación: los nombres de propiedades del README coinciden con `application.yml`)

## 10. Verificación integral

- [ ] 10.1 Recorrido FL-04 y FL-05 de punta a punta con `docker compose up` y el seed `demo`: reservar, ver por token, reprogramar, cancelar y ver el email en Mailpit (verificación: capturas o log de `curl` en el PR, con datos ficticios)

## Workflow follow-up

- Al aprobar el PR: commit de cierre con `[x]` en CHANGES.md, las correcciones de documentación de design §Open Questions 6 (CHANGES §C-15, 07 FL-04 y 04 §TokenGestionTurno) y `openspec archive reserva-publica-backend`.
