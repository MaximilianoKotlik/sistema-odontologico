/**
 * Conversor mínimo de Markdown para los borradores de `docs/legal/` (C-07, design D9, aprobada por Maxi en #37).
 *
 * Arma el DOM con `createElement` y `textContent`: nunca construye strings de HTML ni usa `innerHTML`, así que un
 * `<script>` o un `<img onerror>` escrito en el Markdown termina como texto visible. Cubre solo el subconjunto que
 * usan esos archivos: títulos, párrafos, listas, citas, tablas, negrita, cursiva, código en línea y links.
 * Los links absolutos se aceptan solo con `https:` o `mailto:`; los demás (y los relativos al repo) quedan como texto.
 */

const PROTOCOLOS_PERMITIDOS = new Set(['https:', 'mailto:']);

function enlaceSeguro(texto: string, destino: string): Node {
  let url: URL;
  try {
    url = new URL(destino);
  } catch {
    return document.createTextNode(texto); // relativo (p. ej. a knowledge-base/): no existe en la app publicada
  }
  if (!PROTOCOLOS_PERMITIDOS.has(url.protocol)) return document.createTextNode(texto);
  const a = document.createElement('a');
  a.setAttribute('href', url.href);
  if (url.protocol === 'https:') a.setAttribute('rel', 'noopener noreferrer');
  a.className = 'text-primario underline underline-offset-2';
  a.textContent = texto;
  return a;
}

