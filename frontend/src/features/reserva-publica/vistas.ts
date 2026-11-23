import { el, type Hijo } from '../../shared/dom';
import { icono, type NombreIcono } from '../../shared/ui/iconos';
import { diaElegible, etiquetaDia, type DiaDeHuecos } from './dias';
import { agruparHorarios, type Horario } from './horarios';
import { hrefTelefono, telefonoLegible } from './telefono';
import { nombreDeZona } from './zona';

/**
 * Piezas de vista de las páginas del paciente (C-16), compartidas por la reserva y la reprogramación. Siguen el
 * mockup aprobado en #25 y el patrón "Reserva del paciente" de design-system/components.html, solo con tokens.
 */

export interface DatosDelConsultorio {
  readonly nombre: string;
  readonly direccion: string | null;
  readonly telefono: string | null;
}

/** Llena la cabecera del layout con nombre, dirección y teléfono (DD-33). El nombre es el h1 solo si se pide. */
export function pintarCabecera(cabecera: HTMLElement, consultorio: DatosDelConsultorio, comoTitulo: boolean): void {
  cabecera.hidden = false;
  cabecera.replaceChildren(
    el(
      'div',
      { class: 'mx-auto w-full max-w-xl px-4 py-4 md:py-5 grid gap-1' },
      el(comoTitulo ? 'h1' : 'p', { class: 'm-0 text-xl font-bold text-texto' }, consultorio.nombre),
      el(
        'div',
        { class: 'flex flex-wrap gap-x-4 text-sm text-texto-tenue' },
        consultorio.direccion === null
          ? null
          : el('span', { class: 'inline-flex items-center gap-2 min-h-touch' }, icono('ubicacion', 'w-icono-sm h-icono-sm flex-none'), consultorio.direccion),
        consultorio.telefono === null
          ? null
          : el(
              'a',
              { href: hrefTelefono(consultorio.telefono), class: 'inline-flex items-center gap-2 min-h-touch font-semibold text-primario' },
              icono('telefono', 'w-icono-sm h-icono-sm flex-none'),
              telefonoLegible(consultorio.telefono),
            ),
      ),
    ),
  );
}

export function ocultarCabecera(cabecera: HTMLElement): void {
  cabecera.hidden = true;
  cabecera.replaceChildren();
}

export type TonoNota = 'peligro' | 'info' | 'advertencia' | 'exito';

const TONOS: Record<TonoNota, { clases: string; icono: NombreIcono }> = {
  peligro: { clases: 'bg-peligro-fondo border-cancelado-bd text-cancelado-fg', icono: 'alerta' },
  info: { clases: 'bg-primario-tinte border-reservado-bd text-reservado-fg', icono: 'info' },
  advertencia: { clases: 'bg-sobreturno-bg border-sobreturno-bd text-sobreturno-fg', icono: 'reloj' },
  exito: { clases: 'bg-confirmado-bg border-confirmado-bd text-confirmado-fg', icono: 'check-circulo' },
};

/** Aviso en la página, con ícono y texto (nunca solo color). `role="alert"` para errores, `status` para el resto. */
export function nota(
  tono: TonoNota,
  titulo: string,
  cuerpo: Hijo = null,
  opciones: { readonly role?: 'alert' | 'status'; readonly icono?: NombreIcono; readonly atributos?: Readonly<Record<string, string | boolean>> } = {},
): HTMLElement {
  const { clases, icono: porDefecto } = TONOS[tono];
  return el(
    'div',
    { role: opciones.role ?? (tono === 'peligro' ? 'alert' : 'status'), class: `flex gap-3 items-start px-4 py-3 mb-4 rounded-md border text-sm ${clases}`, ...opciones.atributos },
    icono(opciones.icono ?? porDefecto, 'w-icono h-icono flex-none mt-1'),
    el('div', { class: 'min-w-0' }, el('strong', { class: 'block text-base' }, titulo), cuerpo === null ? null : el('div', {}, cuerpo)),
  );
}

