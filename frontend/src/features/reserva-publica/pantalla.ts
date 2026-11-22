import { el, idUnico, type Hijo } from '../../shared/dom';
import { formatearHora, hoyEn } from '../../shared/fechas';
import type { ClienteHttp } from '../../shared/http';
import { aviso } from '../../shared/ui/aviso';
import { clasesBoton } from '../../shared/ui/boton';
import { errorRecuperable } from '../../shared/ui/error-recuperable';
import { esqueleto } from '../../shared/ui/esqueleto';
import { icono } from '../../shared/ui/iconos';
import { aConsultorio, aHuecos, aReservaCreada, type Consultorio, type Prestacion, type ReservaCreada } from './api';
import { etiquetaDia, proximoDiaConHorarios, quedanDias, sumarDias, ventanaDesde, type DiaDeHuecos } from './dias';
import { mensajeDeEspera, traducirErrorPublico, type EstadoDeError } from './errores';
import { cuandoCorto, cuandoLargo } from './horarios';
import { generarIcs } from './ics';
import { hrefTelefono, telefonoLegible } from './telefono';
import { ERRORES, validarDatos, type CampoDelFormulario, type DatosDelFormulario, type ErrorDeCampo } from './validaciones';
import {
  avisoDeZona,
  barraDeAccion,
  botonLlamar,
  botonPrincipal,
  listaDeHorarios,
  nota,
  ocultarCabecera,
  pintarCabecera,
  resultado,
  resumenDelTurno,
  tiraDeDias,
  tituloDePaso,
} from './vistas';

/**
 * Página `/c/{slug}` (C-16, spec pantalla-reserva-publica): un paciente sin cuenta elige prestación, día y horario,
 * deja sus datos y recibe una sola vez el link de gestión. Todo el avance vive en memoria (design D2): recargar empieza
 * de nuevo y no queda ningún dato personal ni el token en el navegador.
 */
export interface DependenciasReserva {
  readonly http: Pick<ClienteHttp, 'get' | 'post'>;
  readonly slug: string;
  readonly cabecera: HTMLElement;
  readonly ahora?: () => Date;
  readonly copiar?: (texto: string) => Promise<void>;
  readonly descargar?: (nombre: string, contenido: string) => void;
}

type Paso = 'cargando' | 'elegir' | 'datos' | 'confirmado' | 'no-toma' | 'no-encontrado' | 'error-carga';
type AvisoDeDatos = { readonly tipo: 'limite' } | { readonly tipo: 'demasiadas'; readonly segundos: number } | { readonly tipo: 'inesperado' } | null;

const MS_POR_MINUTO = 60_000;

function descargarArchivo(nombre: string, contenido: string): void {
  const url = URL.createObjectURL(new Blob([contenido], { type: 'text/calendar;charset=utf-8' }));
  const enlace = el('a', { href: url, download: nombre, hidden: true });
  document.body.append(enlace);
  enlace.click();
  enlace.remove();
  window.setTimeout(() => URL.revokeObjectURL(url), 0);
}

const copiarAlPortapapeles = (texto: string) => navigator.clipboard.writeText(texto);

