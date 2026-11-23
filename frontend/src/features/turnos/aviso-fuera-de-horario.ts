import { el } from '../../shared/dom';
import { formatearDia } from '../../shared/fechas';
import type { ClienteHttp } from '../../shared/http';
import { boton } from '../../shared/ui/boton';
import { nombreCompleto, type TurnoAgenda } from '../agenda/modelo';
import { rutaDelTurno } from '../agenda/tarjeta';
import { diaLocal, horaLocal, lunesDe } from '../agenda/tiempo';
import { avisoAuth, spinner } from '../auth/ui';
import { turnosFueraDeDisponibilidad } from './api';

/**
 * Aviso de turnos fuera de horario (C-14, design D10; decisión de Maxi en #15 y #34). Después de guardar los horarios
 * en Configuración, lista los turnos futuros activos que quedaron afuera (sin sobreturnos) con un link a cada uno. Es
 * información: no bloquea ni deshace el guardado, y si la consulta falla no aparece.
 */

const MAXIMO_VISIBLE = 5;

/** "Vie 20 · 16:00". */
function diaYHora(instante: string, zona: string): string {
  const [diaSemana = '', numero = ''] = formatearDia(instante, zona).split(' ');
  return `${diaSemana.charAt(0).toUpperCase()}${diaSemana.slice(1, 3)} ${numero} · ${horaLocal(instante, zona)}`;
}

function avisoDeTurnos(turnos: readonly TurnoAgenda[], zona: string, alCerrar: () => void): HTMLElement {
  const cantidad = turnos.length;
  const titulo = cantidad === 1 ? '1 turno queda fuera de tu horario nuevo' : `${cantidad} turnos quedan fuera de tu horario nuevo`;
  const lista = el(
    'ul',
    { class: 'list-none m-0 mt-2 p-0 grid gap-1' },
    ...turnos.slice(0, MAXIMO_VISIBLE).map((t) =>
      el(
        'li',
        { class: 'flex items-center justify-between gap-2 px-2 py-1 rounded-sm bg-superficie text-texto tabular' },
        el('span', { class: 'min-w-0' }, el('b', {}, diaYHora(t.inicio, zona)), ` · ${nombreCompleto(t.paciente)}`),
        ' ',
        el('a', { href: rutaDelTurno(t.id), 'data-link': true, class: 'inline-flex items-center min-h-touch font-semibold text-current underline whitespace-nowrap flex-none' }, 'Ver turno'),
      ),
    ),
  );
  const primero = turnos[0];
  const verTodos =
    cantidad > MAXIMO_VISIBLE && primero !== undefined
      ? el('a', { href: `/app/agenda?vista=semana&fecha=${lunesDe(diaLocal(primero.inicio, zona))}`, 'data-link': true, class: 'inline-flex items-center min-h-touch font-semibold text-current underline' }, `Ver los ${cantidad} en la agenda`)
      : null;
  const nodo = avisoAuth({
    tono: 'advertencia',
    icono: 'alerta',
    titulo,
    cuerpo: ['Siguen vigentes en tu agenda: reprogramalos o cancelalos desde cada turno. Los sobreturnos no se cuentan, porque pueden quedar fuera de horario.'],
  });
  nodo.dataset['avisoFuera'] = '';
  nodo.lastElementChild?.append(lista, el('div', { class: 'flex flex-wrap items-center gap-2 mt-2' }, verTodos, boton({ texto: 'Entendido', variante: 'fantasma', alHacerClic: alCerrar })));
  return nodo;
}

/** Revisa los turnos afuera y deja el aviso arriba de la grilla, en `zona`. */
export async function avisarTurnosFueraDeHorario(http: ClienteHttp, zonaHoraria: string, zona: HTMLElement): Promise<void> {
  const revisando = el('p', { 'data-revisando-fuera': true, role: 'status', class: 'flex items-center gap-2 m-0 mb-4 text-sm text-texto-tenue' }, spinner(), 'Estamos revisando si algún turno quedó afuera…');
  zona.prepend(revisando);
  try {
    const turnos = await turnosFueraDeDisponibilidad(http);
    revisando.remove();
    if (turnos.length === 0) return;
    const ordenados = [...turnos].sort((a, b) => Date.parse(a.inicio) - Date.parse(b.inicio));
    const nodo = avisoDeTurnos(ordenados, zonaHoraria, () => nodo.remove());
    zona.prepend(nodo);
  } catch {
    // Es información, no una condición del guardado (D10).
    revisando.remove();
  }
}
