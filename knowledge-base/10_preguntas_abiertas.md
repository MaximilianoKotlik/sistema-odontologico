# Preguntas Abiertas

> Fuentes: [`../discovery/discovery.md`](../discovery/discovery.md) §10–§11 y las decisiones técnicas
> del 2026-11-16. Cada pregunta indica qué bloquea y quién decide. Las respuestas se registran
> moviendo la pregunta a [09](09_decisiones_y_supuestos.md) como `DD-NN`.

## Inconsistencias detectadas

### IN-01 — "Sin integraciones externas" vs. recuperación de contraseña por email
**Documento A dice**: v1 sin integraciones externas ([DD-07](09_decisiones_y_supuestos.md)).
**Documento B dice**: v1 incluye recuperación de contraseña del odontólogo, que necesita un canal fuera de banda (email).
**Impacto**: sin SMTP no hay recuperación de contraseña; un odontólogo que la olvida pierde acceso.
**Resolución propuesta**: tratar SMTP como infraestructura genérica, no como integración de negocio: Mailpit en local ([DD-15](09_decisiones_y_supuestos.md)) y un proveedor SMTP estándar en producción (decisión ligada a [PA-02](#preguntas-abiertas-priorizadas)). Confirmar con el usuario.

### IN-02 — "Sobreturno" listado como estado del turno
**Documento A dice**: estados reservado / confirmado / cancelado / sobreturno / ausente.
**Documento B dice**: un sobreturno también puede confirmarse, cancelarse o quedar ausente.
**Impacto**: modelar sobreturno como estado haría imposible cancelar un sobreturno sin perder la marca.
**Resolución propuesta**: marca `es_sobreturno` + 4 estados ([DD-10](09_decisiones_y_supuestos.md)). Ya aplicada en [04](04_modelo_de_datos.md).

### IN-03 — "Precios públicos con prueba gratuita" sin cobro integrado
**Documento A dice**: precios públicos en ARS y prueba gratuita en v1.
**Documento B dice**: sin Mercado Pago ni otro medio de cobro en v1.
**Impacto**: al vencer la prueba no hay forma automática de pagar.
**Resolución propuesta**: activación manual del plan por el equipo (transferencia o factura fuera del sistema) durante el piloto ([RN-PL-04](05_reglas_de_negocio.md)); cobro integrado como change posterior.

## Preguntas abiertas (priorizadas)

| ID | Prioridad | Pregunta | Propuesta por defecto | Bloquea | Decisor |
|----|-----------|----------|-----------------------|---------|---------|
| PA-01 | **Alta** | ¿Cómo se identifica al paciente sin cuenta para evitar duplicados: nombre + teléfono, DNI o código de verificación? | Teléfono E.164 como clave + nombre; verificación por código como opción futura ([DD-09](09_decisiones_y_supuestos.md), **Suposición:** a confirmar) | Modelo de `paciente` y change de reserva pública | Product Owner |
| PA-02 | **Alta** | ¿Dónde se aloja en producción y en qué país residen los datos? ¿Proveedor con región en Argentina o transferencia internacional con garantías? | Seguir local-first hasta tener asesoría legal; elegir proveedor con Postgres gestionado, backups cifrados y región compatible con la Ley 25.326 ([11](11_marco_legal_argentino.md)) | Piloto con pacientes reales y lanzamiento | Product Owner + asesor legal |
| PA-03 | **Alta** | Modelo de precios: ¿plan fijo, por profesional o por sillón? ¿Hay plan gratuito? ¿Cuánto dura la prueba? | Plan único fijo por consultorio + prueba de 30 días, sin plan gratuito permanente (**Suposición**) | Página `/precios` y RN-PL | Product Owner |
| PA-04 | **Alta** | Sin WhatsApp, ¿cómo se notifica al paciente (confirmación, cambios hechos por el odontólogo)? ¿Email transaccional opcional? | v1: link en pantalla + `.ics`; email opcional si el paciente lo deja y hay SMTP (P1) | Alcance de la reserva pública; depende de IN-01 | Product Owner |
| PA-05 | **Alta** | ¿Cómo se valida el supuesto principal (el paciente reserva por link) antes de invertir más? | Piloto con 3–5 consultorios y métrica `origen = LINK` ≥ 40 % a 60 días ([SU-01](09_decisiones_y_supuestos.md)) | Priorización post-v1 (odontograma vs. WhatsApp) | Product Owner |
| PA-06 | **Alta** | ¿Quién da la asesoría legal sobre Leyes 25.326 y 26.529, inscripción ante la AAIP y textos de privacidad/términos? | Contratar revisión puntual antes del piloto; hasta entonces declarar cumplimiento con honestidad, sin certificaciones | Lanzamiento | Product Owner |
| PA-07 | Media | ¿Cómo se da de baja una cuenta y qué pasa con los datos (plazo de retención, borrado, export previo)? | Baja por soporte; export ofrecido antes; borrado definitivo a los 30 días salvo obligación legal | Política de privacidad | Product Owner + legal |
| PA-08 | Media | ¿El odontólogo debe poder recibir aviso cuando un paciente reserva, reprograma o cancela? | En v1, indicador de "nuevos" en la agenda; email opcional si se resuelve IN-01 | UX de agenda | Product Owner |
| PA-09 | Media | ¿Se necesita verificación de email del odontólogo al registrarse? | No en v1 (fricción); sí antes de habilitar cobro | Change de auth (CRITICAL) | Tech Lead + PO |
| PA-10 | Media | ¿Los pacientes cargados manualmente sin teléfono (p. ej. adultos mayores) se permiten? | Teléfono obligatorio también en carga manual; revisar con odontólogos | Modelo de `paciente` | Product Owner |
| PA-11 | Baja | ¿Se permite que el odontólogo personalice la página pública (logo, colores, texto)? | Solo nombre, dirección y teléfono en v1 | — | Product Owner |
| PA-12 | Baja | ¿Nombre comercial y dominio del producto? | — | Landing y emails | Product Owner |
| PA-13 | Baja | ¿2FA para el odontólogo antes de que entre la historia clínica? | Sí, como requisito de v2 (datos de salud más sensibles) | v2 | Tech Lead |

## Cómo cerrar una pregunta

1. El decisor responde (chat, issue o reunión).
2. Se registra como `DD-NN` en [09](09_decisiones_y_supuestos.md) con alternativas y trade-offs.
3. Se actualizan las reglas ([05](05_reglas_de_negocio.md)) y criterios ([06](06_funcionalidades.md)) afectados.
4. Se elimina la fila de esta tabla y se deja referencia cruzada.
