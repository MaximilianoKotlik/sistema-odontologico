# Marco Legal Argentino

> **Advertencia**: este archivo es un mapa técnico para diseñar el sistema, **no es asesoría legal**.
> Fue redactado sin revisión profesional. Antes del piloto con pacientes reales debe validarlo un
> abogado con experiencia en datos personales y salud ([PA-06](10_preguntas_abiertas.md)). Coherente
> con el diferenciador de transparencia, el cumplimiento se declara con honestidad y **sin
> certificaciones ni sellos**.

## Normas relevantes

| Norma | Tema | Aplica a v1 | Aplica a v2+ |
|-------|------|-------------|--------------|
| Ley 25.326 de Protección de Datos Personales y Decreto 1558/2001 | Tratamiento de datos personales; datos sensibles | **Sí** | Sí |
| Decreto 746/2017 | Designa a la **AAIP** (Agencia de Acceso a la Información Pública) como autoridad de control | Sí | Sí |
| Resolución AAIP 47/2018 | Medidas de seguridad recomendadas para el tratamiento de datos | Sí (guía) | Sí |
| Ley 27.483 | Aprueba el Convenio 108 del Consejo de Europa y su protocolo | Sí (marco) | Sí |
| Ley 26.529 de Derechos del Paciente (modif. Ley 26.742) | Derechos del paciente, consentimiento informado, **historia clínica** | Parcial (confidencialidad) | **Sí, plena** al entrar la historia clínica |

## Ley 25.326 — Protección de datos personales

### Roles en el tratamiento

| Rol legal | Quién es en este sistema | Consecuencia |
|-----------|--------------------------|--------------|
| Titular de los datos | El paciente (y el odontólogo respecto de sus propios datos de cuenta) | Ejerce derechos de acceso, rectificación y supresión |
| Responsable de la base | **El odontólogo / consultorio**: decide qué datos de sus pacientes trata y para qué | Responde ante el paciente y ante la AAIP |
| Encargado / prestador de servicios informatizados (art. 25) | **El proveedor del SaaS** | Trata los datos solo por cuenta del responsable, no los usa para otros fines y los destruye o devuelve al terminar el servicio |

Respecto de los datos de cuenta del odontólogo, el proveedor es **responsable**.

### Datos sensibles de salud

- La ley define como sensibles los datos referidos a la salud. **Suposición conservadora:** el solo
  hecho de que una persona tenga un turno en un consultorio odontológico, junto con la prestación
  reservada (p. ej. "extracción"), puede revelar información de salud; el sistema trata todos los
  datos de pacientes como sensibles desde v1.
- Nadie puede ser obligado a proporcionar datos sensibles. Los profesionales de la salud pueden
  tratar datos de salud de sus pacientes respetando el secreto profesional.
- Consecuencia de diseño: **minimización** — en v1 solo nombre, apellido, teléfono y email opcional;
  sin DNI, sin motivo de consulta libre del paciente, sin datos clínicos.

### Consentimiento e información al titular

- El tratamiento requiere consentimiento libre, expreso e informado, por escrito o medio equiparable.
- Al recolectar datos hay que informar: finalidad, destinatarios, existencia de la base y responsable,
  carácter obligatorio u optativo de cada dato, consecuencias de no darlo y cómo ejercer derechos.
- Implementación v1 ([RN-RP-02](05_reglas_de_negocio.md)): checkbox no premarcado en la reserva con
  texto breve + link a la política; se guarda `consentimiento_at` y `consentimiento_version` en `paciente`.

### Derechos del titular (acceso, rectificación, supresión)

| Derecho | Plazo legal de respuesta | Cómo se atiende en v1 |
|---------|--------------------------|-----------------------|
| Acceso | 10 días corridos desde la intimación | El paciente lo pide al consultorio; el odontólogo ve y exporta sus datos (búsqueda por teléfono + exportación) |
| Rectificación / actualización | 5 días hábiles | El odontólogo edita el paciente (`PUT /pacientes/{id}`), auditado |
| Supresión | 5 días hábiles | **Pendiente de diseño**: anonimización del paciente conservando turnos y auditoría con datos seudonimizados. Backlog P1; en v1 se resuelve por soporte ([PA-07](10_preguntas_abiertas.md)) |

El proveedor, como encargado, debe asistir al odontólogo para responder en plazo.

### Seguridad y confidencialidad

- El responsable y el encargado deben adoptar medidas técnicas y organizativas que garanticen
  seguridad y confidencialidad, y guardar secreto profesional.