export interface OpcionesResultado {
  readonly icono: NombreIcono;
  readonly tono: 'exito' | 'neutro';
  readonly titulo: string;
  /** h1 en las pantallas de resultado; h2 si el h1 ya está en la cabecera. */
  readonly nivel?: 'h1' | 'h2';
  readonly texto?: Hijo;
  readonly hijos?: readonly Hijo[];
}

/** Pantalla de resultado centrada: confirmado, cancelado, link inválido, no toma turnos. */
export function resultado(opciones: OpcionesResultado): HTMLElement {
  const fondo = opciones.tono === 'exito' ? 'bg-confirmado-bg text-exito' : 'bg-superficie-tenue text-texto-tenue';
  return el(
    'section',
    { class: 'text-center', 'data-resultado': true },
    el(
      'div',
      { 'aria-hidden': 'true', class: `grid place-items-center w-[calc(var(--space-7)+var(--space-4))] h-[calc(var(--space-7)+var(--space-4))] rounded-full mx-auto mt-2 mb-4 ${fondo}` },
      icono(opciones.icono, 'w-6 h-6 flex-none'),
    ),
    el(opciones.nivel ?? 'h1', { tabindex: '-1', 'data-titulo': true, class: 'mt-0 mb-2 text-2xl font-bold text-texto focus:outline-none' }, opciones.titulo),
    opciones.texto === undefined ? null : el('p', { class: 'mx-auto mt-0 mb-4 max-w-lectura text-texto-tenue' }, opciones.texto),
    ...(opciones.hijos ?? []),
  );
}

/** Botón grande para llamar al consultorio. */
export function botonLlamar(telefono: string): HTMLAnchorElement {
  return el(
    'a',
    {
      href: hrefTelefono(telefono),
      class:
        'flex items-center justify-center gap-2 min-h-[calc(var(--touch-min)+var(--space-3))] rounded-md bg-primario text-sobre-primario text-lg font-bold no-underline tabular hover:bg-primario-hover',
    },
    icono('telefono', 'w-icono h-icono flex-none'),
    `Llamar al ${telefonoLegible(telefono)}`,
  );
}

export function tituloDePaso(textoTitulo: string, primero = false): HTMLHeadingElement {
  return el('h2', { class: `${primero ? 'mt-0' : 'mt-5'} mb-3 text-lg font-bold text-texto` }, textoTitulo);
}

/** "Horarios en hora de Buenos Aires, la del consultorio." (design D4). */
export function avisoDeZona(zona: string): HTMLElement {
  return el(
    'p',
    { class: 'flex items-center gap-2 mt-3 mb-0 text-sm text-texto-tenue' },
    icono('reloj', 'w-icono-sm h-icono-sm flex-none'),
    el('span', {}, 'Horarios en hora de ', el('b', {}, nombreDeZona(zona)), ', la del consultorio.'),
  );
}

export interface OpcionesDias {
  readonly dias: readonly DiaDeHuecos[];
  readonly elegido: string | null;
  readonly alElegir: (fecha: string) => void;
  readonly quedanMas: boolean;
  readonly cargando: boolean;
  readonly alVerMas: () => void;
}

function rotuloDeMeses(dias: readonly DiaDeHuecos[]): string {
  const primero = dias[0];
  const ultimo = dias.at(-1);
  if (primero === undefined || ultimo === undefined) return '';
  const desde = etiquetaDia(primero.fecha).mes;
  const hasta = etiquetaDia(ultimo.fecha).mes;
  if (desde === hasta) return desde;
  // "noviembre – diciembre 2026"; si cambia el año, los dos llevan el suyo.
  const mismoAnio = primero.fecha.slice(0, 4) === ultimo.fecha.slice(0, 4);
  return `${mismoAnio ? desde.replace(/ \d{4}$/, '') : desde} – ${hasta}`;
}

