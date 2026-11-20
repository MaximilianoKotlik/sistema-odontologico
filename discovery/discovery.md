# Discovery — Sistema de gestión para odontólogo independiente (Argentina)

**Fecha**: 2026-11-16
**Fuentes investigadas**: 30 sistemas relevados (26 verificables, 4 de baja confianza). Notas de esquema fijo en `discovery/sources/`, fichas detalladas por dimensión en `discovery/research/`, informe de cuatro secciones (tabla comparativa, matriz ponderada, análisis competitivo y recomendación) en `discovery/informe-discovery.md`.

## 1. Problema que resuelve

Los consultorios odontológicos chicos de Argentina gestionan sus turnos, su
historia clínica y sus cobros con planillas, papel, WhatsApp y sistemas sueltos.
El proyecto apunta a la gestión integral del consultorio en un solo lugar, y
arranca por la agenda: que un odontólogo independiente administre su agenda y
que sus pacientes reserven o reprogramen turnos por un link, sin crear cuenta.

La investigación muestra que los sistemas dentales argentinos cubren la agenda
básica, pero casi ninguno documenta facturación ARCA, liquidación real de obras
sociales, lista de espera, recuperación de ausentes, periodontograma,
trazabilidad ni exportación de datos, y en el segmento dental los precios son
opacos. El diferenciador elegido para la v1 es la **transparencia**: precios
públicos en ARS, exportación completa de datos, registro de auditoría y
cumplimiento declarado con honestidad.

## 2. Usuarios / roles

Dos únicos actores. No hay recepción, ni roles, ni permisos en la v1.

- **Odontólogo independiente**: es el dueño y el único usuario del sistema. Ve
  y administra su agenda del día y de la semana, bloquea horarios, agrega
  sobreturnos y exporta sus datos. Una cuenta por consultorio.
- **Paciente**: reserva, reprograma o cancela un turno desde un link público,
  sin registrarse ni crear cuenta.

**Alcance v1 confirmado por el usuario (2026-11-20)**: la v1 es deliberadamente
modesta: solo la agenda de un odontólogo, sin multi-profesional, sin
secretaria/recepción y sin roles. Es una decisión intencional del equipo, no un
descuido, aunque el MVP sugerido por el informe (D.3) es más grande (ver la
nota de alcance en `informe-discovery.md`, D.3; los imprescindibles que la v1 no cubre
quedaron en "D.3 Etapas posteriores, primera etapa posterior a la v1").

## 3. Casos de uso

1. Como odontólogo, quiero ver mi agenda del día y de la semana para saber
   cuántos pacientes tengo y a qué hora.
2. Como odontólogo, quiero bloquear horarios y agregar sobreturnos marcados
   para que mi agenda refleje mi disponibilidad real.
3. Como paciente, quiero reservar un turno desde un link sin crear cuenta para
   no tener que llamar ni escribir por WhatsApp.
4. Como paciente, quiero reprogramar o cancelar mi turno con anticipación para
   liberar el horario.
5. Como odontólogo, quiero exportar todos mis datos en un formato abierto para
   no quedar atado al sistema.

**Primer incremento post-v1 (no bloqueante)**: como odontólogo, quiero
registrar la atención en el odontograma y emitir un presupuesto o plan de
tratamiento.

## 4. Competidores / soluciones existentes

Competidores validados para analizar mediante demo (de 30 relevados):

| Competidor | Problema que resuelve | Pricing | Diferenciadores |
|---|---|---|---|
| DentalSoft (AR) | Gestión integral de consultorio y clínica dental | Público: Gratis $0; Gestión $30.000/mes; Pro $60.000/mes por profesional (moneda no aclarada) | Precios públicos, sobreturnos, multi-sucursal, Mercado Pago, liquidaciones de obras sociales. Primero en la matriz (3,65/5) |
| AgendaPro Argentina | Agenda online y recordatorios para reemplazar planillas | Desde ARS 13.900/mes, 14 días gratis | Agenda por sillón, odontograma, Mercado Pago, IA conversacional en WhatsApp (afirmación comercial). Segundo (3,40/5) |
| ClinIA / OdontoClinIA (AR) | Historia clínica electrónica, agenda, odontograma y facturación para clínicas | No publicado; abono por institución | Integraciones argentinas declaradas (ARCA, PUCO, RENAPER), odontograma versionado. Todo afirmación comercial (2,85/5) |
| Bilog (AR) | Gestión integral y auditoría de obras sociales | No publicado; suscripción mensual sin permanencia | 20+ años declarados, módulo de auditoría de obras sociales, app móvil, IA (afirmación comercial) |
| Dentalink (CL) | Software dental cloud regional | No publicado; planes Esencial/Pro/Titanium | Único con módulo específico para Argentina (obras sociales, receta electrónica), ISO 27001 declarada (2,95/5) |

