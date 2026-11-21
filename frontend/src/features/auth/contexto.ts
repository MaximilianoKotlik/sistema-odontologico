import type { EstadoConectividad } from '../../shared/conectividad';
import type { ClienteHttp } from '../../shared/http';
import type { Store } from '../../shared/store';

/**
 * Contrato de las páginas públicas de auth (C-07, design D2): se montan en el layout de auth, sin shell.
 * El aviso de llegada al login vive en memoria (no en la URL ni en el historial): puede llevar el email.
 */

export type AvisoLogin =
  | { readonly tipo: 'sesion-cerrada' }
  | { readonly tipo: 'sesion-vencida' }
  | { readonly tipo: 'contrasena-cambiada' }
  | { readonly tipo: 'registro-sin-login'; readonly email: string };

export interface ContextoAuth {
  readonly contenedor: HTMLElement;
  readonly http: ClienteHttp;
  readonly marca: string;
  readonly query: URLSearchParams;
  readonly conectividad: Store<EstadoConectividad>;
  navegar(ruta: string, opciones?: { reemplazar?: boolean }): Promise<void>;
  /** Guarda el token en memoria y abre el destino saneado (D4, D5). */
  entrar(accessToken: string, volver: string | null): Promise<void>;
  tomarAviso(): AvisoLogin | null;
  dejarAviso(aviso: AvisoLogin): void;
}

export interface PaginaAuth {
  readonly titulo: string;
  /** Muestra el panel de marca a la izquierda en desktop (login y registro). */
  readonly conPanel?: boolean;
  /** Devuelve una limpieza que corre al salir de la pantalla (p. ej. vaciar contraseñas, D4), o null. */
  montar(contexto: ContextoAuth): (() => void) | null;
}
