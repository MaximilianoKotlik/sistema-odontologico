# Mockups de C-13 · `frontend-agenda`

`Approval: pending`

- Design system revision: 1
- Preview: rendered (2026-11-20) · capturas de página completa a 390×844 y 1440×900. Sin desborde horizontal en 360, 390 y 1440 px, y sin errores de consola.
- Preparado e implementa: Nacho (trabajo anticipado, paso 3 del plan). El código se escribe cuando C-07 y C-12 estén `[x]` y Maxi apruebe estos mockups.

Son mockups de revisión de la agenda del odontólogo y del detalle de turno. **No es código de producción**: no hay cambios en `backend/` ni en `frontend/`. Las acciones (crear, confirmar, reprogramar, cancelar, ajustar duración, nota y bloqueos) son de C-14: acá solo aparecen sus puntos de entrada.

## Brief

- **Audiencia**: el odontólogo, que mira la agenda muchas veces por día, casi siempre en el celular y entre paciente y paciente.
- **Tarea**: saber de un vistazo a quién atiende ahora, quién sigue, qué huecos le quedan y qué cambiaron los pacientes desde el link (DD-30). En desktop, planificar la semana.
- **Responsive**:
  - Mobile:
    - Día como línea de tiempo cronológica, con una tira de días para cambiar de fecha.
    - Semana como lista agrupada por día.
  - Desktop:
    - Semana como grilla proporcional de 7 columnas.
    - Día como la misma línea de tiempo, en una columna de 720 px.
  - Detalle: una columna en mobile y dos en desktop (datos a la izquierda, nota y acciones a la derecha).
- **Tokens y assets**: importan `design-system/tokens.css` y `design-system/brand.js`. Se usan solo variables del sistema de diseño (rev. 1, sin tokens nuevos):
  - `base.css` es la base aprobada de C-09 (shell de C-06, botones, avisos);
  - `agenda.css` sigue los patrones de `components.html` (chip de estado, tarjeta de turno, slot rayado de bloqueo y borde punteado de sobreturno).

## Pantallas y estados

Cada archivo elige el estado con `?estado=…`. Al pie de cada pantalla hay una franja "Estados del mockup" que solo existe en el mockup. Los datos son del viernes 20 de noviembre a las 12:10, con horario de lunes a viernes de 9 a 13 y de 15 a 19 y sábados de 9 a 13.

| Pantalla | Archivo | Estados (`?estado=`) |
|---|---|---|
| Agenda (`/app/agenda`) | `agenda.html` | `inicio` (día en mobile, semana en desktop), `dia`, `semana`, `sin-cancelados`, `dia-vacio` (sábado sin turnos), `dia-sin-horario` (domingo), `cargando`, `error`, `offline` (agenda guardada), `offline-sin-cache` |
| Detalle (`/app/agenda/turnos/{id}`) | `turno.html` | `confirmado-nota`, `reservado-nuevo` (reservó por el link), `reprogramado` (por el paciente, con el horario anterior), `sobreturno`, `cancelado` (por el paciente, con motivo), `ausente`, `offline`, `cargando`, `no-encontrado` (404) |

Capturas: `previews/<pantalla>-<estado>-mobile-390x844.png` y `previews/<pantalla>-<estado>-desktop-1440x900.png` (38 en total).

Para verlos: abrir el HTML en el navegador, o servir la raíz del repo (`python -m http.server`) y entrar a `/openspec/changes/frontend-agenda/mockups/agenda.html`.

## Decisiones para revisar

1. **El día es una línea de tiempo, no una grilla proporcional** (design D3). En una grilla, un turno de 20 minutos no llega a 44 px y un sobreturno queda en media columna de 150 px. En la lista, cada tarjeta es tocable y legible a 360 px. Entre los turnos aparecen "Libre · 11:30 a 13:00" (de ahí parte C-14 para crear un turno), "No atendés · 13:00 a 15:00" y la marca "Ahora · 12:10".
2. **Semana en mobile como lista por día** (D4). La grilla de 7 columnas no se lee a 360 px. En desktop sí es grilla proporcional, con el horario de atención en blanco y lo de fuera de horario sombreado.
3. **Estados sin depender del color** (D5): cada estado tiene color, ícono y texto. El sobreturno lleva borde punteado y la indicación "Se superpone con 10:30 · Carla F.". El bloqueo es rayado y los cancelados se ven atenuados y tachados. **Ausente** usa los neutros del sistema, sin token propio (aprobado por Maxi). Los **cancelados** se atenúan sin opacidad: fondo neutro, texto muted y nombre tachado, para mantener el contraste AA de sus etiquetas.
4. **"Nuevo"** (DD-30): etiqueta sólida con el tipo de cambio ("reservó", "reprogramó" o "canceló por el link"), punto en la grilla y un aviso con el resumen. **Se marca como visto al abrir el detalle** con `POST /api/v1/turnos/{id}/visto`, que Maxi sumó a C-12.
5. **"Mostrar cancelados"** viene encendido y se recuerda en el dispositivo. El resumen del día no cuenta los cancelados ocultos.
6. **El detalle es una página** (`/app/agenda/turnos/{id}`) y no un panel lateral (D7, aprobado): funciona igual en mobile y desktop y se puede recargar. Muestra solo las acciones válidas para el estado, y "Marcar ausente" recién aparece cuando el turno empezó (US-023).
7. **API del detalle y del visto** (D2, decidido por Maxi): C-12 suma `GET /api/v1/turnos/{id}` con el detalle completo y `POST /api/v1/turnos/{id}/visto`. La agenda sigue liviana, así la caché offline no guarda más datos personales de los necesarios.
7b. **Sobreturnos en la grilla semanal**: el turno base ocupa todo el ancho y el sobreturno se superpone a la derecha, con alto mínimo, ícono "+" y borde punteado, hora y apellido, y un `title` con el resto.
8. **Sin conexión**: la agenda guardada se ve con el aviso y la hora en que se guardó. "Nuevo turno", los huecos libres y las acciones del detalle quedan deshabilitados.

## Datos ficticios

- Nombre del producto: Muelas Felices (DD-34). El logo sigue provisorio, como en el sistema de diseño.
- Pacientes ficticios evidentes ("Ana Ejemplo", "Bruno Prueba", "Carla Ficticia"…), DNI `30.000.0xx`, teléfonos `+54 9 11 0000-00xx` y emails `@example.com`.
