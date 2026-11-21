# Proposal

> Change **C-16** de [CHANGES.md](../../../CHANGES.md) · Governance **MEDIO** · Depende de `C-06` (`frontend-shell-pwa`, `[x]`) y `C-15` (`reserva-publica-backend`) · Responsable: Octi · Aprobación: Maxi.
>
> Es trabajo anticipado. Los mockups ya están aprobados (#25, versión `461d39f`) y la propuesta de C-15 está en `main` (#30), con los nombres de los campos y el cuerpo de los errores fijados en sus specs. El código se escribe recién cuando C-15 esté `[x]`.

## Why

El diferenciador central de la v1 es que el paciente reserve, reprograme o cancele su turno solo, sin cuenta y sin llamar (US-030 a US-032). C-15 expone la API pública, pero sin pantallas el paciente no tiene por dónde entrar: el link `/c/{slug}` que comparte el consultorio y el link `/t/{token}` que recibe el paciente no muestran nada. C-16 es la cara del producto para el paciente, y la parte con más riesgo de abandono: tiene que poder reservar desde el celular en menos de 90 segundos.

## What Changes

- **Página de reserva `/c/{slug}`** (feature `reserva-publica/`), pública y sin el shell del odontólogo:
  - Encabezado del consultorio con nombre, dirección y teléfono (DD-33).
  - Tres pasos en una sola página: prestación (nombre y duración, sin precio), día y horario, según `GET /api/v1/public/consultorios/{slug}` y `/huecos`.
  - La tira de días muestra todo el horizonte. Los días con `atiende: false` o sin huecos se ven como "sin turnos", con borde punteado y texto. Los horarios se agrupan en mañana y tarde, en la zona horaria del consultorio (`zonaHoraria`), con el aviso "Horarios en hora de …, la del consultorio".
  - Paso de datos: nombre, apellido, DNI (con o sin puntos), celular, email opcional y la casilla de consentimiento con el texto de `docs/legal/politica-de-privacidad.md` §Anexo, sin marcar. Validación en el cliente antes de enviar, igual a la del backend (RN-GL-04).
  - Confirmación: el link `/t/{token}` destacado ("Guardá este link, no lo vamos a volver a mostrar"), con "Copiar link" y "Agregar al calendario" (`.ics` generado en el cliente con el link adentro, DD-26). Aviso de que el consultorio lo va a confirmar (RN-RP-10).
  - Estados de error por código: `HUECO_NO_DISPONIBLE` (vuelve a horarios con los huecos actualizados del 409), `LIMITE_TURNOS`, `DNI_INVALIDO`, `TELEFONO_INVALIDO`, `VALIDACION`, `RESERVA_NO_DISPONIBLE`, 429 con `Retry-After` y 404 del slug.
  - Con `tomaTurnosOnline: false`, la página dice "Por ahora este consultorio no toma turnos online" con el botón para llamar (RN-RP-11).
- **Gestión del turno `/t/{token}`** (feature `gestion-turno/`):
  - Muestra el turno (día, hora, prestación, estado con chip, ícono y texto), el nombre del paciente y el consultorio, y hasta cuándo se puede cambiar o cancelar (`modificableHasta`). Nunca DNI, teléfono ni la nota interna.
  - Las acciones dependen de `puedeModificar` y `puedeReprogramar`: reprogramar (misma tira de días y horarios, con `/turnos/{token}/huecos`) y cancelar, cada una con un diálogo de confirmación.
  - Fuera de plazo (`FUERA_DE_PLAZO` o `puedeModificar: false`): mensaje con el botón para llamar a `telefonoContacto`. Con la reserva desactivada: solo cancelar.
  - Un turno `CANCELADO` se ve sin acciones, con quién lo canceló (`canceladoPor`). `HUECO_NO_DISPONIBLE` al reprogramar avisa que el turno conserva su horario (RN-RP-05).
  - Cualquier 404 muestra un único "Este link no funciona", sin datos del consultorio.
- **Ruteo público**: las dos rutas se suman a las páginas públicas de `publico.ts` (C-21), sin sesión ni `Authorization`. El token nunca se guarda en el navegador.
- **Contrato**: los tipos salen de `api-types.ts`, regenerado cuando C-15 publique sus endpoints.
- **Tests Vitest** de las validaciones (DNI, celular, email, consentimiento), los estados de error por código, la zona horaria del consultorio y el `.ics`.

**Fuera de alcance**:
- La API pública, el token y el email de confirmación (C-15).
- El aviso al odontólogo de los cambios del paciente (DD-30, C-12/C-13).
- La revisión de pacientes marcados (C-17).

## Capabilities

### New Capabilities

- `pantalla-reserva-publica`: la página `/c/{slug}` donde un paciente sin cuenta elige prestación, día y horario, deja sus datos y recibe una sola vez el link para gestionar su turno.
- `pantalla-gestion-turno`: la página `/t/{token}` donde el paciente ve su turno y lo reprograma o cancela dentro del plazo, sin revelar nada ante un link inválido.

### Modified Capabilities

- Ninguna. `pwa-offline` ya prohíbe guardar `/api/v1/public/**` y la gestión por token, y `cliente-api` ya excluye `Authorization` en las rutas públicas.

## Impact

- **Código**: `frontend/src/features/reserva-publica/**`, `frontend/src/features/gestion-turno/**`, las páginas en `frontend/src/pages/`, la tabla de páginas públicas de `frontend/src/publico.ts` y `frontend/src/shared/api-types.ts` regenerado. Sin cambios en `backend/`.
- **Dependencias**: ninguna nueva. El `.ics` se arma a mano (texto plano con CRLF) y el copiado usa `navigator.clipboard`.
- **Coordinación**:
  - **C-15 (Mati)**: este change consume exactamente los nombres de sus specs (`tomaTurnosOnline`, `zonaHoraria`, `dias[].atiende`, `dias[].huecos`, `aceptaPrivacidad`, `linkGestion`, `puedeModificar`, `puedeReprogramar`, `modificableHasta`, `canceladoPor`, `telefonoContacto`, `nuevoInicio`). Si cambian al implementar, se ajustan los specs de este change.
  - **C-21**: reutiliza la estructura de páginas públicas de `publico.ts`.
- **Correcciones de documentación**: CHANGES.md §C-15 todavía menciona el `.ics`, que pasa a C-16 (ya anotado en la propuesta de C-15).
- **Preguntas abiertas**: no toca ninguna Alta. El dominio del link sigue siendo `agenda.example` hasta DD-34.
