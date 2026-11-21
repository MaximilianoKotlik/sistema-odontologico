import { enlaceBoton } from '../shared/ui/boton';
import { tarjetaEstado } from '../shared/ui/estado-vacio';
import type { Pagina } from './pagina';

/** Ruta inexistente (spec app-shell: Ruta inexistente). */
export const pagina: Pagina = {
  titulo: 'Página no encontrada',
  seccion: null,
  montar({ contenedor }) {
    contenedor.append(
      tarjetaEstado({
        icono: 'buscar',
        titulo: 'No encontramos esta página',
        texto: 'Puede que el enlace esté mal escrito o que la sección ya no exista.',
        acciones: [enlaceBoton({ texto: 'Ir a mi agenda', href: '/app/agenda' })],
      }),
    );
  },
};
