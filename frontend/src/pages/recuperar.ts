import { el } from '../shared/dom';
import { icono } from '../shared/ui/iconos';
import { clasesBoton } from '../shared/ui/boton';
import { confirmarRecuperacion, pedirRecuperacion } from '../features/auth/api';
import type { ContextoAuth, PaginaAuth } from '../features/auth/contexto';
import { mensajeDeError } from '../features/auth/errores';
import { avisoDeLimite, avisoInesperado, esApiError, vigilarConexion } from '../features/auth/estados';
import { avisoAuth, botonEnvio, campoAuth, encabezado, enlaceBotonBloque, listaRequisitos, marcarInvalido, resultado } from '../features/auth/ui';
import { evaluarRequisitos, validarRepeticion } from '../features/auth/validaciones';

/**
 * `/recuperar` (C-07, spec app-auth/contrasena; mockups `recuperar.html` y `recuperar-confirmar.html`):
 * sin token, pide el link; con `#token=…`, pide la contraseña nueva. El token sale de la URL antes de pintar (D7).
 */

/** Lee el token del fragmento y lo saca de la URL y del historial. Devuelve null si no hay. */
function tomarTokenDelFragmento(): string | null {
  const token = new URLSearchParams(location.hash.slice(1)).get('token');
  if (location.hash !== '') history.replaceState(null, '', '/recuperar');
  return token === null || token === '' ? null : token;
}

function volverAIngresar(): HTMLAnchorElement {
  return el(
    'a',
    { href: '/login', 'data-link': true, class: `${clasesBoton('fantasma')} -ml-4 mb-3` },
    icono('izquierda', 'w-icono-sm h-icono-sm flex-none'),
    'Volver a ingresar',
  );
}

function montarPedido(contexto: ContextoAuth): () => void {
  const email = campoAuth({ etiqueta: 'Email', nombre: 'email', tipo: 'email', autocompletar: 'username' });
  const envio = botonEnvio('Enviarme el link', 'Enviando…');
  const zonaConexion = el('div', {});
  const zonaError = el('div', {});
  const formulario = el('form', { novalidate: true }, email.contenedor, envio.boton);
  contexto.contenedor.replaceChildren(
    volverAIngresar(),
    ...encabezado('Restablecé tu contraseña', 'Escribí el email de tu cuenta y te mandamos un link para crear una contraseña nueva.'),
    zonaConexion,
    zonaError,
    formulario,
  );
  const dejarDeVigilar = vigilarConexion(contexto.conectividad, zonaConexion, envio, 'Para pedir el link necesitás internet.');

  formulario.addEventListener('submit', (evento) => {
    evento.preventDefault();
    const valor = email.input.value.trim();
    marcarInvalido(email.input, valor === '');
    if (valor === '') {
      email.input.focus();
      return;
    }
    envio.enviando(true);
    pedirRecuperacion(contexto.http, valor)
      .then(() => {
        // RN-AU-04: la misma respuesta exista o no la cuenta.
        contexto.contenedor.replaceChildren(
          resultado({
            icono: 'sobre',
            tono: 'info',
            titulo: 'Revisá tu email',
            rol: 'status',
            parrafos: [
              ['Si hay una cuenta con ', el('b', {}, valor), ', te llega un link para crear una contraseña nueva. ', el('b', {}, 'Vence en 30 minutos'), ' y sirve una sola vez.'],
              ['¿No llegó en unos minutos? Revisá la carpeta de spam o pedí otro link.'],
            ],
            acciones: [enlaceBotonBloque('/login', 'Volver a ingresar'), enlaceBotonBloque('/recuperar', 'Pedir otro link', 'secundario')],
          }),
        );
      })
      .catch((error: unknown) => {
        envio.enviando(false);
        if (esApiError(error) && error.codigo === 'DEMASIADAS_SOLICITUDES') zonaError.replaceChildren(avisoDeLimite(error, 'recuperar', envio));
        else if (!esApiError(error) || error.codigo !== 'SIN_CONEXION') zonaError.replaceChildren(avisoInesperado('recuperar'));
      });
  });
  return dejarDeVigilar;
}

function linkVencido(): HTMLDivElement {
  return resultado({
    icono: 'link-roto',
    tono: 'peligro',
    titulo: 'Este link ya no sirve',
    rol: 'alert',
    parrafos: [['Puede que haya vencido (dura 30 minutos) o que ya lo hayas usado. Pedí uno nuevo y abrilo apenas te llegue.']],
    acciones: [enlaceBotonBloque('/recuperar', 'Pedir un link nuevo'), enlaceBotonBloque('/login', 'Volver a ingresar', 'fantasma')],
  });
}

