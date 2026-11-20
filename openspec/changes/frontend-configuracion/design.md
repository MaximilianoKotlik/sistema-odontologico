# Design

## Context

- Ver `proposal.md` §Why. Governance **MEDIO**: se implementa con checkpoints. Tiene pantallas, así que el mockup se aprueba antes de codear (`system-and-mockups`).
- **Base disponible al implementar**:
  - C-06 (PR #17): router por History API, cliente HTTP con refresh y Problem Details tipados por `codigo`, store, `ui/` (botón, campo, diálogo, aviso, estado vacío, esqueleto, error recuperable), fechas con `Intl` y la zona del consultorio, y el placeholder `pages/configuracion.ts`.
  - C-07 (mockups en el PR #14): login, registro y guarda de rutas privadas. El login es quien decide si abre el asistente.
  - C-08 (propuesta en el PR #15): 9 endpoints, códigos de error y propiedades extra (`sugerencias`, `dias`) por D6 de C-08.
- **Sin backend todavía**: los tests usan dobles de `fetch` con las respuestas de las specs de C-08.

## Goals / Non-Goals

**Goals:**
- Que un odontólogo recién registrado llegue a compartir su link en menos de 5 minutos desde el celular.
- Que ningún dato inválido llegue a la API cuando se puede detectar en el cliente, sin duplicar reglas que solo conoce el backend (unicidad del slug y del nombre).
- Que cada error de C-08 se muestre en el campo, la prestación o el día al que se refiere.

**Non-Goals:**
- Edición offline: la configuración necesita red, y el Service Worker de C-06 no la cachea.
- Historial de cambios de la configuración (C-18 muestra la auditoría).
- Arrastrar y soltar para ordenar prestaciones.

## Decisions

### D1 — Estructura
- `features/configuracion/`:
  - `api.ts`: funciones tipadas con `api-types.ts` para los 9 endpoints;
  - `validaciones.ts`: funciones puras (`validarSlug`, `validarDuracion`, `validarGrilla`, `validarReglas`) que devuelven errores por campo;
  - `unidades.ts`: horas y días ⇄ minutos;
  - `errores.ts`: código de error de la API → campo y mensaje;
  - `consultorio.ts`, `prestaciones.ts`, `horarios.ts` y `asistente.ts`: vistas con DOM vanilla y `ui/`.
- `pages/configuracion.ts` compone las pestañas y `pages/bienvenida.ts` el asistente.
- Las funciones puras concentran la lógica testeable. Las vistas se prueban con jsdom en los flujos clave.

### D2 — Rutas y pestañas
- Rutas: `/app/configuracion`, `/app/configuracion/prestaciones`, `/app/configuracion/horarios` y `/app/bienvenida`.
- Las pestañas son links (`<a>` con `aria-current="page"`), no un `tablist` de ARIA: cada una es una URL navegable con historial propio, y el patrón de pestañas ARIA es para paneles en la misma página.
- En mobile las pestañas se desplazan en horizontal si no entran. En 360 px entran las tres.
- El asistente se muestra **sin** la navegación del shell: solo la marca, el progreso y "Terminar después". Así el primer ingreso no se distrae con secciones vacías. "Terminar después" lleva a la agenda.

### D3 — Cuándo se abre el asistente
- Después del login (C-07) y al restaurar la sesión, la app pide `GET /api/v1/prestaciones`. Si no hay activas, va a `/app/bienvenida`.
- No se guarda una marca de "onboarding terminado": el criterio es el mismo dato que hace falta para dar turnos (FL-02, "primer login sin prestaciones"). Si después desactiva todas, el asistente vuelve a aparecer, y es correcto porque sin prestaciones no puede dar turnos.
- Siempre arranca en el paso 1, con lo que ya está guardado precargado. Un paso sin cambios avanza con "Siguiente" sin volver a enviarlo.
- *Alternativa*: un campo `onboarding_completo` en el consultorio → agrega un campo a la API de C-08 sin ganar nada.

### D4 — Unidades en el formulario
- Anticipación de cancelación y de reserva en **horas** (enteros), horizonte en **días** y paso en un `<select>` (5, 10, 15, 20, 30).
- Si la API devuelve minutos que no son horas enteras (por ejemplo 90, cargado por otro cliente), el campo muestra minutos con la unidad "minutos" para no redondear en silencio.
- Rango de cancelación: 0 a 168 h (= 10080 min de C-08).
- Anticipación de reserva: 0 o más, y **menor que el horizonte** (horizonte en días × 24 h). Si no, no queda ningún horario para reservar. Se valida cruzando los dos campos, y el mensaje va en el campo de anticipación (decisión de Maxi en #23; C-08 la valida en `ReglasDeReserva`).

### D4b — Teléfono de contacto obligatorio
- Es lo que ve el paciente en la página pública (DD-33), cuando ya no puede cancelar por el link (RN-RP-04) y cuando el consultorio no toma turnos online (RN-RP-11). Por eso es obligatorio en el asistente y en Configuración (decisión de Maxi en #23; C-08 rechaza un teléfono vacío).
- El registro (C-04) puede crear el consultorio sin teléfono: el asistente lo pide en el paso 1.

### D5 — Slug
- Se normaliza mientras se escribe: minúsculas, espacios a guiones y sin tildes. Se valida con las reglas de RN-TE-03: 3 a 40 caracteres de `[a-z0-9-]`, sin guiones al inicio ni al final ni dobles.
- La lista de palabras reservadas **no** se copia al cliente: el backend responde `SLUG_RESERVADO`. Así no hay dos listas que mantener.
- Vista previa del link con el origen real (`location.origin + "/c/" + slug`).
- El aviso de que el link anterior deja de funcionar aparece solo si el slug cambió respecto del guardado.
- Las sugerencias de `SLUG_EN_USO` se muestran como botones (chips). Elegir una solo completa el campo: no guarda sola, para que el odontólogo vea el cambio antes de confirmarlo.

### D6 — Prestaciones
- Alta y edición en un `dialogo` de `ui/` con foco atrapado. Duración: `<input type="number" step="5" min="5" max="480">` más chips de 15, 20, 30, 45 y 60.
- El orden se cambia con botones "Subir" y "Bajar" por fila (WCAG 2.5.7: alternativa a arrastrar). Cada movimiento hace `PUT` de las dos prestaciones que intercambian `orden`. Un error revierte la lista y lo avisa.
- Las inactivas se piden con `incluirInactivas=true` y se muestran en un grupo plegable "Inactivas (n)".
- No hay botón de borrar (RN-AG-07). "Desactivar" pide confirmación con el texto de RN-AG-10.
- Atajos del asistente: Consulta 30', Limpieza 45', Extracción 60' y Control 20' (los mismos del seed de C-08). Cada atajo hace un `POST`. Si responde `PRESTACION_DUPLICADA`, se muestra que ya existe y no es un error.

### D7 — Editor de horarios
- Una tarjeta por día con un `<input type="time" step="300">` de inicio y otro de fin por franja. El teclado y el selector nativos del celular respetan el paso de 5 minutos.
- La validación de la grilla usa intervalos semiabiertos `[inicio, fin)`, igual que D5 de C-08: franjas contiguas válidas.
- "Copiar a…" abre un diálogo con casillas por día. Reemplaza las franjas de los días elegidos.
- El guardado es único para la semana (`PUT` de reemplazo completo de C-08). Una barra fija abajo dice "Tenés cambios sin guardar" con "Descartar" y "Guardar horarios". En desktop la barra queda al pie de la columna de contenido.
- Las horas se muestran y se envían como `"HH:mm"` locales a la zona del consultorio (D10 de C-08). La pantalla aclara la zona solo si no es `America/Argentina/Buenos_Aires`.

### D8 — Errores de la API → interfaz
| Código | Dónde se muestra |
|---|---|
| `VALIDACION` (detalle por campo) | En cada campo informado, más el resumen arriba |
| `SLUG_INVALIDO`, `SLUG_RESERVADO` | En el campo del slug |
| `SLUG_EN_USO` (`sugerencias`) | En el campo del slug, con chips de sugerencias |
| `TELEFONO_INVALIDO` | En el campo del teléfono, con un ejemplo de formato |
| Teléfono vacío (`VALIDACION` en `telefonoContacto`) | En el campo del teléfono: "Poné un teléfono de contacto…" |
| Anticipación de reserva ≥ horizonte (código que defina C-08 en `ReglasDeReserva`; si llega como `VALIDACION`, por campo) | En el campo de anticipación de reserva |
| `ZONA_HORARIA_INVALIDA` | En el selector de zona horaria |
| `DURACION_INVALIDA` | En la duración del diálogo |
| `PRESTACION_DUPLICADA` | En el nombre del diálogo |
| `FRANJAS_SOLAPADAS` (`dias`) | En cada día informado |
| `FRANJA_INVALIDA` | Aviso arriba del editor |
| `CONFLICTO_CONCURRENCIA` (409) | Aviso con "Ver los valores actuales" |
| Red caída / 5xx | `errorRecuperable` de `ui/` con "Reintentar", sin perder lo editado |

### D9 — Cambios sin guardar
- La guarda de navegación del router (C-06) pregunta antes de salir de una pestaña con cambios: diálogo "¿Descartar los cambios?" con "Seguir editando" como opción por defecto.
- `beforeunload` solo mientras hay cambios, para cerrar o recargar la pestaña.
- **Si el router de C-06 no expone una guarda**, se agrega como primera tarea de C-09, con su test.

### D10 — Accesibilidad
- Los errores de campo se asocian con `aria-describedby` y `aria-invalid`. El resumen de errores recibe el foco al fallar el envío (patrón del registro de C-07).
- Las confirmaciones ("Guardamos los cambios", "Agregamos Limpieza", "¡Copiado!") van por una región `aria-live="polite"`.
- El interruptor de reserva pública es un `<button role="switch" aria-checked>` con el estado en texto ("Activa" / "Pausada").
- El progreso del asistente es una lista ordenada con `aria-current="step"`, y cada paso completo tiene ícono ✓ y texto oculto "(completo)".
- Objetivos táctiles ≥ 44 px, incluidos los botones Subir/Bajar y Quitar franja.

## Risks / Trade-offs

- [C-08 todavía no está implementado y sus códigos o propiedades pueden cambiar] → los mensajes están centralizados en `errores.ts`, y los tests usan los ejemplos de sus specs. Si cambian, se ajusta un solo archivo.
- [El router de C-06 puede no tener guarda de navegación] → D9 la agrega con test, sin cambiar el comportamiento existente.
- [Reordenar con dos `PUT` no es atómico: si falla el segundo, quedan dos prestaciones con el mismo orden] → C-08 ordena por `orden` y nombre, así que el listado sigue siendo estable. La pantalla revierte y vuelve a pedir la lista.
- [`input type="time"` se ve distinto en cada navegador] → es la opción más accesible y con mejor teclado en el celular. El mockup muestra la versión de Chrome.
- [El link compartido en el paso 4 no funciona hasta C-16] → la v1 se libera con C-16 (C-23 cierra). En desarrollo, el paso lo aclara solo en el mockup.

## Migration Plan

- Solo frontend. Reemplaza el placeholder de Configuración de C-06. Rollback = no mergear.

## UI Mockups

- Design system revision: 1
- Decision: yes (2026-11-20)
- Mockups: `openspec/changes/frontend-configuracion/mockups/` (`bienvenida.html`, `consultorio.html`, `prestaciones.html`, `horarios.html`, más `config.css` y `mockup.js`)
- Approval: pending
- Preview: rendered (2026-11-20); `mockups/previews/*-mobile-390x844.png`, `mockups/previews/*-desktop-1440x900.png`
- Visual verify: pending
