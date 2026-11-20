# Decisiones y Supuestos

> DD-01 a DD-08 fueron **confirmadas por el usuario** el 2026-11-16 y DD-16 y DD-18 el 2026-11-20. El resto
> (DD-09 a DD-15 y DD-17) son decisiones por defecto propuestas al armar la KB: se aplican salvo que el usuario
> las revise. Las decisiones de diseño derivadas de las reglas (RN-AG-03/04/05/08/13, valores por defecto) están
> registradas en el Discovery ([`discovery.md` §7](../discovery/discovery.md)), con su procedencia.

## Decisiones documentadas

### DD-01 — Backend en Java 21 + Spring Boot + Gradle + PostgreSQL (confirmada)
**Decisión**: monolito modular Spring Boot 3 sobre Java 21, build con Gradle, PostgreSQL 16, OpenAPI con springdoc.
**Contexto**: hay que elegir un stack mantenible por un equipo chico para un SaaS con datos sensibles.
**Alternativas consideradas**: Node/NestJS, Python/Django, BaaS (Supabase).
**Justificación**: ecosistema maduro para seguridad (Spring Security), transacciones y testing (Testcontainers); PostgreSQL permite expresar invariantes de agenda en la base (exclusión GiST).
**Trade-offs aceptados**: más ceremonia y mayor consumo de memoria que alternativas livianas; arranque más lento.

### DD-02 — Frontend en TypeScript vanilla + Vite + Tailwind (confirmada)
**Decisión**: SPA sin framework, con router, cliente HTTP y store propios mínimos en `shared/`.
**Contexto**: superficie chica (≈10 pantallas) y prioridad mobile.
**Alternativas consideradas**: React, Vue, Svelte, server-side rendering con Thymeleaf.
**Justificación**: bundle liviano, cero dependencia de ciclo de vida de un framework, control total.
**Trade-offs aceptados**: hay que escribir y testear infraestructura de UI (router, render, estado) que un framework daría hecha; riesgo de crecer desordenado → se mitiga con feature folders y tests Vitest.

### DD-03 — Sin Supabase ni otro BaaS (confirmada)
**Decisión**: autenticación, datos y API propios.
**Contexto**: el usuario descartó Supabase explícitamente para "no mezclar cosas".
**Alternativas consideradas**: Supabase Auth + Postgres gestionado; Firebase.
**Justificación**: una sola fuente de verdad (Spring + Postgres), sin dependencia de un proveedor para auth ni RLS ajena.
**Trade-offs aceptados**: hay que construir y mantener auth (dominio CRITICAL) y operar la base.

### DD-04 — Local-first con Docker Compose; hosting cloud sin decidir (confirmada)
**Decisión**: todo el sistema corre localmente con `docker compose up` (postgres + backend + frontend + mailpit).
**Contexto**: presupuesto acotado y foco en construir antes de operar.
**Alternativas consideradas**: desplegar desde el día uno en un PaaS.
**Justificación**: costo cero en desarrollo, entorno reproducible, postergar la decisión de residencia de datos hasta tener asesoría ([PA-02](10_preguntas_abiertas.md)).
**Trade-offs aceptados**: sin URL pública no hay piloto real con pacientes; la decisión de hosting bloquea el lanzamiento.

### DD-05 — Migraciones con Flyway en SQL plano
**Decisión**: Flyway con migraciones versionadas SQL; Hibernate en modo `validate`.
**Contexto**: el modelo depende de constraints que JPA no expresa (exclusión GiST, triggers, FKs compuestas).
**Alternativas consideradas**: Liquibase (XML/YAML), `ddl-auto=update`.
**Justificación**: SQL legible y revisable, integración nativa con Spring Boot, mismo esquema en tests (Testcontainers) y en local.
**Trade-offs aceptados**: rollback manual (migraciones solo hacia adelante).

### DD-06 — JWT propio con Spring Security (confirmada; dominio CRITICAL)
**Decisión**: access JWT corto + refresh opaco rotado en cookie `HttpOnly`; registro, login y recuperación propios.
**Contexto**: sin BaaS, la SPA necesita autenticación stateless.
**Alternativas consideradas**: sesiones con cookie de servidor; Keycloak; OAuth social.
**Justificación**: stateless, simple de operar con una instancia o varias; el refresh opaco permite revocación.
**Trade-offs aceptados**: responsabilidad total sobre seguridad; **todo cambio en `auth` requiere aprobación humana explícita** (governance CRITICAL). Sin 2FA en v1 (backlog).

