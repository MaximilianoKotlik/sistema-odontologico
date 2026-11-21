import { el, idUnico, type Hijo } from '../../shared/dom';
import { clasesBoton } from '../../shared/ui/boton';
import { icono, type NombreIcono } from '../../shared/ui/iconos';
import type { EstadoRequisito, RequisitosContrasena } from './validaciones';

/**
 * Piezas de las pantallas de auth, traducidas del mockup aprobado (#14, `auth.css`) a los tokens vía Tailwind.
 * Todo el texto entra como nodos de texto; ningún dato del usuario pasa por HTML.
 */

export type TonoAviso = 'peligro' | 'info' | 'exito' | 'advertencia';

const CLASE_TONO: Record<TonoAviso, string> = {
  peligro: 'bg-peligro-fondo border-cancelado-bd text-cancelado-fg',
  info: 'bg-primario-tinte border-reservado-bd text-reservado-fg',
  exito: 'bg-confirmado-bg border-confirmado-bd text-confirmado-fg',
  advertencia: 'bg-sobreturno-bg border-sobreturno-bd text-sobreturno-fg',
};

export const CLASE_ENLACE = 'text-primario font-semibold underline underline-offset-2 hover:text-primario-hover';
const CLASE_ENLACE_AVISO = 'font-semibold underline underline-offset-2 text-current';

export function enlace(href: string, texto: string, clase = CLASE_ENLACE): HTMLAnchorElement {
  return el('a', { href, 'data-link': true, class: clase }, texto);
}

export function enlaceEnAviso(href: string, texto: string): HTMLAnchorElement {
  return enlace(href, texto, CLASE_ENLACE_AVISO);
}

export interface OpcionesAviso {
  readonly tono: TonoAviso;
  readonly icono: NombreIcono;
  readonly titulo?: string;
  readonly cuerpo?: readonly Hijo[];
  /** `alert` para errores del envío; `status` para avisos de llegada (design D10). */
  readonly rol?: 'alert' | 'status' | null;
}

export function avisoAuth(opciones: OpcionesAviso): HTMLDivElement {
  const texto = el(
    'div',
    {},
    opciones.titulo === undefined ? null : el('strong', { class: 'block text-base' }, opciones.titulo),
    opciones.cuerpo === undefined ? null : el('p', { class: 'm-0' }, ...opciones.cuerpo),
  );
  return el(
    'div',
    {
      role: opciones.rol === undefined ? 'alert' : opciones.rol,
      class: `flex gap-3 items-start px-4 py-3 mb-5 rounded-md border text-sm ${CLASE_TONO[opciones.tono]}`,
    },
    icono(opciones.icono, 'w-icono h-icono flex-none'),
    texto,
  );
}

export function spinner(): HTMLSpanElement {
  return el('span', {
    'aria-hidden': 'true',
    class:
      'inline-block flex-none w-icono-sm h-icono-sm rounded-full border-2 border-current border-r-transparent motion-safe:animate-spin motion-reduce:border-r-current motion-reduce:opacity-60',
  });
}

const CLASE_INPUT =
  'min-h-touch w-full px-3 font-sans text-base text-texto bg-superficie border-control border-borde-fuerte rounded-md ' +
  'focus-visible:border-primario aria-[invalid=true]:border-peligro aria-[invalid=true]:bg-peligro-fondo';

export interface Campo {
  readonly contenedor: HTMLDivElement;
  readonly input: HTMLInputElement;
  /** Muestra u oculta el error del campo (texto o nodos con links). */
  error(contenido: string | readonly Hijo[] | null): void;
}

export interface OpcionesCampoAuth {
  readonly etiqueta: string;
  readonly nombre: string;
  readonly tipo?: 'text' | 'email' | 'password';
  readonly autocompletar: string;
  readonly ayuda?: string;
  /** Contenido a la derecha de la etiqueta (p. ej. "¿La olvidaste?"). */
  readonly extraEtiqueta?: HTMLElement;
  /** Etiqueta accesible del botón de mostrar contraseña. */
  readonly etiquetaMostrar?: string;
}

