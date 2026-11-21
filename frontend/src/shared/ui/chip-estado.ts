import { el } from '../dom';
import { icono, type NombreIcono } from './iconos';

export type EstadoChip = 'reservado' | 'confirmado' | 'cancelado' | 'sobreturno' | 'bloqueo';

/** Texto + ícono + color: el estado nunca se comunica solo por color (regla dura 12). */
const ESTADOS: Record<EstadoChip, { texto: string; icono: NombreIcono; clases: string }> = {
  reservado: { texto: 'Reservado', icono: 'reloj', clases: 'text-reservado-fg bg-reservado-bg border-reservado-bd' },
  confirmado: { texto: 'Confirmado', icono: 'check', clases: 'text-confirmado-fg bg-confirmado-bg border-confirmado-bd' },
  cancelado: { texto: 'Cancelado', icono: 'cruz', clases: 'text-cancelado-fg bg-cancelado-bg border-cancelado-bd' },
  sobreturno: { texto: 'Sobreturno', icono: 'mas', clases: 'text-sobreturno-fg bg-sobreturno-bg border-sobreturno-bd border-dashed' },
  bloqueo: { texto: 'Bloqueo', icono: 'candado', clases: 'text-bloqueo-fg bg-bloqueo-bg border-bloqueo-bd' },
};

export function chipEstado(estado: EstadoChip): HTMLSpanElement {
  const { texto, icono: nombreIcono, clases } = ESTADOS[estado];
  return el(
    'span',
    { class: `inline-flex items-center gap-1 min-h-6 px-3 rounded-full border text-sm font-semibold ${clases}`, 'data-estado': estado },
    icono(nombreIcono, 'w-icono-sm h-icono-sm flex-none'),
    texto,
  );
}
