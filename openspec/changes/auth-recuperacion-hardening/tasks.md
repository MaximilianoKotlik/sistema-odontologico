# Tasks

> Requisitos previos: C-03 y C-04 mergeados en `main` (`[x]`), rebase de `feat/C-05-auth-recuperacion-hardening` y la propuesta aprobada por Maxi (governance CRITICO, RN-AU-06).
> Strict TDD en cada tarea: test que falla → código mínimo → triangular → refactor. Datos ficticios (`@example.com`).

## 0. Alinear con C-02, C-03 y C-04 implementados

- [ ] 0.1 Contrastar la tabla de §Context de `design.md` con el código mergeado (nombres de casos de uso, puertos, columnas de `usuario`, revocación masiva de refresh, rutas públicas de `SecurityConfig`). Actualizar `design.md`/specs si algo cambia y verificar con `openspec validate auth-recuperacion-hardening --strict`
- [ ] 0.2 Fijar el número de la migración con el siguiente `V{n}` libre y verificar que no choque con otras ramas abiertas
- [x] 0.3 Agregar `spring-boot-starter-mail`, `bucket4j_jdk17-core` y `caffeine` a `build.gradle.kts`, y la configuración SMTP (`SMTP_*`, `MAIL_FROM`) y `app.rate-limit.*` a `application.yml` (y valores bajos en `application-test.yml`); verificar que `./gradlew test` sigue en verde

## 1. Bloqueo por fallos y auditoría del login

- [x] 1.1 Tests unitarios de `Usuario`: 5 fallos bloquean 15 min, intentos durante el bloqueo no lo extienden, el éxito reinicia el contador y el bloqueo termina con el `Clock`; implementar las reglas en `auth.domain`; verificar en verde
- [x] 1.2 Test de integración del escenario "Sexto intento con la contraseña correcta" (423 `CUENTA_BLOQUEADA` con minutos restantes, sin token ni cookie); cambiar `IniciarSesion` para devolver `ResultadoLogin` con `SELECT … FOR UPDATE` y mapearlo en `auth.web`; verificar en verde
- [x] 1.3 Tests de integración de "Fin del bloqueo", "Intentos durante el bloqueo no lo extienden" y "Fallos no consecutivos"; verificar en verde
- [x] 1.4 Tests de integración de la auditoría de logins: `LOGIN_FALLIDO` persistido aunque la respuesta sea 401/423, `LOGIN_EXITOSO` sin tokens y ningún evento con email inexistente; publicar los eventos desde `IniciarSesion`; verificar en verde
- [x] 1.5 Test de concurrencia: 5 logins fallidos simultáneos dejan la cuenta bloqueada (no se pierden incrementos); verificar en verde

## 2. Auditoría del registro

- [x] 2.1 Tests de integración de "Registro auditado" (`CUENTA_CREADA` con `{slug, plan, prueba_hasta}` e IP, sin contraseña ni hash) y de "Registro rechazado no deja evento"; publicar el evento desde `RegistrarOdontologo`; verificar en verde

## 3. Recuperación de contraseña

- [x] 3.1 Test de integración de la migración `token_recuperacion`: FK compuesta cruzada falla, `token_hash` único y el rol de la aplicación sin `DELETE` ni `TRUNCATE`; crear `V{n}__token_recuperacion.sql` con los permisos sobre `"${app-user}"`; verificar en verde
- [x] 3.2 Tests unitarios de `TokenDeRecuperacion` (vigente, usado, revocado y vencido a los 30 min con `Clock` fijo); implementar; verificar en verde
- [x] 3.3 Tests de integración de "Email registrado", "Email no registrado" (mismo cuerpo 202, sin envío), "Token guardado como hash" y "Nueva solicitud invalida la anterior", con un `EnviadorDeEmails` fake; implementar `SolicitarRecuperacion`, el listener `AFTER_COMMIT` asíncrono y el endpoint; verificar en verde
- [x] 3.4 Test de "Falla el servidor de correo" (202 igual y log sin token ni email en claro, con el conversor de C-03); verificar en verde
- [x] 3.5 Tests unitarios de `Usuario.establecerContrasena` (sube versión, fija `password_cambiado_at`, limpia el bloqueo); implementar; verificar en verde
- [x] 3.6 Tests de integración de "Restablecimiento exitoso", "Restablecer desbloquea la cuenta", los tres de token inválido y "Contraseña nueva de 9 caracteres" (no consume el token); implementar `ConfirmarRecuperacion` y el endpoint; verificar en verde
- [x] 3.7 Test de integración del evento `PASSWORD_RECUPERADA` sin secretos; verificar en verde
- [x] 3.8 Test del adaptador `EnviadorDeEmailsSmtp` contra un contenedor de Mailpit: llega un email de `MAIL_FROM`, asunto en voseo y link `/recuperar#token=…`; implementar; verificar en verde

## 4. Cambio de contraseña

- [x] 4.1 Tests de integración de "Cambio exitoso" (204, cookie borrada, access y refresh invalidados), "Contraseña actual incorrecta", "Contraseña nueva débil" y "Sin sesión"; implementar `CambiarContrasena`, el endpoint y la excepción en `SecurityConfig`; verificar en verde
- [x] 4.2 Test de que una contraseña actual incorrecta no suma al contador de bloqueo del login; verificar en verde
- [x] 4.3 Test de integración del evento `PASSWORD_CAMBIADA` sin secretos; verificar en verde

## 5. Rate limiting

- [x] 5.1 Tests unitarios del `LimitadorBucket4j` (consume, agota, informa `Retry-After`, se recupera con el tiempo); implementar con Caffeine; verificar en verde
- [ ] 5.2 Tests de integración de "Ráfaga de logins desde una IP" (429 que no suma intento fallido), "Recuperación del límite" y "Rutas fuera de autenticación"; implementar `LimiteDeSolicitudesFilter` con los límites por IP de D8; verificar en verde
- [ ] 5.3 Tests de integración de "Recuperación repetida para un mismo email" (desde IPs distintas, y también para un email sin cuenta) y "Cambio de contraseña repetido"; conectar `LimitadorDeSolicitudes` a los casos de uso; verificar en verde
- [ ] 5.4 Documentar en `backend/README.md` los límites, `app.rate-limit.*` y la configuración de `server.forward-headers-strategy` detrás de un proxy; verificar que lo documentado coincide con `application.yml`

## 6. Integración y cierre

- [ ] 6.1 Agregar `TokenDeRecuperacionRepositorio.buscarPorHash` a la lista de excepciones ArchUnit (con referencia a D10) y verificar `./gradlew test` completo en verde
- [ ] 6.2 Corregir en `knowledge-base/07_flujos_principales.md` `LOGIN_OK` → `LOGIN_EXITOSO` y en `CHANGES.md` la ruta `confirmar-recuperacion` → `recuperacion/confirmar`; verificar con `grep` que no queden las formas viejas
- [ ] 6.3 Regenerar el contrato OpenAPI y `api-types.ts` (`npm run api:types`) con los endpoints y códigos nuevos; verificar que el chequeo de CI del contrato pasa
- [ ] 6.4 Pasar el PR de borrador a listo con la evidencia de los tests; pedir la revisión a Maxi, avisar a Fran (cambios en `IniciarSesion`/`RegistrarOdontologo`) y a Nacho (link con `#token=` para C-07)

## Workflow follow-up

- Maxi marca `[x]` en `CHANGES.md` al mergear.
- Archivar el change (`/opsx:archive`) después de que se archiven C-03 y C-04, para que los deltas se apliquen sobre capabilities ya creadas.
