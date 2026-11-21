import { el } from '../../shared/dom';
import { formatearDia } from '../../shared/fechas';
import { icono } from '../../shared/ui/iconos';
import {
  bloquesDelDia,
  conteoPorDia,
  franjasDelDia,
  nombreCompleto,
  nombreCorto,
  rangoDeHoras,
  type BloqueDeGrilla,
  type BloqueoAgenda,
  type FranjaDisponible,
  type TurnoAgenda,
} from './modelo';
import { APARIENCIA, nombreAccesibleTurno, rutaDelTurno } from './tarjeta';
import { diaLocal, horaDeMinutos, horaLocal, minutosLocales } from './tiempo';

/**
 * Vista semanal (C-13, design D4): grilla proporcional de lunes a domingo desde 768 px y lista agrupada por día en
 * mobile, donde la grilla de 7 columnas no se lee.
 */

export interface EntradaSemana {
  readonly lunes: string;
  readonly zona: string;
  readonly turnos: readonly TurnoAgenda[];
  readonly bloqueos: readonly BloqueoAgenda[];
  /** null: sin horario conocido (sin conexión y sin copia en memoria). Se dibuja todo como atendido. */
  readonly franjas: readonly FranjaDisponible[] | null;
  readonly ahora: Date;
  readonly mostrarCancelados: boolean;
}

const CORTOS = ['LUN', 'MAR', 'MIÉ', 'JUE', 'VIE', 'SÁB', 'DOM'];
const LARGOS = ['Lunes', 'Martes', 'Miércoles', 'Jueves', 'Viernes', 'Sábado', 'Domingo'];

export const rutaDelDia = (dia: string) => `/app/agenda?vista=dia&fecha=${dia}`;
const numeroDelDia = (dia: string) => String(Number(dia.slice(8, 10)));
const turnosEnPalabras = (n: number) => (n === 1 ? '1 turno' : `${n} turnos`);

interface DiaDeLaSemana {
  readonly dia: string;
  readonly indice: number;
  readonly cantidad: number;
  readonly atiende: boolean;
}

function diasDe(entrada: EntradaSemana): DiaDeLaSemana[] {
  return conteoPorDia(entrada.lunes, entrada.zona, entrada.turnos, entrada.mostrarCancelados).map(({ dia, cantidad }, indice) => ({
    dia,
    indice,
    cantidad,
    atiende: entrada.franjas === null || franjasDelDia(dia, entrada.franjas).length > 0,
  }));
}

const resumenDelDia = (d: DiaDeLaSemana) => (d.cantidad > 0 ? turnosEnPalabras(d.cantidad) : d.atiende ? 'Sin turnos' : 'No atendés');

/** 1,2 px por minuto (D4): con base 4 px, `--space-1 × 0,3`. Un turno de 20 minutos mide 24 px (WCAG 2.5.8). */
const escala = (minutos: number) => `calc(var(--space-1) * 0.3 * ${minutos})`;
/** Minutos que entran en dos líneas de texto chico (32 px a 1,2 px por minuto). */
const MINUTOS_PARA_DOS_LINEAS = 27;

