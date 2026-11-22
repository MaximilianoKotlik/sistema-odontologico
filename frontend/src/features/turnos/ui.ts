import { el, type Hijo } from '../../shared/dom';
import { formatearDia } from '../../shared/fechas';
import type { ClienteHttp } from '../../shared/http';
import { boton } from '../../shared/ui/boton';
import { icono } from '../../shared/ui/iconos';
import { nombreCompleto, type BloqueoAgenda, type FranjaDisponible, type TurnoAgenda } from '../agenda/modelo';
import { obtenerAgendaEntre, obtenerFranjas } from '../agenda/api';
import { diaLocal, horaLocal, instanteLocal, sumarDias } from '../agenda/tiempo';
import { avisoAuth, spinner } from '../auth/ui';
import { campoConfig, type CampoConfig } from '../configuracion/ui';
import type { ErrorDeTurno } from './errores';

/** Piezas compartidas de los diálogos de C-14, traducidas de `c14.css` del mockup aprobado (#31) a los tokens. */

const mayuscula = (texto: string) => texto.charAt(0).toUpperCase() + texto.slice(1);

/** "viernes 20 a las 16:00", para los avisos de confirmación. */
export function cuandoCorto(instante: string, zona: string): string {
  const [diaSemana = '', numero = ''] = formatearDia(instante, zona).split(' ');
  return `${diaSemana} ${numero} a las ${horaLocal(instante, zona)}`;
}

/** "Viernes 20 de noviembre · 16:00 a 16:30". */
export const rangoLargo = (inicio: string, fin: string, zona: string) => `${mayuscula(formatearDia(inicio, zona))} · ${horaLocal(inicio, zona)} a ${horaLocal(fin, zona)}`;

/** Turno de referencia arriba del diálogo (`.ref` del mockup). */
export function referenciaDelTurno(turno: TurnoAgenda, zona: string): HTMLElement {
  return el(
    'div',
    { 'data-referencia': true, class: 'flex items-center gap-3 p-3 mb-4 rounded-md bg-superficie-tenue text-sm' },
    icono('calendario', 'w-icono h-icono flex-none text-texto-tenue'),
    el('div', {}, el('b', { class: 'block text-base' }, `${nombreCompleto(turno.paciente)} · ${turno.prestacion}`), ' ', el('span', { class: 'tabular' }, rangoLargo(turno.inicio, turno.fin, zona))),
  );
}

/** "Termina 12:30" debajo de la hora (RN-AG-12): el fin se muestra, no se escribe. */
export function lineaDeFin(): { readonly elemento: HTMLElement; mostrar(contenido: string): void } {
  const valor = el('b', { class: 'text-texto' });
  const elemento = el('p', { 'data-fin': true, 'aria-live': 'polite', class: 'flex items-center gap-2 m-0 mt-1 text-sm text-texto-tenue tabular' }, icono('reloj', 'w-icono-sm h-icono-sm flex-none'), 'Termina ', valor);
  return {
    elemento,
    mostrar: (contenido) => {
      valor.textContent = contenido;
    },
  };
}

export interface SalidasDelError {
  readonly alSobreturno?: () => void;
  readonly alVerActualizado?: () => void;
  readonly alReintentar?: () => void;
}

/** Aviso de un rechazo con su salida (D5), `role="alert"`. */
export function avisoDeError(error: ErrorDeTurno, salidas: SalidasDelError = {}): HTMLElement {
  const acciones: HTMLElement[] = [];
  if (error.accion === 'SOBRETURNO' && salidas.alSobreturno !== undefined) acciones.push(boton({ texto: 'Crear como sobreturno', icono: 'mas', variante: 'secundario', alHacerClic: salidas.alSobreturno }));
  if (error.accion === 'VER_ACTUALIZADO' && salidas.alVerActualizado !== undefined) acciones.push(boton({ texto: 'Ver el turno actualizado', variante: 'secundario', alHacerClic: salidas.alVerActualizado }));
  if (error.accion === 'REINTENTAR' && salidas.alReintentar !== undefined) acciones.push(boton({ texto: 'Reintentar', icono: 'reintentar', variante: 'secundario', alHacerClic: salidas.alReintentar }));
  const cuerpo: Hijo[] = [error.texto, acciones.length === 0 ? null : el('span', { class: 'flex flex-wrap gap-2 mt-2' }, ...acciones)];
  const nodo = avisoAuth({ tono: error.tono, icono: 'alerta', titulo: error.titulo, cuerpo });
  nodo.classList.replace('mb-5', 'mb-4');
  nodo.dataset['errorTurno'] = '';
  return nodo;
}

/** Botón principal con estado "guardando" (`Creando…`, `Guardando…`). */
export function conCarga(b: HTMLButtonElement, textoCargando: string): (activo: boolean) => void {
  const original = [...b.childNodes];
  return (activo) => {
    b.disabled = activo;
    b.setAttribute('aria-busy', String(activo));
    b.replaceChildren(...(activo ? [spinner(), textoCargando] : original));
  };
}

/** Sección del formulario con su título (`fieldset.fs` del mockup). */
export function seccion(titulo: string, ...hijos: Hijo[]): HTMLFieldSetElement {
  return el('fieldset', { class: 'm-0 mb-5 p-0 border-0 min-w-0' }, el('legend', { class: 'p-0 mb-2 font-bold text-base' }, titulo), ...hijos);
}

export interface DatosDelDia {
  readonly turnos: TurnoAgenda[];
  readonly bloqueos: BloqueoAgenda[];
  readonly franjas: FranjaDisponible[] | null;
}

/**
 * Agenda y horario de un día para sugerir huecos y nombrar con qué choca un rechazo. Es apoyo: si no se puede pedir,
 * el formulario sigue igual y el backend decide.
 */
export async function datosDelDia(http: ClienteHttp, dia: string, zona: string): Promise<DatosDelDia> {
  const [agenda, franjas] = await Promise.all([
    obtenerAgendaEntre(http, instanteLocal(dia, '00:00', zona).toISOString(), instanteLocal(sumarDias(dia, 1), '00:00', zona).toISOString()).catch(() => ({ turnos: [], bloqueos: [] })),
    obtenerFranjas(http).catch(() => null),
  ]);
  return { turnos: agenda.turnos, bloqueos: agenda.bloqueos, franjas };
}

export const diaDelTurno = (turno: TurnoAgenda, zona: string) => diaLocal(turno.inicio, zona);

export interface CamposDiaYHora {
  readonly elemento: HTMLElement;
  readonly dia: CampoConfig<HTMLInputElement>;
  readonly hora: CampoConfig<HTMLInputElement>;
}

/** Día con `<input type="date">` y hora en pasos de 5 minutos (D3), lado a lado como en el mockup (`.row-dt`). */
export function camposDiaYHora(dia: string, hora: string): CamposDiaYHora {
  const campoDia = campoConfig({ etiqueta: 'Día', control: el('input', { name: 'dia', type: 'date', value: dia, required: true }) });
  const campoHora = campoConfig({ etiqueta: 'Empieza', control: el('input', { name: 'hora', type: 'time', step: 300, value: hora, required: true }) });
  return { elemento: el('div', { class: 'grid grid-cols-[minmax(0,1.3fr)_minmax(0,1fr)] gap-3 items-start' }, campoDia.contenedor, campoHora.contenedor), dia: campoDia, hora: campoHora };
}
