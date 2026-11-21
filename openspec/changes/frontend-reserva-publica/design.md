# Design

## Context

- Ver `proposal.md` para la motivación. C-16 es el frontend público del paciente: `/c/{slug}` para reservar y `/t/{token}` para ver, reprogramar o cancelar, sin cuenta (`CHANGES.md` §C-16, FL-04 y FL-05).
- **Contrato**: la propuesta de C-15 (#30, en `main`) fija los endpoints, los nombres de los campos y el cuerpo de los errores en sus specs `reserva-publica` y `gestion-turno-paciente`:
  - `GET /api/v1/public/consultorios/{slug}` → nombre, dirección, teléfono, `zonaHoraria`, `tomaTurnosOnline` y prestaciones (id, nombre, duración).
  - `GET .../huecos?prestacionId&desde&hasta` (hasta 31 días) → `{ zonaHoraria, dias: [{ fecha, atiende, huecos: [instante UTC] }] }`.
  - `POST .../turnos` con `aceptaPrivacidad` → 201 con el turno y `linkGestion`; 409 `HUECO_NO_DISPONIBLE` (con `huecos` del día), `LIMITE_TURNOS`, `RESERVA_NO_DISPONIBLE`; 400 `VALIDACION`, `DNI_INVALIDO`, `TELEFONO_INVALIDO`.
  - `GET /api/v1/public/turnos/{token}` → turno, paciente (nombre), consultorio, `puedeModificar`, `puedeReprogramar`, `modificableHasta` y, si está cancelado, `canceladoPor`.
  - `GET .../turnos/{token}/huecos`, `POST .../reprogramacion` (`nuevoInicio`) y `POST .../cancelacion`; 409 `FUERA_DE_PLAZO` (con `telefonoContacto`), `HUECO_NO_DISPONIBLE`, `RESERVA_NO_DISPONIBLE`, `CONFLICTO_CONCURRENCIA`; 404 uniforme.
- **Lo que ya existe**: la página pública `/precios` de C-21 (`publico.ts` monta páginas sin shell ni sesión), el cliente HTTP de C-06 (sin `Authorization` en `/api/v1/public/**`, errores como `ApiError` con `codigo`), los helpers de fechas en la zona que se pase (`shared/fechas.ts`) y el Service Worker, que no cachea `/api/v1/public/**` ni la gestión por token y excluye `/c/` y `/t/` del fallback de navegación.
- Decisiones de la KB que condicionan la UI: DD-24 (DNI), DD-26 (link, `.ics` y email opcional), DD-33 (solo nombre, dirección y teléfono del consultorio), DD-34 (marca), DD-37 (sin DNI argentino, se reserva llamando) y el consentimiento de `docs/legal/politica-de-privacidad.md` §Anexo.

## Goals / Non-Goals

**Goals:**

- Reservar desde el celular en menos de 90 segundos: 3 elecciones, 5 campos y 1 casilla.
- Que cada error de C-15 tenga un mensaje que explique qué hacer, sin revelar datos de otros (404 uniforme, `LIMITE_TURNOS` sin fechas).
- Mostrar siempre los horarios en la zona del consultorio, nunca en la del dispositivo.

**Non-Goals:**

- Guardar el token o el turno en el dispositivo para "ver mis turnos": el link es la única llave (RN-RP-06).
- Funcionar sin conexión: las páginas públicas necesitan red, igual que en C-06.
- Compartir por WhatsApp (DD-18).

## Decisions

En la revisión de #25 (2026-11-20) Maxi resolvió:

- **Dominio del link**: queda `agenda.example` hasta que se defina el dominio (DD-34, pendiente).
- **Nombre del paciente en `/t/{token}`**: se muestra completo, para que la persona confirme que es su turno. Nunca DNI, teléfono ni la nota interna.
- **Mensaje de `LIMITE_TURNOS` (aceptado con su trade-off)**: queda "Ya tenés 2 turnos próximos en este consultorio…". **Trade-off**: el límite se cuenta por DNI (RN-RP-08, DD-24), así que quien escriba el DNI de otra persona puede deducir que esa persona tiene turnos en el consultorio. Maxi lo acepta porque un mensaje genérico confundiría a quien de verdad llegó al límite. **Para C-15 (Mati)**: el 409 `LIMITE_TURNOS` no tiene que devolver fechas, horarios ni datos de esos turnos, solo el código; y conviene que el rate limit del endpoint de reserva haga caro probar DNI en serie. C-15 lo recogió en su design (riesgo "Revelación por `LIMITE_TURNOS`").
- **Días según la disponibilidad**: la tira muestra todos los días del horizonte de reserva; los que tienen franjas se pueden elegir (también los sábados) y los que no, se ven como "sin turnos".
- **Zona horaria del consultorio**: "Horarios en hora de …" sale de la zona del consultorio (C-09), no queda fija en Buenos Aires.

Decisiones de implementación:

### D1 — Rutas públicas con parámetros en `publico.ts`

`publico.ts` (C-21) hoy resuelve rutas exactas (`/precios`). Se le suman patrones con parámetro, `/c/:slug` y `/t/:token`, reutilizando la compilación de patrones del router de C-06 en vez de escribir otra. Las páginas públicas siguen sin shell, sin sesión y sin `Authorization`.

- **Alternativa descartada**: montarlas dentro de la SPA del odontólogo con el router de la app. Arrastraría el shell, la sesión y el refresh de C-06, que el paciente no necesita.

### D2 — Un paso por vez en la misma página, con estado en memoria

La reserva es una sola página con estado en memoria (prestación, día, horario, datos), sin rutas por paso. Volver atrás desde los datos conserva lo elegido; un 409 `HUECO_NO_DISPONIBLE` vuelve a horarios conservando los datos escritos. Recargar la página empieza de nuevo, que es aceptable para un flujo de menos de 90 segundos.

- **Alternativa descartada**: guardar el avance en `sessionStorage`. Dejaría datos personales del paciente en el navegador sin necesidad.

### D3 — Días en ventanas de 14

La API acepta rangos de hasta 31 días y el horizonte puede ser de hasta 365. La tira pide los primeros 14 días y suma la ventana siguiente con "Ver más días" mientras la última ventana haya traído algún hueco. Cada día se pinta según `atiende` y la cantidad de huecos: con huecos se puede elegir, sin huecos se ve "sin turnos".

- **Alternativa descartada**: pedir 31 días de una. Es más lento en el celular y la mayoría de los pacientes reserva en la primera semana.

### D4 — Zona horaria y envío de instantes

Las fechas y horas se presentan con los helpers de `shared/fechas.ts` usando `zonaHoraria` de la respuesta. El horario elegido se guarda como el instante UTC que devolvió la API y se envía tal cual (`inicio`, `nuevoInicio`): nunca se reconstruye desde la hora local. El nombre de la zona para el aviso ("Buenos Aires", "Córdoba") sale de una tabla chica de zonas argentinas, con el identificador IANA como respaldo.

### D5 — Validación en el cliente igual a la del backend

DNI: se quitan puntos y espacios y tiene que quedar con 7 u 8 dígitos. Celular: el mismo criterio de celular argentino que usa C-02 para normalizar a E.164. Email: formato simple, opcional. Consentimiento: obligatorio. Se valida al enviar (no mientras escribe) y, si el backend igual responde 400, se muestra el error en el campo que corresponda. El backend sigue siendo la fuente de verdad (RN-GL-04).

### D6 — `.ics` generado en el cliente

El archivo se arma como texto (`BEGIN:VCALENDAR`… con CRLF y `UID` derivado del id del turno), con `DTSTART`/`DTEND` en UTC, el consultorio como `LOCATION` y el link de gestión en `DESCRIPTION`, y se descarga con un `Blob`. Sin librerías.

### D7 — El token no sale de la URL ni del texto en pantalla

En la confirmación, el link se muestra y se copia, pero el token no se guarda en ningún almacenamiento. En `/t/{token}`, el token solo vive en la URL y en memoria. Como viaja en la ruta, las páginas `/c/` y `/t/` se sirven con `Referrer-Policy: no-referrer` desde nginx, así un link saliente (mapa, `tel:`) no lo filtra. Hoy nginx no envía esa cabecera: se suma en este change.

### D8 — Errores por código en un solo lugar

Cada feature traduce el `codigo` del `ApiError` a un estado de pantalla en una función pura con tests (`HUECO_NO_DISPONIBLE`, `LIMITE_TURNOS`, `RESERVA_NO_DISPONIBLE`, `FUERA_DE_PLAZO`, `CONFLICTO_CONCURRENCIA`, 404, 429, `SIN_CONEXION`). Un `CONFLICTO_CONCURRENCIA` en la gestión recarga la vista del turno y avisa que cambió.

## Risks / Trade-offs

- **[C-15 cambia un nombre al implementar]** → los tipos salen de `api-types.ts` regenerado y TypeScript marca la diferencia; los specs de este change se ajustan en el mismo PR.
- **[El paciente pierde el link]** → la confirmación lo destaca, ofrece copiarlo y el `.ics` lo incluye; si dejó email, C-15 se lo manda. Sin el link, llama al consultorio (FL-05).
- **[Celular viejo o conexión lenta]** → sin librerías nuevas, carga por ventanas de días y el bundle público no incluye el shell de la app.
- **[Recarga a mitad del flujo]** → se pierde lo elegido (D2). Es un flujo corto y no se guardan datos personales en el navegador.

## Migration Plan

Sin migración de datos. Se despliega junto con C-15 o después: sin los endpoints de C-15, `/c/{slug}` muestra el error de carga reintentable. El cambio de nginx (D7) es aditivo.

## Open Questions

- Dominio definitivo del link público (DD-34). No cambia el diseño: el link se arma con el origen del sitio.

## UI Mockups

- Design system revision: 1
- Decision: yes (2026-11-20)
- Mockups: openspec/changes/frontend-reserva-publica/mockups/ (`reserva.html`, `gestion.html`, más `publica.css` y `mockup.js`)
- Approval: approved (2026-11-20, by Maxi), versión 461d39f (#25)
- Preview: rendered (2026-11-20, segunda versión con la revisión de #25); `mockups/previews/*-mobile-390x844.png`, `mockups/previews/*-desktop-1440x900.png`
- Visual verify: pending
