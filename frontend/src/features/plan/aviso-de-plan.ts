import { el, type Hijo } from '../../shared/dom';
import { formatearDia } from '../../shared/fechas';
import { clasesBoton } from '../../shared/ui/boton';
import { icono, type NombreIcono } from '../../shared/ui/iconos';
import type { EstadoDelPlan, EstadoPlan } from './estado-del-plan';

/**
 * Aviso de plan del shell (C-20, US-061, design D7), traducido del mockup aprobado en #53 (`aviso-de-plan.html`) a los
 * tokens vía Tailwind. Va debajo del encabezado y arriba del contenido de todas las pantallas privadas.
 *
 * - Prueba con 8 días o más: discreto (info). Con 7 o menos: destacado (advertencia). El último día: "termina hoy".
 * - Vencido: persistente, en advertencia y no en peligro (no se perdió nada), con "Exportar mis datos".
 * - Peligro solo para el 403 de una escritura que no se guardó (decisión de Maxi en #53).
 */

/** Contacto para pedir el pase a `ACTIVO`: `(TBD)` hasta que haya dominio (DD-34, decisión de Maxi en #53). */
export const CONTACTO_ACTIVACION = '(TBD)@example.com';
export const ID_AVISO_SOLO_LECTURA = 'aviso-plan-solo-lectura';

const DIAS_DESTACADOS = 7;
const RUTA_PRECIOS = '/precios';
const RUTA_EXPORTAR = '/app/exportar';

type Tono = 'info' | 'advertencia' | 'peligro';

const CLASE_TONO: Record<Tono, string> = {
  info: 'bg-primario-tinte border-reservado-bd text-reservado-fg',
  advertencia: 'bg-sobreturno-bg border-sobreturno-bd text-sobreturno-fg',
  peligro: 'bg-peligro-fondo border-cancelado-bd text-cancelado-fg',
};
const CLASE_AVISO = 'flex gap-3 items-start px-4 py-3 rounded-md border text-sm';
const CLASE_ENLACE = 'font-semibold underline underline-offset-2 text-current min-h-touch inline-flex items-center';

export interface OpcionesAvisoDePlan {
  readonly estado: EstadoDelPlan;
  readonly zonaHoraria: () => string;
}

export interface AvisoDePlan {
  /** Región `role="status"` que el shell ubica arriba del contenido. */
  readonly elemento: HTMLElement;
  /** Muestra el error de una escritura rechazada con 403 `PLAN_VENCIDO` (role="alert"). */
  mostrarEscrituraRechazada(): void;
  destruir(): void;
}

function caja(tono: Tono, nombreIcono: NombreIcono, ...contenido: Hijo[]): HTMLDivElement {
  return el(
    'div',
    { 'data-aviso-plan': true, 'data-tono': tono, class: `${CLASE_AVISO} ${CLASE_TONO[tono]}` },
    icono(nombreIcono, 'w-icono-sm h-icono-sm flex-none mt-1'),
    el('div', { class: 'grid gap-1 min-w-0' }, ...contenido),
  );
}

function acciones(...enlaces: HTMLAnchorElement[]): HTMLDivElement {
  return el('div', { class: 'actions flex flex-wrap gap-2 mt-2' }, ...enlaces);
}

const verPrecios = (variante: 'secundario' | 'enlace') =>
  el('a', { href: RUTA_PRECIOS, class: variante === 'enlace' ? CLASE_ENLACE : clasesBoton('secundario') }, 'Ver precios');
const comoActivar = () => el('a', { href: `mailto:${CONTACTO_ACTIVACION}`, class: clasesBoton('fantasma') }, 'Cómo activar el plan');
const despues = 'la agenda queda en solo lectura: vas a poder ver y exportar todo, y no se borra nada.';

function contenidoDe(estado: EstadoPlan, zona: string): HTMLElement | null {
  if (estado.soloLectura) {
    const nodo = caja(
      'advertencia',
      'candado',
      el('strong', { class: 'text-texto' }, 'Tu prueba terminó: la agenda está en solo lectura'),
      el(
        'p',
        { class: 'm-0' },
        'Podés ver tus turnos y exportar todos tus datos cuando quieras. No se borra nada. Para volver a cargar turnos, pedinos la activación del plan escribiendo a ',
        el('a', { href: `mailto:${CONTACTO_ACTIVACION}`, class: CLASE_ENLACE }, CONTACTO_ACTIVACION),
        '.',
      ),
      acciones(
        el('a', { href: RUTA_EXPORTAR, 'data-link': true, class: clasesBoton('primario') }, icono('descargar', 'w-icono-sm h-icono-sm flex-none'), 'Exportar mis datos'),
        verPrecios('secundario'),
      ),
    );
    nodo.id = ID_AVISO_SOLO_LECTURA;
    return nodo;
  }
  const dias = estado.diasDePruebaRestantes;
  if (estado.plan !== 'PRUEBA' || dias === null || estado.pruebaHasta === null) return null;
  const fecha = formatearDia(estado.pruebaHasta, zona);
  if (dias === 1) {
    return caja(
      'advertencia',
      'reloj',
      el('strong', { class: 'text-texto' }, 'Tu prueba termina hoy'),
      el('p', { class: 'm-0' }, `Desde mañana ${despues}`),
      acciones(verPrecios('secundario'), comoActivar()),
    );
  }
  if (dias <= DIAS_DESTACADOS) {
    return caja(
      'advertencia',
      'reloj',
      el('strong', { class: 'text-texto' }, `Te quedan ${dias} días de prueba`),
      el('p', { class: 'm-0' }, `Tu prueba termina el ${fecha}. Después, ${despues}`),
      acciones(verPrecios('secundario'), comoActivar()),
    );
  }
  return caja(
    'info',
    'reloj',
    el('p', { class: 'm-0' }, el('strong', { class: 'text-texto' }, 'Prueba gratis'), ` · Te quedan ${dias} días, hasta el ${fecha}. `, verPrecios('enlace')),
  );
}

export function crearAvisoDePlan({ estado, zonaHoraria }: OpcionesAvisoDePlan): AvisoDePlan {
  const elemento = el('div', { 'data-region-plan': true, role: 'status', hidden: true, class: 'grid gap-2 px-4 pt-3 md:px-6' });
  let error: HTMLElement | null = null;

  function pintar(): void {
    const actual = estado.get();
    const contenido = actual === null ? null : contenidoDe(actual, zonaHoraria());
    elemento.replaceChildren(...[error, contenido].filter((n): n is HTMLElement => n !== null));
    elemento.hidden = elemento.childElementCount === 0;
  }

  const desuscribir = estado.suscribir(pintar);
  pintar();

  return {
    elemento,
    mostrarEscrituraRechazada() {
      error = el(
        'div',
        { role: 'alert', 'data-tono': 'peligro', class: `${CLASE_AVISO} ${CLASE_TONO.peligro}` },
        icono('alerta', 'w-icono-sm h-icono-sm flex-none mt-1'),
        el(
          'div',
          { class: 'grid gap-1' },
          el('strong', { class: 'text-texto' }, 'No se guardó el cambio'),
          el('p', { class: 'm-0' }, 'Tu prueba terminó y la agenda pasó a solo lectura. No se perdió nada de lo que ya tenías.'),
        ),
      );
      pintar();
    },
    destruir() {
      desuscribir();
      elemento.remove();
    },
  };
}
