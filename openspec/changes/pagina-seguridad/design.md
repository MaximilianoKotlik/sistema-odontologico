# Design

## Context

- Ver `proposal.md` para la motivación. C-22 es la página pública `/seguridad` (US-062): cómo se tratan los datos, auditoría inmutable, exportación completa, hosting y lo que todavía no está, sin declarar cumplimiento que no se pueda respaldar (RN-PL-05). Cada afirmación tiene que corresponder a una capacidad implementada y la página muestra la fecha de actualización (RN-PL-06).
- **Lo que ya existe en `main`**: la estructura de páginas públicas de C-21 (`publico.ts` monta `/precios` sin shell ni sesión), los componentes y tokens del sistema de diseño, y el mockup aprobado con su estado `respaldo` (#40), que ya anota el respaldo de cada frase.
- **Capacidades que la página describe**: aislamiento por tenant (C-02), auditoría append-only (C-03), autenticación, bloqueo y rate limit (C-04, C-05), exportación (C-19), consulta de la auditoría (C-18), token del paciente (C-15, C-16) y lo informado en la reserva (DD-38, C-17).
- Base visual: sistema de diseño rev. 1 y la estructura de `/precios`.

## Goals / Non-Goals

**Goals:**

- Que ninguna frase publicada prometa más de lo que el sistema hace, y que eso se verifique de forma automática antes de cada versión.
- Que cambiar un texto sea editar un solo archivo, revisado en un PR.

**Non-Goals:**

- Un CMS o una API para editar el contenido en caliente.
- Traducir la página (la v1 es solo es-AR).
- Resolver PA-02 o los otros bloqueos de publicación.

## Decisions

Las decisiones de contenido y de UI están en `mockups/README.md` §Decisiones, aprobadas por Maxi en la revisión de #40 (2026-11-20): fuente versionada compilada con la página, aviso de "sin revisión legal" primero, "Lo que todavía no está" como sección propia, lenguaje llano, frases condicionadas a changes futuros y proveedores nombrados. También decidió sumar el email de seguridad como `(TBD)`.

Decisiones de implementación:

### D1 — La fuente es un JSON tipado

El contenido vive en `frontend/src/contenido/seguridad.json`: `actualizadoEn` (fecha), el aviso, el índice y los temas, cada uno con sus párrafos e ítems. Cada afirmación es un objeto con `texto`, `respaldo` (lista de referencias: regla, decisión, change o test) y `requiere` (changes que tienen que estar `[x]`). Vite lo importa sin dependencias y un type guard lo valida en los tests.

- **Alternativa descartada**: Markdown. Necesita un parser (dependencia nueva o uno propio) y no tiene dónde guardar el respaldo de cada frase sin inventar una sintaxis.

### D2 — La página se arma al compilar, sin pedidos

La página importa el JSON y arma el DOM con los helpers de C-06. No hay pedido a la API, así que no hay estados de carga ni de error, y funciona con el mismo Service Worker sin cachear nada nuevo. El respaldo y `requiere` nunca se pintan.

### D3 — La revisión es un script, no un test

`npm run seguridad:revisar` (`frontend/scripts/seguridad-revisar.mjs`) lee la fuente y `CHANGES.md`, y falla si:

- alguna afirmación tiene en `requiere` un change que no está `[x]`;
- alguna afirmación no tiene respaldo;
- la fuente tiene marcadores `(TBD)`.

Lista cada problema con la afirmación y el motivo. Es un script aparte y no un test de CI porque, mientras PA-02 siga abierta, fallaría siempre y bloquearía cualquier PR. Lo corre la checklist de release de C-23 antes de publicar.

- **Alternativa descartada**: un test que falle en CI. Frenaría el trabajo de todo el equipo por un bloqueo que solo importa al publicar.

### D4 — Los tests de la página validan la fuente y las frases prohibidas

Los tests Vitest verifican que la página muestra la fecha y "Lo que todavía no está", que su texto coincide con la fuente, que cada afirmación tiene respaldo (este sí corre en CI: no depende de PA-02) y que no aparecen frases prohibidas (certificaciones, cumplimiento legal, "100 % seguro", "contraseña cifrada").

### D5 — Ruta pública y marcadores visibles

`/seguridad` se suma a la tabla de páginas de `publico.ts` (C-21), con el título "Seguridad y datos · {marca}". Los `(TBD)` se muestran en la página tal como en el mockup, para que nadie los confunda con texto final si alguien la abre antes de publicar.

## Risks / Trade-offs

- **[Una afirmación queda vieja después de un cambio de código]** → el campo `requiere` y el script de D3 solo detectan changes sin terminar, no regresiones. Mitigación: la checklist de release pide revisar el respaldo de cada frase contra el código, como exige RN-PL-06.
- **[Se publica con `(TBD)`]** → el script de D3 falla y la página muestra los marcadores a la vista.
- **[Regla dura 16 y PA-02]** → se implementa con `(TBD)` y se bloquea solo la publicación; queda como pregunta para Maxi en el proposal.
- **[Divergencia con C-19]** → si la exportación cambia al implementarse, la frase de "Tus datos son tuyos" se actualiza en la fuente; su `requiere` incluye C-19.

## Migration Plan

Sin migración. Se despliega cuando C-19 esté `[x]`, pero la página se publica recién cuando `npm run seguridad:revisar` pase: PA-02 resuelta, proveedor SMTP elegido, email de seguridad definido y TLS confirmado en el VPS.

## Open Questions

- ¿Implementar con `(TBD)` cumple la regla dura 16, o hay que resolver PA-02 antes de escribir el código? (ver proposal).

## UI Mockups

- Design system revision: 1
- Decision: yes (2026-11-20)
- Mockups: openspec/changes/pagina-seguridad/mockups/ (`seguridad.html`, más `seguridad.css` y `mockup.js`)
- Approval: approved (2026-11-20, by Maxi), versión 1609d16 (#40)
- Preview: rendered (2026-11-20, segunda versión con la revisión de #40); `mockups/previews/*-mobile-390x844.png`, `mockups/previews/*-desktop-1440x900.png`
- Visual verify: pending
