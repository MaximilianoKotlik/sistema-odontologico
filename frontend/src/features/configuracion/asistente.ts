import type { ContextoPagina } from '../../pages/pagina';
import { el, type Hijo } from '../../shared/dom';
import { ApiError } from '../../shared/http';
import { nombreDeMarca } from '../../shared/marca';
import { boton, clasesBoton } from '../../shared/ui/boton';
import { errorRecuperable } from '../../shared/ui/error-recuperable';
import { esqueleto } from '../../shared/ui/esqueleto';
import { icono } from '../../shared/ui/iconos';
import { avisoAuth } from '../auth/ui';
import { crearPrestacion, guardarConfiguracion, listarPrestaciones, obtenerConfiguracion } from './api';
import { linkPublico } from './consultorio';
import { traducirError } from './errores';
import { montarHorarios, type ControlHorarios } from './horarios';
import { abrirDialogoPrestacion, aPrestacion, type Prestacion } from './prestaciones';
import { campoConfig, regionAnuncios } from './ui';
import { formularioDesdeRespuesta, solicitudDesdeFormulario, type FormularioConsultorio } from './unidades';
import { normalizarSlug, validarConsultorio } from './validaciones';

/**
 * Asistente de primer ingreso `/app/bienvenida` (C-09, spec app-configuracion/onboarding, FL-02; mockup
 * `bienvenida.html`): cuatro pasos sin la navegación del shell, cada uno guardado en la API antes de avanzar.
 */

const PASOS = ['Consultorio', 'Prestaciones', 'Horarios', 'Compartir'] as const;
/** Atajos de prestaciones comunes, los mismos del seed de C-08 (D6). */
const ATAJOS = [
  { nombre: 'Consulta', duracionMin: 30 },
  { nombre: 'Limpieza', duracionMin: 45 },
  { nombre: 'Extracción', duracionMin: 60 },
  { nombre: 'Control', duracionMin: 20 },
] as const;

const DIAS = ['lunes', 'martes', 'miércoles', 'jueves', 'viernes', 'sábado', 'domingo'];

function progreso(actual: number): HTMLOListElement {
  return el(
    'ol',
    { 'aria-label': 'Pasos de la configuración', class: 'grid grid-cols-4 gap-2 list-none m-0 mb-5 p-0' },
    ...PASOS.map((nombre, i) => {
      const completo = i < actual;
      const enCurso = i === actual;
      return el(
        'li',
        { 'aria-current': enCurso ? 'step' : null, class: 'grid gap-1 text-xs font-medium' },
        el('span', { 'aria-hidden': 'true', class: `block h-1 rounded-full ${completo || enCurso ? 'bg-primario' : 'bg-borde'}` }),
        el(
          'span',
          { class: `flex items-center gap-1 ${enCurso ? 'text-primario font-bold' : completo ? 'text-texto' : 'text-texto-tenue'}` },
          completo ? icono('check', 'w-icono-sm h-icono-sm flex-none text-exito') : null,
          el('span', { class: 'max-[419px]:sr-only' }, nombre),
          completo ? el('span', { class: 'sr-only' }, ' (completo)') : null,
        ),
      );
    }),
  );
}

