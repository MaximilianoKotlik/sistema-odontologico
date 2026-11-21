# Design

> **Borrador parcial.** Por ahora este documento solo registra el gate de mockups de C-16. `proposal.md`, `specs/` y `tasks.md`, más el resto de este diseño (features `reserva-publica/` y `gestion-turno/`, cliente de la API pública, `.ics` en el cliente, Service Worker sin cachear respuestas públicas y tests Vitest), se completan antes de implementar, cuando C-06 y C-15 estén `[x]`.

## Context

- C-16 es el frontend público del paciente: `/c/{slug}` para reservar y `/t/{token}` para ver, reprogramar o cancelar, sin cuenta (ver `CHANGES.md` §C-16, FL-04 y FL-05).
- Contrato que consume: los endpoints públicos de C-15 (`GET /api/v1/public/consultorios/{slug}`, `.../huecos`, `POST .../turnos`, `GET/POST /api/v1/public/turnos/{token}`) y sus errores `HUECO_NO_DISPONIBLE`, `LIMITE_TURNOS`, `FUERA_DE_PLAZO`, 404 uniforme y 429. C-15 todavía no tiene propuesta OpenSpec.
- Decisiones de la KB que condicionan la UI:
  - DD-24: el paciente se identifica por DNI.
  - DD-26: el turno se guarda con el link, un `.ics` y un email opcional.
  - DD-33: la página solo muestra nombre, dirección y teléfono del consultorio.
  - DD-34: la marca es "Muelas Felices".
  - DD-37: quien no tiene DNI argentino reserva llamando.
  - El texto de consentimiento sale de `docs/legal/politica-de-privacidad.md` §Anexo.
- Base visual: sistema de diseño rev. 1, con el patrón "Reserva del paciente" de `components.html`.

## Decisions

Las decisiones de UI que salen de los mockups están en `mockups/README.md` §Decisiones para revisar. En la revisión de #25 (2026-11-20) Maxi resolvió:

- **Dominio del link**: queda `agenda.example` hasta que se defina el dominio (DD-34, pendiente).
- **Nombre del paciente en `/t/{token}`**: se muestra completo, para que la persona confirme que es su turno. Nunca DNI, teléfono ni la nota interna.
- **Mensaje de `LIMITE_TURNOS` (aceptado con su trade-off)**: queda "Ya tenés 2 turnos próximos en este consultorio…". **Trade-off**: el límite se cuenta por DNI (RN-RP-08, DD-24), así que quien escriba el DNI de otra persona puede deducir que esa persona tiene turnos en el consultorio. Maxi lo acepta porque un mensaje genérico confundiría a quien de verdad llegó al límite. **Para C-15 (Mati)**: el 409 `LIMITE_TURNOS` no tiene que devolver fechas, horarios ni datos de esos turnos, solo el código; y conviene que el rate limit del endpoint de reserva haga caro probar DNI en serie.
- **Días según la disponibilidad**: la tira muestra todos los días del horizonte de reserva; los que tienen franjas se pueden elegir (también los sábados) y los que no, se ven como "sin turnos".
- **Zona horaria del consultorio**: "Horarios en hora de …" sale de `zona_horaria` del consultorio (C-09), no queda fija en Buenos Aires.
- **Campos de C-15**: se ajustan cuando se apruebe la propuesta de Mati (#30).

## Open Questions

- Nombres finales de los campos de C-15 (`puedeModificar` y el cuerpo del 409 con los huecos actualizados): se ajustan con la propuesta de Mati (#30).
- Dominio definitivo del link público (DD-34).

## UI Mockups

- Design system revision: 1
- Decision: yes (2026-11-20)
- Mockups: openspec/changes/frontend-reserva-publica/mockups/ (`reserva.html`, `gestion.html`, más `publica.css` y `mockup.js`)
- Approval: pending
- Preview: rendered (2026-11-20, segunda versión con la revisión de #25); `mockups/previews/*-mobile-390x844.png`, `mockups/previews/*-desktop-1440x900.png`
- Visual verify: pending