Referentes de experiencia de usuario: **Turnito** (reserva sin cuenta del
paciente y seña con Mercado Pago), **AgendaPro** (agenda por sillón y
recordatorio que insiste si el paciente no confirma), **MedicAI** (auditoría
inalterable, exportación completa y segundo factor).

**Notas**: la evidencia es casi toda afirmación comercial de los proveedores;
Capterra y G2 bloquearon el acceso a reseñas, y no se accedió a demos. Hoy el
usuario objetivo resuelve esto con planilla, papel y WhatsApp, que son la
competencia real a desplazar. Detalle completo en `discovery/informe-discovery.md`.

## 5. Funcionalidades necesarias

- Agenda diaria y semanal de un único profesional.
- Bloqueos de horario y sobreturnos marcados explícitamente.
- Duración configurable por tipo de prestación y prevención de solapamientos.
- Página pública de reserva por link, sin cuenta del paciente: reservar,
  reprogramar y cancelar.
- Registro de auditoría de los cambios sobre turnos (quién, qué, cuándo).
- Exportación completa de los datos del consultorio en formato abierto.
- Precios públicos en ARS, con prueba gratuita.

## 6. Funcionalidades opcionales

En orden tentativo de llegada, todas fuera de la v1:

1. Historia clínica con anamnesis, odontograma FDI versionado, presupuestos y
   plan de tratamiento (primer incremento).
2. Recordatorios, confirmación y recuperación de ausentes por WhatsApp.
3. Caja, cuenta corriente del paciente, señas y cobro con Mercado Pago.
4. Obras sociales y prepagas: coberturas, prestaciones y liquidación.
5. Facturación electrónica ARCA desde el cobro.
6. Lista de espera automática y recall de controles periódicos.
7. Multi-profesional, múltiples sillones, recepción, roles y permisos.
8. Periodontograma e imágenes/radiografías.
9. Sincronización con Google Calendar.

## 7. Reglas de negocio

- Un turno no puede solaparse con otro del mismo profesional, salvo que se
  marque explícitamente como sobreturno.
- El paciente solo puede cancelar o reprogramar por link con una anticipación
  mínima configurable por el odontólogo (por ejemplo, 24 horas).
- Todo cambio sobre un turno queda registrado con autor, fecha y valor anterior.
- Regla documentada para cuando entre la historia clínica: las evoluciones y el
  odontograma no se borran ni se editan en el lugar, se versionan con autor y
  fecha, en línea con la Ley 26.529 sobre historia clínica.

### Decisiones de diseño derivadas (definidas al armar la KB, 2026-11-20)

Estas reglas no surgieron de la investigación de mercado: son **decisiones de
diseño** tomadas al armar la Knowledge Base para volver operables las reglas
anteriores. Se registran acá para que la KB tenga respaldo explícito y quedan
sujetas a validación con odontólogos reales.

- **Decisión de diseño** (KB RN-AG-03): un turno que no es sobreturno debe caer
  completo dentro de una franja de disponibilidad y no intersectar ningún
  bloqueo.
- **Decisión de diseño** (KB RN-AG-04): el sobreturno solo lo crea el
  odontólogo, puede solaparse con otros turnos y salirse de la disponibilidad,
  pero no puede caer dentro de un bloqueo; nunca se ofrece en la página pública.
- **Decisión de diseño** (KB RN-AG-05): no se puede crear un bloqueo sobre
  turnos activos; el sistema lo rechaza y lista los turnos en conflicto para
  cancelarlos o reprogramarlos primero.
- **Decisión de diseño** (KB RN-AG-08): horarios de turnos y bloqueos en
  múltiplos de 5 minutos.
- **Decisión de diseño** (KB RN-AG-13): un único sillón/box implícito; el
  no-solapamiento se evalúa por consultorio. Punto de extensión: al incorporar
  multi-profesional o multi-sillón (opcionales, §6 ítem 7), el no-solapamiento
  pasa a evaluarse por profesional y por sillón mediante una migración aditiva.
- **Valores por defecto (decisión de diseño, configurables)**: anticipación
  mínima de cancelación 24 h (concreta el "por ejemplo, 24 horas" de arriba),
  anticipación mínima de reserva 2 h, horizonte de reserva 60 días, paso de
  huecos 15 min, máximo 2 turnos futuros activos por teléfono vía link
  (anti-abuso) y prueba gratuita de 30 días sin medio de pago.
- **Decisión de diseño** (KB RN-RP-03): el paciente se deduplica por teléfono
  normalizado dentro del consultorio (ver pregunta abierta sobre identidad, §11).

## 8. Integraciones

Ninguna integración externa en la v1, por decisión explícita. La reserva por
link es una página web pública, no un bot de WhatsApp. Quedan diferidas, con su
costo y riesgo propios:

