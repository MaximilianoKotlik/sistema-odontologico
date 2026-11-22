import type { ContextoPagina } from '../../pages/pagina';
import { el } from '../../shared/dom';
import { formatearDia, hoyEn } from '../../shared/fechas';
import { ApiError } from '../../shared/http';
import { boton, clasesBoton, enlaceBoton } from '../../shared/ui/boton';
import { errorRecuperable } from '../../shared/ui/error-recuperable';
import { esqueleto } from '../../shared/ui/esqueleto';
import { tarjetaEstado } from '../../shared/ui/estado-vacio';
import { icono } from '../../shared/ui/iconos';
import { obtenerAgendaDeSemana, obtenerFranjas, type Agenda } from './api';
import { lineaDeTiempo } from './linea-de-tiempo';
import {
  avisoDeNuevos,
  conteoPorDia,
  franjasDelDia,
  lineaDelDia,
  nuevosVisibles,
  resumenDelDia,
  turnosDelDia,
  vistaDeLaUrl,
  type FranjaDisponible,
  type Vista,
} from './modelo';
import { puntosDeEntrada } from './puntos-de-entrada';
import { grillaSemanal, listaSemanal, rutaDelDia } from './semana';
import { chipBloqueo, chipEstadoTurno, chipSobreturno } from './tarjeta';
import { diaDeSemana, instanteLocal, lunesDe, sumarDias } from './tiempo';

/**
 * Pantalla de la agenda (C-13, spec vista-agenda; mockup aprobado en #27): barra con navegación, interruptor de
 * cancelados y Día/Semana; aviso de cambios de los pacientes; y la vista diaria o semanal con sus estados.
 */

const CLAVE_CANCELADOS = 'agenda.mostrarCancelados';
const ANCHO_SEMANA = '(min-width: 768px)';
const DIAS_EN_PLURAL = ['lunes', 'martes', 'miércoles', 'jueves', 'viernes', 'sábados', 'domingos'];
const LETRAS_TIRA = ['LUN', 'MAR', 'MIÉ', 'JUE', 'VIE', 'SÁB', 'DOM'];

/** Preferencia del dispositivo, no un dato (D6): si el almacenamiento falla, vale encendido. */
function leerMostrarCancelados(): boolean {
  try {
    return localStorage.getItem(CLAVE_CANCELADOS) !== 'false';
  } catch {
    return true;
  }
}

function guardarMostrarCancelados(valor: boolean): void {
  try {
    localStorage.setItem(CLAVE_CANCELADOS, String(valor));
  } catch {
    // Sin almacenamiento, la preferencia dura lo que la pantalla.
  }
}

const esAncho = () => (typeof window.matchMedia === 'function' ? window.matchMedia(ANCHO_SEMANA).matches : false);
const rutaDe = (vista: Vista, fecha: string) => `/app/agenda?vista=${vista}&fecha=${fecha}`;
const mayuscula = (texto: string) => texto.charAt(0).toUpperCase() + texto.slice(1);
const plural = (n: number, singular: string, varios: string) => `${n} ${n === 1 ? singular : varios}`;

/** Al cambiar de día o de semana desde la agenda, el foco va al título nuevo (D9). */
let enfocarTituloAlMontar = false;

function tituloDeSemana(lunes: string, zona: string): string {
  const domingo = sumarDias(lunes, 6);
  const [, numeroLunes, , mesLunes] = formatearDia(lunes, zona).split(' ');
  const [, numeroDomingo, , mesDomingo] = formatearDia(domingo, zona).split(' ');
  return mesLunes === mesDomingo ? `${numeroLunes ?? ''} al ${numeroDomingo ?? ''} de ${mesDomingo ?? ''}` : `${numeroLunes ?? ''} de ${mesLunes ?? ''} al ${numeroDomingo ?? ''} de ${mesDomingo ?? ''}`;
}

function subtituloDelDia(fecha: string, hoy: string): string | null {
  if (fecha === hoy) return 'Hoy';
  if (fecha === sumarDias(hoy, 1)) return 'Mañana';
  if (fecha === sumarDias(hoy, -1)) return 'Ayer';
  return null;
}

