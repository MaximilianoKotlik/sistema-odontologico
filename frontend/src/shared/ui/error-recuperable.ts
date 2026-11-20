import { el } from '../dom';
import { boton } from './boton';
import { tarjetaEstado } from './estado-vacio';

export interface OpcionesErrorRecuperable {
  readonly titulo: string;
  readonly texto?: string;
  readonly codigo?: string;
  readonly alReintentar: () => void;
}

/** Error de carga con "Reintentar" que no saca al usuario de la página (spec app-shell). */
export function errorRecuperable(opciones: OpcionesErrorRecuperable): HTMLElement {
  const tarjeta = tarjetaEstado(
    {
      icono: 'alerta',
      titulo: opciones.titulo,
      texto: opciones.texto ?? 'Puede ser un problema momentáneo del servidor. Probá de nuevo en unos segundos.',
      acciones: [boton({ texto: 'Reintentar', icono: 'reintentar', alHacerClic: () => opciones.alReintentar() })],
    },
    { clase: 'text-peligro', role: 'alert' },
  );
  if (opciones.codigo !== undefined) {
    tarjeta.append(el('p', { class: 'mt-4 mb-0 font-mono text-xs text-texto-sutil' }, `Código: ${opciones.codigo}`));
  }
  return tarjeta;
}
