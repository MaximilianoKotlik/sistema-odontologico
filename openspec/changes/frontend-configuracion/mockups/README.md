# Mockups de C-09 · `frontend-configuracion`

`Approval: pending`

- Design system revision: 1
- Preview: rendered (2026-11-20). Capturas a 390×844 y 1440×900, de página completa salvo en los estados con diálogo, que se capturan con la ventana del tamaño indicado. Sin desborde horizontal en 360, 390 y 1440 px, y sin errores de consola.
- Preparado e implementa: Nacho (trabajo anticipado). El código se escribe cuando C-07 y C-08 estén `[x]` y Maxi apruebe estos mockups.

Son mockups de revisión de la sección Configuración y del asistente de primer ingreso. **No es código de producción**: no hay cambios en `backend/` ni en `frontend/`.

## Brief

- **Audiencia**: el odontólogo independiente. Lo configura una vez, recién registrado y muchas veces desde el celular, y vuelve a Configuración de vez en cuando para ajustar algo.
- **Tarea**: pasar de "cuenta recién creada" a "link listo para compartir" en pocos minutos (FL-02). Después, cambiar datos, prestaciones u horarios sin miedo a romper la agenda.
- **Responsive**:
  - Mobile: una columna, con la barra de guardado fija arriba de la navegación inferior.
  - Desktop:
    - Consultorio en dos columnas (datos y link a la izquierda, reglas de reserva a la derecha).
    - Horarios en una grilla de dos días por fila.
    - El asistente queda en una columna centrada de 640 px.
- **Tokens y assets**: importan `design-system/tokens.css` y `design-system/brand.js`. `config.css` solo usa variables del sistema de diseño (rev. 1, sin tokens nuevos). El shell es el del mockup aprobado de C-06, y los botones, campos y avisos siguen los mockups de C-07.

## Pantallas y estados

Cada archivo elige el estado con `?estado=…`. Al pie de cada pantalla hay una franja "Estados del mockup" con links a todos los estados. Esa franja existe solo en el mockup.

| Pantalla | Archivo | Estados (`?estado=`) |
|---|---|---|
| Asistente (`/app/bienvenida`) | `bienvenida.html` | `paso-1`, `paso-1-slug` (409 con sugerencias), `paso-1-telefono` (sin teléfono), `paso-1-guardando`, `paso-2` (vacío con atajos), `paso-2-cargado`, `paso-2-minimo` (avanzar sin prestaciones), `paso-3` (vacío con atajo), `paso-3-cargado`, `paso-4`, `paso-4-copiado`, `paso-4-sin-share` |
| Consultorio (`/app/configuracion`) | `consultorio.html` | `normal`, `cargando`, `slug-editando` (aviso de que el link anterior deja de funcionar), `slug-en-uso` (409), `slug-reservado` (400), `validacion` (resumen de errores), `telefono-vacio`, `telefono-invalido` (400), `anticipacion` (mayor que el horizonte), `guardando`, `guardado`, `pausada` (reserva pública pausada), `conflicto` (409 `CONFLICTO_CONCURRENCIA`), `salir` (cambios sin guardar) |
| Prestaciones (`/app/configuracion/prestaciones`) | `prestaciones.html` | `normal`, `vacio`, `alta`, `duracion-invalida`, `duplicada` (409), `editar` (aviso RN-AG-02), `agregada`, `inactivas`, `desactivar`, `desactivar-ultima` |
| Horarios (`/app/configuracion/horarios`) | `horarios.html` | `normal`, `vacia`, `editando`, `solapadas` (detectado en el cliente), `invalida` (fin antes del inicio), `api-solapadas` (400 con `dias: [3]`), `copiar`, `guardando`, `guardado`, `vaciar` (confirmar semana sin horarios), `zona` (zona horaria distinta) |

Capturas: `previews/<pantalla>-<estado>-mobile-390x844.png` y `previews/<pantalla>-<estado>-desktop-1440x900.png` (94 en total).

Para verlos: abrir el HTML con el navegador o servir la raíz del repo (`python -m http.server`) y entrar a `/openspec/changes/frontend-configuracion/mockups/bienvenida.html`.

## Decisiones para revisar

1. **Asistente sin la navegación del shell** (design D2): solo marca, progreso y "Terminar después". Así el primer ingreso no se distrae con secciones vacías. Se abre mientras no haya prestaciones activas (D3). No se guarda una marca de "onboarding terminado".
2. **Atajos**:
   - Prestaciones comunes con duración sugerida: Consulta 30', Limpieza 45', Extracción 60' y Control 20', las mismas del seed de C-08.
   - Horarios: "Lunes a viernes, 9 a 13 y 15 a 19".
   Cada atajo carga datos que después se pueden editar.
3. **Reglas de reserva con valores por defecto en el asistente**: el paso 1 no las pregunta para no alargar el primer ingreso. Muestra los valores con que arrancan y dice dónde se cambian.
4. **Unidades legibles** (D4): anticipaciones en horas, horizonte en días y paso de huecos como opciones. La anticipación de reserva tiene que ser menor que el horizonte, con el mensaje en su campo (revisión de Maxi).
4b. **Teléfono de contacto obligatorio** en el asistente y en Configuración (D4b, revisión de Maxi).
5. **Slug**:
   - Se normaliza mientras se escribe.
   - Las palabras reservadas las valida el backend (D5), para no mantener dos listas.
   - Elegir una sugerencia completa el campo pero no guarda sola.
6. **Pestañas como links** (`aria-current="page"`) y no como `tablist`, porque cada una tiene su URL. En menos de 420 px se ocultan los íconos de las pestañas para que entren las tres.
7. **Barra "Tenés cambios sin guardar"** con "Descartar" y "Guardar", visible solo con cambios, igual en Consultorio y Horarios. Al salir con cambios se pide confirmación, con "Seguir editando" como opción por defecto.
8. **Prestaciones**:
   - Se ordenan con botones Subir/Bajar, sin arrastrar (WCAG 2.5.7).
   - "Desactivar" (ícono de pausa) reemplaza a borrar (RN-AG-07).
   - Las inactivas quedan en un grupo plegable con "Activar".
   - La visibilidad se indica con etiqueta, ícono y texto: "En tu página" o "Solo para vos".
9. **Horarios**: `input type="time"` con paso de 5 minutos. "Copiar a…" reemplaza las franjas de los días elegidos. Los errores de solapamiento se marcan en el día, con el par de franjas en conflicto, y un resumen arriba lleva a cada día.
10. **Paso Compartir**: "Compartir" aparece solo si el navegador ofrece Web Share API. No hay botón de WhatsApp (DD-18).

## (TBD) y datos ficticios

- Nombre: **Muelas Felices** (DD-34), desde `BRAND_NAME` de `design-system/brand.js`. El logo sigue provisorio (isotipo de diente de C-06), igual que en el sistema de diseño.
- Dominio del link: `turnos.example.com` es un placeholder. En producción sale de `location.origin`.
- Datos ficticios: "Dra. Ejemplo", "Consultorio Demo Sur", "Calle Falsa 123, CABA", `+54 9 11 0000-0001` y los slugs `dra-ejemplo*`.
- El link público no muestra nada hasta C-16.