function interruptor(encendido: boolean, alCambiar: (valor: boolean) => void): HTMLElement {
  const control = el(
    'button',
    {
      type: 'button',
      role: 'switch',
      'aria-checked': String(encendido),
      class: 'group inline-flex items-center gap-2 min-h-touch bg-transparent border-0 p-0 cursor-pointer font-sans text-sm font-medium text-texto',
    },
    'Mostrar cancelados',
    el(
      'span',
      {
        'aria-hidden': 'true',
        class:
          'relative inline-block w-7 h-5 rounded-full border-control border-borde-fuerte bg-superficie-tenue transition-colors duration-rapida ' +
          'group-aria-checked:bg-exito group-aria-checked:border-exito',
      },
      el('span', {
        class:
          'absolute top-[calc(var(--space-1)/2)] left-[calc(var(--space-1)/2)] w-4 h-4 rounded-full bg-superficie shadow-sm transition-transform duration-rapida ' +
          'group-aria-checked:translate-x-5',
      }),
    ),
  );
  control.addEventListener('click', () => alCambiar(control.getAttribute('aria-checked') !== 'true'));
  return control;
}

function selectorDeVista(vista: Vista, fecha: string): HTMLElement {
  const opcion = (destino: Vista, texto: string) =>
    el(
      'a',
      {
        href: rutaDe(destino, destino === 'semana' ? lunesDe(fecha) : fecha),
        'data-link': true,
        'aria-current': vista === destino ? 'page' : null,
        class:
          'inline-flex items-center min-h-touch px-3 text-texto font-medium no-underline ' +
          'aria-[current=page]:bg-primario aria-[current=page]:text-sobre-primario aria-[current=page]:font-semibold',
      },
      texto,
    );
  return el(
    'nav',
    { 'aria-label': 'Vista de la agenda', class: 'inline-flex border-control border-borde-fuerte rounded-md overflow-hidden bg-superficie divide-x divide-borde-fuerte' },
    opcion('dia', 'Día'),
    opcion('semana', 'Semana'),
  );
}

function avisoNuevos(agenda: Agenda, mostrarCancelados: boolean): HTMLElement | null {
  const resumen = avisoDeNuevos(nuevosVisibles(agenda.turnos, mostrarCancelados));
  if (resumen === null) return null;
  return el(
    'div',
    { 'data-aviso-nuevos': true, role: 'status', class: 'flex gap-3 items-start mb-3 px-4 py-3 rounded-md border border-reservado-bd bg-reservado-bg text-texto' },
    icono('info', 'w-icono h-icono flex-none text-primario'),
    el(
      'div',
      {},
      el('strong', { class: 'block text-primario' }, resumen.titulo),
      el('p', { class: 'm-0 text-sm' }, `${resumen.detalle.replace(/\.$/, '')}, por el link. Se marcan como vistos al abrir cada turno.`),
    ),
  );
}

function tiraDeDias(fecha: string, hoy: string, agenda: Agenda, franjas: readonly FranjaDisponible[] | null, zona: string, mostrarCancelados: boolean): HTMLElement {
  const conteos = conteoPorDia(lunesDe(fecha), zona, agenda.turnos, mostrarCancelados);
  return el(
    'ol',
    { 'data-tira-dias': true, 'aria-label': 'Días de la semana', class: 'list-none m-0 mb-3 p-0 grid grid-cols-7 gap-1 md:max-w-3xl' },
    ...conteos.map(({ dia, cantidad }, i) => {
      const atiende = franjas === null || franjasDelDia(dia, franjas).length > 0;
      const detalle = cantidad > 0 ? String(cantidad) : atiende ? 'libre' : 'cerrado';
      const elegido = dia === fecha;
      return el(
        'li',
        {},
        el(
          'a',
          {
            href: rutaDelDia(dia),
            'data-link': true,
            'aria-current': elegido ? 'date' : null,
            'aria-label': `${formatearDia(dia, zona)}${dia === hoy ? ' (hoy)' : ''}, ${cantidad > 0 ? plural(cantidad, 'turno', 'turnos') : atiende ? 'sin turnos' : 'no atendés'}`,
            class:
              'flex flex-col items-center justify-center gap-0 min-h-7 rounded-md border-control border-borde bg-superficie text-texto no-underline tabular ' +
              'aria-[current=date]:bg-primario aria-[current=date]:border-primario aria-[current=date]:text-sobre-primario',
          },
          el('span', { class: `text-xs font-semibold ${elegido ? '' : 'text-texto-tenue'}` }, LETRAS_TIRA[i]),
          el('span', { class: `text-base font-bold ${dia === hoy ? 'underline underline-offset-4' : ''} ${dia === hoy && !elegido ? 'text-primario' : ''}` }, String(Number(dia.slice(8)))),
          el('span', { class: `text-xs ${elegido ? '' : 'text-texto-tenue'}` }, detalle),
        ),
      );
    }),
  );
}

