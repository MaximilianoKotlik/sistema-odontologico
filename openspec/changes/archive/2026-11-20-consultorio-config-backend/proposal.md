# Proposal

> Change **C-08** de [CHANGES.md](../../../CHANGES.md) · Governance **MEDIO** · Depende de `C-03` (`auditoria-base`) y `C-04` (`auth-sesion-tokens`) · Responsable: Fran · Aprobación: Maxi.
>
> Se apoya en C-02 (mergeado), en la propuesta aprobada de C-03 (#10) y en la de C-04 (#8, con los ajustes de su revisión en la rama `feat/C-04-auth-sesion-tokens`). El código no se escribe hasta que C-03 y C-04 estén `[x]`.

## Why

Recién registrado, el consultorio tiene nombre y slug, pero no tiene prestaciones ni franjas horarias. Sin eso, la agenda (C-10 en adelante) no tiene con qué calcular la duración de un turno ni dónde caben, y la página pública (C-15) no tiene nada que ofrecer. C-08 le da al odontólogo la API para configurar su consultorio: datos y reglas de reserva, tipos de prestación con duración y disponibilidad semanal. Cada cambio queda auditado (RN-AD-04). Es el paso 1 a 3 del onboarding (FL-02) y está en el camino crítico: C-09, C-10 y C-15 dependen de él.

## What Changes

- **Migración**: tablas `tipo_prestacion` y `disponibilidad` según 04 y la convención de C-02 (`consultorio_id`, FKs compuestas, constraints con nombre).
  - `tipo_prestacion`: duración entre 5 y 480 min, múltiplo de 5; nombre único por consultorio sin distinguir mayúsculas; sin `DELETE` para `app_rw`, porque se desactiva y no se borra (RN-AG-07).
  - `disponibilidad`: restricción `EXCLUDE USING gist` que impide franjas solapadas del mismo día como red de seguridad.
- **`GET/PUT /api/v1/consultorio`**: nombre, slug, dirección, teléfono de contacto (normalizado a E.164), zona horaria, anticipación de cancelación y de reserva, horizonte, paso de huecos y `reserva_publica_activa`.
  - Errores: 400 `SLUG_INVALIDO` o `SLUG_RESERVADO`; 409 `SLUG_EN_USO` con slugs sugeridos libres; 400 `TELEFONO_INVALIDO`, `ZONA_HORARIA_INVALIDA` y `VALIDACION` por rango.
- **Prestaciones**:
  - `GET /api/v1/prestaciones` (con `incluirInactivas`), `POST /api/v1/prestaciones` y `PUT /api/v1/prestaciones/{id}`.
  - `POST /api/v1/prestaciones/{id}/desactivar` y `/activar`. No hay `DELETE`.
  - Errores: 400 `DURACION_INVALIDA`, 409 `PRESTACION_DUPLICADA`.
- **Disponibilidad**: `GET /api/v1/disponibilidad` y `PUT /api/v1/disponibilidad` (reemplazo completo de la grilla semanal).
  - Errores: 400 `FRANJAS_SOLAPADAS` (con los días en conflicto) y `FRANJA_INVALIDA` (fin no posterior al inicio, día fuera de 1..7, hora no múltiplo de 5).
- **Auditoría** (vía el puerto de C-02, que persiste C-03), con valor anterior y nuevo de los campos que cambian: `CONSULTORIO_CONFIG_CAMBIADA`, `PRESTACION_CREADA`, `PRESTACION_CAMBIADA`, `PRESTACION_DESACTIVADA`, `PRESTACION_ACTIVADA` y `DISPONIBILIDAD_CAMBIADA`.
- **Seed local** (`db/seed-local`, solo con el perfil `local`): "Consultorio Demo" (slug `demo`), usuario `demo@consultorio.test`, prestaciones Consulta 30', Limpieza 45', Extracción 60' y Control 20', y disponibilidad de lunes a viernes de 09:00 a 13:00 y de 15:00 a 19:00. Los pacientes y turnos de ejemplo de 04 llegan con C-10 en adelante.
- **Tests**: US-004, US-010 y US-011, más el aislamiento por tenant en **cada** endpoint (otro consultorio da 404, regla dura 6).

**Fuera de alcance**:
- La página pública `/c/{slug}` del primer criterio de US-004 (se publica en C-15/C-16).
- El cálculo de huecos con la disponibilidad (C-15).
- Las pantallas del asistente (C-09).
- Bloqueos y turnos (C-11).
- El cambio de plan (C-20).

## Capabilities

### New Capabilities

- `configuracion-consultorio`: consulta y edición de los datos y reglas de reserva del consultorio, con su auditoría.
- `prestaciones`: tipos de prestación con duración, visibilidad pública, orden y desactivación en lugar de borrado.
- `disponibilidad-semanal`: franjas de atención por día de la semana, reemplazo completo y prevención de solapamientos.

### Modified Capabilities

- `slug-consultorio`: se agrega el cambio de slug por el odontólogo, con sugerencias de slugs libres cuando el pedido está en uso. Los requisitos existentes no cambian.

## Impact

- **Código**: `backend/.../consultorio/**` (web, application, domain, infrastructure), migración `V{n}__prestaciones_y_disponibilidad.sql` (número provisorio; se fija al rebasar después de C-03, C-04 y C-05), `db/seed-local/R__demo.sql` y `application-local.yml` (`spring.flyway.locations`).
- **API**: 9 endpoints privados nuevos; contrato regenerado en `api-types.ts` para C-09 (Nacho).
- **Dependencias**: ninguna nueva.
- **Coordinación con C-04 (mío)**:
  - El asignador de slug único con sufijo (D6 de C-04) se ubica en `consultorio.application` para que lo reusen el registro y el cambio de slug.
  - C-04 expone el usuario autenticado (`sub`) para el `actor_id` de la auditoría (design D3).
- **Preguntas abiertas**: no toca ninguna Alta (PA-01..PA-06). La zona horaria editable respeta RN-TE-05.
- **Changes desbloqueados**: C-09 (con C-07), C-10 y, junto con C-11, C-15.
