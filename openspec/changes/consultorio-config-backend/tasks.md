# Tasks

> Strict TDD en cada tarea: test en rojo → verde → triangular → refactor dentro del ciclo. **No arrancar** hasta que C-03 y C-04 estén `[x]` y Maxi apruebe esta propuesta. Verificación base de cada grupo: `./gradlew test` en verde. Todos los tests de integración extienden `IntegracionPostgresTest` y usan datos ficticios (regla dura 17).

## 1. Preparación

- [ ] 1.1 Rebasar sobre `main` con C-03 y C-04 mergeados; verificar que `Consultorio`, `ConsultorioRepositorio` y `AsignadorDeSlugUnico` vivan en el módulo `consultorio` y que exista `SesionActual` (design D1, D3). Si falta `SesionActual`, implementarlo con su test en `auth.infrastructure` (verificación: el registro de C-04 sigue verde y un test obtiene el `usuarioId` del JWT)
- [ ] 1.2 `ErrorDeNegocio` con `propiedades()` y el manejador que las agrega al Problem Details (verificación: test en `ManejadorGlobalDeErroresTest` con una propiedad extra; los tests existentes siguen verdes)

## 2. Migración

- [ ] 2.1 Test de integración de `tipo_prestacion`: duración fuera de rango o no múltiplo de 5 falla, nombre repetido con otras mayúsculas en el mismo consultorio falla y en otro consultorio pasa, `app_rw` sin `DELETE`; crear la migración (verificación: tests verdes)
- [ ] 2.2 Test de integración de `disponibilidad`: día fuera de 1..7, fin no posterior al inicio y minutos no múltiplos de 5 fallan; dos franjas solapadas del mismo día fallan por `ex_disponibilidad_sin_solape`; contiguas, en días distintos o de otro consultorio pasan (verificación: escenario "Red de seguridad en la base")

## 3. Configuración del consultorio

- [ ] 3.1 `ReglasDeReserva` y validaciones de dominio: rangos, horizonte de 1 a 365, zona IANA y teléfono normalizado (verificación: tests unitarios de cada borde)
- [ ] 3.2 `GET` y `PUT /api/v1/consultorio` con MockMvc, JWT y Testcontainers: consulta con defaults, cambio válido, `plan`/`pruebaHasta` ignorados, `VALIDACION`, `TELEFONO_INVALIDO` y `ZONA_HORARIA_INVALIDA` (verificación: escenarios de `configuracion-consultorio`)
- [ ] 3.3 Cambio de slug: libre, reservado, en uso con `sugerencias`, el anterior queda libre y el mismo slug no choca consigo (verificación: escenarios del delta de `slug-consultorio`)
- [ ] 3.4 Auditoría `CONSULTORIO_CONFIG_CAMBIADA` solo con los campos cambiados y sin evento en un guardado idéntico, leyendo `evento_auditoria` de C-03 (verificación: escenarios "Anticipación auditada" y "Guardado idéntico")
- [ ] 3.5 Aislamiento: el body con otro consultorio no lo modifica (verificación: escenario "Body con otro consultorio")

## 4. Prestaciones

- [ ] 4.1 `TipoDePrestacion` en el dominio: duración válida, activar/desactivar y orden por defecto (verificación: tests unitarios)
- [ ] 4.2 Endpoints de prestaciones: alta 201 activa y visible, `DURACION_INVALIDA`, `PRESTACION_DUPLICADA` por mayúsculas, el mismo nombre en otro consultorio, edición, desactivar y activar, `DELETE` → 405 y listado ordenado con y sin inactivas (verificación: escenarios de `prestaciones`)
- [ ] 4.3 Auditoría de los cuatro eventos de prestaciones (verificación: escenario "Alta auditada" y un test por acción)
- [ ] 4.4 Aislamiento: consultar, editar, desactivar y activar una prestación ajena → 404 sin modificarla (verificación: escenario "Desactivar una prestación ajena" y los otros tres)

## 5. Disponibilidad

- [ ] 5.1 `GrillaSemanal` en el dominio: orden, solapamiento con intervalos semiabiertos, franjas contiguas válidas, `dias` en conflicto y `FRANJA_INVALIDA` (verificación: tests unitarios)
- [ ] 5.2 `GET` y `PUT /api/v1/disponibilidad`: grilla vacía, lunes en dos franjas, reemplazo completo, una franja inválida no deja cambios parciales, `FRANJAS_SOLAPADAS` con `dias` (verificación: escenarios de `disponibilidad-semanal`)
- [ ] 5.3 Mapear `ex_disponibilidad_sin_solape` → 400 `FRANJAS_SOLAPADAS` en el manejador (verificación: test con dos guardados concurrentes que se solapan)
- [ ] 5.4 Auditoría `DISPONIBILIDAD_CAMBIADA` con las grillas completas, y sin evento si la grilla es idéntica en otro orden (verificación: escenario "Grilla cambiada")
- [ ] 5.5 Aislamiento: guardar la grilla de A no toca la de B (verificación: escenario "Otro consultorio intacto")

## 6. Seed local

- [ ] 6.1 `db/seed-local/R__demo.sql` y `spring.flyway.locations` en `application-local.yml`; documentar la contraseña de desarrollo en el README local (verificación: `docker compose up --build` con el perfil `local`, login con `demo@consultorio.test`, y `GET /api/v1/prestaciones` devuelve las 4 prestaciones)
- [ ] 6.2 Test de que el seed **no** se carga con el perfil por defecto ni en `test` (verificación: el test de integración no encuentra el slug `demo`)

## 7. Cierre del change

- [ ] 7.1 `./gradlew test` completo en verde, `ArchitectureTest` verde (repositorios con tenant), cobertura ≥ 80 % en `consultorio.domain` y `consultorio.application`, `npm run api:types` regenerado y commiteado (verificación: CI del PR)
- [ ] 7.2 Si Maxi aprueba el tope de horizonte de 365 días (D9), actualizar `knowledge-base/04_modelo_de_datos.md` §Consultorio (verificación: diff de 04 en el PR)
- [ ] 7.3 PR listo con evidencia, revisión de Maxi y aviso a Nacho del contrato para C-09 (verificación: PR con revisor asignado)

## Workflow follow-up

- Maxi marca `[x]` en `CHANGES.md` al mergear.
- Archivar el change con `/opsx:archive` después del merge.