function textoDelResumen(turnos: Parameters<typeof resumenDelDia>[0], mostrarCancelados: boolean): HTMLElement {
  const r = resumenDelDia(turnos, mostrarCancelados);
  return el(
    'p',
    { 'data-resumen': true, class: 'm-0 mb-3 text-sm text-texto-tenue' },
    el('b', { class: 'text-texto' }, plural(r.turnos, 'turno', 'turnos')),
    r.sobreturnos > 0 ? ` · ${plural(r.sobreturnos, 'sobreturno', 'sobreturnos')}` : '',
    r.cancelados > 0 ? ` · ${plural(r.cancelados, 'cancelado', 'cancelados')}` : '',
  );
}

function leyendaSemanal(): HTMLElement {
  return el(
    'div',
    { 'aria-hidden': 'true', class: 'hidden md:flex flex-wrap items-center gap-x-3 gap-y-2 mt-3 text-xs text-texto-tenue' },
    chipEstadoTurno('RESERVADO'),
    chipEstadoTurno('CONFIRMADO'),
    chipEstadoTurno('AUSENTE'),
    chipEstadoTurno('CANCELADO'),
    chipSobreturno(),
    chipBloqueo(),
    el('span', { class: 'inline-flex items-center gap-1' }, el('span', { class: 'inline-block w-2 h-2 rounded-full bg-primario' }), 'Cambio de un paciente sin ver'),
  );
}

