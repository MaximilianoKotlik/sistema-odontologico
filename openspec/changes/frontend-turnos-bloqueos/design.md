# Design

## Context

- Ver `proposal.md` §Why. Governance **MEDIO**: se implementa con checkpoints. Tiene pantallas, así que el mockup se aprueba antes de codear (`system-and-mockups`).
- **Base disponible al implementar**:
  - C-06: `ui/` (diálogo con foco atrapado, campo, aviso, botón) y cliente HTTP con Problem Details tipados por `codigo`.
  - C-13: agenda, detalle de turno y modelo de la línea de tiempo (huecos libres y disponibilidad). C-14 abre sus diálogos sobre esas pantallas.
  - C-08: prestaciones activas con su duración.
- **Sin backend todavía**: C-12 no tiene propuesta. Los tests usan dobles de `fetch` con los códigos de error del scope de C-12.

## Goals / Non-Goals

**Goals:**
- Cargar el turno de un paciente que llama en menos de 30 segundos, con una mano, desde el celular.
- Que ningún 409 deje al odontólogo sin saber qué hacer: cada rechazo dice por qué y ofrece la salida.
- No mandar al backend lo que el cliente ya sabe que es inválido (múltiplos de 5, fin derivado, rango del bloqueo, DNI, largo de nota y motivo).

**Non-Goals:**
- Validar en el cliente solapamientos y disponibilidad como autoridad: el backend decide. El cliente solo usa la agenda cargada para sugerir huecos.
- Revisión de pacientes (C-17) y edición de sus datos.

## Decisions

### D1 — Estructura
- `features/turnos/`:
  - `api.ts`;
  - `validaciones.ts` (funciones puras): fin derivado, múltiplo de 5, rango de duración, DNI, celular, largo de nota y de motivo;
  - `errores.ts`: código → mensaje, campo y acción ("Crear como sobreturno", "Ver el turno actualizado");
  - `transiciones.ts`: acciones válidas por estado y por hora (RN-AG-06);
  - vistas `nuevo-turno.ts`, `cambiar-estado.ts`, `reprogramar.ts`, `duracion.ts` y `nota.ts`.
- `features/bloqueos/`: `api.ts`, `validaciones.ts`, `errores.ts` y las vistas de listado, alta y baja.
- Los huecos libres para reprogramar salen del `modelo.ts` de C-13, sin duplicarlo.

### D2 — Diálogos sobre la agenda y el detalle
- Todos los formularios usan el `dialogo` de C-06: hoja a pantalla completa en mobile (con el botón principal fijo abajo, al alcance del pulgar) y diálogo centrado de hasta 560 px en desktop.
- Cerrar con cambios pide confirmación, igual que la guarda de C-09.
- Al terminar, el diálogo se cierra, la agenda o el detalle se recargan con `GET` y un aviso `status` confirma la acción ("Turno creado para el viernes 20 a las 12:00").
- **Confirmar no abre diálogo**: es reversible por otro camino (se puede cancelar) y es la acción más frecuente. Cancelar y Marcar ausente sí piden confirmación, porque son terminales (RN-AG-06).

### D3 — Hora y fin
- Día con `<input type="date">` y hora con `<input type="time" step="300">`, como en los horarios de C-09.
- El fin no se edita: se muestra "Termina 12:30 · 30 min" debajo (RN-AG-12). Al cambiar la prestación, se recalcula.
- Si la hora elegida no cae en un hueco libre de la agenda cargada, se avisa antes de enviar ("Ese horario se superpone con 10:30 · Carla F.") y se sugiere marcar Sobreturno. Igual se puede enviar: el backend decide.

### D4 — Paciente
- Un solo buscador por nombre, DNI o teléfono, con resultados a partir de 3 caracteres y "Cargar paciente nuevo" siempre visible.
- Alta rápida en el mismo diálogo: nombre, apellido, DNI (solo dígitos, 7 u 8, con el teclado numérico), celular y email opcional.
- Si el DNI ya existe, el backend reutiliza el paciente sin sobrescribirlo y puede marcar `requiere_revision` (RN-RP-03). El formulario avisa "Ya había un paciente con ese DNI: usamos sus datos".
- **Para revisar**: el texto de ayuda para pacientes sin DNI argentino (DD-37: el odontólogo los carga a mano "con un DNI"). La propuesta es no mostrar ayuda extra y dejar el criterio al consultorio.
- **Para acordar con C-12**: la búsqueda (`GET /api/v1/pacientes?q=`) no está en ningún scope.

