# Mockups de C-17 · `pacientes-revision`

`Approval: pending`

- Design system revision: 1
- Preview: rendered (2026-11-20) · capturas de página completa a 390×844 y 1440×900 (39). Sin scroll horizontal, sin errores de consola, controles de al menos 44 px y un solo `h1` por pantalla.
- Preparado e implementa: Octi (trabajo anticipado, paso 4 del plan). El código se escribe cuando C-09 y C-11 estén `[x]` y Maxi apruebe estos mockups.

Mockups de revisión de la lista de pacientes y de la revisión de los marcados por la deduplicación (US-070). **No es código de producción**: no hay cambios en `backend/` ni en `frontend/`.

## Brief

- **Audiencia**: el odontólogo, entre paciente y paciente, casi siempre en el celular.
- **Tarea**: enterarse de que hay pacientes con datos para revisar, ver **qué** difiere entre su ficha y lo que se escribió al reservar, y decidir en un toque qué queda. Nunca se cambia la ficha sin que él elija (RN-RP-03).
- **Responsive**:
  - Mobile: lista en una columna. La comparación se apila por dato ("Guardado" y "En la reserva" uno debajo del otro) y las acciones ocupan todo el ancho.
  - Desktop: tabla de tres columnas (dato, guardado, en la reserva) y una columna lateral con el "¿Por qué aparece?" y el turno de la reserva.
- **Tokens y assets**: importan `design-system/tokens.css` y `design-system/brand.js`. Solo variables del sistema de diseño (rev. 1, sin tokens nuevos):
  - `base.css` es la base aprobada de C-13 (shell de C-06, botones, avisos, campos), copiada sin cambios;
  - `pacientes.css` suma la lista, la comparación, el contador y la hoja "Más" de C-07;
  - `agenda.css` es la de C-13, copiada sin cambios, solo para `agenda-aviso.html`.

## Pantallas y estados

Cada archivo elige el estado con `?estado=…`. Al pie hay una franja "Estados del mockup" que solo existe en el mockup. Los datos son del viernes 20 de noviembre, como en C-13.

| Pantalla | Archivo | Estados (`?estado=`) |
|---|---|---|
| Agenda con aviso (`/app/agenda`) | `agenda-aviso.html` | `inicio` (aviso "3 pacientes para revisar" y contador en la navegación), `offline` |
| Pacientes (`/app/pacientes`) | `pacientes.html` | `revision` (filtro "Requieren revisión", al que lleva el aviso), `todos` (paginado con "Mostrar más"), `revisado` (vuelve a la lista con el aviso de listo y el contador en 2), `sin-revision`, `sin-pacientes`, `mas` (hoja "Más" en mobile), `cargando`, `error`, `offline` |
| Revisar paciente (`/app/pacientes/{id}`) | `paciente.html` | `comparar`, `corregir`, `validacion`, `guardando`, `error-guardar`, `ver` (paciente sin revisión pendiente), `cargando`, `no-encontrado` (404, también para otro consultorio), `offline` |

Capturas: `previews/<pantalla>-<estado>-mobile-390x844.png` y `previews/<pantalla>-<estado>-desktop-1440x900.png` (el estado `mas` solo existe en mobile).

Para verlos: abrí cualquier `.html` directo en el navegador (funciona con `file://`), o serví la raíz del repo y entrá a `/openspec/changes/pacientes-revision/mockups/pacientes.html`.

## Decisiones para revisar