export function montarAgenda(contexto: ContextoPagina): void {
  const { contenedor, http, zonaHoraria: zona } = contexto;
  const hoy = hoyEn(zona);
  const { vista, fecha } = vistaDeLaUrl(new URLSearchParams(location.search), esAncho(), hoy);
  const lunes = lunesDe(fecha);
  let mostrarCancelados = leerMostrarCancelados();
  let datos: { readonly agenda: Agenda; readonly franjas: FranjaDisponible[] | null } | null = null;

  const ir = (ruta: string) => {
    enfocarTituloAlMontar = true;
    void contexto.navegacion.navegar(ruta);
  };

  contexto.acciones.replaceChildren(
    boton({ texto: 'Nuevo turno', icono: 'mas', requiereConexion: true, alHacerClic: () => puntosDeEntrada().nuevoTurno(null) }),
  );

  // Barra: navegación y título, interruptor y Día/Semana.
  const paso = vista === 'dia' ? 1 : 7;
  const anterior = vista === 'dia' ? 'Día anterior' : 'Semana anterior';
  const siguiente = vista === 'dia' ? 'Día siguiente' : 'Semana siguiente';
  const flecha = (etiqueta: string, nombre: 'izquierda' | 'derecha', delta: number) => {
    const b = el('button', { type: 'button', 'aria-label': etiqueta, class: `${clasesBoton('fantasma')} w-touch px-0` }, icono(nombre));
    b.addEventListener('click', () => ir(rutaDe(vista, sumarDias(fecha, delta))));
    return b;
  };
  const incluyeHoy = vista === 'dia' ? fecha === hoy : lunes === lunesDe(hoy);
  const subtitulo = vista === 'dia' ? subtituloDelDia(fecha, hoy) : incluyeHoy ? 'Esta semana' : null;
  const titulo = el(
    'h2',
    { tabindex: '-1', class: 'm-0 mx-1 text-lg font-bold focus:outline-none' },
    vista === 'dia' ? mayuscula(formatearDia(fecha, zona)) : tituloDeSemana(lunes, zona),
    subtitulo === null ? null : ' ',
    subtitulo === null ? null : el('small', { class: 'block text-sm font-normal text-texto-tenue' }, subtitulo),
  );
  const botonHoy = incluyeHoy ? null : boton({ texto: 'Hoy', variante: 'secundario', alHacerClic: () => ir(rutaDe(vista, vista === 'dia' ? hoy : lunesDe(hoy))) });
  const zonaInterruptor = el('span', {});
  const barra = el(
    'div',
    { class: 'flex flex-wrap items-center gap-x-3 gap-y-2 mb-3' },
    el('div', { class: 'flex items-center gap-1' }, flecha(anterior, 'izquierda', -paso), titulo, flecha(siguiente, 'derecha', paso), botonHoy),
    el('div', { class: 'flex flex-wrap items-center justify-between gap-x-3 gap-y-2 max-md:w-full md:ml-auto' }, zonaInterruptor, selectorDeVista(vista, fecha)),
  );
  const cuerpo = el('div', {});
  contenedor.replaceChildren(el('div', { 'data-agenda': true, class: 'pb-6' }, barra, cuerpo));

  function pintarInterruptor(): void {
    zonaInterruptor.replaceChildren(
      interruptor(mostrarCancelados, (valor) => {
        mostrarCancelados = valor;
        guardarMostrarCancelados(valor);
        pintarInterruptor();
        pintar();
      }),
    );
  }

  function vistaDiaria(agenda: Agenda, franjas: FranjaDisponible[] | null): HTMLElement {
    const delDia = turnosDelDia(fecha, zona, agenda.turnos);
    const visibles = delDia.filter((t) => mostrarCancelados || t.estado !== 'CANCELADO');
    const franjasHoy = franjas === null ? [] : franjasDelDia(fecha, franjas);
    const filas = lineaDelDia({ dia: fecha, zona, turnos: agenda.turnos, bloqueos: agenda.bloqueos, franjas: franjas ?? [], ahora: new Date(), mostrarCancelados });
    const hayAlgo = filas.some((f) => f.tipo === 'turno' || f.tipo === 'bloqueo');
    const alElegirLibre = (desde: string) => puntosDeEntrada().nuevoTurno(instanteLocal(fecha, desde, zona).toISOString());

    let contenido: HTMLElement;
    if (!hayAlgo && franjas !== null && franjasHoy.length === 0) {
      contenido = tarjetaEstado({
        icono: 'luna',
        titulo: `No atendés los ${DIAS_EN_PLURAL[diaDeSemana(fecha) - 1] ?? ''}`,
        texto: 'Si querés dar turnos este día, sumá una franja en tus horarios de atención.',
        acciones: [enlaceBoton({ texto: 'Cambiar mis horarios', href: '/app/configuracion/horarios', variante: 'secundario' })],
      });
    } else if (!hayAlgo && visibles.length === 0) {
      contenido = el(
        'div',
        { class: 'grid gap-3' },
        el('p', { 'data-sin-turnos': true, class: 'm-0 font-semibold' }, 'No tenés turnos', el('span', { class: 'font-normal text-texto-tenue' }, franjasHoy.length > 0 ? '. Tus horarios libres de este día:' : ' este día.')),
        lineaDeTiempo(filas, { alElegirLibre }),
      );
    } else {
      contenido = lineaDeTiempo(filas, { alElegirLibre });
    }

    return el(
      'section',
      { 'aria-label': 'Agenda del día', class: 'max-w-3xl' },
      tiraDeDias(fecha, hoy, agenda, franjas, zona, mostrarCancelados),
      textoDelResumen(delDia, mostrarCancelados),
      contenido,
    );
  }

  function vistaSemanal(agenda: Agenda, franjas: FranjaDisponible[] | null): HTMLElement {
    const entrada = { lunes, zona, turnos: agenda.turnos, bloqueos: agenda.bloqueos, franjas, ahora: new Date(), mostrarCancelados };
    return el(
      'section',
      { 'aria-label': 'Agenda de la semana' },
      el('div', { class: 'hidden md:block' }, grillaSemanal(entrada)),
      leyendaSemanal(),
      el('div', { class: 'md:hidden' }, listaSemanal(entrada)),
    );
  }

  function pintar(): void {
    if (datos === null) return;
    const { agenda, franjas } = datos;
    cuerpo.replaceChildren(...[avisoNuevos(agenda, mostrarCancelados), vista === 'dia' ? vistaDiaria(agenda, franjas) : vistaSemanal(agenda, franjas)].filter((n): n is HTMLElement => n !== null));
  }

  async function cargar(): Promise<void> {
    datos = null;
    cuerpo.replaceChildren(esqueleto({ etiqueta: 'Cargando tu agenda…', filas: 5 }));
    try {
      const [agenda, franjas] = await Promise.all([obtenerAgendaDeSemana(http, lunes, zona), obtenerFranjas(http)]);
      datos = { agenda, franjas };
      pintar();
    } catch (error) {
      if (error instanceof ApiError && error.codigo === 'SESION_VENCIDA') return;
      const reintentar = () => void cargar();
      cuerpo.replaceChildren(
        error instanceof ApiError && error.codigo === 'SIN_CONEXION'
          ? tarjetaEstado({
              icono: 'sin-conexion',
              titulo: 'No tenés guardada la agenda de esta fecha',
              texto: 'Sin conexión solo podés ver las fechas que abriste antes. Volvé a intentar cuando tengas conexión.',
              acciones: [boton({ texto: 'Reintentar', icono: 'reintentar', alHacerClic: reintentar })],
            })
          : errorRecuperable({ titulo: 'No pudimos cargar tu agenda', alReintentar: reintentar }),
      );
    }
  }

  pintarInterruptor();
  if (enfocarTituloAlMontar) {
    enfocarTituloAlMontar = false;
    titulo.focus({ preventScroll: true });
  }
  void cargar();

  // La marca de ahora avanza sola mientras se ve hoy; se detiene al salir de la pantalla.
  if (incluyeHoy) {
    const reloj = window.setInterval(() => {
      if (!cuerpo.isConnected) {
        window.clearInterval(reloj);
        return;
      }
      pintar();
    }, 60_000);
  }
}
