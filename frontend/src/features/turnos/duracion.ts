import { el } from '../../shared/dom';
import { aviso } from '../../shared/ui/aviso';
import { clasesBoton } from '../../shared/ui/boton';
import { icono } from '../../shared/ui/iconos';
import type { DetalleTurno } from '../agenda/api';
import type { EntornoDeAccion } from '../agenda/puntos-de-entrada';
import { horaLocal } from '../agenda/tiempo';
import { CLASE_CHIP } from '../configuracion/ui';
import { ajustarDuracion as pedirAjuste } from './api';
import { traducirErrorDeTurno } from './errores';
import { abrirFormulario } from './formulario';
import { avisoDeError, datosDelDia, diaDelTurno, referenciaDelTurno } from './ui';
import { DURACION_MAXIMA, DURACION_MINIMA, PASO_MINUTOS, ajustarDuracion } from './validaciones';

/** Ajustar duración (C-14, US-024; design D7): −5 / +5, atajos y el fin nuevo en vivo. */

const ATAJOS = [15, 20, 30, 45, 60] as const;
/** Códigos que se explican mejor con la agenda del día (con qué turno o bloqueo choca). */
const CON_AGENDA = new Set(['TURNO_SOLAPADO', 'HORARIO_BLOQUEADO', 'FUERA_DE_DISPONIBILIDAD']);

export function abrirDuracion(turno: DetalleTurno, entorno: EntornoDeAccion): void {
  const { zona } = entorno;
  let minutos = turno.duracionMin;
  const finCon = (duracion: number) => horaLocal(new Date(Date.parse(turno.inicio) + duracion * 60000), zona);

  const valor = el('span', {});
  const fin = el('small', { class: 'block text-sm font-normal text-texto-tenue' });
  const salida = el('output', { 'aria-live': 'polite', class: 'text-center text-2xl font-bold tabular' }, valor, ' ', fin);
  const paso = (delta: number, etiqueta: string, nombre: 'mas' | 'menos') => {
    const b = el('button', { type: 'button', 'aria-label': etiqueta, class: `${clasesBoton('secundario')} w-touch px-0 text-xl` }, nombre === 'mas' ? icono('mas', 'w-icono h-icono flex-none') : el('span', { 'aria-hidden': 'true' }, '−'));
    b.addEventListener('click', () => elegir(ajustarDuracion(minutos, delta)));
    return b;
  };
  const menos = paso(-PASO_MINUTOS, 'Restar 5 minutos', 'menos');
  const mas = paso(PASO_MINUTOS, 'Sumar 5 minutos', 'mas');
  const atajos = ATAJOS.map((m) => {
    const b = el('button', { type: 'button', 'aria-pressed': 'false', 'aria-label': `${m} minutos`, class: `${CLASE_CHIP} justify-center min-w-touch tabular` }, String(m));
    b.addEventListener('click', () => elegir(m));
    return { m, b };
  });

  function elegir(nuevo: number): void {
    minutos = nuevo;
    valor.textContent = `${minutos} min`;
    fin.textContent = `Termina ${finCon(minutos)}`;
    menos.disabled = minutos <= DURACION_MINIMA;
    mas.disabled = minutos >= DURACION_MAXIMA;
    for (const { m, b } of atajos) b.setAttribute('aria-pressed', String(m === minutos));
  }

  abrirFormulario({
    titulo: 'Ajustar duración',
    contenido: el(
      'div',
      {},
      referenciaDelTurno(turno, zona),
      el('div', { class: 'grid grid-cols-[var(--touch-min)_1fr_var(--touch-min)] items-center gap-3 max-w-xs mx-auto' }, menos, salida, mas),
      el('ul', { 'aria-label': 'Duraciones frecuentes', class: 'flex flex-wrap justify-center gap-2 list-none m-0 mt-3 p-0' }, ...atajos.map(({ b }) => el('li', {}, b))),
      el('p', { class: 'm-0 mt-3 text-center text-sm text-texto-tenue tabular' }, `Antes: ${turno.duracionMin} min (${horaLocal(turno.inicio, zona)} a ${horaLocal(turno.fin, zona)}). En pasos de 5 minutos, de 5 a 480.`),
    ),
    textoGuardar: 'Guardar duración',
    textoCargando: 'Guardando…',
    conCambios: () => minutos !== turno.duracionMin,
    ejecutar: async () => {
      if (minutos !== turno.duracionMin) {
        await pedirAjuste(entorno.http, turno.id, { duracionMin: minutos, version: turno.version ?? 0 });
        aviso(`Ahora el turno dura ${minutos} min y termina a las ${finCon(minutos)}`, { tono: 'exito' });
        entorno.alTerminar();
      }
    },
    alFallar: async (error, { zonaDeAviso, ventana, reintentar }) => {
      const dia = diaDelTurno(turno, zona);
      const datos = CON_AGENDA.has(error.codigo) ? await datosDelDia(entorno.http, dia, zona) : { turnos: [], bloqueos: [], franjas: null };
      const traducido = traducirErrorDeTurno(error, { operacion: 'duracion', zona, dia, ...datos, duracion: { nueva: minutos, anterior: turno.duracionMin, finNuevo: finCon(minutos) } });
      zonaDeAviso.replaceChildren(
        avisoDeError(traducido, {
          alReintentar: reintentar,
          alVerActualizado: () => {
            ventana.cerrar();
            entorno.alTerminar();
          },
        }),
      );
    },
  });
  elegir(minutos);
}
