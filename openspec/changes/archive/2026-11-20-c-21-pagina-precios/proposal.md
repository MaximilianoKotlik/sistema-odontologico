# Proposal

## Why

El diferenciador de transparencia de la v1 exige que un visitante pueda conocer el costo antes de registrarse: planes en ARS con impuestos incluidos, prueba gratuita de 30 días sin tarjeta y fecha de actualización visible (RN-PL-01, RN-PL-02, US-060). Hoy no existe ningún catálogo público ni pantalla de precios; C-21 es la cara pública de ese diferenciador y, según el roadmap, puede planificarse en paralelo sin esperar la agenda.

## What Changes

- Publicar `GET /api/v1/public/planes` sin autenticación, con planes, qué incluye cada uno, importes en ARS (como texto decimal exacto, nunca `float`/`double`), impuestos incluidos y fecha de última actualización.
- Servir el catálogo desde una fuente de configuración versionada en el repositorio, sin tabla nueva ni cobro integrado (RN-PL-04).
- Agregar la página pública `/precios` (sin login), mobile-first a 360 px, con la leyenda explícita de prueba sin tarjeta y la fecha de actualización en es-AR.
- Adjuntar un mockup revisado de `/precios` basado en el sistema de diseño aprobado (rev. 1); es requisito previo a escribir código de producción.
- Registrar la decisión de **PA-03** (Alta) antes de implementar: la KB propone plan único fijo por consultorio + 30 días de prueba, sin plan gratuito permanente, pero los importes exactos siguen sin decidir. Los montos del mockup son marcadores **PENDIENTE DE DECISIÓN DEL Product Owner (Maxi)** y no deben publicarse como precios reales.
- Sin cambios rupturistas. No se modifica el esquema de la base ni se agregan dependencias de agenda, turnos ni pacientes.

## Capabilities

### New Capabilities

- `public-plans`: catálogo público de planes y su presentación (endpoint, fuente versionada y página `/precios`), incluida la divulgación honesta de la prueba sin tarjeta y la fecha de actualización.

### Modified Capabilities

- Ninguna. El repo no tiene specs canónicas cargadas en `openspec/specs/` y este change no altera requisitos de capacidades existentes.

## Impact

- Backend: nuevo controlador público en el paquete `plan/` y recurso de configuración versionado; Reutilización de Problem Details (RN-GL-03) de C-02. Sin migración Flyway.
- Frontend: `pages/precios` y `features/precios/`, router público, `api-types.ts` regenerado desde OpenAPI, formateo de ARS con `Intl.NumberFormat('es-AR', ...)` y zona `America/Argentina/Buenos_Aires`.
- Diseño: mockup en `openspec/changes/c-21-pagina-precios/mockups/` usando `design-system/tokens.css` y `components.html`; aprobación de Maxi antes de implementar.
- Gobernanza: C-21 es MEDIO y tiene pantalla → propuesta + mockup en PR en borrador, aprobación del dueño, Strict TDD y PR listo para revisión. El checkbox `[x]` de `CHANGES.md` lo marca Maxi al mergear.
- Dependencias declaradas en `CHANGES.md`: C-02 (Fran) y C-06 (Nacho) para implementar; la propuesta y el mockup no dependen de ellas.
- Fuera de alcance: cobro, Mercado Pago, planes por profesional/sillón, personalización de marca por consultorio, textos legales (son PA-06 y C-22) y cualquier dato de paciente.