function montarConfirmacion(contexto: ContextoAuth, token: string): () => void {
  // El token queda solo en esta variable hasta enviarlo (D7); ante TOKEN_INVALIDO se descarta.
  let tokenEnMemoria: string | null = token;
  const nueva = campoAuth({ etiqueta: 'Contraseña nueva', nombre: 'passwordNueva', tipo: 'password', autocompletar: 'new-password' });
  const requisitos = listaRequisitos('requisitos-recuperar', 'Distinta del email de tu cuenta');
  nueva.input.setAttribute('aria-describedby', 'requisitos-recuperar');
  nueva.contenedor.append(requisitos.elemento);
  const repetida = campoAuth({
    etiqueta: 'Repetí la contraseña nueva',
    nombre: 'passwordRepetida',
    tipo: 'password',
    autocompletar: 'new-password',
    etiquetaMostrar: 'Mostrar contraseña repetida',
  });
  const envio = botonEnvio('Guardar contraseña nueva', 'Guardando…');
  const zonaConexion = el('div', {});
  const zonaError = el('div', {});
  const formulario = el('form', { novalidate: true }, nueva.contenedor, repetida.contenedor, envio.boton);
  contexto.contenedor.replaceChildren(
    ...encabezado('Creá tu contraseña nueva', 'Elegí una que no uses en otros sitios.'),
    avisoAuth({ tono: 'info', icono: 'info', cuerpo: ['Al guardarla, cerramos la sesión en todos tus dispositivos y vas a ingresar con la contraseña nueva.'], rol: null }),
    zonaConexion,
    zonaError,
    formulario,
  );
  const dejarDeVigilar = vigilarConexion(contexto.conectividad, zonaConexion, envio, 'Para guardar la contraseña necesitás internet.');

  const actualizar = (alEnviar = false) => {
    const estado = evaluarRequisitos(nueva.input.value, null, { alEnviar });
    requisitos.actualizar(estado);
    marcarInvalido(nueva.input, estado.hasta72Bytes !== null || (alEnviar && estado.largoMinimo !== 'cumplido'));
    return estado;
  };
  actualizar();
  nueva.input.addEventListener('input', () => actualizar());
  repetida.input.addEventListener('input', () => repetida.error(null));

  formulario.addEventListener('submit', (evento) => {
    evento.preventDefault();
    const estado = actualizar(true);
    const noCoinciden = validarRepeticion(nueva.input.value, repetida.input.value);
    repetida.error(noCoinciden);
    if (estado.largoMinimo !== 'cumplido' || estado.hasta72Bytes !== null) {
      nueva.input.focus();
      return;
    }
    if (noCoinciden !== null || tokenEnMemoria === null) {
      repetida.input.focus();
      return;
    }
    zonaError.replaceChildren();
    envio.enviando(true);
    confirmarRecuperacion(contexto.http, tokenEnMemoria, nueva.input.value)
      .then(async () => {
        tokenEnMemoria = null;
        contexto.dejarAviso({ tipo: 'contrasena-cambiada' });
        await contexto.navegar('/login');
      })
      .catch((error: unknown) => {
        envio.enviando(false);
        if (!esApiError(error)) {
          zonaError.replaceChildren(avisoInesperado('recuperar-confirmar'));
        } else if (error.codigo === 'TOKEN_INVALIDO') {
          tokenEnMemoria = null;
          contexto.contenedor.replaceChildren(linkVencido());
        } else if (error.codigo === 'PASSWORD_DEBIL') {
          // El backend no consume el token: se conserva para reintentar.
          marcarInvalido(nueva.input, true);
          zonaError.replaceChildren(
            avisoAuth({
              tono: 'peligro',
              icono: 'alerta',
              titulo: mensajeDeError(error.codigo, 'recuperar-confirmar').texto,
              cuerpo: ['Tiene que tener entre 10 y 72 caracteres y no puede ser igual a tu email.'],
            }),
          );
          nueva.input.focus();
        } else if (error.codigo === 'DEMASIADAS_SOLICITUDES') {
          zonaError.replaceChildren(avisoDeLimite(error, 'recuperar-confirmar', envio));
        } else if (error.codigo !== 'SIN_CONEXION') {
          zonaError.replaceChildren(avisoInesperado('recuperar-confirmar'));
        }
      });
  });

  return () => {
    dejarDeVigilar();
    tokenEnMemoria = null;
    nueva.input.value = '';
    repetida.input.value = '';
  };
}

export const pagina: PaginaAuth = {
  titulo: 'Restablecé tu contraseña',
  montar(contexto) {
    const token = tomarTokenDelFragmento();
    return token === null ? montarPedido(contexto) : montarConfirmacion(contexto, token);
  },
};