export function montarAsistente(contexto: ContextoPagina): void {
  const anuncios = regionAnuncios();
  let paso = 0;
  let configuracion: FormularioConsultorio | null = null;
  let prestaciones: Prestacion[] = [];
  let controlHorarios: ControlHorarios | null = null;
  let resumenHorarios = '';
  let alSiguiente: () => Promise<boolean> = async () => true;

  const cuerpo = el('div', {});
  const atras = boton({ texto: 'Atrás', variante: 'fantasma', icono: 'izquierda', alHacerClic: () => irA(paso - 1) });
  const siguiente = boton({ texto: 'Siguiente', icono: 'derecha', requiereConexion: true });
  siguiente.addEventListener('click', () => {
    siguiente.disabled = true;
    void alSiguiente()
      .then((avanzar) => {
        if (avanzar) irA(paso + 1);
      })
      .finally(() => {
        siguiente.disabled = false;
      });
  });
  const terminarDespues = el('a', { href: '/app/agenda', 'data-link': true, class: `${clasesBoton('fantasma')} whitespace-nowrap` }, 'Terminar después');
  const irAgenda = el('a', { href: '/app/agenda', 'data-link': true, class: clasesBoton('primario') }, 'Ir a mi agenda', icono('derecha', 'w-icono-sm h-icono-sm flex-none'));
  const pie = el('footer', { class: 'sticky bottom-0 flex justify-between gap-2 px-4 py-3 bg-superficie border-t border-borde' }, atras, siguiente, irAgenda);

  contexto.contenedor.replaceChildren(
    el(
      'div',
      { class: 'min-h-screen flex flex-col bg-fondo' },
      el(
        'header',
        { class: 'flex items-center justify-between gap-3 px-4 py-2 bg-superficie border-b border-borde' },
        el(
          'span',
          { class: 'inline-flex items-center gap-2' },
          el('span', { 'aria-hidden': 'true', class: 'grid place-items-center w-6 h-6 rounded-md bg-primario text-sobre-primario' }, icono('diente', 'w-icono-sm h-icono-sm')),
          el('span', { 'data-marca': true, class: 'font-bold' }, nombreDeMarca()),
        ),
        terminarDespues,
      ),
      el('main', { id: 'contenido', tabindex: '-1', class: 'flex-1 w-full max-w-2xl mx-auto px-4 pt-5 pb-6 focus:outline-none' }, cuerpo, anuncios.elemento),
      pie,
    ),
  );

  function encabezadoDePaso(titulo: string, apoyo: string): HTMLElement[] {
    return [
      progreso(paso),
      el('p', { class: 'mt-0 mb-1 text-sm font-semibold text-texto-tenue' }, `Paso ${paso + 1} de ${PASOS.length}`),
      el('h1', { class: 'mt-0 mb-2 text-2xl' }, titulo),
      el('p', { class: 'mt-0 mb-5 text-texto-tenue' }, apoyo),
    ];
  }

  function irA(nuevo: number): void {
    paso = Math.max(0, Math.min(PASOS.length - 1, nuevo));
    atras.hidden = paso === 0 || paso === PASOS.length - 1;
    siguiente.hidden = paso === PASOS.length - 1;
    irAgenda.hidden = paso !== PASOS.length - 1;
    terminarDespues.hidden = paso === PASOS.length - 1;
    [pasoConsultorio, pasoPrestaciones, pasoHorarios, pasoCompartir][paso]?.();
    contexto.contenedor.querySelector<HTMLElement>('#contenido')?.focus({ preventScroll: true });
  }

  // ===== Paso 1: Consultorio =====
  async function pasoConsultorio(): Promise<void> {
    cuerpo.replaceChildren(esqueleto({ etiqueta: 'Cargando tu consultorio…', filas: 3 }));
    alSiguiente = async () => false;
    try {
      configuracion ??= formularioDesdeRespuesta(await obtenerConfiguracion(contexto.http));
    } catch {
      cuerpo.replaceChildren(errorRecuperable({ titulo: 'No pudimos cargar tu consultorio', alReintentar: () => void pasoConsultorio() }));
      return;
    }
    const base = configuracion;
    const nombre = campoConfig({ etiqueta: 'Nombre del consultorio', control: el('input', { name: 'nombre', autocomplete: 'organization', maxlength: 120 }) });
    const slug = campoConfig({
      etiqueta: 'Dirección de tu link',
      control: el('input', { name: 'slug', autocapitalize: 'none', autocomplete: 'off', spellcheck: 'false', maxlength: 40 }),
      prefijo: `${location.host}/c/`,
      ayuda: 'Lo armamos con el nombre. De 3 a 40 letras minúsculas, números o guiones.',
    });
    const telefono = campoConfig({
      etiqueta: 'Teléfono de contacto',
      control: el('input', { name: 'telefonoContacto', type: 'tel', inputmode: 'tel', autocomplete: 'tel', required: true, placeholder: '11 0000-0001' }),
      ayuda: 'Se muestra en tu página y cuando tus pacientes ya no pueden cancelar por el link.',
    });
    nombre.control.value = base.nombre;
    slug.control.value = base.slug;
    telefono.control.value = base.telefonoContacto;
    const sugerencias = el('ul', { 'aria-label': 'Direcciones libres sugeridas', class: 'flex flex-wrap gap-2 mt-1 mb-0 p-0 list-none' });
    slug.extra.append(sugerencias);
    slug.control.addEventListener('input', () => {
      slug.control.value = normalizarSlug(slug.control.value);
      slug.error(null);
    });
    nombre.control.addEventListener('input', () => nombre.error(null));
    telefono.control.addEventListener('input', () => telefono.error(null));

    const zonaError = el('div', {});
    const formulario = el(
      'form',
      { novalidate: true },
      zonaError,
      nombre.contenedor,
      slug.contenedor,
      telefono.contenedor,
      el('p', { class: 'm-0 text-sm text-texto-tenue' }, 'Las reglas de reserva arrancan con valores comunes: cancelar hasta 24 h antes, reservar con 2 h de anticipación y hasta 60 días adelante. Las cambiás en Configuración.'),
    );
    formulario.addEventListener('submit', (evento) => {
      evento.preventDefault();
      siguiente.click();
    });
    cuerpo.replaceChildren(...encabezadoDePaso('¡Hola! Armemos tu consultorio', 'En 4 pasos vas a tener tu link para que tus pacientes reserven. Lo podés cambiar todo después.'), formulario);

    alSiguiente = async () => {
      const datos: FormularioConsultorio = { ...base, nombre: nombre.control.value, slug: slug.control.value, telefonoContacto: telefono.control.value };
      const campos = { nombre, slug, telefonoContacto: telefono } as const;
      const errores = validarConsultorio(datos).filter((e): e is typeof e & { campo: keyof typeof campos } => e.campo in campos);
      for (const error of errores) {
        campos[error.campo].error(error.campo === 'telefonoContacto' ? 'Poné un teléfono de contacto: es lo que ven tus pacientes cuando no pueden cancelar por el link.' : error.mensaje);
      }
      if (errores.length > 0) {
        const primero = errores[0];
        if (primero !== undefined) campos[primero.campo].control.focus();
        return false;
      }
      if (JSON.stringify(datos) === JSON.stringify(base)) return true;
      try {
        configuracion = formularioDesdeRespuesta(await guardarConfiguracion(contexto.http, solicitudDesdeFormulario(datos)));
        return true;
      } catch (error) {
        const traducido = error instanceof ApiError ? traducirError(error) : null;
        for (const c of traducido?.campos ?? []) (campos as Record<string, (typeof campos)[keyof typeof campos] | undefined>)[c.campo]?.error(c.mensaje);
        if (traducido !== null && traducido.sugerencias.length > 0) {
          slug.error([el('b', {}, slug.control.value), ' ya lo usa otro consultorio. Elegí una de estas o escribí otra:']);
          sugerencias.replaceChildren(
            ...traducido.sugerencias.map((s) =>
              el(
                'li',
                {},
                boton({
                  texto: s,
                  variante: 'secundario',
                  alHacerClic: () => {
                    slug.control.value = s;
                    slug.error(null);
                    sugerencias.replaceChildren();
                  },
                }),
              ),
            ),
          );
        }
        if ((traducido?.campos.length ?? 0) === 0) zonaError.replaceChildren(avisoAuth({ tono: 'peligro', icono: 'alerta', titulo: traducido?.aviso ?? 'No pudimos guardar. Probá de nuevo.' }));
        return false;
      }
    };
  }

  // ===== Paso 2: Prestaciones =====
  async function pasoPrestaciones(): Promise<void> {
    cuerpo.replaceChildren(esqueleto({ etiqueta: 'Cargando tus prestaciones…', filas: 3 }));
    alSiguiente = async () => false;
    try {
      prestaciones = (await listarPrestaciones(contexto.http)).flatMap((p) => aPrestacion(p) ?? []).filter((p) => p.activa);
    } catch {
      cuerpo.replaceChildren(errorRecuperable({ titulo: 'No pudimos cargar tus prestaciones', alReintentar: () => void pasoPrestaciones() }));
      return;
    }
    const zonaError = el('div', {});
    const atajos = el('ul', { 'aria-labelledby': 'atajos-titulo', class: 'flex flex-wrap gap-2 m-0 p-0 list-none' });
    const lista = el('div', {});
    const siguienteOrden = () => Math.max(0, ...prestaciones.map((p) => p.orden)) + 1;
    const existe = (nombre: string) => prestaciones.some((p) => p.nombre.toLocaleLowerCase('es') === nombre.toLocaleLowerCase('es'));

    function dibujar(): void {
      atajos.replaceChildren(
        ...ATAJOS.map((a) => {
          const texto = `${a.nombre} · ${a.duracionMin} min`;
          if (existe(a.nombre)) {
            return el('li', {}, el('span', { class: 'inline-flex items-center gap-1 min-h-touch px-3 rounded-full border-control border-dashed border-exito text-exito text-sm font-medium' }, icono('check', 'w-icono-sm h-icono-sm flex-none'), texto, el('span', { class: 'sr-only' }, ' (agregada)')));
          }
          const chip = el('button', { type: 'button', 'data-requiere-conexion': true, class: 'inline-flex items-center gap-1 min-h-touch px-3 rounded-full border-control border-borde-fuerte bg-superficie text-sm font-medium cursor-pointer hover:border-primario hover:text-primario' }, icono('mas', 'w-icono-sm h-icono-sm flex-none'), texto);
          chip.addEventListener('click', () => void agregarAtajo(a.nombre, a.duracionMin));
          return el('li', {}, chip);
        }),
      );
      lista.replaceChildren(
        ...(prestaciones.length === 0
          ? []
          : [
              el('p', { class: 'mt-5 mb-2 text-sm font-semibold text-texto-tenue' }, `Tus prestaciones (${prestaciones.length})`),
              el(
                'ul',
                { 'aria-label': 'Tus prestaciones', class: 'list-none m-0 p-0 bg-superficie border border-borde rounded-lg' },
                ...prestaciones.map((p) => {
                  const editar = el('button', { type: 'button', 'aria-label': `Editar ${p.nombre}`, class: 'inline-flex items-center justify-center w-touch min-h-touch rounded-md bg-transparent border-0 cursor-pointer hover:bg-superficie-tenue' }, icono('lapiz'));
                  editar.addEventListener('click', () =>
                    abrirDialogoPrestacion({
                      http: contexto.http,
                      existente: p,
                      ordenNuevo: p.orden,
                      alGuardar: (editada) => {
                        prestaciones = prestaciones.map((x) => (x.id === editada.id ? editada : x));
                        dibujar();
                      },
                    }),
                  );
                  return el('li', { class: 'flex items-center gap-2 px-3 py-1 border-b border-borde last:border-b-0' }, el('span', { class: 'flex-1 font-semibold' }, p.nombre), el('span', { class: 'text-sm text-texto-tenue' }, `${p.duracionMin} min`), editar);
                }),
              ),
            ]),
      );
    }

    async function agregarAtajo(nombre: string, duracionMin: number): Promise<void> {
      zonaError.replaceChildren();
      try {
        const nueva = aPrestacion(await crearPrestacion(contexto.http, { nombre, duracionMin, visiblePublica: true, orden: siguienteOrden() }));
        if (nueva !== null) prestaciones = [...prestaciones, nueva];
        anuncios.anunciar(`Agregamos ${nombre}`);
      } catch (error) {
        if (error instanceof ApiError && error.codigo === 'PRESTACION_DUPLICADA') {
          // Ya existe: no es un error (D6). Se vuelve a pedir la lista para mostrarla.
          prestaciones = (await listarPrestaciones(contexto.http).catch(() => [])).flatMap((p) => aPrestacion(p) ?? []).filter((p) => p.activa);
        } else {
          zonaError.replaceChildren(avisoAuth({ tono: 'peligro', icono: 'alerta', titulo: `No pudimos agregar ${nombre}. Probá de nuevo.` }));
        }
      }
      dibujar();
    }

    const agregarOtra = boton({
      texto: 'Agregar otra',
      variante: 'secundario',
      icono: 'mas',
      requiereConexion: true,
      alHacerClic: () =>
        abrirDialogoPrestacion({
          http: contexto.http,
          ordenNuevo: siguienteOrden(),
          alGuardar: (nueva) => {
            prestaciones = [...prestaciones, nueva];
            dibujar();
            anuncios.anunciar(`Agregamos ${nueva.nombre}`);
          },
        }),
    });

    cuerpo.replaceChildren(
      ...encabezadoDePaso('¿Qué atendés?', 'Cargá tus prestaciones con cuánto duran. Así cada turno ocupa el tiempo real en tu agenda.'),
      zonaError,
      el('p', { id: 'atajos-titulo', class: 'mt-0 mb-2 text-sm font-semibold text-texto-tenue' }, 'Sugeridas: tocá para agregar'),
      atajos,
      lista,
      el('p', { class: 'mt-4 mb-0' }, agregarOtra),
    );
    dibujar();

    alSiguiente = async () => {
      if (prestaciones.length > 0) return true;
      zonaError.replaceChildren(avisoAuth({ tono: 'peligro', icono: 'alerta', cuerpo: [el('b', {}, 'Agregá al menos una prestación para seguir.'), ' Podés tocar una de las sugeridas.'] }));
      return false;
    };
  }

  // ===== Paso 3: Horarios =====
  function pasoHorarios(): void {
    const editor = el('div', {});
    cuerpo.replaceChildren(...encabezadoDePaso('¿Cuándo atendés?', 'Tus pacientes solo van a poder reservar dentro de estos horarios.'), editor);
    let guardado = false;
    controlHorarios = montarHorarios(contexto, editor, {
      sinBarra: true,
      exigirFranjas: true,
      textoAtajo: 'Lunes a viernes, 9 a 13 y 15 a 19',
      alGuardar: (franjas) => {
        guardado = true;
        resumenHorarios = resumirFranjas(franjas);
      },
    });
    alSiguiente = async () => {
      guardado = false;
      await controlHorarios?.guardar();
      return guardado;
    };
  }

  // ===== Paso 4: Compartir =====
  function pasoCompartir(): void {
    const url = linkPublico(configuracion?.slug ?? '');
    const copiar = boton({ texto: 'Copiar link', icono: 'copiar' });
    copiar.setAttribute('aria-live', 'polite');
    copiar.addEventListener('click', () => {
      void (navigator.clipboard?.writeText(url) ?? Promise.reject(new Error('sin portapapeles')))
        .then(() => copiar.replaceChildren(icono('check', 'w-icono-sm h-icono-sm flex-none'), '¡Copiado!'))
        .catch(() => anuncios.anunciar('No pudimos copiar el link. Seleccionalo y copialo a mano.'));
    });
    const puedeCompartir = typeof navigator.share === 'function';
    const compartir = puedeCompartir
      ? boton({ texto: 'Compartir', variante: 'secundario', icono: 'compartir', alHacerClic: () => void navigator.share({ title: configuracion?.nombre ?? nombreDeMarca(), url }).catch(() => undefined) })
      : null;
    const item = (...hijos: Hijo[]) => el('li', { class: 'flex gap-2 items-start' }, icono('check', 'w-icono-sm h-icono-sm flex-none mt-1 text-exito'), el('span', {}, ...hijos));
    const cancelan = configuracion === null ? '' : ` · cancelan hasta ${configuracion.anticipacionCancelacion.valor} ${configuracion.anticipacionCancelacion.unidad === 'horas' ? 'h' : 'min'} antes`;

    cuerpo.replaceChildren(
      progreso(paso),
      el('p', { class: 'mt-0 mb-1 text-sm font-semibold text-texto-tenue' }, `Paso ${PASOS.length} de ${PASOS.length}`),
      el(
        'div',
        { class: 'text-center mb-4' },
        el('div', { 'aria-hidden': 'true', class: 'grid place-items-center w-7 h-7 mx-auto mb-3 rounded-full bg-confirmado-bg text-exito' }, icono('check')),
        el('h1', { class: 'mt-0 mb-2 text-2xl' }, '¡Tu consultorio está listo!'),
        el('p', { class: 'm-0 text-texto-tenue' }, 'Compartí tu link para que tus pacientes reserven sin crear una cuenta.'),
      ),
      el('div', { class: 'p-4 mb-3 bg-primario-tinte border border-reservado-bd rounded-md' }, el('p', { class: 'm-0 font-mono font-semibold text-lg text-reservado-fg break-all' }, el('small', { class: 'block font-sans font-normal text-xs text-texto-tenue' }, 'Tu link público'), url)),
      el('div', { class: 'grid gap-2 min-[520px]:grid-cols-2' }, copiar, compartir),
      puedeCompartir ? '' : el('p', { class: 'mt-2 mb-0 text-sm text-texto-tenue' }, 'Tu navegador no permite compartir directo: copiá el link y pegalo donde quieras.'),
      el(
        'ul',
        { 'aria-label': 'Lo que configuraste', class: 'grid gap-2 mt-5 mb-0 p-0 list-none' },
        item(el('b', {}, configuracion?.nombre ?? ''), cancelan),
        item(el('b', {}, prestaciones.length === 1 ? '1 prestación' : `${prestaciones.length} prestaciones`), prestaciones.length === 0 ? '' : `: ${prestaciones.map((p) => p.nombre).join(', ')}`),
        resumenHorarios === '' ? null : item(resumenHorarios),
      ),
      el('p', { class: 'mt-4 mb-0 text-sm text-texto-tenue' }, 'Podés cambiar todo esto cuando quieras en ', el('b', {}, 'Configuración'), '.'),
    );
  }

  irA(0);
}

function resumirFranjas(franjas: readonly { readonly diaSemana: number; readonly horaInicio: string; readonly horaFin: string }[]): string {
  const dias = [...new Set(franjas.map((f) => f.diaSemana))].sort((a, b) => a - b);
  return dias.length === 0 ? '' : `Atendés ${dias.map((d) => DIAS[d - 1] ?? '').join(', ')}`;
}
