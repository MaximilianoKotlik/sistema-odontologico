# Proposal

> Change **C-19** de [CHANGES.md](../../../CHANGES.md) · Governance **ALTO** · Depende de `C-09` (`frontend-configuracion`) y `C-15` (`reserva-publica-backend`) · Responsable: Mati · Aprobación: Maxi.
>
> Es trabajo anticipado. Se apoya en la propuesta de C-15 (PR #30, en borrador) y en las de C-08 y C-10 (PRs #15 y #18). El código no se escribe hasta que se cumplan tres condiciones:
> - C-09 y C-15 están `[x]`. Por C-15 llegan también C-08, C-10 y C-11, que crean las tablas que se exportan.
> - Maxi aprobó esta propuesta (ALTO) y el mockup de `/app/exportar` (modo `system-and-mockups`).
> - Las decisiones de `design.md` §Open Questions están resueltas.

## Why

La exportación completa es uno de los pilares del diferenciador de transparencia: el odontólogo nunca queda rehén del sistema (Discovery D.3, US-050, RN-EX-03). Además, es la forma en que la v1 atiende la portabilidad y el derecho de acceso (LG-06 y §Derechos del titular de [11](../../../knowledge-base/11_marco_legal_argentino.md)). Hoy la ruta `/app/exportar` del app-shell (C-06) muestra "Esta sección todavía no está disponible" y no hay ningún endpoint. C-19 entrega la descarga completa, en formato abierto y con un PDF legible, también con el plan vencido.

## What Changes

- **Endpoint** `POST /api/v1/exportacion` (con sesión): responde un ZIP `export-{slug}-{fecha}.zip` generado por streaming dentro de la request (DD-14), con `Cache-Control: no-store`.
  - **Un JSON y un CSV por entidad** (UTF-8, separador `,` y encabezado): consultorio, odontólogo (sin secretos), prestaciones, disponibilidad, bloqueos, pacientes, turnos y auditoría.
  - **`resumen.pdf` legible**: datos del consultorio, prestaciones, pacientes, listado de turnos y resumen de auditoría (RN-EX-05). Se genera con **OpenPDF** (design D6).
  - **`manifest.json`**: versión del esquema, fecha de generación, zona horaria, cantidad de registros por entidad y SHA-256 de cada archivo.
  - Foto consistente: transacción de solo lectura `REPEATABLE READ`, así las cantidades del manifest coinciden con la base.
- **Sin secretos** (RN-EX-02): nunca el hash de la contraseña, refresh tokens, tokens de recuperación ni tokens de gestión (`token_gestion_turno` de C-15). Un test recorre el ZIP completo buscando hashes y tokens.
- **Límite**: 1 exportación cada 5 minutos por consultorio, contado desde la última exportación completada → 429 `EXPORTACION_RECIENTE` con `Retry-After`. Dos exportaciones simultáneas del mismo consultorio también reciben 429.
- **Plan `VENCIDO`**: la exportación funciona igual (RN-EX-03). Queda como requisito explícito para la matriz de escrituras bloqueadas de C-20.
- **Auditoría**: `EXPORTACION_GENERADA`, con las cantidades por entidad, en una transacción aparte al terminar el streaming (FL-06 paso 4). Si el streaming falla, no se registra.
- **CSV seguro para planillas**: las celdas que empiezan con `=`, `+`, `-`, `@`, tabulación o retorno se escapan. Los datos de pacientes vienen de un formulario público anónimo (C-15). El JSON guarda el valor original.
- **Frontend** (feature `exportacion/` en `/app/exportar`, reemplaza la sección pendiente de C-06):
  - qué incluye la descarga, el botón "Exportar mis datos", el estado de generación y la descarga del archivo;
  - mensaje en español con voseo para el 429, con el tiempo que falta;
  - mockup aprobado antes de implementar.
- **Contrato OpenAPI** y `api-types.ts` regenerados.
- **Tests**: US-050 completo, contenido del ZIP (incluido el PDF con sus secciones mínimas), ausencia de secretos, límite y concurrencia, plan `VENCIDO`, aislamiento por tenant (el ZIP de A no contiene nada de B) y la página con Vitest.

**Fuera de alcance**:
- Exportación por paciente (Ley 26.529, con la historia clínica en v2).
- Generación asíncrona y almacenamiento de archivos (trade-off de DD-14).
- Importar datos.
- La página `/seguridad` (C-22), que describe esta exportación y debe alinearse con ella (RN-PL-06).

## Capabilities

### New Capabilities

- `exportacion-datos`: descarga completa de los datos del consultorio en ZIP (JSON, CSV, PDF y manifest), sin secretos, con límite de frecuencia, disponible en cualquier estado del plan y auditada.
- `pantalla-exportacion`: la sección `/app/exportar` de la app, que explica qué se descarga, dispara la exportación y muestra sus estados y errores.

### Modified Capabilities

- `cliente-api`: se agrega la descarga de archivos binarios (ZIP) con la misma autorización, renovación y errores tipados que los pedidos JSON. Los requisitos existentes no cambian.

`auditoria` ya admite cualquier acción con formato válido, y `app-shell` ya tiene la ruta `/app/exportar` en la navegación: no cambian.

## Impact

- **Código**:
  - `backend/.../exportacion/**` (hoy vacío).
  - `frontend/src/features/exportacion/**` y `frontend/src/pages/exportar.ts`.
  - Sin migraciones: el change lee las tablas existentes y escribe en `evento_auditoria`.
- **API**: 1 endpoint privado nuevo y `api-types.ts` regenerado.
- **Dependencias**: `com.github.librepdf:openpdf` (licencia LGPL 2.1 / MPL 2.0) y una fuente TTF con licencia OFL embebida, para que el PDF muestre bien cualquier carácter de los nombres. Requiere la aprobación de Maxi (design D6).
- **Coordinación**:
  - **C-15**: la exclusión de `token_gestion_turno` se prueba contra su tabla.
  - **C-20 (Fran)**: la exportación tiene que quedar habilitada con el plan `VENCIDO`, igual que su evento.
  - **C-22 (Octi)**: `/seguridad` tiene que describir el contenido real del ZIP.
  - **Mockup**: lo preparó Mati en este PR, aunque el plan se lo asignaba a Octi. Está pendiente de aprobación (design §UI Mockups).
- **Correcciones de documentación al cerrar el change**:
  - CHANGES.md §C-19 ubica la pantalla en `configuracion/`, pero la ruta es `/app/exportar` (03 y C-06).
  - FL-06 y DD-14 dicen `GET /exportacion` y CHANGES.md dice `POST` (design D1).
  - El comentario de `seccion-pendiente.ts` tiene cruzados los números de C-18 y C-19.
- **Preguntas abiertas**: no toca ninguna Alta.
- **Changes desbloqueados**: C-22 (con C-03 y C-06).
