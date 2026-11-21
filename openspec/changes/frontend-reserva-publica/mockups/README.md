# Mockups de C-16 · `frontend-reserva-publica`

`Approval: approved (2026-11-20, by Maxi), versión 461d39f (#25)`

- Design system revision: 1
- Preview: rendered (2026-11-20) · 390×844 y 1440×900, revisado también a 360 px
- Preparado por: Octi (dueño de C-16). Implementación cuando C-06 y C-15 estén `[x]`.

Mockups de revisión de todo el flujo del paciente: la reserva por link y la gestión del turno. **No es código de producción**: no hay cambios en `backend/` ni `frontend/`. Governance **MEDIO**.

## Brief

- **Audiencia**: el paciente, sin cuenta, casi siempre desde el celular y desde un link que le pasó el consultorio (por WhatsApp o redes).
- **Tareas**:
  - Reservar en menos de 90 segundos: prestación, día, horario, datos y confirmación.
  - Guardar el link de gestión.
  - Ver, cambiar o cancelar el turno desde ese link.
- **Contrato**: el alcance de C-15 en `CHANGES.md` (sin propuesta OpenSpec todavía), US-030 a US-032, FL-04, FL-05 y RN-RP-01 a RN-RP-11, más las decisiones nuevas:
  - DD-24: identidad por DNI.
  - DD-26: link, `.ics` y email opcional.
  - DD-33: la página muestra solo nombre, dirección y teléfono.
  - DD-34: la marca es "Muelas Felices".
  - DD-37: sin DNI argentino, se reserva llamando.
- **Responsive**: una columna de 560 px como máximo, mobile-first a 360 px. En mobile, la acción principal ("Continuar con…", "Mover al…") queda fija abajo, y la sección reserva el alto de la barra como margen inferior: al llegar al final, el último horario se ve y se toca. En desktop es la misma columna centrada, con la acción en el flujo y sin barra fija.
- **Tokens y assets**: importan `design-system/tokens.css` y `design-system/brand.js`. Los estilos están en `publica.css`, que solo usa variables del sistema de diseño rev. 1. El selector de prestación, día y horario sigue el patrón "Reserva del paciente" de `design-system/components.html`.

## Pantallas y estados

Cada archivo elige el estado con `?estado=…`. Al pie hay una franja "Estados del mockup" con links a todos; esa franja existe solo en el mockup.

| Pantalla | Archivo | Estados (`?estado=`) |
|---|---|---|
| Reserva (`/c/{slug}`) | `reserva.html` | `inicio`, `horarios`, `otra-zona` (consultorio con zona de Córdoba), `dia-sin-horarios`, `datos`, `datos-errores`, `enviando`, `hueco-ocupado` (409), `limite-turnos` (409), `confirmado`, `no-toma-turnos` (RN-RP-11), `limite` (429), `no-encontrado` (404) |
| Gestión del turno (`/t/{token}`) | `gestion.html` | `ver`, `reprogramar`, `reprogramar-confirmar`, `reprogramado`, `hueco-ocupado` (409), `cancelar`, `cancelado`, `fuera-de-plazo` (409), `solo-cancelar` (reserva desactivada), `invalido` (404 uniforme) |

## Decisiones para revisar

1. **Mensajes por código de error** (alcance de C-15):

   | Código | HTTP | Dónde | Texto |
   |---|---|---|---|
   | `HUECO_NO_DISPONIBLE` al reservar | 409 | Vuelve al paso de horarios | "Alguien reservó las 10:30 hace un instante. Actualizamos los horarios: elegí otro." El horario aparece tachado, deshabilitado y con etiqueta para lectores de pantalla. |
   | `LIMITE_TURNOS` | 409 | Paso de datos | "Ya tenés 2 turnos próximos en este consultorio. Para sacar otro, cancelá uno desde el link que guardaste o llamá al…" (RN-RP-08, contado por DNI). |
   | Validación de DNI y celular | 400 | Paso de datos | Resumen arriba con links a cada campo, más el error debajo de cada uno. El DNI se acepta con o sin puntos y se normaliza a dígitos (RN-RP-03). |
   | Consentimiento sin marcar | 400 | Paso de datos | Se valida antes de enviar; la casilla se marca en rojo con texto. |
   | Reserva desactivada o plan `VENCIDO` | — | `/c/{slug}` | "Por ahora este consultorio no toma turnos online" con el botón para llamar. |
   | 429 | 429 | `/c/{slug}` | "Hiciste muchas consultas seguidas. Esperá un minuto y volvé a intentar." |
   | Slug inexistente | 404 | `/c/{slug}` | "No encontramos este consultorio", sin encabezado de consultorio. |
   | `HUECO_NO_DISPONIBLE` al reprogramar | 409 | Gestión | "Ese horario se ocupó hace un instante. Tu turno sigue el martes 24 a las 10:30" (RN-RP-05: conserva el horario original). |
   | `FUERA_DE_PLAZO` | 409 | Gestión | "Ya no podés cambiarlo ni cancelarlo por acá. Faltan menos de 24 horas…" con el botón para llamar. Las acciones se ocultan antes de intentar (`puedeModificar` de FL-05). |
   | Token inválido, vencido o revocado | 404 | Gestión | Un solo mensaje para todos los casos, sin datos del consultorio: "Este link no funciona". |

