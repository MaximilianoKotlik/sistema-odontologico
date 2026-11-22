import type { ContextoPagina } from '../../pages/pagina';
import { el } from '../../shared/dom';
import { formatearDia, hoyEn } from '../../shared/fechas';
import { ApiError } from '../../shared/http';
import { boton } from '../../shared/ui/boton';
import { errorRecuperable } from '../../shared/ui/error-recuperable';
import { esqueleto } from '../../shared/ui/esqueleto';
import { tarjetaEstado } from '../../shared/ui/estado-vacio';
import { icono } from '../../shared/ui/iconos';
import { diaLocal, horaLocal, sumarDias } from '../agenda/tiempo';
import { avisoAuth } from '../auth/ui';
import { campoConfig, CLASE_CONTROL } from '../configuracion/ui';
import { obtenerRegistro, type EventoAuditoria } from './api';
import { actorDelEvento, cambiosDelEvento, detalleDeAcceso, GRUPOS_DE_FILTRO, iconoDelEvento, instanteCorto, muestraIp, opcionDeFiltro, textoDelEvento } from './catalogo';
import { listaDeCambios } from './vista-evento';

/** Sección "Registro de cambios" (C-18, spec pantalla-registro-cambios, design D8; mockup `registro.html`). */

const MENSAJE_RANGO = 'Tiene que ser igual o posterior a "Desde".';

interface Filtros {
  turnoId: string | null;
  desde: string | null;
  hasta: string | null;
  accion: string | null;
}

function leerFiltros(): Filtros {
  const query = new URLSearchParams(location.search);
  const accion = query.get('accion');
  return { turnoId: query.get('turnoId') || null, desde: query.get('desde') || null, hasta: query.get('hasta') || null, accion: opcionDeFiltro(accion)?.clave ?? null };
}

/** Con `replaceState`, el botón Atrás vuelve a la pantalla anterior y recargar conserva los filtros (design D8). */
function escribirFiltros(filtros: Filtros): void {
  const query = new URLSearchParams();
  for (const clave of ['turnoId', 'desde', 'hasta', 'accion'] as const) {
    const valor = filtros[clave];
    if (valor !== null) query.set(clave, valor);
  }
  const texto = query.toString();
  history.replaceState(history.state, '', `${location.pathname}${texto === '' ? '' : `?${texto}`}`);
}

const hayFiltros = (f: Filtros) => f.turnoId !== null || f.desde !== null || f.hasta !== null || f.accion !== null;
const mayuscula = (texto: string) => texto.charAt(0).toUpperCase() + texto.slice(1);
const textoDelTurno = (evento: EventoAuditoria, zona: string) =>
  evento.contexto?.paciente === undefined || evento.contexto.paciente === null || evento.contexto.inicio === null
    ? null
    : `Turno de ${evento.contexto.paciente} · ${instanteCorto(evento.contexto.inicio, zona)}`;

function tituloDelDia(dia: string, zona: string): string {
  const hoy = hoyEn(zona);
  const nombre = formatearDia(dia, zona);
  if (dia === hoy) return `Hoy · ${nombre}`;
  if (dia === sumarDias(hoy, -1)) return `Ayer · ${nombre}`;
  return mayuscula(nombre);
}

function tonoDelEvento(evento: EventoAuditoria): string {
  if (evento.accion === 'LOGIN_FALLIDO') return 'bg-cancelado-bg text-cancelado-fg';
  if (evento.actor.tipo === 'PACIENTE') return 'bg-reservado-bg text-reservado-fg';
  if (evento.actor.tipo === 'SISTEMA') return 'bg-superficie-tenue text-texto-tenue';
  return 'bg-primario-tinte text-primario';
}

function contextoDeFila(evento: EventoAuditoria, zona: string): HTMLElement | null {
  const { contexto } = evento;
  if (evento.entidad !== 'turno' || evento.entidadId === null || contexto?.paciente === undefined || contexto.paciente === null || contexto.inicio === null) return null;
  return el(
    'p',
    { class: 'mt-1 mb-0 text-sm' },
    'Turno de ',
    el('a', { href: `/app/agenda/turnos/${encodeURIComponent(evento.entidadId)}`, 'data-link': true, class: 'font-semibold text-primario' }, contexto.paciente),
    ` · ${instanteCorto(contexto.inicio, zona)}`,
  );
}