function bloqueDeTurno(b: Extract<BloqueDeGrilla, { tipo: 'turno' }>, zona: string, desdeRango: number, hastaRango: number): HTMLLIElement | null {
  const desde = Math.max(b.desde, desdeRango);
  const hasta = Math.min(b.hasta, hastaRango);
  if (hasta <= desde) return null;
  const { turno } = b;
  const a = APARIENCIA[turno.estado];
  const nombre = nombreAccesibleTurno(turno, horaLocal(turno.inicio, zona), horaLocal(turno.fin, zona));
  const dosLineas = !b.superpuesto && hasta - desde >= MINUTOS_PARA_DOS_LINEAS;
  const enlace = el(
    'a',
    {
      href: rutaDelTurno(turno.id),
      'data-link': true,
      'data-turno': turno.id,
      'data-superpuesto': b.superpuesto,
      'aria-label': nombre,
      title: nombre,
      class:
        `absolute z-10 block overflow-hidden mx-[calc(var(--space-1)/2)] ${b.superpuesto ? 'px-1' : 'px-2'} rounded-sm text-xs leading-tight no-underline ${a.compacta} ` +
        `${turno.esSobreturno ? 'border-2 border-dashed border-l-4' : 'border border-l-4'} ` +
        `${b.superpuesto ? 'left-1/2 right-0 z-20 shadow-md' : 'inset-x-0'} ${dosLineas || b.superpuesto ? '' : 'truncate whitespace-nowrap'}`,
    },
    turno.cambioPaciente === null ? null : el('span', { 'data-punto-nuevo': true, 'aria-hidden': 'true', class: 'inline-block w-2 h-2 mr-1 rounded-full bg-primario' }),
    // El sobreturno superpuesto tiene media columna: hora arriba y apellido abajo (D4, revisión de Maxi en #27).
    el(
      'span',
      { class: b.superpuesto ? 'block whitespace-nowrap' : '' },
      turno.esSobreturno ? icono('mas', 'inline w-3 h-3 align-[-2px]') : null,
      el('b', { class: 'tabular' }, horaLocal(turno.inicio, zona)),
    ),
    ' ',
    turno.esSobreturno ? null : icono(a.icono, 'inline w-3 h-3 align-[-2px]'),
    ' ',
    el(
      'span',
      { class: `font-semibold ${turno.estado === 'CANCELADO' ? 'line-through' : ''} ${b.superpuesto ? 'block truncate' : ''}` },
      turno.esSobreturno ? turno.paciente.apellido : nombreCorto(turno.paciente),
    ),
    dosLineas ? el('span', { 'data-prestacion': true, class: 'block truncate' }, turno.prestacion) : null,
  );
  enlace.style.top = escala(desde - desdeRango);
  enlace.style.height = escala(hasta - desde);
  if (b.superpuesto) enlace.style.minHeight = 'calc(var(--touch-min) - var(--space-2))';
  return el('li', {}, enlace);
}

function bloqueDeBloqueo(b: Extract<BloqueDeGrilla, { tipo: 'bloqueo' }>, desdeRango: number, hastaRango: number): HTMLLIElement | null {
  const desde = Math.max(b.desde, desdeRango);
  const hasta = Math.min(b.hasta, hastaRango);
  if (hasta <= desde) return null;
  const motivo = b.bloqueo.motivo ?? 'Bloqueado';
  const caja = el(
    'div',
    { 'data-bloqueo': b.bloqueo.id, title: motivo, class: 'absolute inset-x-0 z-10 overflow-hidden mx-[calc(var(--space-1)/2)] px-2 rounded-sm border border-l-4 border-bloqueo-bd fondo-rayado text-xs leading-tight text-bloqueo-fg' },
    el('b', { class: 'tabular' }, horaDeMinutos(b.desde)),
    ' ',
    icono('candado', 'inline w-3 h-3 align-[-2px]'),
    el('span', { class: 'sr-only' }, ` a ${b.hasta >= 24 * 60 ? '24:00' : horaDeMinutos(b.hasta)}, Bloqueo, `),
    ' ',
    motivo,
  );
  caja.style.top = escala(desde - desdeRango);
  caja.style.height = escala(hasta - desde);
  return el('li', {}, caja);
}

