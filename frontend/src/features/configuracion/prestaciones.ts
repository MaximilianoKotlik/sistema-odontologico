import type { ContextoPagina } from '../../pages/pagina';
import { el, idUnico } from '../../shared/dom';
import { ApiError, type ClienteHttp } from '../../shared/http';
import { boton } from '../../shared/ui/boton';
import { dialogo } from '../../shared/ui/dialogo';
import { errorRecuperable } from '../../shared/ui/error-recuperable';
import { esqueleto } from '../../shared/ui/esqueleto';
import { icono } from '../../shared/ui/iconos';
import { avisoAuth } from '../auth/ui';
import { activarPrestacion, crearPrestacion, desactivarPrestacion, editarPrestacion, listarPrestaciones, type PrestacionResponse } from './api';
import { traducirError } from './errores';
import { campoConfig, regionAnuncios } from './ui';
import { DURACION_MAXIMA, DURACION_MINIMA, validarDuracion } from './validaciones';

/**
 * Pestaña Prestaciones (C-09, spec app-configuracion/prestaciones; mockup `prestaciones.html`): listado en orden,
 * alta y edición en un diálogo, subir y bajar sin arrastrar (WCAG 2.5.7), desactivar y activar. No hay borrado (RN-AG-07).
 */

export interface Prestacion {
  readonly id: string;
  readonly nombre: string;
  readonly duracionMin: number;
  readonly activa: boolean;
  readonly visiblePublica: boolean;
  readonly orden: number;
}

export function aPrestacion(p: PrestacionResponse): Prestacion | null {
  if (p.id === undefined || p.nombre === undefined || p.duracionMin === undefined) return null;
  return { id: p.id, nombre: p.nombre, duracionMin: p.duracionMin, activa: p.activa ?? true, visiblePublica: p.visiblePublica ?? true, orden: p.orden ?? 0 };
}

const porOrden = (a: Prestacion, b: Prestacion) => a.orden - b.orden || a.nombre.localeCompare(b.nombre, 'es');
const DURACIONES_FRECUENTES = [15, 20, 30, 45, 60] as const;
const CLASE_ICONO_BOTON = 'inline-flex items-center justify-center w-touch min-h-touch rounded-md bg-transparent border-0 text-texto cursor-pointer hover:bg-superficie-tenue disabled:text-texto-sutil disabled:cursor-not-allowed';

function botonIcono(nombreIcono: Parameters<typeof icono>[0], etiqueta: string, alHacerClic: () => void, deshabilitado = false): HTMLButtonElement {
  const b = el('button', { type: 'button', 'aria-label': etiqueta, disabled: deshabilitado, class: CLASE_ICONO_BOTON, 'data-requiere-conexion': true }, icono(nombreIcono));
  b.addEventListener('click', alHacerClic);
  return b;
}

const insignia = (nombreIcono: Parameters<typeof icono>[0], texto: string, clase: string) =>
  el('span', { class: `inline-flex items-center gap-1 px-2 rounded-full border text-xs font-semibold ${clase}` }, icono(nombreIcono, 'w-icono-sm h-icono-sm flex-none'), texto);

export interface OpcionesDialogoPrestacion {
  readonly http: ClienteHttp;
  readonly existente?: Prestacion;
  /** Orden de una prestación nueva (al final). */
  readonly ordenNuevo: number;
  readonly alGuardar: (prestacion: Prestacion) => void;
}