function filaDeEvento(evento: EventoAuditoria, zona: string, conContexto: boolean): HTMLLIElement {
  const actor = actorDelEvento(evento.actor);
  const detalle = detalleDeAcceso(evento);
  const ip = muestraIp(evento.accion) ? evento.ip : null;
  return el(
    'li',
    { 'data-evento': evento.id, tabindex: -1, class: 'grid grid-cols-[var(--touch-min)_minmax(0,1fr)] gap-3 p-3 bg-superficie border border-borde rounded-md focus-visible:shadow-foco' },
    el('span', { 'aria-hidden': 'true', class: `grid place-items-center w-[calc(var(--space-6)+var(--space-2))] h-[calc(var(--space-6)+var(--space-2))] rounded-full ${tonoDelEvento(evento)}` }, icono(iconoDelEvento(evento), 'w-icono-sm h-icono-sm')),
    el(
      'div',
      { class: 'min-w-0' },
      el('div', { class: 'flex flex-wrap items-baseline gap-x-2' }, el('span', { class: 'font-semibold' }, textoDelEvento(evento)), el('span', { class: 'ml-auto text-sm text-texto-tenue tabular' }, horaLocal(evento.ocurridoAt, zona))),
      el(
        'span',
        { class: 'text-sm text-texto-tenue' },
        el('span', { class: 'inline-flex items-center px-2 rounded-full border border-borde-fuerte bg-superficie text-xs font-semibold text-texto' }, actor.etiqueta),
        actor.nombre === null ? null : ` ${actor.nombre}`,
        detalle === null ? null : ` ${detalle}`,
        ip === null ? null : el('span', { class: 'font-mono text-xs' }, ` IP ${ip}`),
      ),
      conContexto ? contextoDeFila(evento, zona) : null,
      listaDeCambios(cambiosDelEvento(evento, zona)),
    ),
  );
}

function listaPorDia(eventos: readonly EventoAuditoria[], zona: string, conContexto: boolean): HTMLElement[] {
  const grupos = new Map<string, EventoAuditoria[]>();
  for (const evento of eventos) {
    const dia = diaLocal(evento.ocurridoAt, zona);
    grupos.set(dia, [...(grupos.get(dia) ?? []), evento]);
  }
  return [...grupos].map(([dia, delDia]) => {
    const id = `registro-dia-${dia}`;
    return el(
      'section',
      { 'aria-labelledby': id, class: 'mb-5' },
      el('h2', { id, class: 'sticky top-pildora-x md:top-[calc(var(--touch-min)+var(--space-5))] z-10 m-0 mb-2 py-2 bg-fondo text-sm font-bold uppercase tracking-wide text-texto-tenue' }, tituloDelDia(dia, zona)),
      el('ul', { class: 'list-none m-0 p-0 grid gap-2' }, ...delDia.map((evento) => filaDeEvento(evento, zona, conContexto))),
    );
  });
}

function fechaLarga(dia: string, zona: string): string {
  return formatearDia(dia, zona).replace(/^\S+\s/, '');
}

function textoDeConteo(cantidad: number, filtros: Filtros, zona: string): string {
  const cambios = `${cantidad} ${cantidad === 1 ? 'cambio' : 'cambios'}`;
  if (filtros.turnoId !== null) return `${cambios} de este turno`;
  if (filtros.desde !== null && filtros.hasta !== null) return `${cambios} entre el ${fechaLarga(filtros.desde, zona)} y el ${fechaLarga(filtros.hasta, zona)}`;
  return `${cambios} con estos filtros`;
}

function selectorDeAccion(): HTMLSelectElement {
  return el(
    'select',
    { name: 'accion' },
    el('option', { value: '' }, 'Todos'),
    ...GRUPOS_DE_FILTRO.map((grupo) => {
      const nodo = el('optgroup', {}, ...grupo.opciones.map((o) => el('option', { value: o.clave }, o.etiqueta)));
      nodo.label = grupo.etiqueta;
      return nodo;
    }),
  );
}