export function grillaSemanal(entrada: EntradaSemana): HTMLElement {
  const { zona } = entrada;
  const dias = diasDe(entrada);
  const visibles = entrada.turnos.filter((t) => entrada.mostrarCancelados || t.estado !== 'CANCELADO');
  const rango = rangoDeHoras(entrada.franjas ?? [], visibles, zona);
  const hoy = diaLocal(entrada.ahora, zona);
  const columnas = 'grid grid-cols-[calc(var(--space-7)+var(--space-2))_repeat(7,minmax(0,1fr))]';

  const encabezado = el(
    'div',
    { class: `${columnas} border-b border-borde bg-superficie` },
    el('span', {}),
    ...dias.map((d) =>
      el(
        'a',
        {
          href: rutaDelDia(d.dia),
          'data-link': true,
          'data-encabezado-dia': d.dia,
          'aria-label': `${formatearDia(d.dia, zona)}, ${resumenDelDia(d).replace(/^S/, 's').replace(/^N/, 'n')}`,
          'aria-current': d.dia === hoy ? 'date' : null,
          class: 'flex flex-col items-center justify-center gap-1 min-h-7 py-2 border-l border-borde text-texto no-underline aria-[current=date]:bg-primario-tinte',
        },
        el('span', { class: 'text-xs font-semibold text-texto-tenue' }, CORTOS[d.indice]),
        el('span', { class: `font-bold tabular ${d.dia === hoy ? 'text-primario' : ''}` }, numeroDelDia(d.dia)),
        el('span', { class: 'text-xs text-texto-tenue' }, resumenDelDia(d)),
      ),
    ),
  );

  const alto = escala(rango.hasta - rango.desde);
  const horas = el('div', { 'data-horas': true, 'aria-hidden': 'true', class: 'relative' });
  horas.style.height = alto;
  for (let minuto = rango.desde + 60; minuto < rango.hasta; minuto += 60) {
    const etiqueta = el('span', { class: 'absolute right-2 -translate-y-2 text-xs text-texto-sutil tabular' }, horaDeMinutos(minuto));
    etiqueta.style.top = escala(minuto - rango.desde);
    horas.append(etiqueta);
  }

  const cuerpo = el('div', { class: `${columnas} relative` }, horas);
  for (const d of dias) {
    const columna = el('div', { 'data-columna': d.dia, class: 'relative border-l border-borde bg-superficie-tenue' });
    columna.style.height = alto;
    const atendidos = entrada.franjas === null ? [{ desde: rango.desde, hasta: rango.hasta }] : franjasDelDia(d.dia, entrada.franjas);
    for (const tramo of atendidos) {
      const abierto = el('div', { 'aria-hidden': 'true', class: 'absolute inset-x-0 bg-superficie' });
      abierto.style.top = escala(Math.max(tramo.desde, rango.desde) - rango.desde);
      abierto.style.height = escala(Math.min(tramo.hasta, rango.hasta) - Math.max(tramo.desde, rango.desde));
      columna.append(abierto);
    }
    for (let minuto = rango.desde + 60; minuto < rango.hasta; minuto += 60) {
      const linea = el('div', { 'aria-hidden': 'true', class: 'absolute inset-x-0 border-t border-borde' });
      linea.style.top = escala(minuto - rango.desde);
      columna.append(linea);
    }
    if (!d.atiende && d.cantidad === 0) {
      columna.append(el('div', { 'aria-hidden': 'true', class: 'absolute inset-0 grid place-items-center p-2 text-xs text-center text-texto-tenue' }, 'No atendés'));
    }
    const bloques = bloquesDelDia({ dia: d.dia, zona, turnos: entrada.turnos, bloqueos: entrada.bloqueos, mostrarCancelados: entrada.mostrarCancelados });
    columna.append(
      el(
        'ol',
        { 'aria-label': formatearDia(d.dia, zona), class: 'list-none m-0 p-0' },
        ...bloques.map((b) => (b.tipo === 'turno' ? bloqueDeTurno(b, zona, rango.desde, rango.hasta) : bloqueDeBloqueo(b, rango.desde, rango.hasta))),
      ),
    );
    if (d.dia === hoy) {
      const ahora = minutosLocales(entrada.ahora, zona);
      if (ahora >= rango.desde && ahora <= rango.hasta) {
        const marca = el('div', { 'data-ahora': true, 'aria-hidden': 'true', class: 'absolute inset-x-0 z-30 h-indicador bg-primario' }, el('span', { class: 'absolute -left-1 -top-1 w-2 h-2 rounded-full bg-primario' }));
        marca.style.top = escala(ahora - rango.desde);
        columna.append(marca);
      }
    }
    cuerpo.append(columna);
  }

  return el('div', { 'data-grilla-semanal': true, class: 'bg-superficie border border-borde rounded-lg overflow-hidden' }, encabezado, cuerpo);
}

type EntradaLista = { readonly inicio: number; readonly nodo: HTMLLIElement };

