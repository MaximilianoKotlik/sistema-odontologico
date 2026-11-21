// brand.js es un script clásico que define window.BRAND_NAME: se importa sin copiarlo (única fuente del nombre).
import '../../../design-system/brand.js';

/** Nombre provisorio del producto (TBD), tomado de design-system/brand.js. */
export function nombreDeMarca(): string {
  const valor: unknown = Reflect.get(window, 'BRAND_NAME');
  if (typeof valor !== 'string' || valor === '') {
    throw new Error('design-system/brand.js no definió BRAND_NAME');
  }
  return valor;
}
