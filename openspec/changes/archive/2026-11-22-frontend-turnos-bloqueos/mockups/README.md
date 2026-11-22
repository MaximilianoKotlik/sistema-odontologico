# Mockups de C-14 · `frontend-turnos-bloqueos`

`Approval: approved (2026-11-20, by Maxi), versión `e91e1b8` (#31). El estado `aviso-fuera-de-horario` se sumó después, a pedido de Maxi (#34)`

- Design system revision: 1
- Visual verify: passed (2026-11-22) contra la implementación, con el backend local de C-12 y datos ficticios. Capturas en `verify/` y detalle de los desvíos en `design.md` §UI Mockups.
- Preview: rendered (2026-11-20) · capturas a 390×844 y 1440×900. En los estados con diálogo, la captura mobile muestra el formulario completo. Sin desborde horizontal en 360, 390 y 1440 px y sin errores de consola. Objetivos táctiles de 44 px o más, salvo las casillas de 22 px, que tienen una etiqueta grande que también las activa (mismo patrón que C-07 y C-09).
- Preparado e implementa: Nacho (trabajo anticipado, paso 3 del plan). El código se escribe cuando C-13 esté `[x]` y Maxi apruebe estos mockups.

Son mockups de revisión de las acciones sobre turnos y de los bloqueos. **No es código de producción**: no hay cambios en `backend/` ni en `frontend/`. Los diálogos se abren sobre la agenda y el detalle de turno de C-13 (en revisión en el #27), que aparecen de fondo.

## Brief

- **Audiencia**: el odontólogo, muchas veces con el teléfono en una mano y el paciente del otro lado de la línea.
- **Tarea**: cargar un turno o un sobreturno, confirmarlo, cancelarlo, marcar ausente, moverlo, ajustar cuánto duró, anotar algo y bloquear horarios. Si el backend rechaza algo, que se entienda por qué y qué hacer.
- **Responsive**: en mobile, los formularios son hojas a pantalla completa con el botón principal fijo abajo. En desktop, son diálogos centrados de hasta 560 px. Las confirmaciones cortas (cancelar, ausente, quitar bloqueo) son diálogos chicos en los dos tamaños.
- **Tokens y assets**: importan `design-system/tokens.css` y `design-system/brand.js`. Se usan solo variables del sistema de diseño (rev. 1, sin tokens nuevos):
  - `base.css`, `agenda.css` y `mockup.js` son copias de los mockups de C-13;
  - `c14.css` suma los estilos de formularios y diálogos.

## Pantallas y estados

Cada archivo elige el estado con `?estado=…`. Al pie hay una franja "Estados del mockup" que solo existe en el mockup. Los datos son del viernes 20 de noviembre a las 12:10.

| Pantalla | Archivo | Estados (`?estado=`) |
|---|---|---|
| Nuevo turno y sobreturno (sobre la agenda) | `nuevo-turno.html` | `vacio`, `buscar`, `paciente-nuevo`, `dni-invalido`, `paciente-elegido` (desde el hueco de 12:00), `aviso-superpone` (antes de enviar), `sobreturno`, `solapado` (409), `fuera` (409 `FUERA_DE_DISPONIBILIDAD`), `bloqueado` (409 `HORARIO_BLOQUEADO`), `guardando`, `creado`, `plan-vencido` (403) |
| Acciones sobre el turno (sobre el detalle) | `acciones-turno.html` | `confirmado-ok` (sin diálogo), `cancelar`, `ausente`, `reprogramar`, `reprogramar-solapado` (409), `duracion`, `duracion-solapado` (409), `nota`, `nota-larga`, `transicion` (409 `TRANSICION_INVALIDA`), `concurrencia` (409 `CONFLICTO_CONCURRENCIA`) |
| Aviso de turnos fuera de horario (Configuración › Horarios de C-09, después de guardar) | `aviso-fuera-de-horario.html` | `aviso` (3 turnos), `aviso-muchos` (12: muestra 5 y "Ver los 12 en la agenda"), `cargando-aviso`, `sin-turnos` |
| Bloqueos (`/app/bloqueos`) | `bloqueos.html` | `lista`, `vacio`, `nuevo` (por horas), `nuevo-dias` (todo el día), `fin-invalido`, `con-turnos` (409 `BLOQUEO_CON_TURNOS`), `eliminar`, `eliminado`, `cargando` |

Capturas: `previews/<pantalla>-<estado>-mobile-390x844.png` y `previews/<pantalla>-<estado>-desktop-1440x900.png` (74 en total).

Para verlos: abrir el HTML en el navegador, o servir la raíz del repo (`python -m http.server`) y entrar a `/openspec/changes/frontend-turnos-bloqueos/mockups/nuevo-turno.html`.

## Decisiones para revisar

1. **Cada rechazo con su salida** (design D5):
   - `TURNO_SOLAPADO` y `FUERA_DE_DISPONIBILIDAD` ofrecen **"Crear como sobreturno"**;
   - `HORARIO_BLOQUEADO` no lo ofrece (RN-AG-04);
   - `BLOQUEO_CON_TURNOS` lista los turnos con "Ver turno";
   - `TRANSICION_INVALIDA` y `CONFLICTO_CONCURRENCIA` recargan o llevan a la versión actual.
2. **Aviso antes de enviar**: si la hora elegida se superpone con la agenda cargada, se avisa y se sugiere el sobreturno, pero igual se puede enviar porque el backend decide (D3).
3. **El fin nunca se escribe**: siempre se muestra "Termina 12:30", calculado con la prestación o la duración (RN-AG-12).
4. **Confirmar no abre diálogo** (D2): es la acción más frecuente y se puede revertir cancelando. Cancelar y marcar ausente sí piden confirmación, porque son terminales. El motivo de cancelación es opcional y no lo ve el paciente.
5. **Reprogramar con huecos sugeridos** (D6): los huecos del día donde entra la duración del turno, para elegir con un toque.
6. **Duración con −5 / +5 y atajos** (D7), con el fin nuevo en vivo.
7. **Paciente: un solo buscador** por nombre, DNI o teléfono, y alta rápida en el mismo diálogo, con DNI y celular obligatorios (DD-24, DD-32). **Para revisar**: no se muestra ayuda extra para pacientes sin DNI argentino (DD-37). El criterio queda en el consultorio.
8. **Bloqueos**: por horas o por días completos ("Todo el día"), con motivo opcional de hasta 120 caracteres (04) y confirmación para quitarlos.
9. **API de C-12**: Maxi sumó a su alcance (#34) la búsqueda de pacientes, el listado de bloqueos futuros, el turno con el que choca en `TURNO_SOLAPADO` y los turnos fuera de disponibilidad.
10. **Aviso de turnos fuera de horario** (D10, pedido por Maxi en #34): después de guardar los horarios, si quedan turnos futuros afuera se listan con "Ver turno". No bloquea el guardado ni cuenta los sobreturnos.

## Datos ficticios

- Nombre del producto: Muelas Felices (DD-34).
- Pacientes ficticios evidentes ("Carla Ficticia", "Federico Ejemplo", "Iris Ejemplo"…), DNI `30.000.0xx`, teléfonos `+54 9 11 0000-00xx` y emails `@example.com`.
