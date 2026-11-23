import { registrarPuntosDeEntrada, type PuntosDeEntrada } from '../agenda/puntos-de-entrada';
import { abrirAusente, abrirCancelar, confirmarTurno } from './cambiar-estado';
import { abrirDuracion } from './duracion';
import { abrirNota } from './nota';
import { abrirNuevoTurno } from './nuevo-turno';
import { abrirReprogramar } from './reprogramar';

/**
 * Manejadores de C-14 para los puntos de entrada de C-13 (design D1). Las páginas de agenda y detalle los registran al
 * montarse, así los formularios se cargan junto con esas páginas diferidas.
 */
export const accionesDeTurnos: PuntosDeEntrada = {
  nuevoTurno: (inicio, entorno) => abrirNuevoTurno(inicio, entorno),
  accion: (accion, turno, entorno) => {
    switch (accion) {
      case 'CONFIRMAR':
        return void confirmarTurno(turno, entorno);
      case 'CANCELAR':
        return abrirCancelar(turno, entorno);
      case 'MARCAR_AUSENTE':
        return abrirAusente(turno, entorno);
      case 'REPROGRAMAR':
        return abrirReprogramar(turno, entorno);
      case 'AJUSTAR_DURACION':
        return abrirDuracion(turno, entorno);
      case 'EDITAR_NOTA':
        return abrirNota(turno, entorno);
    }
  },
};

export function registrarAccionesDeTurnos(): void {
  registrarPuntosDeEntrada(accionesDeTurnos);
}
