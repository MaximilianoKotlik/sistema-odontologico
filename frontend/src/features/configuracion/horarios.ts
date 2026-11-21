import type { ContextoPagina } from '../../pages/pagina';
import { el, idUnico } from '../../shared/dom';
import { ApiError } from '../../shared/http';
import { boton } from '../../shared/ui/boton';
import { dialogo } from '../../shared/ui/dialogo';
import { errorRecuperable } from '../../shared/ui/error-recuperable';
import { esqueleto } from '../../shared/ui/esqueleto';
import { icono } from '../../shared/ui/iconos';
import { avisoAuth } from '../auth/ui';
import { guardarGrilla, obtenerGrilla, type Franja } from './api';
import { vigilarCambios } from './cambios';
import { traducirError } from './errores';
import { barraDeGuardado, regionAnuncios, resumenDeErrores, CLASE_CONTROL } from './ui';
import { validarGrilla, type ErrorDeDia, type FranjaEditable } from './validaciones';

/**
 * Pestaña Horarios (C-09, spec app-configuracion/disponibilidad; mockup `horarios.html`): una tarjeta por día con sus
 * franjas, "Copiar a…", validación antes de guardar y guardado único de la semana (PUT de reemplazo de C-08, D7).
 */

const DIAS = ['Lunes', 'Martes', 'Miércoles', 'Jueves', 'Viernes', 'Sábado', 'Domingo'] as const;
const nombreDelDia = (dia: number) => DIAS[dia - 1] ?? `Día ${dia}`;
const ZONA_POR_DEFECTO = 'America/Argentina/Buenos_Aires';

/** Atajo "Lunes a viernes, 9 a 13 y 15 a 19" (mockups, decisión 2). */
export const SEMANA_TIPICA: readonly Franja[] = [1, 2, 3, 4, 5].flatMap((diaSemana) => [
  { diaSemana, horaInicio: '09:00', horaFin: '13:00' },
  { diaSemana, horaInicio: '15:00', horaFin: '19:00' },
]);

const aMinutos = (hora: string) => {
  const [h, m] = hora.split(':').map(Number);
  return (h ?? 0) * 60 + (m ?? 0);
};
const aHora = (minutos: number) => `${String(Math.floor(minutos / 60)).padStart(2, '0')}:${String(minutos % 60).padStart(2, '0')}`;

/** Franja nueva de un día: 09:00–13:00 si está vacío; si no, cuatro horas desde el fin de la última (tope 23:55). */
function franjaNueva(dia: number, existentes: readonly FranjaEditable[]): FranjaEditable {
  const ultima = existentes.at(-1);
  if (ultima === undefined) return { diaSemana: dia, horaInicio: '09:00', horaFin: '13:00' };
  const inicio = Math.min(aMinutos(ultima.horaFin), 23 * 60 + 50);
  return { diaSemana: dia, horaInicio: aHora(inicio), horaFin: aHora(Math.min(inicio + 240, 23 * 60 + 55)) };
}

const ordenarSemana = (franjas: readonly FranjaEditable[]) => [...franjas].sort((a, b) => a.diaSemana - b.diaSemana);

