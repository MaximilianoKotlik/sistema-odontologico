# Proposal

> Change **C-09** de [CHANGES.md](../../../CHANGES.md) · Governance **MEDIO** · Depende de `C-07` (`frontend-auth`) y `C-08` (`consultorio-config-backend`) · Responsable: Nacho · Aprobación: Maxi.
>
> Trabajo anticipado: esta propuesta y los mockups se preparan antes de que C-07 y C-08 estén `[x]`. El código de producción no se escribe hasta que los dos estén mergeados y Maxi apruebe los mockups.

## Why

Después de registrarse, el odontólogo no tiene prestaciones ni horarios, y sin eso no puede dar turnos ni compartir su link público. C-08 expone la API para configurarlo, pero no hay pantallas. C-09 le da la sección **Configuración** de la app y el **asistente de primer ingreso** (FL-02), que lo lleva en cuatro pasos de "cuenta recién creada" a "link listo para compartir". Es la primera pantalla que ve el odontólogo después del login, así que define la primera impresión del producto.

## What Changes

- **Asistente de primer ingreso** (`/app/bienvenida`, FL-02). Se abre después del login mientras el consultorio no tenga prestaciones activas. Tiene cuatro pasos:
  1. Consultorio: nombre, slug con vista previa del link y teléfono de contacto (obligatorio).
  2. Prestaciones: al menos una, con atajos para las más comunes.
  3. Horarios: grilla semanal, con un atajo de "lunes a viernes".
  4. Compartir: link `/c/{slug}` con Copiar y Compartir (Web Share API, solo si el navegador la ofrece).
  Se puede salir con "Terminar después" y retomar. Cada paso guarda en la API apenas se confirma.
- **Sección Configuración** (`/app/configuracion`), con tres pestañas:
  - **Consultorio**: datos, slug con su link público copiable, reglas de reserva (anticipación de cancelación y de reserva, horizonte, paso de huecos), reserva pública activa o pausada, y zona horaria.
  - **Prestaciones** (`/app/configuracion/prestaciones`): listado con duración y visibilidad, alta y edición en un diálogo, orden con botones subir/bajar, desactivar y reactivar (no hay borrado, RN-AG-07).
  - **Horarios** (`/app/configuracion/horarios`): una tarjeta por día con sus franjas, "Copiar a otros días" y guardado de la grilla completa.
- **Validaciones en el cliente**, que repiten las del backend para avisar antes de enviar:
  - duración entre 5 y 480 min, múltiplo de 5;
  - horas de franja en múltiplos de 5, fin posterior al inicio y sin solapamientos en el mismo día;
  - formato del slug (RN-TE-03);
  - teléfono de contacto obligatorio (decisión de Maxi en #23);
  - rangos de las reglas de reserva, con la anticipación de reserva menor que el horizonte.
  El backend sigue siendo la autoridad: los errores de C-08 se muestran junto al campo o al día que corresponde.
- **Errores de C-08 traducidos a mensajes**: `SLUG_EN_USO` con slugs sugeridos para elegir con un toque, `SLUG_RESERVADO`, `SLUG_INVALIDO`, `TELEFONO_INVALIDO`, `ZONA_HORARIA_INVALIDA`, `VALIDACION` por campo, `DURACION_INVALIDA`, `PRESTACION_DUPLICADA`, `FRANJAS_SOLAPADAS` con los días en conflicto, `FRANJA_INVALIDA` y `CONFLICTO_CONCURRENCIA`.
- **Mockups** de las cuatro pantallas y sus estados, revisados y aprobados por Maxi antes de implementar.
- **Tests Vitest**: validaciones (múltiplo de 5, franjas solapadas y contiguas, slug), conversión de unidades (horas y días a minutos), mapeo de errores a campos y el flujo del asistente con dobles de `fetch`.

**Fuera de alcance**:
- La página pública `/c/{slug}` (C-16) y el cálculo de huecos (C-15). Hasta C-16 el link compartido todavía no muestra nada.
- El botón de compartir por WhatsApp (DD-18).
- Cambiar el plan o ver la prueba (C-20).
- Bloqueos (C-14) y auditoría (C-18).

## Capabilities

### New Capabilities

- `app-configuracion/onboarding`: asistente de primer ingreso del odontólogo, cuándo aparece, sus pasos y cómo se retoma.
- `app-configuracion/consultorio`: pantalla de datos, slug, link público y reglas de reserva del consultorio.
- `app-configuracion/prestaciones`: pantalla de alta, edición, orden, desactivación y reactivación de prestaciones.
- `app-configuracion/disponibilidad`: editor de la grilla semanal de horarios de atención.

### Modified Capabilities

<!-- Ninguna. Las rutas nuevas cuelgan de /app/configuracion, que ya define app-shell (C-06), sin cambiar sus requisitos. -->

## Impact

- **Código**:
  - `frontend/src/features/configuracion/` (formularios, validaciones, cliente de la API de C-08 y el asistente, según la estructura de 08);
  - `frontend/src/pages/` (composición por ruta: Configuración y Bienvenida);
  - reemplazo del placeholder `frontend/src/pages/configuracion.ts` de C-06;
  - rutas nuevas en el router de C-06.
- **API**: consume los 9 endpoints de C-08 por `api-types.ts`. No agrega endpoints.
- **Dependencias**: ninguna nueva.
- **App-shell (C-06)**: sin cambios en la navegación. La pestaña activa se marca dentro de la página.
- **Coordinación**:
  - Con C-07 (Octi preparó los mockups, implementa Nacho): el login redirige al asistente si no hay prestaciones activas.
  - Con C-08 (Fran): se usan sus códigos de error y propiedades (`sugerencias`, `dias`). Si cambian, se ajustan los mensajes.
- **Preguntas abiertas**: no toca ninguna Alta (PA-01..PA-06). El horizonte máximo de 365 días depende de que Maxi apruebe D9 de C-08.
