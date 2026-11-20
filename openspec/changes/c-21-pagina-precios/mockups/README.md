# Mockups de C-21 · `pagina-precios`

`Approval: pending`

Mockup de revisión para la página pública `/precios`. **No es código de producción**: no hay cambios en `backend/` ni `frontend/`.

## Pantallas cubiertas

| Pantalla / estado | Archivo | Alcance |
|---|---|---|
| `/precios` — vista pública sin autenticación, con importe pendiente | `precios.html` | Página completa: encabezado, plan, leyenda de prueba, CTA, fecha de actualización y apéndice de estados |
| `/precios` — cargando | `precios.html` (sección «Estados previstos») | Bloque del plan reemplazado por «Cargando precios…», sin importes |
| `/precios` — error reintentable | `precios.html` (sección «Estados previstos») | Mensaje «No pudimos cargar los precios.» + botón «Reintentar», sin importes inventados |

## Fuentes referenciadas

- **Sistema de diseño aprobado**: `design-system/README.md` — modo `system-and-mockups`, `Revision: 1`, `Approval: approved (2026-11-20, by user)`. Se usan `design-system/tokens.css`, `design-system/brand.js` (`BRAND_NAME` / `[data-brand-name]`), `design-system/components.html` como patrón de componentes y `design-system/assets/logo.svg`. **No se modifican tokens ni la revisión.**
- **Knowledge base**: `knowledge-base/05_reglas_de_negocio.md` (RN-PL-01, RN-PL-02, RN-PL-04) y `knowledge-base/06_funcionalidades.md` (US-060).
- **Change C-21**: `proposal.md`, `specs/public-plans/spec.md`, `design.md`, `tasks.md`.

### Decisiones abiertas reflejadas como provisionales

- **PA-03 (Alta)**: importes sin decidir → se muestra `Importe (TBD) — PA-03`, nunca un monto inventado. Requiere `DD-NN` antes de implementar.
- **PA-12**: nombre comercial sin definir → se usa `BRAND_NAME` de `design-system/brand.js`, hoy `Turnos Odonto (TBD)`.
- Logo provisorio y fecha de actualización de ejemplo (`2026-11-20`), que en producción sale de la fuente versionada.

## Cómo previsualizar

Opción simple (sin servidor, funciona con `file://` porque `brand.js` es un script clásico):

1. Abrir `mockups/precios.html` en un navegador moderno.
2. Emular viewport de **390 × 844** y de **1440 × 900**.
3. Comprobar que cargan `tokens.css`, `brand.js` y `assets/logo.svg` (el logo rotulado como provisorio y el nombre con `(TBD)` confirman que las rutas relativas funcionan).

Opción con servidor local, desde la raíz del repo:

```powershell
python -m http.server 8000
# navegar a http://localhost:8000/openspec/changes/c-21-pagina-precios/mockups/precios.html
```

## Capturas

- `previews/precios-mobile-390x844.png`
- `previews/precios-desktop-1440x900.png`

## Checklist de revisión para Maxi

- [ ] Un solo `h1`; jerarquía de encabezados correcta (`h1` → `h2` → `h3`).
- [ ] Texto en es-AR con voseo («Probá», «tus pacientes»).
- [ ] Leyenda explícita: «30 días de prueba sin tarjeta ni medio de pago».
- [ ] Importe marcado como `(TBD) — PA-03`; ningún monto inventado.
- [ ] Fecha de actualización visible en es-AR.
- [ ] Colores, tipografía, espaciado, radios, foco y movimiento solo con tokens del sistema (rev. 1).
- [ ] Foco visible con `outline: 2px`; botones con objetivo táctil ≥ 44 px (`--touch-min`).
- [ ] `prefers-reduced-motion` heredado de `tokens.css`; hover detrás de `@media (hover: hover) and (pointer: fine)`.
- [ ] Sin scroll horizontal a 390 px ni a 1440 px; contraste AA; estados no dependen solo del color.
- [ ] Sin datos de pacientes ni afirmaciones de cobro, 2FA, certificaciones ni cumplimiento legal verificado.
