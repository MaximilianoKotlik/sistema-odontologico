import type { Seccion } from '../app/shell';
import type { NombreIcono } from '../shared/ui/iconos';
import { estadoVacio } from '../shared/ui/estado-vacio';
import type { Pagina } from './pagina';

/**
 * Página provisoria de una sección cuyo contenido llega en otro change
 * (agenda C-13, bloqueos C-11/C-13, configuración C-09, registro de cambios C-19, exportar C-18).
 */
export function seccionPendiente(titulo: string, seccion: Seccion, nombreIcono: NombreIcono): Pagina {
  return {
    titulo,
    seccion,
    montar({ contenedor }) {
      contenedor.append(
        estadoVacio({
          icono: nombreIcono,
          titulo: 'Esta sección todavía no está disponible',
          texto: 'La estamos preparando. Mientras tanto, podés moverte por las otras secciones.',
        }),
      );
    },
  };
}
