# Tasks

> Strict TDD en cada tarea: test en rojo → verde → triangular → refactor dentro del ciclo. No arrancar hasta que **C-01 esté `[x]`** y Maxi apruebe esta propuesta (governance CRITICO). Verificación base de cada grupo: `./gradlew test` en verde.

## 1. Preparación sobre C-01

- [x] 1.1 Actualizar la rama desde `main` con C-01 mergeado y confirmar paquete base, roles `app_owner`/`app_rw` y quién crea las extensiones; ajustar D7 de `design.md` si difiere (verificación: `design.md` sin la Open Question o con la decisión anotada)
- [x] 1.2 Agregar `libphonenumber` y `java-uuid-generator` a `build.gradle.kts` (verificación: `./gradlew dependencies` las lista y `./gradlew test` sigue verde)

## 2. Migración V1 y convención de tenant

- [x] 2.1 Test de integración (Testcontainers PG16) que espera las extensiones `btree_gist` y `citext` y la tabla `consultorio` con defaults de la spec; crear `V1__consultorio_y_extensiones.sql` hasta que pase (verificación: escenario "Alta con valores por defecto")
- [x] 2.2 Tests de constraints: anticipación > 10080, paso de huecos inválido, plan inválido, slug con formato inválido y slug duplicado fallan en la base (verificación: escenarios "Valores fuera de rango" y "Slug en uso" a nivel base)
- [x] 2.3 Test de permisos: `app_rw` puede `SELECT/INSERT/UPDATE` y no `DELETE` sobre `consultorio` (verificación: test de integración con conexión como `app_rw`)
- [x] 2.4 Escribir `db/migration/CONVENCIONES.md` con la convención de `consultorio_id`, `UNIQUE (consultorio_id, id)`, FKs compuestas e índices (verificación: lo referencia el test de 4.3, que crea sus tablas siguiéndolo)

## 3. Núcleo de dominio de `compartido`

- [x] 3.1 `GeneradorDeIds` UUIDv7: tests de versión 7, unicidad y orden; implementación con `java-uuid-generator` (verificación: escenario "Identificadores ordenables y no secuenciales")
- [x] 3.2 Bean `Clock` UTC y regla ArchUnit que prohíbe `now()` sin `Clock` (verificación: test de arquitectura falla con una clase de prueba que llama `Instant.now()` y pasa sin ella)
- [x] 3.3 `ConsultorioId` y `TenantContext` `@RequestScope` con `actual()`/`establecer()`: tests de tenant ausente, establecido y reemplazo por otro distinto (verificación: escenario "Operación sin tenant resuelto")
- [x] 3.4 `ValidadorDeSlug` con `PalabrasReservadas`: tests de todos los casos válidos, inválidos y reservados de la spec (verificación: escenarios de `slug-consultorio` "Formato" y "Palabras reservadas")
- [x] 3.5 `SugeridorDeSlug`: tests con tildes, `&`, espacios dobles, nombre largo, reservado y corto (verificación: escenarios de "Sugerencia de slug")
- [x] 3.6 `NormalizadorDeTelefono`: primero el test de que `011 15-0000-0001` y `+54 9 11 0000-0001` dan `+5491100000001` con la versión real de la librería (si falla, ajustar la spec y avisar a Maxi), luego fijo, inválidos y que el error no incluye el valor (verificación: escenarios de `telefono-e164`)

## 4. Errores HTTP y aislamiento

- [x] 4.1 Jerarquía `ErrorDeNegocio` y `ManejadorGlobalDeErrores`: tests MockMvc con controlador de prueba para negocio (409), `VALIDACION` con `errores` por campo, `CUERPO_INVALIDO`, `RECURSO_NO_ENCONTRADO`, `CONFLICTO_CONCURRENCIA` y `ERROR_INTERNO` sin mensaje ni teléfono en el cuerpo (verificación: escenarios de `errores-api`)
- [x] 4.2 Mapeo de violación de `uk_consultorio_slug` a 409 `SLUG_EN_USO` (verificación: test de integración que inserta dos consultorios con el mismo slug vía la capa de aplicación)
- [x] 4.3 Test de integración de aislamiento con `recurso_prueba`/`recurso_prueba_hijo` creadas en el test y repositorio + controlador solo en `src/test`: FK cruzada falla, listado de A no ve B, `GET` de id de B y de id inexistente dan el mismo 404 (verificación: escenarios de `aislamiento-tenant` "Referencia…", "Listado entre dos consultorios" y "Recurso ajeno")
- [x] 4.4 Test de que un `consultorio_id` en body o query no cambia el tenant del controlador de prueba (verificación: escenario "Consultorio en el body ignorado")
- [x] 4.5 Reglas ArchUnit D9 (repositorios con `ConsultorioId`, Spring Data solo en `infrastructure`) con exclusión documentada de `consultorio` (verificación: la regla falla con un repositorio de prueba sin tenant y pasa sin él)

## 5. Puerto de auditoría

- [x] 5.1 `EventoDeAuditoria` con validación: campos obligatorios y claves prohibidas (`password`, `password_hash`, `token`, `*_token`, `*_hash`) (verificación: escenarios "Evento sin consultorio" y "Valor con contraseña")
- [x] 5.2 `PublicadorDeAuditoriaSpring`: test de integración con un listener `BEFORE_COMMIT` de prueba: recibe el evento con instante del `Clock` fijo, no confirma tras rollback, y publicar sin transacción falla (verificación: escenarios de "Publicación dentro de la transacción del cambio")

## 6. Cierre del change

- [x] 6.1 `./gradlew test` completo en verde, cobertura ≥ 80 % en `compartido.domain` y `compartido.application`, y `npm run api:types` sin diferencias (verificación: salida de CI del PR)
- [x] 6.2 Pasar el PR de borrador a listo con evidencia (tests y cobertura) y pedir revisión a Maxi y revisión técnica a Mati (verificación: PR con ambos revisores asignados)

## Workflow follow-up

- Maxi marca `[x]` en `CHANGES.md` al mergear.
- Archivar el change con `/opsx:archive` después del merge, para crear las specs principales.
