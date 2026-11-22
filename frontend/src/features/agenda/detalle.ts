import type { ContextoPagina } from '../../pages/pagina';
import { el } from '../../shared/dom';
import { formatearDia, hoyEn } from '../../shared/fechas';
import { ApiError } from '../../shared/http';
import { boton, enlaceBoton, type VarianteBoton } from '../../shared/ui/boton';
import { errorRecuperable } from '../../shared/ui/error-recuperable';
import { esqueleto } from '../../shared/ui/esqueleto';
import { tarjetaEstado } from '../../shared/ui/estado-vacio';
import { icono, type NombreIcono } from '../../shared/ui/iconos';
import { tarjetaHistorial } from '../auditoria/historial-turno';
import { marcarVisto, obtenerAgendaEntre, obtenerTurno, type DetalleTurno } from './api';
import { accionesDelTurno, nombreCompleto, type AccionTurno, type CambioPaciente } from './modelo';
import { puntosDeEntrada } from './puntos-de-entrada';
import { rutaDelDia } from './semana';
import { chipEstadoTurno, chipSobreturno } from './tarjeta';
import { diaLocal, horaLocal, sumarDias } from './tiempo';

/**
 * Detalle de turno (C-13, spec detalle-turno, design D7; mockup aprobado en #27): una página con los datos del
 * paciente y del turno, la nota interna y las acciones válidas para el estado, cuyos diálogos son de C-14.
 */

const ACCIONES: Record<AccionTurno, { readonly texto: string; readonly icono: NombreIcono; readonly variante: VarianteBoton }> = {
  CONFIRMAR: { texto: 'Confirmar', icono: 'check', variante: 'primario' },
  REPROGRAMAR: { texto: 'Reprogramar', icono: 'repetir', variante: 'secundario' },
  AJUSTAR_DURACION: { texto: 'Ajustar duración', icono: 'reloj-arena', variante: 'secundario' },
  MARCAR_AUSENTE: { texto: 'Marcar ausente', icono: 'ausente', variante: 'secundario' },
  EDITAR_NOTA: { texto: 'Editar nota', icono: 'lapiz', variante: 'secundario' },
  CANCELAR: { texto: 'Cancelar turno', icono: 'cruz', variante: 'peligro-secundario' },
};

const VERBO: Record<CambioPaciente, string> = { RESERVO: 'reservó', REPROGRAMO: 'reprogramó', CANCELO: 'canceló' };

const mayuscula = (texto: string) => texto.charAt(0).toUpperCase() + texto.slice(1);
const formatoDni = new Intl.NumberFormat('es-AR', { useGrouping: true, maximumFractionDigits: 0 });
/** "30.000.003" solo para leer (D7); si no son solo dígitos, se muestra tal cual. */
const dniLegible = (dni: string) => (/^\d+$/.test(dni) ? formatoDni.format(Number(dni)) : dni);
const hrefTelefono = (telefono: string) => `tel:${telefono.replace(/[^\d+]/g, '')}`;

/** "hoy a las 10:05", "ayer a las 21:40" o "el jueves 19 de noviembre a las 16:30", en la zona del consultorio. */
function cuandoFue(instante: string, zona: string): string {
  const dia = diaLocal(instante, zona);
  const hoy = hoyEn(zona);
  const hora = horaLocal(instante, zona);
  if (dia === hoy) return `hoy a las ${hora}`;
  if (dia === sumarDias(hoy, -1)) return `ayer a las ${hora}`;
  return `el ${formatearDia(dia, zona)} a las ${hora}`;
}

function tarjeta(titulo: string, subtitulo: string | null, ...contenido: (Node | null)[]): HTMLElement {
  return el(
    'section',
    { class: 'bg-superficie border border-borde rounded-lg shadow-sm p-4 md:p-5' },
    el('h3', { class: 'm-0 text-lg font-bold' }, titulo),
    subtitulo === null ? null : el('p', { class: 'm-0 mt-1 text-sm text-texto-tenue' }, subtitulo),
    el('div', { class: 'mt-3' }, ...contenido),
  );
}

function listaDeDatos(...pares: [string, Node | string][]): HTMLDListElement {
  return el(
    'dl',
    { class: 'grid grid-cols-[auto_minmax(0,1fr)] gap-x-4 gap-y-2 m-0' },
    ...pares.flatMap(([termino, valor]) => [el('dt', { class: 'text-sm text-texto-tenue' }, termino), el('dd', { class: 'm-0 font-medium [overflow-wrap:anywhere]' }, valor)]),
  );
}

const vacio = (texto: string) => el('span', { class: 'font-normal text-texto-tenue' }, texto);

