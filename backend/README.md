# Backend

API de la agenda (Spring Boot 3, Java 21). El arranque local, los tests y el flujo de trabajo están en el
[README del repositorio](../README.md); las variables de entorno, en
[knowledge-base/08_arquitectura_propuesta.md](../knowledge-base/08_arquitectura_propuesta.md).

## Límite de solicitudes en autenticación

Las rutas de autenticación limitan cuántas solicitudes acepta cada origen y cada cuenta (C-05, design D8). Al
superar un límite, la respuesta es `429 DEMASIADAS_SOLICITUDES` en Problem Details, con la cabecera `Retry-After`
en segundos, y la operación no se ejecuta: un 429 en el login no cuenta como intento fallido de ninguna cuenta.

| Ruta | Clave | Límite por defecto | Propiedad |
|---|---|---|---|
| `POST /api/v1/auth/login` | IP | 10 por minuto | `app.rate-limit.login` |
| `POST /api/v1/auth/registro` | IP | 5 por hora | `app.rate-limit.registro` |
| `POST /api/v1/auth/refresh` | IP | 30 por minuto | `app.rate-limit.refresh` |
| `POST /api/v1/auth/recuperacion` | IP | 5 por hora | `app.rate-limit.recuperacion` |
| `POST /api/v1/auth/recuperacion` | email normalizado | 3 por hora | `app.rate-limit.recuperacion-por-email` |
| `POST /api/v1/auth/recuperacion/confirmar` | IP | 10 por hora | `app.rate-limit.confirmar-recuperacion` |
| `POST /api/v1/auth/cambiar-password` | usuario | 5 cada 15 minutos | `app.rate-limit.cambiar-password` |

- Cada propiedad tiene `capacidad` y `periodo` (formato de duración de Spring: `1m`, `1h`, `15m`). Los valores por
  defecto están en `src/main/resources/application.yml`; se ajustan por configuración, sin cambiar el código:

  ```yaml
  app:
    rate-limit:
      login: { capacidad: 10, periodo: 1m }
  ```

- El cupo se repone de a poco (Bucket4j, recarga gradual): con 10 por minuto vuelve una solicitud cada 6 segundos.
- La clave por email cuenta igual exista o no la cuenta, para que el 429 no revele si el email está registrado.
- Los contadores viven en memoria de cada instancia (Caffeine, como máximo 100.000 claves, y se descartan tras una
  hora sin uso). Con varias instancias, cada una cuenta por separado: el rate limit distribuido queda fuera de v1.
- Estos límites de autenticación solo se aplican a esas rutas. La API pública tiene los suyos (ver abajo).

### Límites de la API pública

`/api/v1/public/**` tiene sus propios cupos (C-15, RN-RP-09), con la misma respuesta `429 DEMASIADAS_SOLICITUDES` y `Retry-After`:

| Operación | Clave | Límite por defecto | Propiedad |
|---|---|---|---|
| `POST /api/v1/public/consultorios/{slug}/turnos` | IP | 10 por hora | `app.rate-limit-publico.reserva` |
| Todos los `GET`, y reprogramar o cancelar | IP | 60 por minuto | `app.rate-limit-publico.consulta` |
| `POST .../turnos/{token}/reprogramacion` y `/cancelacion` | hash del token | 10 por hora | `app.rate-limit-publico.token` |

- Reprogramar y cancelar gastan los dos cupos: el de la IP evita probar tokens por `POST`, y el del token frena las repeticiones desde IPs distintas.
- El cuerpo admite hasta 64 KB. Si lo supera, la respuesta es `413 CUERPO_DEMASIADO_GRANDE`.
- Las respuestas de `/api/v1/public/turnos/**` llevan `Cache-Control: no-store`, y el token se enmascara en logs y Problem Details.

### Política de privacidad vigente

La versión que acepta quien reserva por el link se configura en `app.privacidad.version-vigente` (variable `PRIVACIDAD_VERSION_VIGENTE`, por defecto `2026-11-borrador`, de `docs/legal/politica-de-privacidad.md`). Cuando se publique una versión nueva, hay que actualizar este valor.

### IP de origen detrás de un proxy

La IP es `request.getRemoteAddr()`. Por defecto `server.forward-headers-strategy` queda en `none`, así que nadie
puede falsear su IP mandando un `X-Forwarded-For`. Si el backend corre detrás de un proxy de confianza (por ejemplo,
el reverse proxy del VPS), todas las solicitudes llegan con la IP del proxy y comparten cupo. En ese caso hay que
configurar:

```yaml
server:
  forward-headers-strategy: native   # o framework, según el proxy
```

y asegurarse de que el proxy **reemplace** (no agregue) la cabecera `X-Forwarded-For` con la IP real del cliente.
Sin eso, el límite por IP se puede esquivar. En local, detrás del proxy de Vite, todas las solicitudes comparten IP:
se acepta en desarrollo.

## Cursores del registro de cambios (C-18)

Los cursores de paginación de la auditoría se firman con HMAC usando una clave derivada de `JWT_SECRET`. Si se rota
ese secreto, los cursores en uso dejan de ser válidos y responden `400`. Para seguir, hay que volver a pedir el
registro desde la primera página.

## Email de recuperación

El email sale por SMTP con la configuración `spring.mail.*` (`SMTP_HOST`, `SMTP_PORT`, `SMTP_USER`,
`SMTP_PASSWORD`) y el remitente `MAIL_FROM`; en local, Mailpit (<http://localhost:8025>). Se envía después del
commit y fuera del hilo de la petición, así la respuesta `202` no espera al servidor de correo. Si el envío falla,
queda en el log en `WARN` sin el email ni el token, y el health del backend no depende del SMTP
(`management.health.mail.enabled: false`).