// Orden de la alternancia: código, link, negrita, cursiva. Cada token se convierte en nodos, nunca en HTML.
const EN_LINEA = /`([^`]+)`|\[([^\]]+)\]\(([^)\s]+)\)|\*\*([^*]+)\*\*|\*([^*]+)\*/g;

function enLinea(texto: string): Node[] {
  const nodos: Node[] = [];
  let desde = 0;
  for (const coincidencia of texto.matchAll(EN_LINEA)) {
    const inicio = coincidencia.index;
    if (inicio > desde) nodos.push(document.createTextNode(texto.slice(desde, inicio)));
    const [, codigo, textoLink, destino, negrita, cursiva] = coincidencia;
    if (codigo !== undefined) {
      const c = document.createElement('code');
      c.className = 'font-mono text-sm';
      c.textContent = codigo;
      nodos.push(c);
    } else if (textoLink !== undefined && destino !== undefined) {
      nodos.push(enlaceSeguro(textoLink, destino));
    } else if (negrita !== undefined) {
      const b = document.createElement('strong');
      b.append(...enLinea(negrita));
      nodos.push(b);
    } else if (cursiva !== undefined) {
      const i = document.createElement('em');
      i.append(...enLinea(cursiva));
      nodos.push(i);
    }
    desde = inicio + coincidencia[0].length;
  }
  if (desde < texto.length) nodos.push(document.createTextNode(texto.slice(desde)));
  return nodos;
}

function elemento<K extends keyof HTMLElementTagNameMap>(etiqueta: K, clase: string, hijos: Node[]): HTMLElementTagNameMap[K] {
  const nodo = document.createElement(etiqueta);
  if (clase !== '') nodo.className = clase;
  nodo.append(...hijos);
  return nodo;
}

const CLASES_TITULO = ['mt-0 mb-4 text-2xl', 'mt-6 mb-3 text-xl', 'mt-5 mb-2 text-lg'] as const;
const ES_ITEM = /^(\s*)([-*]|\d+\.)\s+(.*)$/;
const ES_FILA = /^\|.*\|\s*$/;
const ES_SEPARADOR = /^\|(\s*:?-+:?\s*\|)+\s*$/;

const celdas = (fila: string) =>
  fila
    .trim()
    .replace(/^\||\|$/g, '')
    .split('|')
    .map((celda) => celda.trim());

function tabla(filas: string[]): HTMLElement {
  const [encabezado = '', , ...cuerpo] = filas;
  const thead = elemento('thead', '', [elemento('tr', '', celdas(encabezado).map((c) => elemento('th', 'text-left font-semibold p-2 border-b border-borde-fuerte', enLinea(c))))]);
  const tbody = elemento(
    'tbody',
    '',
    cuerpo.map((fila) => elemento('tr', '', celdas(fila).map((c) => elemento('td', 'align-top p-2 border-b border-borde', enLinea(c))))),
  );
  const t = elemento('table', 'w-full border-collapse text-sm', [thead, tbody]);
  return elemento('div', 'mb-4 overflow-x-auto', [t]);
}

export function renderizarMarkdown(markdown: string): DocumentFragment {
  const fragmento = document.createDocumentFragment();
  const lineas = markdown.replace(/\r\n?/g, '\n').split('\n');
  let i = 0;

  while (i < lineas.length) {
    const linea = lineas[i] ?? '';
    if (linea.trim() === '') {
      i += 1;
      continue;
    }

    const titulo = /^(#{1,3})\s+(.*)$/.exec(linea);
    if (titulo !== null) {
      const nivel = (titulo[1] ?? '#').length as 1 | 2 | 3;
      fragmento.append(elemento(`h${nivel}`, CLASES_TITULO[nivel - 1] ?? '', enLinea(titulo[2] ?? '')));
      i += 1;
      continue;
    }

    if (linea.startsWith('>')) {
      const partes: string[] = [];
      while (i < lineas.length && (lineas[i] ?? '').startsWith('>')) {
        partes.push((lineas[i] ?? '').replace(/^>\s?/, ''));
        i += 1;
      }
      const parrafo = elemento('p', 'm-0', enLinea(partes.join(' ').trim()));
      fragmento.append(elemento('blockquote', 'mx-0 mt-0 mb-4 px-4 py-3 rounded-md border border-sobreturno-bd bg-sobreturno-bg text-sobreturno-fg', [parrafo]));
      continue;
    }

    if (ES_FILA.test(linea) && ES_SEPARADOR.test(lineas[i + 1] ?? '')) {
      const filas: string[] = [];
      while (i < lineas.length && ES_FILA.test(lineas[i] ?? '')) {
        filas.push(lineas[i] ?? '');
        i += 1;
      }
      fragmento.append(tabla(filas));
      continue;
    }

    const item = ES_ITEM.exec(linea);
    if (item !== null) {
      const ordenada = /\d/.test(item[2] ?? '');
      const lista = elemento(ordenada ? 'ol' : 'ul', `mt-0 mb-4 pl-5 grid gap-1 ${ordenada ? 'list-decimal' : 'list-disc'}`, []);
      while (i < lineas.length) {
        const actual = ES_ITEM.exec(lineas[i] ?? '');
        if (actual === null || /\d/.test(actual[2] ?? '') !== ordenada) break;
        const texto = [actual[3] ?? ''];
        i += 1;
        // Continuación del ítem: líneas con sangría que no empiezan otro ítem.
        while (i < lineas.length && /^\s+\S/.test(lineas[i] ?? '') && ES_ITEM.exec(lineas[i] ?? '') === null) {
          texto.push((lineas[i] ?? '').trim());
          i += 1;
        }
        lista.append(elemento('li', '', enLinea(texto.join(' '))));
      }
      fragmento.append(lista);
      continue;
    }

    const partes: string[] = [];
    while (i < lineas.length) {
      const actual = lineas[i] ?? '';
      if (actual.trim() === '' || /^(#{1,3}\s|>)/.test(actual) || ES_ITEM.test(actual) || (ES_FILA.test(actual) && ES_SEPARADOR.test(lineas[i + 1] ?? ''))) break;
      partes.push(actual.trim());
      i += 1;
    }
    fragmento.append(elemento('p', 'mt-0 mb-4', enLinea(partes.join(' '))));
  }

  return fragmento;
}
