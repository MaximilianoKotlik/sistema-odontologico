# Mockups de C-17 · `pacientes-revision`

`Approval: pending`

- Design system revision: 1
- Preview: rendered (2026-11-20, segunda versión con la revisión de #36) · capturas de página completa a 390×844 y 1440×900 (39). En mobile se renderizan con la ventana del alto de la página, así la barra inferior fija queda al final y no tapa contenido; la hoja "Más" es una captura del viewport. Sin scroll horizontal, sin errores de consola, controles de al menos 44 px y un solo `h1` por pantalla.
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

## Decisiones

Maxi aprobó estas decisiones en la revisión de #36 (2026-11-20); la 6 se ajustó según su pedido.

1. **Mostrar qué se escribió en la reserva** (aprobada, **[DD-38](../../../../knowledge-base/09_decisiones_y_supuestos.md)**). La deduplicación (C-11 y C-15) guarda lo informado en la reserva que disparó la marca: `nombre_informado`, `apellido_informado`, `email_informado`, `informado_at` e `informado_turno_id`. Si hay varias reservas distintas antes de revisar, queda la última. Se **borra al revisar** con cualquiera de las tres salidas (minimización, Ley 25.326). Las columnas van en la migración `V6` de C-10 (Fran) y ya están en `04_modelo_de_datos.md` §Paciente.
2. **Tres salidas en un toque**: "Mantener «Ana Ejemplo»" (`POST …/confirmar-revision`), "Usar «Anna Ejemplo»" (`PATCH` con lo informado) y "Corregir a mano" (formulario). Las tres marcan revisado y auditan `PACIENTE_REVISADO` solo con los campos que cambian (RN-AD-05). Ninguna pide confirmación: no se pierde nada y queda en el Registro de cambios.
3. **Solo se muestran los datos que difieren**; lo que coincide se resume en una línea ("Coinciden el apellido, el email y el teléfono"). El dato distinto lleva fondo, negrita, ícono y la palabra "Distinto": no depende solo del color.
4. **DNI y teléfono no se editan**: el DNI es la clave (DD-24) y el teléfono queda fuera en v1 (alcance de C-17). Se muestran de solo lectura, con la razón.
5. **"Pacientes" es una sección nueva del shell**: en desktop va en la barra lateral, después de "Bloqueos"; en mobile, dentro de "Más" (la barra inferior ya tiene sus cuatro lugares). El contador aparece en "Pacientes" y sobre "Más", con el número y un texto para lectores de pantalla. **Toca el shell de C-06 (Nacho).**
6. **Aviso en la agenda** ("contador visible desde la agenda", scope de C-17): va **pegado al aviso de cambios de DD-30**, uno debajo del otro y con la misma jerarquía, en versión compacta (título, una línea y el link "Revisar pacientes", que lleva al filtro). Desaparece cuando no queda nadie para revisar. **Toca la agenda de C-13 (Nacho)**, que C-17 suma al implementar.
7. **Lista**: ordenada por apellido, con DNI, teléfono y email. Los marcados muestran "Revisar" y una línea con lo que se escribió ("Escribió «Anna Ejemplo» al reservar el 18 de noviembre"). Paginada con "Mostrar más". Sin búsqueda en v1.
8. **Sin conexión**: la lista y la revisión no se guardan en el dispositivo (son datos personales, igual que en C-06). Se avisa y no se puede revisar.
9. **Otro consultorio**: un paciente ajeno responde 404 (RN-TE-04) y se ve "No encontramos este paciente", igual que un enlace roto.

## Deduplicación por DNI

Estos mockups siguen DD-24 (DNI). El texto de §C-11 en `CHANGES.md` que todavía dice "por teléfono E.164" lo corrige Fran en la propuesta de C-11 (decidido por Maxi en #36).

## Datos ficticios

- Producto: Muelas Felices (DD-34). El logo sigue provisorio.
- Pacientes ficticios evidentes ("Ana Ejemplo", "Bruno Prueba", "Carla Ficticia"…), DNI `90.000.0xx`, teléfonos `+54 9 11 0000-00xx` y emails `@example.com`. Las variantes escritas al reservar ("Anna", "Pruebas", "Karla") son errores de tipeo inventados.

## Checklist de revisión para Maxi

- [ ] DD-38 en `09_decisiones_y_supuestos.md` y §Paciente de `04_modelo_de_datos.md`.
- [ ] Capturas mobile con la barra al final y avisos de la agenda juntos (revisión de #36).
- [ ] Las tres salidas de la revisión y que ninguna pida confirmación (decisión 2).
- [ ] Ubicación de "Pacientes" y del contador en el shell (decisión 5) y el aviso en la agenda (decisión 6).
- [ ] Textos en es-AR con voseo; ningún texto culpa al paciente.
- [ ] Solo tokens del sistema de diseño rev. 1; foco visible; objetivos ≥ 44 px; estados con ícono y texto, no solo color; `prefers-reduced-motion` (el spinner queda quieto).
- [ ] Sin datos reales de pacientes.
