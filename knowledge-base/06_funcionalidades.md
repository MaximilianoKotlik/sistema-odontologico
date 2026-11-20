# Funcionalidades

Organizadas por **épica** y luego por **historia de usuario** (`US-NNN`). Los criterios de aceptación
están en formato **Given / When / Then** (Dado / Cuando / Entonces) para traducirse 1:1 en tests
(Strict TDD, [08 §Estrategia de tests](08_arquitectura_propuesta.md#estrategia-de-tests-strict-tdd)).
Prioridad: **P0** imprescindible para lanzar v1, **P1** deseable en v1.

## Épica 1: Cuenta y consultorio

### US-001 — Registrarme y crear mi consultorio (P0)
**Como** odontólogo **quiero** registrarme con email y contraseña **para** empezar la prueba gratuita sin hablar con nadie.

- [ ] **Dado** un email no registrado, **cuando** envío nombre, apellido, email, contraseña válida, nombre del consultorio y acepto términos, **entonces** se crean usuario y consultorio en plan `PRUEBA` con `prueba_hasta = hoy + 30 días` y un slug sugerido.
- [ ] **Dado** un email ya registrado, **cuando** me registro, **entonces** recibo 409 `EMAIL_EN_USO` y no se crea nada.
- [ ] **Dado** una contraseña de 9 caracteres, **cuando** me registro, **entonces** recibo 400 `PASSWORD_DEBIL`.

**Reglas**: RN-TE-02, RN-TE-03, RN-AU-01, RN-PL-02

### US-002 — Iniciar y cerrar sesión (P0)
**Como** odontólogo **quiero** entrar con mi email y contraseña **para** ver mi agenda desde cualquier dispositivo.

- [ ] **Dado** credenciales válidas, **cuando** hago login, **entonces** recibo un access token de 15 min y una cookie de refresh `HttpOnly`.
- [ ] **Dado** 5 intentos fallidos seguidos, **cuando** intento por sexta vez aun con la contraseña correcta, **entonces** recibo 423 `CUENTA_BLOQUEADA` hasta que pasen 15 min.
- [ ] **Dado** un refresh token ya rotado, **cuando** se reutiliza, **entonces** se revocan todos mis refresh tokens y debo loguearme de nuevo.

**Reglas**: RN-AU-02, RN-AU-03, RN-AD-04

### US-003 — Recuperar mi contraseña (P0)
**Como** odontólogo **quiero** restablecer mi contraseña por email **para** no perder acceso a mi agenda.

- [ ] **Dado** cualquier email (exista o no), **cuando** pido recuperación, **entonces** la respuesta es 202 idéntica; si existe, se envía un email con link válido 30 min.
- [ ] **Dado** un token usado o vencido, **cuando** confirmo una nueva contraseña, **entonces** recibo 400 `TOKEN_INVALIDO`.
- [ ] **Dado** un restablecimiento exitoso, **entonces** se revocan todos mis refresh tokens.

**Reglas**: RN-AU-04, RN-AU-05

### US-004 — Configurar mi consultorio (P0)
**Como** odontólogo **quiero** definir nombre, slug, teléfono de contacto y anticipación mínima **para** que mi link público refleje cómo trabajo.

- [ ] **Dado** un slug libre y válido, **cuando** lo guardo, **entonces** mi página pública responde en `/c/{slug}`.
- [ ] **Dado** un slug reservado (`admin`) o en uso, **cuando** lo guardo, **entonces** recibo 400/409 con el motivo.
- [ ] **Dado** que cambio la anticipación de cancelación a 48 h, **entonces** el cambio queda auditado con valor anterior y nuevo.

**Reglas**: RN-TE-03, RN-RP-04, RN-AD-04

### US-005 — Cambiar mi contraseña con la sesión iniciada (P0)
**Como** odontólogo **quiero** cambiar mi contraseña desde la app **para** proteger mi cuenta si sospecho que alguien la conoce.

- [ ] **Dado** que estoy logueado y envío mi contraseña actual correcta y una nueva válida, **cuando** la cambio, **entonces** se guarda con BCrypt, se revocan todos mis refresh tokens (también el de esta sesión), los access tokens emitidos dejan de valer y debo volver a iniciar sesión.
- [ ] **Dado** una contraseña actual incorrecta, **cuando** intento cambiarla, **entonces** recibo 400 `PASSWORD_ACTUAL_INVALIDA` y nada cambia.
- [ ] **Dado** una contraseña nueva de 9 caracteres o igual a mi email, **entonces** recibo 400 `PASSWORD_DEBIL`.
- [ ] **Dado** un cambio exitoso, **entonces** queda un evento `PASSWORD_CAMBIADA` sin la contraseña ni su hash.

**Reglas**: RN-AU-01, RN-AU-05, RN-AU-06, RN-AD-04, RN-AD-05

## Épica 2: Disponibilidad y prestaciones

### US-010 — Definir tipos de prestación con duración (P0)
**Como** odontólogo **quiero** cargar mis prestaciones con su duración **para** que cada turno ocupe el tiempo real.

- [ ] **Dado** "Limpieza, 45 min", **cuando** la creo, **entonces** queda activa y visible en la página pública.
- [ ] **Dado** una duración de 47 min, **entonces** recibo 400 (múltiplo de 5).
- [ ] **Dado** una prestación con turnos, **cuando** la desactivo, **entonces** deja de ofrecerse pero sus turnos siguen vigentes.

**Reglas**: RN-AG-02, RN-AG-10

### US-011 — Definir mi disponibilidad semanal (P0)
**Como** odontólogo **quiero** cargar mis franjas por día de la semana **para** que solo se reserve cuando atiendo.

- [ ] **Dado** lunes 09:00–13:00 y 15:00–19:00, **cuando** guardo, **entonces** los huecos públicos del lunes solo caen en esas franjas.
- [ ] **Dado** dos franjas solapadas el mismo día, **entonces** recibo 400 `FRANJAS_SOLAPADAS`.

**Reglas**: RN-AG-03, RN-RP-01, RN-TE-05

### US-012 — Bloquear horarios (P0)
**Como** odontólogo **quiero** bloquear un rango (vacaciones, congreso) **para** que nadie reserve en ese período.

- [ ] **Dado** un rango sin turnos activos, **cuando** lo bloqueo, **entonces** desaparece de los huecos públicos y se ve en la agenda.
- [ ] **Dado** un rango con 2 turnos activos, **cuando** lo bloqueo, **entonces** recibo 409 `BLOQUEO_CON_TURNOS` con la lista de esos turnos.

**Reglas**: RN-AG-05, RN-AD-04

## Épica 3: Agenda

### US-020 — Ver mi agenda del día y de la semana (P0)
**Como** odontólogo **quiero** ver mi agenda diaria y semanal en el teléfono **para** saber cuántos pacientes tengo y a qué hora.

- [ ] **Dado** turnos, sobreturnos y bloqueos en la semana, **cuando** abro la vista semanal, **entonces** veo cada uno en su horario local, los sobreturnos con marca distintiva y los cancelados atenuados (ocultables).
- [ ] **Dado** un viewport de 360 px, **cuando** abro la vista diaria, **entonces** se lee sin scroll horizontal.

**Reglas**: RN-AG-04, RN-TE-05

### US-021 — Crear un turno manual (P0)
**Como** odontólogo **quiero** cargar un turno para un paciente que me llamó **para** tener toda la agenda en un solo lugar.

- [ ] **Dado** un hueco libre dentro de la disponibilidad, **cuando** creo el turno, **entonces** queda `RESERVADO`, `origen = MANUAL`, con la duración de la prestación y un evento `TURNO_CREADO`.
- [ ] **Dado** un turno activo 10:00–10:30, **cuando** creo otro 10:15–10:45 sin marcar sobreturno, **entonces** recibo 409 `TURNO_SOLAPADO`.
- [ ] **Dado** un turno 10:00–10:30, **cuando** creo otro 10:30–11:00, **entonces** se crea (intervalo semiabierto).

**Reglas**: RN-AG-01, RN-AG-02, RN-AG-03, RN-AD-01

### US-022 — Agregar un sobreturno marcado (P0)
**Como** odontólogo **quiero** agregar un sobreturno explícito **para** atender una urgencia sin desarmar la agenda.

- [ ] **Dado** un turno 10:00–10:30, **cuando** creo un sobreturno 10:00–10:20, **entonces** se crea con `es_sobreturno = true` y se ve marcado.
- [ ] **Dado** un bloqueo 14:00–18:00, **cuando** creo un sobreturno a las 15:00, **entonces** recibo 409 `HORARIO_BLOQUEADO`.
- [ ] **Dado** un sobreturno fuera de la disponibilidad, **entonces** se crea igual.

**Reglas**: RN-AG-01, RN-AG-04

### US-023 — Cambiar estado y reprogramar un turno (P0)
**Como** odontólogo **quiero** confirmar, cancelar, marcar ausente o mover un turno **para** que la agenda refleje lo que pasó.

- [ ] **Dado** un turno `RESERVADO`, **cuando** lo confirmo, **entonces** pasa a `CONFIRMADO` y se audita con estado anterior.
- [ ] **Dado** un turno futuro, **cuando** lo marco `AUSENTE`, **entonces** recibo 409 `TRANSICION_INVALIDA`.
- [ ] **Dado** un turno `CANCELADO`, **cuando** intento confirmarlo, **entonces** recibo 409.
- [ ] **Dado** un turno a 2 h de empezar, **cuando** lo reprogramo como odontólogo a un hueco libre, **entonces** se mueve (sin anticipación mínima) y se conserva el token de gestión.

**Reglas**: RN-AG-06, RN-AG-07, RN-AG-11, RN-AD-01

### US-024 — Ajustar la duración de un turno (P0)
**Como** odontólogo **quiero** alargar o acortar un turno ya cargado **para** que la agenda refleje el tiempo real de la atención.

- [ ] **Dado** un turno 10:00–10:30 y libre hasta las 11:00, **cuando** ajusto la duración a 45 min, **entonces** `fin` pasa a 10:45 (se deriva de `inicio + duracion_min`) y se registra `TURNO_DURACION_CAMBIADA` con valor anterior y nuevo.
- [ ] **Dado** una duración de 47 min, **entonces** recibo 400 (múltiplo de 5).
- [ ] **Dado** otro turno activo 10:30–11:00, **cuando** ajusto a 45 min un turno que no es sobreturno, **entonces** recibo 409 `TURNO_SOLAPADO` y la duración no cambia.
- [ ] **Dado** un bloqueo desde las 10:40, **cuando** ajusto a 45 min, **entonces** recibo 409 `HORARIO_BLOQUEADO`.

**Reglas**: RN-AG-01, RN-AG-02, RN-AG-03, RN-AG-04, RN-AG-08, RN-AG-12, RN-AD-01

### US-025 — Agregar o editar la nota interna de un turno (P1)
**Como** odontólogo **quiero** anotar algo breve en un turno (p. ej. "trae radiografía") **para** tenerlo a mano al atender.

- [ ] **Dado** un turno, **cuando** guardo una nota de hasta 500 caracteres, **entonces** queda en `nota_interna` y se registra `TURNO_NOTA_CAMBIADA` con valor anterior y nuevo.
- [ ] **Dado** una nota de 501 caracteres, **entonces** recibo 400 y la nota no cambia.
- [ ] **Dado** un paciente que abre su turno por `/t/{token}`, **entonces** no ve la nota interna.

**Reglas**: RN-AD-01, RN-AD-05, RN-GL-02

## Épica 4: Reserva pública

### US-030 — Reservar un turno por link sin cuenta (P0)
**Como** paciente **quiero** reservar desde el link del consultorio **para** no tener que llamar ni escribir por WhatsApp.

- [ ] **Dado** una prestación de 30 min y un hueco libre, **cuando** envío nombre, apellido, teléfono y acepto la privacidad, **entonces** se crea un turno `RESERVADO` con `origen = LINK` y veo una confirmación con el link `/t/{token}` para guardar.
- [ ] **Dado** un hueco a menos de 2 h, **entonces** no se ofrece y reservarlo por API responde 409 `HUECO_NO_DISPONIBLE`.
- [ ] **Dado** dos reservas concurrentes al mismo hueco, **entonces** exactamente una prospera.
- [ ] **Dado** un teléfono `011 15-1234-5678` ya registrado como `+5491112345678`, **entonces** se reutiliza el paciente existente.
- [ ] **Dado** un teléfono con 2 turnos futuros activos, **cuando** reserva un tercero, **entonces** recibo 409 `LIMITE_TURNOS`.

**Reglas**: RN-RP-01, RN-RP-02, RN-RP-03, RN-RP-06, RN-RP-07, RN-RP-08, RN-RP-10

### US-031 — Reprogramar mi turno por link (P0)
**Como** paciente **quiero** mover mi turno **para** no perderlo si me surge un imprevisto.

- [ ] **Dado** un turno dentro de 3 días y anticipación de 24 h, **cuando** elijo un nuevo hueco libre, **entonces** el turno cambia de horario y se audita con `actor_tipo = PACIENTE`.
- [ ] **Dado** un turno dentro de 20 h y anticipación de 24 h, **entonces** recibo 409 `FUERA_DE_PLAZO` y veo el teléfono del consultorio.

**Reglas**: RN-RP-04, RN-RP-05, RN-AD-01

### US-032 — Cancelar mi turno por link (P0)
**Como** paciente **quiero** cancelar con anticipación **para** liberar el horario.

- [ ] **Dado** un turno dentro del plazo, **cuando** cancelo, **entonces** pasa a `CANCELADO` con `cancelado_por = PACIENTE`, el token se revoca y el hueco vuelve a ofrecerse.
- [ ] **Dado** un token inexistente o revocado, **entonces** recibo 404 sin detalles.

**Reglas**: RN-RP-04, RN-RP-06, RN-AG-09

## Épica 5: Auditoría

### US-040 — Consultar el historial de cambios (P0)
**Como** odontólogo **quiero** ver quién cambió qué y cuándo en un turno **para** resolver dudas con un paciente.

- [ ] **Dado** un turno reservado por link y luego reprogramado por mí, **cuando** abro su historial, **entonces** veo 2 eventos en orden con actor, fecha, valor anterior y nuevo.
- [ ] **Dado** cualquier evento, **cuando** se intenta un `UPDATE` o `DELETE` sobre él en la base, **entonces** la operación falla.
- [ ] **Dado** eventos de distintos turnos, fechas y acciones, **cuando** filtro el registro por turno, por rango de fechas y por tipo de acción (solos o combinados), **entonces** solo veo los eventos que cumplen todos los filtros, en orden cronológico.

**Reglas**: RN-AD-01, RN-AD-02, RN-AD-06

## Épica 6: Exportación

### US-050 — Exportar todos mis datos (P0)
**Como** odontólogo **quiero** descargar todos mis datos en formato abierto **para** no quedar atado al sistema.

- [ ] **Dado** un consultorio con datos, **cuando** exporto, **entonces** descargo un ZIP con JSON y CSV por entidad y un `manifest.json` cuyas cantidades coinciden con la base.
- [ ] **Dado** el ZIP generado, **entonces** incluye un PDF legible con datos del consultorio, prestaciones, pacientes, listado de turnos y resumen de auditoría, sin hashes ni tokens.
- [ ] **Dado** el ZIP generado, **entonces** no contiene ningún hash ni token.
- [ ] **Dado** un consultorio en plan `VENCIDO`, **cuando** exporto, **entonces** funciona igual.
- [ ] **Dado** una exportación hace 2 min, **cuando** pido otra, **entonces** recibo 429.

**Reglas**: RN-EX-01..05

## Épica 7: Precios y plan

### US-060 — Ver precios públicos en ARS (P0)
**Como** visitante **quiero** ver los precios sin registrarme **para** decidir con información completa.

- [ ] **Dado** que no estoy logueado, **cuando** entro a `/precios`, **entonces** veo planes en ARS, qué incluye cada uno, la prueba gratuita (sin tarjeta) y la fecha de actualización.

**Reglas**: RN-PL-01

### US-061 — Prueba gratuita y vencimiento (P1)
**Como** odontólogo **quiero** saber cuántos días de prueba me quedan **para** decidir a tiempo.

- [ ] **Dado** un consultorio en `PRUEBA` con 5 días restantes, **entonces** la app muestra un aviso con los días.
- [ ] **Dado** `prueba_hasta` vencida, **cuando** corre la tarea diaria, **entonces** el plan pasa a `VENCIDO`, la agenda queda en solo lectura y la página pública deja de tomar reservas.

**Reglas**: RN-PL-02, RN-PL-03, RN-RP-11

### US-062 — Ver la página pública de seguridad y cumplimiento (P1)
**Como** visitante u odontólogo **quiero** leer cómo se protegen y tratan mis datos, qué garantías hay y qué todavía falta **para** confiar antes de cargar información de mis pacientes.

- [ ] **Dado** que no estoy logueado, **cuando** entro a `/seguridad`, **entonces** veo cómo se tratan los datos, el registro de auditoría inmutable, la exportación completa, el hosting/tratamiento y la fecha de última actualización.
- [ ] **Dado** la página, **entonces** declara explícitamente lo que aún no existe (sin 2FA en v1, sin revisión legal profesional todavía) y no afirma cumplimiento ni certificaciones no respaldadas.
- [ ] **Dado** cada afirmación de la página, **entonces** corresponde a una capacidad implementada (RN-PL-06) y el contenido sale de una fuente editable.

**Reglas**: RN-PL-05, RN-PL-06, RN-AD-02, RN-EX-01

## Épica 8: Pacientes

### US-070 — Revisar pacientes marcados por la deduplicación (P1)
**Como** odontólogo **quiero** ver los pacientes cuyos datos no coincidieron al reservar **para** confirmarlos o corregirlos.

- [ ] **Dado** un teléfono ya registrado como "Ana Pérez", **cuando** reserva por link "Ana Peres" con ese teléfono, **entonces** se reutiliza el paciente sin sobrescribir sus datos, queda con `requiere_revision = true` y aparece al filtrar "requiere revisión".
- [ ] **Dado** un paciente marcado, **cuando** confirmo sus datos o corrijo nombre, apellido o email, **entonces** `requiere_revision` pasa a `false` y se registra `PACIENTE_REVISADO` solo con los campos modificados.
- [ ] **Dado** un paciente de otro consultorio, **cuando** intento revisarlo, **entonces** recibo 404.

**Reglas**: RN-RP-03, RN-AD-05, RN-TE-04, RN-GL-01

## Trazabilidad con el Discovery y el mercado

| Funcionalidad necesaria (Discovery §5) | Historias | Vacío o referente del informe |
|----------------------------------------|-----------|-------------------------------|
| Agenda diaria y semanal de un único profesional | US-020 | Agenda multiprofesional/por sillón queda fuera ([RN-AG-13](05_reglas_de_negocio.md)); AgendaPro la documenta |
| Bloqueos y sobreturnos marcados | US-012, US-022 | Solo DentalSoft documenta sobreturnos con solapamiento |
| Duración por prestación y prevención de solapamientos | US-010, US-021 | Combina lo repartido entre DentalSoft y AgendaPro (menos sillones) |
| Reserva pública sin cuenta: reservar, reprogramar, cancelar | US-030 a US-032 | Referente Turnito; la autogestión del paciente es un vacío (C.3 n.º 6) |
| Auditoría de cambios sobre turnos | US-040 | Referente MedicAI; vacío C.3 n.º 7 |
| Exportación completa en formato abierto | US-050 | Solo MedicAI, YoFacturo (Premium) y DentalSoft exportan |
| Precios públicos en ARS con prueba gratuita | US-060, US-061 | Solo DentalSoft publica todo entre los dentales argentinos |
| D.3 diferenciador: página pública de seguridad y cumplimiento (Leyes 25.326/26.529) con auditoría inmutable | US-062, US-040 | NE-13: solo AgendaPro y MedicAI lo mencionan, como afirmación comercial; acá se declara con honestidad, sin certificaciones |
| D.3: exportación completa (CSV/PDF) | US-050 | Ampliada con PDF legible (RN-EX-05) |
| D.3: prueba sin tarjeta | US-060, US-061 | RN-PL-02: sin medio de pago |

> Brecha conocida: recordatorios (92 % del mercado) y WhatsApp (87 % del lote AR + LATAM) son estándar y **no** están en
> v1 por decisión del Discovery ([DD-16](09_decisiones_y_supuestos.md), [SU-02](09_decisiones_y_supuestos.md)).

## Backlog post-v1 (no implementar)

Los imprescindibles del informe que la v1 no cubre están en [D.3 Etapas posteriores, primera etapa posterior a la v1](../docs/discovery/informe-discovery.md) (orden sugerido: WhatsApp, HC/odontograma, presupuestos, caja/MP, obras sociales, multiprofesional/sillón y roles); el mercado los considera estándar (SU-02).

Odontograma y presupuestos (primer incremento), WhatsApp (incluido el botón de compartir el link por WhatsApp, DD-18), 2FA del odontólogo (PA-13, DD-18), caja/Mercado Pago, obras sociales, ARCA,
lista de espera/recall, multi-profesional/roles, periodontograma, Google Calendar. Ver
[01 §Fuera de alcance](01_vision_y_objetivos.md#fuera-de-alcance).
