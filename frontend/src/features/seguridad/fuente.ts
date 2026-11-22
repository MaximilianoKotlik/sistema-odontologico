/**
 * Fuente versionada de `/seguridad` (C-22, design D1; RN-PL-06): `src/contenido/seguridad.json`. Cada afirmación declara
 * su respaldo (regla, decisión, change o test) y los changes que tienen que estar `[x]` para que sea cierta. El
 * respaldo nunca se pinta: lo usan los tests (D4) y `npm run seguridad:revisar` (D3).
 *
 * Este módulo no importa nada en tiempo de ejecución: lo usa también el script de revisión, que corre con Node.
 */

/** Un tramo del texto: plano, resaltado, enlace o un dato que falta definir (`(TBD)`). */
export type Segmento = string | { readonly fuerte: string } | { readonly enlace: string; readonly href: string } | { readonly tbd: string };

export interface Afirmacion {
  readonly texto: readonly Segmento[];
  readonly respaldo: readonly string[];
  /** Changes (`C-NN`) que tienen que estar completados en CHANGES.md para que la frase sea cierta. */
  readonly requiere: readonly string[];
}

export type Bloque =
  | { readonly tipo: 'parrafo'; readonly estilo?: 'nota'; readonly afirmacion: Afirmacion }
  | { readonly tipo: 'lista'; readonly items: readonly Afirmacion[] }
  | { readonly tipo: 'roles'; readonly filas: readonly { readonly quien: string; readonly afirmacion: Afirmacion }[] }
  | { readonly tipo: 'faltan'; readonly items: readonly { readonly titulo: string; readonly afirmacion: Afirmacion }[] };

export type IconoDeTema = 'personas' | 'credencial' | 'muro' | 'historial' | 'descargar' | 'candado' | 'servidor' | 'reloj-arena';

export interface Tema {
  readonly id: string;
  readonly titulo: string;
  readonly icono: IconoDeTema;
  readonly bloques: readonly Bloque[];
}

export interface FuenteSeguridad {
  readonly titulo: string;
  readonly bajada: string;
  /** Día calendario (`YYYY-MM-DD`) de la última revisión del contenido. */
  readonly actualizadoEn: string;
  readonly revision: string;
  readonly aviso: Afirmacion;
  readonly temas: readonly Tema[];
  readonly enlacesDelPie: readonly { readonly texto: string; readonly href: string }[];
}

const ICONOS: readonly string[] = ['personas', 'credencial', 'muro', 'historial', 'descargar', 'candado', 'servidor', 'reloj-arena'];
const esObjeto = (v: unknown): v is Record<string, unknown> => typeof v === 'object' && v !== null && !Array.isArray(v);
const esTexto = (v: unknown): v is string => typeof v === 'string' && v.trim() !== '';
const listaDeTextos = (v: unknown): boolean => Array.isArray(v) && v.every(esTexto);

function esSegmento(v: unknown): v is Segmento {
  if (esTexto(v)) return true;
  if (!esObjeto(v)) return false;
  if ('fuerte' in v) return esTexto(v['fuerte']);
  if ('tbd' in v) return esTexto(v['tbd']);
  return esTexto(v['enlace']) && esTexto(v['href']);
}

function esAfirmacion(v: unknown): v is Afirmacion {
  return (
    esObjeto(v) &&
    Array.isArray(v['texto']) &&
    v['texto'].length > 0 &&
    v['texto'].every(esSegmento) &&
    listaDeTextos(v['respaldo']) &&
    Array.isArray(v['requiere']) &&
    v['requiere'].every((c) => typeof c === 'string' && /^C-\d{2}$/.test(c))
  );
}

function esBloque(v: unknown): v is Bloque {
  if (!esObjeto(v)) return false;
  switch (v['tipo']) {
    case 'parrafo':
      return esAfirmacion(v['afirmacion']) && (v['estilo'] === undefined || v['estilo'] === 'nota');
    case 'lista':
      return Array.isArray(v['items']) && v['items'].length > 0 && v['items'].every(esAfirmacion);
    case 'roles':
      return Array.isArray(v['filas']) && v['filas'].every((f) => esObjeto(f) && esTexto(f['quien']) && esAfirmacion(f['afirmacion']));
    case 'faltan':
      return Array.isArray(v['items']) && v['items'].every((f) => esObjeto(f) && esTexto(f['titulo']) && esAfirmacion(f['afirmacion']));
    default:
      return false;
  }
}

function esTema(v: unknown): v is Tema {
  return esObjeto(v) && esTexto(v['id']) && esTexto(v['titulo']) && typeof v['icono'] === 'string' && ICONOS.includes(v['icono']) && Array.isArray(v['bloques']) && v['bloques'].every(esBloque);
}

