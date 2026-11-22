import { aviso } from '../../shared/ui/aviso';
import type { DetalleTurno } from './api';
import type { AccionTurno } from './modelo';

/**
 * Puntos de entrada de las acciones sobre turnos (C-13, design Non-Goals). C-13 deja los botones y los huecos
 * tocables; los formularios y diálogos son de C-14, que registra acá sus manejadores. Mientras tanto, se avisa.
 */
export interface PuntosDeEntrada {
  /** "Nuevo turno" (inicio null) o un hueco libre (instante UTC del inicio). */
  nuevoTurno(inicio: string | null): void;
  accion(accion: AccionTurno, turno: DetalleTurno): void;
}

const MENSAJE_PENDIENTE = 'Esta acción todavía no está disponible. Muy pronto vas a poder hacerla desde acá.';

const PENDIENTES: PuntosDeEntrada = {
  nuevoTurno: () => void aviso(MENSAJE_PENDIENTE),
  accion: () => void aviso(MENSAJE_PENDIENTE),
};

let actuales: PuntosDeEntrada = PENDIENTES;

export const puntosDeEntrada = (): PuntosDeEntrada => actuales;

export function registrarPuntosDeEntrada(puntos: PuntosDeEntrada): void {
  actuales = puntos;
}

export function restablecerPuntosDeEntrada(): void {
  actuales = PENDIENTES;
}