### D5 — Errores → interfaz
| Código | Dónde y salida |
|---|---|
| 409 `TURNO_SOLAPADO` | Aviso en el formulario: "Se superpone con el turno de 10:30 de Carla Ficticia" + **Crear como sobreturno**. Al ajustar duración: "No alcanza: a las 10:30 empieza otro turno". |
| 409 `FUERA_DE_DISPONIBILIDAD` | "Ese horario está fuera de tu horario de atención" + **Crear como sobreturno** (FL-03). |
| 409 `HORARIO_BLOQUEADO` | "Ese horario está bloqueado (Reunión con el laboratorio)". Sin sobreturno (RN-AG-04). |
| 409 `BLOQUEO_CON_TURNOS` | Lista de turnos en conflicto con **Ver turno** cada uno. |
| 409 `TRANSICION_INVALIDA` | "Este turno ya cambió de estado" + recarga del detalle. |
| 409 `CONFLICTO_CONCURRENCIA` | "El turno cambió mientras lo editabas" + **Ver el turno actualizado**. |
| 400 duración / nota | En el campo, aunque el cliente ya lo impide. |
| 403 `PLAN_VENCIDO` | "Tu agenda está en solo lectura hasta activar el plan" (C-20 muestra el banner). |
| Red caída / 5xx | `errorRecuperable` con **Reintentar**, sin perder lo cargado. |

**Para acordar con C-12**: que `TURNO_SOLAPADO` incluya el turno con el que choca (como `BLOQUEO_CON_TURNOS` incluye su lista). Si no lo trae, el mensaje queda genérico.

### D6 — Reprogramar
- Diálogo con el turno actual arriba ("Hoy viernes 20 · 16:00 a 16:30"), día, hora y la lista "Huecos libres el viernes" con los inicios posibles, que salen de C-13, alcanzan para la duración y **siguen el paso de huecos del consultorio** (`paso_huecos_min` de C-08, 15 minutos por defecto), revisión de Maxi en #31.
- Al elegir, se ve "Nuevo horario: 16:30 a 17:00". El paciente conserva su link de gestión (RN-AG-11).

### D7 — Ajustar duración
- Botones −5 y +5 de 44 px, el valor grande en el centro, chips de 15, 20, 30, 45 y 60, y "termina 10:45" en vivo. Límites de 5 y 480, con el botón deshabilitado en el extremo.

### D8 — Bloqueos
- `/app/bloqueos`: lista de tarjetas rayadas, igual que en la agenda, con rango y motivo. Por defecto muestra solo los actuales y futuros.
- Alta en diálogo con "Todo el día" (por defecto apagado). Apagado: día, desde y hasta. Encendido: desde el día y hasta el día. Motivo opcional de hasta 120 caracteres (04).
- Un rango de días completos se envía como `[inicio del primer día, inicio del día siguiente al último)` en la zona del consultorio.
- **Para acordar con C-12**: el listado de bloqueos futuros (`GET /api/v1/bloqueos?desde=`). Si no se suma, la pantalla los arma con la agenda de los próximos 90 días.

### D9 — Accesibilidad
- Diálogos con título, foco inicial en el primer campo y vuelta del foco al disparador.
- Los errores de campo van con `aria-describedby` y los avisos de 409 con `role="alert"`.
- El contador de la nota es `aria-live="polite"` y se anuncia solo cada 50 caracteres y al pasarse.
- El selector de duración se puede usar con teclado y el valor nuevo se anuncia.

### D10 — Aviso de turnos fuera de horario
- Vive en la pestaña Horarios de C-09. Después de un `PUT /api/v1/disponibilidad` exitoso, se muestra "Guardamos tus horarios" y se pide `GET /api/v1/turnos/fuera-de-disponibilidad` (C-12, #34). Mientras tanto, "Estamos revisando si algún turno quedó afuera…".
- Si hay turnos afuera: aviso `role="alert"` arriba de la grilla, con la cantidad, la explicación (siguen vigentes; reprogramalos o cancelalos) y hasta 5 turnos con "Ver turno", que lleva al detalle de C-13 donde están las acciones. Con más de 5: "Ver los N en la agenda". El botón "Entendido" lo cierra.
- No bloquea ni deshace el guardado: la grilla ya se guardó. Los sobreturnos no se cuentan, porque pueden quedar fuera de horario (RN-AG-04).
- Si el `GET` falla, el guardado sigue confirmado y el aviso no aparece. Es información, no una condición del guardado.

## Risks / Trade-offs

- [C-12 sin propuesta y tres puntos sin dueño: búsqueda de pacientes, listado de bloqueos y detalle del solapado] → quedan en la tarea 0.4 para acordar antes de C-12. Mientras tanto, `api.ts` aísla el contrato.
- [Sugerir huecos con la agenda cargada puede quedar viejo] → es solo una sugerencia: el backend valida, y un 409 se explica con D5.
- [Confirmar sin diálogo puede dispararse sin querer] → se puede revertir cancelando, y el aviso de "Turno confirmado" lo hace visible.

## Migration Plan

- Solo frontend. Reemplaza el placeholder de bloqueos de C-06. Rollback = no mergear.

## UI Mockups

- Design system revision: 1
- Decision: yes (2026-11-20)
- Mockups: `openspec/changes/frontend-turnos-bloqueos/mockups/` (`nuevo-turno.html`, `acciones-turno.html`, `bloqueos.html`, `aviso-fuera-de-horario.html`, más `c14.css`, y `base.css`, `agenda.css` y `mockup.js` copiados de C-13)
- Approval: pending
- Preview: rendered (2026-11-20); `mockups/previews/*-mobile-390x844.png`, `mockups/previews/*-desktop-1440x900.png`
- Visual verify: pending
