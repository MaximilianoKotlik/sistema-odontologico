import type { ContextoPagina } from '../../pages/pagina';
import { el } from '../../shared/dom';
import { ApiError } from '../../shared/http';
import { aviso } from '../../shared/ui/aviso';
import { boton, clasesBoton } from '../../shared/ui/boton';
import { errorRecuperable } from '../../shared/ui/error-recuperable';
import { esqueleto } from '../../shared/ui/esqueleto';
import { tarjetaEstado } from '../../shared/ui/estado-vacio';
import { icono, type NombreIcono } from '../../shared/ui/iconos';
import { dniLegible, telefonoLegible } from '../agenda/formato';
import { listarPacientes, type Paciente } from './api';
import { tomarAvisoDeRevision } from './aviso-de-revision';
import { cantidadConDatos, lineaDeLoInformado, nombreCompleto, nombreDeLista } from './formato';

/**
 * `/app/pacientes` (C-17, spec pantalla-pacientes, mockup aprobado #36): lista por apellido con el filtro "Requieren
 * revisión" / "Todos", paginada con "Mostrar más pacientes" (D4). La lista no se guarda en el dispositivo (Ley 25.326).
 */

import { RUTA_PACIENTES, RUTA_PARA_REVISAR } from './rutas';

const CLASE_FILTRO =
  'inline-flex items-center gap-2 min-h-touch px-3 rounded-full border border-borde bg-superficie text-texto font-sans text-sm font-semibold cursor-pointer ' +
  'hover:bg-superficie-tenue aria-pressed:bg-primario-tinte aria-pressed:border-primario aria-pressed:text-primario';
const CLASE_CONTADOR = 'inline-grid place-items-center min-w-6 h-6 px-1 rounded-full border text-xs font-bold leading-none bg-sobreturno-bg border-sobreturno-bd text-sobreturno-fg';

function contador(cantidad: number): HTMLElement[] {
  return cantidad > 0 ? [el('span', { 'aria-hidden': 'true', class: CLASE_CONTADOR }, String(cantidad)), el('span', { class: 'sr-only' }, ` (${cantidad} para revisar)`)] : [];
}

function dato(nombreIcono: NombreIcono, etiqueta: string, valor: string): HTMLElement {
  return el('span', { class: 'inline-flex items-center gap-1' }, icono(nombreIcono, 'w-icono-sm h-icono-sm flex-none text-texto-sutil'), el('span', { class: 'sr-only' }, `${etiqueta} `), valor);
}

