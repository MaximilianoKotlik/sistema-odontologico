# Mockups de C-22 · `pagina-seguridad`

`Approval: approved (2026-11-20, by Maxi), versión 1609d16 (#40)`

- Design system revision: 1
- Preview: rendered (2026-11-20, segunda versión con la revisión de #40) · capturas de página completa a 390×844 y 1440×900 (4). En mobile, con la ventana del alto de la página. Sin scroll horizontal, sin errores de consola, controles de al menos 44 px y un solo `h1`.
- Preparado e implementa: Octi (trabajo anticipado, paso 5 del plan). El código se escribe cuando C-19 esté `[x]` (C-03 y C-06 ya están) y Maxi apruebe este mockup.

Mockup de revisión de la página pública `/seguridad` (US-062, RN-PL-05, RN-PL-06). **No es código de producción**. Governance **ALTO**: son afirmaciones públicas sobre cómo se tratan datos de pacientes, así que cada frase sale de la KB o del código y tiene su respaldo a la vista en el estado `respaldo`.

## Brief

- **Audiencia**: el odontólogo que evalúa el producto antes de cargar datos de sus pacientes, y cualquier visitante (también un paciente curioso).
- **Tarea**: entender en pocos minutos qué datos se guardan, quién es responsable, qué garantías hay y, sobre todo, **qué todavía falta**. Nada de lenguaje comercial ni promesas.
- **Responsive**: una columna de 672 px, la misma estructura que `/precios` (C-21, ya implementada): cabecera con la marca, título, aviso, índice y una tarjeta por tema. En mobile la tabla de roles se apila.
- **Tokens y assets**: importan `design-system/tokens.css` y `design-system/brand.js`. `seguridad.css` usa solo variables del sistema de diseño rev. 1, sin tokens nuevos.

## Pantallas y estados

| Pantalla | Archivo | Estados (`?estado=`) |
|---|---|---|
| Seguridad y datos (`/seguridad`) | `seguridad.html` | `pagina` (lo que ve el visitante), `respaldo` (solo revisión: debajo de cada tema, qué regla, decisión, change o test respalda cada afirmación) |

No hay estados de carga ni de error: el contenido sale de una fuente versionada en el repo y se arma al compilar (decisión 1), sin pedirlo a la API.

Capturas: `previews/seguridad-<estado>-mobile-390x844.png` y `previews/seguridad-<estado>-desktop-1440x900.png`.

## Decisiones

Maxi aprobó las seis decisiones en la revisión de #40 (2026-11-20).

1. **Contenido en una fuente versionada que se compila con la página** (RN-PL-06: "fuente editable versionada, no hardcodeado en componentes"). Propuesta: un Markdown o YAML en el repo (por ejemplo `frontend/src/contenido/seguridad.md`) con la fecha de actualización, que el frontend convierte en la página al compilar. Así no hace falta un endpoint, no hay estados de carga ni de error, y cada cambio de texto pasa por un PR con revisión. El test de US-062 compara la página con esa fuente.
2. **El aviso de "sin revisión legal" va arriba de todo**, antes del índice, y lleva a "Lo que todavía no está". Es lo primero que tiene que saber quien evalúa el producto (RN-PL-05, LG-10).
3. **"Lo que todavía no está" es una sección propia**, con el mismo color de advertencia del aviso, ícono y texto en cada ítem (no solo color). Incluye: revisión legal (DD-28), verificación en dos pasos (DD-35), certificaciones (LG-10), país del servidor y backups (PA-02) y la baja de la cuenta con conservación de datos (DD-29, LG-08).
4. **Lenguaje llano**: "una huella que no se puede revertir" en vez de "BCrypt" (es un hash, no un cifrado: no se puede volver atrás); "no encontrado, como si no existiera" en vez de "404". Los nombres técnicos quedan solo en el respaldo.
5. **Afirmaciones que dependen de changes futuros**, marcadas en el respaldo: la exportación (C-19, dependencia de C-22), la consulta del registro de cambios (C-18) y el token del link del paciente (C-15/C-16). Si alguno no está `[x]` al implementar C-22, la frase se saca (RN-PL-06).
6. **Proveedores que tratan datos**: se nombran el VPS y el proveedor de email (que recibe el email del paciente cuando lo deja, DD-26). No se afirma "sin terceros".

## Bloqueos para publicar

- **PA-02 bloquea la publicación**: el proveedor y país del VPS, los backups y el proveedor SMTP aparecen como `(TBD)`. La página no puede publicarse con esos marcadores; se completan cuando se decida PA-02 (antes de cargar pacientes reales, DD-25).
- **TLS en el VPS**: "la conexión viaja cifrada (HTTPS)" vale cuando el VPS tenga TLS configurado. Hay que confirmarlo antes de publicar.
- **Email de seguridad** (decidido por Maxi en #40): va en "Cómo cuidamos el acceso" como `(TBD)`. Se define junto con el dominio (DD-34) y bloquea la publicación igual que PA-02.

`08_arquitectura_propuesta.md` §Seguridad ya no dice "sin DNI": se corrigió en este PR citando DD-24.

## Datos ficticios

Sin datos de pacientes. Producto: Muelas Felices (DD-34); el logo sigue provisorio. La fecha de actualización (20 de noviembre de 2026) es de ejemplo.

## Cómo verlo

Abrí `seguridad.html` directo en el navegador (funciona con `file://`) y usá la franja de estados para ver el respaldo.

## Checklist de revisión para Maxi

- [ ] Cada afirmación tiene respaldo en una regla, decisión, change o test (estado `respaldo`), y ninguna promete más de lo implementado (RN-PL-06).
- [ ] "Lo que todavía no está" es completa: revisión legal, 2FA, certificaciones, país y backups, baja de la cuenta.
- [ ] Ninguna frase declara cumplimiento legal ni certificaciones (RN-PL-05, LG-10).
- [ ] Fuente versionada compilada con la página (decisión 1).
- [ ] Bloqueos: PA-02, TLS en el VPS y email de seguridad.
- [ ] Ajustes de #40: huella en vez de "versión cifrada", DD-38 en "Qué datos guardamos", email de seguridad `(TBD)` y 08 corregido.
- [ ] Textos en es-AR con voseo; solo tokens rev. 1; foco visible; objetivos ≥ 44 px; estados con ícono y texto.
