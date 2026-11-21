import { el, idUnico } from '../shared/dom';
import { icono } from '../shared/ui/iconos';
import { login, registrar } from '../features/auth/api';
import type { PaginaAuth } from '../features/auth/contexto';
import { mensajeDeError } from '../features/auth/errores';
import { avisoDeLimite, avisoInesperado, esApiError, vigilarConexion } from '../features/auth/estados';
import {
  avisoAuth,
  botonEnvio,
  campoAuth,
  CLASE_ENLACE,
  encabezado,
  enlace,
  enlaceEnAviso,
  listaRequisitos,
  marcarInvalido,
  parrafoAlternativo,
} from '../features/auth/ui';
import { evaluarRequisitos, validarRegistro, type CampoRegistro, type DatosRegistro } from '../features/auth/validaciones';

/** Registro con entrada automática (C-07, spec app-auth/registro; mockup `registro.html`). */

const ID_REQUISITOS = 'requisitos-registro';

export const pagina: PaginaAuth = {
  titulo: 'Creá tu consultorio',
  conPanel: true,
  montar(contexto) {
    const nombre = campoAuth({ etiqueta: 'Nombre', nombre: 'nombre', autocompletar: 'given-name' });
    const apellido = campoAuth({ etiqueta: 'Apellido', nombre: 'apellido', autocompletar: 'family-name' });
    const email = campoAuth({ etiqueta: 'Email', nombre: 'email', tipo: 'email', autocompletar: 'username' });
    const password = campoAuth({ etiqueta: 'Contraseña', nombre: 'password', tipo: 'password', autocompletar: 'new-password' });
    const requisitos = listaRequisitos(ID_REQUISITOS, 'Distinta de tu email');
    password.input.setAttribute('aria-describedby', ID_REQUISITOS);
    password.contenedor.append(requisitos.elemento);
    const consultorio = campoAuth({
      etiqueta: 'Nombre del consultorio',
      nombre: 'nombreConsultorio',
      autocompletar: 'organization',
      ayuda: 'Lo ven tus pacientes en tu link de reservas. Podés cambiarlo después.',
    });

    const idTerminos = idUnico('auth-terminos');
    const terminos = el('input', {
      type: 'checkbox',
      id: idTerminos,
      name: 'aceptaTerminos',
      class: 'flex-none w-icono h-icono m-3 accent-primario aria-[invalid=true]:outline aria-[invalid=true]:outline-2 aria-[invalid=true]:outline-peligro',
    });
    const errorTerminos = el('p', { id: `${idTerminos}-error`, hidden: true, class: '-mt-2 mb-4 flex gap-1 items-start text-sm font-medium text-peligro' });
    const filaTerminos = el(
      'div',
      { class: 'flex gap-3 items-start mb-4' },
      terminos,
      el(
        'label',
        { for: idTerminos, class: 'pt-2 text-sm' },
        'Leí y acepto los ',
        el('a', { href: '/terminos', 'data-link': true, class: CLASE_ENLACE }, 'términos y condiciones'),
        ' y la ',
        el('a', { href: '/privacidad', 'data-link': true, class: CLASE_ENLACE }, 'política de privacidad'),
        '.',
      ),
    );

    const zonaConexion = el('div', {});
    const zonaError = el('div', {});
    const envio = botonEnvio('Crear mi consultorio', 'Creando tu consultorio…');
    const formulario = el(
      'form',
      { novalidate: true },
      el('div', { class: 'grid min-[480px]:grid-cols-2 gap-x-3' }, nombre.contenedor, apellido.contenedor),
      email.contenedor,
      password.contenedor,
      consultorio.contenedor,
      filaTerminos,
      errorTerminos,
      envio.boton,
      el('p', { class: 'flex gap-2 items-center justify-center mt-3 mb-0 text-sm text-texto-tenue' }, icono('check', 'w-icono-sm h-icono-sm flex-none text-exito'), '30 días de prueba · sin tarjeta ni medio de pago'),
    );

    contexto.contenedor.replaceChildren(
      ...encabezado('Creá tu consultorio', 'Tu cuenta y tu consultorio, listos en un minuto.'),
      zonaConexion,
      zonaError,
      formulario,
      parrafoAlternativo('¿Ya tenés cuenta? ', enlace('/login', 'Ingresá')),
    );

    const dejarDeVigilar = vigilarConexion(contexto.conectividad, zonaConexion, envio, 'Para crear tu cuenta necesitás internet.');

    const actualizarRequisitos = (alEnviar = false) => {
      const estado = evaluarRequisitos(password.input.value, email.input.value, { alEnviar });
      requisitos.actualizar(estado);
      marcarInvalido(password.input, estado.hasta72Bytes !== null || (alEnviar && (estado.largoMinimo !== 'cumplido' || estado.distintaDelEmail === 'fallado')));
    };
    actualizarRequisitos();
    password.input.addEventListener('input', () => actualizarRequisitos());
    email.input.addEventListener('input', () => {
      email.error(null);
      actualizarRequisitos();
    });

    const inputDe: Record<CampoRegistro, HTMLInputElement> = {
      nombre: nombre.input,
      apellido: apellido.input,
      email: email.input,
      password: password.input,
      nombreConsultorio: consultorio.input,
      aceptaTerminos: terminos,
    };

    const leerDatos = (): DatosRegistro => ({
      nombre: nombre.input.value.trim(),
      apellido: apellido.input.value.trim(),
      email: email.input.value.trim(),
      password: password.input.value,
      nombreConsultorio: consultorio.input.value.trim(),
      aceptaTerminos: terminos.checked,
    });

    function mostrarTerminos(error: string | null): void {
      marcarInvalido(terminos, error !== null);
      errorTerminos.hidden = error === null;
      errorTerminos.replaceChildren(...(error === null ? [] : [icono('alerta', 'w-icono-sm h-icono-sm flex-none mt-1'), el('span', {}, error)]));
      if (error === null) terminos.removeAttribute('aria-describedby');
      else terminos.setAttribute('aria-describedby', errorTerminos.id);
    }

    function mostrarResumen(errores: ReturnType<typeof validarRegistro>): void {
      for (const [campo, input] of Object.entries(inputDe) as [CampoRegistro, HTMLInputElement][]) {
        if (campo !== 'aceptaTerminos' && campo !== 'password') marcarInvalido(input, errores.some((e) => e.campo === campo));
      }
      mostrarTerminos(errores.some((e) => e.campo === 'aceptaTerminos') ? 'Para crear la cuenta tenés que aceptarlos.' : null);
      const cantidad = errores.length === 1 ? 'Revisá 1 dato para seguir:' : `Revisá ${errores.length} datos para seguir:`;
      const resumen = avisoAuth({
        tono: 'peligro',
        icono: 'alerta',
        titulo: cantidad,
      });
      resumen.setAttribute('tabindex', '-1');
      resumen.dataset['resumenErrores'] = '';
      resumen.lastElementChild?.append(
        el(
          'ul',
          { class: 'mt-1 mb-0 pl-5' },
          ...errores.map((e) => el('li', {}, el('a', { href: `#${inputDe[e.campo].id}`, class: 'font-semibold underline text-current' }, e.mensaje))),
        ),
      );
      zonaError.replaceChildren(resumen);
      resumen.focus();
    }

    async function entrarDespuesDelRegistro(datos: DatosRegistro): Promise<void> {
      envio.enviando(true);
      envio.boton.replaceChildren(envio.boton.firstChild ?? '', '¡Listo! Entrando a tu consultorio…');
      let credencial: string | null = datos.password;
      try {
        const token = await login(contexto.http, datos.email, credencial);
        credencial = null;
        await contexto.entrar(token, null);
      } catch {
        contexto.dejarAviso({ tipo: 'registro-sin-login', email: datos.email });
        await contexto.navegar('/login');
      } finally {
        // D4: la contraseña se descarta apenas termina el intento, salga bien o mal.
        credencial = null;
        password.input.value = '';
      }
    }

    formulario.addEventListener('submit', (evento) => {
      evento.preventDefault();
      const datos = leerDatos();
      const errores = validarRegistro(datos);
      actualizarRequisitos(true);
      if (errores.length > 0) {
        mostrarResumen(errores);
        return;
      }
      zonaError.replaceChildren();
      mostrarTerminos(null);
      envio.enviando(true);
      registrar(contexto.http, datos)
        .then(() => entrarDespuesDelRegistro(datos))
        .catch((error: unknown) => {
          envio.enviando(false);
          password.input.value = '';
          actualizarRequisitos();
          if (!esApiError(error)) {
            zonaError.replaceChildren(avisoInesperado('registro'));
            return;
          }
          if (error.codigo === 'EMAIL_EN_USO') {
            email.error([`${mensajeDeError(error.codigo, 'registro').texto} `, enlaceEnAviso('/login', 'Ingresá'), ' o ', enlaceEnAviso('/recuperar', 'restablecé tu contraseña'), '.']);
            email.input.focus();
          } else if (error.codigo === 'PASSWORD_DEBIL') {
            marcarInvalido(password.input, true);
            zonaError.replaceChildren(
              avisoAuth({ tono: 'peligro', icono: 'alerta', titulo: mensajeDeError(error.codigo, 'registro').texto, cuerpo: ['Tiene que tener entre 10 y 72 caracteres y no puede ser igual a tu email.'] }),
            );
            password.input.focus();
          } else if (error.codigo === 'TERMINOS_NO_ACEPTADOS' || error.codigo === 'VALIDACION') {
            mostrarResumen(validarRegistro(leerDatos()).length > 0 ? validarRegistro(leerDatos()) : [{ campo: 'aceptaTerminos', mensaje: mensajeDeError('TERMINOS_NO_ACEPTADOS', 'registro').texto }]);
          } else if (error.codigo === 'DEMASIADAS_SOLICITUDES') {
            zonaError.replaceChildren(avisoDeLimite(error, 'registro', envio));
          } else if (error.codigo !== 'SIN_CONEXION') {
            zonaError.replaceChildren(avisoInesperado('registro'));
          }
        });
    });

    return () => {
      dejarDeVigilar();
      password.input.value = '';
    };
  },
};