export function campoAuth(opciones: OpcionesCampoAuth): Campo {
  const id = idUnico(`auth-${opciones.nombre}`);
  const idError = `${id}-error`;
  const idAyuda = `${id}-ayuda`;
  const esEmail = opciones.tipo === 'email';
  const esContrasena = opciones.tipo === 'password';
  const input = el('input', {
    id,
    name: opciones.nombre,
    type: opciones.tipo ?? 'text',
    autocomplete: opciones.autocompletar,
    inputmode: esEmail ? 'email' : null,
    autocapitalize: esEmail || esContrasena ? 'none' : null,
    spellcheck: esEmail || esContrasena ? 'false' : null,
    'aria-describedby': opciones.ayuda === undefined ? null : idAyuda,
    class: `${CLASE_INPUT}${esContrasena ? ' pr-touch' : ''}`,
  });

  const etiqueta = el('label', { for: id, class: 'font-medium text-sm' }, opciones.etiqueta);
  const filaEtiqueta =
    opciones.extraEtiqueta === undefined ? etiqueta : el('div', { class: 'flex justify-between items-baseline gap-2' }, etiqueta, opciones.extraEtiqueta);

  let control: HTMLElement = input;
  if (esContrasena) {
    const alternar = el(
      'button',
      {
        type: 'button',
        'aria-pressed': 'false',
        'aria-label': opciones.etiquetaMostrar ?? 'Mostrar contraseña',
        class: 'absolute right-0 top-0 grid place-items-center w-touch min-h-touch h-full bg-transparent border-0 rounded-md text-texto-tenue cursor-pointer',
      },
      icono('ojo'),
    );
    alternar.addEventListener('click', () => {
      const mostrar = input.type === 'password';
      input.type = mostrar ? 'text' : 'password';
      alternar.setAttribute('aria-pressed', String(mostrar));
    });
    control = el('div', { class: 'relative' }, input, alternar);
  }

  const mensaje = el('p', { id: idError, hidden: true, class: 'm-0 flex gap-1 items-start text-sm font-medium text-peligro' });
  const ayuda = opciones.ayuda === undefined ? null : el('p', { id: idAyuda, class: 'm-0 text-sm text-texto-tenue' }, opciones.ayuda);
  const contenedor = el('div', { class: 'grid gap-1 mb-4' }, filaEtiqueta, control, ayuda, mensaje);

  function describir(conError: boolean): void {
    const ids = (input.getAttribute('aria-describedby') ?? '').split(' ').filter((v) => v !== '' && v !== idError);
    if (conError) ids.push(idError);
    if (ids.length === 0) input.removeAttribute('aria-describedby');
    else input.setAttribute('aria-describedby', ids.join(' '));
  }

  return {
    contenedor,
    input,
    error(contenido) {
      if (contenido === null) {
        mensaje.hidden = true;
        mensaje.replaceChildren();
        input.removeAttribute('aria-invalid');
        describir(false);
        return;
      }
      const nodos = typeof contenido === 'string' ? [contenido] : contenido;
      mensaje.replaceChildren(icono('alerta', 'w-icono-sm h-icono-sm flex-none mt-1'), el('span', {}, ...nodos));
      mensaje.hidden = false;
      input.setAttribute('aria-invalid', 'true');
      describir(true);
    },
  };
}

/** Marca un campo como inválido sin mensaje propio (p. ej. cuando el error va en el resumen o en los requisitos). */
export function marcarInvalido(input: HTMLInputElement, invalido: boolean): void {
  if (invalido) input.setAttribute('aria-invalid', 'true');
  else input.removeAttribute('aria-invalid');
}

export interface BotonEnvio {
  readonly boton: HTMLButtonElement;
  enviando(activo: boolean): void;
  bloquear(motivo: string, bloqueado: boolean): void;
}

/** Botón de envío que cambia a "Enviando…" con spinner; puede bloquearse por varios motivos (red, 429). */
export function botonEnvio(texto: string, textoEnviando: string, clasesExtra = 'w-full'): BotonEnvio {
  const boton = el('button', { type: 'submit', class: `${clasesBoton('primario')} ${clasesExtra}` }, texto);
  const motivos = new Set<string>();
  let enviandoAhora = false;
  const actualizar = () => {
    boton.disabled = enviandoAhora || motivos.size > 0;
  };
  return {
    boton,
    enviando(activo) {
      enviandoAhora = activo;
      boton.setAttribute('aria-live', 'polite');
      if (activo) boton.replaceChildren(spinner(), textoEnviando);
      else boton.replaceChildren(texto);
      actualizar();
    },
    bloquear(motivo, bloqueado) {
      if (bloqueado) motivos.add(motivo);
      else motivos.delete(motivo);
      actualizar();
    },
  };
}