export function montarListaDePacientes(contexto: ContextoPagina): void {
  const { contenedor, http, zonaHoraria: zona } = contexto;
  let soloConRevision = new URLSearchParams(location.search).get('requiereRevision') === 'true';
  let pagina = 0;
  let pacientes: Paciente[] = [];
  let total = 0;

  const filtroRevision = el('button', { type: 'button', class: CLASE_FILTRO });
  const filtroTodos = el('button', { type: 'button', class: CLASE_FILTRO }, 'Todos');
  const filtros = el('div', { role: 'group', 'aria-label': 'Filtrar pacientes', class: 'flex flex-wrap gap-2' }, filtroTodos, filtroRevision);
  const resumen = el('p', { 'data-resumen': true, 'aria-live': 'polite', class: 'mt-3 mb-0 text-sm text-texto-tenue' });
  const cuerpo = el('div', { class: 'mt-4' });
  contenedor.replaceChildren(el('div', { 'data-pacientes': true, class: 'pb-6 max-w-3xl' }, filtros, resumen, cuerpo));

  const avisoPendiente = tomarAvisoDeRevision();
  if (avisoPendiente !== null) aviso(avisoPendiente, { tono: 'exito' }).dataset['avisoRevision'] = '';

  function pintarFiltros(): void {
    filtroTodos.setAttribute('aria-pressed', String(!soloConRevision));
    filtroRevision.setAttribute('aria-pressed', String(soloConRevision));
    const cantidad = soloConRevision ? total : (contexto.pacientes?.cantidad() ?? 0);
    filtroRevision.replaceChildren(icono('alerta', 'w-icono-sm h-icono-sm flex-none'), 'Requieren revisión', ...contador(cantidad));
  }
  const dejarDeEscuchar = contexto.pacientes?.suscribir(() => {
    if (!contenedor.isConnected) {
      dejarDeEscuchar?.();
      return;
    }
    pintarFiltros();
  });

  function fila(paciente: Paciente): HTMLElement {
    const marcado = paciente.requiereRevision;
    const enlace = el(
      'a',
      { href: `${RUTA_PACIENTES}/${encodeURIComponent(paciente.id)}`, 'data-link': true, 'aria-label': `${marcado ? 'Revisar' : 'Ver'} a ${nombreCompleto(paciente)}`, class: `${clasesBoton(marcado ? 'primario' : 'secundario')} flex-none` },
      marcado ? 'Revisar' : 'Ver',
    );
    return el(
      'li',
      { 'data-paciente': paciente.id, class: 'flex flex-wrap items-center gap-3 py-3 px-4 bg-superficie border border-borde rounded-md' },
      el(
        'div',
        { class: 'flex-1 min-w-0' },
        el(
          'span',
          { 'data-nombre': true, class: 'flex flex-wrap items-center gap-2 font-bold text-texto' },
          nombreDeLista(paciente),
          ' ',
          marcado
            ? el('span', { 'data-etiqueta-revisar': true, class: 'inline-flex items-center gap-1 px-2 rounded-full border text-xs font-semibold bg-sobreturno-bg border-sobreturno-bd text-sobreturno-fg' }, icono('alerta', 'w-icono-sm h-icono-sm flex-none'), 'Revisar')
            : null,
        ),
        el(
          'div',
          { class: 'flex flex-wrap gap-x-4 gap-y-1 mt-1 text-sm text-texto-tenue tabular' },
          dato('dni', 'DNI', dniLegible(paciente.dni)),
          ' ',
          dato('telefono', 'Teléfono', telefonoLegible(paciente.telefono)),
          ...(paciente.email === null ? [] : [' ', dato('sobre', 'Email', paciente.email)]),
        ),
        paciente.informado === null ? null : el('p', { class: 'mt-1 mb-0 text-sm text-texto-tenue' }, lineaDeLoInformado(paciente.informado, zona)),
      ),
      enlace,
    );
  }

  function pintar(): void {
    pintarFiltros();
    resumen.textContent = soloConRevision ? (total === 0 ? 'Ningún paciente para revisar' : cantidadConDatos(total)) : `${total} ${total === 1 ? 'paciente' : 'pacientes'} · ordenados por apellido`;
    if (pacientes.length === 0) {
      cuerpo.replaceChildren(
        soloConRevision
          ? tarjetaEstado({
              icono: 'check',
              titulo: 'No hay pacientes para revisar',
              texto: 'Cuando alguien reserve por tu link con un DNI que ya tenías y otro nombre, lo vas a ver acá y en tu agenda.',
              acciones: [boton({ texto: 'Ver todos los pacientes', variante: 'secundario', alHacerClic: () => elegir(false) })],
            })
          : tarjetaEstado({ icono: 'personas', titulo: 'Todavía no tenés pacientes', texto: 'Se agregan solos cuando reservan por tu link o cuando cargás un turno.' }),
      );
      return;
    }
    const explicacion = soloConRevision
      ? el(
          'div',
          { class: 'flex gap-3 items-start mb-4 px-4 py-3 rounded-md border bg-primario-tinte border-primario text-texto text-sm' },
          icono('info', 'w-icono-sm h-icono-sm flex-none mt-1 text-primario'),
          el('p', { class: 'm-0' }, 'Son pacientes que reservaron por tu link con un DNI que ya tenías, pero con otro nombre. No cambiamos nada: elegís vos qué datos quedan.'),
        )
      : null;
    const mas = pacientes.length < total ? el('div', { class: 'flex justify-center mt-4' }, boton({ texto: 'Mostrar más pacientes', variante: 'secundario', alHacerClic: () => void cargar(pagina + 1) })) : null;
    cuerpo.replaceChildren(...(explicacion === null ? [] : [explicacion]), el('ul', { class: 'list-none m-0 p-0 grid gap-3' }, ...pacientes.map(fila)), ...(mas === null ? [] : [mas]));
  }

  async function cargar(siguiente: number): Promise<void> {
    if (siguiente === 0) cuerpo.replaceChildren(esqueleto({ etiqueta: 'Cargando pacientes…', filas: 3 }));
    try {
      const respuesta = await listarPacientes(http, { soloConRevision, pagina: siguiente });
      pagina = siguiente;
      pacientes = siguiente === 0 ? [...respuesta.items] : [...pacientes, ...respuesta.items];
      total = respuesta.total;
      pintar();
    } catch (error) {
      if (error instanceof ApiError && error.codigo === 'SESION_VENCIDA') return;
      const reintentar = () => void cargar(0);
      cuerpo.replaceChildren(
        error instanceof ApiError && error.codigo === 'SIN_CONEXION'
          ? tarjetaEstado({
              icono: 'sin-conexion',
              titulo: 'Sin conexión',
              texto: 'La lista de pacientes no se guarda en el dispositivo. Se va a ver apenas vuelva la conexión.',
              acciones: [boton({ texto: 'Reintentar', icono: 'reintentar', alHacerClic: reintentar })],
            })
          : errorRecuperable({ titulo: 'No pudimos cargar tus pacientes', texto: 'Puede ser un problema momentáneo del servidor. Probá de nuevo en unos segundos.', alReintentar: reintentar }),
      );
    }
  }

  function elegir(revision: boolean): void {
    if (revision === soloConRevision) return;
    soloConRevision = revision;
    history.replaceState(history.state, '', revision ? RUTA_PARA_REVISAR : RUTA_PACIENTES);
    pintarFiltros();
    void cargar(0);
  }
  filtroTodos.addEventListener('click', () => elegir(false));
  filtroRevision.addEventListener('click', () => elegir(true));

  pintarFiltros();
  void cargar(0);
}
