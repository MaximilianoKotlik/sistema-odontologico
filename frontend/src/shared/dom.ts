/** Helpers mínimos para construir DOM sin framework (design D4). */

export type Atributo = string | number | boolean | null | undefined;
export type Hijo = Node | string | null | undefined | false;

/**
 * Crea un elemento con atributos e hijos. `true` agrega el atributo vacío; `false`, `null` y
 * `undefined` lo omiten. Los textos se insertan como nodos de texto (nunca como HTML).
 */
export function el<K extends keyof HTMLElementTagNameMap>(
  etiqueta: K,
  atributos: Readonly<Record<string, Atributo>> = {},
  ...hijos: Hijo[]
): HTMLElementTagNameMap[K] {
  const elemento = document.createElement(etiqueta);
  for (const [nombre, valor] of Object.entries(atributos)) {
    if (valor === false || valor === null || valor === undefined) continue;
    elemento.setAttribute(nombre, valor === true ? '' : String(valor));
  }
  elemento.append(...hijos.filter((h): h is Node | string => h !== null && h !== undefined && h !== false));
  return elemento;
}

let contadorIds = 0;
/** Id único y estable dentro de la página, para asociar etiquetas y descripciones. */
export function idUnico(prefijo: string): string {
  contadorIds += 1;
  return `${prefijo}-${contadorIds}`;
}

/** Elementos que pueden recibir foco con Tab dentro de un contenedor. */
export function enfocables(contenedor: ParentNode): HTMLElement[] {
  const selector = 'a[href], button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])';
  return [...contenedor.querySelectorAll<HTMLElement>(selector)].filter((e) => !e.closest('[hidden]'));
}
