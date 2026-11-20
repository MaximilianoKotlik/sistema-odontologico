# Design

## Context

Ver `proposal.md` para la motivación. Hoy el repo es documentación y gobernanza: no hay código de backend ni frontend, no hay specs canónicas y `CHANGES.md` tiene todos los changes pendientes. C-21 declara dependencias de C-02 (`core-compartido-tenant`, Fran) y C-06 (`frontend-shell-pwa`, Nacho), por lo que la implementación real no puede arrancar hasta que ambas estén mergeadas. La KB fija RN-PL-01/02/04, US-060 y la regla dura 15 (nunca `float`/`double` para precios); PA-03 (Alta) sigue abierta y es competencia del Product Owner.

## Goals / Non-Goals

**Goals:**

- Definir un contrato público estable para el catálogo de planes, sin acoplarlo a la agenda ni a un tenant.
- Dejar un mockup revisable de `/precios` antes de escribir código de producción.
- Preparar una secuencia de implementación en Strict TDD que arranque cuando C-02 y C-06 estén listos.

**Non-Goals:**

- Elegir los importes exactos (PA-03): se usan marcadores hasta que Maxi lo decida y se documente como `DD-NN`.
- Cobro, Mercado Pago, activación automática, planes por profesional/sillón o personalización por consultorio.
- Textos legales de privacidad/términos (PA-06 y C-22) ni datos de pacientes.

## Decisions

### 1. Fuente versionada en lugar de tabla

El catálogo vive en un archivo de configuración versionado del backend (p. ej. `backend/src/main/resources/planes.yaml`), cargado al arranque en registros inmutables y validado con errores de arranque si falta un campo obligatorio. La fecha de actualización se guarda explícitamente en esa fuente, no se infiere del `mtime` del archivo.

- **Por qué**: evita una migración Flyway, evita el acoplamiento a `consultorio_id` de C-02 y permite revisar cambios de precios por PR.
- **Alternativa descartada**: tabla `plan` con seeds; exige una migración y acoplamiento al tenant para un dato global, con costo de operación sin beneficio en v1.
- **Trade-off**: cambiar un precio requiere redeploy; aceptable durante el piloto con activación manual (RN-PL-04).

### 2. Importes como texto decimal

La configuración guarda importes entre comillas (p. ej. `"24900.00"`) y el contrato los expone como `importeARS` de tipo `String` con formato `^\d+\.\d{2}$`. Cualquier cálculo interno usa `BigDecimal`; nunca `float`/`double` (regla dura 15).

### 3. Contrato del endpoint

`GET /api/v1/public/planes` responde 200 con:

```json
{
  "planes": [
    {
      "id": "consultorio-unico",
      "nombre": "Plan consultorio",
      "incluye": ["Agenda diaria y semanal", "Reserva pública por link"],
      "importeARS": "PENDIENTE",
      "moneda": "ARS",
      "impuestosIncluidos": true
    }
  ],
  "prueba": { "dias": 30, "requiereMedioDePago": false },
  "actualizadoEn": "2026-11-20T00:00:00Z"
}
```

`"PENDIENTE"` es un marcador del borrador: la implementación no se publica con ese valor. `actualizadoEn` es un instante UTC; la presentación usa `Intl.DateTimeFormat('es-AR', { timeZone: 'America/Argentina/Buenos_Aires' })`. Los errores usan Problem Details de C-02 con `codigo = PLANES_NO_DISPONIBLES`.

- **Por qué**: el visitante no necesita token y el frontend genera sus tipos desde el OpenAPI de springdoc.
- **Alternativa descartada**: HTML estático con precios hardcodeados; rompe RN-PL-01 (fecha visible versionada) y duplica la fuente de verdad.

### 4. Frontend y mockup

El mockup se entrega como HTML estático dentro del change, importando `design-system/tokens.css` y reutilizando los patrones de `components.html` (botones, tarjetas, foco, contraste AA). La implementación futura usa `features/precios/` + `pages/precios/`, TypeScript strict sin framework, Tailwind mapeado a tokens, voseo rioplatense, `Intl.NumberFormat('es-AR', { style: 'currency', currency: 'ARS' })`, objetivos táctiles ≥ 44 px y `prefers-reduced-motion`. La respuesta pública no se guarda en caché del Service Worker.

### 5. Pruebas y gobernanza

Tests de backend sin base de datos (carga y validación de configuración, contrato JSON, Problem Details) y tests Vitest de render, formateo ARS/fecha y estado de error. Todo con Strict TDD (rojo → verde → triangulación → refactor dentro del ciclo). Rama `feat/C-21-pagina-precios` desde `main` actualizado, commits conventional en español con `(C-21)`, PR en borrador para el mockup, aprobación de Maxi antes de código, y el `[x]` de `CHANGES.md` lo marca él al mergear.

## Risks / Trade-offs

- **PA-03 sin decidir** → los importes son marcadores; no se publica la página con precios ficticios y se registra un `DD-NN` antes de implementar.
- **Fecha de actualización desincronizada del precio** → la fecha vive en la misma fuente versionada y un test verifica que el valor expuesto coincide con la configuración.
- **Cambio de nombre comercial (PA-12)** → toda la UI usa `BRAND_NAME` de `design-system/brand.js`; no se hardcodea el nombre.
- **Retraso de C-02 o C-06** → se avanza solo con propuesta, spec, diseño y mockup; no se abre PR de código hasta que las dependencias estén `[x]`.
- **Regresión de accesibilidad** → el mockup incluye foco visible y tamaños táctiles, y la revisión de Maxi chequea AA antes del código.

## Migration Plan

Sin migración de base de datos ni datos que preservar. El despliegue publica configuración + backend + frontend en el mismo release; ante un problema se revierte la imagen anterior y se repone la configuración versionada previa. `CHANGES.md` no se modifica para marcar avance: lo hace el líder al mergear.

## Open Questions

- ¿Nombre comercial definitivo? Es PA-12, no afecta el contrato ni las specs: se resuelve cambiando `BRAND_NAME` cuando el Product Owner lo decida.

## UI Mockups

- Design system revision: 1
- Decision: yes (2026-11-20)
- Mockups: openspec/changes/c-21-pagina-precios/mockups/
- Approval: pending
- Preview: rendered (2026-11-20); `previews/precios-mobile-390x844.png`, `previews/precios-desktop-1440x900.png`
- Visual verify: pending
