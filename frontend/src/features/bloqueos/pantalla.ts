import type { ContextoPagina } from '../../pages/pagina';
import { el } from '../../shared/dom';
import { formatearDia, hoyEn } from '../../shared/fechas';
import { ApiError } from '../../shared/http';
import { aviso } from '../../shared/ui/aviso';
import { boton } from '../../shared/ui/boton';
import { errorRecuperable } from '../../shared/ui/error-recuperable';
import { esqueleto } from '../../shared/ui/esqueleto';
import { tarjetaEstado } from '../../shared/ui/estado-vacio';
import { icono } from '../../shared/ui/iconos';
import { obtenerAgendaEntre } from '../agenda/api';
import { nombreCompleto, type BloqueoAgenda, type TurnoAgenda } from '../agenda/modelo';
import { rutaDelTurno } from '../agenda/tarjeta';
import { horaLocal } from '../agenda/tiempo';
import { campoConfig } from '../configuracion/ui';
import { dialogoDeConfirmacion } from '../turnos/cambiar-estado';
import type { ErrorDeTurno } from '../turnos/errores';
import { abrirFormulario } from '../turnos/formulario';
import { avisoDeError } from '../turnos/ui';
import { crearBloqueo, eliminarBloqueo, listarBloqueos } from './api';
import { agruparBloqueos, describirBloqueo, rangoDelBloqueo, validarMotivoBloqueo, type CampoBloqueo } from './validaciones';

/**
 * Bloqueos (C-14, design D8; spec bloqueos; mockup `bloqueos.html` aprobado en #31): listado de los actuales y futuros,
 * alta por horas o por días completos y baja con confirmación. Un bloqueo no puede pisar turnos activos: el 409
 * `BLOQUEO_CON_TURNOS` lista cada turno con acceso a su detalle (RN-AG-05).
 */

/** Máximo rango que admite `GET /api/v1/agenda` (C-12) para nombrar los turnos en conflicto. */
const DIAS_MAXIMOS_AGENDA = 42;

const tituloDelBloqueo = (b: BloqueoAgenda) => b.motivo ?? 'Bloqueado';
const comillas = (b: BloqueoAgenda) => (b.motivo === null ? 'este bloqueo' : `el bloqueo “${b.motivo}”`);

/** "Vie 20 · 16:00". */
function diaYHoraCortos(instante: string, zona: string): string {
  const [diaSemana = '', numero = ''] = formatearDia(instante, zona).split(' ');
  return `${diaSemana.charAt(0).toUpperCase()}${diaSemana.slice(1, 3)} ${numero} · ${horaLocal(instante, zona)}`;
}

function traducirError(error: ApiError, cantidad: number): ErrorDeTurno {
  const aviso_ = (titulo: string, texto: string, accion: ErrorDeTurno['accion'] = null): ErrorDeTurno => ({ tono: 'peligro', titulo, texto, accion, campos: [] });
  switch (error.codigo) {
    case 'BLOQUEO_CON_TURNOS':
      return aviso_(cantidad === 1 ? 'Hay 1 turno en ese horario' : `Hay ${cantidad} turnos en ese horario`, 'Cancelalos o reprogramalos antes de bloquear. Después volvé a intentar.');
    case 'HORARIO_INVALIDO':
      return { ...aviso_('Revisá el horario', ''), campos: [{ campo: 'hasta', mensaje: 'El fin tiene que ser posterior al inicio.' }] };
    case 'VALIDACION':
      return { ...aviso_('Revisá los datos', ''), campos: [{ campo: 'motivo', mensaje: 'El motivo puede tener hasta 120 caracteres.' }] };
    case 'PLAN_VENCIDO':
      return aviso_('Tu agenda está en solo lectura', 'El período de prueba terminó. Activá el plan para volver a bloquear horarios; tus datos siguen guardados.');
    case 'SIN_CONEXION':
      return aviso_('No hay conexión', 'Revisá tu conexión y volvé a intentar. No perdiste lo que cargaste.', 'REINTENTAR');
    default:
      if (error.status === 404) return aviso_('Ese bloqueo ya no existe', 'Puede que lo hayas quitado desde otra pestaña.');
      return aviso_('No pudimos guardar', 'Puede ser un problema momentáneo del servidor. Probá de nuevo en unos segundos.', 'REINTENTAR');
  }
}

