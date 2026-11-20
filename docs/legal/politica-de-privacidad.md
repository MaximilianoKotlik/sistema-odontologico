# Política de privacidad de Muelas Felices

> **BORRADOR · PENDIENTE DE REVISIÓN LEGAL.** No publicar ni usar con pacientes reales hasta que lo
> revise un abogado ([DD-28](../../knowledge-base/09_decisiones_y_supuestos.md)). Lo marcado con
> `[COMPLETAR: …]` todavía no está definido. Las notas `> Para revisar:` son preguntas para el abogado
> y se borran antes de publicar.

- **Versión**: `2026-11-borrador` (es el valor que se guarda en `consentimiento_version` al reservar).
- **Última actualización**: 2026-11-20.

Esta política explica qué datos personales se tratan en Muelas Felices, para qué, quién es
responsable y cómo ejercer tus derechos según la Ley 25.326 de Protección de Datos Personales.

## Quién es responsable de tus datos

Depende de quién seas:

| Si sos… | Responsable de tus datos | Rol de Muelas Felices |
|---|---|---|
| **Paciente** que reserva un turno | **El consultorio** donde reservás: decide qué datos pide y para qué | Los trata solo por cuenta del consultorio, como prestador de servicios informatizados (art. 25, Ley 25.326) |
| **Odontólogo** con cuenta en Muelas Felices | **Muelas Felices**: `[COMPLETAR: razón social]`, CUIT `[COMPLETAR]`, domicilio `[COMPLETAR]` | Responsable |

Contacto de Muelas Felices para temas de privacidad: `[COMPLETAR: email de privacidad]`.

## Si sos paciente

### Qué datos se piden y por qué

| Dato | ¿Obligatorio? | Para qué |
|---|---|---|
| Nombre y apellido | Sí | Saber quién es el turno |
| DNI | Sí | Identificarte en el consultorio sin confundirte con otra persona, por ejemplo con un familiar que comparte tu teléfono |
| Teléfono celular | Sí | Que el consultorio pueda contactarte por tu turno |
| Email | No | Mandarte la confirmación y avisarte si el consultorio cambia o cancela tu turno |
| Turno: día, horario y prestación | Sí | Reservar y gestionar el turno |

**Si no das los datos obligatorios**, no podés reservar por la página. Podés pedir el turno llamando
al consultorio.

No se piden datos de salud. Igual, como un turno odontológico y su prestación pueden revelar
información sobre tu salud, **todos tus datos se tratan como sensibles**.

### Para qué se usan

Solo para gestionar tus turnos con ese consultorio: reservarlos, confirmarlos, reprogramarlos,
cancelarlos y contactarte por ellos. **No se usan para publicidad, no se venden y no se comparten**
con otros consultorios ni con terceros, salvo los prestadores técnicos que se indican más abajo o
si lo exige una autoridad competente.

### Tu link de turno

Al reservar recibís un link personal para ver, reprogramar o cancelar tu turno. Guardalo y no lo
compartas: quien lo tenga puede gestionar ese turno. El link deja de funcionar cuando termina el
turno o si lo cancelás.

### Tus derechos

Podés pedir **acceso** a tus datos, su **rectificación** o actualización y su **supresión**.
Como el responsable es el consultorio, **el pedido se hace al consultorio** (su teléfono está en su
página de reservas). Muelas Felices lo ayuda a responder en los plazos legales: 10 días corridos
para el acceso y 5 días hábiles para la rectificación o supresión.

## Si sos odontólogo

### Qué datos tratamos

- **Datos de tu cuenta**: nombre, apellido, email, matrícula (si la cargás) y tu contraseña, que
  guardamos solo cifrada con un algoritmo de una sola vía (nunca la podemos ver).
- **Datos de tu consultorio**: nombre, dirección, teléfono de contacto, horarios, prestaciones y
  configuración.
- **Datos de uso y seguridad**: fecha y hora de tus ingresos, intentos fallidos, la dirección IP
  desde la que operás y el registro de cambios de tu consultorio.
- **Aceptación de los términos**: qué versión aceptaste y cuándo.

### Para qué los usamos

Para prestarte el servicio, proteger tu cuenta, contactarte por temas del servicio (por ejemplo,
recuperar tu contraseña o avisarte de cambios en el precio o en estos textos) y coordinar la
activación de tu plan. No los usamos para publicidad de terceros ni los vendemos.

### Base del tratamiento

Tu consentimiento al crear la cuenta y la necesidad de los datos para cumplir el contrato
([términos y condiciones](terminos-y-condiciones.md)).

### Tus derechos