export function esFuenteSeguridad(v: unknown): v is FuenteSeguridad {
  return (
    esObjeto(v) &&
    esTexto(v['titulo']) &&
    esTexto(v['bajada']) &&
    typeof v['actualizadoEn'] === 'string' &&
    /^\d{4}-\d{2}-\d{2}$/.test(v['actualizadoEn']) &&
    esTexto(v['revision']) &&
    esAfirmacion(v['aviso']) &&
    Array.isArray(v['temas']) &&
    v['temas'].every(esTema) &&
    Array.isArray(v['enlacesDelPie']) &&
    v['enlacesDelPie'].every((e) => esObjeto(e) && esTexto(e['texto']) && esTexto(e['href']))
  );
}

/** Todas las afirmaciones de la fuente, en el orden de la página. */
export function afirmaciones(fuente: FuenteSeguridad): Afirmacion[] {
  const lista: Afirmacion[] = [fuente.aviso];
  for (const tema of fuente.temas) {
    for (const bloque of tema.bloques) {
      if (bloque.tipo === 'parrafo') lista.push(bloque.afirmacion);
      else if (bloque.tipo === 'lista') lista.push(...bloque.items);
      else if (bloque.tipo === 'roles') lista.push(...bloque.filas.map((f) => f.afirmacion));
      else lista.push(...bloque.items.map((f) => f.afirmacion));
    }
  }
  return lista;
}

/** Texto visible de una afirmación; un `(TBD)` se lee como en la página. */
export function textoPlano(texto: readonly Segmento[]): string {
  return texto
    .map((s) => (typeof s === 'string' ? s : 'fuerte' in s ? s.fuerte : 'tbd' in s ? `(TBD) ${s.tbd}` : s.enlace))
    .join('');
}

/** Afirmaciones sin respaldo, por su texto (D4). */
export function sinRespaldo(fuente: FuenteSeguridad): string[] {
  return afirmaciones(fuente)
    .filter((a) => a.respaldo.length === 0 || a.respaldo.every((r) => r.trim() === ''))
    .map((a) => textoPlano(a.texto));
}

/**
 * Frases que la página no puede decir (RN-PL-05, LG-10). "Certificaciones ... ISO 27001" está permitido solo para
 * decir que no las hay: el patrón prohibido es afirmar "certificado/a" o "certificación ISO".
 */
const PROHIBIDAS: readonly RegExp[] = [/\bcertificad[oa]s?\b/i, /certificaci[oó]n(es)? ISO/i, /\bcumpl\w* (con )?la ley\b/i, /100\s?%\s?segur/i, /contraseñas? cifradas?/i, /no guardamos datos de salud/i];

/** Changes marcados `[x]` en CHANGES.md (`### [C-NN] …` seguido de `- **Estado**: \`[x]\``). */
export function changesCompletados(changesMd: string): Set<string> {
  const completados = new Set<string>();
  for (const coincidencia of changesMd.matchAll(/^### \[(C-\d{2})\][^\n]*\r?\n- \*\*Estado\*\*: `\[x\]`/gm)) {
    if (coincidencia[1] !== undefined) completados.add(coincidencia[1]);
  }
  return completados;
}

function marcadores(texto: readonly Segmento[]): string[] {
  return texto.flatMap((s) => (typeof s !== 'string' && 'tbd' in s ? [s.tbd] : []));
}

/**
 * Revisión antes de publicar (D3, RN-PL-06): una frase que depende de un change sin `[x]`, una sin respaldo y los
 * `(TBD)` pendientes. Lista vacía = lista para publicar.
 */
export function revisarFuente(fuente: FuenteSeguridad, changesMd: string): string[] {
  const completados = changesCompletados(changesMd);
  const problemas: string[] = [];
  for (const a of afirmaciones(fuente)) {
    const texto = textoPlano(a.texto);
    for (const change of a.requiere) {
      if (!completados.has(change)) problemas.push(`Depende de ${change}, que no está completado en CHANGES.md: "${texto}"`);
    }
  }
  problemas.push(...sinRespaldo(fuente).map((texto) => `No tiene respaldo: "${texto}"`));
  const pendientes = afirmaciones(fuente).flatMap((a) => marcadores(a.texto));
  if (pendientes.length > 0) problemas.push(`Quedan marcadores (TBD): ${pendientes.join(', ')}`);
  return problemas;
}

export function frasesProhibidas(fuente: FuenteSeguridad): string[] {
  const textos = [fuente.titulo, fuente.bajada, ...afirmaciones(fuente).map((a) => textoPlano(a.texto))];
  return textos.filter((t) => PROHIBIDAS.some((p) => p.test(t)));
}