export function montarRegistro({ contenedor, http, zonaHoraria: zona }: Pick<ContextoPagina, 'contenedor' | 'http' | 'zonaHoraria'>): void {
  let filtros = leerFiltros();
  let eventos: EventoAuditoria[] = [];
  let cursor: string | null = null;
  let pedido = 0;
  let textoChip: string | null = null;

  const desde = campoConfig({ etiqueta: 'Desde', control: el('input', { type: 'date', name: 'desde', class: CLASE_CONTROL }) });
  const hasta = campoConfig({ etiqueta: 'Hasta', control: el('input', { type: 'date', name: 'hasta', class: CLASE_CONTROL }) });
  const accion = campoConfig({ etiqueta: 'Tipo de cambio', control: selectorDeAccion() });
  for (const campo of [desde, hasta, accion]) campo.contenedor.classList.replace('mb-4', 'mb-0');
  accion.contenedor.classList.add('col-span-2', 'min-[900px]:col-span-1');

  const chips = el('ul', { 'aria-label': 'Filtros activos', class: 'flex flex-wrap gap-2 m-0 p-0 list-none' });
  const limpiar = boton({ texto: 'Quitar filtros', icono: 'cruz', variante: 'fantasma', alHacerClic: () => quitarFiltros() });
  limpiar.classList.add('col-span-2', 'justify-self-start', 'whitespace-nowrap', 'min-[900px]:col-span-1', 'min-[900px]:self-end');

  const formulario = el(
    'form',
    { role: 'search', 'aria-label': 'Filtrar el registro', class: 'grid gap-3 mb-4 p-4 bg-superficie border border-borde rounded-lg' },
    chips,
    el('div', { class: 'grid gap-3 items-start grid-cols-2 min-[900px]:grid-cols-[calc(var(--space-7)*3.75)_calc(var(--space-7)*3.75)_minmax(0,1fr)_auto]' }, desde.contenedor, hasta.contenedor, accion.contenedor, limpiar),
  );
  formulario.addEventListener('submit', (e) => e.preventDefault());

  const conteo = el('p', { class: 'mt-0 mb-3 text-sm text-texto-tenue', hidden: true });
  const zonaLista = el('div', {});
  contenedor.replaceChildren(
    el(
      'div',
      { class: 'max-w-[calc(var(--space-7)*18)] pb-6' },
      el('p', { class: 'mt-0 mb-4 text-sm text-texto-tenue' }, 'Todo lo que se cambió en tu consultorio, quién lo hizo y cuándo. Nadie puede editarlo ni borrarlo, ni siquiera vos.'),
      formulario,
      conteo,
      zonaLista,
    ),
  );

  function sincronizarControles(): void {
    desde.control.value = filtros.desde ?? '';
    hasta.control.value = filtros.hasta ?? '';
    accion.control.value = filtros.accion ?? '';
    limpiar.hidden = !hayFiltros(filtros);
    limpiar.classList.toggle('hidden', !hayFiltros(filtros));
    pintarChip();
  }

  function pintarChip(): void {
    chips.hidden = filtros.turnoId === null;
    chips.replaceChildren();
    if (filtros.turnoId === null) return;
    const quitar = el(
      'button',
      { type: 'button', 'aria-label': 'Quitar el filtro del turno', class: 'grid place-items-center w-touch h-touch -my-1 border-0 rounded-full bg-transparent text-inherit cursor-pointer hover:bg-superficie' },
      icono('cruz', 'w-4 h-4'),
    );
    quitar.addEventListener('click', () => {
      filtros = { ...filtros, turnoId: null };
      textoChip = null;
      aplicar();
    });
    chips.append(
      el(
        'li',
        { 'data-chip-turno': true, class: 'inline-flex items-center gap-2 min-h-touch pl-3 pr-1 rounded-full bg-primario-tinte border border-reservado-bd text-reservado-fg text-sm font-semibold' },
        textoChip ?? 'Este turno',
        quitar,
      ),
    );
  }

  function aplicar(): void {
    hasta.error(null);
    escribirFiltros(filtros);
    sincronizarControles();
    void cargar();
  }

  function quitarFiltros(): void {
    filtros = { turnoId: null, desde: null, hasta: null, accion: null };
    textoChip = null;
    aplicar();
  }

  function alCambiarFiltros(): void {
    const nuevoDesde = desde.control.value || null;
    const nuevoHasta = hasta.control.value || null;
    if (nuevoDesde !== null && nuevoHasta !== null && nuevoHasta < nuevoDesde) {
      hasta.error(MENSAJE_RANGO);
      limpiar.hidden = false;
      limpiar.classList.remove('hidden');
      return;
    }
    filtros = { ...filtros, desde: nuevoDesde, hasta: nuevoHasta, accion: accion.control.value || null };
    aplicar();
  }
  for (const control of [desde.control, hasta.control, accion.control]) control.addEventListener('change', alCambiarFiltros);

  function pintarLista(enfocar: string | null): void {
    const conContexto = filtros.turnoId === null;
    const hijos: HTMLElement[] = listaPorDia(eventos, zona, conContexto);
    if (cursor !== null) hijos.push(pieCargarMas());
    zonaLista.replaceChildren(...hijos);
    conteo.hidden = !hayFiltros(filtros) || cursor !== null;
    conteo.textContent = conteo.hidden ? '' : textoDeConteo(eventos.length, filtros, zona);
    if (enfocar !== null) [...zonaLista.querySelectorAll<HTMLElement>('[data-evento]')].find((fila) => fila.dataset['evento'] === enfocar)?.focus();
  }

  function pieCargarMas(mensaje = false): HTMLElement {
    const accionMas = boton({ texto: 'Cargar más', variante: 'secundario', alHacerClic: () => void cargarMas(accionMas) });
    return el(
      'div',
      { 'data-cargar-mas': true, class: 'grid justify-items-center gap-2 mt-4' },
      mensaje ? el('p', { role: 'alert', class: 'm-0 text-sm font-medium text-peligro' }, 'No pudimos cargar más cambios. Probá de nuevo.') : null,
      accionMas,
    );
  }

  function pintarEstadoVacio(): void {
    conteo.hidden = true;
    if (hayFiltros(filtros)) {
      zonaLista.replaceChildren(
        tarjetaEstado({
          icono: 'buscar',
          titulo: 'No hay cambios con estos filtros',
          texto: 'Probá con otras fechas o con otro tipo de cambio.',
          acciones: [boton({ texto: 'Quitar filtros', icono: 'cruz', variante: 'secundario', alHacerClic: () => quitarFiltros() })],
        }),
      );
      return;
    }
    formulario.hidden = true;
    zonaLista.replaceChildren(tarjetaEstado({ icono: 'historial', titulo: 'Todavía no hay cambios', texto: 'Cuando cargues tu consultorio, recibas reservas o muevas turnos, cada cambio va a quedar acá.' }));
  }

  function pintarError(error: unknown): void {
    conteo.hidden = true;
    if (error instanceof ApiError && error.codigo === 'SIN_CONEXION') {
      formulario.hidden = true;
      const aviso = avisoAuth({ tono: 'advertencia', icono: 'sin-conexion', titulo: 'No tenés conexión', cuerpo: ['El registro se consulta en línea. Volvé a intentar cuando tengas internet.'], rol: 'status' });
      zonaLista.replaceChildren(aviso, boton({ texto: 'Reintentar', icono: 'reintentar', variante: 'secundario', alHacerClic: () => void cargar() }));
      return;
    }
    zonaLista.replaceChildren(errorRecuperable({ titulo: 'No pudimos cargar el registro', texto: 'Puede ser un problema momentáneo.', alReintentar: () => void cargar() }));
  }

  const filtrosDeApi = () => ({ turnoId: filtros.turnoId, desde: filtros.desde, hasta: filtros.hasta, acciones: opcionDeFiltro(filtros.accion)?.acciones ?? [] });

  async function cargar(): Promise<void> {
    pedido += 1;
    const actual = pedido;
    conteo.hidden = true;
    formulario.hidden = false;
    zonaLista.replaceChildren(esqueleto({ etiqueta: 'Cargando el registro…', filas: 3 }));
    try {
      const pagina = await obtenerRegistro(http, filtrosDeApi(), null);
      if (actual !== pedido) return;
      eventos = pagina.eventos;
      cursor = pagina.siguienteCursor;
      if (filtros.turnoId !== null && textoChip === null) {
        textoChip = eventos.map((e) => textoDelTurno(e, zona)).find((t) => t !== null) ?? null;
        pintarChip();
      }
      if (eventos.length === 0) pintarEstadoVacio();
      else pintarLista(null);
    } catch (error) {
      if (actual !== pedido || (error instanceof ApiError && error.codigo === 'SESION_VENCIDA')) return;
      pintarError(error);
    }
  }

  async function cargarMas(accionMas: HTMLButtonElement): Promise<void> {
    if (cursor === null) return;
    pedido += 1;
    const actual = pedido;
    accionMas.disabled = true;
    try {
      const pagina = await obtenerRegistro(http, filtrosDeApi(), cursor);
      if (actual !== pedido) return;
      const conocidos = new Set(eventos.map((e) => e.id));
      const nuevos = pagina.eventos.filter((e) => !conocidos.has(e.id));
      eventos = [...eventos, ...nuevos];
      cursor = pagina.siguienteCursor;
      pintarLista(nuevos[0]?.id ?? null);
    } catch (error) {
      if (actual !== pedido || (error instanceof ApiError && error.codigo === 'SESION_VENCIDA')) return;
      // Un cursor que dejó de valer (p. ej. por rotar el secreto) vuelve a la primera página (design Risks).
      if (error instanceof ApiError && error.status === 400) {
        void cargar();
        return;
      }
      zonaLista.querySelector('[data-cargar-mas]')?.replaceWith(pieCargarMas(true));
    }
  }

  sincronizarControles();
  void cargar();
}