2. **Sin precio en las prestaciones**: el modelo (`tipo_prestacion`) no tiene precio. Se muestran nombre y duración.
3. **Días según la disponibilidad** (revisión de #25): aparecen todos los días del horizonte de reserva. Los que tienen franjas se pueden elegir, también los sábados; los que no tienen horarios (domingo, o un día ya completo) se muestran con borde punteado, deshabilitados y con el texto "sin turnos" (no solo color). Si el paciente elige un día que se quedó sin horarios, se le ofrece ir al próximo día con horarios.
4. **Horarios agrupados en mañana y tarde**, en pasos de `paso_huecos_min` (15 min por defecto). Siempre se aclara la zona: "Horarios en hora de Buenos Aires, la del consultorio". El nombre sale de `zona_horaria` del consultorio (C-09), no queda fijo; el estado `otra-zona` muestra un consultorio en Córdoba.
5. **Datos**:
   - Nombre, apellido, DNI, celular y email opcional (RN-RP-02). No se piden datos de salud.
   - El texto de ayuda del DNI ofrece llamar al consultorio a quien no tiene DNI argentino (DD-37).
   - La casilla de consentimiento usa el texto de `docs/legal/politica-de-privacidad.md` §Anexo, versión `2026-11-borrador`, sin marcar por defecto.
6. **Confirmación** (RN-RP-06, DD-26):
   - El link `/t/{token}` va en un recuadro destacado, "Guardá este link", que aclara que no se vuelve a mostrar.
   - Botones "Copiar link" y "Agregar al calendario". El `.ics` se genera en el cliente e incluye el link.
   - Se avisa que el consultorio lo va a confirmar (RN-RP-10: nace `RESERVADO`).
7. **Gestión**:
   - Muestra el estado del turno con chip, ícono y texto, más una explicación de qué significa "Reservado".
   - Muestra hasta cuándo se puede cambiar o cancelar (RN-RP-04).
   - Nunca muestra la nota interna.
   - Cambiar y cancelar piden confirmación en un diálogo. Cancelar avisa que el link deja de funcionar y que no se puede deshacer.
8. **Reserva desactivada**: en la gestión solo se ofrece cancelar, más el botón para llamar (RN-RP-11).
9. **Marca**: la página es del consultorio (DD-33). "Muelas Felices" aparece solo en el pie ("Turnos online con…"), tomado de `BRAND_NAME`.

## Decidido por Maxi en la revisión de #25 (2026-11-20)

- **Dominio del link**: queda `agenda.example` hasta que se defina (DD-34).
- **Nombre del paciente en `/t/{token}`**: se muestra completo. Nunca DNI, teléfono ni la nota interna.
- **`LIMITE_TURNOS`**: el mensaje queda como está, con el trade-off anotado en `design.md` (quien escriba un DNI ajeno puede deducir que esa persona tiene turnos).
- **Campos de C-15**: se ajustan cuando se apruebe la propuesta de Mati (#30).

## Datos ficticios

"Consultorio Demo Sur", "Av. Ejemplo 1234, CABA", teléfono `11 0000-0001`; paciente "Ana Ejemplo", DNI `30.000.001`, celular `11 0000-0042`, `ana@example.com`. El token del link es un texto de ejemplo, no un token real. Las fechas (martes 24 y jueves 26 de noviembre de 2026) son de ejemplo.

## Cómo verlo

Abrí `reserva.html` o `gestion.html` directo en el navegador: funcionan con `file://`. Para verlo como en un celular, usá las herramientas de desarrollo (F12, Ctrl+Shift+M) a 360 o 390 px.

## Capturas

`previews/<pantalla>-<estado>-mobile-390x844.png` y `previews/<pantalla>-<estado>-desktop-1440x900.png`, 47 en total.

- Las pantallas son capturas de página completa. En mobile la barra fija aparece al pie de la página, como se ve al llegar al final.
- `reserva-horarios-final-mobile-390x844.png` es el viewport al final de la lista: 18:15 se ve arriba de la barra.
- Los diálogos de confirmar el cambio y cancelar son capturas del viewport.

Verificado con Playwright en cada estado a 360, 390 y 1440 px:

- Sin scroll horizontal.
- Sin errores de consola ni recursos fallidos.
- Todos los botones y campos con al menos 44 px de alto.
- Un solo `h1` visible.
- En mobile, al llegar al final, el último horario no queda tapado por la barra.

## Checklist de revisión para Maxi

- [ ] Flujo completo en menos de 90 segundos: 3 elecciones, 5 campos y 1 casilla.
- [ ] Tabla de mensajes por código de error (decisión 1).
- [ ] Texto del consentimiento igual al borrador legal y sin marcar por defecto.
- [ ] Link de gestión destacado, avisando que se muestra una sola vez.
- [ ] Cambios de la revisión de #25: días según la disponibilidad, zona del consultorio, margen bajo la barra y trade-off de `LIMITE_TURNOS` en `design.md`.
- [ ] Textos en es-AR con voseo; solo tokens rev. 1; foco visible; objetivos ≥ 44 px; estados con ícono + texto; `prefers-reduced-motion`.
- [ ] Sin datos reales.
