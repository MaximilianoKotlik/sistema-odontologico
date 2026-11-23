# Revisión de seguridad de la v1

> C-23 (`e2e-hardening-release`, design D9 y D10). Cada fila dice qué se verifica, con qué evidencia que se vuelve a
> correr (un test, un archivo de configuración o un comando), su estado y la fecha. Estados: **ok** (verificado),
> **arreglado acá** (se corrigió en C-23 con su test), **pendiente** (depende del despliegue o de una decisión; está
> en [checklist-lanzamiento.md](checklist-lanzamiento.md)) e **issue #N**.
>
> Revisado el 2026-11-23 sobre `main` en `4fbff75` más la rama de C-23. Los tests citados corren en el job `Backend`
> del CI (`./gradlew build`), en `Frontend` (`npm test`) y en `E2E (humo)`.

## Cabeceras HTTP

| Qué se verifica | Evidencia | Estado | Fecha |
|-----------------|-----------|--------|-------|
| CSP `default-src 'self'`, `Referrer-Policy: no-referrer`, `X-Content-Type-Options: nosniff` y HSTS fuera de `local` | `CabecerasDeSeguridadTest` (`conElPerfilPorDefectoMandaLasCuatroCabeceras`, `sinHsts` con `local`) | ok | 2026-11-23 |
| Swagger UI solo en `local`, con su propia CSP, y el resto sigue estricto | `CabecerasDeSeguridadTest.swaggerUiTieneUnaCspPropiaYElRestoSigueEstricto`, `springdoc.swagger-ui.enabled: false` en `application.yml` | ok | 2026-11-23 |
| CORS solo para el frontend propio, con credenciales | `CabecerasDeSeguridadTest` (`preflightDesdeElFrontendPropioSePermiteConCredenciales`, `preflightDesdeOtroOrigenNoRecibePermiso`) | ok | 2026-11-23 |
| Respuestas privadas sin caché, también los errores (400, 404 y 403 `PLAN_VENCIDO`) | `CacheControlDeLaApiPrivadaTest` (C-23, tarea 2.1) | ok, sin cambios de código | 2026-11-23 |
| La descarga de la exportación no se guarda en caché | `ExportacionApiTest` (`Cache-Control` con `no-store`) | ok | 2026-11-23 |

## Límites de pedidos y bloqueo de cuentas

| Qué se verifica | Evidencia | Estado | Fecha |
|-----------------|-----------|--------|-------|
| Límites por IP en login, registro, refresh y recuperación, con 429 y `Retry-After` | `LimiteDeSolicitudesTest` | ok | 2026-11-23 |
| Bloqueo de la cuenta por 15 minutos después de 5 intentos fallidos | `BloqueoDeLoginTest` | ok | 2026-11-23 |
| Límites de la API pública: reserva y consulta por IP, modificaciones por token | `LimitesPublicosTest` | ok | 2026-11-23 |
| IP real detrás del reverse proxy: con `none` un `X-Forwarded-For` del cliente se ignora; con `native` cada cliente tiene su cupo y un `X-Forwarded-For` falseado no cambia el cupo | `IpSinProxyConfiguradoTest`, `IpDetrasDelProxyTest`, `server.forward-headers-strategy: ${FORWARD_HEADERS_STRATEGY:none}` en `application.yml` | arreglado acá (C-23, tareas 1.1 y 1.2) | 2026-11-23 |
| En el VPS se configura `FORWARD_HEADERS_STRATEGY=native` y, si el proxy no está en una red privada, `internal-proxies` | `backend/README.md` §IP de origen detrás de un proxy | pendiente (despliegue) | 2026-11-23 |

## Datos personales en los logs

| Qué se verifica | Evidencia | Estado | Fecha |
|-----------------|-----------|--------|-------|
| Una excepción de la base no muestra el email ni en el mensaje ni en el stack trace | `LogsSinPiiTest.unaExcepcionDeLaBaseNoMuestraElEmailNiEnElMensajeNiEnElStackTrace` | ok | 2026-11-23 |
| Si falla el registro de auditoría, el log nombra la acción y la entidad pero no los valores | `LogsSinPiiTest.siFallaElRegistroElLogNombraLaAccionYLaEntidadPeroNoLosValores` | ok | 2026-11-23 |
| El token de gestión no aparece en los logs (vista, cancelación y 404) | `TokenFueraDeLogsTest` | ok | 2026-11-23 |
| Un error en la revisión de un paciente no escribe sus datos en los logs (C-17) | `RevisionDePacientesTest.unErrorEnLaCorreccionNoEscribeLosDatosEnLosLogs` | ok | 2026-11-23 |

## Tokens de gestión: Referer y caché