/** Diálogo de alta y edición (D6), con foco atrapado; un error de la API lo deja abierto con el error en su campo. */
export function abrirDialogoPrestacion(opciones: OpcionesDialogoPrestacion): void {
  const { existente } = opciones;
  const nombre = campoConfig({ etiqueta: 'Nombre', control: el('input', { name: 'nombre', maxlength: 80 }) });
  const duracion = campoConfig({
    etiqueta: 'Duración',
    control: el('input', { name: 'duracionMin', type: 'number', inputmode: 'numeric', min: DURACION_MINIMA, max: DURACION_MAXIMA, step: 5 }),
    sufijo: 'minutos',
    corto: true,
    ayuda: 'Entre 5 y 480 minutos, en múltiplos de 5.',
  });
  const avisoDuracion = el('div', {});
  const chips = el(
    'ul',
    { 'aria-label': 'Duraciones frecuentes', class: 'flex flex-wrap gap-2 mt-1 mb-0 p-0 list-none' },
    ...DURACIONES_FRECUENTES.map((minutos) => {
      const chip = el('button', { type: 'button', 'aria-pressed': 'false', class: 'inline-flex items-center min-h-touch px-3 rounded-full border-control border-borde-fuerte bg-superficie text-sm font-medium cursor-pointer aria-pressed:border-primario aria-pressed:bg-primario-tinte aria-pressed:text-primario aria-pressed:font-semibold' }, String(minutos));
      chip.addEventListener('click', () => {
        duracion.control.value = String(minutos);
        duracion.control.dispatchEvent(new Event('input', { bubbles: true }));
      });
      return el('li', {}, chip);
    }),
  );
  duracion.extra.append(chips, avisoDuracion);

  const idPublica = idUnico('prestacion-publica');
  const publica = el('input', { type: 'checkbox', id: idPublica, name: 'visiblePublica', class: 'flex-none w-icono h-icono m-3 accent-primario' });
  publica.checked = existente?.visiblePublica ?? true;
  const filaPublica = el(
    'div',
    { class: 'flex gap-3 items-start mb-4' },
    publica,
    el(
      'label',
      { for: idPublica, class: 'pt-2 text-sm' },
      el('strong', { class: 'block' }, 'Mostrar en mi página pública'),
      'Si la desmarcás, solo vos podés darla desde tu agenda (por ejemplo, controles post-tratamiento).',
    ),
  );

  nombre.control.value = existente?.nombre ?? '';
  duracion.control.value = existente === undefined ? '30' : String(existente.duracionMin);
  const zonaError = el('div', {});
  const guardar = boton({ texto: existente === undefined ? 'Agregar' : 'Guardar cambios', tipo: 'submit', requiereConexion: true });
  const formulario = el(
    'form',
    { novalidate: true },
    zonaError,
    nombre.contenedor,
    duracion.contenedor,
    filaPublica,
    el('div', { class: 'flex flex-wrap gap-2 justify-end' }, boton({ texto: 'Cancelar', variante: 'fantasma', alHacerClic: () => ventana.cerrar() }), guardar),
  );
  const ventana = dialogo({ titulo: existente === undefined ? 'Agregar prestación' : 'Editar prestación', contenido: formulario, acciones: [] });

  const marcarChips = () => {
    for (const chip of chips.querySelectorAll('button')) chip.setAttribute('aria-pressed', String(chip.textContent === duracion.control.value));
  };
  duracion.control.addEventListener('input', () => {
    duracion.error(null);
    marcarChips();
    const nueva = Number(duracion.control.value);
    avisoDuracion.replaceChildren();
    if (existente !== undefined && Number.isInteger(nueva) && nueva !== existente.duracionMin && validarDuracion(nueva) === null) {
      const aviso = avisoAuth({
        tono: 'info',
        icono: 'info',
        rol: null,
        cuerpo: [`Pasa de ${existente.duracionMin} a ${nueva} minutos `, el('b', {}, 'para los turnos nuevos'), '. Los turnos que ya diste conservan su duración.'],
      });
      aviso.classList.replace('mb-5', 'mt-2');
      avisoDuracion.replaceChildren(aviso);
    }
  });
  nombre.control.addEventListener('input', () => nombre.error(null));
  marcarChips();

  formulario.addEventListener('submit', (evento) => {
    evento.preventDefault();
    const valorNombre = nombre.control.value.trim();
    const minutos = duracion.control.value.trim() === '' ? Number.NaN : Number(duracion.control.value);
    const errorDuracion = validarDuracion(minutos);
    nombre.error(valorNombre === '' ? 'Poné el nombre de la prestación.' : null);
    duracion.error(errorDuracion);
    if (valorNombre === '') return nombre.control.focus();
    if (errorDuracion !== null) return duracion.control.focus();

    guardar.disabled = true;
    const solicitud = { nombre: valorNombre, duracionMin: minutos, visiblePublica: publica.checked, orden: existente?.orden ?? opciones.ordenNuevo };
    const pedido = existente === undefined ? crearPrestacion(opciones.http, solicitud) : editarPrestacion(opciones.http, existente.id, solicitud);
    pedido
      .then((respuesta) => {
        const prestacion = aPrestacion(respuesta) ?? { id: existente?.id ?? '', activa: true, ...solicitud };
        ventana.cerrar();
        opciones.alGuardar(prestacion);
      })
      .catch((error: unknown) => {
        guardar.disabled = false;
        const traducido = error instanceof ApiError ? traducirError(error) : null;
        const enNombre = traducido?.campos.find((c) => c.campo === 'nombre');
        const enDuracion = traducido?.campos.find((c) => c.campo === 'duracionMin');
        if (enNombre !== undefined) nombre.error(enNombre.mensaje);
        if (enDuracion !== undefined) duracion.error(enDuracion.mensaje);
        if (enNombre === undefined && enDuracion === undefined) {
          zonaError.replaceChildren(avisoAuth({ tono: 'peligro', icono: 'alerta', titulo: traducido?.aviso ?? 'No pudimos guardar. Probá de nuevo.' }));
        }
      });
  });

  ventana.abrir();
}

