# Visión y Objetivos

> Fuente: [`../discovery/discovery.md`](../discovery/discovery.md) (puntos 1, 2, 3, 5, 6 y 10). Fecha: 2026-11-16.

## Propósito del sistema

**Que un odontólogo independiente de Argentina administre su agenda desde el teléfono y que sus
pacientes reserven, reprogramen o cancelen turnos por un link, sin crear cuenta.**

Los consultorios odontológicos chicos gestionan hoy turnos, historia clínica y cobros con planillas,
papel, WhatsApp y sistemas sueltos. La visión de largo plazo es la gestión integral del consultorio
en un solo lugar; la v1 arranca deliberadamente por la agenda, que es el dolor diario y el punto de
entrada más barato de construir y de adoptar.

El relevamiento de 30 sistemas (ver [informe competitivo](../discovery/informe-discovery.md))
muestra que la agenda básica está resuelta por varios competidores (DentalSoft, AgendaPro, MedicAI,
Dentalink, ClinIA; Bilog por declaración propia), pero que casi ninguno publica precios en el segmento dental, ofrece exportación
completa de datos ni trazabilidad de cambios. La competencia real a desplazar es la combinación
planilla + papel + WhatsApp. Por eso el diferenciador elegido no es una función sino una postura:
**transparencia**.

### Posicionamiento frente a la competencia y espacio libre del mercado

Ranking de la matriz ponderada del informe (sobre 5): **DentalSoft 3,65**, **AgendaPro AR 3,40**, **MedicAI 3,05** (no dental), **Dentalink 2,95** (Chile, módulo argentino) y **ClinIA 2,85** (todo afirmación comercial). Bilog (2,30) se priorizó para demo por su trayectoria declarada. Turnito (reserva sin cuenta) y MedicAI (auditoría y exportación) son las referencias de experiencia. **Toda la evidencia es documental o comercial: no hubo demos ni reseñas verificadas** ([10 §NE](10_preguntas_abiertas.md)).

| Vacío del mercado argentino (informe C.3) | Qué hay hoy | Qué cubre v1 |
|-------------------------------------------|-------------|--------------|
| Sobreturnos con control de solapamiento (vacío 5) | Solo DentalSoft; agenda por sillón solo AgendaPro; ninguno ambas | Sobreturno marcado + no-solapamiento ([RN-AG-01](05_reglas_de_negocio.md)); **sin** sillones (RN-AG-13) |
| Autogestión del paciente tras reservar (vacío 6) | Parcial en DentalSoft; el resto, afirmación comercial | Reprogramar y cancelar por link sin cuenta (modelo Turnito) |
| Trazabilidad, auditoría y exportación (vacío 7) | Solo MedicAI (no dental); DentalSoft exporta Excel/PDF | Auditoría append-only y exportación JSON + CSV |
| Normativa argentina explícita (vacío 8) | Solo AgendaPro y MedicAI, como afirmación comercial | Cumplimiento declarado con honestidad ([11](11_marco_legal_argentino.md)) |
| Precios transparentes en el vertical dental (vacío 9) | Solo DentalSoft publica todo, por profesional | Precios públicos en ARS con prueba gratuita |

**Alcance deliberadamente menor que el MVP sugerido por el informe.** El informe (D.3) recomienda como imprescindibles agenda multiprofesional y por sillón, recordatorios por WhatsApp, historia clínica y odontograma, presupuestos, caja con Mercado Pago, obras sociales, roles y ARCA, y advierte que en Argentina "no tener WhatsApp descalifica". El Discovery eligió una **v1 acotada a la agenda de un profesional** y difiere todo eso; el usuario lo **confirmó el 2026-11-20** como decisión intencional (sin multi-profesional, secretaria ni roles). La KB sigue esa decisión y registra el riesgo en [DD-16](09_decisiones_y_supuestos.md) y [SU-02](09_decisiones_y_supuestos.md).

**Pilares de transparencia del diferenciador:**

| Pilar de transparencia | Cómo se materializa en v1 |
|------------------------|---------------------------|
| Precios públicos | Página de precios en ARS, visible sin registrarse, con prueba gratuita |
| Sin encierro | Exportación completa de los datos del consultorio en JSON + CSV, en cualquier momento |
| Trazabilidad | Registro de auditoría append-only de todo cambio sobre turnos (quién, qué, cuándo, valor anterior) |
| Cumplimiento honesto | Política de privacidad que declara qué se cumple y qué no, sin certificaciones ni sellos inventados ([11](11_marco_legal_argentino.md)) |

## Objetivos por actor

