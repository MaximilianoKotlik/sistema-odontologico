# Reglas de Negocio

Cada regla tiene un código único `RN-{DOMINIO}-{NN}` para trazabilidad con historias
([06](06_funcionalidades.md)), flujos ([07](07_flujos_principales.md)) y tests. Las cuatro reglas del
Discovery ([`discovery.md` §7](../discovery/discovery.md)) están marcadas con **[D]**.

Valores por defecto configurables por consultorio se indican como `(config, default X)`.

## Dominio: Tenant / Consultorio (RN-TE)

- **RN-TE-01**: Todo dato de negocio pertenece a exactamente un consultorio. Las operaciones privadas usan el `consultorio_id` del JWT; las públicas lo resuelven por `slug` o por token de gestión. Nunca se acepta desde el body o la query. — Evita fuga de datos entre tenants.
- **RN-TE-02**: Un consultorio tiene exactamente un usuario y un usuario pertenece a exactamente un consultorio (v1). El email es único en todo el sistema.
- **RN-TE-03**: El `slug` del link público es único global, de 3 a 40 caracteres `[a-z0-9-]`, sin guiones al inicio/fin ni dobles, y no puede ser una palabra reservada (`app`, `api`, `login`, `registro`, `precios`, `admin`, `c`, `t`, etc.). Se sugiere a partir del nombre del consultorio y es editable; al cambiarlo, el link anterior deja de funcionar.
- **RN-TE-04**: Pedir un recurso de otro consultorio responde **404**, no 403 — no se revela su existencia.
- **RN-TE-05**: La zona horaria del consultorio (config, default `America/Argentina/Buenos_Aires`) gobierna la interpretación de la disponibilidad semanal y la visualización; los instantes se persisten en UTC.

## Dominio: Autenticación (RN-AU) — governance **CRITICAL**

- **RN-AU-01**: Contraseña de al menos 10 caracteres, que no sea igual al email; se guarda con BCrypt (costo 12).
- **RN-AU-02**: Access token JWT de 15 minutos; refresh token opaco de 14 días en cookie `HttpOnly; Secure; SameSite=Strict`, rotado en cada uso. Reutilizar un refresh ya rotado revoca toda la familia de tokens del usuario.
- **RN-AU-03**: Tras 5 intentos fallidos consecutivos la cuenta se bloquea 15 minutos. El mensaje de error es genérico ("email o contraseña incorrectos").
- **RN-AU-04**: La recuperación de contraseña emite un token de un solo uso, válido 30 minutos, guardado como hash. La respuesta a la solicitud es siempre la misma exista o no el email.
- **RN-AU-05**: Cambiar o restablecer la contraseña revoca todos los refresh tokens vigentes.
- **RN-AU-06**: Cualquier cambio de código, configuración o dependencia en el dominio `auth` requiere aprobación humana explícita antes de escribirse.

## Dominio: Agenda (RN-AG)

- **RN-AG-01** **[D]**: Un turno no puede solaparse con otro turno activo (`RESERVADO` o `CONFIRMADO`) del mismo profesional, salvo que se marque explícitamente como **sobreturno**. Se valida en el servicio y se garantiza con una restricción de exclusión en PostgreSQL ([04](04_modelo_de_datos.md)). Intervalos semiabiertos `[inicio, fin)`: un turno que termina 10:30 no solapa con uno que empieza 10:30.
- **RN-AG-02**: La duración de un turno se toma de su tipo de prestación al crearlo (copia, no referencia). El odontólogo puede ajustarla en múltiplos de 5 minutos. Cambiar la duración de una prestación no altera turnos existentes.
- **RN-AG-03**: Un turno no-sobreturno debe caer completo dentro de una franja de disponibilidad y no intersectar ningún bloqueo.
- **RN-AG-04**: Un sobreturno solo lo crea el odontólogo, puede solaparse con otros turnos y puede quedar fuera de la disponibilidad, pero **no** puede caer dentro de un bloqueo. Se muestra siempre con marca visual distintiva y nunca se ofrece en la página pública.
- **RN-AG-05**: No se puede crear un bloqueo que intersecte turnos activos. El sistema rechaza la operación y lista los turnos en conflicto para que el odontólogo los cancele o reprograme primero.
- **RN-AG-06**: Máquina de estados del turno:
  - `RESERVADO → CONFIRMADO` (odontólogo).
  - `RESERVADO | CONFIRMADO → CANCELADO` (odontólogo en cualquier momento; paciente según RN-RP-04).
  - `RESERVADO | CONFIRMADO → AUSENTE` (solo odontólogo y solo cuando `inicio` ya pasó).
  - `CANCELADO` y `AUSENTE` son terminales. Cualquier otra transición responde 409 `TRANSICION_INVALIDA`.
