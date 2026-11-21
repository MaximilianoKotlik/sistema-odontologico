# Mockups de C-20 · `plan-ciclo-de-vida`

`Approval: approved (2026-11-21, by Maxi), versión 1a8340b (#53)`

- Design system revision: 1
- Preview: rendered (2026-11-21). Capturas de página completa a 390×844 y 1440×900, en `previews/`. Sin desborde horizontal, sin errores de consola y con objetivos táctiles de 44 px.
- Prepara e implementa: Fran. El código se escribe cuando C-09 esté `[x]` y Maxi apruebe la propuesta y estos mockups (governance CRITICO).

Es un mockup de revisión del aviso de plan en el shell de la app. **No es código de producción**: no hay cambios en `backend/` ni en `frontend/`.

## Brief

- **Audiencia**: el odontólogo en prueba gratis o con la prueba vencida.
- **Tarea**:
  - durante la prueba, saber cuántos días le quedan sin que el aviso moleste (US-061);
  - con la prueba vencida, entender que no perdió nada, que puede ver y exportar todo, y cómo seguir (RN-PL-03, RN-EX-03).
- **Dónde va**: arriba del contenido de todas las pantallas privadas, debajo del encabezado. Se muestra sobre la agenda de C-13 porque es la pantalla principal.
- **Tokens y assets**:
  - importan `design-system/tokens.css` y `design-system/brand.js`, y se usan solo variables del sistema de diseño (rev. 1, sin tokens nuevos);
  - `base.css`, `agenda.css` y `mockup.js` son copias de los mockups aprobados de C-13;
  - `plan.css` suma solo la variante discreta del aviso y el motivo de "Solo lectura".

## Pantalla y estados

`aviso-de-plan.html` elige el estado con `?estado=…`. Al pie hay una franja "Estados del mockup" que solo existe en el mockup. Los datos son del viernes 20 de noviembre.

| Estado | Qué se ve | Capturas |
|---|---|---|
| `prueba` | Aviso discreto (info): "Prueba gratis · Te quedan 20 días, hasta el miércoles 9 de diciembre", con "Ver precios" | `prueba-390.png`, `prueba-1440.png` |
| `prueba-ultimos-dias` | Aviso destacado (warn): "Te quedan 5 días de prueba", qué pasa después y las acciones "Ver precios" y "Cómo activar el plan" | `prueba-ultimos-dias-*.png` |
| `ultimo-dia` | "Tu prueba termina hoy", con las mismas acciones | `ultimo-dia-*.png` |
| `vencido` | Aviso persistente de solo lectura con "Exportar mis datos" como acción principal y el contacto `(TBD)` para activar. "Nuevo turno" queda deshabilitado con candado y el texto "Solo lectura"; los huecos libres dicen "solo lectura" y no ofrecen crear | `vencido-*.png` |
| `error-403` | La prueba venció con la app abierta: un error (`role="alert"`) "No se guardó el bloqueo" arriba del aviso de solo lectura | `error-403-*.png` |

## Decisiones para revisar

1. **Aviso durante toda la prueba**: discreto con 8 días o más y destacado en los últimos 7 (pregunta 4 del design).
2. **Color del vencido**: es `warn` y no `danger`, porque no es un error del usuario y no se perdió nada. El `danger` queda solo para el 403 de una escritura que no se guardó.
3. **Contacto para activar**: `(TBD)@example.com` hasta que haya dominio (DD-34, pregunta 2 del design). Queda en una constante, como el nombre de marca.
4. **Solo lectura sin depender del color**: candado y texto "Solo lectura" en el botón deshabilitado, y "solo lectura" escrito en los huecos libres.

## Accesibilidad

- Los avisos de plan son `role="status"` y no roban el foco. El error del 403 es `role="alert"`.
- El botón deshabilitado apunta al aviso con `aria-describedby`.
- Las acciones miden 44 px de alto como mínimo.

## Cómo verlo

Servir la raíz del repo en localhost (los navegadores automatizados bloquean `file:`) y abrir `openspec/changes/plan-ciclo-de-vida/mockups/aviso-de-plan.html?estado=vencido`. Abierto con doble clic también funciona.
