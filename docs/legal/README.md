# Textos legales (borrador)

> **Estado: BORRADOR · PENDIENTE DE REVISIÓN LEGAL** ([DD-28](../../knowledge-base/09_decisiones_y_supuestos.md)).
> No se publican ni se usan con pacientes reales hasta que los revise un abogado con experiencia en
> datos personales y salud.

| Archivo | Para quién | Dónde se muestra | Se acepta en |
|---|---|---|---|
| [terminos-y-condiciones.md](terminos-y-condiciones.md) | Odontólogo | `/terminos` | Registro (C-04/C-07): `terminos_version` |
| [politica-de-privacidad.md](politica-de-privacidad.md) | Paciente y odontólogo | `/privacidad` | Reserva pública (C-15/C-16): `consentimiento_version` |

Versión de ambos: `2026-11-borrador`. Cuando cambie el texto, cambia la versión y la fecha, y se
actualiza `TERMINOS_VERSION_VIGENTE`.

Fuentes: [`knowledge-base/11_marco_legal_argentino.md`](../../knowledge-base/11_marco_legal_argentino.md)
(roles, LG-01 a LG-10), las reglas RN-RP, RN-PL, RN-EX y RN-AD de
[`05_reglas_de_negocio.md`](../../knowledge-base/05_reglas_de_negocio.md) y las decisiones DD-22 y DD-24 a DD-37.

## Datos que faltan (`[COMPLETAR]`)

- Razón social, CUIT, domicilio y emails de soporte y de privacidad (el dominio está pendiente, DD-34).
- Proveedor y país del VPS, y política de backups (PA-02).
- Proveedor de envío de emails.
- Medio de pago y facturación del plan.
- Días de aviso previo para cambios de precio y de términos.
- Plazo de aviso de incidentes de seguridad.
- Plazo de conservación de datos después de la baja (DD-29).
- Límites de responsabilidad y jurisdicción.
- Tratamiento de turnos de menores.

## Preguntas para el abogado

1. **Roles**: el consultorio como responsable de la base de pacientes y Muelas Felices como encargado (art. 25). ¿Es correcto? ¿El anexo de tratamiento de datos alcanza como contrato de encargo?
2. **Inscripción ante la AAIP**: ¿cada consultorio inscribe su base de pacientes? ¿Muelas Felices inscribe la base de odontólogos?
3. **Conservación después de la baja** (DD-29): hoy los datos se conservan sin plazo. ¿Qué plazo corresponde y qué se hace al vencer?
4. **DNI como dato obligatorio** (DD-24): ¿está justificado para identificar al paciente? ¿Hay que reforzar el aviso?
5. **Datos sensibles**: ¿es correcto tratar los turnos como datos de salud? ¿Cambia el consentimiento que hay que pedir?
6. **Defensa del Consumidor** (Ley 24.240): ¿aplica al odontólogo que contrata el servicio? ¿Qué límites de responsabilidad son válidos?
7. **Transferencia internacional**: según el país del VPS (PA-02), ¿qué garantía usar?
8. **Menores**: ¿cómo se reserva el turno de un menor?
9. **Textos obligatorios de la AAIP**: confirmar la redacción vigente de la sección "Autoridad de control".
10. **Aceptación tácita** de cambios en los términos: ¿es válida o hay que pedir una nueva aceptación expresa?

## Antes de publicar

- [ ] Revisión del abogado y `[COMPLETAR]` resueltos.
- [ ] Borrar las notas `> Para revisar:` y el aviso de borrador.
- [ ] Verificar cada afirmación de seguridad y de cookies contra el código (RN-PL-06).
- [ ] Nueva versión y fecha; actualizar `TERMINOS_VERSION_VIGENTE`.
