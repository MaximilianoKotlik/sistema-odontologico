# Términos y condiciones de Muelas Felices

> **BORRADOR · PENDIENTE DE REVISIÓN LEGAL.** No publicar ni usar con pacientes reales hasta que lo
> revise un abogado ([DD-28](../../knowledge-base/09_decisiones_y_supuestos.md)). Lo marcado con
> `[COMPLETAR: …]` todavía no está definido. Las notas `> Para revisar:` son preguntas para el abogado
> y se borran antes de publicar.

- **Versión**: `2026-11-borrador` (es el valor que se guarda al aceptar, `TERMINOS_VERSION_VIGENTE`).
- **Última actualización**: 2026-11-20.

## 1. Quiénes somos

Muelas Felices es un servicio web de agenda de turnos para consultorios odontológicos, prestado por
`[COMPLETAR: razón social]`, CUIT `[COMPLETAR]`, con domicilio en `[COMPLETAR]` (en adelante,
"Muelas Felices", "nosotros"). Contacto: `[COMPLETAR: email de soporte; el dominio está pendiente]`.

Estos términos son el acuerdo entre Muelas Felices y vos, el odontólogo que crea una cuenta
("vos", "el consultorio"). Al crear la cuenta y marcar la casilla de aceptación, los aceptás.

## 2. Qué incluye el servicio

- Una agenda diaria y semanal para cargar y gestionar los turnos de tu consultorio, con bloqueos de
  horario, sobreturnos marcados y duración por prestación.
- Una página pública de tu consultorio donde tus pacientes, sin crear una cuenta, reservan,
  reprograman o cancelan su turno.
- Un registro de cambios (auditoría) que no se puede editar ni borrar.
- La exportación completa de tus datos en cualquier momento.

**Qué no incluye** en esta versión: historia clínica u odontograma, recordatorios por WhatsApp o
SMS, cobros a pacientes, obras sociales ni varios profesionales o sillones en un mismo consultorio.

## 3. Tu cuenta

- Cada consultorio tiene una sola cuenta, de uso personal del odontólogo titular.
- Los datos que cargues al registrarte tienen que ser verdaderos y estar actualizados.
- Cuidás tu contraseña y no la compartís. Si sospechás que alguien entró a tu cuenta, cambiala y
  avisanos.
- Podemos bloquear el ingreso por un rato después de varios intentos fallidos, para proteger la
  cuenta.

> Para revisar: ¿hay que exigir que el titular sea un profesional matriculado? Hoy la matrícula es
> optativa.

## 4. Prueba gratuita, precio y vencimiento

- Todo consultorio nuevo tiene **30 días de prueba gratuita**, sin tarjeta ni ningún medio de pago.
- Después, el servicio cuesta **ARS 14.900 por mes, IVA incluido**, por consultorio. No hay plan
  gratuito permanente. El precio vigente y su fecha de actualización están siempre en `/precios`.
- En esta versión no hay cobro dentro de la app: la activación del plan la hacemos nosotros a mano,
  después de coordinar el pago con vos por `[COMPLETAR: medio de pago y facturación]`.
- Si cambiamos el precio, te avisamos con al menos `[COMPLETAR: días]` días de anticipación por
  email, y el cambio rige desde el período siguiente.
- **Si la prueba o el plan vencen sin renovarse**, tu consultorio pasa a estado "vencido": la
  agenda queda en solo lectura, la página pública deja de tomar turnos (muestra tu teléfono) y los
  pacientes pueden seguir cancelando los turnos que ya tenían. **No se borra ningún dato** y podés
  exportar todo.

## 5. Uso aceptable

Te comprometés a:

- Usar el servicio solo para gestionar los turnos de tu consultorio.
- **No cargar datos clínicos** (diagnósticos, tratamientos, evoluciones) en las notas de los turnos:
  la agenda no es una historia clínica.
- No intentar acceder a datos de otros consultorios ni afectar el funcionamiento del servicio.
- Tratar los datos de tus pacientes según la ley (ver punto 6).

## 6. Los datos de tus pacientes

- **Vos sos el responsable de la base de datos de tus pacientes**: decidís qué datos tratás y para
  qué. Eso incluye informarles cómo usás sus datos, atender sus pedidos de acceso, corrección o
  supresión, y `[COMPLETAR: según la revisión legal, inscribir tu base en el Registro Nacional de
  Bases de Datos de la AAIP]`.
