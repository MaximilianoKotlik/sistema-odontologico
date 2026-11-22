import { el } from '../../shared/dom';
import { icono, type NombreIcono } from '../../shared/ui/iconos';
import { nombreCompleto, type CambioPaciente, type EstadoTurno, type Fila, type TurnoAgenda } from './modelo';

/**
 * Tarjetas de la agenda (C-13, design D5 y D9; mockup aprobado en #27). Cada estado se distingue con color, ícono y
 * texto; el sobreturno con borde punteado; el bloqueo rayado; y los cancelados atenuados sin opacidad.
 */

interface Apariencia {
  readonly texto: string;
  readonly icono: NombreIcono;
  /** Chip de estado (components.html). */
  readonly chip: string;
  /** Borde y fondo de la tarjeta de la línea de tiempo. */
  readonly tarjeta: string;
  /** Borde y fondo compactos (grilla semanal y lista de la semana). */
  readonly compacta: string;
}

export const APARIENCIA: Record<EstadoTurno, Apariencia> = {
  RESERVADO: {
    texto: 'Reservado',
    icono: 'reloj',
    chip: 'text-reservado-fg bg-reservado-bg border-reservado-bd',
    tarjeta: 'border-reservado-bd tinte-reservado',
    compacta: 'border-reservado-bd bg-reservado-bg text-reservado-fg',
  },
  CONFIRMADO: {
    texto: 'Confirmado',
    icono: 'check',
    chip: 'text-confirmado-fg bg-confirmado-bg border-confirmado-bd',
    tarjeta: 'border-confirmado-bd tinte-confirmado',
    compacta: 'border-confirmado-bd bg-confirmado-bg text-confirmado-fg',
  },
  CANCELADO: {
    texto: 'Cancelado',
    icono: 'cruz',
    chip: 'text-cancelado-fg bg-cancelado-bg border-cancelado-bd',
    tarjeta: 'border-cancelado-bd bg-superficie-tenue',
    compacta: 'border-cancelado-bd bg-superficie-tenue text-texto-tenue',
  },
  // Sin token propio de Ausente: neutros del sistema (aprobado por Maxi en #27).
  AUSENTE: {
    texto: 'Ausente',
    icono: 'ausente',
    chip: 'text-texto-tenue bg-superficie border-borde-fuerte',
    tarjeta: 'border-borde-fuerte bg-superficie-tenue',
    compacta: 'border-borde-fuerte bg-superficie-tenue text-texto-tenue',
  },
};

const CLASE_CHIP = 'inline-flex items-center gap-1 min-h-5 px-2 rounded-full border text-xs font-semibold';
const CLASE_ICONO_CHIP = 'w-3 h-3 flex-none';

export function chipEstadoTurno(estado: EstadoTurno): HTMLSpanElement {
  const a = APARIENCIA[estado];
  return el('span', { class: `${CLASE_CHIP} ${a.chip}`, 'data-chip': estado }, icono(a.icono, CLASE_ICONO_CHIP), a.texto);
}

export function chipSobreturno(): HTMLSpanElement {
  return el('span', { class: `${CLASE_CHIP} border-dashed text-sobreturno-fg bg-sobreturno-bg border-sobreturno-bd`, 'data-chip': 'SOBRETURNO' }, icono('mas', CLASE_ICONO_CHIP), 'Sobreturno');
}

export function chipBloqueo(): HTMLSpanElement {
  return el('span', { class: `${CLASE_CHIP} text-bloqueo-fg bg-bloqueo-bg border-bloqueo-bd`, 'data-chip': 'BLOQUEO' }, icono('candado', CLASE_ICONO_CHIP), 'Bloqueo');
}

const VERBO: Record<CambioPaciente, string> = { RESERVO: 'reservó', REPROGRAMO: 'reprogramó', CANCELO: 'canceló' };
export const textoDelCambio = (cambio: CambioPaciente) => `${VERBO[cambio]} por el link`;

/** Etiqueta sólida "Nuevo · reservó por el link" (DD-30). El punto acompaña al texto, no lo reemplaza. */
export function chipNuevo(cambio: CambioPaciente): HTMLSpanElement {
  return el(
    'span',
    { class: `${CLASE_CHIP} text-sobre-primario bg-primario border-primario`, 'data-chip': 'NUEVO' },
    el('span', { 'aria-hidden': 'true', class: 'w-1 h-1 rounded-full bg-current' }),
    `Nuevo · ${textoDelCambio(cambio)}`,
  );
}