| Qué se verifica | Evidencia | Estado | Fecha |
|-----------------|-----------|--------|-------|
| El token de `/t/{token}` no viaja en el `Referer` de ningún link saliente | `frontend/src/referer.test.ts` (meta `no-referrer` en `index.html` y `Referrer-Policy` en `nginx.conf` para `/c/` y `/t/`) | ok | 2026-11-23 |
| Las respuestas por token no se guardan en caché | `GestionPorTokenTest`, `LimitesPublicosTest` (`no-store`) | ok | 2026-11-23 |
| El service worker no sirve `/c/` ni `/t/` desde su caché | `frontend/src/pwa/opciones.test.ts` (`navigateFallbackDenylist`) | ok | 2026-11-23 |
| En la base se guarda solo el SHA-256 del token | `token_gestion_turno.token_hash` con `CHECK` de 64 hex (`V7__token_gestion_turno.sql`), `TokenDeGestion.hash()` | ok | 2026-11-23 |

## Secretos

| Qué se verifica | Evidencia | Estado | Fecha |
|-----------------|-----------|--------|-------|
| Los secretos llegan solo por variables de entorno, sin valores por defecto: sin `DB_URL` o `JWT_SECRET` el arranque falla y nombra la variable | `ConfiguracionObligatoriaTest`, `application.yml` | ok | 2026-11-23 |
| La exportación nunca incluye contraseñas ni claves de acceso | `ExportacionApiTest.ningunArchivoContieneSecretos`, `ExportadoresTest.ningunaListaBlancaTieneSecretos` | ok | 2026-11-23 |
| Las contraseñas de demo y el token de demo valen solo en local: el seed no se carga con el perfil por defecto | `SeedLocalTest.elSeedNoSeCargaFueraDelPerfilLocal` | ok | 2026-11-23 |
| `JWT_SECRET`, credenciales de la base y SMTP de producción generados para el VPS y fuera del repositorio | Checklist de lanzamiento §Despliegue | pendiente (despliegue) | 2026-11-23 |

## Aislamiento entre consultorios

| Qué se verifica | Evidencia | Estado | Fecha |
|-----------------|-----------|--------|-------|
| Los 31 endpoints privados responden 404 `RECURSO_NO_ENCONTRADO` con un id de otro consultorio (en la ruta o en el cuerpo), sin cambiar datos ni auditoría de ninguno de los dos; los listados y altas no devuelven datos ajenos | `MatrizDeAislamientoTest.ningunEndpointPrivadoLlegaADatosDeOtroConsultorio` (C-23, tarea 3.2) | ok, ningún endpoint falló | 2026-11-23 |
| Un endpoint privado nuevo que no se sume a la matriz hace fallar el test con su método y su ruta | `MatrizDeAislamientoTest.todoEndpointPrivadoEstaEnElRegistro` | ok | 2026-11-23 |
| La matriz detecta una fuga real | Prueba de mutación manual: sacar el filtro por consultorio del detalle de paciente hizo fallar la matriz nombrando `GET /api/v1/pacientes/{id}` (revertido) | ok | 2026-11-23 |
| Todo repositorio de negocio recibe `ConsultorioId` | `ArchitectureTest` con `ReglasDeArquitectura` | ok | 2026-11-23 |

## Plan vencido

| Qué se verifica | Evidencia | Estado | Fecha |
|-----------------|-----------|--------|-------|
| Toda escritura privada responde 403 `PLAN_VENCIDO` salvo las permitidas, también las de C-17 | `MatrizDeEscriturasTest.todaEscrituraPrivadaResponde403SalvoLasPermitidas` | ok | 2026-11-23 |
| Ninguna ruta HTTP cambia el plan; la URL no se puede evadir con variantes | `MatrizDeEscriturasTest` (`ningunaRutaHttpCambiaElPlan`, `Evasion`) | ok | 2026-11-23 |
| La exportación funciona con el plan vencido | `ExportacionApiTest.funcionaConElPlanVencido`, humo `vencido.spec.ts` | ok | 2026-11-23 |
| Un consultorio vencido no toma turnos online | `ConsultorioPublico.tomaTurnos`, humo `vencido.spec.ts` | ok | 2026-11-23 |

## Página `/seguridad` (D10, RN-PL-06)

Cada afirmación de `frontend/src/contenido/seguridad.json`, en el orden de la página. La página no se publica hasta
que `npm run seguridad:revisar` pase y el checklist esté completo.

