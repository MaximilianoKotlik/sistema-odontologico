/**
 * Código de error → mensaje y campo (C-07, design D8). Los textos salen de los mockups aprobados (#14, `3ffa2f7`).
 * Nunca se muestra el código crudo ni se confirma si un email tiene cuenta, salvo `EMAIL_EN_USO` (RN-AU-03).
 */

export type PantallaAuth = 'login' | 'registro' | 'recuperar' | 'recuperar-confirmar' | 'cambiar-contrasena';

export interface MensajeDeError {
  /** Campo del formulario donde se muestra, o `null` para un aviso arriba del formulario. */
  readonly campo: string | null;
  readonly texto: string;
}

const TEXTO_GENERICO = 'No pudimos completar el pedido. Probá de nuevo en unos minutos.';

const LIMITE_POR_PANTALLA: Record<PantallaAuth, string> = {
  login: 'Demasiados intentos desde esta conexión.',
  registro: 'Se crearon varias cuentas desde esta conexión.',
  recuperar: 'Ya pediste varios links.',
  'recuperar-confirmar': 'Hiciste varios intentos seguidos.',
  'cambiar-contrasena': 'Hiciste varios intentos seguidos.',
};

function campoDeContrasenaNueva(pantalla: PantallaAuth): string {
  return pantalla === 'registro' ? 'password' : 'passwordNueva';
}

export function mensajeDeError(codigo: string, pantalla: PantallaAuth): MensajeDeError {
  switch (codigo) {
    case 'CREDENCIALES_INVALIDAS':
      return { campo: null, texto: 'Email o contraseña incorrectos.' };
    case 'CUENTA_BLOQUEADA':
      return { campo: null, texto: 'Pausamos el ingreso por seguridad.' };
    case 'SESION_EXPIRADA':
      return { campo: null, texto: 'Tu sesión venció.' };
    case 'EMAIL_EN_USO':
      return { campo: 'email', texto: 'Ya hay una cuenta con este email.' };
    case 'PASSWORD_DEBIL':
      return { campo: campoDeContrasenaNueva(pantalla), texto: 'Esa contraseña no se puede usar.' };
    case 'PASSWORD_ACTUAL_INVALIDA':
      return { campo: 'passwordActual', texto: 'La contraseña actual no es correcta.' };
    case 'TOKEN_INVALIDO':
      return { campo: null, texto: 'Este link ya no sirve' };
    case 'TERMINOS_NO_ACEPTADOS':
      return { campo: 'aceptaTerminos', texto: 'Tenés que aceptar los términos y la política de privacidad.' };
    case 'DEMASIADAS_SOLICITUDES':
      return { campo: null, texto: LIMITE_POR_PANTALLA[pantalla] };
    case 'SIN_CONEXION':
      return { campo: null, texto: 'Sin conexión.' };
    default:
      return { campo: null, texto: TEXTO_GENERICO };
  }
}

const SOLO_DIGITOS = /^\d+$/;

/** Segundos a esperar según `Retry-After` (segundos o fecha HTTP), o `null` si no hay un valor válido. */
export function segundosDeEspera(retryAfter: string | null, ahora: Date): number | null {
  if (retryAfter === null) return null;
  const valor = retryAfter.trim();
  if (valor === '') return null;
  if (SOLO_DIGITOS.test(valor)) return Number(valor);
  if (!/[a-z]/i.test(valor)) return null;
  const instante = Date.parse(valor);
  if (Number.isNaN(instante)) return null;
  return Math.max(0, Math.ceil((instante - ahora.getTime()) / 1000));
}

/** "1 minuto", "45 minutos": siempre en minutos redondeados hacia arriba y nunca 0, como en los mockups. */
export function textoDeEspera(segundos: number): string {
  const minutos = Math.max(1, Math.ceil(segundos / 60));
  return minutos === 1 ? '1 minuto' : `${minutos} minutos`;
}