/** "10:30 a 11:30, Carla Ficticia, Extracción, Confirmado, con nota" (D9). */
export function nombreAccesibleTurno(turno: TurnoAgenda, desde: string, hasta: string, superponeCon?: { readonly hora: string; readonly paciente: string }): string {
  return [
    `${desde} a ${hasta}`,
    nombreCompleto(turno.paciente),
    turno.prestacion,
    APARIENCIA[turno.estado].texto,
    turno.esSobreturno ? 'sobreturno' : null,
    superponeCon === undefined ? null : `se superpone con ${superponeCon.hora} · ${superponeCon.paciente}`,
    turno.tieneNota ? 'con nota' : null,
    turno.cambioPaciente === null ? null : `nuevo: ${textoDelCambio(turno.cambioPaciente)}`,
  ]
    .filter((parte) => parte !== null && parte !== '')
    .join(', ');
}

export const rutaDelTurno = (id: string) => `/app/agenda/turnos/${encodeURIComponent(id)}`;

const BORDE_SOBRETURNO = 'border-2 border-dashed border-l-4';

/** Tarjeta de la línea de tiempo del día: un link al detalle, tocable y legible a 360 px. */
export function tarjetaTurno(fila: Extract<Fila, { tipo: 'turno' }>): HTMLAnchorElement {
  const { turno } = fila;
  const a = APARIENCIA[turno.estado];
  const cancelado = turno.estado === 'CANCELADO';
  return el(
    'a',
    {
      href: rutaDelTurno(turno.id),
      'data-link': true,
      'data-turno': turno.id,
      'data-estado': turno.estado,
      'data-sobreturno': turno.esSobreturno,
      'aria-label': nombreAccesibleTurno(turno, fila.desde, fila.hasta, fila.superponeCon),
      class: `block min-h-touch px-3 py-2 rounded-md text-texto no-underline hover:shadow-sm ${turno.esSobreturno ? BORDE_SOBRETURNO : 'border border-l-4'} ${a.tarjeta}`,
    },
    el(
      'span',
      { class: 'flex flex-wrap items-baseline gap-x-2' },
      el('span', { 'data-nombre': true, class: `font-semibold ${cancelado ? 'text-texto-tenue line-through decoration-1' : 'text-texto'}` }, nombreCompleto(turno.paciente)),
      el('span', { class: 'text-sm text-texto-tenue' }, `${turno.prestacion} · ${turno.duracionMin} min`),
    ),
    el(
      'span',
      { class: 'flex flex-wrap gap-1 mt-1' },
      chipEstadoTurno(turno.estado),
      turno.esSobreturno ? chipSobreturno() : null,
      turno.cambioPaciente === null ? null : chipNuevo(turno.cambioPaciente),
    ),
    fila.superponeCon === undefined
      ? null
      : el('span', { class: 'flex items-center gap-1 mt-1 text-xs text-texto-tenue' }, icono('info', CLASE_ICONO_CHIP), `Se superpone con ${fila.superponeCon.hora} · ${fila.superponeCon.paciente}`),
    turno.tieneNota ? el('span', { class: 'flex items-center gap-1 mt-1 text-xs text-texto-tenue' }, icono('nota', CLASE_ICONO_CHIP), 'Con nota') : null,
  );
}

/** Bloqueo de la línea de tiempo: rayado, con su motivo. No es un link: se gestiona en Bloqueos (C-14). */
export function tarjetaBloqueo(fila: Extract<Fila, { tipo: 'bloqueo' }>): HTMLElement {
  return el(
    'div',
    { 'data-bloqueo': fila.bloqueo.id, class: 'min-h-touch px-3 py-2 rounded-md border border-l-4 border-bloqueo-bd fondo-rayado text-bloqueo-fg' },
    el('span', { class: 'block font-semibold' }, fila.bloqueo.motivo ?? 'Bloqueado'),
    el('span', { class: 'flex flex-wrap gap-1 mt-1' }, chipBloqueo()),
  );
}
