# Preguntas Abiertas

> Fuentes: [`../docs/discovery/discovery.md`](../docs/discovery/discovery.md) §10–§11, el informe competitivo ([`../docs/discovery/informe-discovery.md`](../docs/discovery/informe-discovery.md), puntos "No evidenciado") y las decisiones técnicas
> del 2026-11-16. Cada pregunta indica qué bloquea y quién decide. Las respuestas se registran
> moviendo la pregunta a [09](09_decisiones_y_supuestos.md) como `DD-NN`.

## Inconsistencias detectadas

> Las tres inconsistencias quedaron **resueltas** el 2026-11-20 con decisión confirmada por el usuario (DD-19, DD-20, DD-21). Ya no bloquean ningún change; se conservan acá como trazabilidad.

### IN-01 — "Sin integraciones externas" vs. recuperación de contraseña por email
**Estado**: **Resuelta (2026-11-20)** → [DD-19](09_decisiones_y_supuestos.md#dd-19--smtp-es-infraestructura-no-integración-de-negocio-resuelve-in-01-confirmada-por-el-usuario-2026-11-20).
**Documento A dice**: v1 sin integraciones externas ([DD-07](09_decisiones_y_supuestos.md)).
**Documento B dice**: v1 incluye recuperación de contraseña del odontólogo, que necesita un canal fuera de banda (email).
**Impacto**: sin SMTP no hay recuperación de contraseña; un odontólogo que la olvida pierde acceso.
**Resolución propuesta**: tratar SMTP como infraestructura genérica, no como integración de negocio: Mailpit en local ([DD-15](09_decisiones_y_supuestos.md)) y un proveedor SMTP estándar en producción (decisión ligada a [PA-02](#preguntas-abiertas-priorizadas)). Confirmada por el usuario (DD-19).

### IN-02 — "Sobreturno" listado como estado del turno
**Estado**: **Resuelta (2026-11-20)** → [DD-20](09_decisiones_y_supuestos.md#dd-20--sobreturno-como-marca-es_sobreturno--4-estados-resuelve-in-02-confirmada-por-el-usuario-2026-11-20).
**Documento A dice**: estados reservado / confirmado / cancelado / sobreturno / ausente.
**Documento B dice**: un sobreturno también puede confirmarse, cancelarse o quedar ausente.
**Impacto**: modelar sobreturno como estado haría imposible cancelar un sobreturno sin perder la marca.
**Resolución propuesta**: marca `es_sobreturno` + 4 estados ([DD-10](09_decisiones_y_supuestos.md)). Ya aplicada en [04](04_modelo_de_datos.md).

### IN-03 — "Precios públicos con prueba gratuita" sin cobro integrado
**Estado**: **Resuelta (2026-11-20)** → [DD-21](09_decisiones_y_supuestos.md#dd-21--activación-manual-del-plan-durante-el-piloto-resuelve-in-03-confirmada-por-el-usuario-2026-11-20).
**Documento A dice**: precios públicos en ARS y prueba gratuita en v1.
**Documento B dice**: sin Mercado Pago ni otro medio de cobro en v1.
**Impacto**: al vencer la prueba no hay forma automática de pagar.
**Resolución propuesta**: activación manual del plan por el equipo (transferencia o factura fuera del sistema) durante el piloto ([RN-PL-04](05_reglas_de_negocio.md)); cobro integrado como change posterior.

## Preguntas abiertas (priorizadas)

| ID | Prioridad | Pregunta | Propuesta por defecto | Bloquea | Decisor |
|----|-----------|----------|-----------------------|---------|---------|
| PA-01 | ~~Alta~~ **Resuelta** | ~~¿Cómo se identifica al paciente sin cuenta para evitar duplicados: nombre + teléfono, DNI o código de verificación?~~ | Resuelta el 2026-11-20 → [DD-24](09_decisiones_y_supuestos.md): DNI único por consultorio; el teléfono queda como contacto obligatorio | Modelo de `paciente` (C-10) y reserva pública (C-15, C-16) | Product Owner |
| PA-02 | ~~Alta~~ **Resuelta** | ~~¿En qué país va a estar el VPS de producción y cómo se hacen los backups?~~ | Resuelta el 2026-11-20 → [DD-40](09_decisiones_y_supuestos.md): VPS y backups en Argentina; el proveedor, los backups funcionando, TLS y el SMTP son requisitos previos al lanzamiento (criterio de salida de C-23) | — | Product Owner |
| PA-03 | ~~Alta~~ **Resuelta** | ~~Modelo de precios: ¿plan fijo, por profesional o por sillón? ¿Hay plan gratuito? ¿Cuánto dura la prueba?~~ | Resuelta el 2026-11-20 → [DD-22](09_decisiones_y_supuestos.md): plan único por consultorio, ARS 14.900/mes con IVA, prueba de 30 días sin tarjeta, sin plan gratuito | Página `/precios` (C-21) y RN-PL | Product Owner |
| PA-04 | ~~Alta~~ **Resuelta** | ~~Sin WhatsApp, ¿cómo se notifica al paciente (confirmación, cambios hechos por el odontólogo)? ¿Email transaccional opcional?~~ | Resuelta el 2026-11-20 → [DD-26](09_decisiones_y_supuestos.md): link + `.ics` + email opcional, también para los cambios del odontólogo | Alcance de la reserva pública; IN-01 resuelta (DD-19) | Product Owner |
| PA-05 | ~~Alta~~ **Resuelta** | ~~¿Cómo se valida el supuesto principal (el paciente reserva por link) antes de invertir más?~~ | Resuelta el 2026-11-20 → [DD-27](09_decisiones_y_supuestos.md): sin piloto formal; se lanza y se miden las métricas como seguimiento | Priorización post-v1 (odontograma vs. WhatsApp) | Product Owner |
| PA-06 | ~~Alta~~ **Resuelta** | ~~¿Quién da la asesoría legal sobre Leyes 25.326 y 26.529, inscripción ante la AAIP y textos de privacidad/términos?~~ | Resuelta el 2026-11-20 → [DD-28](09_decisiones_y_supuestos.md): borrador propio de términos y privacidad, después revisión de un abogado | Lanzamiento | Product Owner |
| PA-07 | ~~Media~~ **Resuelta** | ~~¿Cómo se da de baja una cuenta y qué pasa con los datos (plazo de retención, borrado, export previo)?~~ | Resuelta el 2026-11-20 → [DD-29](09_decisiones_y_supuestos.md): la cuenta queda desactivada y los datos se conservan (a validar en la revisión legal) | Política de privacidad | Product Owner + legal |
| PA-08 | ~~Media~~ **Resuelta** | ~~¿El odontólogo debe poder recibir aviso cuando un paciente reserva, reprograma o cancela?~~ | Resuelta el 2026-11-20 → [DD-30](09_decisiones_y_supuestos.md): marca de "nuevo" en la agenda + email opcional | UX de agenda | Product Owner |
| PA-09 | ~~Media~~ **Resuelta** | ~~¿Se necesita verificación de email del odontólogo al registrarse?~~ | Resuelta el 2026-11-20 → [DD-31](09_decisiones_y_supuestos.md): sin verificación en v1; se exige antes del cobro | Change de auth (CRITICAL) | Tech Lead + PO |
| PA-10 | ~~Media~~ **Resuelta** | ~~¿Los pacientes cargados manualmente sin teléfono (p. ej. adultos mayores) se permiten?~~ | Resuelta el 2026-11-20 → [DD-32](09_decisiones_y_supuestos.md): teléfono obligatorio también en carga manual | Modelo de `paciente` | Product Owner |
| PA-11 | ~~Baja~~ **Resuelta** | ~~¿Se permite que el odontólogo personalice la página pública (logo, colores, texto)?~~ | Resuelta el 2026-11-20 → [DD-33](09_decisiones_y_supuestos.md): solo nombre, dirección y teléfono en v1 | — | Product Owner |
| PA-12 | ~~Baja~~ **Resuelta** | ~~¿Nombre comercial y dominio del producto?~~ | Resuelta el 2026-11-20 → [DD-34](09_decisiones_y_supuestos.md): "Muelas Felices"; dominio pendiente | Landing y emails | Product Owner |
| PA-13 | ~~Baja~~ **Resuelta** | ~~¿2FA para el odontólogo antes de que entre la historia clínica?~~ | Resuelta el 2026-11-20 → [DD-35](09_decisiones_y_supuestos.md): 2FA como requisito de la versión con historia clínica | v2 | Tech Lead |
| PA-14 | ~~Media~~ **Resuelta** | ~~¿Un único sillón/box implícito alcanza para el odontólogo independiente objetivo, o hay consultorios con 2 sillones y un solo profesional que necesiten agenda por sillón desde el inicio? (el Discovery lista múltiples sillones como opcional; AgendaPro lo documenta y tab32/ClinIA lo declaran)~~ | Resuelta el 2026-11-20 → [DD-36](09_decisiones_y_supuestos.md): un sillón implícito en v1; multi-sillón después | Cuándo adelantar el change de multi-sillón; modelo de `turno` | Product Owner |
| PA-15 | ~~Media~~ **Resuelta** | ~~¿Cómo se registra a un paciente sin DNI argentino (extranjero con pasaporte, menor sin DNI a mano)?~~ | Resuelta el 2026-11-20 → [DD-37](09_decisiones_y_supuestos.md): DNI obligatorio; quien no lo tenga pide el turno por teléfono | Modelo de `paciente`, reserva pública | Product Owner |

## Puntos "No evidenciado" (NE) del Discovery que importan para decidir

> Fuente: [`informe-discovery.md`](../docs/discovery/informe-discovery.md), metodología (limitaciones), tablas A.1–A.3 y C.
> **NE no equivale a "no existe"**: significa que no apareció en las fuentes consultadas. El Discovery no hizo demos ni
> cuentas de prueba, no accedió a reseñas independientes (Capterra y G2 devolvieron 403) y WebFetch entrega resúmenes
> automáticos. Solo se listan los NE que afectan una decisión de este producto; no se vuelcan todas las celdas NE.
> Cuando un punto se valida, se registra en [09](09_decisiones_y_supuestos.md) o se ajusta [01](01_vision_y_objetivos.md).

### A. Competidores priorizados: qué verificar con demo o cuenta de prueba

| ID | Qué no se pudo verificar | Por qué importa | Cómo validar |
|----|--------------------------|-----------------|--------------|
| NE-01 | **DentalSoft** (1.º, 3,65/5): ARCA, periodontograma, lista de espera y reserva por DNI en uso real; sillones **NE**; cumplimiento de Leyes 25.326/26.529 **NE**; moneda de sus planes ($30.000 / $60.000 por profesional) no aclarada | Es el benchmark directo en sobreturnos + solapamiento, precios públicos y exportación (Excel/PDF); define cuánto vale el diferenciador de transparencia ([SU-03](09_decisiones_y_supuestos.md)) | Demo + cuenta del plan gratuito; probar solapamiento, sobreturno, portal de cancelación y exportación |
| NE-02 | **AgendaPro AR** (2.º, 3,40/5): obras sociales y ARCA **NE**; sobreturnos y solapamiento **NE**; precio real más allá del "desde ARS 13.900"; cumplimiento declarado solo como afirmación comercial; página poco localizada (menciona FONASA, testimonios de otros países) | Único con agenda por sillón y recordatorio con reinsistencia documentados; marca el piso de precio y el modelo de agenda por sillón que v1 no cubre ([RN-AG-13](05_reglas_de_negocio.md)) | Prueba de 14 días sin tarjeta: precios por plan y cantidad de odontólogos, flujo de agenda por sillón |
| NE-03 | **MedicAI** (3.º, 3,05/5; no dental): API con documentación no leída; auditoría inmutable y exportación FHIR documentadas pero no probadas | Referencia de confianza (auditoría + exportación) que v1 replica en versión mínima ([RN-AD-02](05_reglas_de_negocio.md), [RN-EX-01](05_reglas_de_negocio.md)) | Cuenta de prueba de 30 días sin tarjeta; revisar formato del log y de la exportación |
| NE-04 | **Dentalink** (4.º, 2,95/5): ARCA y Mercado Pago **NE**; precio para Argentina no publicado (Capterra US$29 es fuente secundaria); ISO 27001 declarada sin verificar; normativa argentina **NE** | Único regional con localización argentina comprobada; amenaza de actor con escala | Pedir cotización y demo; consultar el certificado ISO |
| NE-05 | **ClinIA** (5.º, 2,85/5): todo es afirmación comercial (RENAPER, PUCO, ARCA, odontograma versionado, "varias sillas", IA que cancela y reprograma); precio no publicado | Si se confirma, es el competidor más peligroso en el segmento institucional; en autogestión del paciente compite con la propuesta de v1 | Demo de 45 min que ofrece el proveedor |
| NE-06 | **Bilog** (8.º, 2,30/5; prioritario por trayectoria declarada): precios, prueba, caja, MP, ARCA y seguridad **NE**; "1.500+ clínicas" autodeclarado | Mayor brecha entre posicionamiento y evidencia; muestra qué espera el cliente que ya usa software | Demo |
| NE-07 | **DentalCore, ZYRA, XIAgenda, Doctoralia AR** (baja confianza, fuera de la matriz): existencia y alcance sin confirmar (sitios caídos, SPA, directorios) | DentalCore, de confirmarse, sería competidor directo (OS + MP + facturación en vertical dental); XIAgenda y Doctoralia mencionan lista de espera y marketplace de pacientes | Revisión manual con navegador antes de cerrar el mapa competitivo |

### B. Capacidades del mercado y supuestos de producto sin verificar

| ID | Qué no se pudo verificar | Por qué importa | Cómo validar |
|----|--------------------------|-----------------|--------------|
| NE-08 | **Reseñas y adopción reales**: no hay reseñas independientes accesibles; las cifras de clientes (Bilog 1.500+, DentalSoft 300+, AgendaPro 30.000+) son autodeclaradas | No se sabe qué satisface o frustra a los usuarios actuales de los competidores | Entrevistas a odontólogos que hoy usan DentalSoft, AgendaPro o planilla; reseñas en Google |
| NE-09 | **Tamaño de mercado**: la cifra de ~70.000 odontólogos en Argentina con ~3 % usando software de gestión viene de una tesis citada por buscador, sin verificar | Sostiene la hipótesis de mercado poco penetrado | Buscar la fuente primaria (tesis UdeSA, colegios odontológicos) |
| NE-10 | **Precios de referencia**: solo DentalSoft publica todos sus planes entre los dentales argentinos; el resto cotiza o publica un "desde"; montos de Doctocliq, Doctoralia y XIAgenda provienen de fuentes secundarias | Insumo de [PA-03](#preguntas-abiertas-priorizadas); sin referencias verificadas el precio de v1 es una suposición | Pedir cotizaciones; releer páginas de precios en la fecha de decidir |
| NE-11 | **Autogestión del paciente** (reprogramar/cancelar): solo DentalSoft la documenta parcialmente (portal de cancelación); Bilog y ClinIA son afirmación comercial | Es parte del diferenciador de v1 y de [SU-01](09_decisiones_y_supuestos.md); si existe mejor de lo documentado, el diferencial se achica | Probar el portal de DentalSoft en demo; comparar fricción con la reserva por link |
| NE-12 | **Exportación y auditoría** en el vertical dental argentino: solo DentalSoft exporta (Excel/PDF) y solo MedicAI documenta auditoría inmutable; no se sabe si el odontólogo las valora | Fundamento de "transparencia" como motivo de compra ([SU-03](09_decisiones_y_supuestos.md)) | Entrevistas: ¿alguna vez quiso migrar de sistema y qué le costó? |
| NE-13 | **Cumplimiento normativo declarado** (Leyes 25.326/26.529): solo AgendaPro y MedicAI lo mencionan, ambos como afirmación comercial sin auditoría externa; DentalSoft, Dentalink y ClinIA **NE** | Define cómo redactar el cumplimiento honesto de v1 sin sobrepromesas ([11](11_marco_legal_argentino.md), [PA-06](#preguntas-abiertas-priorizadas)) | Asesoría legal; leer las políticas de privacidad de los competidores (no leídas en el Discovery) |
| NE-14 | **Eficacia de la IA y de los recordatorios por WhatsApp** de los competidores: 77 % declara IA y 87 % del lote AR + LATAM tiene WhatsApp, sin eficacia verificada | El Discovery señala que en Argentina "no tener WA descalifica"; v1 no lo incluye ([DD-07](09_decisiones_y_supuestos.md), [SU-02](09_decisiones_y_supuestos.md)) | Piloto: medir ausentismo y retención; preguntar en entrevistas si la falta de WhatsApp es un descarte |
| NE-15 | **Costo y operación de las integraciones diferidas**: costo por conversación de WhatsApp (Meta), comisión de Mercado Pago, certificado digital para ARCA; obras sociales con liquidación real y ARCA no están documentadas en ningún vertical dental argentino | Ordena el backlog post-v1 y el modelo de precios | Cotizar Meta Cloud API y Mercado Pago; consulta con contador por ARCA; entrevistas sobre obras sociales |

## Supuestos y riesgos del Discovery a validar (hipótesis sin probar)

| Supuesto / riesgo (Discovery §10) | Dónde se registra | Cómo se valida |
|-----------------------------------|-------------------|----------------|
| El paciente prefiere reservar por link antes que llamar o escribir por WhatsApp (el que más preocupa); incluye la pregunta del Discovery de si el link **reemplaza o complementa** el WhatsApp del consultorio | [SU-01](09_decisiones_y_supuestos.md), [PA-05](#preguntas-abiertas-priorizadas) | Piloto con 3–5 consultorios; métrica `origen = LINK` ≥ 40 % a 60 días; entrevistas a pacientes |
| La agenda sola aporta valor sin recordatorios | [SU-02](09_decisiones_y_supuestos.md) | Retención a 4 semanas y entrevista de salida |
| La transparencia (precios públicos, exportación, auditoría, cumplimiento) motiva la compra frente a DentalSoft o AgendaPro | [SU-03](09_decisiones_y_supuestos.md) | Motivo de elección en el onboarding; demos de los competidores priorizados (NE-01 a NE-06) |
| Riesgo legal: interpretar las Leyes 25.326 y 26.529 sin asesoría | [PA-06](#preguntas-abiertas-priorizadas), [11](11_marco_legal_argentino.md) | Asesoría legal puntual antes del piloto (incluye inscripción ante la AAIP "si corresponde") |
| Riesgo de evidencia: el análisis competitivo se basa en afirmaciones comerciales y puede cambiar con las demos | [SU-07](09_decisiones_y_supuestos.md), NE-01 a NE-08 | Demos de los cinco priorizados antes de fijar precio y posicionamiento definitivos |
| Preguntas propias del Discovery: identificación del paciente sin cuenta; modelo de precios y plan gratuito | [PA-01](#preguntas-abiertas-priorizadas), [PA-03](#preguntas-abiertas-priorizadas) | Ver tabla de preguntas priorizadas |

## Cómo cerrar una pregunta

1. El decisor responde (chat, issue o reunión).
2. Se registra como `DD-NN` en [09](09_decisiones_y_supuestos.md) con alternativas y trade-offs.
3. Se actualizan las reglas ([05](05_reglas_de_negocio.md)) y criterios ([06](06_funcionalidades.md)) afectados.
4. Se elimina la fila de esta tabla y se deja referencia cruzada.