function confirmar(titulo: string, texto: string, textoConfirmar: string): Promise<boolean> {
  return new Promise((resolver) => {
    let confirmado = false;
    const ventana = dialogo({
      titulo,
      descripcion: texto,
      acciones: [
        boton({ texto: 'Seguir editando', alHacerClic: () => ventana.cerrar() }),
        boton({
          texto: textoConfirmar,
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

export interface OpcionesHorarios {
  /** Avisa después de guardar (el asistente pasa al paso siguiente). */
  readonly alGuardar?: (franjas: readonly Franja[]) => void;
  /** Texto del botón de guardar (el asistente usa "Guardar y seguir"). */
  readonly textoGuardar?: string;
  /** Asistente (spec onboarding): sin franjas no se guarda ni se avanza, en vez de confirmar una semana vacía. */
  readonly exigirFranjas?: boolean;
  /** Asistente: el guardado lo dispara su "Siguiente", así que no se muestra la barra de cambios. */
  readonly sinBarra?: boolean;
  /** Texto del atajo de lunes a viernes. */
  readonly textoAtajo?: string;
}

export interface ControlHorarios {
  /** Valida y guarda; sin cambios y con franjas, solo avisa `alGuardar` sin volver a enviar (D3). */
  guardar(): Promise<void>;
}

export function montarHorarios(contexto: Pick<ContextoPagina, 'http' | 'navegacion' | 'zonaHoraria'>, destino: HTMLElement, opciones: OpcionesHorarios = {}): ControlHorarios {
  const anuncios = regionAnuncios();
  const vigia = vigilarCambios(contexto.navegacion, window);
  let guardadas: FranjaEditable[] = [];
  let franjas: FranjaEditable[] = [];
  let erroresPorDia: ErrorDeDia[] = [];
  const zonaDeAvisos = el('div', {});
  const dias = el('div', { class: 'grid gap-4 min-[1100px]:grid-cols-2' });
  const barra = barraDeGuardado(opciones.textoGuardar ?? 'Guardar horarios', () => {
    franjas = [...guardadas];
    erroresPorDia = [];
    zonaDeAvisos.replaceChildren();
    dibujarDias();
  });
  const formulario = el('form', { novalidate: true, class: opciones.sinBarra === true ? '' : 'pb-7' }, zonaDeAvisos, dias, opciones.sinBarra === true ? null : barra.elemento);

  async function cargar(): Promise<void> {
    destino.replaceChildren(esqueleto({ etiqueta: 'Cargando tus horarios…' }), anuncios.elemento);
    try {
      guardadas = await obtenerGrilla(contexto.http);
      franjas = [...guardadas];
      destino.replaceChildren(introduccion(), formulario, anuncios.elemento);
      dibujarDias();
    } catch {
      destino.replaceChildren(errorRecuperable({ titulo: 'No pudimos cargar tus horarios', alReintentar: () => void cargar() }), anuncios.elemento);
    }
  }

  function introduccion(): HTMLElement {
    if (contexto.zonaHoraria === ZONA_POR_DEFECTO) return el('p', { class: 'mt-0 mb-4 text-texto-tenue' }, 'Los pacientes solo pueden reservar dentro de estas franjas.');
    return avisoAuth({
      tono: 'info',
      icono: 'info',
      rol: null,
      cuerpo: ['Los pacientes solo pueden reservar dentro de estas franjas. Horarios en la zona ', el('b', {}, contexto.zonaHoraria), ', la que elegiste en Consultorio.'],
    });
  }

  const conCambios = () => JSON.stringify(ordenarSemana(franjas)) !== JSON.stringify(ordenarSemana(guardadas));

  function cambiar(nuevas: FranjaEditable[]): void {
    franjas = nuevas;
    erroresPorDia = [];
    dibujarDias();
  }

  function copiarDia(dia: number): void {
    const delDia = franjas.filter((f) => f.diaSemana === dia);
    const casillas = DIAS.map((nombre, i) => ({ dia: i + 1, nombre })).filter((d) => d.dia !== dia).map((d) => {
      const id = idUnico('copiar-dia');
      const casilla = el('input', { type: 'checkbox', id, class: 'flex-none w-icono h-icono m-3 accent-primario' });
      return { dia: d.dia, casilla, fila: el('div', { class: 'flex items-center gap-1' }, casilla, el('label', { for: id, class: 'font-semibold' }, d.nombre)) };
    });
    const ventana = dialogo({
      titulo: `Copiar los horarios del ${nombreDelDia(dia).toLowerCase()}`,
      contenido: el(
        'div',
        { class: 'mb-4' },
        el('p', { class: 'mt-0 mb-2' }, `${delDia.map((f) => `${f.horaInicio}–${f.horaFin}`).join(' y ') || 'Sin franjas'}. Reemplaza los horarios de los días que elijas.`),
        el('fieldset', { class: 'border-0 m-0 p-0' }, el('legend', { class: 'sr-only' }, 'Días a los que copiar'), ...casillas.map((c) => c.fila)),
      ),
      acciones: [
        boton({ texto: 'Cancelar', variante: 'fantasma', alHacerClic: () => ventana.cerrar() }),
        boton({
          texto: 'Copiar',
          alHacerClic: () => {
            const elegidos = casillas.filter((c) => c.casilla.checked).map((c) => c.dia);
            ventana.cerrar();
            if (elegidos.length === 0) return;
            cambiar([...franjas.filter((f) => !elegidos.includes(f.diaSemana)), ...elegidos.flatMap((d) => delDia.map((f) => ({ ...f, diaSemana: d })))]);
            anuncios.anunciar(`Copiamos el ${nombreDelDia(dia).toLowerCase()} a ${elegidos.length} ${elegidos.length === 1 ? 'día' : 'días'}`);
          },
        }),
      ],
    });
    ventana.abrir();
  }

  function tarjetaDia(dia: number): HTMLElement {
    const nombre = nombreDelDia(dia);
    const minuscula = nombre.toLowerCase();
    const delDia = franjas.filter((f) => f.diaSemana === dia);
    const error = erroresPorDia.find((e) => e.diaSemana === dia);
    const idTitulo = `dia-${dia}`;

    const filas = delDia.map((f, i) => {
      const numero = i + 1;
      const hora = (valor: string, etiqueta: string, campo: 'horaInicio' | 'horaFin') => {
        const entrada = el('input', { type: 'time', step: 300, value: valor, 'aria-label': `${nombre}, franja ${numero}: ${etiqueta}`, 'aria-invalid': error === undefined ? null : 'true', class: `${CLASE_CONTROL} flex-1 min-w-0` });
        entrada.value = valor;
        // La posición no cambia hasta el próximo dibujo: agregar, quitar o copiar vuelven a dibujar los días.
        const posicion = franjas.indexOf(f);
        entrada.addEventListener('change', () => {
          franjas = franjas.map((x, j) => (j === posicion ? { ...x, [campo]: entrada.value } : x));
          actualizarBarra();
        });
        return entrada;
      };
      const quitar = el('button', { type: 'button', 'aria-label': `Quitar la franja ${numero} del ${minuscula}`, class: 'inline-flex items-center justify-center w-touch min-h-touch rounded-md bg-transparent border-0 text-texto-tenue cursor-pointer hover:bg-superficie-tenue' }, icono('cruz'));
      const posicionQuitar = franjas.indexOf(f);
      quitar.addEventListener('click', () => cambiar(franjas.filter((_, j) => j !== posicionQuitar)));
      return el('li', { class: 'flex items-center gap-2 mb-2' }, hora(f.horaInicio, 'desde', 'horaInicio'), el('span', { 'aria-hidden': 'true', class: 'text-texto-tenue' }, 'a'), hora(f.horaFin, 'hasta', 'horaFin'), quitar);
    });

    const agregar = el('button', { type: 'button', 'aria-label': `Agregar una franja al ${minuscula}`, class: 'inline-flex items-center gap-2 min-h-touch px-2 rounded-md bg-transparent border-0 font-semibold text-sm text-primario cursor-pointer hover:bg-superficie-tenue' }, icono('mas', 'w-icono-sm h-icono-sm flex-none'), 'Agregar franja');
    agregar.addEventListener('click', () => cambiar([...franjas, franjaNueva(dia, delDia)]));
    const copiar =
      delDia.length === 0
        ? null
        : (() => {
            const b = el('button', { type: 'button', 'aria-label': `Copiar los horarios del ${minuscula} a otros días`, class: 'inline-flex items-center gap-2 min-h-touch px-2 rounded-md bg-transparent border-0 text-sm text-texto cursor-pointer hover:bg-superficie-tenue' }, icono('copiar', 'w-icono-sm h-icono-sm flex-none'), 'Copiar a…');
            b.addEventListener('click', () => copiarDia(dia));
            return b;
          })();

    const estado = delDia.length === 0 ? 'No atendés' : delDia.length === 1 ? '1 franja' : `${delDia.length} franjas`;
    return el(
      'section',
      { 'data-dia': dia, 'aria-labelledby': idTitulo, class: `min-w-0 bg-superficie border rounded-lg p-4 ${error === undefined ? 'border-borde' : 'border-peligro'}` },
      el(
        'div',
        { class: 'flex items-center justify-between gap-2 mb-2' },
        el('h3', { id: idTitulo, tabindex: '-1', class: 'm-0 text-base font-bold' }, nombre, el('span', { class: 'ml-2 text-sm font-normal text-texto-tenue' }, estado)),
        copiar,
      ),
      delDia.length === 0 ? null : el('ul', { 'aria-label': `Franjas del ${minuscula}`, class: 'list-none m-0 p-0' }, ...filas),
      error === undefined ? null : el('p', { class: 'mt-0 mb-2 flex gap-1 items-start text-sm font-medium text-peligro' }, icono('alerta', 'w-icono-sm h-icono-sm flex-none mt-1'), el('span', {}, error.mensaje)),
      agregar,
    );
  }

  function actualizarBarra(): void {
    const hay = conCambios();
    barra.mostrar(hay);
    vigia.marcar(hay);
  }

  function dibujarDias(): void {
    const vacia = franjas.length === 0 && guardadas.length === 0;
    if (vacia && zonaDeAvisos.querySelector('[data-semana-vacia]') === null) {
      const aviso = avisoAuth({ tono: 'advertencia', icono: 'info', rol: null, titulo: 'Todavía no cargaste horarios', cuerpo: ['Sin horarios, tus pacientes no pueden reservar desde tu link.'] });
      aviso.dataset['semanaVacia'] = '';
      aviso.lastElementChild?.append(el('div', { class: 'mt-2' }, boton({ texto: opciones.textoAtajo ?? 'Cargar lunes a viernes, 9 a 13 y 15 a 19', variante: 'secundario', alHacerClic: () => cambiar([...SEMANA_TIPICA]) })));
      zonaDeAvisos.replaceChildren(aviso);
    } else if (!vacia) {
      zonaDeAvisos.querySelector('[data-semana-vacia]')?.remove();
    }
    dias.replaceChildren(...DIAS.map((_, i) => tarjetaDia(i + 1)));
    actualizarBarra();
  }

  function mostrarErrores(errores: ErrorDeDia[], titulo: string): void {
    erroresPorDia = errores;
    dibujarDias();
    const resumen = resumenDeErrores(titulo, errores.map((e) => ({ id: `dia-${e.diaSemana}`, mensaje: nombreDelDia(e.diaSemana) })));
    zonaDeAvisos.replaceChildren(resumen);
    resumen.focus();
  }

  formulario.addEventListener('submit', (evento) => {
    evento.preventDefault();
    void guardar();
  });

  async function guardar(): Promise<void> {
    const errores = validarGrilla(franjas);
    if (errores.length > 0) {
      const solapadas = errores.some((e) => e.mensaje.includes('se superponen'));
      return mostrarErrores(errores, solapadas ? 'No guardamos: hay franjas superpuestas' : 'No guardamos: revisá los horarios marcados');
    }
    if (franjas.length === 0 && opciones.exigirFranjas === true) {
      zonaDeAvisos.querySelector('[data-falta-franja]')?.remove();
      const aviso = avisoAuth({ tono: 'peligro', icono: 'alerta', titulo: 'Cargá al menos una franja para seguir.', cuerpo: ['Podés usar el atajo de lunes a viernes y después ajustarlo.'] });
      aviso.dataset['faltaFranja'] = '';
      zonaDeAvisos.prepend(aviso);
      return;
    }
    if (!conCambios() && franjas.length > 0) {
      opciones.alGuardar?.(ordenarSemana(franjas));
      return;
    }
    if (franjas.length === 0 && !(await confirmar('¿Guardar la semana sin horarios?', 'Tus pacientes no van a poder reservar desde tu link hasta que cargues horarios. Los turnos que ya tenés no cambian.', 'Guardar sin horarios'))) {
      return;
    }
    barra.guardando(true);
    try {
      const enviadas = ordenarSemana(franjas).map(({ diaSemana, horaInicio, horaFin }) => ({ diaSemana, horaInicio, horaFin }));
      await guardarGrilla(contexto.http, enviadas);
      guardadas = enviadas;
      franjas = [...enviadas];
      erroresPorDia = [];
      barra.guardando(false);
      zonaDeAvisos.replaceChildren();
      dibujarDias();
      anuncios.anunciar('Guardamos tus horarios');
      opciones.alGuardar?.(enviadas);
    } catch (error) {
      barra.guardando(false);
      const traducido = error instanceof ApiError ? traducirError(error) : null;
      if (traducido !== null && traducido.dias.length > 0) return mostrarErrores([...traducido.dias], 'No guardamos: hay franjas superpuestas');
      zonaDeAvisos.replaceChildren(avisoAuth({ tono: 'peligro', icono: 'alerta', titulo: traducido?.aviso ?? 'No pudimos guardar. Lo que editaste sigue acá: probá de nuevo.' }));
    }
  }

  void cargar();
  return { guardar };
}
