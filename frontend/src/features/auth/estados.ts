import type { Hijo } from '../../shared/dom';
import { el } from '../../shared/dom';
import type { EstadoConectividad } from '../../shared/conectividad';
import { ApiError } from '../../shared/http';
import type { Store } from '../../shared/store';
import { mensajeDeError, segundosDeEspera, textoDeEspera, type PantallaAuth } from './errores';
import { avisoAuth, type BotonEnvio } from './ui';

/** Comportamientos que comparten todas las pantallas de auth: límite de intentos (429) y falta de red (D8). */

const CUERPO_LIMITE: Record<PantallaAuth, (espera: HTMLElement) => Hijo[]> = {
  login: (espera) => ['Esperá ', espera, ' y volvé a intentar.'],
  registro: (espera) => ['Probá de nuevo en ', espera, '.'],
  recuperar: (espera) => ['Usá el último que te llegó o probá de nuevo en ', espera, '.'],
  'recuperar-confirmar': (espera) => ['Esperá ', espera, ' y volvé a intentar.'],
  'cambiar-contrasena': (espera) => ['Esperá ', espera, ' y volvé a intentar.'],
};

/**
 * Aviso del 429 con la espera de `Retry-After`; deshabilita el envío hasta que pasa (spec "Límite de intentos").
 * Sin `Retry-After` válido, espera un minuto.
 */
export function avisoDeLimite(error: ApiError, pantalla: PantallaAuth, envio: BotonEnvio, ahora: Date = new Date()): HTMLDivElement {
  const segundos = segundosDeEspera(error.retryAfter, ahora) ?? 60;
  envio.bloquear('limite', true);
  setTimeout(() => envio.bloquear('limite', false), segundos * 1000);
  return avisoAuth({
    tono: 'advertencia',
    icono: 'reloj',
    titulo: mensajeDeError('DEMASIADAS_SOLICITUDES', pantalla).texto,
    cuerpo: CUERPO_LIMITE[pantalla](el('b', {}, textoDeEspera(segundos))),
  });
}

export function avisoSinConexion(cuerpo: string): HTMLDivElement {
  return avisoAuth({ tono: 'advertencia', icono: 'sin-conexion', titulo: 'Sin conexión.', cuerpo: [cuerpo], rol: 'status' });
}

export function avisoInesperado(pantalla: PantallaAuth): HTMLDivElement {
  return avisoAuth({ tono: 'peligro', icono: 'alerta', titulo: mensajeDeError('ERROR_INESPERADO', pantalla).texto });
}

/**
 * Refleja la conectividad en un contenedor de aviso y en el botón de envío. Devuelve la desuscripción.
 */
export function vigilarConexion(conectividad: Store<EstadoConectividad>, zona: HTMLElement, envio: BotonEnvio, cuerpo: string): () => void {
  const aviso = avisoSinConexion(cuerpo);
  const aplicar = ({ online }: Readonly<EstadoConectividad>) => {
    envio.bloquear('sin-conexion', !online);
    if (!online && !zona.contains(aviso)) zona.prepend(aviso);
    if (online) aviso.remove();
  };
  aplicar(conectividad.get());
  return conectividad.suscribir(aplicar);
}

export const esApiError = (valor: unknown): valor is ApiError => valor instanceof ApiError;
