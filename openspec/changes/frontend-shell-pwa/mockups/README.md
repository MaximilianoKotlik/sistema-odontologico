# Mockups · C-06 frontend-shell-pwa (app-shell)

- Design system revision: 1
- Approval: pending
- Preview: rendered (2026-11-20) · 390×844, 360×780 y 1440×900

## Brief

- **Audiencia**: el odontólogo, sobre todo desde el celular entre pacientes.
- **Tarea**: moverse entre secciones de la app privada y entender siempre en qué estado está la pantalla (cargando, vacía, con error, sin conexión).
- **Pantallas y estados**: un solo archivo, `app-shell.html`, con el estado en la URL:

| Estado | URL | Qué muestra |
|---|---|---|
| Contenido | `app-shell.html` | Shell con agenda ilustrativa (la agenda real es de C-13) |
| Carga | `?estado=carga` | Esqueleto con `aria-busy`, navegación operable |
| Vacío | `?estado=vacio` | "Todavía no tenés turnos hoy" + Copiar mi link / Nuevo turno |
| Error | `?estado=error` | "No pudimos cargar tu agenda" + Reintentar + código |
| Sin conexión | `?estado=offline` | Aviso persistente con la hora de guardado; "Nuevo turno" deshabilitado |
| Sin conexión, día no guardado | `?estado=offline-sin-cache` | "Este día no está guardado" |
| Menú "Más" (mobile) | `?estado=mas` | Hoja inferior: Registro de cambios, Exportar mis datos, Cerrar sesión |
| No encontrada | `?estado=404` | "No encontramos esta página" + Ir a mi agenda |

- **Responsive**: < 768 px barra inferior (Agenda, Bloqueos, Configuración, Más) y botón "Nuevo turno" solo ícono; ≥ 768 px barra lateral con todas las secciones, cuenta y cierre de sesión, y selector Día/Semana.
- **Sección activa**: fondo tinte + barra de 3 px + ícono relleno + texto en negrita (no depende solo del color).
- **Accesibilidad**: "Saltar al contenido", destinos de 88×64 px en mobile (≥ 44 px), foco del sistema de diseño, `aria-current="page"`, hoja "Más" como `dialog` modal, movimiento reducido respetado por `tokens.css`.
- **Tokens/assets**: importa `design-system/tokens.css` y `design-system/brand.js`; sin colores ni tamaños sueltos.

## (TBD)
- Nombre y logo del producto: provisorios (el isotipo del diente es un placeholder, se rotula en la barra lateral).
- Datos de la cuenta ("Dra. Ejemplo · Consultorio Demo") y pacientes ("Ana Ejemplo", "Mateo Prueba", "Sofía Demo") son ficticios.
- Hora del aviso sin conexión (10:32) es de ejemplo.

## Cómo verlo
Desde la raíz del repo:

```bash
python -m http.server 8765 --bind 127.0.0.1
```

Abrí `http://127.0.0.1:8765/openspec/changes/frontend-shell-pwa/mockups/app-shell.html` y agregá `?estado=...` según la tabla. También funciona abriendo el archivo directo en el navegador.

## Capturas
`previews/mobile-390x844-*.png`, `previews/mobile-360x780-offline.png` y `previews/desktop-1440x900-*.png` (página completa). Verificado sin scroll horizontal a 360, 390 y 1440 px.