- **Muelas Felices trata esos datos solo por tu cuenta**, como prestador de servicios informatizados
  (art. 25 de la Ley 25.326). Las condiciones están en el
  [Anexo: acuerdo de tratamiento de datos](#anexo-acuerdo-de-tratamiento-de-datos).
- La página pública le pide al paciente nombre, apellido, DNI, teléfono celular y, si quiere, email,
  y le pide que acepte la [política de privacidad](politica-de-privacidad.md) antes de reservar.

## 7. Disponibilidad del servicio

- Hacemos lo razonable para que el servicio funcione todo el tiempo, pero en esta versión **no
  garantizamos un nivel de disponibilidad** (SLA).
- Puede haber cortes por mantenimiento; cuando los podamos prever, te avisamos antes.
- Copias de seguridad: `[COMPLETAR: frecuencia y lugar de los backups (PA-02)]`.

> Para revisar: alcance de la limitación de responsabilidad si un corte impide atender turnos.

## 8. Tus datos son tuyos: exportación

- Podés exportar todos los datos de tu consultorio en cualquier momento, también con el plan
  vencido: consultorio, prestaciones, disponibilidad, bloqueos, pacientes, turnos y auditoría, en
  JSON, CSV y un PDF legible.
- La exportación nunca incluye contraseñas ni tokens.

## 9. Baja de la cuenta

- La baja se pide escribiendo a `[COMPLETAR: email de soporte]`. Antes de darla, te ofrecemos la
  exportación completa.
- Con la baja, la cuenta queda **desactivada**: no se puede ingresar y la página pública deja de
  tomar turnos. Los datos se conservan `[COMPLETAR: plazo de conservación que fije la revisión legal
  (DD-29)]` y podés pedir la reactivación.

> Para revisar: DD-29 conserva los datos sin plazo. ¿Es compatible con la finalidad y con el derecho
> de supresión de la Ley 25.326? Fijar un plazo y qué pasa al vencer (devolución y destrucción, art. 25).

## 10. Propiedad intelectual

El software, la marca Muelas Felices y el diseño del servicio son nuestros. Te damos una licencia de
uso personal, no exclusiva e intransferible mientras tu cuenta esté vigente. Los datos que cargás
son tuyos.

## 11. Responsabilidad

- No somos parte de la relación entre vos y tus pacientes ni respondemos por la atención
  odontológica.
- No respondemos por daños causados por un uso del servicio contrario a estos términos ni por
  información incorrecta que cargues vos o tus pacientes.
- `[COMPLETAR: límites de responsabilidad que defina la revisión legal]`.

> Para revisar: ¿aplica la Ley 24.240 de Defensa del Consumidor a un profesional que contrata el
> servicio para su actividad? Ajustar las cláusulas de responsabilidad según eso.

## 12. Cambios a estos términos

Si cambiamos estos términos, te avisamos por email y en la app con al menos `[COMPLETAR: días]` días
de anticipación. Cada versión tiene una fecha y un identificador. Si seguís usando el servicio
después del cambio, se considera que aceptás la nueva versión; si no estás de acuerdo, podés exportar
tus datos y pedir la baja.

## 13. Ley aplicable y jurisdicción

Estos términos se rigen por las leyes de la República Argentina. Cualquier conflicto se resuelve en
los tribunales ordinarios de `[COMPLETAR: jurisdicción]`.

---

## Anexo: acuerdo de tratamiento de datos

Este anexo regula cómo Muelas Felices trata los datos de tus pacientes por tu cuenta (art. 25 de la
Ley 25.326 y su Decreto reglamentario 1558/2001).

1. **Objeto.** Tratamos los datos de tus pacientes (nombre, apellido, DNI, teléfono, email si lo
   dieron, y sus turnos) solo para prestarte el servicio de agenda y reserva descripto en estos
   términos.
2. **Instrucciones.** Solo tratamos esos datos según tus instrucciones, que son las acciones que hacés
   en la app y la configuración de tu consultorio. **No los usamos para fines propios**, no los
   vendemos, no los cedemos y no los usamos para publicidad.
3. **Datos sensibles.** Como un turno odontológico y su prestación pueden revelar información de
   salud, tratamos todos los datos de pacientes como sensibles.
4. **Confidencialidad.** Las personas de nuestro equipo que puedan acceder a esos datos están
   obligadas a guardar secreto, también después de terminar su relación con nosotros.
5. **Seguridad.** Aplicamos medidas técnicas y organizativas como las que describe la
   [política de privacidad](politica-de-privacidad.md#cómo-protegemos-los-datos).
6. **Prestadores que usamos.** Para prestar el servicio usamos: `[COMPLETAR: proveedor y país del
   servidor VPS (PA-02)]` y `[COMPLETAR: proveedor de envío de emails]`. Si sumamos o cambiamos
   alguno, te avisamos antes.
7. **Ayuda con los derechos de tus pacientes.** Te damos las herramientas para buscar, corregir y
   exportar los datos de un paciente, y te ayudamos a responder sus pedidos dentro de los plazos
   legales.
8. **Incidentes de seguridad.** Si detectamos un incidente que afecte los datos de tus pacientes, te
   avisamos sin demora indebida y en un plazo máximo de `[COMPLETAR: horas]`, con lo que sepamos
   del incidente y las medidas tomadas.
9. **Al terminar el servicio.** Te entregamos los datos mediante la exportación completa y, después
   del plazo de conservación del punto 9 de los términos, los destruimos, salvo que la ley nos obligue
   a conservarlos.
10. **Auditoría.** Ponemos a tu disposición el registro de cambios de tu consultorio y la información
    necesaria para demostrar que cumplimos este anexo.
