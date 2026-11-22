import { el } from '../../shared/dom';
import { formatearDia } from '../../shared/fechas';
import { ApiError } from '../../shared/http';
import { aviso } from '../../shared/ui/aviso';
import { boton, type VarianteBoton } from '../../shared/ui/boton';
import { dialogo, type Dialogo } from '../../shared/ui/dialogo';
import type { NombreIcono } from '../../shared/ui/iconos';
import type { DetalleTurno } from '../agenda/api';
import { nombreCompleto } from '../agenda/modelo';
import type { EntornoDeAccion } from '../agenda/puntos-de-entrada';
import { horaLocal } from '../agenda/tiempo';
import { campoConfig } from '../configuracion/ui';
import { cambiarEstado, type AccionDeEstado } from './api';
import { traducirErrorDeTurno, type ErrorDeTurno } from './errores';
import { avisoDeError, conCarga, cuandoCorto, diaDelTurno } from './ui';
import { validarMotivoCancelacion } from './validaciones';

/**
 * Cambios de estado desde el detalle (C-14, design D2; spec "Cambios de estado válidos"). Confirmar es directo porque
 * se puede revertir cancelando; Cancelar y Marcar ausente piden confirmación porque son terminales (RN-AG-06).
 */

const mayuscula = (texto: string) => texto.charAt(0).toUpperCase() + texto.slice(1);

/** "Viernes 20 a las 16:00 · Consulta". */
function resumenDelTurno(turno: DetalleTurno, zona: string): string {
  const [diaSemana = '', numero = ''] = formatearDia(turno.inicio, zona).split(' ');
  return `${mayuscula(diaSemana)} ${numero} a las ${horaLocal(turno.inicio, zona)} · ${turno.prestacion}`;
}

export function errorDeEstado(error: ApiError, turno: DetalleTurno, entorno: EntornoDeAccion): ErrorDeTurno {
  return traducirErrorDeTurno(error, { operacion: 'estado', zona: entorno.zona, dia: diaDelTurno(turno, entorno.zona), turnos: [], bloqueos: [], franjas: null });
}

/** Turno que cambió de estado (o ya no existe): se avisa y se recarga sin abrir nada (spec "Transición rechazada"). */
function avisarYRecargar(error: ErrorDeTurno, entorno: EntornoDeAccion): void {
  aviso(`${error.titulo}. ${error.texto}`, { tono: 'error' });
  entorno.alTerminar();
}

export async function confirmarTurno(turno: DetalleTurno, entorno: EntornoDeAccion): Promise<void> {
  try {
    await cambiarEstado(entorno.http, turno.id, { accion: 'CONFIRMAR', version: turno.version ?? 0 });
    aviso(`Turno confirmado: ${nombreCompleto(turno.paciente)}, ${cuandoCorto(turno.inicio, entorno.zona)}`, { tono: 'exito' });
    entorno.alTerminar();
  } catch (error) {
    if (!(error instanceof ApiError) || error.codigo === 'SESION_VENCIDA') return;
    const traducido = errorDeEstado(error, turno, entorno);
    if (traducido.accion === 'REINTENTAR') aviso(`${traducido.titulo}. ${traducido.texto}`, { tono: 'error' });
    else avisarYRecargar(traducido, entorno);
  }
}

export interface OpcionesConfirmacion {
  readonly titulo: string;
  readonly descripcion: string;
  readonly contenido?: Node;
  readonly textoVolver: string;
  readonly textoAccion: string;
  readonly textoCargando: string;
  readonly variante: VarianteBoton;
  readonly icono?: NombreIcono;
  /** false si el formulario tiene errores y no hay que enviar. */
  readonly validar?: () => boolean;
  /** Hace el pedido; si falla, se muestra el error sin cerrar. */
  readonly ejecutar: () => Promise<void>;
  readonly alFallar: (error: ApiError, zonaDeAviso: HTMLElement, ventana: Dialogo, reintentar: () => void) => void;
}

