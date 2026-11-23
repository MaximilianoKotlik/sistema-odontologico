import type { ClienteHttp } from '../../shared/http';
import { aviso } from '../../shared/ui/aviso';
import type { DetalleTurno } from './api';
import type { AccionTurno } from './modelo';

/**
 * Puntos de entrada de las acciones sobre turnos (C-13, design Non-Goals). C-13 deja los botones y los huecos
 * tocables; los formularios y diálogos son de C-14, que registra acá sus manejadores al montar la agenda o el detalle
 * (así se cargan con esas páginas, que son diferidas). Sin registrar, se avisa.
 */

/** Lo que una acción necesita de la pantalla que la abre. */
export interface EntornoDeAccion {
  readonly http: ClienteHttp;
  /** Zona del consultorio (RN-TE-05). */
  readonly zona: string;
  /** Al terminar (o ante un turno que cambió): la pantalla vuelve a pedir sus datos. */
  readonly alTerminar: () => void;
}

export interface PuntosDeEntrada {
  /** "Nuevo turno" (inicio null) o un hueco libre (instante UTC del inicio). */
  nuevoTurno(inicio: string | null, entorno: EntornoDeAccion): void;
  accion(accion: AccionTurno, turno: DetalleTurno, entorno: EntornoDeAccion): void;
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
