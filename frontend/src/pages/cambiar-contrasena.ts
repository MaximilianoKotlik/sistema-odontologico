import { el } from '../shared/dom';
import { clasesBoton } from '../shared/ui/boton';
import { cambiarPassword } from '../features/auth/api';
import { mensajeDeError } from '../features/auth/errores';
import { avisoDeLimite, avisoInesperado, esApiError } from '../features/auth/estados';
import { avisoAuth, botonEnvio, campoAuth, listaRequisitos, marcarInvalido } from '../features/auth/ui';
import { evaluarRequisitos, validarRepeticion, type RequisitosContrasena } from '../features/auth/validaciones';
import type { Pagina } from './pagina';

/**
 * Cambiar contraseña con la sesión iniciada (C-07, US-005; mockup `cambiar-contrasena.html`). Con el 204, la app
 * borra lo local, avisa a las otras pestañas (D11) y lleva al login.
 */

export const pagina: Pagina = {
  titulo: 'Cambiar contraseña',
  seccion: null,
  montar({ contenedor, http, cuenta }) {
    const actual = campoAuth({
      etiqueta: 'Contraseña actual',
      nombre: 'passwordActual',
      tipo: 'password',
      autocompletar: 'current-password',
      etiquetaMostrar: 'Mostrar contraseña actual',
    });
    const nueva = campoAuth({
      etiqueta: 'Contraseña nueva',
      nombre: 'passwordNueva',
      tipo: 'password',
      autocompletar: 'new-password',
      etiquetaMostrar: 'Mostrar contraseña nueva',
    });
    const requisitos = listaRequisitos('requisitos-cambio', 'Distinta de tu email');
    nueva.input.setAttribute('aria-describedby', 'requisitos-cambio');
    nueva.contenedor.append(requisitos.elemento);
    const repetida = campoAuth({
      etiqueta: 'Repetí la contraseña nueva',
      nombre: 'passwordRepetida',
      tipo: 'password',
      autocompletar: 'new-password',
      etiquetaMostrar: 'Mostrar contraseña repetida',
    });

    const envio = botonEnvio('Cambiar contraseña', 'Cambiando…', 'flex-1 basis-48');
    envio.boton.dataset['requiereConexion'] = '';
    const zonaError = el('div', {});
    const formulario = el(
      'form',
      { novalidate: true },
      actual.contenedor,
      nueva.contenedor,
      repetida.contenedor,
      el(
        'div',
        { class: 'flex flex-wrap gap-2 mt-2' },
        envio.boton,
        el('a', { href: '/app/agenda', 'data-link': true, class: `${clasesBoton('fantasma')} flex-1 basis-48` }, 'Cancelar'),
      ),
    );

    contenedor.replaceChildren(
      el(
        'div',
        { class: 'max-w-md' },
        el('p', { class: 'mt-0 mb-4 text-texto-tenue' }, 'Usá una contraseña que no uses en otros sitios.'),
        avisoAuth({
          tono: 'info',
          icono: 'info',
          rol: null,
          cuerpo: ['Al cambiarla, cerramos la sesión en ', el('b', {}, 'todos tus dispositivos, también en este'), ', y vas a ingresar de nuevo con la contraseña nueva.'],
        }),
        zonaError,
        formulario,
      ),
    );

    // El email de la cuenta no está en el frontend: "Distinta de tu email" lo valida el backend (PASSWORD_DEBIL).
    let debilSegunBackend = false;
    const actualizar = (alEnviar = false): RequisitosContrasena => {
      const estado = evaluarRequisitos(nueva.input.value, null, { alEnviar });
      const mostrado: RequisitosContrasena = debilSegunBackend ? { ...estado, distintaDelEmail: 'fallado' } : estado;
      requisitos.actualizar(mostrado);
      marcarInvalido(nueva.input, debilSegunBackend || estado.hasta72Bytes !== null || (alEnviar && estado.largoMinimo !== 'cumplido'));
      return estado;
    };
    actualizar();
    nueva.input.addEventListener('input', () => {
      debilSegunBackend = false;
      actualizar();
    });
    actual.input.addEventListener('input', () => actual.error(null));
    repetida.input.addEventListener('input', () => repetida.error(null));

    formulario.addEventListener('submit', (evento) => {
      evento.preventDefault();
      const estado = actualizar(true);
      marcarInvalido(actual.input, actual.input.value === '');
      const noCoinciden = validarRepeticion(nueva.input.value, repetida.input.value);
      repetida.error(noCoinciden);
      if (actual.input.value === '') return actual.input.focus();
      if (estado.largoMinimo !== 'cumplido' || estado.hasta72Bytes !== null) return nueva.input.focus();
      if (noCoinciden !== null) return repetida.input.focus();

      zonaError.replaceChildren();
      envio.enviando(true);
      cambiarPassword(http, actual.input.value, nueva.input.value)
        .then(() => {
          actual.input.value = '';
          nueva.input.value = '';
          repetida.input.value = '';
          return cuenta.terminarPorCambioDeContrasena();
        })
        .catch((error: unknown) => {
          envio.enviando(false);
          if (!esApiError(error)) {
            zonaError.replaceChildren(avisoInesperado('cambiar-contrasena'));
          } else if (error.codigo === 'PASSWORD_ACTUAL_INVALIDA') {
            actual.input.value = '';
            actual.error(mensajeDeError(error.codigo, 'cambiar-contrasena').texto);
            actual.input.focus();
          } else if (error.codigo === 'PASSWORD_DEBIL') {
            debilSegunBackend = true;
            actualizar(true);
            nueva.input.focus();
          } else if (error.codigo === 'DEMASIADAS_SOLICITUDES') {
            zonaError.replaceChildren(avisoDeLimite(error, 'cambiar-contrasena', envio));
          } else if (error.codigo !== 'SIN_CONEXION' && error.codigo !== 'SESION_VENCIDA') {
            zonaError.replaceChildren(avisoInesperado('cambiar-contrasena'));
          }
        });
    });
  },
};
