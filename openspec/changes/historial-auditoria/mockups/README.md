# Mockups de C-18 · `historial-auditoria`

`Approval: pending`

- Design system revision: 1
- Preview: rendered (2026-11-20).
  - 24 capturas a 390×844 (del tamaño de la ventana, para que la navegación inferior fija no tape contenido) y a 1440 de ancho con la ventana del alto de la página. Así la barra lateral llega hasta abajo, igual que en las capturas de C-13.
  - Sin desborde horizontal en 360, 390 y 1440 px, y sin errores de consola.
- Preparado e implementa: Mati (trabajo anticipado). El plan del equipo le asignaba el mockup a Octi. El código se escribe cuando C-12 y C-13 estén `[x]` y Maxi apruebe estos mockups y la propuesta.

Son los mockups de revisión de la sección **Registro de cambios** (`/app/auditoria`) y de la tarjeta **Historial** del detalle del turno. **No es código de producción**: no hay cambios en `backend/` ni en `frontend/`.

## Brief

- **Audiencia**: el odontólogo que quiere resolver una duda con un paciente ("yo no cancelé", "el turno era a las 10") o revisar quién tocó su consultorio y cuándo.
- **Tarea**: encontrar rápido un cambio y entenderlo sin conocer el modelo: quién, qué, sobre qué turno y qué valor tenía antes.
- **Responsive**:
  - Mobile: filtros en una tarjeta arriba (fechas en dos columnas y el tipo de cambio a todo el ancho), y la lista de eventos debajo con el encabezado de cada día fijo al scrollear. Se llega desde la hoja "Más".
  - Desktop: filtros en una fila y lista de hasta 860 px.
  - El detalle del turno conserva el layout aprobado de C-13. La tarjeta "Historial" va en la columna izquierda, debajo de "Turno".
- **Tokens y assets**:
  - Importan `design-system/tokens.css` y `design-system/brand.js`.
  - `base.css`, `agenda.css` y `mockup.js` son copias de los aprobados de C-13 (que vienen de C-09, C-07 y C-06).
  - `auditoria.css` suma solo el bloque del registro, con variables del sistema de diseño (rev. 1, sin tokens nuevos).
  - `turno-historial.html` se genera a partir del estado "reprogramado" de `frontend-agenda/mockups/turno.html`, más la tarjeta nueva.

## Pantallas y estados

Cada archivo elige el estado con `?estado=…`. Al pie hay una franja "Estados del mockup" con links a todos los estados. Esa franja existe solo en el mockup.

| Pantalla | Archivo | Estados (`?estado=`) |
|---|---|---|
| Registro de cambios (`/app/auditoria`) | `registro.html` | `normal` (todos, agrupados por día, con "Cargar más"), `turno` (chip del turno al llegar desde el detalle), `accion` (rango y tipo "Accesos", con IP), `rango-invalido`, `sin-resultados` (con "Quitar filtros"), `vacio` (consultorio sin cambios todavía), `cargando`, `error` (con "Reintentar"), `offline` |
| Detalle del turno (`/app/agenda/turnos/{id}`) | `turno-historial.html` | `historial` (reservó por link y reprogramó, con "Ver en el registro de cambios"), `historial-cargando`, `historial-error` (el resto del detalle se ve igual) |

Capturas: `previews/<pantalla>-<estado>-mobile-390x844.png` y `previews/<pantalla>-<estado>-desktop-1440x900.png` (24 en total).

Para verlos: servir la raíz del repo (`python -m http.server`) y entrar a `/openspec/changes/historial-auditoria/mockups/registro.html`.

## Decisiones para revisar

1. **Más nuevo primero en el registro y cronológico en el historial del turno** (design Open Question 1). El registro se lee como actividad reciente. El historial cuenta la vida del turno de principio a fin, como pide US-040.
2. **Cada evento en lenguaje simple**:
   - El verbo depende del actor: "Reprogramaste un turno" o "Reprogramó por el link".
   - El actor va con etiqueta de texto ("Vos", "Paciente", "Sistema") y no solo con el color del ícono.
   - El contexto del turno es un link al detalle ("Turno de Carla Ficticia · lun 23/11 11:00").
3. **"Antes → después"** con el valor anterior tachado y el nuevo en negrita. Para lectores de pantalla se anuncia "antes …, después …" (texto `.sr`), y la flecha queda oculta. El alta de un turno muestra solo el valor nuevo.
4. **Tipo de cambio agrupado por tema** (`optgroup`): Turnos, Bloqueos, Configuración, Pacientes, Accesos y Exportaciones, en lenguaje de usuario y no con los códigos (design D6).
5. **Filtro por turno como chip removible**. Se llega con "Ver en el registro de cambios" desde el detalle. Los filtros viven en la URL (design D8).
6. **IP visible solo en los accesos** (design Open Question 3), en tipografía mono y discreta.
7. **Un intento de sesión fallido** usa el ícono en tono de alerta, para que se destaque al recorrer la lista. Se muestra con el actor **"Sin sesión"**, porque C-05 lo registra como `ODONTOLOGO` sin id, nunca como "Sistema", con el número de intento y la IP (revisión de Maxi).
8. **El error del historial no rompe el detalle**: la tarjeta muestra su propio error con "Reintentar".
9. **Sin conexión**: el registro no se cachea (tiene datos personales), así que se muestra el aviso y no hay filtros.

## (TBD) y datos ficticios

- Nombre: **Muelas Felices** (DD-34), desde `BRAND_NAME` de `design-system/brand.js`. El logo sigue provisorio (isotipo de diente de C-06).
- Datos ficticios: "Dra. Ejemplo", "Consultorio Demo Sur", los pacientes de los mockups de C-13 (Hernán Muestra, Carla Ficticia, Elena Demo) e IPs de documentación (`203.0.113.0/24` y `198.51.100.0/24`, RFC 5737).