1. **Mostrar qué se escribió en la reserva (necesita una DD nueva).** Hoy el modelo (`04_modelo_de_datos.md`, tabla `paciente`) solo guarda `requiere_revision`: con eso la pantalla no puede decir **qué** difiere, y el odontólogo tendría que adivinar. La propuesta es que la deduplicación (C-11 y C-15) guarde lo informado en la reserva que disparó la marca, por ejemplo `nombre_informado`, `apellido_informado`, `email_informado`, `informado_at` y el turno de origen, y que se **borre al revisar** (minimización, Ley 25.326). Si hay varias reservas distintas antes de revisar, queda la última. Toca C-10 (migración), C-11/C-15 (escritura) y C-17 (API). Si Maxi no lo aprueba, la pantalla queda con los datos guardados, el aviso y el turno de origen, sin la columna "En la reserva".
2. **Tres salidas en un toque**: "Mantener «Ana Ejemplo»" (`POST …/confirmar-revision`), "Usar «Anna Ejemplo»" (`PATCH` con lo informado) y "Corregir a mano" (formulario). Las tres marcan revisado y auditan `PACIENTE_REVISADO` solo con los campos que cambian (RN-AD-05). Ninguna pide confirmación: no se pierde nada y queda en el Registro de cambios.
3. **Solo se muestran los datos que difieren**; lo que coincide se resume en una línea ("Coinciden el apellido, el email y el teléfono"). El dato distinto lleva fondo, negrita, ícono y la palabra "Distinto": no depende solo del color.
4. **DNI y teléfono no se editan**: el DNI es la clave (DD-24) y el teléfono queda fuera en v1 (alcance de C-17). Se muestran de solo lectura, con la razón.
5. **"Pacientes" es una sección nueva del shell**: en desktop va en la barra lateral, después de "Bloqueos"; en mobile, dentro de "Más" (la barra inferior ya tiene sus cuatro lugares). El contador aparece en "Pacientes" y sobre "Más", con el número y un texto para lectores de pantalla. **Toca el shell de C-06 (Nacho).**
6. **Aviso en la agenda** ("contador visible desde la agenda", scope de C-17): un aviso arriba del día con "Revisar pacientes", que lleva al filtro. Desaparece cuando no queda nadie para revisar. **Toca la agenda de C-13 (Nacho).** Convive con el aviso de cambios de DD-30.
7. **Lista**: ordenada por apellido, con DNI, teléfono y email. Los marcados muestran "Revisar" y una línea con lo que se escribió ("Escribió «Anna Ejemplo» al reservar el 18 de noviembre"). Paginada con "Mostrar más". Sin búsqueda en v1.
8. **Sin conexión**: la lista y la revisión no se guardan en el dispositivo (son datos personales, igual que en C-06). Se avisa y no se puede revisar.
9. **Otro consultorio**: un paciente ajeno responde 404 (RN-TE-04) y se ve "No encontramos este paciente", igual que un enlace roto.

## Inconsistencia para Maxi

En `CHANGES.md`, C-11, C-15 y C-17 todavía dicen "deduplicación por teléfono E.164", pero DD-24 la cambió a **DNI** (RN-RP-03 ya está actualizada). Estos mockups siguen DD-24. Conviene corregir el texto de los tres changes.

## Datos ficticios

- Producto: Muelas Felices (DD-34). El logo sigue provisorio.
- Pacientes ficticios evidentes ("Ana Ejemplo", "Bruno Prueba", "Carla Ficticia"…), DNI `30.000.0xx`, teléfonos `+54 9 11 0000-00xx` y emails `@example.com`. Las variantes escritas al reservar ("Anna", "Pruebas", "Karla") son errores de tipeo inventados.

## Checklist de revisión para Maxi

- [ ] Decisión 1: guardar lo informado en la reserva (DD nueva) o seguir sin la columna "En la reserva".
- [ ] Las tres salidas de la revisión y que ninguna pida confirmación (decisión 2).
- [ ] Ubicación de "Pacientes" y del contador en el shell (decisión 5) y el aviso en la agenda (decisión 6).
- [ ] Textos en es-AR con voseo; ningún texto culpa al paciente.
- [ ] Solo tokens del sistema de diseño rev. 1; foco visible; objetivos ≥ 44 px; estados con ícono y texto, no solo color; `prefers-reduced-motion` (el spinner queda quieto).
- [ ] Sin datos reales de pacientes.