function confirmarDesactivar(prestacion: Prestacion, esLaUltima: boolean): Promise<boolean> {
  return new Promise((resolver) => {
    let confirmado = false;
    const contenido = el(
      'div',
      {},
      el('p', { class: 'mt-0 mb-4' }, 'Deja de ofrecerse para turnos nuevos, en tu agenda y en tu link. ', el('b', {}, 'Los turnos que ya tiene siguen vigentes.'), ' La podés volver a activar cuando quieras.'),
      esLaUltima ? avisoAuth({ tono: 'advertencia', icono: 'alerta', rol: null, cuerpo: [el('b', {}, 'Es tu única prestación activa.'), ' Sin prestaciones activas no vas a poder dar turnos nuevos.'] }) : null,
    );
    const ventana = dialogo({
      titulo: `¿Desactivar ${prestacion.nombre}?`,
      contenido,
      acciones: [
        boton({ texto: 'Cancelar', variante: 'fantasma', alHacerClic: () => ventana.cerrar() }),
        boton({
          texto: 'Desactivar',
          variante: 'peligro',
          alHacerClic: () => {
            confirmado = true;
            ventana.cerrar();
          },
        }),
      ],
      alCerrar: () => resolver(confirmado),
    });
    ventana.abrir();
  });
}

export interface OpcionesPrestaciones {
  /** Avisa cada vez que cambia la lista de activas (el asistente habilita "Siguiente" con al menos una). */
  readonly alCambiarActivas?: (activas: readonly Prestacion[]) => void;
}