function filaDeTurno(turno: TurnoAgenda, zona: string): HTMLLIElement {
  const a = APARIENCIA[turno.estado];
  const nombre = nombreAccesibleTurno(turno, horaLocal(turno.inicio, zona), horaLocal(turno.fin, zona));
  return el(
    'li',
    {},
    el(
      'a',
      {
        href: rutaDelTurno(turno.id),
        'data-link': true,
        'data-turno': turno.id,
        'aria-label': nombre,
        class:
          `grid grid-cols-[calc(var(--space-7))_minmax(0,1fr)_auto] items-center gap-2 min-h-touch px-3 py-1 rounded-md text-sm text-texto no-underline ` +
          `${turno.esSobreturno ? 'border-2 border-dashed border-l-4' : 'border border-l-4'} ${a.compacta}`,
      },
      el('b', { class: 'tabular whitespace-nowrap' }, turno.esSobreturno ? icono('mas', 'inline w-3 h-3 align-[-1px]') : null, horaLocal(turno.inicio, zona)),
      el(
        'span',
        { class: `min-w-0 truncate text-texto ${turno.estado === 'CANCELADO' ? 'line-through text-texto-tenue' : ''}` },
        turno.cambioPaciente === null ? null : el('span', { 'data-punto-nuevo': true, 'aria-hidden': 'true', class: 'inline-block w-2 h-2 mr-1 rounded-full bg-primario' }),
        nombreCompleto(turno.paciente),
      ),
      el('span', { class: 'inline-flex items-center gap-1 text-xs font-semibold' }, icono(a.icono, 'w-3 h-3 flex-none'), a.texto),
    ),
  );
}

/** `desde`: minuto local en que el bloqueo empieza ese día (0 si viene del día anterior). */
function filaDeBloqueo(bloqueo: BloqueoAgenda, desde: number): HTMLLIElement {
  return el(
    'li',
    {},
    el(
      'div',
      { 'data-bloqueo': bloqueo.id, class: 'grid grid-cols-[calc(var(--space-7))_minmax(0,1fr)_auto] items-center gap-2 min-h-touch px-3 py-1 rounded-md border border-l-4 border-bloqueo-bd fondo-rayado text-sm text-bloqueo-fg' },
      el('b', { class: 'tabular' }, horaDeMinutos(desde)),
      el('span', { class: 'min-w-0 truncate' }, bloqueo.motivo ?? 'Bloqueado'),
      el('span', { class: 'inline-flex items-center gap-1 text-xs font-semibold' }, icono('candado', 'w-3 h-3 flex-none'), 'Bloqueo'),
    ),
  );
}

export function listaSemanal(entrada: EntradaSemana): HTMLElement {
  const { zona } = entrada;
  const hoy = diaLocal(entrada.ahora, zona);
  const secciones = diasDe(entrada).map((d) => {
    const turnos: EntradaLista[] = entrada.turnos
      .filter((t) => diaLocal(t.inicio, zona) === d.dia && (entrada.mostrarCancelados || t.estado !== 'CANCELADO'))
      .map((t) => ({ inicio: Date.parse(t.inicio), nodo: filaDeTurno(t, zona) }));
    const bloqueos: EntradaLista[] = bloquesDelDia({ dia: d.dia, zona, turnos: [], bloqueos: entrada.bloqueos, mostrarCancelados: true }).flatMap((b) =>
      b.tipo === 'bloqueo' ? [{ inicio: Date.parse(b.bloqueo.inicio), nodo: filaDeBloqueo(b.bloqueo, b.desde) }] : [],
    );
    const filas = [...turnos, ...bloqueos].sort((x, y) => x.inicio - y.inicio).map((x) => x.nodo);
    return el(
      'section',
      { 'data-dia': d.dia },
      el(
        'h3',
        { class: 'm-0 mb-2 text-base' },
        el(
          'a',
          { href: rutaDelDia(d.dia), 'data-link': true, 'aria-current': d.dia === hoy ? 'date' : null, class: 'inline-flex items-center gap-1 min-h-touch text-texto no-underline aria-[current=date]:text-primario' },
          `${LARGOS[d.indice] ?? ''} ${numeroDelDia(d.dia)}`,
          el('small', { class: 'font-normal text-sm text-texto-tenue' }, ` · ${resumenDelDia(d)}`),
          icono('derecha', 'w-icono-sm h-icono-sm text-texto-tenue'),
        ),
      ),
      filas.length === 0
        ? el('p', { 'data-vacio': true, class: 'm-0 px-3 py-2 rounded-md bg-superficie-tenue text-sm text-texto-tenue' }, d.atiende ? 'Sin turnos' : 'No atendés')
        : el('ol', { class: 'list-none m-0 p-0 grid gap-1' }, ...filas),
    );
  });
  return el('div', { 'data-lista-semanal': true, class: 'grid gap-4' }, ...secciones);
}
