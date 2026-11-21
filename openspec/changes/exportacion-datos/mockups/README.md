# Mockups de C-19 · `exportacion-datos`

`Approval: approved (2026-11-20, by Maxi)`, versión `45847c9` (#32)

- Design system revision: 1
- Preview: rendered (2026-11-20).
  - Capturas a 390×844 (del tamaño de la ventana, para que la navegación inferior fija no tape el contenido) y 1440×900 (página completa).
  - Sin desborde horizontal en 360, 390 y 1440 px, y sin errores de consola.
- Preparado e implementa: Mati (trabajo anticipado). El código se escribe cuando C-09 y C-15 estén `[x]` y Maxi apruebe estos mockups y la propuesta.

Es el mockup de revisión de la sección **Exportar mis datos** (`/app/exportar`). **No es código de producción**: no hay cambios en `backend/` ni en `frontend/`.

## Brief

- **Audiencia**: el odontólogo independiente. Entra pocas veces: para tener una copia, antes de cambiar de sistema o cuando su plan venció.
- **Tarea**: descargar todos sus datos en un paso, entendiendo qué se lleva y que el archivo tiene datos sensibles de pacientes.
- **Responsive**:
  - Mobile: una columna. La acción queda arriba, visible sin scrollear, y el detalle de qué incluye va abajo. Se llega desde la hoja "Más" del app-shell, así que "Más" queda marcado en la navegación inferior.
  - Desktop: una columna de hasta 720 px. El botón y su ayuda van en una fila.
- **Tokens y assets**:
  - Importan `design-system/tokens.css` y `design-system/brand.js`.
  - `exportar.css` es una copia del `config.css` aprobado de C-09 (shell de C-06 y componentes de C-07). Suma solo el bloque "Exportación" con variables del sistema de diseño (rev. 1, sin tokens nuevos).
  - `mockup.js` es el de C-09.

## Pantallas y estados

`exportar.html` elige el estado con `?estado=…`. Al pie hay una franja "Estados del mockup" con links a todos los estados. Esa franja existe solo en el mockup.

| Estado (`?estado=`) | Qué muestra |
|---|---|
| `inicial` | Lista para exportar |
| `generando` | Botón deshabilitado con "Preparando tu archivo…" y anuncio en `aria-live` |
| `listo` | Aviso de éxito con el nombre del archivo descargado |
| `reciente` | 429 `EXPORTACION_RECIENTE`: "Vas a poder pedir otra en 3 minutos" (sale de `Retry-After`), con el botón habilitado |
| `sin-conexion` | Sin conexión, con el botón para reintentar |
| `error` | Se cortó la descarga: no se guarda un archivo incompleto |
| `vencido` | Plan vencido: aviso de que la exportación funciona igual (RN-EX-03) |

Capturas: `previews/exportar-<estado>-mobile-390x844.png` y `previews/exportar-<estado>-desktop-1440x900.png` (14 en total).

Para verlo: servir la raíz del repo (`python -m http.server`) y entrar a `/openspec/changes/exportacion-datos/mockups/exportar.html`.

## Decisiones para revisar

1. **La acción primero**: el botón queda arriba y el detalle de "Qué incluye" abajo. En mobile, el botón se ve sin scrollear.
2. **Aviso de datos sensibles junto al botón**: "El archivo tiene datos de tus pacientes: guardalo en un lugar seguro" (Ley 25.326, [11](../../../../knowledge-base/11_marco_legal_argentino.md)).
3. **Qué incluye, en lenguaje simple**: planillas CSV y JSON, resumen en PDF e índice (`manifest.json`). Aclara qué **no** incluye: contraseña ni links privados de pacientes, sin nombrar tokens ni hashes.
4. **Límite de 5 minutos visible antes de chocar con él**: en la lista de "Qué incluye". El 429 muestra los minutos que faltan, redondeados hacia arriba.
5. **Sin "última exportación"**: el backend propuesto no expone la fecha de la última exportación, así que la pantalla no la inventa. Si Maxi lo quiere, se suma un `GET` al backend (Open Question nueva).
6. **Resultado en el lugar**: los avisos de éxito y error aparecen debajo del botón, dentro de una región `role="status"`. No se usa toast, para que el mensaje no desaparezca antes de leerlo.

## (TBD) y datos ficticios

- Nombre: **Muelas Felices** (DD-34), desde `BRAND_NAME` de `design-system/brand.js`. El logo sigue provisorio (isotipo de diente de C-06).
- Datos ficticios: "Dra. Ejemplo", "Consultorio Demo Sur" y el archivo `export-consultorio-demo-sur-2026-11-20.zip`.