| # | Afirmación (resumida) | Evidencia | Estado | Fecha |
|---|-----------------------|-----------|--------|-------|
| 1 | El odontólogo es responsable de los datos de sus pacientes | Ley 25.326; `11_marco_legal_argentino.md` §Roles | ok (declaración de roles, sin verificación técnica) | 2026-11-23 |
| 2 | Tratamos los datos solo por cuenta del consultorio y para la agenda | Sin integraciones de terceros en el código; CSP `default-src 'self'` (`CabecerasDeSeguridadTest`); `docs/legal/politica-de-privacidad.md` | ok | 2026-11-23 |
| 3 | Somos responsables de los datos de cuenta | `11_marco_legal_argentino.md` §Roles; política de privacidad | ok | 2026-11-23 |
| 4 | Lo detallamos en los términos y la política | Rutas `/terminos` y `/privacidad` (C-07); textos en borrador (DD-28) | ok (los textos siguen en borrador: ver #18) | 2026-11-23 |
| 5 | De cada paciente guardamos solo lo necesario | `V6__agenda_paciente_turno_bloqueo.sql` (tabla `paciente`), RN-RP-02 | ok | 2026-11-23 |
| 6 | No guardamos historia clínica, odontograma ni diagnósticos, y tratamos todo como sensible | No existen esas tablas en `db/migration/`; DD-16; frase vieja prohibida en `frontend/src/features/seguridad/fuente.test.ts` | ok | 2026-11-23 |
| 7 | Los datos de pacientes no se escriben en los logs | `LogsSinPiiTest`, `TokenFueraDeLogsTest` | ok | 2026-11-23 |
| 8 | Cada consultorio ve solo lo suyo; lo de otro es "no encontrado" | `MatrizDeAislamientoTest`, `ArchitectureTest` | ok | 2026-11-23 |
| 9 | Cada cambio queda registrado con quién, cuándo, antes y después | `RegistradorDeAuditoriaTest`; eventos de C-08, C-11, C-15, C-17 y C-19 | ok | 2026-11-23 |
| 10 | El registro solo se puede agregar y si falla el cambio no se guarda | `MigracionEventoAuditoriaTest` (`laAplicacionNoPuedeModificarBorrarNiVaciarElRegistro`, `elTriggerFrenaAunAUnRolConTodosLosPrivilegios`), `RegistradorDeAuditoriaTest.siFallaLaAuditoriaElCambioSeRevierte` | ok | 2026-11-23 |
| 11 | Se consulta en "Registro de cambios" | C-18; humo `odontologo.spec.ts` (historial del turno reprogramado) | ok | 2026-11-23 |
| 12 | La descarga trae todo en un ZIP con JSON y CSV | `ExportacionApiTest` | ok | 2026-11-23 |
| 13 | La descarga funciona en cualquier estado y nunca trae secretos | `ExportacionApiTest` (`funcionaConElPlanVencido`, `ningunArchivoContieneSecretos`) | ok | 2026-11-23 |
| 14 | La cuenta se protege con contraseña; 2FA llega después | DD-18, DD-35 | ok | 2026-11-23 |
| 15 | Email para reportar problemas de seguridad | Sigue `(TBD)`: se define con el dominio (DD-34) | **pendiente** (bloquea publicar) | 2026-11-23 |
| 16 | El sistema y la base corren en un VPS en Argentina, con backups en Argentina | Hoy no hay VPS: corre en local (DD-25). Se verifica al contratarlo (DD-40, puntos 1 y 2) | **pendiente** (bloquea publicar: la frase está en presente y todavía no es cierta) | 2026-11-23 |
| 17 | Los proveedores se nombran antes de cargar datos reales; el SMTP fuera del país es transferencia | DD-40, punto 4 | **pendiente** (falta elegir el SMTP) | 2026-11-23 |
| 18 | Los textos legales son un borrador y los va a revisar un abogado | `docs/legal/` marcado `BORRADOR · PENDIENTE DE REVISIÓN LEGAL` (DD-28) | ok (declara lo pendiente) | 2026-11-23 |
| 19 | Hoy hay contraseña, bloqueo por intentos y límite de pedidos | `BloqueoDeLoginTest`, `LimiteDeSolicitudesTest` | ok | 2026-11-23 |
| 20 | No tenemos certificaciones y no decimos lo contrario | Frases prohibidas en `fuente.ts` (`npm test` del frontend) | ok | 2026-11-23 |
| 21 | Hoy corre en desarrollo; el VPS se contrata antes de los datos reales | DD-40 | ok (declara lo pendiente) | 2026-11-23 |
| 22 | La baja se pide por soporte y antes se ofrece la descarga | DD-29; exportación de C-19 | ok | 2026-11-23 |

**Lo que falta antes de publicar `/seguridad`**: las filas 15, 16 y 17. La 16 tiene que cambiar de texto o esperar al
VPS, porque hoy la página la afirma en presente. `npm run seguridad:revisar` ya falla por el `(TBD)` de la fila 15, así
que la página no se puede publicar por error.

## Hallazgos de esta revisión

- **Arreglado acá**: la IP real detrás del proxy (`FORWARD_HEADERS_STRATEGY`). Antes, con un reverse proxy, todos los
  clientes compartían el cupo de los límites por IP.
- **Sin defectos** en el aislamiento (31 endpoints), el `Cache-Control` de la API privada, las cabeceras y los logs.
- **Para el checklist**: la política de privacidad todavía dice `[COMPLETAR: backups definidos (PA-02)]`, aunque
  DD-40 ya fijó la política de backups. Es un texto legal: se completa junto con la revisión de DD-28.
