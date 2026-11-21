import { CACHE_AGENDA } from '../pwa/opciones';
import { crearStore, type Store } from './store';

/** Estado de red del dispositivo y hora de la última agenda recibida (design D3, D6). */
export interface EstadoConectividad {
  readonly online: boolean;
  /** Instante ISO (UTC) del encabezado `Date` de la última respuesta de la agenda. */
  readonly ultimaSincronizacion: string | null;
}

export function crearConectividad(ventana: Window): { store: Store<EstadoConectividad>; detener: () => void } {
  const store = crearStore<EstadoConectividad>({ online: ventana.navigator.onLine, ultimaSincronizacion: null });
  const alConectar = () => store.set({ online: true });
  const alDesconectar = () => store.set({ online: false });
  ventana.addEventListener('online', alConectar);
  ventana.addEventListener('offline', alDesconectar);
  return {
    store,
    detener: () => {
      ventana.removeEventListener('online', alConectar);
      ventana.removeEventListener('offline', alDesconectar);
    },
  };
}

/**
 * Registra la hora de la agenda mostrada. Si la respuesta vino de la caché del Service Worker,
 * su encabezado `Date` es el de cuando se guardó: esa es la hora que ve el odontólogo offline.
 */
export function registrarSincronizacion(store: Store<EstadoConectividad>, ruta: string, respuesta: Response): void {
  const path = ruta.split('?')[0] ?? '';
  if (path !== '/api/v1/agenda' && !path.startsWith('/api/v1/agenda/')) return;
  const encabezado = respuesta.headers.get('Date');
  if (encabezado === null) return;
  const fecha = new Date(encabezado);
  if (Number.isNaN(fecha.getTime())) return;
  store.set({ ultimaSincronizacion: fecha.toISOString() });
}

/** Borra la agenda guardada por el Service Worker (al cerrar sesión, spec pwa-offline). */
export async function borrarCacheAgenda(almacen: Pick<CacheStorage, 'delete'> | undefined = globalThis.caches): Promise<void> {
  if (almacen === undefined) return;
  await almacen.delete(CACHE_AGENDA);
}