- **RN-AG-07**: Los turnos nunca se borran físicamente: se cancelan. Lo mismo vale para pacientes y prestaciones (se desactivan).
- **RN-AG-08**: Los horarios de turnos y bloqueos se expresan en múltiplos de 5 minutos.
- **RN-AG-09**: Un turno cancelado libera su horario de inmediato para nuevas reservas.
- **RN-AG-10**: Una prestación desactivada deja de ofrecerse, pero sus turnos existentes siguen vigentes y visibles.
- **RN-AG-11**: La reprogramación hecha por el odontólogo mantiene el mismo turno (mismo `id` y mismo token de gestión), valida RN-AG-01/03/04 sobre el nuevo horario y no está sujeta a anticipación mínima.

## Dominio: Reserva pública (RN-RP)

- **RN-RP-01**: La página pública ofrece solo prestaciones activas y visibles. Un hueco es ofrecible si: cae dentro de la disponibilidad, no intersecta bloqueos ni turnos activos (los sobreturnos **sí** ocupan el hueco para el público), empieza en un múltiplo de `paso_huecos_min` (config, default 15) desde el inicio de la franja, y está entre `ahora + anticipacion_reserva_min` (config, default 2 h) y `ahora + horizonte_reserva_dias` (config, default 60 días).
- **RN-RP-02**: Datos mínimos obligatorios para reservar: nombre, apellido, teléfono celular argentino y aceptación de la política de privacidad (se guarda fecha y versión). Email opcional. No se pide DNI ni datos de salud.
- **RN-RP-03**: Deduplicación de pacientes por defecto: el teléfono se normaliza a E.164 (`+549...`) y es la clave dentro del consultorio. Si ya existe, se reutiliza el paciente sin sobrescribir sus datos; si nombre/apellido difieren, se marca `requiere_revision` para que lo revise el odontólogo. **Suposición** a confirmar en [PA-01](10_preguntas_abiertas.md).
- **RN-RP-04** **[D]**: El paciente solo puede cancelar o reprogramar por link si faltan al menos `anticipacion_cancelacion_min` minutos (config, default 24 h) para el inicio del turno. Fuera de ese plazo, la página muestra el teléfono de contacto del consultorio.
- **RN-RP-05**: Reprogramar por link mueve el mismo turno a un nuevo hueco ofrecible (RN-RP-01) en una única transacción; si el nuevo hueco ya no está libre, el turno conserva su horario original.
- **RN-RP-06**: Al reservar se emite un token de gestión de 256 bits (base64url) que se muestra **una sola vez** junto con el link `/t/{token}`, para que el paciente lo guarde. Se persiste solo su hash SHA-256. Vence al terminar el turno y se revoca al cancelarlo.
- **RN-RP-07**: Si dos pacientes intentan el mismo hueco a la vez, solo una reserva prospera; la otra recibe 409 `HUECO_NO_DISPONIBLE` con los huecos actualizados.
- **RN-RP-08**: Un mismo teléfono puede tener como máximo 2 turnos futuros activos por consultorio vía link. — Anti-abuso. **Suposición**.
- **RN-RP-09**: Endpoints públicos con rate limit por IP (default: 10 reservas/hora, 60 consultas de huecos/minuto) y por token (10 operaciones/hora).
- **RN-RP-10**: Un turno reservado por link nace en estado `RESERVADO` con `origen = LINK`; el odontólogo lo confirma desde la agenda.
- **RN-RP-11**: Si el consultorio desactiva la reserva pública o su plan está `VENCIDO`, la página pública informa que no toma turnos online y muestra el teléfono de contacto. Los links de gestión de turnos existentes siguen permitiendo cancelar.