### DD-07 — Sin integraciones externas en v1 (confirmada)
**Decisión**: ni WhatsApp, ni Mercado Pago, ni ARCA, ni Google Calendar.
**Contexto**: cada integración agrega costo variable, certificados y riesgo.
**Justificación**: validar primero el núcleo (agenda + link).
**Trade-offs aceptados**: sin recordatorios automáticos el ausentismo puede no bajar ([SU-02](#su-02--la-agenda-sola-aporta-valor)); el cobro de la suscripción es manual.

### DD-08 — Un tenant = un consultorio = un odontólogo (confirmada)
**Decisión**: relación 1:1 usuario–consultorio; sin roles; `consultorio_id` en todas las tablas.
**Justificación**: refleja el usuario objetivo (independiente) y simplifica la autorización.
**Trade-offs aceptados**: multi-profesional requerirá la migración descrita en [03](03_actores_y_roles.md#cómo-se-agregaría-rbac-post-v1).

### DD-09 — Identidad del paciente: teléfono E.164 como clave de deduplicación (por defecto, a confirmar)
**Decisión**: el paciente se identifica por teléfono celular normalizado a E.164, único por consultorio, más nombre y apellido. Si el teléfono existe con otro nombre, se reutiliza y se marca para revisión. Verificación por código (SMS/WhatsApp/email) queda como opción futura.
**Contexto**: pregunta abierta clave del Discovery ([PA-01](10_preguntas_abiertas.md)).
**Alternativas consideradas**: DNI (dato más sensible, fricción, no evita errores de tipeo); nombre + teléfono como clave compuesta (genera duplicados por variantes de nombre); código de verificación (requiere integración de mensajería, contradice DD-07).
**Justificación**: el teléfono es el dato que el consultorio ya usa para contactar; no requiere integraciones; minimiza datos.
**Trade-offs aceptados**: un familiar que reserva con su teléfono para otra persona queda asociado al mismo paciente; sin verificación, alguien puede reservar con un teléfono ajeno.

### DD-10 — "Sobreturno" como marca, no como estado
**Decisión**: `turno.es_sobreturno` booleano + `estado ∈ {RESERVADO, CONFIRMADO, CANCELADO, AUSENTE}`.
**Contexto**: el enunciado listaba "sobreturno" entre los estados.
**Justificación**: un sobreturno también se confirma, cancela o queda ausente; mezclarlo con el estado obligaría a duplicar estados.
**Trade-offs aceptados**: ninguno relevante; se documenta para evitar confusión.

### DD-11 — PWA en lugar de app nativa
**Decisión**: PWA instalable con lectura offline de la última agenda.
**Justificación**: un solo código, sin tiendas, coherente con presupuesto y mobile-first.
**Trade-offs aceptados**: sin push notifications confiables en iOS para v1; sin acceso nativo.

### DD-12 — Garantía de no-solapamiento en dos niveles
**Decisión**: validación en `agenda.domain` + `EXCLUDE USING gist` en PostgreSQL.
**Justificación**: la regla más crítica del Discovery no puede depender solo de código; resuelve carreras entre reservas concurrentes sin locks explícitos.
**Trade-offs aceptados**: acoplamiento a PostgreSQL (aceptable, es el motor elegido).

### DD-13 — Link de gestión mostrado en pantalla, sin email obligatorio
**Decisión**: el token de gestión se entrega una sola vez en la confirmación (copiar, compartir, `.ics`). Si el paciente dejó email y hay SMTP, se puede enviar también (P1, sujeto a [PA-04](10_preguntas_abiertas.md)).
**Justificación**: no depender de integraciones para el flujo principal.
**Trade-offs aceptados**: si el paciente pierde el link, debe contactar al consultorio para cancelar.

### DD-14 — Exportación síncrona por streaming (ZIP con JSON + CSV + PDF)
**Decisión**: `GET /exportacion` genera el ZIP (JSON, CSV y PDF legible, RN-EX-05; ver DD-18) en streaming dentro de la request.
**Justificación**: volúmenes chicos por consultorio; sin colas ni almacenamiento de archivos.
**Trade-offs aceptados**: si un consultorio crece mucho, migrar a generación asíncrona.

### DD-15 — SMTP con Mailpit en local para la recuperación de contraseña
**Decisión**: el backend envía mails por SMTP genérico; en local apunta a Mailpit.
**Contexto**: recuperar la contraseña requiere un canal fuera de banda; ver [IN-01](10_preguntas_abiertas.md).
**Trade-offs aceptados**: en producción hará falta un proveedor SMTP real.

### DD-16 — v1 acotada a la agenda de un profesional, más chica que el MVP del informe (Discovery) (confirmada por el usuario, 2026-11-20)
**Estado**: **Confirmada por el usuario (2026-11-20)**: la v1 es deliberadamente modesta (solo la agenda de un odontólogo, sin multi-profesional, sin secretaria/recepción y sin roles); es una decisión intencional del equipo, no un descuido, aunque el MVP del informe (D.3) sea mayor. Registrada en [`discovery.md` §2 y §10](../discovery/discovery.md) y en la nota de alcance de [`informe-discovery.md` D.3](../discovery/informe-discovery.md).
**Decisión**: v1 = agenda de un único profesional + reserva pública + auditoría + exportación + precios públicos. Se difieren WhatsApp, historia clínica/odontograma, caja/Mercado Pago, obras sociales, ARCA, multi-profesional, múltiples sillones y roles ([`discovery.md` §5–§6, §8](../discovery/discovery.md)).
**Contexto**: el informe competitivo (D.3) sugería un MVP mucho mayor (los imprescindibles no cubiertos por la v1 están ahora en "D.3 Etapas posteriores, primera etapa posterior a la v1", pero el mercado los sigue considerando estándar; riesgo SU-02) (agenda multiprofesional y por sillón, WhatsApp con reinsistencia, HC y odontograma, presupuestos, caja con MP, obras sociales, roles) y advierte que en Argentina "no tener WA descalifica" (C.1). El Discovery eligió, y el usuario confirmó, en cambio, equipo chico, presupuesto acotado y transparencia como diferenciador.
**Alternativas consideradas**: seguir el MVP del informe (más alcance y costo); v1 mínima (elegida).
**Justificación**: validar primero el supuesto principal (reserva por link) con el menor costo ([SU-01](#su-01--el-paciente-argentino-acepta-reservar-por-link)).
**Trade-offs aceptados**: riesgo de no igualar el estándar de mercado (recordatorios, odontograma); mitigado con el piloto y la priorización post-v1 ([PA-05](10_preguntas_abiertas.md)).

### DD-17 — Un único sillón/box implícito en v1
**Decisión**: no existe la entidad "sillón"; el no-solapamiento se evalúa por consultorio ([RN-AG-13](05_reglas_de_negocio.md)). Multi-sillón es opcional del Discovery y queda fuera de v1.
**Alternativas consideradas**: modelar `sillon` desde ya (agrega alcance y UI que el Discovery no pide).
**Trade-offs aceptados**: agregar sillones luego requiere una migración aditiva y una segunda restricción de exclusión; ver [PA-14](10_preguntas_abiertas.md).

### DD-18 — Revisión del roadmap contra D.3 (2026-11-20) (confirmada por el usuario)
**Estado**: **Confirmada por el usuario (2026-11-20)**, tras revisar la v1 contra D.3 (MVP sugerido) del [informe](../discovery/informe-discovery.md). No modifica DD-16: la v1 sigue siendo solo la agenda de un profesional + link público.
**Agregado a v1**: (1) página pública `/seguridad` de seguridad y cumplimiento, honesta (RN-PL-05/06, US-062, dentro de C-17); (2) PDF legible en la exportación, además de JSON y CSV (RN-EX-05, US-050, C-16); (3) prueba gratuita **sin tarjeta** explícita (RN-PL-02, C-17).
**Dejado fuera de v1 por decisión del usuario**: (a) botón de compartir el link de reserva por WhatsApp (`wa.me` / compartir nativo); (b) 2FA del odontólogo (sigue en v2, [PA-13](10_preguntas_abiertas.md)). Ninguno se implementa en v1; ambos quedan en el backlog post-v1.
**Justificación**: sumar los diferenciadores de transparencia de D.3 que cuestan poco y no requieren integraciones; (a) queda fuera por decisión del usuario, en línea con cero integraciones ([DD-07](#dd-07--sin-integraciones-externas-en-v1-confirmada)); (b) porque aún no hay historia clínica en v1 ([PA-13](10_preguntas_abiertas.md)).
**Trade-offs aceptados**: `/seguridad` debe declarar que no hay 2FA ni revisión legal profesional aún; el link se comparte copiándolo.

## Supuestos inferidos

### SU-01 — El paciente argentino acepta reservar por link
**Supuesto**: una parte relevante de los pacientes reservará por la página pública en vez de llamar o escribir por WhatsApp. **Riesgo principal del producto.**
**Origen**: Discovery §10.
**Riesgo si es falso**: la página pública queda sin uso y el producto se reduce a una agenda privada.
**Cómo validar**: piloto con 3–5 consultorios; medir % de turnos con `origen = LINK` a 60 días ([01](01_vision_y_objetivos.md#métricas-de-éxito)); entrevistas a pacientes.

### SU-02 — La agenda sola aporta valor
**Supuesto**: sin recordatorios automáticos, el odontólogo igual percibe mejora frente a planilla + WhatsApp.
**Origen**: Discovery §10. El informe agrava el riesgo: los recordatorios son estándar (92 %) y WhatsApp está en el 87 % de la oferta AR + LATAM (C.1).
**Riesgo si es falso**: baja retención; habría que adelantar recordatorios.
**Cómo validar**: retención semanal a 4 semanas y entrevista de salida.

### SU-03 — La transparencia es motivo de compra
**Supuesto**: precios públicos, exportación y auditoría inclinan la decisión frente a DentalSoft o AgendaPro.
**Origen**: Discovery §1 y §10; informe competitivo.
**Riesgo si es falso**: el diferenciador no convierte.
**Cómo validar**: preguntar en onboarding por qué eligió el sistema; demos de los 5 competidores priorizados (DentalSoft, AgendaPro AR, ClinIA, Bilog, Dentalink; [10 §NE](10_preguntas_abiertas.md)).

### SU-04 — El teléfono basta como identidad del paciente
**Supuesto**: duplicados y suplantaciones serán raros en consultorios chicos. **Suposición:** a confirmar en [PA-01](10_preguntas_abiertas.md).
**Riesgo si es falso**: pacientes fusionados o duplicados; reservas falsas.
**Cómo validar**: medir `requiere_revision` y cancelaciones por "no fui yo" en el piloto.

### SU-05 — Valores por defecto de configuración
**Supuesto**: anticipación de cancelación 24 h, de reserva 2 h, horizonte 60 días, paso de huecos 15 min, prueba de 30 días, máximo 2 turnos futuros por teléfono. **Decisión de diseño** (registrada en [`discovery.md` §7](../discovery/discovery.md) y a validar con 3 odontólogos, §11): todos configurables o fáciles de cambiar.
**Riesgo si es falso**: fricción en el onboarding.
**Cómo validar**: revisar con 3 odontólogos antes del piloto.

### SU-06 — Una zona horaria por consultorio y una instancia de backend
**Supuesto**: cada consultorio opera en una sola zona horaria argentina; el volumen inicial cabe en una instancia (rate limit en memoria). **Supuesto de diseño** (registrado en [`discovery.md` §10](../discovery/discovery.md)).
**Riesgo si es falso**: hay que mover rate limiting a almacenamiento compartido.
**Cómo validar**: revisar al decidir hosting.

### SU-07 — El mapa competitivo del Discovery es correcto
**Supuesto**: las posiciones y los vacíos del informe (DentalSoft 3,65, AgendaPro 3,40, etc.) reflejan la realidad. **Origen**: informe, metodología: casi toda la evidencia es afirmación comercial, sin demos ni reseñas verificadas; "No evidenciado" no equivale a "no existe".
**Riesgo si es falso**: un competidor ya cubre auditoría, exportación o autogestión mejor de lo documentado y el diferenciador se achica.
**Cómo validar**: demos de los cinco priorizados y revisión manual de DentalCore, ZYRA y XIAgenda ([10 §NE](10_preguntas_abiertas.md)).
