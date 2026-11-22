import { el } from '../../shared/dom';
import { ApiError, type ClienteHttp } from '../../shared/http';
import { boton } from '../../shared/ui/boton';
import { esqueleto } from '../../shared/ui/esqueleto';
import { icono } from '../../shared/ui/iconos';
import { obtenerHistorial, type EventoAuditoria } from './api';
import { actorDelEvento, cambiosDelEvento, iconoDelEvento, instanteCorto, textoEnElTurno } from './catalogo';
import { listaDeCambios } from './vista-evento';

/**
 * Tarjeta "Historial" del detalle del turno (C-18, design D8; mockup `turno-historial.html`). Se carga por su cuenta:
 * si falla, el resto del detalle se ve igual.
 */

function quienYCuando(evento: EventoAuditoria, zona: string): string {
  const actor = actorDelEvento(evento.actor);
  return `${actor.etiqueta === 'Paciente' ? (actor.nombre ?? actor.etiqueta) : actor.etiqueta} · ${instanteCorto(evento.ocurridoAt, zona)}`;
}

function fila(evento: EventoAuditoria, zona: string, ultima: boolean): HTMLLIElement {
  const tono = evento.actor.tipo === 'PACIENTE' ? 'bg-reservado-bg text-reservado-fg' : 'bg-primario-tinte text-primario';
  return el(
    'li',
    { class: `relative grid grid-cols-[var(--space-6)_minmax(0,1fr)] gap-3 ${ultima ? '' : 'pb-4 before:absolute before:left-[calc(var(--space-6)/2-1px)] before:top-6 before:bottom-0 before:w-[2px] before:bg-borde'}` },
    el('span', { 'aria-hidden': 'true', class: `grid place-items-center w-6 h-6 rounded-full ${tono}` }, icono(iconoDelEvento(evento), 'w-4 h-4')),
    el(
      'div',
      { class: 'min-w-0' },
      el('span', { class: 'block font-semibold' }, textoEnElTurno(evento)),
      el('span', { class: 'block text-sm text-texto-tenue tabular' }, quienYCuando(evento, zona)),
      listaDeCambios(cambiosDelEvento(evento, zona)),
    ),
  );
}

export function tarjetaHistorial(http: ClienteHttp, turnoId: string, zona: string): HTMLElement {
  const cuerpo = el('div', { class: 'mt-3' });

  async function cargar(): Promise<void> {
    cuerpo.replaceChildren(esqueleto({ etiqueta: 'Cargando el historial…', filas: 2 }));
    try {
      const eventos = await obtenerHistorial(http, turnoId);
      cuerpo.replaceChildren(
        el('ol', { class: 'list-none m-0 p-0' }, ...eventos.map((evento, i) => fila(evento, zona, i === eventos.length - 1))),
        el(
          'p',
          { class: 'mt-4 mb-0 pt-3 border-t border-borde' },
          el(
            'a',
            { href: `/app/auditoria?turnoId=${encodeURIComponent(turnoId)}`, 'data-link': true, class: 'inline-flex items-center gap-1 min-h-touch font-semibold text-primario no-underline' },
            icono('flecha', 'w-4 h-4 flex-none'),
            'Ver en el registro de cambios',
          ),
        ),
      );
    } catch (error) {
      if (error instanceof ApiError && error.codigo === 'SESION_VENCIDA') return;
      cuerpo.replaceChildren(
        el(
          'div',
          { role: 'alert', class: 'flex gap-3 items-start px-4 py-3 rounded-md border border-cancelado-bd bg-peligro-fondo text-texto' },
          icono('alerta', 'w-icono h-icono flex-none text-peligro'),
          el(
            'div',
            {},
            el('strong', { class: 'block' }, 'No pudimos cargar el historial'),
            el('p', { class: 'mt-0 mb-2 text-sm' }, 'El resto del turno se ve igual.'),
            boton({ texto: 'Reintentar', icono: 'reintentar', variante: 'secundario', alHacerClic: () => void cargar() }),
          ),
        ),
      );
    }
  }

  void cargar();
  return el(
    'section',
    { 'data-historial': true, class: 'bg-superficie border border-borde rounded-lg shadow-sm p-4 md:p-5' },
    el('h3', { class: 'm-0 text-lg font-bold' }, 'Historial'),
    el('p', { class: 'm-0 mt-1 text-sm text-texto-tenue' }, 'Cada cambio de este turno. Nadie puede editarlo.'),
    cuerpo,
  );
}
