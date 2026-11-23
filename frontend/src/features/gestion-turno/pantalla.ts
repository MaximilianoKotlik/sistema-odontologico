import { el, type Hijo } from '../../shared/dom';
import { formatearHora, hoyEn } from '../../shared/fechas';
import type { ClienteHttp } from '../../shared/http';
import { clasesBoton } from '../../shared/ui/boton';
import { chipEstado, type EstadoChip } from '../../shared/ui/chip-estado';
import { dialogo } from '../../shared/ui/dialogo';
import { errorRecuperable } from '../../shared/ui/error-recuperable';
import { esqueleto } from '../../shared/ui/esqueleto';
import { icono } from '../../shared/ui/iconos';
import { aHuecos } from '../reserva-publica/api';
import { quedanDias, sumarDias, ventanaDesde, type DiaDeHuecos } from '../reserva-publica/dias';
import { mensajeDeEspera, traducirErrorPublico, type EstadoDeError } from '../reserva-publica/errores';
import { cuandoCorto, cuandoLargo } from '../reserva-publica/horarios';
import { generarIcs } from '../reserva-publica/ics';
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
} from '../reserva-publica/vistas';
import { aVistaDeTurno, type VistaDeTurno } from './api';

/**
 * Página `/t/{token}` (C-16, spec pantalla-gestion-turno): el paciente ve su turno y lo reprograma o cancela dentro
 * del plazo. El token vive solo en la URL y en memoria; cualquier 404 muestra el mismo "Este link no funciona".
 */
export interface DependenciasGestion {
  readonly http: Pick<ClienteHttp, 'get' | 'post'>;
  readonly token: string;
  readonly cabecera: HTMLElement;
  readonly ahora?: () => Date;
  readonly descargar?: (nombre: string, contenido: string) => void;
}

type Paso = 'cargando' | 'ver' | 'reprogramar' | 'reprogramado' | 'cancelado' | 'invalido' | 'error-carga';
type AvisoDeAccion =
  | { readonly tipo: 'fuera-de-plazo'; readonly telefono: string | null }
  | { readonly tipo: 'solo-cancelar'; readonly telefono: string | null }
  | { readonly tipo: 'conflicto' }
  | { readonly tipo: 'demasiadas'; readonly segundos: number }
  | { readonly tipo: 'inesperado' }
  | null;

/**
 * La vista por token no trae el horizonte de reserva (`VistaDeTurno` de C-15): se ofrecen más días hasta el máximo que
 * acepta la configuración (C-08, 365 días). Los días después del horizonte se ven "sin turnos".
 */
const HORIZONTE_MAXIMO_DIAS = 365;
const MS_POR_MINUTO = 60_000;
const MS_POR_HORA = 3_600_000;

const ESTADOS: Readonly<Record<string, EstadoChip>> = { RESERVADO: 'reservado', CONFIRMADO: 'confirmado', CANCELADO: 'cancelado' };
const EXPLICACIONES: Readonly<Record<string, string>> = {
  RESERVADO: '"Reservado" quiere decir que el consultorio todavía no lo confirmó. No tenés que hacer nada.',
  CONFIRMADO: '"Confirmado" quiere decir que el consultorio ya lo confirmó. Te esperamos.',
};

function descargarArchivo(nombre: string, contenido: string): void {
  const url = URL.createObjectURL(new Blob([contenido], { type: 'text/calendar;charset=utf-8' }));
  const enlace = el('a', { href: url, download: nombre, hidden: true });
  document.body.append(enlace);
  enlace.click();
  enlace.remove();
  window.setTimeout(() => URL.revokeObjectURL(url), 0);
}

/** UID del evento sin el token: un resumen SHA-256, estable para el mismo link. */
async function uidDelTurno(token: string): Promise<string> {
  const resumen = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(token));
  return [...new Uint8Array(resumen)].slice(0, 16).map((b) => b.toString(16).padStart(2, '0')).join('');
}