| Actor | Objetivo principal | Objetivos secundarios |
|-------|--------------------|-----------------------|
| Odontólogo independiente (único usuario del tenant) | Saber en todo momento cuántos pacientes tiene y a qué hora, sin planilla ni cuaderno | Bloquear horarios y agregar sobreturnos marcados; configurar prestaciones con su duración; reducir mensajes de WhatsApp para coordinar turnos; poder irse del sistema con todos sus datos |
| Paciente (anónimo, sin cuenta) | Reservar un turno desde el teléfono sin llamar ni escribir | Reprogramar o cancelar respetando la anticipación mínima; tener un link de gestión de su turno |
| Negocio (equipo del producto) | Validar que la reserva por link y la transparencia generan adopción paga | Mantener costos de operación bajos; sentar la base técnica para historia clínica (v2) |

## Alcance v1.0

- Registro, login y recuperación de contraseña del odontólogo (un usuario por consultorio).
- Configuración del consultorio: nombre, dirección, zona horaria (`America/Argentina/Buenos_Aires` por defecto), slug del link público, anticipación mínima para cancelar/reprogramar, horizonte máximo de reserva.
- Tipos de prestación con duración configurable (p. ej. consulta 30 min, limpieza 45 min).
- Disponibilidad semanal (franjas por día de la semana).
- Bloqueos de horario puntuales (vacaciones, congresos, trámites).
- Agenda diaria y semanal del profesional, usable en mobile.
- Alta manual de turnos y de **sobreturnos marcados explícitamente** por el odontólogo.
- Prevención de solapamientos entre turnos que no son sobreturno.
- Cambio de estado del turno: reservado → confirmado → ausente / cancelado.
- Página pública de reserva por link (`/c/{slug}`): elegir prestación, ver huecos libres, dejar datos mínimos y reservar.
- Gestión del turno por el paciente mediante un link con token: reprogramar o cancelar con anticipación mínima.
- Registro de auditoría append-only de los cambios sobre turnos, consultable por el odontólogo.
- Exportación completa de datos del consultorio en formato abierto (ZIP con JSON + CSV).
- Página pública de precios en ARS y prueba gratuita (sin cobro integrado en v1).
- PWA instalable y diseño responsivo mobile-first.
- Ejecución local completa con Docker Compose.

## Fuera de alcance

Explícitamente **no** forma parte de la v1 (orden tentativo de llegada según el Discovery):

1. Historia clínica, anamnesis, odontograma FDI versionado, presupuestos y plan de tratamiento — **primer incremento post-v1**.
2. Recordatorios, confirmación y recuperación de ausentes por WhatsApp.
3. Caja, cuenta corriente, señas y cobro con Mercado Pago (incluye el cobro automático de la suscripción).
4. Obras sociales y prepagas: coberturas, prestaciones y liquidación.
5. Facturación electrónica ARCA.
6. Lista de espera automática y recall de controles periódicos.
7. Multi-profesional, múltiples sillones, recepción, roles y permisos.
8. Periodontograma, imágenes y radiografías.
9. Sincronización con Google Calendar.

Tampoco entran: app nativa (iOS/Android), cuenta del paciente, notificaciones por SMS o WhatsApp,
hosting cloud productivo (ver [PA-02](10_preguntas_abiertas.md)), Supabase u otro BaaS.

## Métricas de éxito

| Métrica | Definición | Meta tentativa v1 | Valida |
|---------|------------|-------------------|--------|
| Reservas por link | % de turnos creados por el paciente desde la página pública sobre el total | ≥ 40 % a 60 días de uso | [SU-01](09_decisiones_y_supuestos.md) (el paciente acepta el link) |
| Activación | Consultorios que configuran disponibilidad + ≥ 1 prestación + comparten el link en la primera semana | ≥ 60 % de los registros | Facilidad de onboarding |
| Retención | Consultorios con ≥ 1 turno cargado por semana durante 4 semanas | ≥ 50 % | Que la agenda sola aporte valor ([SU-02](09_decisiones_y_supuestos.md)) |
| Conversión de prueba | Consultorios que pasan de prueba gratuita a plan pago | A definir con el modelo de precios ([PA-03](10_preguntas_abiertas.md)) | [SU-03](09_decisiones_y_supuestos.md) (transparencia como motivo de compra) |
| Solapamientos indebidos | Turnos no-sobreturno superpuestos en base | 0 (invariante cubierta por tests y constraint) | [RN-AG-01](05_reglas_de_negocio.md) |
| Tiempo de reserva | Mediana desde abrir el link hasta confirmar | < 90 s en mobile | UX de la página pública |

> **Hipótesis a validar:** las metas numéricas (incluido el ≥ 40 % de reservas por link) son tentativas, no tienen fuente de mercado y se recalibran tras el piloto con los primeros consultorios. Están registradas como hipótesis en [`discovery.md` §10](../discovery/discovery.md) (definidas al armar la KB, 2026-11-20).