Podés pedirnos acceso, rectificación o supresión de tus datos escribiendo a
`[COMPLETAR: email de privacidad]`. Además, podés corregir la mayoría de tus datos desde la app y
exportar todos los datos de tu consultorio en cualquier momento.

## Para todos

### Quién más interviene

Para prestar el servicio usamos estos prestadores, que solo tratan los datos para eso:

| Prestador | Para qué | País |
|---|---|---|
| `[COMPLETAR: proveedor del servidor VPS]` | Alojar la aplicación y la base de datos | `[COMPLETAR (PA-02)]` |
| `[COMPLETAR: proveedor de email]` | Enviar emails del servicio | `[COMPLETAR]` |

`[COMPLETAR según PA-02: si algún dato se aloja fuera de Argentina, indicar el país y la garantía
que se usa para la transferencia internacional (país con protección adecuada o cláusulas
contractuales).]`

### Cómo protegemos los datos

> Antes de publicar, cada punto de esta lista se verifica contra lo que está implementado (RN-PL-06).
> Si algo todavía no está, se saca de la lista.

- Cada consultorio solo ve sus propios datos: el aislamiento se controla en la aplicación y en la
  base de datos.
- Las contraseñas se guardan cifradas (BCrypt) y los tokens de sesión, de recuperación y de los
  links de turno se guardan solo como huella (hash).
- Las conexiones viajan cifradas (HTTPS).
- Los registros técnicos no muestran datos personales de pacientes.
- Todo cambio sobre turnos, bloqueos, configuración y pacientes queda en un registro de auditoría
  que nadie puede editar ni borrar, ni siquiera nosotros desde la aplicación.
- La cuenta se bloquea por un rato después de varios intentos fallidos de ingreso, y hay límites
  de pedidos para evitar abusos.

**Lo que todavía no tenemos**, dicho con honestidad: verificación en dos pasos (2FA) para el
odontólogo y `[COMPLETAR: backups definidos (PA-02)]`. Esta política todavía no tiene revisión legal
profesional y no declaramos certificaciones.

### Cuánto tiempo se conservan los datos

- Mientras la cuenta del consultorio esté activa o vencida, se conservan todos los datos del
  consultorio y de sus pacientes. Ningún dato se borra por vencimiento del plan.
- Si el odontólogo da de baja la cuenta, queda desactivada y los datos se conservan
  `[COMPLETAR: plazo de conservación que fije la revisión legal (DD-29)]`.
- El registro de auditoría no se borra mientras existan los datos del consultorio.

> Para revisar: DD-29 conserva los datos sin plazo después de la baja. Definir un plazo compatible
> con la finalidad y con el derecho de supresión.

### Cookies y almacenamiento en tu dispositivo

- **Pacientes**: la página de reservas no usa cookies.
- **Odontólogos**: usamos una sola cookie técnica, necesaria para mantener tu sesión iniciada de
  forma segura. La app también guarda en tu dispositivo una copia de la última agenda que viste,
  para que la puedas consultar sin conexión; se borra al cerrar la sesión.
- No usamos cookies de publicidad ni de analítica de terceros.

### Menores de edad

`[COMPLETAR según la revisión legal: quién reserva el turno de un menor (madre, padre o tutor) y con
qué datos.]`

> Para revisar: los turnos de menores. ¿Alcanza con que el adulto reserve con los datos del menor y
> acepte la política en su nombre?

### Cambios a esta política

Si cambiamos esta política, publicamos la nueva versión con su fecha. Los pacientes la aceptan en su
próxima reserva y los odontólogos reciben un aviso por email y en la app.

### Autoridad de control

> Para revisar: confirmar el texto vigente exigido por la AAIP (Disposición DNPDP 10/2008) antes de
> publicar.

El titular de los datos personales tiene la facultad de ejercer el derecho de acceso a los mismos en
forma gratuita a intervalos no inferiores a seis meses, salvo que se acredite un interés legítimo al
efecto conforme lo establecido en el artículo 14, inciso 3 de la Ley N° 25.326.

La Agencia de Acceso a la Información Pública, en su carácter de Órgano de Control de la Ley
N° 25.326, tiene la atribución de atender las denuncias y reclamos que interpongan quienes resulten
afectados en sus derechos por incumplimiento de las normas vigentes en materia de protección de
datos personales.

---

## Anexo: texto de la casilla de consentimiento al reservar

Se muestra sin marcar, junto al botón de reservar, con un link a esta política:

> Acepto que **[nombre del consultorio]** use mis datos (nombre, apellido, DNI, teléfono y, si lo
> dejo, mi email) para gestionar este turno, según la [política de privacidad](/privacidad).

Al reservar se guarda la fecha y la versión aceptada (`consentimiento_at`, `consentimiento_version`).