## Dominio: Auditoría (RN-AD)

- **RN-AD-01** **[D]**: Todo cambio sobre un turno (alta, reprogramación, cambio de estado, cambio de duración o nota) queda registrado con autor (`actor_tipo` + `actor_id`), fecha, valor anterior y valor nuevo.
- **RN-AD-02**: El registro es append-only: no se edita ni se borra, ni siquiera por el odontólogo. Se garantiza por trigger y por permisos de base.
- **RN-AD-03**: El evento se escribe en la misma transacción que el cambio; si la auditoría falla, el cambio se revierte.
- **RN-AD-04**: También se auditan: cambios de configuración del consultorio, disponibilidad y prestaciones; altas y bajas de bloqueos; exportaciones; login exitoso y fallido; cambio y recuperación de contraseña.
- **RN-AD-05**: Los eventos guardan solo lo necesario: nunca contraseñas, hashes ni tokens; datos de paciente solo como `paciente_id` y los campos efectivamente modificados.
- **RN-AD-06**: El odontólogo puede consultar el registro filtrando por turno, rango de fechas y tipo de acción.

## Dominio: Exportación (RN-EX) — governance **HIGH**

- **RN-EX-01**: La exportación es completa: consultorio, prestaciones, disponibilidad, bloqueos, pacientes, turnos y auditoría. Se entrega como ZIP con un JSON por entidad, un CSV por entidad (UTF-8, separador `,`, encabezado) y un `manifest.json` con versión de esquema, fecha de generación y cantidad de registros.
- **RN-EX-02**: Nunca incluye secretos (hash de contraseña, refresh tokens, tokens de recuperación o de gestión).
- **RN-EX-03**: Está disponible en cualquier estado del plan, incluido `VENCIDO`. — Pilar de transparencia: el odontólogo nunca queda rehén.
- **RN-EX-04**: Cada exportación genera un evento `EXPORTACION_GENERADA`. Límite: 1 exportación cada 5 minutos por consultorio.

## Dominio: Plan y precios (RN-PL)

- **RN-PL-01**: Los planes y precios se publican en ARS en `/precios`, accesibles sin registrarse, con impuestos incluidos y la fecha de última actualización visible.
- **RN-PL-02**: Todo consultorio nuevo arranca en plan `PRUEBA` por 30 días sin pedir medio de pago. **Suposición** sobre la duración ([PA-03](10_preguntas_abiertas.md)).
- **RN-PL-03**: Vencida la prueba sin plan activo, el consultorio pasa a `VENCIDO`: agenda en solo lectura, reserva pública desactivada (RN-RP-11), exportación habilitada (RN-EX-03). Ningún dato se borra.
- **RN-PL-04**: En v1 no hay cobro integrado; el pase a `ACTIVO` se hace manualmente por el equipo (operación interna auditada con `actor_tipo = SISTEMA`).

## Dominio: Historia clínica (RN-HC) — vigente desde v2

- **RN-HC-01** **[D]**: Las evoluciones y el odontograma no se borran ni se editan en el lugar: se versionan con autor y fecha, en línea con la Ley 26.529 ([11](11_marco_legal_argentino.md)). Se documenta ahora para que el diseño de auditoría y de tenant de v1 no lo impida.

## Dominio: Excepciones globales (RN-GL)

- **RN-GL-01**: Los datos de pacientes (nombre, teléfono, email) no se escriben en logs de aplicación; se enmascaran.
- **RN-GL-02**: Toda modificación concurrente sobre un turno usa optimistic locking (`version`); un conflicto responde 409 `CONFLICTO_CONCURRENCIA`.
- **RN-GL-03**: Los errores se devuelven en formato RFC 7807 con un `codigo` estable que el frontend traduce a mensajes en español.
- **RN-GL-04**: Ninguna regla de negocio se implementa solo en el frontend; el backend es la fuente de verdad y el frontend solo anticipa validaciones para UX.