function aviso(tono: 'info' | 'sobreturno', nombreIcono: NombreIcono, titulo: string, texto: string, atributos: Record<string, string | boolean> = {}): HTMLElement {
  const clases = tono === 'info' ? 'border-reservado-bd bg-reservado-bg' : 'border-sobreturno-bd bg-sobreturno-bg';
  return el(
    'div',
    { ...atributos, class: `flex gap-3 items-start px-4 py-3 rounded-md border text-texto ${clases}` },
    icono(nombreIcono, `w-icono h-icono flex-none ${tono === 'info' ? 'text-primario' : 'text-sobreturno-fg'}`),
    el('div', {}, el('strong', { class: 'block' }, titulo), el('p', { 'data-texto': true, class: 'm-0 text-sm' }, texto)),
  );
}

function avisoDeCambio(turno: DetalleTurno, zona: string): HTMLElement | null {
  if (turno.cambioPaciente === null) return null;
  const partes = [`El paciente lo ${VERBO[turno.cambioPaciente]} desde tu link${turno.novedadPacienteAt === null ? '' : ` ${cuandoFue(turno.novedadPacienteAt, zona)}`}.`];
  if (turno.cambioPaciente === 'REPROGRAMO' && turno.horarioAnterior !== null) partes.push(`Antes era el ${formatearDia(turno.horarioAnterior.inicio, zona)} a las ${horaLocal(turno.horarioAnterior.inicio, zona)}.`);
  return aviso('info', 'info', 'Cambio de tu paciente', partes.join(' '), { 'data-cambio-paciente': true, role: 'status' });
}

function vistaDelTurno(turno: DetalleTurno, zona: string, historial: HTMLElement): { readonly nodo: HTMLElement; readonly avisoCambio: HTMLElement | null; readonly avisoSobreturno: HTMLElement | null } {
  const dia = diaLocal(turno.inicio, zona);
  const avisoCambio = avisoDeCambio(turno, zona);
  const avisoSobreturno = turno.esSobreturno ? aviso('sobreturno', 'mas', 'Es un sobreturno', 'Se atiende en un horario que ya tiene otro turno.', { 'data-aviso-sobreturno': true }) : null;

  const telefono =
    turno.paciente.telefono === null
      ? vacio('Sin teléfono')
      : el('a', { href: hrefTelefono(turno.paciente.telefono), class: 'inline-flex items-center gap-1 font-semibold text-primario' }, icono('telefono', 'w-icono-sm h-icono-sm flex-none'), turno.paciente.telefono);

  const cancelacion: [string, Node | string][] =
    turno.estado === 'CANCELADO'
      ? [
          [
            'Cancelación',
            el(
              'span',
              {},
              turno.canceladoPor === 'PACIENTE' ? 'Cancelado por el paciente' : turno.canceladoPor === 'ODONTOLOGO' ? 'Cancelado por vos' : 'Cancelado',
              turno.motivoCancelacion === null ? null : el('span', { class: 'block font-normal text-sm text-texto-tenue' }, `“${turno.motivoCancelacion}”`),
            ),
          ],
        ]
      : [];

  const acciones = accionesDelTurno(turno.estado, turno.inicio, new Date());
  const sinAusenteTodavia = (turno.estado === 'RESERVADO' || turno.estado === 'CONFIRMADO') && !acciones.includes('MARCAR_AUSENTE');

  const nodo = el(
    'article',
    { 'aria-labelledby': 'titulo-turno', class: 'grid gap-4 lg:grid-cols-[minmax(0,1.4fr)_minmax(calc(var(--space-7)*6),1fr)] lg:items-start' },
    el(
      'div',
      { class: 'grid gap-4' },
      el(
        'div',
        { class: 'flex flex-col gap-2' },
        el('div', { class: 'flex flex-wrap gap-1' }, chipEstadoTurno(turno.estado), turno.esSobreturno ? chipSobreturno() : null),
        el('h2', { id: 'titulo-turno', class: 'm-0 text-2xl font-bold' }, nombreCompleto(turno.paciente)),
        el('p', { class: 'm-0 text-lg font-semibold tabular' }, `${mayuscula(formatearDia(dia, zona))} · ${horaLocal(turno.inicio, zona)} a ${horaLocal(turno.fin, zona)}`),
      ),
      avisoCambio,
      avisoSobreturno,
      tarjeta(
        'Paciente',
        null,
        listaDeDatos(['DNI', turno.paciente.dni === null ? vacio('Sin DNI') : dniLegible(turno.paciente.dni)], ['Teléfono', telefono], ['Email', turno.paciente.email ?? vacio('No dejó email')]),
      ),
      tarjeta(
        'Turno',
        null,
        listaDeDatos(['Prestación', `${turno.prestacion} · ${turno.duracionMin} min`], ['Origen', turno.origen === 'LINK' ? 'Reservó por tu link' : 'Lo cargaste vos'], ...cancelacion),
      ),
      historial,
    ),
    el(
      'div',
      { class: 'grid gap-4' },
      tarjeta(
        'Nota interna',
        'No la ve el paciente.',
        turno.notaInterna === null
          ? el('p', { class: 'm-0 text-texto-tenue' }, 'Sin nota. Solo la ves vos.')
          : el('p', { 'data-nota': true, class: 'm-0 p-3 rounded-md border border-sobreturno-bd bg-sobreturno-bg text-texto whitespace-pre-wrap' }, turno.notaInterna),
      ),
      tarjeta(
        'Acciones',
        null,
        el(
          'div',
          { 'data-acciones': true, class: 'grid gap-2' },
          ...acciones.map((accion) => {
            const { texto, icono: nombreIcono, variante } = ACCIONES[accion];
            const b = boton({ texto, icono: nombreIcono, variante, requiereConexion: true, alHacerClic: () => puntosDeEntrada().accion(accion, turno) });
            b.classList.add('w-full', '!justify-start');
            return b;
          }),
        ),
        sinAusenteTodavia ? el('p', { class: 'm-0 mt-2 text-xs text-texto-tenue' }, '“Marcar ausente” aparece cuando el turno ya empezó.') : null,
      ),
    ),
  );
  return { nodo, avisoCambio, avisoSobreturno };
}

