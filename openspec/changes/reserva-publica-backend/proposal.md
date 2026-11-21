# Proposal

> Change **C-15** de [CHANGES.md](../../../CHANGES.md) · Governance **ALTO** · Depende de `C-08` (`consultorio-config-backend`) y `C-11` (`agenda-casos-de-uso`) · Responsable: Mati · Aprobación: Maxi.
>
> Es trabajo anticipado. Se apoya en C-08 (mergeado en #15) y en la propuesta de C-10 (PR #18, aprobada). C-11 todavía no tiene propuesta. Maxi aprobó esta propuesta en el PR #30, incluido el movimiento de clases de `auth` (RN-AU-06, design D9). El código se escribe recién cuando C-08 y C-11 estén `[x]`.

## Why

El diferenciador central de la v1 es que el paciente reserve, reprograme o cancele su turno solo, sin cuenta y sin llamar (Discovery §5, US-030 a US-032). Hoy el backend no tiene ninguna ruta pública con datos: la página `/c/{slug}` no tiene qué mostrar y el paciente no tiene cómo volver a su turno. C-15 expone esa API pública sobre la agenda de C-10 y C-11, con las defensas que exige una superficie anónima: tokens de 256 bits guardados como hash, 404 uniforme, rate limiting, límite de turnos por paciente y aislamiento por consultorio. Destraba el frontend público (C-16, Octi), la exportación (C-19) y el ciclo de vida del plan (C-20).

## What Changes

- **Migración** `V{n}__token_gestion_turno.sql` (número provisorio): tabla `token_gestion_turno` con `token_hash CHAR(64) UNIQUE` (SHA-256 hex), `turno_id` único, FK compuesta al turno y `revocado_at`. La vigencia se deriva del turno (design D3).
- **Página pública del consultorio**: `GET /api/v1/public/consultorios/{slug}` devuelve nombre, dirección, teléfono, zona horaria, las prestaciones activas y visibles, y `tomaTurnosOnline`. Con la reserva desactivada o el plan `VENCIDO`, responde 200 con `tomaTurnosOnline = false`, el teléfono y sin prestaciones (RN-RP-11).
- **Huecos**: `GET /api/v1/public/consultorios/{slug}/huecos?prestacionId&desde&hasta` (hasta 31 días) devuelve cada fecha del rango con `atiende` y sus huecos ofrecibles (RN-RP-01), usando la `CalculadoraDeHuecos` de C-10.
- **Reserva**: `POST /api/v1/public/consultorios/{slug}/turnos` con nombre, apellido, DNI, teléfono, email opcional y aceptación de la política de privacidad.
  - Normaliza DNI y teléfono, deduplica el paciente por DNI y marca `requiere_revision` si el nombre difiere (RN-RP-03, DD-24).
  - Aplica el límite de 2 turnos futuros activos por link (RN-RP-08).
  - Crea el turno `RESERVADO` con `origen = LINK` mediante el caso de uso de C-11.
  - Emite el token y lo devuelve en claro **una sola vez** (RN-RP-06).
  - Errores: 409 `HUECO_NO_DISPONIBLE` con los huecos actualizados del día, 409 `LIMITE_TURNOS` y 409 `RESERVA_NO_DISPONIBLE`.
  - Si el paciente dejó email y hay SMTP, le llega la confirmación con el link después del commit (DD-26).
- **Gestión por token**:
  - `GET /api/v1/public/turnos/{token}` devuelve el turno, el consultorio, el nombre del paciente, `puedeModificar` y `puedeReprogramar`. Nunca expone la nota interna, el DNI ni el teléfono.
  - `GET /api/v1/public/turnos/{token}/huecos?desde&hasta` devuelve los huecos de la misma prestación.
  - `POST /api/v1/public/turnos/{token}/reprogramacion` y `POST /api/v1/public/turnos/{token}/cancelacion`.
  - Errores: 409 `FUERA_DE_PLAZO` con el teléfono del consultorio (RN-RP-04), 409 `HUECO_NO_DISPONIBLE` (el turno conserva su horario, RN-RP-05) y 404 uniforme ante cualquier token inexistente o de un turno terminado.
  - Un turno cancelado (por el paciente o por el odontólogo) se sigue viendo por su link hasta su fin, con `estado: CANCELADO` y quién canceló, pero sin acciones.
  - Con la reserva desactivada, se puede cancelar pero no reprogramar (criterio explícito de RN-RP-11).
- **Concurrencia**: de dos reservas al mismo hueco prospera exactamente una (RN-RP-07). El límite de turnos y la deduplicación resisten reservas simultáneas del mismo DNI.
- **Seguridad de la superficie pública**:
  - Rate limiting por IP (10 reservas por hora, 60 consultas por minuto) y por token (10 operaciones por hora) (RN-RP-09).
  - Body máximo de 64 KB (413).
  - El token no aparece en logs.
  - Las respuestas por token se marcan `Cache-Control: no-store`.
- **Marca de novedad** `turno.novedad_paciente_at` (DD-30), seteada al reservar, reprogramar y cancelar por link.
- **Auditoría** con `actor_tipo = PACIENTE` en la misma transacción:
  - `TURNO_CREADO`, `TURNO_REPROGRAMADO` y `TURNO_ESTADO_CAMBIADO` (cancelación).
  - `PACIENTE_CREADO` y `PACIENTE_REQUIERE_REVISION`, sin PII (RN-AD-05).
- **Contrato OpenAPI** y `api-types.ts` regenerados para C-16.
- **Tests**: US-030, US-031, US-032, RN-RP-11 (cancelar sí, reservar y reprogramar no), concurrencia, enumeración de tokens, rate limit y aislamiento por tenant en cada endpoint.

**Fuera de alcance**:
- Las pantallas `/c/{slug}` y `/t/{token}` (C-16).
- El archivo `.ics`, que se genera en el cliente (FL-04 paso 6, design de C-16). Hay que corregir la mención en CHANGES.md (ver Impact).
- El email al odontólogo por cambios del paciente (DD-30): necesita una preferencia que hoy no existe en la configuración.
- La revisión de pacientes marcados (C-17) y el pase a `VENCIDO` (C-20).

## Capabilities

### New Capabilities

- `reserva-publica`: página pública del consultorio, huecos ofrecibles y reserva sin cuenta, con deduplicación por DNI, límite de turnos y emisión del token de gestión.
- `gestion-turno-paciente`: acceso del paciente a su turno por token, para ver, reprogramar y cancelar dentro del plazo, con 404 uniforme.

### Modified Capabilities

- `seguridad-http`: se agrega el límite de solicitudes de la API pública (por IP y por token) y el tamaño máximo del cuerpo en `/api/v1/public/**`. Los requisitos existentes no cambian.

## Impact

- **Código**:
  - `backend/.../reserva/**` (web, application, domain e infrastructure), hoy vacío.
  - Migración `V{n}__token_gestion_turno.sql`, con número fijado al rebasar.
  - El puerto de rate limiting de C-05 pasa de `auth.application` a `compartido` para reusarlo (design D9). **Toca el dominio `auth`**: por RN-AU-06, la aprobación de esta propuesta cubre ese movimiento, sin cambios de comportamiento.
  - Filtro de límites y de tamaño de body para `/api/v1/public/**`.
- **API**: 6 endpoints públicos nuevos y `api-types.ts` regenerado.
- **Dependencias**: ninguna nueva (Bucket4j y Caffeine ya están desde C-05).
- **Coordinación**:
  - **C-11 (Fran)**: C-15 necesita casos de uso para crear un turno con `origen = LINK` y para reprogramar y cancelar con actor `PACIENTE`, con la serialización por consultorio que pide C-10. Están listados en design D2 para que su propuesta los incluya.
  - **C-12 (Fran)**: la marca de "nuevo" de DD-30 es `turno.novedad_paciente_at`, que escribe C-15 y limpia C-12 con `/visto` (decisión de Maxi, design D14). La crea la migración del primero que se mergee.
  - **C-16 (Octi)**: los nombres de los campos y el cuerpo de los 409 quedan fijados en los specs. Atiende el punto 4 de la revisión de Maxi al PR #25.
- **Correcciones de documentación al cerrar el change**:
  - CHANGES.md §C-15 dice "dedupe por teléfono", pero DD-24 lo reemplazó por DNI, y la mención del `.ics` debe pasar a C-16.
  - En 07 FL-04, "límite por teléfono" pasa a "límite por paciente (DNI)".
  - En 04 §TokenGestionTurno, `expira_at` cambia según design D3.
- **Preguntas abiertas**: no toca ninguna Alta. PA-02 (país del VPS) no afecta a este change.
- **Changes desbloqueados**: C-16 (con C-06), C-19 (con C-09) y C-20 (con C-09 y C-12).