function interruptorTodoElDia(alCambiar: (valor: boolean) => void): { readonly elemento: HTMLElement; readonly control: HTMLButtonElement } {
  const estado = el('span', { 'aria-hidden': 'true', class: 'text-sm font-semibold' }, 'No');
  const control = el(
    'button',
    { type: 'button', role: 'switch', 'aria-checked': 'false', 'aria-labelledby': 'todo-el-dia', 'aria-describedby': 'todo-el-dia-ayuda', class: 'group inline-flex items-center gap-2 min-h-touch bg-transparent border-0 p-0 cursor-pointer' },
    el(
      'span',
      { 'aria-hidden': 'true', class: 'relative inline-block w-7 h-5 rounded-full border-control border-borde-fuerte bg-superficie-tenue transition-colors duration-rapida group-aria-checked:bg-exito group-aria-checked:border-exito' },
      el('span', { class: 'absolute top-[calc(var(--space-1)/2)] left-[calc(var(--space-1)/2)] w-4 h-4 rounded-full bg-superficie shadow-sm transition-transform duration-rapida group-aria-checked:translate-x-5' }),
    ),
    estado,
  );
  control.addEventListener('click', () => {
    const valor = control.getAttribute('aria-checked') !== 'true';
    control.setAttribute('aria-checked', String(valor));
    estado.textContent = valor ? 'Sí' : 'No';
    alCambiar(valor);
  });
  const elemento = el(
    'div',
    { class: 'flex items-center justify-between gap-3 mb-4' },
    el('div', {}, el('span', { id: 'todo-el-dia', class: 'block font-semibold' }, 'Todo el día'), el('span', { id: 'todo-el-dia-ayuda', class: 'block text-sm text-texto-tenue' }, 'Para vacaciones o días completos.')),
    control,
  );
  return { elemento, control };
}

