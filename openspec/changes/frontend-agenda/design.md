# Design

## Context

- Ver `proposal.md` §Why. Governance **MEDIO**: se implementa con checkpoints. Tiene pantallas, así que el mockup se aprueba antes de codear (`system-and-mockups`).
- **Base disponible al implementar**:
  - C-06 (#17): router, cliente HTTP, store, `fechas.ts` con la zona del consultorio, `ui/` (chip de estado, esqueleto, estado vacío, error recuperable), aviso sin conexión y caché `agenda-v1` (NetworkFirst sobre `GET /api/v1/agenda`).
  - C-07: sesión y guarda de rutas.
  - C-09: la zona horaria y la disponibilidad llegan de la configuración (C-08).
- **Sin backend todavía**: C-12 no tiene propuesta. Los tests usan dobles de `fetch` con la forma de respuesta de D2, que se ajusta a lo que publique C-12.

## Goals / Non-Goals

**Goals:**
- Que en 2 segundos, desde el celular, el odontólogo sepa a quién atiende ahora, quién sigue y qué cambió.
- Que estados, sobreturnos y bloqueos se distingan sin depender del color (WCAG 1.4.1).
- Que la vista funcione igual con la agenda guardada sin conexión.

**Non-Goals:**
- Acciones sobre turnos y bloqueos (C-14). C-13 deja los botones y los huecos tocables como puntos de entrada.
- Arrastrar turnos para moverlos.
- Vista mensual.

## Decisions

### D1 — Estructura
- `features/agenda/`:
  - `api.ts`;
  - `modelo.ts`: funciones puras que arman la línea de tiempo del día (turnos, huecos libres, fuera de horario, ahora), distribuyen los superpuestos en columnas para la grilla y agrupan la semana;
  - `tarjeta.ts`, `linea-de-tiempo.ts`, `grilla-semanal.ts`, `lista-semanal.ts` y `detalle.ts`: vistas con DOM vanilla.
- `pages/agenda.ts` y `pages/turno.ts`. Las funciones puras concentran la lógica y se prueban sin DOM.

### D2 — Lo que la vista necesita de la API (a acordar con C-12)
- `GET /api/v1/agenda?desde&hasta` (instantes UTC). Por cada turno: `id`, `inicio`, `fin`, `duracionMin`, `estado`, `esSobreturno`, `origen`, `prestacion {id, nombre}`, `paciente {id, nombre, apellido}`, `tieneNota`, `canceladoPor` y `cambioPaciente` (`RESERVO` | `REPROGRAMO` | `CANCELO` | `null`, sin ver). Por cada bloqueo: `id`, `inicio`, `fin` y `motivo`. Los cancelados vienen siempre: el filtro es del cliente, para que la caché offline sirva con los dos valores del interruptor.
- **Detalle**: `GET /api/v1/turnos/{id}` con los datos completos (DNI, teléfono, email, nota, motivo de cancelación, horario anterior si el paciente lo reprogramó). No está en el scope de C-12. **Para acordar**: se suma a C-12, o la agenda trae todo y el detalle se arma desde la caché.
- **Visto**: abrir el detalle marca el cambio como visto. **Para acordar**: `POST /api/v1/turnos/{id}/visto` u otro mecanismo de C-12.
- La disponibilidad sale de `GET /api/v1/disponibilidad` (C-08), cacheada en memoria durante la sesión.

### D3 — Vista diaria como línea de tiempo y no como grilla proporcional
- En una grilla proporcional, un control de 20 minutos mide menos de 44 px aunque se use una escala grande, y dos superpuestos quedan en media columna de 150 px. Por eso el día es una **lista cronológica**: cada tarjeta tiene su propio alto y todas son tocables.
- Entre los turnos aparecen filas de "Libre · 11:30 a 13:00" (punto de entrada de C-14 para crear un turno) y "No atendés · 13:00 a 15:00", y la marca "Ahora · 12:10" en su lugar.
- Un sobreturno superpuesto va inmediatamente después del turno con el que se cruza y dice "Se superpone con 10:30 · Carla F.".
- En desktop, la vista Día es la misma lista, en una columna de hasta 720 px.

### D4 — Vista semanal
- **Desktop**: grilla proporcional de 7 columnas, de 08:00 a 20:00 a 1,2 px por minuto (un turno de 20 minutos mide 24 px, el mínimo de WCAG 2.5.8 para puntero). El horario de atención va en fondo de superficie y lo de fuera de horario en `surface-muted`. Los superpuestos se reparten en columnas dentro del día. El rango de horas se ajusta a la disponibilidad y a los turnos de la semana.
- **Mobile**: la grilla de 7 columnas no se lee a 360 px, así que la semana es una lista agrupada por día ("Lunes 16 · 6 turnos") con filas compactas. Tocar el día abre su vista diaria.

### D5 — Estados y marcas visuales (sin tokens nuevos)
| Estado o marca | Visual | Texto e ícono |
|---|---|---|
| Reservado | `state-reservado`, borde izquierdo de 5 px | reloj · "Reservado" |
| Confirmado | `state-confirmado` | ✓ · "Confirmado" |
| Cancelado | `state-cancelado`, opacidad 0,7 | ✕ · "Cancelado" |
| Ausente | `surface-muted` y `text-muted` (no hay token propio) | persona tachada · "Ausente" |
| Sobreturno | borde punteado `state-sobreturno` | + · "Sobreturno" |
| Bloqueo | rayado `state-bloqueo` | candado · "Bloqueo · motivo" |
| Nuevo | etiqueta sólida `primary` | punto · "Nuevo · reservó / reprogramó / canceló por el link" |
| Nota | ícono de nota y texto oculto "con nota" | — |

**Para revisar**: Ausente usa los neutros porque el sistema de diseño rev. 1 no tiene un token de estado para Ausente. Si Maxi prefiere un token propio, se sube la revisión del sistema de diseño.

### D6 — URL y estado de la vista
- `/app/agenda?vista=dia&fecha=2026-11-20` y `?vista=semana&fecha=2026-11-16`. Sin parámetros se aplica D1 de la spec (por ancho y hoy).
- "Mostrar cancelados" se guarda en `localStorage`, con `try` y valor por defecto encendido. Es una preferencia del dispositivo, no un dato.

### D7 — Detalle como página
- `/app/agenda/turnos/{id}` es una página dentro del shell, con "Volver a la agenda", y no un panel lateral. Así funciona igual en mobile y desktop, se puede compartir o recargar, y C-14 abre sus diálogos encima. En desktop se usa en dos columnas: datos a la izquierda y acciones a la derecha.
- El teléfono es un link `tel:` ("Llamar"). El DNI se muestra con separador de miles (12.345.678) solo para leer.

### D8 — Sin conexión
- Usa la caché `agenda-v1` de C-06: si `GET /api/v1/agenda` cae a la caché, se muestra con el aviso y la hora del encabezado `Date`.
- "Nuevo turno", las acciones del detalle y los huecos "Libre" quedan deshabilitados.
- Si no hay caché para ese rango: "No tenés guardada la agenda de esta fecha" con "Reintentar" (spec `pwa-offline`).

### D9 — Accesibilidad
- Cada tarjeta es un link con nombre accesible completo, por ejemplo: "10:30 a 11:30, Carla Ficticia, Extracción, Confirmado, con nota".
- La línea de tiempo es una lista ordenada. La grilla semanal expone por día una lista en orden de hora, y las horas de la grilla son decorativas.
- El aviso de nuevos es un `status`. Cambiar de día anuncia el día nuevo y deja el foco en el título.
- Objetivos ≥ 44 px en mobile y ≥ 24 px en la grilla de desktop (WCAG 2.5.8). Sin animaciones con `prefers-reduced-motion`.

## Risks / Trade-offs

- [C-12 sin propuesta: la forma de la respuesta puede cambiar] → D2 queda como contrato propuesto y el mapeo vive en `api.ts`. Los dos puntos abiertos (detalle y visto) se acuerdan con Fran antes de implementar C-12.
- [Una agenda muy cargada hace larga la lista del día] → la marca de ahora y el botón "Hoy" llevan al momento actual. Las filas de libre y fuera de horario son de una línea.
- [La grilla semanal con muchos superpuestos se vuelve angosta] → con un solo sillón (DD-36), lo normal es un turno más algún sobreturno, y nunca más de 3 columnas.
- [Ausente sin token propio] → D5, para revisar.

## Migration Plan

- Solo frontend. Reemplaza el placeholder de agenda de C-06. Rollback = no mergear.

## UI Mockups

- Design system revision: 1
- Decision: yes (2026-11-20)
- Mockups: `openspec/changes/frontend-agenda/mockups/` (`agenda.html`, `turno.html`, más `agenda.css` y `mockup.js`)
- Approval: pending
- Preview: rendered (2026-11-20); `mockups/previews/*-mobile-390x844.png`, `mockups/previews/*-desktop-1440x900.png`
- Visual verify: pending
