/**
 * Íconos del app-shell (trazos tomados del mockup aprobado de C-06). Siempre decorativos:
 * se ocultan a lectores de pantalla porque acompañan un texto visible o un aria-label.
 */
const L = 'fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"';

const TRAZOS = {
  diente: `<path d="M12 4.5c-4 0-7 1.6-7 5.6 0 2.4.8 4 1.6 7.2.8 3.2.8 5.2 2 5.2 1.6 0 1.6-4 3.4-4s1.8 4 3.4 4c1.2 0 1.2-2 2-5.2.8-3.2 1.6-4.8 1.6-7.2 0-4-3-5.6-7-5.6z" ${L}/>`,
  calendario: `<rect x="3.5" y="5" width="17" height="15.5" rx="3" ${L}/><path d="M3.5 10h17M8 3v4M16 3v4" ${L}/>`,
  'calendario-relleno': `<path d="M6.5 5h11a3 3 0 013 3v2h-17V8a3 3 0 013-3z" fill="currentColor"/><rect x="3.5" y="5" width="17" height="15.5" rx="3" ${L}/><path d="M8 3v4M16 3v4" ${L}/><rect x="7" y="13" width="4" height="3.5" rx="1" fill="currentColor"/>`,
  candado: `<rect x="5" y="10.5" width="14" height="10" rx="2.5" ${L}/><path d="M8.5 10.5V8a3.5 3.5 0 017 0v2.5" ${L}/>`,
  'candado-relleno': `<rect x="5" y="10.5" width="14" height="10" rx="2.5" fill="currentColor"/><path d="M8.5 10.5V8a3.5 3.5 0 017 0v2.5" ${L}/>`,
  engranaje: `<circle cx="12" cy="12" r="3" ${L}/><path d="M12 3.5v2.2M12 18.3v2.2M3.5 12h2.2M18.3 12h2.2M6 6l1.6 1.6M16.4 16.4L18 18M6 18l1.6-1.6M16.4 7.6L18 6" ${L}/>`,
  'engranaje-relleno': `<circle cx="12" cy="12" r="4.2" fill="currentColor"/><path d="M12 3.5v2.2M12 18.3v2.2M3.5 12h2.2M18.3 12h2.2M6 6l1.6 1.6M16.4 16.4L18 18M6 18l1.6-1.6M16.4 7.6L18 6" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round"/>`,
  'mas-opciones': '<circle cx="6" cy="12" r="1.8" fill="currentColor"/><circle cx="12" cy="12" r="1.8" fill="currentColor"/><circle cx="18" cy="12" r="1.8" fill="currentColor"/>',
  historial: `<path d="M4.5 12a7.5 7.5 0 102.2-5.3L4.5 9" ${L}/><path d="M4.5 4.5V9H9M12 8v4l2.8 1.8" ${L}/>`,
  'historial-relleno': '<circle cx="12" cy="12" r="7.5" fill="currentColor"/><path d="M12 8v4l2.8 1.8" fill="none" stroke="var(--color-surface)" stroke-width="2" stroke-linecap="round"/>',
  descargar: `<path d="M12 4v11M7.5 10.5L12 15l4.5-4.5M5 19.5h14" ${L}/>`,
  salir: `<path d="M14 4.5H7a2 2 0 00-2 2v11a2 2 0 002 2h7M17 8.5L20.5 12 17 15.5M20.5 12H10" ${L}/>`,
  mas: '<path d="M12 5v14M5 12h14" stroke="currentColor" stroke-width="2.2" stroke-linecap="round"/>',
  izquierda: '<path d="M14.5 6l-6 6 6 6" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>',
  derecha: '<path d="M9.5 6l6 6-6 6" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>',
  'sin-conexion': `<path d="M3 4l18 16M8.5 16.5a5 5 0 017 0M5 12.8a10 10 0 015.2-2.6M14.6 10.3A10 10 0 0119 12.8M2 9.3a14.5 14.5 0 014.2-2.6M11 5.6a14.5 14.5 0 0111 3.7" ${L}/><circle cx="12" cy="19.5" r="1.2" fill="currentColor"/>`,
  alerta: `<circle cx="12" cy="12" r="9" ${L}/><path d="M12 7.5v5.5M12 16.2v.1" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round"/>`,
  reintentar: `<path d="M19.5 12a7.5 7.5 0 11-2.2-5.3L19.5 9M19.5 4.5V9H15" ${L}/>`,
  buscar: `<circle cx="11" cy="11" r="6.5" ${L}/><path d="M16 16l4 4" ${L}/>`,
  reloj: `<circle cx="12" cy="12" r="8" ${L}/><path d="M12 8v4l2.5 1.5" ${L}/>`,
  check: '<path d="M5 12.5l4.5 4.5L19 7.5" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"/>',
  cruz: '<path d="M6.5 6.5l11 11M17.5 6.5l-11 11" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round"/>',
  // Trazos del mockup aprobado de C-07 (#14).
  ojo: `<path d="M2.5 12S6 5.5 12 5.5 21.5 12 21.5 12 18 18.5 12 18.5 2.5 12 2.5 12z" ${L}/><circle cx="12" cy="12" r="3" ${L}/>`,
  llave: `<circle cx="8" cy="15" r="4" ${L}/><path d="M11 12l8-8M16 7l2.5 2.5M14 9l2 2" ${L}/>`,
  punto: `<circle cx="12" cy="12" r="7" ${L}/>`,
  info: `<circle cx="12" cy="12" r="9" ${L}/><path d="M12 11v5.5M12 7.8v.1" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round"/>`,
  'check-circulo': `<circle cx="12" cy="12" r="9" ${L}/><path d="M8 12.3l2.7 2.7L16 9.5" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>`,
  sobre: `<rect x="3" y="5.5" width="18" height="13" rx="2.5" ${L}/><path d="M3.8 7l8.2 6 8.2-6" ${L}/>`,
  // Trazos del mockup aprobado de C-09 (#23).
  lista: `<path d="M9 6.5h11M9 12h11M9 17.5h11" ${L}/><circle cx="4.5" cy="6.5" r="1.2" fill="currentColor"/><circle cx="4.5" cy="12" r="1.2" fill="currentColor"/><circle cx="4.5" cy="17.5" r="1.2" fill="currentColor"/>`,
  lapiz: `<path d="M4 20l1-4.5L15.5 5a2.1 2.1 0 013 3L8 18.5 4 20z" ${L}/><path d="M13.5 7l3.5 3.5" ${L}/>`,
  copiar: `<rect x="8.5" y="8.5" width="11" height="11" rx="2.5" ${L}/><path d="M15.5 8.5V6a2 2 0 00-2-2H6a2 2 0 00-2 2v7.5a2 2 0 002 2h2.5" ${L}/>`,
  subir: '<path d="M6 14.5l6-6 6 6" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>',
  bajar: '<path d="M6 9.5l6 6 6-6" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>',
  pausa: `<circle cx="12" cy="12" r="9" ${L}/><path d="M10 9v6M14 9v6" ${L}/>`,
  compartir: `<circle cx="18" cy="5.5" r="2.5" ${L}/><circle cx="6" cy="12" r="2.5" ${L}/><circle cx="18" cy="18.5" r="2.5" ${L}/><path d="M8.2 10.8l7.6-4.1M8.2 13.2l7.6 4.1" ${L}/>`,
  'ojo-tachado': `<path d="M3 4l18 16M10.6 6A9.6 9.6 0 0112 5.5c6 0 9.5 6.5 9.5 6.5a17 17 0 01-3 3.7M6.5 7.6A17 17 0 002.5 12S6 18.5 12 18.5c1.6 0 3-.4 4.2-1" ${L}/>`,
  'link-roto': `<path d="M10 14a4 4 0 005.7 0l3-3a4 4 0 00-5.7-5.7l-1 1M14 10a4 4 0 00-5.7 0l-3 3a4 4 0 005.7 5.7l1-1M4 4l16 16" ${L}/>`,
} as const satisfies Record<string, string>;

export type NombreIcono = keyof typeof TRAZOS;

/** SVG decorativo; el tamaño lo define la clase (por defecto, el token de ícono). */
export function icono(nombre: NombreIcono, clase = 'w-icono h-icono flex-none'): SVGSVGElement {
  const svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
  svg.setAttribute('viewBox', '0 0 24 24');
  svg.setAttribute('aria-hidden', 'true');
  svg.setAttribute('focusable', 'false');
  svg.setAttribute('class', clase);
  // Contenido estático y propio (constante de este módulo), nunca datos de usuario.
  svg.innerHTML = TRAZOS[nombre];
  return svg;
}