const minutosEntre = (inicio: string, fin: string) => Math.round((Date.parse(fin) - Date.parse(inicio)) / MS_POR_MINUTO);

export async function montarGestion(contenedor: HTMLElement, dependencias: DependenciasGestion): Promise<void> {
  const { http, cabecera } = dependencias;
  const ahora = dependencias.ahora ?? (() => new Date());
  const descargar = dependencias.descargar ?? descargarArchivo;
  const base = `/api/v1/public/turnos/${encodeURIComponent(dependencias.token)}`;
  const link = new URL(`/t/${encodeURIComponent(dependencias.token)}`, location.origin).href;

  let paso: Paso = 'cargando';
  let datos: VistaDeTurno | null = null;
  let anterior: VistaDeTurno | null = null;
  let errorDeCarga: EstadoDeError | null = null;
  let avisoDeAccion: AvisoDeAccion = null;
  let ocupando = false;

  let dias: DiaDeHuecos[] = [];
  let cargandoDias = false;
  let errorDeDias: EstadoDeError | null = null;
  let diaElegido: string | null = null;
  let horario: string | null = null;
  let ocupados: string[] = [];
  let avisoOcupado = false;

  const zona = () => datos?.consultorio.zonaHoraria ?? 'America/Argentina/Buenos_Aires';
  const horizonte = () => sumarDias(hoyEn(zona(), ahora()), HORIZONTE_MAXIMO_DIAS - 1);
  const telefono = () => (avisoDeAccion !== null && 'telefono' in avisoDeAccion ? avisoDeAccion.telefono : null) ?? datos?.consultorio.telefono ?? null;

  // ───────────── Carga y acciones ─────────────

  async function cargar(aviso: AvisoDeAccion = null): Promise<void> {
    try {
      const vista = aVistaDeTurno(await http.get<unknown>(base));
      if (vista === null) throw new Error('La vista del turno no cumple el contrato');
      datos = vista;
      avisoDeAccion = aviso;
      paso = 'ver';
    } catch (error) {
      errorDeCarga = traducirErrorPublico(error);
      paso = errorDeCarga.tipo === 'no-encontrado' ? 'invalido' : 'error-carga';
    }
    pintar();
  }

  async function cargarDias(desde: string): Promise<void> {
    const ventana = ventanaDesde(desde, horizonte());
    if (ventana === null) return;
    cargandoDias = true;
    errorDeDias = null;
    pintar();
    try {
      const respuesta = aHuecos(await http.get<unknown>(`${base}/huecos?${new URLSearchParams({ desde: ventana.desde, hasta: ventana.hasta }).toString()}`));
      if (respuesta === null) throw new Error('Los huecos no cumplen el contrato');
      dias = [...dias, ...respuesta.dias.filter((d) => !dias.some((x) => x.fecha === d.fecha))];
    } catch (error) {
      const estado = traducirErrorPublico(error);
      if (!manejarErrorComun(estado)) errorDeDias = estado;
    }
    cargandoDias = false;
    pintar();
  }

  /** Errores que cambian de pantalla en cualquier acción. Devuelve true si lo manejó. */
  function manejarErrorComun(estado: EstadoDeError): boolean {
    switch (estado.tipo) {
      case 'no-encontrado':
        paso = 'invalido';
        return true;
      case 'fuera-de-plazo':
        avisoDeAccion = { tipo: 'fuera-de-plazo', telefono: estado.telefono };
        paso = 'ver';
        return true;
      case 'reserva-no-disponible':
        avisoDeAccion = { tipo: 'solo-cancelar', telefono: estado.telefono };
        paso = 'ver';
        return true;
      case 'conflicto':
        void cargar({ tipo: 'conflicto' });
        return true;
      default:
        return false;
    }
  }

  function empezarReprogramacion(): void {
    paso = 'reprogramar';
    avisoDeAccion = null;
    dias = [];
    diaElegido = null;
    horario = null;
    ocupados = [];
    avisoOcupado = false;
    pintar();
    enfocar('[data-titulo]');
    void cargarDias(hoyEn(zona(), ahora()));
  }

  async function reprogramar(nuevoInicio: string): Promise<void> {
    if (ocupando) return;
    ocupando = true;
    try {
      const vista = aVistaDeTurno(await http.post<unknown>(`${base}/reprogramacion`, { nuevoInicio }));
      if (vista === null) throw new Error('La vista del turno no cumple el contrato');
      anterior = datos;
      datos = vista;
      paso = 'reprogramado';
      pintar();
      enfocar('[data-titulo]');
    } catch (error) {
      const estado = traducirErrorPublico(error);
      if (estado.tipo === 'hueco-ocupado') {
        ocupados = [...new Set([...ocupados, nuevoInicio])];
        dias = dias.map((d) => (d.fecha === diaElegido ? { ...d, huecos: estado.huecos.filter((h) => !ocupados.includes(h)) } : d));
        horario = null;
        avisoOcupado = true;
        pintar();
        enfocar('[data-aviso-ocupado]');
      } else if (!manejarErrorComun(estado)) {
        avisoDeAccion = estado.tipo === 'demasiadas' ? { tipo: 'demasiadas', segundos: estado.segundos } : { tipo: 'inesperado' };
        pintar();
      } else {
        pintar();
      }
    } finally {
      ocupando = false;
    }
  }

  async function cancelar(): Promise<void> {
    if (ocupando) return;
    ocupando = true;
    try {
      const vista = aVistaDeTurno(await http.post<unknown>(`${base}/cancelacion`));
      anterior = datos;
      if (vista !== null) datos = vista;
      paso = 'cancelado';
      pintar();
      enfocar('[data-titulo]');
    } catch (error) {
      const estado = traducirErrorPublico(error);
      if (!manejarErrorComun(estado)) avisoDeAccion = estado.tipo === 'demasiadas' ? { tipo: 'demasiadas', segundos: estado.segundos } : { tipo: 'inesperado' };
      pintar();
    } finally {
      ocupando = false;
    }
  }

  function agregarAlCalendario(vista: VistaDeTurno): void {
    void uidDelTurno(dependencias.token).then((uid) => {
      const c = vista.consultorio;
      descargar(
        'turno.ics',
        generarIcs({
          uid,
          inicio: vista.turno.inicio,
          fin: vista.turno.fin,
          titulo: `${vista.turno.prestacion} · ${c.nombre}`,
          ubicacion: c.direccion === null ? c.nombre : `${c.nombre}, ${c.direccion}`,
          descripcion: `Para ver, cambiar o cancelar tu turno: ${link}`,
        }),
      );
    });
  }

  // ───────────── Vistas ─────────────

  function enfocar(selector: string): void {
    contenedor.querySelector<HTMLElement>(selector)?.focus();
  }

  function pintar(): void {
    if (datos !== null && paso !== 'invalido') pintarCabecera(cabecera, datos.consultorio, false);
    else ocultarCabecera(cabecera);
    contenedor.replaceChildren(vista());
  }

  function vista(): HTMLElement {
    switch (paso) {
      case 'cargando':
        return esqueleto({ etiqueta: 'Cargando tu turno…', filas: 2 });
      case 'invalido':
        return resultado({
          icono: 'link-roto',
          tono: 'neutro',
          titulo: 'Este link no funciona',
          texto: 'Puede que el turno ya haya pasado o se haya cancelado, o que el link esté incompleto. Si tenés dudas sobre tu turno, comunicate con el consultorio.',
        });
      case 'error-carga':
        if (errorDeCarga?.tipo === 'demasiadas') {
          return nota('advertencia', 'Hiciste muchas consultas seguidas.', mensajeDeEspera(errorDeCarga.segundos), { role: 'alert' });
        }
        return errorRecuperable({
          titulo: errorDeCarga?.tipo === 'sin-conexion' ? 'No hay conexión.' : 'No pudimos cargar tu turno.',
          texto: errorDeCarga?.tipo === 'sin-conexion' ? 'Revisá tu conexión a internet y probá de nuevo.' : 'Puede ser un problema momentáneo. Probá de nuevo en unos segundos.',
          alReintentar: () => {
            paso = 'cargando';
            pintar();
            void cargar();
          },
        });
      case 'ver':
        return vistaVer(datos as VistaDeTurno);
      case 'reprogramar':
        return vistaReprogramar(datos as VistaDeTurno);
      case 'reprogramado':
        return vistaReprogramado(datos as VistaDeTurno);
      case 'cancelado':
        return vistaCancelado();
    }
  }

  function detalle(v: VistaDeTurno): string {
    return `${v.turno.prestacion} · ${minutosEntre(v.turno.inicio, v.turno.fin)} min · hasta las ${formatearHora(v.turno.fin, zona())}`;
  }

  function tarjetaDelTurno(v: VistaDeTurno): HTMLElement {
    const estado = ESTADOS[v.turno.estado];
    return el(
      'div',
      { class: 'grid gap-2 p-4 rounded-lg border border-borde bg-superficie text-left' },
      el(
        'div',
        { class: 'flex flex-wrap items-center justify-between gap-2' },
        el('span', { class: 'text-xs font-semibold uppercase tracking-wide text-texto-tenue' }, `${v.paciente.nombre} ${v.paciente.apellido}`),
        estado === undefined ? el('span', { class: 'text-sm font-semibold' }, v.turno.estado) : chipEstado(estado),
      ),
      el(
        'div',
        { class: 'flex gap-3 items-start' },
        icono('calendario', 'w-icono h-icono flex-none mt-1 text-primario'),
        el(
          'div',
          {},
          el('div', { class: `text-lg font-bold ${v.turno.estado === 'CANCELADO' ? 'line-through text-texto-tenue' : 'text-texto'}` }, cuandoLargo(v.turno.inicio, zona())),
          el('div', { class: 'text-sm text-texto-tenue' }, detalle(v)),
        ),
      ),
    );
  }

  function textoDelPlazo(v: VistaDeTurno, soloCancelar: boolean): HTMLElement | null {
    if (v.modificableHasta === null) return null;
    const horas = Math.round((Date.parse(v.turno.inicio) - Date.parse(v.modificableHasta)) / MS_POR_HORA);
    const cuando = cuandoCorto(v.modificableHasta, zona()).replace(', ', ' a las ');
    return el(
      'p',
      { class: 'flex gap-2 items-start mt-3 mb-4 text-sm text-texto-tenue' },
      icono('info', 'w-icono-sm h-icono-sm flex-none mt-1'),
      el('span', {}, soloCancelar ? 'Podés cancelarlo por acá hasta el ' : 'Podés cambiarlo o cancelarlo por acá hasta el ', el('b', {}, cuando), ` (${horas} horas antes).`),
    );
  }

  function avisoGeneral(): HTMLElement | null {
    if (avisoDeAccion?.tipo === 'conflicto') return nota('info', 'Tu turno cambió mientras lo mirabas.', 'Revisá cómo quedó antes de hacer otro cambio.', { role: 'status' });
    if (avisoDeAccion?.tipo === 'demasiadas') return nota('advertencia', 'Hiciste muchas consultas seguidas.', mensajeDeEspera(avisoDeAccion.segundos), { role: 'alert' });
    if (avisoDeAccion?.tipo === 'inesperado') return nota('peligro', 'No pudimos hacer el cambio.', 'Tu turno sigue como estaba. Probá de nuevo en unos segundos.');
    return null;
  }

  function vistaVer(v: VistaDeTurno): HTMLElement {
    const cancelado = v.turno.estado === 'CANCELADO';
    const fueraDePlazo = !cancelado && (avisoDeAccion?.tipo === 'fuera-de-plazo' || !v.puedeModificar);
    const soloCancelar = !cancelado && !fueraDePlazo && (avisoDeAccion?.tipo === 'solo-cancelar' || !v.puedeReprogramar);
    const tel = telefono();

    const partes: Hijo[] = [
      el('h1', { id: 'titulo-turno', 'data-titulo': true, tabindex: '-1', class: 'mt-0 mb-2 text-2xl font-bold text-texto focus:outline-none' }, 'Tu turno'),
      avisoGeneral(),
      tarjetaDelTurno(v),
    ];
    const explicacion = EXPLICACIONES[v.turno.estado];
    if (!cancelado && explicacion !== undefined) partes.push(el('p', { class: 'mt-2 mb-0 text-sm text-texto-tenue' }, explicacion));

    if (cancelado) {
      partes.push(
        el(
          'div',
          { class: 'mt-4' },
          nota('info', v.turno.canceladoPor === 'ODONTOLOGO' ? 'El consultorio canceló este turno.' : 'Cancelaste este turno.', 'Si querés otro turno, comunicate con el consultorio.', { icono: 'cruz-circulo' }),
        ),
        tel === null ? null : botonLlamar(tel),
      );
    } else if (fueraDePlazo) {
      const horas = v.modificableHasta === null ? null : Math.round((Date.parse(v.turno.inicio) - Date.parse(v.modificableHasta)) / MS_POR_HORA);
      partes.push(
        el(
          'div',
          { class: 'mt-4' },
          nota(
            'advertencia',
            'Ya no podés cambiarlo ni cancelarlo por acá.',
            horas === null ? 'Si necesitás cambiarlo, llamá al consultorio.' : `Faltan menos de ${horas} horas para el turno. Si necesitás cambiarlo, llamá al consultorio.`,
          ),
        ),
        tel === null ? null : botonLlamar(tel),
      );
    } else {
      if (soloCancelar) {
        partes.push(
          el(
            'div',
            { class: 'mt-4' },
            nota('info', 'El consultorio no está tomando turnos online.', 'Podés cancelar tu turno, pero no cambiarlo de horario. Para cambiarlo, llamá al consultorio.'),
          ),
        );
      }
      partes.push(textoDelPlazo(v, soloCancelar));
      const acciones: HTMLElement[] = [];
      if (!soloCancelar) {
        const cambiar = el('button', { type: 'button', class: `${clasesBoton('secundario')} w-full` }, 'Cambiar día u horario');
        cambiar.addEventListener('click', empezarReprogramacion);
        acciones.push(cambiar);
      }
      const botonCancelar = el('button', { type: 'button', class: `${clasesBoton('peligro-secundario')} w-full` }, 'Cancelar turno');
      botonCancelar.addEventListener('click', () => confirmarCancelacion(v));
      acciones.push(botonCancelar);
      if (soloCancelar && tel !== null) acciones.push(botonLlamar(tel));
      else {
        const calendario = el('button', { type: 'button', class: `${clasesBoton('fantasma')} w-full` }, icono('calendario-mas', 'w-icono-sm h-icono-sm flex-none'), 'Agregar al calendario');
        calendario.addEventListener('click', () => agregarAlCalendario(v));
        acciones.push(calendario);
      }
      partes.push(el('div', { class: 'grid gap-2' }, ...acciones));
    }
    return el('section', { 'aria-labelledby': 'titulo-turno' }, ...partes);
  }

  function confirmarCancelacion(v: VistaDeTurno): void {
    const si = el('button', { type: 'button', class: `${clasesBoton('peligro')} w-full` }, 'Sí, cancelar turno');
    const no = el('button', { type: 'button', class: `${clasesBoton('fantasma')} w-full` }, 'No, mantenerlo');
    const caja = dialogo({
      titulo: `¿Cancelar tu turno del ${cuandoCorto(v.turno.inicio, zona()).replace(', ', ' a las ')}?`,
      descripcion: 'El horario queda libre para otra persona y este link deja de funcionar. No se puede deshacer.',
      acciones: [si, no],
    });
    si.addEventListener('click', () => {
      caja.cerrar();
      void cancelar();
    });
    no.addEventListener('click', () => caja.cerrar());
    caja.abrir();
  }

  function confirmarReprogramacion(v: VistaDeTurno, nuevo: string): void {
    const si = el('button', { type: 'button', class: `${clasesBoton('primario')} w-full` }, 'Sí, mover mi turno');
    const no = el('button', { type: 'button', class: `${clasesBoton('fantasma')} w-full` }, 'No, dejarlo como está');
    const caja = dialogo({
      titulo: `¿Mover tu turno al ${cuandoCorto(nuevo, zona()).replace(', ', ' a las ')}?`,
      descripcion: `Dejás libre el ${cuandoCorto(v.turno.inicio, zona()).replace(', ', ' a las ')}. Es la misma prestación: ${v.turno.prestacion}, ${minutosEntre(v.turno.inicio, v.turno.fin)} min.`,
      acciones: [si, no],
    });
    si.addEventListener('click', () => {
      caja.cerrar();
      void reprogramar(nuevo);
    });
    no.addEventListener('click', () => caja.cerrar());
    caja.abrir();
  }

  function vistaReprogramar(v: VistaDeTurno): HTMLElement {
    const volver = el('button', { type: 'button', class: `${clasesBoton('fantasma')} -ml-4 mb-3` }, icono('izquierda', 'w-icono-sm h-icono-sm flex-none'), 'Volver a mi turno');
    volver.addEventListener('click', () => {
      paso = 'ver';
      pintar();
      enfocar('[data-titulo]');
    });
    const dia = dias.find((d) => d.fecha === diaElegido);
    const ultimo = dias.at(-1);
    const accion =
      horario === null
        ? botonPrincipal('Elegí un horario', { deshabilitado: true })
        : botonPrincipal(`Mover al ${cuandoCorto(horario, zona())}`, { alHacerClic: () => horario !== null && confirmarReprogramacion(v, horario) });

    const horarios: Hijo =
      dia === undefined
        ? el('p', { class: 'm-0 text-sm text-texto-tenue' }, 'Elegí un día para ver los horarios.')
        : dia.huecos.length === 0 && ocupados.length === 0
          ? el('p', { class: 'm-0 p-4 rounded-md border border-dashed border-borde-fuerte text-center text-texto-tenue' }, 'No quedan horarios ese día. Probá con otro.')
          : listaDeHorarios({ huecos: dia.huecos, ocupados, zona: zona(), elegido: horario, alElegir: (h) => ((horario = h), pintar()) });

    return el(
      'section',
      { 'aria-labelledby': 'titulo-reprogramar', class: 'con-barra' },
      volver,
      el('h1', { id: 'titulo-reprogramar', 'data-titulo': true, tabindex: '-1', class: 'mt-0 mb-2 text-2xl font-bold text-texto focus:outline-none' }, 'Cambiá el día u horario'),
      avisoOcupado
        ? nota(
            'peligro',
            'Ese horario se ocupó hace un instante.',
            el('p', { class: 'm-0' }, 'Tu turno sigue el ', el('b', {}, cuandoCorto(v.turno.inicio, zona()).replace(', ', ' a las ')), '. Elegí otro horario si querés moverlo.'),
            { atributos: { 'data-aviso-ocupado': true, tabindex: '-1' } },
          )
        : null,
      avisoGeneral(),
      el(
        'div',
        { class: 'grid gap-1 p-4 rounded-lg border border-borde bg-superficie' },
        el('span', { class: 'text-xs font-semibold uppercase tracking-wide text-texto-tenue' }, 'Tu turno actual'),
        el('div', { class: 'flex gap-3 items-start' }, icono('calendario', 'w-icono h-icono flex-none mt-1 text-primario'), el('div', {}, el('div', { class: 'font-bold text-texto' }, cuandoLargo(v.turno.inicio, zona())), el('div', { class: 'text-sm text-texto-tenue' }, `${v.turno.prestacion} · ${minutosEntre(v.turno.inicio, v.turno.fin)} min`))),
      ),
      tituloDePaso('Elegí el día nuevo'),
      errorDeDias === null
        ? dias.length === 0 && cargandoDias
          ? el('p', { role: 'status', class: 'm-0 text-sm text-texto-tenue' }, 'Buscando días con turnos…')
          : tiraDeDias({
              dias,
              elegido: diaElegido,
              alElegir: (fecha) => {
                if (fecha !== diaElegido) {
                  horario = null;
                  ocupados = [];
                  avisoOcupado = false;
                }
                diaElegido = fecha;
                pintar();
              },
              quedanMas: ultimo !== undefined && quedanDias(ultimo.fecha, horizonte()),
              cargando: cargandoDias,
              alVerMas: () => void cargarDias(ultimo === undefined ? hoyEn(zona(), ahora()) : sumarDias(ultimo.fecha, 1)),
            })
        : errorDeDias.tipo === 'demasiadas'
          ? nota('advertencia', 'Hiciste muchas consultas seguidas.', mensajeDeEspera(errorDeDias.segundos), { role: 'alert' })
          : errorRecuperable({ titulo: 'No pudimos cargar los días.', alReintentar: () => void cargarDias(ultimo === undefined ? hoyEn(zona(), ahora()) : sumarDias(ultimo.fecha, 1)) }),
      tituloDePaso('Elegí el horario nuevo'),
      horarios,
      avisoDeZona(zona()),
      barraDeAccion(accion),
    );
  }

  function vistaReprogramado(v: VistaDeTurno): HTMLElement {
    const previo = anterior;
    const calendario = el('button', { type: 'button', class: `${clasesBoton('secundario')} w-full` }, icono('calendario-mas', 'w-icono-sm h-icono-sm flex-none'), 'Agregar el horario nuevo al calendario');
    calendario.addEventListener('click', () => agregarAlCalendario(v));
    const verTurno = el('button', { type: 'button', class: `${clasesBoton('fantasma')} w-full` }, 'Ver mi turno');
    verTurno.addEventListener('click', () => {
      paso = 'ver';
      avisoDeAccion = null;
      pintar();
      enfocar('[data-titulo]');
    });
    return resultado({
      icono: 'check-circulo',
      tono: 'exito',
      titulo: 'Listo, cambiaste tu turno',
      texto: 'Tu link sigue sirviendo para verlo, cambiarlo o cancelarlo.',
      hijos: [
        previo === null
          ? null
          : el('div', {}, el('span', { class: 'sr-only' }, 'Antes: '), resumenDelTurno(cuandoLargo(previo.turno.inicio, zona()), '', [], { viejo: true })),
        el('div', { 'aria-hidden': 'true', class: 'grid place-items-center my-2 text-texto-tenue' }, icono('flecha-abajo', 'w-icono h-icono')),
        el('div', { class: 'mb-4' }, el('span', { class: 'sr-only' }, 'Ahora: '), resumenDelTurno(cuandoLargo(v.turno.inicio, zona()), detalle(v))),
        el('div', { class: 'grid gap-2' }, calendario, verTurno),
      ],
    });
  }

  function vistaCancelado(): HTMLElement {
    const v = anterior ?? (datos as VistaDeTurno);
    return resultado({
      icono: 'cruz-circulo',
      tono: 'neutro',
      titulo: 'Cancelaste tu turno',
      texto: el('span', {}, 'El horario del ', el('b', {}, cuandoCorto(v.turno.inicio, zona()).replace(', ', ' a las ')), ' quedó libre. Este link ya no funciona: si querés otro turno, reservá de nuevo.'),
      hijos: [resumenDelTurno(cuandoLargo(v.turno.inicio, zona()), detalle(v), [], { viejo: true })],
    });
  }

  pintar();
  await cargar();
}
