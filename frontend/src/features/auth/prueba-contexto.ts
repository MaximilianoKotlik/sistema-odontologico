import { vi } from 'vitest';
import { crearStore } from '../../shared/store';
import type { EstadoConectividad } from '../../shared/conectividad';
import { crearClienteHttp } from '../../shared/http';
import type { AvisoLogin, ContextoAuth } from './contexto';

/** Solo para tests: contexto de una página de auth con un fetch falso que responde según la ruta. */
export type Guion = Record<string, (() => Response | Promise<Response>)[]>;

export interface Llamada {
  readonly url: string;
  readonly cuerpo: unknown;
}

export function crearContextoDePrueba(guion: Guion = {}, extra: { aviso?: AvisoLogin; query?: string; online?: boolean } = {}) {
  const llamadas: Llamada[] = [];
  const fetch = vi.fn(async (entrada: RequestInfo | URL, init: RequestInit = {}) => {
    const url = String(entrada);
    llamadas.push({ url, cuerpo: typeof init.body === 'string' ? (JSON.parse(init.body) as unknown) : undefined });
    const siguiente = guion[url]?.shift();
    if (siguiente === undefined) throw new Error(`Sin respuesta preparada para ${url}`);
    return siguiente();
  });
  const contenedor = document.createElement('div');
  document.body.replaceChildren(contenedor);
  let aviso: AvisoLogin | null = extra.aviso ?? null;
  const conectividad = crearStore<EstadoConectividad>({ online: extra.online ?? true, ultimaSincronizacion: null });
  const contexto: ContextoAuth = {
    contenedor,
    http: crearClienteHttp({ fetch }),
    marca: 'Muelas Felices',
    query: new URLSearchParams(extra.query ?? ''),
    conectividad,
    navegar: vi.fn(async () => undefined),
    entrar: vi.fn(async () => undefined),
    tomarAviso: () => {
      const actual = aviso;
      aviso = null;
      return actual;
    },
    dejarAviso: vi.fn((nuevo: AvisoLogin) => {
      aviso = nuevo;
    }),
  };
  return { contexto, contenedor, llamadas, conectividad };
}

export const respuestaJson = (cuerpo: unknown, status = 200, encabezados: Record<string, string> = {}) => () =>
  new Response(JSON.stringify(cuerpo), { status, headers: { 'Content-Type': 'application/json', ...encabezados } });

export const problemaJson = (status: number, codigo: string, extra: Record<string, unknown> = {}, encabezados: Record<string, string> = {}) => () =>
  new Response(JSON.stringify({ status, codigo, ...extra }), { status, headers: { 'Content-Type': 'application/problem+json', ...encabezados } });

export const sinContenido = () => () => new Response(null, { status: 204 });

/** Escribe en un input como lo haría el usuario (dispara `input`). */
export function escribir(input: HTMLInputElement | null, valor: string): void {
  if (input === null) throw new Error('No se encontró el campo');
  input.value = valor;
  input.dispatchEvent(new Event('input', { bubbles: true }));
}

export function enviar(contenedor: HTMLElement): void {
  const form = contenedor.querySelector('form');
  if (form === null) throw new Error('No hay formulario');
  form.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
}

export const esperarPedidos = () => new Promise((resolver) => setTimeout(resolver, 0));
