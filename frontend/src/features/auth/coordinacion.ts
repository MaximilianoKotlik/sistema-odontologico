/**
 * Coordinación entre pestañas del mismo navegador (C-07):
 * - D6: el refresh se serializa con Web Locks, así la segunda pestaña usa la cookie ya rotada.
 * - D11: el cierre de sesión se avisa por `BroadcastChannel('muelas-auth')`, solo con el tipo y el motivo.
 * Nunca viaja el access token ni datos del usuario entre pestañas (D4).
 */

export const NOMBRE_LOCK = 'muelas-auth-refresh';
export const NOMBRE_CANAL = 'muelas-auth';

export type MotivoCierre = 'logout' | 'contrasena-cambiada';

export interface AvisoSesionCerrada {
  readonly tipo: 'sesion-cerrada';
  readonly motivo: MotivoCierre;
}

type LocksMinimos = { request<T>(nombre: string, tarea: () => Promise<T>): Promise<T> };

function locksDelNavegador(): LocksMinimos | null {
  if (typeof navigator === 'undefined' || !('locks' in navigator)) return null;
  const locks = navigator.locks;
  // `LockManager.request` tipa el resultado como Promise<Awaited<T>>; con una tarea async es lo mismo que Promise<T>.
  return { request: <T,>(nombre: string, tarea: () => Promise<T>) => locks.request(nombre, tarea) as Promise<T> };
}

export type { LocksMinimos };

/** Corre la tarea bajo el lock exclusivo del refresh; con `null` (navegador sin Web Locks), la corre directo. */
export function crearCoordinadorRefresh(locks: LocksMinimos | null = locksDelNavegador()): <T>(tarea: () => Promise<T>) => Promise<T> {
  if (locks === null) return (tarea) => tarea();
  return (tarea) => locks.request(NOMBRE_LOCK, tarea);
}

const MOTIVOS: readonly MotivoCierre[] = ['logout', 'contrasena-cambiada'];

export function esAvisoSesionCerrada(valor: unknown): valor is AvisoSesionCerrada {
  if (typeof valor !== 'object' || valor === null) return false;
  const registro = valor as Record<string, unknown>;
  return registro['tipo'] === 'sesion-cerrada' && MOTIVOS.some((motivo) => motivo === registro['motivo']);
}

export interface CanalMinimo {
  postMessage(mensaje: unknown): void;
  addEventListener(tipo: 'message', oyente: (evento: MessageEvent) => void): void;
  removeEventListener(tipo: 'message', oyente: (evento: MessageEvent) => void): void;
  close(): void;
}

export interface CanalSesion {
  publicar(motivo: MotivoCierre): void;
  alRecibir(oyente: (motivo: MotivoCierre) => void): () => void;
  cerrar(): void;
}

export type FabricaDeCanal = (nombre: string) => CanalMinimo;

function fabricaDelNavegador(): FabricaDeCanal | null {
  return typeof BroadcastChannel === 'undefined' ? null : (nombre) => new BroadcastChannel(nombre);
}

/** Canal del aviso de sesión cerrada. Con `null` (sin `BroadcastChannel`) no hace nada y no falla (D11). */
export function crearCanalSesion(fabrica: FabricaDeCanal | null = fabricaDelNavegador()): CanalSesion {
  const canal = fabrica === null ? undefined : fabrica(NOMBRE_CANAL);
  return {
    publicar(motivo) {
      const aviso: AvisoSesionCerrada = { tipo: 'sesion-cerrada', motivo };
      canal?.postMessage(aviso);
    },
    alRecibir(oyente) {
      if (canal === undefined) return () => undefined;
      const alMensaje = (evento: MessageEvent) => {
        const dato: unknown = evento.data;
        if (esAvisoSesionCerrada(dato)) oyente(dato.motivo);
      };
      canal.addEventListener('message', alMensaje);
      return () => canal.removeEventListener('message', alMensaje);
    },
    cerrar() {
      canal?.close();
    },
  };
}
