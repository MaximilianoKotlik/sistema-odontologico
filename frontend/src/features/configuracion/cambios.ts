import type { Router } from '../../shared/router';
import { boton } from '../../shared/ui/boton';
import { dialogo } from '../../shared/ui/dialogo';

/**
 * Cambios sin guardar (C-09, design D9; mockups, decisión 7): mientras hay cambios, salir de la pantalla pide
 * confirmación con "Seguir editando" como opción por defecto, y cerrar o recargar la pestaña también avisa.
 */

export type Navegacion = Pick<Router, 'navegar' | 'bloquearSalida'>;

export interface VigiaDeCambios {
  marcar(conCambios: boolean): void;
  detener(): void;
}

export function confirmarDescarte(): Promise<boolean> {
  return new Promise((resolver) => {
    let respuesta = false;
    const seguir = boton({ texto: 'Seguir editando', variante: 'primario', alHacerClic: () => ventana.cerrar() });
    const descartar = boton({
      texto: 'Descartar',
      variante: 'secundario',
      alHacerClic: () => {
        respuesta = true;
        ventana.cerrar();
      },
    });
    const ventana = dialogo({
      titulo: '¿Descartar los cambios?',
      descripcion: 'Tenés cambios sin guardar. Si salís ahora, se pierden.',
      acciones: [seguir, descartar],
      alCerrar: () => resolver(respuesta),
    });
    ventana.abrir();
  });
}

export function vigilarCambios(navegacion: Navegacion, ventana: Window): VigiaDeCambios {
  let desbloquear: (() => void) | null = null;

  const alSalirDeLaPagina = (evento: BeforeUnloadEvent) => {
    evento.preventDefault();
  };

  function soltar(): void {
    desbloquear?.();
    desbloquear = null;
    ventana.removeEventListener('beforeunload', alSalirDeLaPagina);
  }

  return {
    marcar(conCambios) {
      if (!conCambios) return soltar();
      if (desbloquear !== null) return;
      desbloquear = navegacion.bloquearSalida(async () => {
        const descartar = await confirmarDescarte();
        if (descartar) soltar();
        return descartar;
      });
      ventana.addEventListener('beforeunload', alSalirDeLaPagina);
    },
    detener: soltar,
  };
}
