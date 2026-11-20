# Tasks

> Strict TDD en cada tarea: test en rojo → verde → triangular → refactor dentro del ciclo. Propuesta aprobada por Maxi en #8 (2026-11-20, RN-AU-06) con ajustes, ya incorporados a `design.md` y a las tareas. **No arrancar** hasta que C-02 (#12) esté `[x]`. Verificación base de cada grupo: `./gradlew test` en verde.

## 1. Preparación sobre C-02

- [x] 1.1 Rebasar sobre `main` con C-02 mergeado y revisar la tabla de §Context de `design.md` contra lo que efectivamente se mergeó; actualizar las decisiones afectadas (verificación: la tabla coincide con el código de `compartido`)
- [x] 1.2 Agregar `spring-boot-starter-security` y `spring-boot-starter-oauth2-resource-server`; configurar `JWT_SECRET`, TTLs, `APP_FRONTEND_ORIGIN` y `TERMINOS_VERSION_VIGENTE` con validación al arranque; sumar `JWT_SECRET` a `VariablesObligatorias` (verificación: test de contexto que falla con un secreto ausente, no base64 o de menos de 32 bytes, y arranca con uno válido)
- [ ] 1.3 Reemplazar el `JWT_SECRET` de `.env.example` por un base64 de 48 bytes marcado como solo para desarrollo local (verificación: `docker compose up --build` levanta el backend y el healthcheck queda `healthy`)

## 2. Migración y modelo

- [x] 2.1 Test de integración (Testcontainers PG16) de las tablas `usuario` y `refresh_token`: email `CITEXT` único, 1:1 con consultorio, FK compuesta de `refresh_token` cruzada falla, `app_rw` sin `DELETE`; crear la migración `V2__usuario_y_refresh_token.sql` (verificación: tests verdes)
- [x] 2.2 Actualizar `knowledge-base/04_modelo_de_datos.md` §Usuario con `credenciales_version`, `terminos_version` y `terminos_aceptados_at`, y agregar `DD-23` en `knowledge-base/09_decisiones_y_supuestos.md` por PA-06 (verificación: el diff de 04 coincide con la migración y DD-23 cita PA-06 y LG-04)
- [x] 2.3 Sumar `uk_usuario_email` → 409 `EMAIL_EN_USO` al mapa de constraints de `ManejadorGlobalDeErrores` (verificación: test de dos registros concurrentes con el mismo email)

## 3. Registro

- [x] 3.1 `PoliticaDeContrasena` en el dominio: 9 caracteres falla, 10 pasa, igual al email (con otras mayúsculas) falla (verificación: escenarios de "Política de contraseña")
- [x] 3.2 Caso de uso `RegistrarOdontologo` con consultorio en `PRUEBA`, `prueba_hasta` = hoy en la zona del consultorio + 30 días con `Clock` fijo, BCrypt de costo 12 y términos versionados (verificación: escenarios "Registro exitoso" y "Versión registrada")
- [x] 3.3 Slug único con sufijo y reintento ante violación de `uk_consultorio_slug` (verificación: escenario "Slug ya tomado" y test de dos registros concurrentes con el mismo nombre)
- [x] 3.4 `POST /api/v1/auth/registro` con MockMvc + Testcontainers: 201 sin cookie ni token, 409 `EMAIL_EN_USO` con otras mayúsculas, 400 `PASSWORD_DEBIL`, `TERMINOS_NO_ACEPTADOS`, `VALIDACION` por campo y rollback total ante falla parcial (verificación: todos los escenarios de `registro-odontologo`)

## 4. Sesión