/** Diálogo chico de confirmación (`alertdialog`, foco en la opción segura) para acciones terminales. */
export function dialogoDeConfirmacion(opciones: OpcionesConfirmacion): Dialogo {
  const zonaDeAviso = el('div', {});
  const volver = boton({ texto: opciones.textoVolver, variante: 'secundario', alHacerClic: () => ventana.cerrar() });
  const confirmar = boton({ texto: opciones.textoAccion, variante: opciones.variante, ...(opciones.icono === undefined ? {} : { icono: opciones.icono }), requiereConexion: true });
  const cargando = conCarga(confirmar, opciones.textoCargando);

  async function enviar(): Promise<void> {
    if (opciones.validar?.() === false) return;
    zonaDeAviso.replaceChildren();
    cargando(true);
    try {
      await opciones.ejecutar();
      ventana.cerrar();
    } catch (error) {
      cargando(false);
      if (!(error instanceof ApiError) || error.codigo === 'SESION_VENCIDA') return;
      opciones.alFallar(error, zonaDeAviso, ventana, () => void enviar());
    }
  }
  confirmar.addEventListener('click', () => void enviar());

  const ventana = dialogo({
    titulo: opciones.titulo,
    descripcion: opciones.descripcion,
    rol: 'alertdialog',
    contenido: el('div', {}, zonaDeAviso, opciones.contenido ?? null),
    acciones: [volver, confirmar],
    focoInicial: volver,
  });
  return ventana;
}

function alFallarEstado(turno: DetalleTurno, entorno: EntornoDeAccion) {
  return (error: ApiError, zonaDeAviso: HTMLElement, ventana: Dialogo, reintentar: () => void) => {
    const traducido = errorDeEstado(error, turno, entorno);
    if (traducido.accion === 'RECARGAR' || error.status === 404) {
      ventana.cerrar();
      avisarYRecargar(traducido, entorno);
      return;
    }
    zonaDeAviso.replaceChildren(
      avisoDeError(traducido, {
        alReintentar: reintentar,
        alVerActualizado: () => {
          ventana.cerrar();
          entorno.alTerminar();
        },
      }),
    );
  };
}

async function aplicar(turno: DetalleTurno, entorno: EntornoDeAccion, accion: AccionDeEstado, motivo?: string): Promise<void> {
  await cambiarEstado(entorno.http, turno.id, { accion, version: turno.version ?? 0, ...(motivo === undefined ? {} : { motivo }) });
}

export function abrirCancelar(turno: DetalleTurno, entorno: EntornoDeAccion): void {
  const motivo = campoConfig({ etiqueta: 'Motivo', opcional: true, ayuda: 'Hasta 200 caracteres.', control: el('input', { name: 'motivo', type: 'text', autocomplete: 'off' }) });
  // "(opcional, no lo ve el paciente)", como en el mockup.
  motivo.contenedor.querySelector('label span')?.replaceChildren(' (opcional, no lo ve el paciente)');
  const nombre = nombreCompleto(turno.paciente);
  dialogoDeConfirmacion({
    titulo: `¿Cancelar el turno de ${nombre}?`,
    descripcion: `${resumenDelTurno(turno, entorno.zona)}. El horario queda libre enseguida y el paciente ve el turno cancelado en su link.`,
    contenido: motivo.contenedor,
    textoVolver: 'No, mantenerlo',
    textoAccion: 'Sí, cancelar turno',
    textoCargando: 'Cancelando…',
    variante: 'peligro-secundario',
    icono: 'cruz',
    validar: () => {
      const error = validarMotivoCancelacion(motivo.control.value);
      motivo.error(error);
      if (error !== null) motivo.control.focus();
      return error === null;
    },
    ejecutar: async () => {
      await aplicar(turno, entorno, 'CANCELAR', motivo.control.value);
      aviso(`Cancelamos el turno de ${nombre}`, { tono: 'exito' });
      entorno.alTerminar();
    },
    alFallar: alFallarEstado(turno, entorno),
  }).abrir();
}

export function abrirAusente(turno: DetalleTurno, entorno: EntornoDeAccion): void {
  const nombre = nombreCompleto(turno.paciente);
  dialogoDeConfirmacion({
    titulo: `¿Marcar a ${nombre} como ausente?`,
    descripcion: `${resumenDelTurno(turno, entorno.zona)}. El turno queda como Ausente y ya no se puede cambiar de estado.`,
    textoVolver: 'Volver',
    textoAccion: 'Marcar ausente',
    textoCargando: 'Marcando…',
    variante: 'primario',
    ejecutar: async () => {
      await aplicar(turno, entorno, 'MARCAR_AUSENTE');
      aviso(`Marcamos a ${nombre} como ausente`, { tono: 'exito' });
      entorno.alTerminar();
    },
    alFallar: alFallarEstado(turno, entorno),
  }).abrir();
}
