import { vi } from 'vitest';
import type { ContextoPagina } from '../../pages/pagina';
import { crearClienteHttp } from '../../shared/http';

/** Solo para tests: contexto de una página privada con un backend falso por ruta y método. Datos ficticios. */

export type Respuesta = Response | (() => Response | Promise<Response>);
export type Guion = Record<string, Respuesta[]>;

export interface Llamada {
  readonly metodo: string;
  readonly url: string;
  readonly cuerpo: unknown;
}

export const json = (cuerpo: unknown, status = 200) => () => new Response(JSON.stringify(cuerpo), { status, headers: { 'Content-Type': 'application/json' } });
export const problema = (status: number, codigo: string, extra: Record<string, unknown> = {}) => () =>
  new Response(JSON.stringify({ status, codigo, ...extra }), { status, headers: { 'Content-Type': 'application/problem+json' } });

export function crearContextoDePagina(guion: Guion) {
  const llamadas: Llamada[] = [];
  const fetch = vi.fn(async (entrada: RequestInfo | URL, init: RequestInit = {}) => {
    const url = String(entrada);
    const metodo = init.method ?? 'GET';
    llamadas.push({ metodo, url, cuerpo: typeof init.body === 'string' ? (JSON.parse(init.body) as unknown) : undefined });
    const siguiente = guion[`${metodo} ${url}`]?.shift();
    if (siguiente === undefined) throw new Error(`Sin respuesta preparada para ${metodo} ${url}`);
    return typeof siguiente === 'function' ? siguiente() : siguiente;
  });
  const http = crearClienteHttp({ fetch });
  http.establecerToken('token-ficticio');
  const contenedor = document.createElement('main');
  document.body.replaceChildren(contenedor);
  let bloqueo: (() => Promise<boolean>) | null = null;
  const contexto: ContextoPagina = {
    contenedor,
    acciones: document.createElement('div'),
    params: {},
    http,
    zonaHoraria: 'America/Argentina/Buenos_Aires',
    cuenta: { terminarPorCambioDeContrasena: vi.fn(async () => undefined) },
    navegacion: {
      navegar: vi.fn(async () => undefined),
      bloquearSalida: vi.fn((confirmar: () => Promise<boolean>) => {
        bloqueo = confirmar;
        return () => {
          if (bloqueo === confirmar) bloqueo = null;
        };
      }),
    },
  };
  return { contexto, contenedor, llamadas, bloqueada: () => bloqueo !== null };
}

export const esperar = () => new Promise((resolver) => setTimeout(resolver, 0));

export function escribir(input: HTMLInputElement | HTMLSelectElement | null, valor: string): void {
  if (input === null) throw new Error('No se encontró el campo');
  input.value = valor;
  input.dispatchEvent(new Event('input', { bubbles: true }));
  input.dispatchEvent(new Event('change', { bubbles: true }));
}

export function enviar(contenedor: HTMLElement): void {
  const form = contenedor.querySelector('form');
  if (form === null) throw new Error('No hay formulario');
  form.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
}

export const botonConTexto = (raiz: ParentNode, texto: string) =>
  [...raiz.querySelectorAll<HTMLButtonElement>('button')].find((b) => b.textContent?.trim() === texto) ?? null;