const CLASES_DIA =
  'group flex-none min-w-[calc(var(--space-7)+var(--space-4))] min-h-[calc(var(--touch-min)+var(--space-3))] p-2 rounded-md ' +
  'border-control border-borde-fuerte bg-superficie text-center font-semibold text-base text-texto tabular cursor-pointer ' +
  'aria-pressed:bg-primario aria-pressed:border-primario aria-pressed:text-sobre-primario ' +
  'disabled:border-dashed disabled:text-texto-sutil disabled:cursor-not-allowed';

/** Tira de días (design D3): los días sin huecos se ven deshabilitados, con borde punteado y "sin turnos". */
export function tiraDeDias(opciones: OpcionesDias): HTMLElement {
  const botonesDeDia = opciones.dias.map((dia) => {
    const etiqueta = etiquetaDia(dia.fecha);
    const elegido = opciones.elegido === dia.fecha;
    // Un día elegido que se quedó sin huecos (409) sigue marcado para mostrar el "próximo día con horarios".
    const habilitado = diaElegible(dia) || elegido;
    const b = el(
      'button',
      {
        type: 'button',
        class: CLASES_DIA,
        disabled: !habilitado,
        'aria-pressed': habilitado ? String(elegido) : null,
        'aria-label': habilitado ? null : `${etiqueta.largo}, sin turnos`,
        'data-fecha': dia.fecha,
      },
      el('small', { class: 'block text-xs uppercase font-medium text-texto-tenue group-aria-pressed:text-sobre-primario group-disabled:text-texto-sutil' }, etiqueta.semana),
      etiqueta.numero,
      habilitado ? null : el('span', { class: 'block text-xs font-medium' }, 'sin turnos'),
    );
    if (habilitado) b.addEventListener('click', () => opciones.alElegir(dia.fecha));
    return b;
  });
  const sinHuecos = opciones.dias.length > 0 && opciones.dias.every((d) => !diaElegible(d));
  const verMas = el(
    'button',
    {
      type: 'button',
      class:
        'inline-flex items-center justify-center gap-2 min-h-touch px-4 rounded-md border border-primario bg-superficie text-primario font-semibold hover:bg-primario-tinte disabled:cursor-not-allowed disabled:text-texto-sutil disabled:border-borde',
      disabled: opciones.cargando,
    },
    opciones.cargando ? 'Buscando más días…' : 'Ver más días',
  );
  verMas.addEventListener('click', () => opciones.alVerMas());
  return el(
    'div',
    {},
    el('p', { class: 'mt-0 mb-2 text-sm font-semibold text-texto-tenue first-letter:uppercase' }, rotuloDeMeses(opciones.dias)),
    el('div', { role: 'group', 'aria-label': 'Días', class: 'flex gap-2 overflow-x-auto px-1 pt-1 pb-2' }, ...botonesDeDia),
    sinHuecos ? el('p', { role: 'status', class: 'mt-2 mb-0 text-sm text-texto-tenue' }, 'No hay turnos en estos días.') : null,
    opciones.quedanMas ? el('div', { class: 'mt-2' }, verMas) : null,
  );
}

export interface OpcionesHorarios {
  readonly huecos: readonly string[];
  /** Horarios que se ocuparon mientras el paciente elegía (409): se muestran tachados y deshabilitados. */
  readonly ocupados: readonly string[];
  readonly zona: string;
  readonly elegido: string | null;
  readonly alElegir: (instante: string) => void;
}

const CLASES_HORA =
  'min-h-touch rounded-md border-control border-primario bg-superficie text-primario font-semibold text-base tabular cursor-pointer ' +
  'hover:bg-primario-tinte aria-pressed:bg-primario aria-pressed:text-sobre-primario ' +
  'disabled:border-borde disabled:bg-superficie-tenue disabled:text-texto-sutil disabled:line-through disabled:cursor-not-allowed';