export function montarDetalle(contexto: ContextoPagina): void {
  const { contenedor, http, zonaHoraria: zona } = contexto;
  const id = contexto.params['id'] ?? '';
  const volver = el('a', { href: '/app/agenda', 'data-link': true, class: 'inline-flex items-center gap-1 min-h-touch mb-2 font-semibold text-primario no-underline' }, icono('izquierda', 'w-icono-sm h-icono-sm'), 'Volver a la agenda');
  const cuerpo = el('div', {});
  contenedor.replaceChildren(el('div', { class: 'pb-6' }, volver, cuerpo));

  /** Avisa con qué turno se superpone un sobreturno. Si no se puede saber, queda el texto general. */
  async function completarSobreturno(turno: DetalleTurno, destino: HTMLElement): Promise<void> {
    try {
      const { turnos } = await obtenerAgendaEntre(http, turno.inicio, turno.fin);
      const base = turnos.find((t) => t.id !== turno.id && !t.esSobreturno && t.estado !== 'CANCELADO');
      const texto = destino.querySelector('[data-texto]');
      if (base !== undefined && texto !== null) texto.textContent = `Se superpone con el turno de ${horaLocal(base.inicio, zona)} de ${nombreCompleto(base.paciente)}.`;
    } catch {
      // Sin la agenda de ese rango, alcanza con saber que es un sobreturno.
    }
  }

  /** Abrir el detalle deja visto el cambio del paciente (DD-30). Si falla, la marca sigue y se intenta la próxima vez. */
  async function dejarVisto(turno: DetalleTurno, destino: HTMLElement): Promise<void> {
    if (turno.novedadPacienteAt === null) return;
    try {
      await marcarVisto(http, turno.id, turno.novedadPacienteAt);
      destino.querySelector('[data-texto]')?.append(' Ya lo marcamos como visto.');
    } catch {
      // Se reintenta al volver a abrirlo.
    }
  }

  async function cargar(): Promise<void> {
    cuerpo.replaceChildren(esqueleto({ etiqueta: 'Cargando el turno…', filas: 3 }));
    // El historial se pide en paralelo y maneja sus propios errores (C-18, design D8).
    const historial = tarjetaHistorial(http, id, zona);
    try {
      const turno = await obtenerTurno(http, id);
      volver.setAttribute('href', rutaDelDia(diaLocal(turno.inicio, zona)));
      const { nodo, avisoCambio, avisoSobreturno } = vistaDelTurno(turno, zona, historial);
      cuerpo.replaceChildren(nodo);
      if (avisoCambio !== null) void dejarVisto(turno, avisoCambio);
      if (avisoSobreturno !== null) void completarSobreturno(turno, avisoSobreturno);
    } catch (error) {
      if (error instanceof ApiError && error.codigo === 'SESION_VENCIDA') return;
      const reintentar = () => void cargar();
      if (error instanceof ApiError && error.status === 404) {
        cuerpo.replaceChildren(
          tarjetaEstado(
            {
              icono: 'alerta',
              titulo: 'No encontramos este turno',
              texto: 'Puede que el link sea viejo o que el turno sea de otro consultorio.',
              acciones: [enlaceBoton({ texto: 'Volver a la agenda', href: '/app/agenda' })],
            },
            { role: 'alert' },
          ),
        );
      } else if (error instanceof ApiError && error.codigo === 'SIN_CONEXION') {
        cuerpo.replaceChildren(
          tarjetaEstado({
            icono: 'sin-conexion',
            titulo: 'Necesitás conexión para ver este turno',
            texto: 'Los datos del paciente no se guardan en el dispositivo. En la agenda guardada ves el horario y el estado.',
            acciones: [boton({ texto: 'Reintentar', icono: 'reintentar', alHacerClic: reintentar }), enlaceBoton({ texto: 'Volver a la agenda', href: '/app/agenda', variante: 'secundario' })],
          }),
        );
      } else {
        cuerpo.replaceChildren(errorRecuperable({ titulo: 'No pudimos cargar el turno', alReintentar: reintentar }));
      }
    }
  }

  void cargar();
}