export async function montarReserva(contenedor: HTMLElement, dependencias: DependenciasReserva): Promise<void> {
  const { http, cabecera } = dependencias;
  const ahora = dependencias.ahora ?? (() => new Date());
  const copiar = dependencias.copiar ?? copiarAlPortapapeles;
  const descargar = dependencias.descargar ?? descargarArchivo;
  const base = `/api/v1/public/consultorios/${encodeURIComponent(dependencias.slug)}`;

  // Ids estables durante toda la visita: el resumen de errores enlaza a ellos aunque se vuelva a pintar.
  const ids: Record<CampoDelFormulario, string> = {
    nombre: idUnico('reserva-nombre'),
    apellido: idUnico('reserva-apellido'),
    dni: idUnico('reserva-dni'),
    telefono: idUnico('reserva-telefono'),
    email: idUnico('reserva-email'),
    acepta: idUnico('reserva-acepta'),
  };

  let paso: Paso = 'cargando';
  let consultorio: Consultorio | null = null;
  let errorDeCarga: EstadoDeError | null = null;
  let telefonoNoToma: string | null = null;
  let zona = 'America/Argentina/Buenos_Aires';

  let prestacion: Prestacion | null = null;
  let dias: DiaDeHuecos[] = [];
  let cargandoDias = false;
  let errorDeDias: EstadoDeError | null = null;
  let generacionDias = 0;
  let diaElegido: string | null = null;
  let horario: string | null = null;
  let ocupados: string[] = [];
  let horaOcupada: string | null = null;

  let formulario: DatosDelFormulario = { nombre: '', apellido: '', dni: '', telefono: '', email: '', acepta: false };
  let errores: readonly ErrorDeCampo[] = [];
  let avisoDeDatos: AvisoDeDatos = null;
  let enviando = false;
  let reserva: ReservaCreada | null = null;

  const telefonoDelConsultorio = () => telefonoNoToma ?? consultorio?.telefono ?? null;

  // ───────────── Carga ─────────────

  async function cargarConsultorio(): Promise<void> {
    paso = 'cargando';
    pintar();
    try {
      const datos = aConsultorio(await http.get<unknown>(base));
      if (datos === null) throw new Error('La página pública no cumple el contrato');
      consultorio = datos;
      zona = datos.zonaHoraria;
      paso = datos.tomaTurnosOnline ? 'elegir' : 'no-toma';
    } catch (error) {
      errorDeCarga = traducirErrorPublico(error);
      paso = errorDeCarga.tipo === 'no-encontrado' ? 'no-encontrado' : 'error-carga';
    }
    pintar();
  }

  async function cargarDias(desde: string): Promise<void> {
    if (consultorio === null || prestacion === null) return;
    const ventana = ventanaDesde(desde, consultorio.reservableHasta);
    if (ventana === null) return;
    const generacion = ++generacionDias;
    cargandoDias = true;
    errorDeDias = null;
    pintar();
    try {
      const consulta = new URLSearchParams({ prestacionId: prestacion.id, desde: ventana.desde, hasta: ventana.hasta });
      const respuesta = aHuecos(await http.get<unknown>(`${base}/huecos?${consulta.toString()}`));
      if (respuesta === null) throw new Error('Los huecos no cumplen el contrato');
      if (generacion !== generacionDias) return;
      zona = respuesta.zonaHoraria;
      dias = [...dias, ...respuesta.dias.filter((d) => !dias.some((x) => x.fecha === d.fecha))];
    } catch (error) {
      if (generacion !== generacionDias) return;
      const estado = traducirErrorPublico(error);
      if (estado.tipo === 'reserva-no-disponible') {
        telefonoNoToma = estado.telefono;
        paso = 'no-toma';
      } else {
        errorDeDias = estado;
      }
    }
    cargandoDias = false;
    pintar();
  }

  function siguienteDesde(): string {
    const ultimo = dias.at(-1);
    return ultimo === undefined ? hoyEn(zona, ahora()) : sumarDias(ultimo.fecha, 1);
  }

  // ───────────── Acciones ─────────────

  function elegirPrestacion(p: Prestacion): void {
    if (prestacion?.id === p.id) return;
    prestacion = p;
    dias = [];
    diaElegido = null;
    horario = null;
    ocupados = [];
    horaOcupada = null;
    void cargarDias(hoyEn(zona, ahora()));
  }

  function elegirDia(fecha: string): void {
    if (diaElegido !== fecha) {
      horario = null;
      ocupados = [];
      horaOcupada = null;
    }
    diaElegido = fecha;
    pintar();
  }

  function elegirHorario(instante: string): void {
    horario = instante;
    pintar();
  }

  function irADatos(): void {
    if (horario === null) return;
    paso = 'datos';
    errores = [];
    pintar();
    enfocar('[data-titulo]');
  }

  function volverAElegir(): void {
    paso = 'elegir';
    pintar();
    enfocar('[data-titulo-paso]');
  }

  async function reservar(): Promise<void> {
    if (enviando || prestacion === null || horario === null) return;
    const validacion = validarDatos(formulario);
    if (!validacion.ok) {
      errores = validacion.errores;
      avisoDeDatos = null;
      pintar();
      enfocar('[data-resumen-errores]');
      return;
    }
    errores = [];
    avisoDeDatos = null;
    enviando = true;
    pintar();
    try {
      const creada = aReservaCreada(await http.post<unknown>(`${base}/turnos`, { prestacionId: prestacion.id, inicio: horario, ...validacion.datos }));
      if (creada === null) throw new Error('La reserva no cumple el contrato');
      reserva = creada;
      enviando = false;
      paso = 'confirmado';
      pintar();
      enfocar('[data-titulo]');
    } catch (error) {
      enviando = false;
      manejarErrorDeReserva(traducirErrorPublico(error));
    }
  }

  function manejarErrorDeReserva(estado: EstadoDeError): void {
    switch (estado.tipo) {
      case 'hueco-ocupado': {
        const elegido = horario;
        if (elegido !== null) {
          ocupados = [...new Set([...ocupados, elegido])];
          horaOcupada = formatearHora(elegido, zona);
        }
        dias = dias.map((d) => (d.fecha === diaElegido ? { ...d, huecos: estado.huecos.filter((h) => !ocupados.includes(h)) } : d));
        horario = null;
        paso = 'elegir';
        pintar();
        enfocar('[data-aviso-ocupado]');
        return;
      }
      case 'limite-turnos':
        avisoDeDatos = { tipo: 'limite' };
        break;
      case 'reserva-no-disponible':
        telefonoNoToma = estado.telefono;
        paso = 'no-toma';
        pintar();
        enfocar('[data-titulo]');
        return;
      case 'campos':
        errores = (estado.campos.length === 0 ? (['dni'] as const) : estado.campos).map((campo) => ({ campo, ...ERRORES[campo] }));
        pintar();
        enfocar('[data-resumen-errores]');
        return;
      case 'demasiadas':
        avisoDeDatos = { tipo: 'demasiadas', segundos: estado.segundos };
        break;
      case 'no-encontrado':
        paso = 'no-encontrado';
        pintar();
        return;
      default:
        avisoDeDatos = { tipo: 'inesperado' };
    }
    pintar();
    enfocar('[data-aviso-datos]');
  }

  // ───────────── Vistas ─────────────

  function enfocar(selector: string): void {
    contenedor.querySelector<HTMLElement>(selector)?.focus();
  }

  function pintar(): void {
    if (consultorio !== null && paso !== 'no-encontrado') pintarCabecera(cabecera, consultorio, paso === 'elegir' || paso === 'no-toma');
    else ocultarCabecera(cabecera);
    contenedor.replaceChildren(vistaDelPaso());
  }

  function vistaDelPaso(): HTMLElement {
    switch (paso) {
      case 'cargando':
        return esqueleto({ etiqueta: 'Cargando los turnos del consultorio…', filas: 3 });
      case 'no-encontrado':
        return resultado({
          icono: 'buscar',
          tono: 'neutro',
          titulo: 'No encontramos este consultorio',
          texto: 'Revisá que el link esté completo, tal como te lo pasaron. Si sigue sin funcionar, pedile al consultorio el link de reservas.',
        });
      case 'error-carga':
        return vistaErrorDeCarga();
      case 'no-toma':
        return vistaNoToma();
      case 'elegir':
        return vistaElegir();
      case 'datos':
        return vistaDatos();
      case 'confirmado':
        return vistaConfirmado();
    }
  }

  function notaDeEspera(segundos: number, alReintentar: () => void): HTMLElement {
    const reintentar = el('button', { type: 'button', class: `${clasesBoton('secundario')} mt-3` }, 'Reintentar');
    reintentar.addEventListener('click', alReintentar);
    return nota('advertencia', 'Hiciste muchas consultas seguidas.', el('div', {}, el('p', { class: 'm-0' }, mensajeDeEspera(segundos)), reintentar), { role: 'alert' });
  }

  function vistaErrorDeCarga(): HTMLElement {
    if (errorDeCarga?.tipo === 'demasiadas') return notaDeEspera(errorDeCarga.segundos, () => void cargarConsultorio());
    return errorRecuperable({
      titulo: errorDeCarga?.tipo === 'sin-conexion' ? 'No hay conexión.' : 'No pudimos cargar los turnos del consultorio.',
      texto: errorDeCarga?.tipo === 'sin-conexion' ? 'Revisá tu conexión a internet y probá de nuevo.' : 'Puede ser un problema momentáneo. Probá de nuevo en unos segundos.',
      alReintentar: () => void cargarConsultorio(),
    });
  }

  function vistaNoToma(): HTMLElement {
    const telefono = telefonoDelConsultorio();
    return resultado({
      icono: 'pausa',
      tono: 'neutro',
      nivel: 'h2',
      titulo: 'Por ahora este consultorio no toma turnos online',
      texto: 'Para pedir un turno, llamá al consultorio. Si ya tenés un turno reservado, podés cancelarlo desde el link que guardaste.',
      hijos: [telefono === null ? null : botonLlamar(telefono)],
    });
  }

  function botonDePrestacion(p: Prestacion): HTMLButtonElement {
    const elegida = prestacion?.id === p.id;
    const b = el(
      'button',
      {
        type: 'button',
        'aria-pressed': String(elegida),
        class:
          'group flex justify-between items-center gap-3 w-full min-h-[calc(var(--touch-min)+var(--space-3))] px-4 py-3 rounded-md border-control border-borde-fuerte bg-superficie text-left text-base text-texto cursor-pointer ' +
          'aria-pressed:border-primario aria-pressed:bg-primario-tinte aria-pressed:shadow-[inset_0_0_0_1px_var(--color-primary)]',
      },
      el('span', {}, p.nombre),
      el(
        'span',
        { class: 'inline-flex items-center gap-1 text-sm text-texto-tenue whitespace-nowrap' },
        icono('reloj', 'w-icono-sm h-icono-sm flex-none'),
        `${p.duracionMin} min`,
        el(
          'span',
          {
            'aria-hidden': 'true',
            class: 'grid place-items-center w-5 h-5 ml-1 rounded-full border-control border-borde-fuerte text-sobre-primario group-aria-pressed:bg-primario group-aria-pressed:border-primario',
          },
          icono('check', 'w-icono-sm h-icono-sm invisible group-aria-pressed:visible'),
        ),
      ),
    );
    b.addEventListener('click', () => elegirPrestacion(p));
    return b;
  }

  function vistaDeHorarios(dia: DiaDeHuecos | undefined): Hijo[] {
    if (dia === undefined || prestacion === null) return [el('p', { class: 'm-0 text-sm text-texto-tenue' }, 'Elegí un día para ver los horarios.')];
    const conHuecos = dia.huecos.length > 0;
    const lista = conHuecos || ocupados.length > 0 ? listaDeHorarios({ huecos: dia.huecos, ocupados, zona, elegido: horario, alElegir: elegirHorario }) : null;
    if (conHuecos) return [lista];
    const proximo = proximoDiaConHorarios(dias, dia.fecha);
    const irAlProximo = proximo === null ? null : el('button', { type: 'button', class: clasesBoton('secundario') }, `Ver el ${etiquetaDia(proximo.fecha).largo}`);
    irAlProximo?.addEventListener('click', () => {
      if (proximo !== null) elegirDia(proximo.fecha);
    });
    return [
      lista,
      el(
        'div',
        { class: `${lista === null ? '' : 'mt-3 '}p-4 rounded-md border border-dashed border-borde-fuerte text-center text-texto-tenue` },
        el('p', { class: 'mt-0 mb-3' }, 'No quedan horarios para ', el('b', {}, prestacion.nombre), ` el ${etiquetaDia(dia.fecha).largo}.`),
        irAlProximo ?? el('p', { class: 'm-0' }, 'Probá con otro día.'),
      ),
    ];
  }

  function vistaDeDias(): Hijo[] {
    if (errorDeDias?.tipo === 'demasiadas') return [notaDeEspera(errorDeDias.segundos, () => void cargarDias(siguienteDesde()))];
    if (errorDeDias !== null && dias.length === 0) {
      return [errorRecuperable({ titulo: 'No pudimos cargar los días.', alReintentar: () => void cargarDias(siguienteDesde()) })];
    }
    if (dias.length === 0 && cargandoDias) return [el('p', { role: 'status', class: 'm-0 text-sm text-texto-tenue' }, 'Buscando días con turnos…')];
    const ultimo = dias.at(-1);
    return [
      tiraDeDias({
        dias,
        elegido: diaElegido,
        alElegir: elegirDia,
        quedanMas: consultorio !== null && ultimo !== undefined && quedanDias(ultimo.fecha, consultorio.reservableHasta),
        cargando: cargandoDias,
        alVerMas: () => void cargarDias(siguienteDesde()),
      }),
      errorDeDias === null ? null : nota('peligro', 'No pudimos cargar más días.', 'Probá de nuevo en unos segundos.'),
    ];
  }

  function vistaElegir(): HTMLElement {
    const c = consultorio as Consultorio;
    const dia = dias.find((d) => d.fecha === diaElegido);
    const accion =
      prestacion === null
        ? botonPrincipal('Elegí qué necesitás', { deshabilitado: true })
        : horario === null
          ? botonPrincipal('Elegí un horario', { deshabilitado: true })
          : botonPrincipal(`Continuar con ${cuandoCorto(horario, zona)}`, { alHacerClic: irADatos });
    return el(
      'section',
      { 'aria-label': 'Elegí tu turno', class: 'con-barra' },
      horaOcupada === null
        ? null
        : nota('peligro', `Alguien reservó las ${horaOcupada} hace un instante.`, 'Actualizamos los horarios: elegí otro.', {
            atributos: { 'data-aviso-ocupado': true, tabindex: '-1' },
          }),
      el('h2', { class: 'mt-0 mb-3 text-lg font-bold text-texto', tabindex: '-1', 'data-titulo-paso': true }, '1. ¿Qué necesitás?'),
      el('div', { role: 'group', 'aria-label': 'Prestaciones', class: 'grid gap-2' }, ...c.prestaciones.map(botonDePrestacion)),
      prestacion === null
        ? null
        : el(
            'div',
            {},
            tituloDePaso('2. Elegí el día'),
            ...vistaDeDias(),
            tituloDePaso('3. Elegí el horario'),
            ...vistaDeHorarios(dia),
            avisoDeZona(zona),
          ),
      barraDeAccion(accion),
    );
  }

  function resumenDeErrores(): HTMLElement | null {
    if (errores.length === 0) return null;
    const titulo = errores.length === 1 ? 'Revisá 1 dato para reservar:' : `Revisá ${errores.length} datos para reservar:`;
    return nota(
      'peligro',
      titulo,
      el(
        'ul',
        { class: 'mt-1 mb-0 pl-5' },
        ...errores.map((e) => {
          const enlace = el('a', { href: `#${ids[e.campo]}`, class: 'font-semibold text-inherit' }, e.resumen);
          enlace.addEventListener('click', (evento) => {
            evento.preventDefault();
            document.getElementById(ids[e.campo])?.focus();
          });
          return el('li', {}, enlace);
        }),
      ),
      { atributos: { 'data-resumen-errores': true, tabindex: '-1' } },
    );
  }

  function avisoDelPasoDeDatos(): HTMLElement | null {
    if (avisoDeDatos === null) return null;
    const atributos = { 'data-aviso-datos': true, tabindex: '-1' };
    if (avisoDeDatos.tipo === 'limite') {
      const telefono = telefonoDelConsultorio();
      return nota(
        'advertencia',
        'Ya tenés 2 turnos próximos en este consultorio.',
        el(
          'p',
          { class: 'm-0' },
          'Para sacar otro, cancelá uno desde el link que guardaste',
          telefono === null ? '.' : el('span', {}, ' o llamá al ', el('a', { href: hrefTelefono(telefono), class: 'font-semibold text-inherit' }, telefonoLegible(telefono)), '.'),
        ),
        { role: 'alert', icono: 'alerta', atributos },
      );
    }
    if (avisoDeDatos.tipo === 'demasiadas') {
      return nota('advertencia', 'Hiciste muchas consultas seguidas.', el('p', { class: 'm-0' }, mensajeDeEspera(avisoDeDatos.segundos)), { role: 'alert', atributos });
    }
    return nota('peligro', 'No pudimos reservar el turno.', el('p', { class: 'm-0' }, 'Revisá tu conexión y probá de nuevo. Tus datos siguen acá.'), { atributos });
  }

  function errorDe(campo: CampoDelFormulario): ErrorDeCampo | undefined {
    return errores.find((e) => e.campo === campo);
  }

  function mensajeDeError(id: string, mensaje: string): HTMLElement {
    return el('p', { id, class: 'm-0 flex gap-1 items-start text-sm font-medium text-peligro' }, icono('alerta', 'w-icono-sm h-icono-sm flex-none mt-1'), el('span', {}, mensaje));
  }

  interface OpcionesDeCampo {
    readonly campo: Exclude<CampoDelFormulario, 'acepta'>;
    readonly etiqueta: Hijo[];
    readonly tipo?: 'text' | 'tel' | 'email';
    readonly autocompletar: string;
    readonly modo?: 'numeric' | 'tel' | 'email';
    readonly ayuda?: Hijo;
    readonly maximo?: number;
  }

  function campoDeTexto(opciones: OpcionesDeCampo): HTMLElement {
    const id = ids[opciones.campo];
    const error = errorDe(opciones.campo);
    const idAyuda = opciones.ayuda === undefined || error !== undefined ? null : `${id}-ayuda`;
    const idError = error === undefined ? null : `${id}-error`;
    const describe = [idAyuda, idError].filter((v) => v !== null).join(' ');
    const input = el('input', {
      id,
      name: opciones.campo,
      type: opciones.tipo ?? 'text',
      value: formulario[opciones.campo],
      autocomplete: opciones.autocompletar,
      inputmode: opciones.modo,
      maxlength: opciones.maximo,
      autocapitalize: opciones.tipo === 'email' ? 'none' : null,
      spellcheck: opciones.tipo === 'email' ? 'false' : null,
      'aria-describedby': describe === '' ? null : describe,
      'aria-invalid': error === undefined ? null : 'true',
      class:
        'min-h-touch w-full px-3 font-sans text-base text-texto bg-superficie border-control border-borde-fuerte rounded-md ' +
        'focus-visible:border-primario aria-[invalid=true]:border-peligro aria-[invalid=true]:bg-peligro-fondo',
    });
    input.addEventListener('input', () => {
      formulario = { ...formulario, [opciones.campo]: input.value };
      reactivarEnvio();
    });
    return el(
      'div',
      { class: 'grid gap-1 mb-4' },
      el('label', { for: id, class: 'font-medium text-sm' }, ...opciones.etiqueta),
      input,
      idAyuda === null ? null : el('p', { id: idAyuda, class: 'm-0 text-sm text-texto-tenue' }, opciones.ayuda),
      idError === null || error === undefined ? null : mensajeDeError(idError, error.mensaje),
    );
  }

  let botonReservar: HTMLButtonElement | null = null;
  /** Después de LIMITE_TURNOS, el envío vuelve a estar disponible en cuanto el paciente cambia algún dato. */
  function reactivarEnvio(): void {
    if (avisoDeDatos?.tipo === 'limite' && botonReservar !== null && !enviando) botonReservar.disabled = false;
  }

  function casillaDeConsentimiento(c: Consultorio): HTMLElement[] {
    const id = ids.acepta;
    const error = errorDe('acepta');
    const casilla = el('input', {
      type: 'checkbox',
      id,
      name: 'acepta',
      checked: formulario.acepta,
      'aria-invalid': error === undefined ? null : 'true',
      'aria-describedby': error === undefined ? null : `${id}-error`,
      class: 'flex-none w-5 h-5 my-3 accent-[var(--color-primary)]',
    });
    casilla.addEventListener('change', () => {
      formulario = { ...formulario, acepta: casilla.checked };
      reactivarEnvio();
    });
    // Texto de docs/legal/politica-de-privacidad.md §Anexo (versión 2026-11-borrador), sin marcar por defecto.
    return [
      el(
        'div',
        { class: `flex gap-3 items-start mb-4 p-3 rounded-md border ${error === undefined ? 'bg-superficie border-borde' : 'bg-peligro-fondo border-peligro'}` },
        casilla,
        el(
          'label',
          { for: id, class: 'pt-2 text-sm' },
          'Acepto que ',
          el('b', {}, c.nombre),
          ' use mis datos (nombre, apellido, DNI, teléfono y, si lo dejo, mi email) para gestionar este turno, según la ',
          el('a', { href: '/privacidad', target: '_blank', rel: 'noopener', class: 'text-primario underline' }, 'política de privacidad'),
          '.',
        ),
      ),
      ...(error === undefined ? [] : [el('div', { class: '-mt-2 mb-4' }, mensajeDeError(`${id}-error`, error.mensaje))]),
    ];
  }

  function vistaDatos(): HTMLElement {
    const c = consultorio as Consultorio;
    const p = prestacion as Prestacion;
    const inicio = horario as string;
    const fin = new Date(Date.parse(inicio) + p.duracionMin * MS_POR_MINUTO).toISOString();
    const telefono = telefonoDelConsultorio();

    const volver = el('button', { type: 'button', class: `${clasesBoton('fantasma')} -ml-4 mb-3` }, icono('izquierda', 'w-icono-sm h-icono-sm flex-none'), 'Cambiar día u horario');
    volver.addEventListener('click', volverAElegir);

    botonReservar = enviando
      ? botonPrincipal('Reservando…', { deshabilitado: true, tipo: 'submit' })
      : botonPrincipal('Reservar turno', { tipo: 'submit', deshabilitado: avisoDeDatos?.tipo === 'limite' });
    if (enviando) {
      botonReservar.setAttribute('aria-live', 'polite');
      botonReservar.prepend(el('span', { 'aria-hidden': 'true', class: 'inline-block w-icono-sm h-icono-sm rounded-full border-2 border-current border-r-transparent motion-safe:animate-spin' }));
    }

    const formularioDom = el(
      'form',
      { novalidate: true, class: 'm-0' },
      el(
        'div',
        { class: 'grid sm:grid-cols-2 sm:gap-x-3' },
        campoDeTexto({ campo: 'nombre', etiqueta: ['Nombre'], autocompletar: 'given-name', maximo: 80 }),
        campoDeTexto({ campo: 'apellido', etiqueta: ['Apellido'], autocompletar: 'family-name', maximo: 80 }),
      ),
      campoDeTexto({
        campo: 'dni',
        etiqueta: ['DNI'],
        autocompletar: 'off',
        modo: 'numeric',
        ayuda: el(
          'span',
          {},
          'Con o sin puntos. ',
          telefono === null ? null : el('span', {}, '¿No tenés DNI argentino? Pedí tu turno llamando al ', el('a', { href: hrefTelefono(telefono), class: 'text-primario' }, telefonoLegible(telefono)), '.'),
        ),
      }),
      campoDeTexto({ campo: 'telefono', etiqueta: ['Celular'], tipo: 'tel', modo: 'tel', autocompletar: 'tel', ayuda: 'El consultorio te va a llamar a este número si necesita avisarte algo.' }),
      campoDeTexto({
        campo: 'email',
        etiqueta: ['Email ', el('span', { class: 'text-texto-tenue font-normal' }, '(opcional)')],
        tipo: 'email',
        modo: 'email',
        autocompletar: 'email',
        ayuda: 'Si lo dejás, te mandamos la confirmación y los cambios de tu turno.',
      }),
      ...casillaDeConsentimiento(c),
      botonReservar,
      el('p', { class: 'mt-3 mb-0 text-center text-sm text-texto-tenue' }, 'No necesitás crear una cuenta.'),
    );
    formularioDom.addEventListener('submit', (evento) => {
      evento.preventDefault();
      void reservar();
    });

    return el(
      'section',
      { 'aria-labelledby': 'titulo-datos' },
      volver,
      el('h1', { id: 'titulo-datos', tabindex: '-1', 'data-titulo': true, class: 'mt-0 mb-2 text-2xl font-bold text-texto focus:outline-none' }, 'Completá tus datos'),
      el('div', { class: 'mb-5' }, resumenDelTurno(cuandoLargo(inicio, zona), `${p.nombre} · ${p.duracionMin} min · hasta las ${formatearHora(fin, zona)}`)),
      resumenDeErrores(),
      avisoDelPasoDeDatos(),
      formularioDom,
    );
  }

  function vistaConfirmado(): HTMLElement {
    const c = consultorio as Consultorio;
    const p = prestacion as Prestacion;
    const creada = reserva as ReservaCreada;
    const link = new URL(creada.linkGestion, location.origin).href;
    const url = el('div', { class: 'font-mono text-sm font-semibold text-primario bg-primario-tinte rounded-sm px-3 py-2 break-all', 'data-link-gestion': true }, link);

    const copiarLink = el('button', { type: 'button', class: clasesBoton('primario') }, icono('copiar', 'w-icono-sm h-icono-sm flex-none'), 'Copiar link');
    copiarLink.addEventListener('click', () => {
      copiar(link).then(
        () => aviso('Copiamos el link.', { tono: 'exito' }),
        () => {
          const rango = document.createRange();
          rango.selectNodeContents(url);
          window.getSelection()?.removeAllRanges();
          window.getSelection()?.addRange(rango);
          aviso('No pudimos copiarlo: mantené apretado el link para copiarlo a mano.', { tono: 'error' });
        },
      );
    });

    const calendario = el('button', { type: 'button', class: clasesBoton('secundario') }, icono('calendario-mas', 'w-icono-sm h-icono-sm flex-none'), 'Agregar al calendario');
    calendario.addEventListener('click', () => {
      const contenido = generarIcs({
        uid: creada.turno.id,
        inicio: creada.turno.inicio,
        fin: creada.turno.fin,
        titulo: `${p.nombre} · ${c.nombre}`,
        ubicacion: c.direccion === null ? c.nombre : `${c.nombre}, ${c.direccion}`,
        descripcion: `Para ver, cambiar o cancelar tu turno: ${link}`,
      });
      descargar('turno.ics', contenido);
    });

    return resultado({
      icono: 'check-circulo',
      tono: 'exito',
      titulo: '¡Listo, reservaste tu turno!',
      texto: 'El consultorio lo va a confirmar. Si dejaste tu email, también te llega la confirmación por ahí.',
      hijos: [
        el(
          'div',
          { class: 'mb-4' },
          resumenDelTurno(cuandoLargo(creada.turno.inicio, zona), `${p.nombre} · ${p.duracionMin} min · ${c.nombre}`, [
            c.direccion === null
              ? null
              : el('div', { class: 'flex gap-3 items-start text-sm text-texto-tenue' }, icono('ubicacion', 'w-icono h-icono flex-none text-primario'), c.direccion),
          ]),
        ),
        // RN-RP-06: el link se muestra una sola vez y no se guarda en el navegador (design D7).
        el(
          'div',
          { role: 'region', 'aria-labelledby': 'titulo-link', class: 'grid gap-3 mb-4 p-4 rounded-lg border-2 border-primario bg-superficie text-left' },
          el('h2', { id: 'titulo-link', class: 'm-0 flex items-center gap-2 text-lg font-bold text-texto' }, icono('marcador', 'w-icono h-icono flex-none text-primario'), 'Guardá este link'),
          el('p', { class: 'm-0 text-sm text-texto-tenue' }, 'Con él podés ver, cambiar o cancelar tu turno. ', el('b', {}, 'No lo vamos a volver a mostrar.')),
          url,
          el('div', { class: 'grid gap-2 sm:grid-cols-2' }, copiarLink, calendario),
          el('p', { class: 'm-0 text-sm text-texto-tenue' }, '"Agregar al calendario" descarga un archivo que incluye el link, así lo tenés junto al turno.'),
        ),
      ],
    });
  }

  await cargarConsultorio();
}