export function montarPrestaciones(contexto: Pick<ContextoPagina, 'http'>, destino: HTMLElement, opciones: OpcionesPrestaciones = {}): void {
  const anuncios = regionAnuncios();
  let todas: Prestacion[] = [];
  const zonaError = el('div', {});

  async function cargar(): Promise<void> {
    destino.replaceChildren(esqueleto({ etiqueta: 'Cargando las prestaciones…', filas: 3 }), anuncios.elemento);
    try {
      todas = (await listarPrestaciones(contexto.http, { incluirInactivas: true })).flatMap((p) => aPrestacion(p) ?? []);
      dibujar();
    } catch {
      destino.replaceChildren(errorRecuperable({ titulo: 'No pudimos cargar las prestaciones', alReintentar: () => void cargar() }), anuncios.elemento);
    }
  }

  const activas = () => todas.filter((p) => p.activa).sort(porOrden);
  const inactivas = () => todas.filter((p) => !p.activa).sort(porOrden);
  const reemplazar = (prestacion: Prestacion) => {
    todas = todas.some((p) => p.id === prestacion.id) ? todas.map((p) => (p.id === prestacion.id ? prestacion : p)) : [...todas, prestacion];
  };
  const siguienteOrden = () => Math.max(0, ...todas.map((p) => p.orden)) + 1;

  function abrirAlta(): void {
    abrirDialogoPrestacion({
      http: contexto.http,
      ordenNuevo: siguienteOrden(),
      alGuardar: (nueva) => {
        reemplazar(nueva);
        dibujar();
        anuncios.anunciar(`Agregamos ${nueva.nombre}`);
      },
    });
  }

  async function mover(prestacion: Prestacion, delta: -1 | 1): Promise<void> {
    const lista = activas();
    const indice = lista.findIndex((p) => p.id === prestacion.id);
    const otra = lista[indice + delta];
    if (otra === undefined) return;
    const movida = { ...prestacion, orden: otra.orden };
    const desplazada = { ...otra, orden: prestacion.orden };
    const anterior = todas;
    reemplazar(movida);
    reemplazar(desplazada);
    dibujar();
    try {
      for (const p of [movida, desplazada]) {
        await editarPrestacion(contexto.http, p.id, { nombre: p.nombre, duracionMin: p.duracionMin, visiblePublica: p.visiblePublica, orden: p.orden });
      }
      anuncios.anunciar(`${prestacion.nombre} pasó al lugar ${indice + delta + 1}`);
    } catch {
      // Dos PUT no son atómicos (Risks del design): se revierte y se vuelve a pedir la lista.
      todas = anterior;
      zonaError.replaceChildren(avisoAuth({ tono: 'peligro', icono: 'alerta', titulo: 'No pudimos cambiar el orden. Probá de nuevo.' }));
      void cargar();
    }
  }

  async function desactivar(prestacion: Prestacion): Promise<void> {
    if (!(await confirmarDesactivar(prestacion, activas().length === 1))) return;
    try {
      reemplazar(aPrestacion(await desactivarPrestacion(contexto.http, prestacion.id)) ?? { ...prestacion, activa: false });
      dibujar();
      anuncios.anunciar(`Desactivamos ${prestacion.nombre}`);
    } catch {
      zonaError.replaceChildren(avisoAuth({ tono: 'peligro', icono: 'alerta', titulo: `No pudimos desactivar ${prestacion.nombre}. Probá de nuevo.` }));
    }
  }

  async function activar(prestacion: Prestacion): Promise<void> {
    try {
      const respuesta = aPrestacion(await activarPrestacion(contexto.http, prestacion.id)) ?? { ...prestacion, activa: true };
      // Vuelve al final de las activas.
      reemplazar({ ...respuesta, activa: true, orden: Math.max(respuesta.orden, siguienteOrden()) });
      dibujar();
      anuncios.anunciar(`Activamos ${prestacion.nombre}`);
    } catch {
      zonaError.replaceChildren(avisoAuth({ tono: 'peligro', icono: 'alerta', titulo: `No pudimos activar ${prestacion.nombre}. Probá de nuevo.` }));
    }
  }

  function filaActiva(prestacion: Prestacion, indice: number, total: number): HTMLLIElement {
    return el(
      'li',
      { class: 'flex items-center gap-2 py-2 border-b border-borde last:border-b-0' },
      el(
        'div',
        { class: 'flex flex-col sm:flex-row' },
        botonIcono('subir', `Subir ${prestacion.nombre}`, () => void mover(prestacion, -1), indice === 0),
        botonIcono('bajar', `Bajar ${prestacion.nombre}`, () => void mover(prestacion, 1), indice === total - 1),
      ),
      el(
        'div',
        { class: 'flex-1 min-w-0' },
        el('span', { 'data-nombre': true, class: 'block font-semibold' }, prestacion.nombre),
        el(
          'span',
          { class: 'flex flex-wrap gap-1 mt-1' },
          insignia('reloj', `${prestacion.duracionMin} min`, 'text-texto-tenue border-borde bg-superficie'),
          prestacion.visiblePublica
            ? insignia('ojo', 'En tu página', 'text-confirmado-fg border-confirmado-bd bg-confirmado-bg')
            : insignia('ojo-tachado', 'Solo para vos', 'text-texto-tenue border-borde bg-superficie-tenue'),
        ),
      ),
      el(
        'div',
        { class: 'flex' },
        botonIcono('lapiz', `Editar ${prestacion.nombre}`, () =>
          abrirDialogoPrestacion({
            http: contexto.http,
            existente: prestacion,
            ordenNuevo: prestacion.orden,
            alGuardar: (editada) => {
              reemplazar(editada);
              dibujar();
              anuncios.anunciar(`Guardamos ${editada.nombre}`);
            },
          }),
        ),
        botonIcono('pausa', `Desactivar ${prestacion.nombre}`, () => void desactivar(prestacion)),
      ),
    );
  }

  function dibujar(): void {
    const listaActivas = activas();
    const listaInactivas = inactivas();
    opciones.alCambiarActivas?.(listaActivas);

    const vacio = el(
      'div',
      { class: 'text-center py-6' },
      el('div', { 'aria-hidden': 'true', class: 'grid place-items-center w-7 h-7 mx-auto mb-3 rounded-full bg-primario-tinte text-primario' }, icono('lista')),
      el('h3', { class: 'm-0 mb-1 text-lg font-bold' }, 'Todavía no cargaste prestaciones'),
      el('p', { class: 'mt-0 mb-4 text-texto-tenue' }, 'Sin prestaciones no se pueden dar turnos, ni desde tu agenda ni desde tu link.'),
      boton({ texto: 'Agregar prestación', icono: 'mas', alHacerClic: abrirAlta, requiereConexion: true }),
    );

    const encabezado = el(
      'div',
      { class: 'flex items-start justify-between gap-3 mb-4' },
      el('div', {}, el('h2', { class: 'm-0 text-lg font-bold' }, 'Tus prestaciones'), el('p', { class: 'mt-1 mb-0 text-sm text-texto-tenue' }, 'La duración define cuánto ocupa cada turno en tu agenda.')),
      listaActivas.length === 0 ? null : boton({ texto: 'Agregar', icono: 'mas', alHacerClic: abrirAlta, requiereConexion: true }),
    );

    const grupoInactivas =
      listaInactivas.length === 0
        ? null
        : el(
            'details',
            { class: 'mt-4 group' },
            el('summary', { class: 'flex items-center gap-2 min-h-touch font-semibold cursor-pointer' }, icono('derecha', 'w-icono-sm h-icono-sm flex-none group-open:rotate-90 motion-safe:transition-transform'), `Inactivas (${listaInactivas.length})`),
            el('p', { class: 'mt-0 mb-2 text-sm text-texto-tenue' }, 'No se ofrecen para turnos nuevos. Los turnos que ya tenían siguen igual.'),
            el(
              'ul',
              { 'data-prestaciones-inactivas': true, 'aria-label': 'Prestaciones inactivas', class: 'list-none m-0 p-0' },
              ...listaInactivas.map((p) =>
                el(
                  'li',
                  { class: 'flex items-center gap-2 py-2 border-b border-borde last:border-b-0 text-texto-tenue' },
                  el(
                    'div',
                    { class: 'flex-1 min-w-0' },
                    el('span', { 'data-nombre': true, class: 'block font-semibold' }, p.nombre),
                    el('span', { class: 'flex flex-wrap gap-1 mt-1' }, insignia('reloj', `${p.duracionMin} min`, 'border-borde bg-superficie'), insignia('pausa', 'Inactiva', 'border-borde bg-superficie-tenue')),
                  ),
                  (() => {
                    const b = boton({ texto: 'Activar', variante: 'secundario', alHacerClic: () => void activar(p), requiereConexion: true });
                    b.setAttribute('aria-label', `Activar ${p.nombre}`);
                    return b;
                  })(),
                ),
              ),
            ),
          );

    const cuerpo =
      listaActivas.length === 0
        ? vacio
        : el(
            'ul',
            { 'data-prestaciones-activas': true, 'aria-label': 'Prestaciones activas, en el orden en que se ofrecen', class: 'list-none m-0 p-0' },
            ...listaActivas.map((p, i) => filaActiva(p, i, listaActivas.length)),
          );

    const contenedor = el('section', { class: 'max-w-3xl min-w-0 bg-superficie border border-borde rounded-lg p-4 md:p-5' }, encabezado, zonaError, cuerpo, grupoInactivas);
    destino.replaceChildren(contenedor, anuncios.elemento);
  }

  void cargar();
}
