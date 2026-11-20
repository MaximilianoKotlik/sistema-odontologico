# Sistema de diseño (provisorio)

- Mode: system-and-mockups
- Revision: 1
- Approval: approved (2026-11-20, by user)
- Fecha: 2026-11-20

## Fuentes
`knowledge-base/01_vision_y_objetivos.md`, `03_actores_y_roles.md`, `06_funcionalidades.md`, `07_flujos_principales.md`, `08_arquitectura_propuesta.md` (stack: TypeScript + Vite + Tailwind, PWA, mobile-first).

## Archivos
- `tokens.css`: color (incl. estados de turno), tipografía, espaciado, radios, sombras, foco, movimiento (+ `prefers-reduced-motion`), tema oscuro opcional (`data-theme="dark"`).
- `components.html`: botones, campos, chips de estado, tarjeta de turno, agenda del día (turno, sobreturno, bloqueo), selector de horarios del paciente, diálogo/aviso, estado vacío. Abrir directo en el navegador.
- `brand.js`, `brand.md`, `assets/logo.svg`, `previews/`.

## Decisiones abiertas (TBD)
- (TBD) Logo definitivo (hoy un isotipo provisorio rotulado).
- (TBD) Guía de imágenes/ilustración.
- (TBD) Tema oscuro: tokens incluidos, sin revisión visual.

## Cómo reemplazar
- **Nombre**: editar solo la constante `BRAND_NAME` en `design-system/brand.js`; cualquier elemento con `data-brand-name` se actualiza.
- **Logo**: reemplazar `design-system/assets/logo.svg` conservando la ruta.
- **Colores/tipografía**: editar variables en `design-system/tokens.css`. Cambiar un token compartido sube `Revision` y vuelve la aprobación a `pending`.

## Mapeo a Tailwind
Los tokens son variables CSS. En `tailwind.config` se referencian, por ejemplo:
`colors: { primary: 'var(--color-primary)', surface: 'var(--color-surface)', turno: { reservado: 'var(--state-reservado-fg)' } }`, `fontFamily: { sans: 'var(--font-sans)' }`, `borderRadius: { md: 'var(--radius-md)' }`, `boxShadow: { md: 'var(--shadow-md)' }`, `spacing` desde `--space-*`. Importar `tokens.css` en el CSS global de Vite.

## Accesibilidad
Contraste AA (texto >= 4.5:1), foco visible, objetivos táctiles >= 44px, estados de turno con color + ícono + texto/patrón (sobreturno: borde punteado; bloqueo: rayado), movimiento reducido respetado. Sin fuentes remotas (pila del sistema).

## Previews
`previews/mobile-390x844.png` (390 px de ancho, página completa) y `previews/desktop-1440x900.png` (1440 px, página completa).