function botonHora(horario: Horario, ocupado: boolean, opciones: OpcionesHorarios): HTMLButtonElement {
  const b = el(
    'button',
    {
      type: 'button',
      class: CLASES_HORA,
      disabled: ocupado,
      'aria-pressed': ocupado ? null : String(opciones.elegido === horario.instante),
      'aria-label': ocupado ? `${horario.hora}, ya no está disponible` : null,
    },
    horario.hora,
  );
  if (!ocupado) b.addEventListener('click', () => opciones.alElegir(horario.instante));
  return b;
}

/** Horarios agrupados en mañana y tarde, en la zona del consultorio (design D4). */
export function listaDeHorarios(opciones: OpcionesHorarios): HTMLElement {
  const ocupados = new Set(opciones.ocupados);
  const todos = [...new Set([...opciones.huecos, ...opciones.ocupados])].sort((a, b) => Date.parse(a) - Date.parse(b));
  const { manana, tarde } = agruparHorarios(todos, opciones.zona);
  const grupo = (titulo: string, horarios: readonly Horario[], primero: boolean) =>
    horarios.length === 0
      ? null
      : el(
          'div',
          {},
          el('p', { class: `${primero ? 'mt-0' : 'mt-3'} mb-2 text-sm font-semibold text-texto-tenue` }, titulo),
          el(
            'div',
            { role: 'group', 'aria-label': `Horarios de la ${titulo.toLowerCase()}`, class: 'grid grid-cols-[repeat(auto-fill,minmax(calc(var(--space-7)+var(--space-6)+var(--space-1)),1fr))] gap-2' },
            ...horarios.map((h) => botonHora(h, ocupados.has(h.instante), opciones)),
          ),
        );
  return el('div', {}, grupo('Mañana', manana, true), grupo('Tarde', tarde, manana.length === 0));
}

/** Barra de la acción principal: fija abajo en mobile, en el flujo desde 768 px (styles.css). */
export function barraDeAccion(...hijos: Hijo[]): HTMLElement {
  return el('div', { class: 'barra-accion' }, ...hijos);
}

export function botonPrincipal(textoBoton: string, opciones: { readonly deshabilitado?: boolean; readonly alHacerClic?: () => void; readonly tipo?: 'button' | 'submit' } = {}): HTMLButtonElement {
  const b = el(
    'button',
    {
      type: opciones.tipo ?? 'button',
      disabled: opciones.deshabilitado === true,
      class:
        'inline-flex items-center justify-center gap-2 w-full min-h-touch px-4 rounded-md border border-transparent bg-primario text-sobre-primario font-semibold text-base cursor-pointer ' +
        'hover:bg-primario-hover active:scale-presion transition-[background-color,transform] duration-rapida ease-salida ' +
        'disabled:bg-superficie-tenue disabled:text-texto-sutil disabled:border-borde disabled:cursor-not-allowed disabled:active:scale-100',
    },
    textoBoton,
  );
  if (opciones.alHacerClic !== undefined) {
    const accion = opciones.alHacerClic;
    b.addEventListener('click', () => accion());
  }
  return b;
}

/** Resumen del turno: cuándo, qué y dónde. */
export function resumenDelTurno(cuando: string, detalle: string, extra: readonly Hijo[] = [], opciones: { readonly viejo?: boolean } = {}): HTMLElement {
  return el(
    'div',
    { class: `grid gap-2 p-4 rounded-lg border border-borde text-left ${opciones.viejo === true ? 'bg-superficie-tenue' : 'bg-superficie'}` },
    el(
      'div',
      { class: 'flex gap-3 items-start' },
      icono('calendario', 'w-icono h-icono flex-none mt-1 text-primario'),
      el(
        'div',
        {},
        el('div', { class: `text-lg font-bold ${opciones.viejo === true ? 'line-through text-texto-tenue' : 'text-texto'}`, 'data-cuando': true }, cuando),
        detalle === '' ? null : el('div', { class: 'text-sm text-texto-tenue' }, detalle),
      ),
    ),
    ...extra,
  );
}