export function montarBloqueos(contexto: ContextoPagina): void {
  const { contenedor, http, zonaHoraria: zona } = contexto;
  const cuerpo = el('div', {});
  const bloquear = () => boton({ texto: 'Bloquear horario', icono: 'mas', requiereConexion: true, alHacerClic: () => abrirAlta() });
  contenedor.replaceChildren(
    el(
      'div',
      { 'data-bloqueos': true, class: 'pb-6 max-w-3xl' },
      el('div', { class: 'flex flex-wrap items-center justify-between gap-3 mb-4' }, el('p', { class: 'm-0 text-sm text-texto-tenue' }, 'Nadie puede reservar en estos horarios, ni vos podés cargar turnos ahí.'), bloquear()),
      cuerpo,
    ),
  );

  function tarjeta(bloqueo: BloqueoAgenda): HTMLElement {
    const quitar = el(
      'button',
      { type: 'button', 'aria-label': `Quitar ${comillas(bloqueo)}`, 'data-requiere-conexion': true, class: 'inline-flex items-center justify-center min-h-touch min-w-touch rounded-md border border-borde bg-superficie text-texto cursor-pointer hover:bg-superficie-tenue' },
      icono('basura', 'w-icono-sm h-icono-sm'),
    );
    quitar.addEventListener('click', () => abrirBaja(bloqueo));
    return el(
      'li',
      { 'data-bloqueo': bloqueo.id, class: 'flex items-center gap-3 py-3 pr-3 pl-4 rounded-md border border-l-4 border-bloqueo-bd fondo-rayado text-bloqueo-fg' },
      el('div', { class: 'flex-1 min-w-0' }, el('span', { class: 'block font-bold text-texto' }, tituloDelBloqueo(bloqueo)), ' ', el('span', { class: 'block text-sm text-texto tabular' }, describirBloqueo(bloqueo, zona))),
      quitar,
    );
  }

  function pintar(bloqueos: readonly BloqueoAgenda[]): void {
    if (bloqueos.length === 0) {
      cuerpo.replaceChildren(
        tarjetaEstado({ icono: 'candado', titulo: 'No tenés horarios bloqueados', texto: 'Bloqueá vacaciones, congresos o trámites para que nadie reserve en ese período.', acciones: [bloquear()] }),
      );
      return;
    }
    cuerpo.replaceChildren(
      ...agruparBloqueos(bloqueos, new Date(), zona).flatMap((grupo) => [
        el('h3', { class: 'm-0 mt-5 mb-2 text-sm font-semibold text-texto-tenue first:mt-0' }, grupo.titulo),
        el('ul', { class: 'list-none m-0 p-0 grid gap-3' }, ...grupo.bloqueos.map(tarjeta)),
      ]),
    );
  }

  async function cargar(): Promise<void> {
    cuerpo.replaceChildren(esqueleto({ etiqueta: 'Cargando tus bloqueos…', filas: 3 }));
    try {
      pintar(await listarBloqueos(http));
    } catch (error) {
      if (error instanceof ApiError && error.codigo === 'SESION_VENCIDA') return;
      const reintentar = () => void cargar();
      cuerpo.replaceChildren(
        error instanceof ApiError && error.codigo === 'SIN_CONEXION'
          ? tarjetaEstado({ icono: 'sin-conexion', titulo: 'Necesitás conexión para ver tus bloqueos', texto: 'Volvé a intentar cuando tengas conexión.', acciones: [boton({ texto: 'Reintentar', icono: 'reintentar', alHacerClic: reintentar })] })
          : errorRecuperable({ titulo: 'No pudimos cargar tus bloqueos', alReintentar: reintentar }),
      );
    }
  }

  /** Turnos en conflicto con su día, hora y paciente, sacados de la agenda del rango (C-12 manda solo los ids). */
  async function turnosEnConflicto(ids: readonly string[], inicio: string, fin: string): Promise<(TurnoAgenda | { readonly id: string })[]> {
    const tope = new Date(Math.min(Date.parse(fin), Date.parse(inicio) + DIAS_MAXIMOS_AGENDA * 86400000)).toISOString();
    const turnos = await obtenerAgendaEntre(http, inicio, tope)
      .then((a) => a.turnos)
      .catch(() => []);
    return ids.map((id) => turnos.find((t) => t.id === id) ?? { id });
  }

  function listaDeConflictos(turnos: readonly (TurnoAgenda | { readonly id: string })[], alSalir: () => void): HTMLElement {
    return el(
      'ul',
      { class: 'list-none m-0 mt-2 mb-4 p-0 grid gap-1' },
      ...turnos.map((t) => {
        const ver = el('a', { href: rutaDelTurno(t.id), 'data-link': true, class: 'inline-flex items-center min-h-touch font-semibold text-primario underline whitespace-nowrap flex-none' }, 'Ver turno');
        ver.addEventListener('click', alSalir);
        const descripcion = 'inicio' in t ? el('span', { class: 'min-w-0' }, el('b', {}, diaYHoraCortos(t.inicio, zona)), ` · ${nombreCompleto(t.paciente)}`) : el('span', { class: 'min-w-0' }, 'Turno en ese horario');
        return el('li', { 'data-conflicto': true, class: 'flex items-center justify-between gap-2 px-2 py-1 rounded-sm bg-superficie text-texto tabular' }, descripcion, ' ', ver);
      }),
    );
  }

  function abrirAlta(): void {
    const hoy = hoyEn(zona);
    let todoElDia = false;
    const campo = (nombre: CampoBloqueo, etiqueta: string, tipo: 'date' | 'time', valor = '') =>
      campoConfig({ etiqueta, control: el('input', { name: nombre, type: tipo, value: valor, ...(tipo === 'time' ? { step: 300 } : {}) }) });
    const campos = {
      dia: campo('dia', 'Día', 'date', hoy),
      desde: campo('desde', 'Desde', 'time'),
      hasta: campo('hasta', 'Hasta', 'time'),
      diaDesde: campo('diaDesde', 'Desde el día', 'date', hoy),
      diaHasta: campo('diaHasta', 'Hasta el día', 'date', hoy),
      motivo: campoConfig({ etiqueta: 'Motivo', opcional: true, ayuda: 'Lo ves solo vos. Hasta 120 caracteres.', control: el('input', { name: 'motivo', type: 'text', autocomplete: 'off' }) }),
    };
    const porHoras = el('div', {}, campos.dia.contenedor, el('div', { class: 'grid grid-cols-2 gap-3 items-start' }, campos.desde.contenedor, campos.hasta.contenedor));
    const porDias = el('div', { hidden: true, class: 'grid grid-cols-2 gap-3 items-start' }, campos.diaDesde.contenedor, campos.diaHasta.contenedor);
    const resumen = el('p', { 'data-resumen-bloqueo': true, 'aria-live': 'polite', class: 'm-0 mt-1 text-sm text-texto-tenue tabular' });
    const conflictos = el('div', {});

    const valores = () => ({
      todoElDia,
      dia: campos.dia.control.value,
      desde: campos.desde.control.value,
      hasta: campos.hasta.control.value,
      diaDesde: campos.diaDesde.control.value,
      diaHasta: campos.diaHasta.control.value,
    });

    function actualizarResumen(): void {
      const rango = rangoDelBloqueo(valores(), zona);
      resumen.textContent = rango.ok ? `Bloquea: ${describirBloqueo({ id: '', inicio: rango.inicio, fin: rango.fin, motivo: null }, zona)}` : '';
    }

    const interruptor = interruptorTodoElDia((valor) => {
      todoElDia = valor;
      porHoras.hidden = valor;
      porDias.hidden = !valor;
      actualizarResumen();
    });

    for (const c of Object.values(campos)) {
      c.control.addEventListener('input', () => {
        c.error(null);
        actualizarResumen();
      });
      c.control.addEventListener('change', actualizarResumen);
    }

    abrirFormulario({
      titulo: 'Bloquear horario',
      contenido: el('div', {}, conflictos, interruptor.elemento, porHoras, porDias, campos.motivo.contenedor, resumen),
      textoGuardar: 'Bloquear',
      textoCargando: 'Bloqueando…',
      iconoGuardar: 'candado',
      conCambios: () => campos.desde.control.value !== '' || campos.hasta.control.value !== '' || campos.motivo.control.value !== '' || todoElDia,
      validar: () => {
        for (const c of Object.values(campos)) c.error(null);
        const rango = rangoDelBloqueo(valores(), zona);
        const errores = rango.ok ? [] : [...rango.errores];
        const errorMotivo = validarMotivoBloqueo(campos.motivo.control.value);
        if (errorMotivo !== null) errores.push({ campo: 'motivo', mensaje: errorMotivo });
        for (const { campo: nombre, mensaje } of errores) campos[nombre].error(mensaje);
        const primero = errores[0];
        if (primero !== undefined) campos[primero.campo].control.focus();
        return errores.length === 0;
      },
      ejecutar: async () => {
        const rango = rangoDelBloqueo(valores(), zona);
        if (!rango.ok) return;
        conflictos.replaceChildren();
        await crearBloqueo(http, { inicio: rango.inicio, fin: rango.fin, motivo: campos.motivo.control.value });
        aviso('Bloqueamos el horario', { tono: 'exito' });
        void cargar();
      },
      alFallar: async (error, { zonaDeAviso, ventana, reintentar }) => {
        const rango = rangoDelBloqueo(valores(), zona);
        const ids = Array.isArray(error.datos['turnosEnConflicto']) ? error.datos['turnosEnConflicto'].filter((x): x is string => typeof x === 'string') : [];
        const traducido = traducirError(error, ids.length);
        for (const { campo: nombre, mensaje } of traducido.campos) campos[nombre === 'hasta' && todoElDia ? 'diaHasta' : (nombre as CampoBloqueo)].error(mensaje);
        if (traducido.campos.length > 0) return;
        zonaDeAviso.replaceChildren(avisoDeError(traducido, { alReintentar: reintentar }));
        if (error.codigo === 'BLOQUEO_CON_TURNOS' && rango.ok) {
          const turnos = await turnosEnConflicto(ids, rango.inicio, rango.fin);
          zonaDeAviso.append(listaDeConflictos(turnos, () => ventana.cerrar()));
        }
      },
    });
  }

  function abrirBaja(bloqueo: BloqueoAgenda): void {
    dialogoDeConfirmacion({
      titulo: `¿Quitar ${comillas(bloqueo)}?`,
      descripcion: `${describirBloqueo(bloqueo, zona)}. Ese horario vuelve a quedar disponible para reservar.`,
      textoVolver: 'No, dejarlo',
      textoAccion: 'Quitar bloqueo',
      textoCargando: 'Quitando…',
      variante: 'peligro',
      ejecutar: async () => {
        await eliminarBloqueo(http, bloqueo.id);
        aviso(bloqueo.motivo === null ? 'Quitamos el bloqueo' : `Quitamos el bloqueo “${bloqueo.motivo}”`, { tono: 'exito' });
        void cargar();
      },
      alFallar: (error, zonaDeAviso, ventana, reintentar) => {
        if (error.status === 404) {
          ventana.cerrar();
          aviso('Ese bloqueo ya no existe.', { tono: 'error' });
          void cargar();
          return;
        }
        zonaDeAviso.replaceChildren(avisoDeError(traducirError(error, 0), { alReintentar: reintentar }));
      },
    }).abrir();
  }

  void cargar();
}