- Medidas de v1 ([08 §Seguridad](08_arquitectura_propuesta.md#seguridad)): aislamiento por tenant en
  base y aplicación, contraseñas con BCrypt, tokens hasheados, TLS fuera de local, PII enmascarada en
  logs, registro de auditoría append-only, mínimo privilegio en roles de base, backups cifrados
  (pendiente de hosting).

### Registro de bases de datos ante la AAIP

- Las bases de datos con datos personales deben inscribirse en el Registro Nacional de Bases de Datos.
- **Suposición:** cada consultorio (responsable) debe inscribir su base de pacientes y el proveedor
  debe inscribir su base de clientes (odontólogos). El sistema puede facilitar un instructivo, pero
  no inscribe por el usuario. A confirmar con asesoría ([PA-06](10_preguntas_abiertas.md)).

### Transferencia internacional

- Está restringida la transferencia de datos personales a países u organismos que no proporcionen
  niveles de protección adecuados, salvo excepciones (consentimiento expreso, cláusulas contractuales
  modelo aprobadas por la autoridad, entre otras).
- Impacto directo en la decisión de hosting ([PA-02](10_preguntas_abiertas.md)): alojar en un
  proveedor con datos fuera de Argentina exige verificar adecuación del país o firmar cláusulas modelo.
  Por eso el hosting **no** se decide sin asesoría.

## Ley 26.529 — Derechos del paciente e historia clínica

### Qué aplica ya en v1

- Derecho a la **confidencialidad** e **intimidad**: refuerza el tratamiento de los datos de turnos
  como sensibles y el aislamiento estricto entre consultorios.
- La agenda **no** es historia clínica: v1 no registra prestaciones realizadas, diagnósticos ni
  evoluciones. La `nota_interna` del turno se limita a logística (aviso en la UI: "no cargar datos
  clínicos").

### Qué exigirá al entrar la historia clínica (primer incremento post-v1)

| Exigencia | Implicancia de diseño |
|-----------|----------------------|
| La historia clínica es documento obligatorio, cronológico, foliado y completo; su titular es el paciente | Entidad `historia_clinica` única por paciente y consultorio; numeración de asientos |
| Cada asiento con fecha, identificación y firma del profesional | Asientos con autor autenticado, timestamp de servidor; evaluar firma digital/electrónica |
| HC informatizada admitida si se asegura integridad, autenticidad, inalterabilidad, perdurabilidad y recuperabilidad | Versionado append-only de evoluciones y odontograma ([RN-HC-01](05_reglas_de_negocio.md)); hash encadenado de asientos; backups verificados |
| Inviolabilidad; el profesional/establecimiento es depositario | Acceso solo del titular de la cuenta; 2FA ([PA-13](10_preguntas_abiertas.md)); auditoría de lecturas, no solo de escrituras |
| Plazo de conservación mínimo de 10 años | Prohibido el borrado físico; la baja de cuenta no puede destruir HC: exportación obligatoria y política de custodia |
| Derecho del paciente a obtener copia | Exportación por paciente (PDF + formato abierto) en el plazo legal |
| Consentimiento informado para prácticas | Registro de consentimientos versionados por prestación |

## Implicancias concretas para v1

| ID | Obligación derivada | Dónde se cumple |
|----|--------------------|-----------------|
| LG-01 | Minimizar datos del paciente | [04 §Paciente](04_modelo_de_datos.md#paciente-datos-mínimos), [RN-RP-02](05_reglas_de_negocio.md) |
| LG-02 | Consentimiento informado y registrado al reservar | `paciente.consentimiento_at/version`, [FL-04](07_flujos_principales.md) |
| LG-03 | Política de privacidad pública y honesta (`/privacidad`): responsable, encargado, finalidad, datos, derechos, hosting, qué **no** se cumple aún | Frontend, revisada por asesor |
| LG-04 | Términos de servicio con contrato de encargo de tratamiento (art. 25) entre proveedor y odontólogo | `/terminos`, aceptación en registro |
| LG-05 | Auditoría de cambios con autor y fecha | [RN-AD-*](05_reglas_de_negocio.md) |
| LG-06 | Portabilidad y devolución de datos al terminar el servicio | Exportación completa ([RN-EX-*](05_reglas_de_negocio.md)) |
| LG-07 | Medidas de seguridad acordes a datos sensibles | [08 §Seguridad](08_arquitectura_propuesta.md#seguridad) |
| LG-08 | Retención y destrucción definidas | **Suposición:** turnos y auditoría se conservan mientras la cuenta esté activa; tras la baja, 30 días para exportar y luego borrado, salvo obligación legal ([PA-07](10_preguntas_abiertas.md)) |
| LG-09 | Residencia de datos / transferencia internacional evaluada antes de hostear | [PA-02](10_preguntas_abiertas.md) |
| LG-10 | No declarar certificaciones ni "cumplimiento total" sin revisión profesional | Landing, precios y privacidad |