- WhatsApp Business API (Meta Cloud API): costo por conversación.
- Mercado Pago: comisión por transacción.
- ARCA (ex AFIP): web services con certificado digital del consultorio.
- Google Calendar.

## 9. Restricciones

- Equipo chico y presupuesto acotado: prioriza simplicidad y servicios gratuitos
  o baratos.
- Mobile-first obligatorio: el paciente reserva desde el teléfono y el
  odontólogo consulta su agenda desde el teléfono.
- Datos de salud tratados conforme a la Ley 25.326 de protección de datos
  personales: hosting y tratamiento acordes, registro de base de datos ante la
  AAIP si corresponde.
- **Marco normativo complementario (referencia de diseño, 2026-11-20)**: la KB
  también considera el Decreto 1558/2001 (reglamenta la Ley 25.326), el Decreto
  746/2017 (la AAIP pasa a ser el órgano de control de la ley de datos
  personales), la Resolución AAIP 47/2018 (medidas de seguridad
  *recomendadas*) y la Ley 27.483 (aprueba el Convenio 108 del Consejo de
  Europa). Su existencia y alcance se verificaron en fuentes oficiales
  (argentina.gob.ar/normativa, Boletín Oficial) el 2026-11-20; su aplicación al
  producto **no tiene revisión profesional** y queda pendiente de asesoría legal.
- **Decisión de diseño (2026-11-20)**: se tratan como datos sensibles todos los
  datos de pacientes (un turno más la prestación puede revelar información de
  salud), y se limita la recolección al mínimo: nombre, apellido, teléfono y
  email opcional, sin DNI ni datos clínicos.

## 10. Riesgos

- **Supuesto sin probar (el que más preocupa)**: que el paciente argentino
  prefiera reservar por link antes que llamar o escribir por WhatsApp. No está
  validado con pacientes reales.
- **Supuesto sin probar**: que la agenda sola aporte valor aunque los
  recordatorios lleguen después. Sin WhatsApp en v1, el ausentismo puede no
  bajar y el odontólogo no percibir diferencia frente a su método actual.
- **Supuesto sin probar**: que la transparencia (precios públicos, exportación,
  cumplimiento) sea motivo de compra frente a DentalSoft o AgendaPro.
- **Riesgo legal**: interpretar las Leyes 25.326 y 26.529 sin asesoría y
  prometer un cumplimiento que no es tal. El cumplimiento se declara con
  honestidad y sin certificaciones hasta tener revisión profesional.
- **Riesgo de evidencia**: el análisis competitivo se basa en afirmaciones
  comerciales; las demos de los cinco competidores priorizados pueden cambiar
  el mapa de diferenciadores.
- **Riesgo de alcance (aceptado, confirmado por el usuario el 2026-11-20)**: la
  v1 es más chica que el MVP que sugiere el informe (D.3: multi-profesional,
  WhatsApp, historia clínica, caja, obras sociales, roles). Puede no igualar el
  estándar de mercado (recordatorios, odontograma); se mitiga con un piloto y
  con la priorización posterior (D.3, "Etapas posteriores": WhatsApp, HC/odontograma,
  presupuestos, caja/MP, obras sociales, multiprofesional/sillón y roles).
- **Hipótesis a validar (meta tentativa, definida al armar la KB, 2026-11-20)**:
  que al menos el 40 % de los turnos se cree por link a los 60 días de uso. Es
  una meta de trabajo sin fuente de mercado; se recalibra tras el piloto. Las
  otras metas tentativas (activación ≥ 60 %, retención ≥ 50 % a 4 semanas,
  reserva en menos de 90 s en mobile) tienen el mismo carácter.
- **Supuesto de diseño (2026-11-20)**: una zona horaria argentina por
  consultorio y una sola instancia de backend alcanzan para el volumen inicial.

## 11. Preguntas abiertas

- ¿Cómo se identifica al paciente sin cuenta para evitar duplicados: nombre y
  teléfono, DNI, o verificación por código?
- Pendiente de decidir más adelante: modelo de precios (plan fijo, por
  profesional o por sillón) y si existe plan gratuito.
- Pendiente de validar con usuarios: si la reserva por link reemplaza o
  complementa el WhatsApp del consultorio.
- Pendiente de validar con 3 odontólogos antes del piloto: los valores por
  defecto de diseño (24 h de cancelación, 2 h de reserva, 60 días, 15 min de
  paso, 2 turnos por teléfono, 30 días de prueba).
- Pendiente de decidir con asesoría: política de baja de cuenta y retención de
  datos (suposición de diseño: 30 días para exportar y luego borrado, salvo
  obligación legal) y quién debe inscribir las bases ante la AAIP (suposición:
  cada consultorio su base de pacientes y el proveedor la de sus clientes).
- Pendiente de revisión legal profesional: las normas del marco complementario
  (§9) antes del piloto con pacientes reales.
