# Tasks

> Requisitos previos: C-02 mergeado en `main` (`[x]`), rebase de `feat/C-03-auditoria-base` y la propuesta aprobada por Maxi (governance CRITICO).
> Strict TDD en cada tarea: test que falla → código mínimo → triangular → refactor.

## 0. Alinear con C-02 implementado

- [x] 0.1 Contrastar §Contrato de C-02 (K-1..K-6) de `design.md` con el código mergeado de C-02: nombres, tipo del evento publicado y campos. Actualizar `design.md` (y la spec si cambiara algún comportamiento) y verificar con `openspec validate auditoria-base --strict`
- [x] 0.2 Fijar el número de la migración con el siguiente `V{n}` libre en `backend/src/main/resources/db/migration/` (`V2`) y verificar que no choque con C-04 (todavía sin código)

## 1. Esquema append-only

- [x] 1.1 Escribir el test de integración (extiende `IntegracionPostgresTest`) que inserta un evento completo y lo relee; verificar que falla en rojo porque la tabla no existe
- [x] 1.2 Crear la migración `V2__evento_auditoria.sql` con la tabla, `ck_evento_auditoria_actor_tipo`, la FK a `consultorio` sin `ON DELETE CASCADE`, `UNIQUE (consultorio_id, id)`, los índices de `04`, y `REVOKE UPDATE, DELETE, TRUNCATE` + `GRANT INSERT, SELECT` a `"${app-user}"` (el placeholder ya existe desde C-02); verificar que el test 1.1 pasa y que la aplicación arranca con la migración aplicada
- [x] 1.3 Tests de que `UPDATE`, `DELETE` y `TRUNCATE` con `app_rw` fallan, y de que el trigger rechaza `UPDATE`, `DELETE` y `TRUNCATE` conectado como `app_owner` (que tiene todos los privilegios); agregar `evento_auditoria_inmutable()` y los triggers de fila y de sentencia a la misma migración (todavía no mergeada); verificar en verde
- [x] 1.4 Tests de un consultorio inexistente (FK) y de un `actor_tipo` inválido (CHECK) que fallan; verificar en verde

## 2. Persistencia desde el puerto de C-02

- [x] 2.1 Test unitario del mapeo de `EventoDeAuditoria` al registro (`id` desde un `GeneradorDeIds` de prueba, instante del evento, actor y valores) y de la IP inválida que queda en `null` sin resolver DNS; implementarlo en `auditoria.domain`; verificar en verde
- [x] 2.2 Implementar `RepositorioDeAuditoriaJdbc` con `JdbcClient` en `auditoria.infrastructure` (JSONB e INET con `CAST`); verificar con tests de integración que persisten un evento con valores anidados y otro de `SISTEMA` sin actor, sin valor anterior y sin IP
- [x] 2.3 Test de integración: el caso de uso publica el evento y confirma → el evento queda; publica y después se revierte → no queda. Implementar `RegistradorDeAuditoria` con `@TransactionalEventListener(BEFORE_COMMIT)`; verificar en verde
- [x] 2.4 Test de integración: el caso de uso inserta un consultorio y publica un evento de otro consultorio inexistente → la persistencia del evento falla y el consultorio tampoco queda; verificar en verde
- [x] 2.5 Test de integración: un caso de uso crea un consultorio **con JPA** (entidad mínima de prueba en `pruebas.auditoria`, registrada con `@EntityScan` solo en ese test) y publica un evento de ese consultorio en la misma transacción → se persisten los dos. Verificar que falla en rojo sin el flush; agregar `EntityManager.flush()` al adaptador; verificar en verde
- [x] 2.6 Test de integración: eventos de los consultorios A y B quedan cada uno solo bajo su `consultorio_id`, y una IP inválida se guarda vacía sin revertir el cambio; verificar en verde

## 3. Logs sin PII

- [x] 3.1 Tests unitarios del caso nuevo de `EnmascaradorDePii`: token base64url de 43 caracteres y hash SHA-256 hex → `***`, UUID legible. Implementarlo; verificar en verde junto con los tests existentes de C-02
- [x] 3.2 Tests unitarios de `MensajeSinPii` y `ExcepcionSinPii` (email, teléfono `+54 9 11 0000-0001`, token y texto sin PII intacto, en el mensaje y en el stack trace); implementarlos en `compartido.infrastructure.logging`; verificar en verde
- [x] 3.3 Test con `OutputCaptureExtension` que loguea una `DataIntegrityViolationException` cuya causa contiene `Key (email)=(odontologa@example.com) already exists` y comprueba que ni el mensaje ni el stack trace muestran el email en claro; crear `logback-spring.xml` con las conversiones redefinidas; verificar en verde
- [x] 3.4 Test que captura el log del listener ante un fallo y comprueba que contiene `accion`/`entidad` pero no los valores; verificar en verde

## 4. Integración y cierre

- [x] 4.1 Agregar la regla ArchUnit "ningún módulo fuera de `auditoria` depende de `auditoria..`" y verificar `./gradlew test` completo en verde
- [x] 4.2 Revisar que los fixtures usen solo datos ficticios evidentes y que no haya `LocalDateTime` en el módulo; verificar con `grep` sobre `backend/src`
- [ ] 4.3 Abrir el PR listo para revisión con la evidencia de los tests y pedirle la revisión a Maxi; Fran revisa el uso del puerto

## Workflow follow-up

- Una vez aprobado el PR, el autor marca `[x]` en `CHANGES.md` y archiva el change en el último commit del PR (cambio de proceso del commit 06ada06).
- Archivar el change (`/opsx:archive`) para que el delta se aplique sobre la capability `auditoria` que creó C-02.
