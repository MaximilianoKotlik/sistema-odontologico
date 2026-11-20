# Decisiones y Supuestos

> DD-01 a DD-08 fueron **confirmadas por el usuario** el 2026-11-16 y DD-16, DD-18, DD-19, DD-20 y DD-21 el 2026-11-20. El resto
> (DD-09 a DD-15 y DD-17) son decisiones por defecto propuestas al armar la KB: se aplican salvo que el usuario
> las revise. Las decisiones de diseño derivadas de las reglas (RN-AG-03/04/05/08/13, valores por defecto) están
> registradas en el Discovery ([`discovery.md` §7](../docs/discovery/discovery.md)), con su procedencia.

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

### DD-09 — Identidad del paciente: teléfono E.164 como clave de deduplicación (**reemplazada por [DD-24](#dd-24--identidad-del-paciente-por-dni-resuelve-pa-01-confirmada-por-el-usuario-2026-11-20)**)
> Se conserva como historia: el usuario eligió el DNI el 2026-11-20.

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
**Estado**: **Confirmada por el usuario (2026-11-20)**: la v1 es deliberadamente modesta (solo la agenda de un odontólogo, sin multi-profesional, sin secretaria/recepción y sin roles); es una decisión intencional del equipo, no un descuido, aunque el MVP del informe (D.3) sea mayor. Registrada en [`discovery.md` §2 y §10](../docs/discovery/discovery.md) y en la nota de alcance de [`informe-discovery.md` D.3](../docs/discovery/informe-discovery.md).
**Decisión**: v1 = agenda de un único profesional + reserva pública + auditoría + exportación + precios públicos. Se difieren WhatsApp, historia clínica/odontograma, caja/Mercado Pago, obras sociales, ARCA, multi-profesional, múltiples sillones y roles ([`discovery.md` §5–§6, §8](../docs/discovery/discovery.md)).
**Contexto**: el informe competitivo (D.3) sugería un MVP mucho mayor (los imprescindibles no cubiertos por la v1 están ahora en "D.3 Etapas posteriores, primera etapa posterior a la v1", pero el mercado los sigue considerando estándar; riesgo SU-02) (agenda multiprofesional y por sillón, WhatsApp con reinsistencia, HC y odontograma, presupuestos, caja con MP, obras sociales, roles) y advierte que en Argentina "no tener WA descalifica" (C.1). El Discovery eligió, y el usuario confirmó, en cambio, equipo chico, presupuesto acotado y transparencia como diferenciador.
**Alternativas consideradas**: seguir el MVP del informe (más alcance y costo); v1 mínima (elegida).
**Justificación**: validar primero el supuesto principal (reserva por link) con el menor costo ([SU-01](#su-01--el-paciente-argentino-acepta-reservar-por-link)).
**Trade-offs aceptados**: riesgo de no igualar el estándar de mercado (recordatorios, odontograma); mitigado con el piloto y la priorización post-v1 ([PA-05](10_preguntas_abiertas.md)).

### DD-17 — Un único sillón/box implícito en v1
**Decisión**: no existe la entidad "sillón"; el no-solapamiento se evalúa por consultorio ([RN-AG-13](05_reglas_de_negocio.md)). Multi-sillón es opcional del Discovery y queda fuera de v1.
**Alternativas consideradas**: modelar `sillon` desde ya (agrega alcance y UI que el Discovery no pide).
**Trade-offs aceptados**: agregar sillones luego requiere una migración aditiva y una segunda restricción de exclusión; ver [PA-14](10_preguntas_abiertas.md).

### DD-18 — Revisión del roadmap contra D.3 (2026-11-20) (confirmada por el usuario)
**Estado**: **Confirmada por el usuario (2026-11-20)**, tras revisar la v1 contra D.3 (MVP sugerido) del [informe](../docs/discovery/informe-discovery.md). No modifica DD-16: la v1 sigue siendo solo la agenda de un profesional + link público.
**Agregado a v1**: (1) página pública `/seguridad` de seguridad y cumplimiento, honesta (RN-PL-05/06, US-062, change C-22 `pagina-seguridad`); (2) PDF legible en la exportación, además de JSON y CSV (RN-EX-05, US-050, C-19 `exportacion-datos`); (3) prueba gratuita **sin tarjeta** explícita (RN-PL-02, C-21 `pagina-precios`). *(IDs de change actualizados tras la renumeración de `CHANGES.md` del 2026-11-20.)*
**Dejado fuera de v1 por decisión del usuario**: (a) botón de compartir el link de reserva por WhatsApp (`wa.me` / compartir nativo); (b) 2FA del odontólogo (sigue en v2, [PA-13](10_preguntas_abiertas.md)). Ninguno se implementa en v1; ambos quedan en el backlog post-v1.
**Justificación**: sumar los diferenciadores de transparencia de D.3 que cuestan poco y no requieren integraciones; (a) queda fuera por decisión del usuario, en línea con cero integraciones ([DD-07](#dd-07--sin-integraciones-externas-en-v1-confirmada)); (b) porque aún no hay historia clínica en v1 ([PA-13](10_preguntas_abiertas.md)).
**Trade-offs aceptados**: `/seguridad` debe declarar que no hay 2FA ni revisión legal profesional aún; el link se comparte copiándolo.

### DD-19 — SMTP es infraestructura, no integración de negocio (resuelve IN-01) (confirmada por el usuario, 2026-11-20)
**Estado**: **Confirmada por el usuario (2026-11-20)**; cierra [IN-01](10_preguntas_abiertas.md#in-01--sin-integraciones-externas-vs-recuperación-de-contraseña-por-email).
**Decisión**: el envío de email por SMTP se trata como infraestructura genérica y no cuenta como "integración externa" en el sentido de [DD-07](#dd-07--sin-integraciones-externas-en-v1-confirmada). En local se usa Mailpit ([DD-15](#dd-15--smtp-con-mailpit-en-local-para-la-recuperación-de-contraseña)); en producción, un proveedor SMTP estándar configurable por variables de entorno. La elección del proveedor sigue abierta en [PA-02](10_preguntas_abiertas.md) y no bloquea la implementación.
**Contexto**: la recuperación de contraseña del odontólogo necesita un canal fuera de banda; sin email, quien olvida la contraseña pierde el acceso.
**Alternativas consideradas**: recuperación manual por el equipo (no escala y expone datos por canales informales); sin recuperación (inaceptable para un SaaS con datos de salud).
**Trade-offs aceptados**: se suma una dependencia operativa (proveedor SMTP) en producción, sin lógica de negocio acoplada.

### DD-20 — Sobreturno como marca `es_sobreturno` + 4 estados (resuelve IN-02) (confirmada por el usuario, 2026-11-20)
**Estado**: **Confirmada por el usuario (2026-11-20)**; cierra [IN-02](10_preguntas_abiertas.md#in-02--sobreturno-listado-como-estado-del-turno). Ratifica [DD-10](#dd-10--sobreturno-como-marca-no-como-estado), ya aplicada en [04](04_modelo_de_datos.md).
**Decisión**: "sobreturno" no es un estado. El turno tiene 4 estados (`RESERVADO`, `CONFIRMADO`, `CANCELADO`, `AUSENTE`) y una marca booleana `es_sobreturno`.
**Justificación**: un sobreturno también puede confirmarse, cancelarse o quedar ausente; modelarlo como estado haría perder la marca al cambiar de estado.

### DD-21 — Activación manual del plan durante el piloto (resuelve IN-03) (confirmada por el usuario, 2026-11-20)
**Estado**: **Confirmada por el usuario (2026-11-20)**; cierra [IN-03](10_preguntas_abiertas.md#in-03--precios-públicos-con-prueba-gratuita-sin-cobro-integrado).
**Decisión**: la v1 publica precios en ARS y ofrece prueba gratuita sin medio de pago, pero no cobra. Al vencer la prueba, el equipo activa el plan a mano (transferencia o factura fuera del sistema), como operación interna auditada ([RN-PL-04](05_reglas_de_negocio.md)). El cobro integrado (Mercado Pago) es un change posterior a la v1.
**Contexto**: [DD-07](#dd-07--sin-integraciones-externas-en-v1-confirmada) excluye Mercado Pago de la v1.
**Trade-offs aceptados**: trabajo manual del equipo por cada alta paga; aceptable para el volumen del piloto.

### DD-22 — Plan único por consultorio a ARS 14.900/mes con IVA incluido (resuelve PA-03) (confirmada por el usuario, 2026-11-20)
**Estado**: **Confirmada por el usuario (2026-11-20)**; cierra [PA-03](10_preguntas_abiertas.md#preguntas-abiertas-priorizadas).
**Decisión**: la v1 tiene **un solo plan por consultorio** ("Plan consultorio") a **ARS 14.900 por mes, IVA incluido**. Hay una **prueba gratuita de 30 días sin tarjeta ni medio de pago** ([RN-PL-02](05_reglas_de_negocio.md)) y **no hay plan gratuito permanente**: vencida la prueba sin activación, el consultorio pasa a `VENCIDO` ([RN-PL-03](05_reglas_de_negocio.md)). La activación es manual ([DD-21](#dd-21--activación-manual-del-plan-durante-el-piloto-resuelve-in-03-confirmada-por-el-usuario-2026-11-20)).
**Contexto**: referencias del Discovery (2026-11-16): AgendaPro desde ARS 13.900/mes, MedicAI ARS 25.900 + IVA por profesional, DentalSoft Gestión ARS 30.000 por profesional, Turnito desde ARS 9.900 sin IVA ([NE-10](10_preguntas_abiertas.md)). La v1 trae solo agenda (sin WhatsApp ni historia clínica), así que se posiciona en la franja de entrada.
**Alternativas consideradas**: dos planes (no hay funciones de v1 que los diferencien); ARS 19.900 o 24.900 (difíciles de justificar sin WhatsApp); plan gratuito limitado (agrega reglas y complica C-20).
**Trade-offs aceptados**: margen bajo durante el piloto; el precio se revisa con la métrica de conversión de prueba ([01](01_vision_y_objetivos.md)) y cada cambio actualiza la fuente versionada de C-21 y su fecha visible ([RN-PL-01](05_reglas_de_negocio.md)).

### DD-24 — Identidad del paciente por DNI (resuelve PA-01) (confirmada por el usuario, 2026-11-20)
**Estado**: **Confirmada por el usuario (2026-11-20)**; cierra [PA-01](10_preguntas_abiertas.md#preguntas-abiertas-priorizadas) y reemplaza a DD-09.
**Decisión**: el paciente se identifica por su **DNI**, único por consultorio. Se guarda solo con dígitos (7 u 8, sin puntos). Es obligatorio en la reserva pública y en la carga manual. El teléfono celular sigue siendo obligatorio como dato de contacto (normalizado a E.164), pero ya no es clave ni es único. Si el DNI ya existe, se reutiliza el paciente sin sobrescribir sus datos; si nombre o apellido difieren, se marca `requiere_revision` (igual que antes con el teléfono). El límite de turnos futuros se cuenta por paciente, o sea por DNI.
**Contexto**: pregunta abierta clave del Discovery ([PA-01](10_preguntas_abiertas.md)). El teléfono como clave asociaba al mismo paciente a familiares que comparten el número.
**Alternativas consideradas**: teléfono E.164 (DD-09, descartada por los familiares que comparten teléfono); nombre + teléfono (duplicados por variantes del nombre); código de verificación (requiere mensajería, DD-07).
**Justificación**: el DNI identifica a la persona y no se comparte entre familiares; los consultorios ya lo usan en su ficha.
**Trade-offs aceptados**: se junta un dato personal más, lo que se aparta de la minimización de [11](11_marco_legal_argentino.md) y hay que declararlo en la política de privacidad (PA-06). Suma un campo al formulario público. No evita errores de tipeo ni que alguien reserve con un DNI ajeno (no hay verificación). Los pacientes sin DNI argentino quedan en [PA-15](10_preguntas_abiertas.md).

### DD-25 — Hosting: Postgres en el mismo Docker Compose, local y después un VPS (resuelve en parte PA-02) (confirmada por el usuario, 2026-11-20)
**Estado**: **Confirmada por el usuario (2026-11-20)**; acota [PA-02](10_preguntas_abiertas.md#preguntas-abiertas-priorizadas).
**Decisión**: la base de datos es un contenedor PostgreSQL dentro del mismo `docker-compose.yml` que el backend, el frontend y Mailpit, tanto en local como en producción. Hoy todo corre en local; la producción va a ser ese mismo Compose en un VPS. Así las migraciones Flyway y los datos de prueba son idénticos en todos los entornos. **Sigue abierto** (PA-02 acotada): el país del VPS y la política de backups, que se deciden antes de cargar pacientes reales.
**Alternativas consideradas**: un Postgres gestionado (backups y alta disponibilidad incluidos, pero distinto de local y más caro); un PaaS.
**Justificación**: mismo entorno en todos lados, costo bajo y control total.
**Trade-offs aceptados**: los backups, actualizaciones de Postgres y la seguridad del servidor quedan a cargo del equipo; si el VPS se cae, se cae todo. Si el VPS está fuera de Argentina, hay que documentar la transferencia internacional (LG-09, [11](11_marco_legal_argentino.md)).

### DD-26 — Avisos al paciente: link, calendario y email opcional (resuelve PA-04) (confirmada por el usuario, 2026-11-20)
**Estado**: **Confirmada por el usuario (2026-11-20)**; cierra [PA-04](10_preguntas_abiertas.md#preguntas-abiertas-priorizadas).
**Decisión**: el paciente ve su turno en pantalla y lo guarda con el link de gestión `/t/{token}` y un archivo `.ics`. Si deja su email y hay SMTP configurado, también recibe un email con la confirmación y con los cambios que haga el odontólogo (reprogramar o cancelar). Sin SMS ni WhatsApp en v1.
**Alternativas consideradas**: solo link y `.ics`, sin email; email obligatorio.
**Justificación**: cero fricción al reservar y aviso automático para quien lo quiera; usa la infraestructura SMTP de [DD-19](#dd-19--smtp-es-infraestructura-no-integración-de-negocio-resuelve-in-01-confirmada-por-el-usuario-2026-11-20).
**Trade-offs aceptados**: un paciente sin email no se entera solo de un cambio que haga el odontólogo: lo tiene que avisar el consultorio.

### DD-27 — Sin piloto formal: lanzar y medir (resuelve PA-05) (confirmada por el usuario, 2026-11-20)
**Estado**: **Confirmada por el usuario (2026-11-20)**; cierra [PA-05](10_preguntas_abiertas.md#preguntas-abiertas-priorizadas).
**Decisión**: no se hace un piloto con un umbral de aprobación antes de lanzar. Se lanza y se mide desde el primer día el % de turnos con `origen = LINK` y el resto de las métricas de [01](01_vision_y_objetivos.md#métricas-de-éxito), que quedan como indicadores de seguimiento y no como condición para avanzar.
**Alternativas consideradas**: piloto con 3–5 consultorios y meta de ≥ 40 % de reservas por link a 60 días.
**Justificación**: decisión del usuario: priorizar salir antes.
**Trade-offs aceptados**: el supuesto principal ([SU-01](#su-01--el-paciente-argentino-acepta-reservar-por-link)) se valida con usuarios reales ya lanzados; si falla, se corrige sobre la marcha.

### DD-28 — Textos legales: borrador propio y después revisión de un abogado (resuelve PA-06) (confirmada por el usuario, 2026-11-20)
**Estado**: **Confirmada por el usuario (2026-11-20)**; cierra [PA-06](10_preguntas_abiertas.md#preguntas-abiertas-priorizadas).
**Decisión**: el equipo escribe un primer borrador de términos y condiciones y de política de privacidad (incluye el DNI de [DD-24](#dd-24--identidad-del-paciente-por-dni-resuelve-pa-01-confirmada-por-el-usuario-2026-11-20) y la conservación de [DD-29](#dd-29--baja-de-cuenta-los-datos-se-conservan-con-la-cuenta-desactivada-resuelve-pa-07-confirmada-por-el-usuario-2026-11-20)), marcado "pendiente de revisión legal". Después lo revisa un abogado con experiencia en datos personales y salud, que también confirma la inscripción ante la AAIP. El registro no se publica con datos reales sin esa revisión.
**Alternativas consideradas**: contratar la revisión desde cero; un asesor propio.
**Justificación**: abarata la revisión y destraba C-07 y C-22 con textos provisorios.
**Trade-offs aceptados**: hasta la revisión, nada declara cumplimiento legal (LG-10).

### DD-29 — Baja de cuenta: los datos se conservan con la cuenta desactivada (resuelve PA-07) (confirmada por el usuario, 2026-11-20)
**Estado**: **Confirmada por el usuario (2026-11-20)**; cierra [PA-07](10_preguntas_abiertas.md#preguntas-abiertas-priorizadas).
**Decisión**: la baja se pide por soporte en v1. La cuenta queda desactivada (no se puede ingresar y la página pública deja de tomar turnos) y sus datos y los de sus pacientes se conservan; no hay borrado automático. Antes de la baja se ofrece la exportación completa.
**Alternativas consideradas**: exportar y borrar a los 30 días; borrado inmediato.
**Justificación**: decisión del usuario: permite reactivar la cuenta sin perder nada.
**Trade-offs aceptados**: conservar datos sin plazo choca con el principio de finalidad y con el derecho de supresión de la Ley 25.326 ([11](11_marco_legal_argentino.md), LG-08): **la revisión legal de DD-28 tiene que validar o fijar un plazo**.

### DD-30 — Aviso al odontólogo: marca de "nuevo" y email opcional (resuelve PA-08) (confirmada por el usuario, 2026-11-20)
**Estado**: **Confirmada por el usuario (2026-11-20)**; cierra [PA-08](10_preguntas_abiertas.md#preguntas-abiertas-priorizadas).
**Decisión**: los turnos que el paciente crea, reprograma o cancela por el link aparecen marcados como nuevos en la agenda; si el odontólogo lo activa y hay SMTP, además recibe un email.
**Alternativas consideradas**: solo la marca; email por cada movimiento.
**Justificación**: no se pierde ningún cambio y el email no molesta a quien no lo quiere.
**Trade-offs aceptados**: sin email activado, el odontólogo se entera recién al abrir la agenda.

### DD-31 — Sin verificación del email del odontólogo en v1 (resuelve PA-09) (confirmada por el usuario, 2026-11-20)
**Estado**: **Confirmada por el usuario (2026-11-20)**; cierra [PA-09](10_preguntas_abiertas.md#preguntas-abiertas-priorizadas).
**Decisión**: el registro no exige confirmar el email. Se exige antes de habilitar el cobro.
**Alternativas consideradas**: verificación obligatoria desde el registro.
**Justificación**: menos fricción en la prueba gratuita.
**Trade-offs aceptados**: una cuenta puede crearse con un email ajeno; la recuperación de contraseña igual va a ese email.

### DD-32 — Teléfono obligatorio también en la carga manual (resuelve PA-10) (confirmada por el usuario, 2026-11-20)
**Estado**: **Confirmada por el usuario (2026-11-20)**; cierra [PA-10](10_preguntas_abiertas.md#preguntas-abiertas-priorizadas).
**Decisión**: todo paciente tiene teléfono celular, también el que carga el odontólogo a mano. Es dato de contacto; la identidad es el DNI (DD-24).
**Alternativas consideradas**: opcional en la carga manual.
**Justificación**: el consultorio siempre puede contactar al paciente.
**Trade-offs aceptados**: para un paciente sin celular se carga el de un familiar.

### DD-33 — Página pública: solo nombre, dirección y teléfono (resuelve PA-11) (confirmada por el usuario, 2026-11-20)
**Estado**: **Confirmada por el usuario (2026-11-20)**; cierra [PA-11](10_preguntas_abiertas.md#preguntas-abiertas-priorizadas).
**Decisión**: en v1 el odontólogo personaliza solo nombre, dirección y teléfono de su página pública. Logo, colores y textos quedan para después de la v1.
**Alternativas consideradas**: sumar logo; personalización completa.
**Justificación**: evita manejo de archivos y mantiene la v1 chica.
**Trade-offs aceptados**: todas las páginas públicas se ven iguales.

### DD-34 — Nombre comercial: Muelas Felices (resuelve PA-12) (confirmada por el usuario, 2026-11-20)
**Estado**: **Confirmada por el usuario (2026-11-20)**; cierra [PA-12](10_preguntas_abiertas.md#preguntas-abiertas-priorizadas).
**Decisión**: el producto se llama **Muelas Felices**. Se cambia en `design-system/brand.js`, la única fuente del nombre. El dominio todavía no está definido; el logo sigue provisorio.
**Alternativas consideradas**: seguir con el nombre provisorio.
**Justificación**: decisión del usuario.
**Trade-offs aceptados**: falta el dominio, que necesitan los emails y el lanzamiento.

### DD-35 — 2FA del odontólogo antes de la historia clínica (resuelve PA-13) (confirmada por el usuario, 2026-11-20)
**Estado**: **Confirmada por el usuario (2026-11-20)**; cierra [PA-13](10_preguntas_abiertas.md#preguntas-abiertas-priorizadas).
**Decisión**: la verificación en dos pasos se suma como requisito de la versión que agregue historia clínica u odontograma (v2). En v1 no hay 2FA ([DD-18](#dd-18--revisión-del-roadmap-contra-d3-2026-11-20-confirmada-por-el-usuario)).
**Alternativas consideradas**: antes del lanzamiento; sin fecha.
**Justificación**: la agenda tiene datos de contacto; la historia clínica, datos de salud.
**Trade-offs aceptados**: en v1 la cuenta se protege solo con contraseña, bloqueo por intentos y rate limit.

### DD-36 — Un solo sillón por consultorio en v1 (resuelve PA-14) (confirmada por el usuario, 2026-11-20)
**Estado**: **Confirmada por el usuario (2026-11-20)**; cierra [PA-14](10_preguntas_abiertas.md#preguntas-abiertas-priorizadas).
**Decisión**: se confirma [RN-AG-13](05_reglas_de_negocio.md): un profesional y un sillón implícito por consultorio; la agenda por sillón queda para después de la v1, con el punto de extensión que ya prevé C-10.
**Alternativas consideradas**: agenda por sillón desde la v1.
**Justificación**: es el caso del odontólogo independiente objetivo.
**Trade-offs aceptados**: un consultorio con dos sillones no puede usar la v1 para ambos.

### DD-37 — Pacientes sin DNI argentino: reservan llamando al consultorio (resuelve PA-15) (confirmada por el usuario, 2026-11-20)
**Estado**: **Confirmada por el usuario (2026-11-20)**; cierra [PA-15](10_preguntas_abiertas.md#preguntas-abiertas-priorizadas).
**Decisión**: en v1 el DNI es obligatorio. Quien no lo tenga (extranjero con pasaporte, menor sin el DNI a mano) pide el turno por teléfono y el odontólogo lo carga.
**Alternativas consideradas**: tipo y número de documento como clave.
**Justificación**: mantiene el modelo de DD-24 simple.
**Trade-offs aceptados**: esos pacientes no pueden reservar solos; el odontólogo carga un DNI en la carga manual.

## Supuestos inferidos

### SU-01 — El paciente argentino acepta reservar por link
**Supuesto**: una parte relevante de los pacientes reservará por la página pública en vez de llamar o escribir por WhatsApp. **Riesgo principal del producto.**
**Origen**: Discovery §10.
**Riesgo si es falso**: la página pública queda sin uso y el producto se reduce a una agenda privada.
**Cómo validar**: sin piloto formal ([DD-27](#dd-27--sin-piloto-formal-lanzar-y-medir-resuelve-pa-05-confirmada-por-el-usuario-2026-11-20)); medir desde el lanzamiento el % de turnos con `origen = LINK` ([01](01_vision_y_objetivos.md#métricas-de-éxito)); entrevistas a pacientes.

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

### SU-04 — El DNI basta como identidad del paciente
**Supuesto**: duplicados y suplantaciones serán raros en consultorios chicos. La identidad por DNI quedó decidida en [DD-24](#dd-24--identidad-del-paciente-por-dni-resuelve-pa-01-confirmada-por-el-usuario-2026-11-20).
**Riesgo si es falso**: pacientes fusionados o duplicados; reservas falsas.
**Cómo validar**: medir `requiere_revision` y cancelaciones por "no fui yo" en el piloto.

### SU-05 — Valores por defecto de configuración
**Supuesto**: anticipación de cancelación 24 h, de reserva 2 h, horizonte 60 días, paso de huecos 15 min, prueba de 30 días, máximo 2 turnos futuros por teléfono. **Decisión de diseño** (registrada en [`discovery.md` §7](../docs/discovery/discovery.md) y a validar con 3 odontólogos, §11): todos configurables o fáciles de cambiar.
**Riesgo si es falso**: fricción en el onboarding.
**Cómo validar**: revisar con 3 odontólogos antes del piloto.

### SU-06 — Una zona horaria por consultorio y una instancia de backend
**Supuesto**: cada consultorio opera en una sola zona horaria argentina; el volumen inicial cabe en una instancia (rate limit en memoria). **Supuesto de diseño** (registrado en [`discovery.md` §10](../docs/discovery/discovery.md)).
**Riesgo si es falso**: hay que mover rate limiting a almacenamiento compartido.
**Cómo validar**: revisar al decidir hosting.

### SU-07 — El mapa competitivo del Discovery es correcto
**Supuesto**: las posiciones y los vacíos del informe (DentalSoft 3,65, AgendaPro 3,40, etc.) reflejan la realidad. **Origen**: informe, metodología: casi toda la evidencia es afirmación comercial, sin demos ni reseñas verificadas; "No evidenciado" no equivale a "no existe".
**Riesgo si es falso**: un competidor ya cubre auditoría, exportación o autogestión mejor de lo documentado y el diferenciador se achica.
**Cómo validar**: demos de los cinco priorizados y revisión manual de DentalCore, ZYRA y XIAgenda ([10 §NE](10_preguntas_abiertas.md)).
