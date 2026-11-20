# Tasks

> Strict TDD en cada tarea: test en rojo → verde → triangular → refactor dentro del ciclo. **No arrancar** hasta que C-02 esté `[x]` y Maxi apruebe esta propuesta (governance CRITICO, RN-AU-06). Verificación base de cada grupo: `./gradlew test` en verde.

## 1. Preparación sobre C-02

- [ ] 1.1 Rebasar sobre `main` con C-02 mergeado y revisar la tabla de §Context de `design.md` contra lo que efectivamente se mergeó; actualizar las decisiones afectadas (verificación: la tabla coincide con el código de `compartido`)
- [ ] 1.2 Agregar `spring-boot-starter-security` y `spring-boot-starter-oauth2-resource-server`; configurar `JWT_SECRET`, TTLs, `APP_FRONTEND_ORIGIN` y `TERMINOS_VERSION_VIGENTE` con validación al arranque (verificación: test de contexto que falla con un secreto de menos de 32 bytes y arranca con uno válido)

## 2. Migración y modelo

- [ ] 2.1 Test de integración (Testcontainers PG16) de las tablas `usuario` y `refresh_token`: email `CITEXT` único, 1:1 con consultorio, FK compuesta de `refresh_token` cruzada falla, `app_rw` sin `DELETE`; crear la migración `V2__usuario_y_refresh_token.sql` (verificación: tests verdes)
- [ ] 2.2 Actualizar `knowledge-base/04_modelo_de_datos.md` §Usuario con `credenciales_version`, `terminos_version` y `terminos_aceptados_at` (verificación: el diff de 04 coincide con la migración)

## 3. Registro

- [ ] 3.1 `PoliticaDeContrasena` en el dominio: 9 caracteres falla, 10 pasa, igual al email (con otras mayúsculas) falla (verificación: escenarios de "Política de contraseña")
- [ ] 3.2 Caso de uso `RegistrarOdontologo` con consultorio en `PRUEBA`, `prueba_hasta` = hoy en la zona del consultorio + 30 días con `Clock` fijo, BCrypt de costo 12 y términos versionados (verificación: escenarios "Registro exitoso" y "Versión registrada")
- [ ] 3.3 Slug único con sufijo y reintento ante violación de `uk_consultorio_slug` (verificación: escenario "Slug ya tomado" y test de dos registros concurrentes con el mismo nombre)
- [ ] 3.4 `POST /api/v1/auth/registro` con MockMvc + Testcontainers: 201 sin cookie ni token, 409 `EMAIL_EN_USO` con otras mayúsculas, 400 `PASSWORD_DEBIL`, `TERMINOS_NO_ACEPTADOS`, `VALIDACION` por campo y rollback total ante falla parcial (verificación: todos los escenarios de `registro-odontologo`)

## 4. Sesión

- [ ] 4.1 Emisión del access token con Nimbus: claims `sub`, `cid`, `iat`, `exp` (+15 min con el `Clock`) y `ver`, firma HS256 (verificación: test que decodifica el token y verifica los claims)
- [ ] 4.2 `POST /api/v1/auth/login`: 200 con `accessToken`/`expiraEn` y cookie con todos sus atributos; 401 `CREDENCIALES_INVALIDAS` idéntico para email inexistente y contraseña incorrecta, con hash ficticio para igualar tiempos (verificación: escenarios "Login exitoso", "Fallo de login genérico" y "Atributos de la cookie")
- [ ] 4.3 `RenovarSesion`: rotación con `FOR UPDATE`, detección de reuso que revoca todo, vencido o desconocido → 401 `SESION_EXPIRADA` con la cookie borrada, solo el hash en la base (verificación: escenarios de rotación, reuso y "Refresh solo como hash", más un test de dos refresh concurrentes del mismo token)
- [ ] 4.4 `POST /api/v1/auth/logout` con y sin cookie (verificación: escenarios de "Logout")
- [ ] 4.5 Filtro de origen en refresh y logout (verificación: escenario "Refresh desde otro sitio")

## 5. Rutas privadas y tenant

- [ ] 5.1 `SecurityConfig` stateless: rutas públicas explícitas y el resto autenticado; entry point en Problem Details 401 `NO_AUTENTICADO` para token ausente, vencido, mal firmado, con `alg` distinto o malformado (verificación: escenarios de "Rutas privadas por defecto")
- [ ] 5.2 Establecer el `TenantContext` desde `cid` y validar `ver` contra `credenciales_version` (verificación: escenario "Credenciales cambiadas" y test con el controlador de prueba de C-02: un recurso de otro consultorio con un JWT válido da 404)
- [ ] 5.3 Excepciones documentadas de ArchUnit para `buscarPorEmail` y `buscarPorHash` (verificación: `ArchitectureTest` verde y falla si se agrega otro método sin tenant)

## 6. Cabeceras y CORS

- [ ] 6.1 Cabeceras CSP, nosniff, Referrer-Policy y HSTS fuera de `local` (verificación: escenarios de "Cabeceras de seguridad" con ambos perfiles)
- [ ] 6.2 CORS solo para `APP_FRONTEND_ORIGIN` con credenciales (verificación: escenarios de "CORS restringido al frontend")

## 7. Cierre del change

- [ ] 7.1 `./gradlew test` completo en verde, cobertura ≥ 80 % en `auth.domain` y `auth.application`, `npm run api:types` regenerado y commiteado (verificación: CI del PR)
- [ ] 7.2 Revisar que ningún log, respuesta o test imprima contraseñas, hashes ni tokens (verificación: búsqueda en los logs de los tests y revisión en el PR)
- [ ] 7.3 Pasar el PR a listo con evidencia, pedir revisión a Maxi (aprobación humana obligatoria en `auth`) y revisión técnica a Mati; avisar a Nacho del contrato para C-07 (verificación: PR con revisores asignados)

## Workflow follow-up

- Maxi marca `[x]` en `CHANGES.md` al mergear.
- Archivar el change con `/opsx:archive` después del merge.
- No desplegar en un entorno público hasta que C-05 esté mergeado.
