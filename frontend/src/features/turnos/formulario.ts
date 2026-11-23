import { el } from '../../shared/dom';
import { ApiError } from '../../shared/http';
import { boton, type VarianteBoton } from '../../shared/ui/boton';
import { dialogo, type Dialogo } from '../../shared/ui/dialogo';
import type { NombreIcono } from '../../shared/ui/iconos';
import { confirmarDescarte } from '../configuracion/cambios';
import { conCarga } from './ui';

/**
 * Formulario en diálogo (C-14, design D2): hoja a pantalla completa en mobile y diálogo de hasta 560 px en desktop,
 * con el botón principal en el pie. Cerrar con cambios pide confirmación, como la guarda de C-09. Si el envío falla,
 * el diálogo queda abierto con lo cargado y el aviso arriba.
 */
export interface OpcionesFormulario {
  readonly titulo: string;
  readonly contenido: Node;
  readonly textoGuardar: string;
  readonly textoCargando: string;
  readonly varianteGuardar?: VarianteBoton;
  readonly iconoGuardar?: NombreIcono;
  readonly conCambios: () => boolean;
  /** false si hay errores de validación y no hay que enviar. */
  readonly validar?: () => boolean;
  readonly ejecutar: () => Promise<void>;
  readonly alFallar: (error: ApiError, contexto: { readonly zonaDeAviso: HTMLElement; readonly ventana: Dialogo; readonly reintentar: () => void }) => void | Promise<void>;
}

export interface FormularioAbierto {
  readonly ventana: Dialogo;
  readonly guardar: HTMLButtonElement;
  readonly zonaDeAviso: HTMLElement;
  enviar(): Promise<void>;
}

export function abrirFormulario(opciones: OpcionesFormulario): FormularioAbierto {
  const zonaDeAviso = el('div', { 'data-zona-aviso': true });
  const cancelar = boton({ texto: 'Cancelar', variante: 'fantasma', alHacerClic: () => void pedirCierre() });
  const guardar = boton({
    texto: opciones.textoGuardar,
    variante: opciones.varianteGuardar ?? 'primario',
    ...(opciones.iconoGuardar === undefined ? {} : { icono: opciones.iconoGuardar }),
    requiereConexion: true,
  });
  guardar.classList.add('max-md:flex-1');
  const cargando = conCarga(guardar, opciones.textoCargando);

  const antesDeCerrar = async () => !opciones.conCambios() || (await confirmarDescarte());
  async function pedirCierre(): Promise<void> {
    if (await antesDeCerrar()) ventana.cerrar();
  }

  async function enviar(): Promise<void> {
    if (guardar.disabled && guardar.getAttribute('aria-busy') === 'true') return;
    if (opciones.validar?.() === false) return;
    zonaDeAviso.replaceChildren();
    cargando(true);
    try {
      await opciones.ejecutar();
      ventana.cerrar();
    } catch (error) {
      cargando(false);
      if (!(error instanceof ApiError) || error.codigo === 'SESION_VENCIDA') return;
      await opciones.alFallar(error, { zonaDeAviso, ventana, reintentar: () => void enviar() });
      zonaDeAviso.scrollIntoView?.({ block: 'nearest' });
    }
  }

  guardar.addEventListener('click', () => void enviar());
  // Enter en un campo envía, igual que el botón del pie.
  const form = el('form', { novalidate: true }, zonaDeAviso, opciones.contenido);
  form.addEventListener('submit', (evento) => {
    evento.preventDefault();
    void enviar();
  });

  const ventana = dialogo({ titulo: opciones.titulo, forma: 'formulario', contenido: form, acciones: [cancelar, guardar], antesDeCerrar });
  ventana.abrir();
  return { ventana, guardar, zonaDeAviso, enviar };
}