const TEXTO_ESTADO: Record<EstadoRequisito, string> = { pendiente: '(pendiente)', cumplido: '(cumplido)', fallado: '(falta)' };
const ICONO_ESTADO: Record<EstadoRequisito, NombreIcono> = { pendiente: 'punto', cumplido: 'check', fallado: 'cruz' };
const CLASE_ESTADO: Record<EstadoRequisito, string> = { pendiente: 'text-texto-tenue', cumplido: 'text-exito', fallado: 'text-peligro font-medium' };

export interface ListaRequisitos {
  readonly elemento: HTMLUListElement;
  actualizar(requisitos: RequisitosContrasena): void;
}

/**
 * Requisitos de RN-AU-01 con ícono + texto + estado para lectores de pantalla, nunca solo color (D10).
 * `textoEmail` cambia según la pantalla; con `null` en el estado queda como indicación fija.
 */
export function listaRequisitos(id: string, textoEmail: string): ListaRequisitos {
  const elemento = el('ul', { id, 'aria-label': 'Requisitos de la contraseña', class: 'list-none mt-1 mb-0 p-0 grid gap-1 text-sm' });
  const item = (texto: string, estado: EstadoRequisito) =>
    el(
      'li',
      { 'data-estado': estado, class: `flex gap-2 items-center ${CLASE_ESTADO[estado]}` },
      icono(ICONO_ESTADO[estado], 'w-icono-sm h-icono-sm flex-none'),
      texto,
      el('span', { class: 'sr-only' }, ` ${TEXTO_ESTADO[estado]}`),
    );
  return {
    elemento,
    actualizar(requisitos) {
      elemento.replaceChildren(
        item('Al menos 10 caracteres', requisitos.largoMinimo),
        item(textoEmail, requisitos.distintaDelEmail ?? 'pendiente'),
        requisitos.hasta72Bytes === null ? '' : item('Hasta 72 caracteres (las letras con tilde y la ñ cuentan doble)', 'fallado'),
      );
    },
  };
}

/** Pantalla de resultado centrada (link enviado, link vencido). */
export function resultado(opciones: {
  readonly icono: NombreIcono;
  readonly tono: 'info' | 'peligro';
  readonly titulo: string;
  readonly parrafos: readonly (readonly Hijo[])[];
  readonly acciones: readonly HTMLElement[];
  readonly rol: 'status' | 'alert';
}): HTMLDivElement {
  const claseIcono = opciones.tono === 'info' ? 'bg-primario-tinte text-primario' : 'bg-peligro-fondo text-peligro';
  return el(
    'div',
    { role: opciones.rol, class: 'text-center' },
    el('div', { 'aria-hidden': 'true', class: `grid place-items-center w-7 h-7 mx-auto mb-4 rounded-full ${claseIcono}` }, icono(opciones.icono)),
    el('h1', { class: 'mt-0 mb-2 text-2xl' }, opciones.titulo),
    ...opciones.parrafos.map((p) => el('p', { class: 'mx-auto mt-0 mb-4 max-w-lectura text-texto-tenue' }, ...p)),
    el('div', { class: 'grid gap-2' }, ...opciones.acciones),
  );
}

export function enlaceBotonBloque(href: string, texto: string, variante: 'primario' | 'secundario' | 'fantasma' = 'primario'): HTMLAnchorElement {
  return el('a', { href, 'data-link': true, class: `${clasesBoton(variante)} w-full` }, texto);
}

/** Encabezado de una tarjeta de auth: un solo h1 y el texto de apoyo. */
export function encabezado(titulo: string, apoyo: string): HTMLElement[] {
  return [el('h1', { class: 'mt-0 mb-2 text-2xl' }, titulo), el('p', { class: 'mt-0 mb-5 text-texto-tenue' }, apoyo)];
}

export function parrafoAlternativo(...hijos: Hijo[]): HTMLParagraphElement {
  return el('p', { class: 'mt-5 mb-0 text-center text-texto-tenue' }, ...hijos);
}