- [x] 4.1 Emisión del access token con Nimbus: claims `sub`, `cid`, `iat`, `exp` (+15 min con el `Clock`) y `ver`, firma HS256 (verificación: test que decodifica el token y verifica los claims)
- [x] 4.2 `POST /api/v1/auth/login`: 200 con `accessToken`/`expiraEn` y cookie con todos sus atributos; 401 `CREDENCIALES_INVALIDAS` idéntico para email inexistente y contraseña incorrecta, con hash ficticio para igualar tiempos (verificación: escenarios "Login exitoso", "Fallo de login genérico" y "Atributos de la cookie")
- [x] 4.3 `RenovarSesion`: rotación con `FOR UPDATE`, detección de reuso que revoca todo, vencido o desconocido → 401 `SESION_EXPIRADA` con la cookie borrada, solo el hash en la base (verificación: escenarios de rotación, reuso y "Refresh solo como hash", más un test de dos refresh concurrentes del mismo token)
- [x] 4.4 `POST /api/v1/auth/logout` con y sin cookie (verificación: escenarios de "Logout")
- [x] 4.5 Filtro de origen en refresh y logout (verificación: escenario "Refresh desde otro sitio" y un test con `Origin: http://localhost:5173` igual a `APP_FRONTEND_ORIGIN`, el caso del proxy de Vite)

## 5. Rutas privadas y tenant

- [x] 5.1 `SecurityConfig` stateless con las rutas públicas de D14 (`/api/v1/auth/*`, `/api/v1/public/**`, `/actuator/health`, `/v3/api-docs/**` y Swagger solo en `local`) y el resto autenticado; entry point en Problem Details 401 `NO_AUTENTICADO` para token ausente, vencido, mal firmado, con `alg` distinto o malformado (verificación: escenarios de "Rutas privadas por defecto", y `SaludDelBackendTest`, `ContratoOpenApiTest` y `SwaggerUiLocalTest` de C-01 en verde **sin modificarlos**)
- [x] 5.2 Establecer el `TenantContext` desde `cid` y validar `ver` contra `credenciales_version` (verificación: escenario "Credenciales cambiadas" y test con el controlador de prueba de C-02: un recurso de otro consultorio con un JWT válido da 404)
- [x] 5.3 Ampliar `repositoriosSiempreConTenant` para eximir métodos puntuales y documentar `UsuarioRepositorio.buscarPorEmail` y `RefreshTokenRepositorio.buscarPorHash` en `REPOSITORIOS_EXCEPTUADOS` (verificación: test con fixtures en `pruebas.arquitectura` donde la excepción de un método no cubre a otro método sin tenant de la misma clase; `ArchitectureTest` verde)
- [x] 5.4 Adaptar los tests de C-02 a la seguridad: `CompartidoAislamientoTest` usa un JWT real en lugar de `FiltroDeTenantDePrueba` (que se elimina) y `ManejadorGlobalDeErroresTest` desactiva los filtros de seguridad (verificación: los mismos escenarios siguen en verde y el 404 entre consultorios se prueba con autenticación real)

## 6. Cabeceras y CORS

- [x] 6.1 Cabeceras CSP, nosniff, Referrer-Policy y HSTS fuera de `local` (verificación: escenarios de "Cabeceras de seguridad" con ambos perfiles)
- [x] 6.2 Verificar Swagger UI con la CSP en el perfil `local`; si se rompe, relajar la CSP solo para `/swagger-ui/**` y solo en `local` (verificación: `/swagger-ui.html` carga sin errores de CSP en la consola del navegador, y el perfil por defecto mantiene `default-src 'self'`)
- [x] 6.3 CORS solo para `APP_FRONTEND_ORIGIN` con credenciales (verificación: escenarios de "CORS restringido al frontend")

## 7. Cierre del change

- [x] 7.1 `./gradlew test` completo en verde, cobertura ≥ 80 % en `auth.domain` y `auth.application`, `npm run api:types` regenerado y commiteado (verificación: CI del PR)
- [x] 7.2 Revisar que ningún log, respuesta o test imprima contraseñas, hashes ni tokens (verificación: búsqueda en los logs de los tests y revisión en el PR)
- [ ] 7.3 Pasar el PR a listo con evidencia, pedir revisión a Maxi (aprobación humana obligatoria en `auth`) y revisión técnica a Mati; avisar a Nacho del contrato para C-07 (verificación: PR con revisores asignados)

## Workflow follow-up

- Maxi marca `[x]` en `CHANGES.md` al mergear.
- Archivar el change con `/opsx:archive` después del merge.
- No desplegar en un entorno público hasta que C-05 esté mergeado.
