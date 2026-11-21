import { el } from '../shared/dom';
import { login } from '../features/auth/api';
import type { AvisoLogin, PaginaAuth } from '../features/auth/contexto';
import { mensajeDeError } from '../features/auth/errores';
import { avisoDeLimite, avisoInesperado, esApiError, vigilarConexion } from '../features/auth/estados';
import { avisoAuth, botonEnvio, campoAuth, encabezado, enlace, enlaceEnAviso, marcarInvalido, parrafoAlternativo } from '../features/auth/ui';

/** Login (C-07, spec app-auth/acceso; mockup `login.html`). */

function avisoDeLlegada(aviso: AvisoLogin): HTMLDivElement {
  switch (aviso.tipo) {
    case 'sesion-cerrada':
      return avisoAuth({ tono: 'info', icono: 'info', titulo: 'Cerraste sesión.', cuerpo: ['Hasta la próxima.'], rol: 'status' });
    case 'sesion-vencida':
      return avisoAuth({ tono: 'info', icono: 'reloj', titulo: 'Tu sesión venció.', cuerpo: ['Ingresá de nuevo y te llevamos a donde estabas.'], rol: 'status' });
    case 'contrasena-cambiada':
      return avisoAuth({
        tono: 'exito',
        icono: 'check-circulo',
        titulo: 'Cambiaste tu contraseña.',
        cuerpo: ['Por seguridad cerramos la sesión en todos tus dispositivos. Ingresá con la contraseña nueva.'],
        rol: 'status',
      });
    case 'registro-sin-login':
      return avisoAuth({
        tono: 'exito',
        icono: 'check-circulo',
        titulo: '¡Tu consultorio está listo!',
        cuerpo: ['No pudimos abrir tu sesión automáticamente. Ingresá con el email y la contraseña que acabás de crear.'],
        rol: 'status',
      });
  }
}

function minutosDe(datos: Readonly<Record<string, unknown>>): string {
  const minutos = datos['minutosRestantes'];
  const valor = typeof minutos === 'number' && Number.isFinite(minutos) ? Math.max(1, Math.ceil(minutos)) : 15;
  return valor === 1 ? '1 minuto' : `${valor} minutos`;
}

export const pagina: PaginaAuth = {
  titulo: 'Ingresá',
  conPanel: true,
  montar(contexto) {
    const llegada = contexto.tomarAviso();
    // Zonas separadas: el aviso de llegada queda, la falta de red va y viene, y cada error reemplaza al anterior.
    const zonaLlegada = el('div', {}, llegada === null ? null : avisoDeLlegada(llegada));
    const zonaConexion = el('div', {});
    const zonaError = el('div', {});

    const email = campoAuth({ etiqueta: 'Email', nombre: 'email', tipo: 'email', autocompletar: 'username' });
    const password = campoAuth({
      etiqueta: 'Contraseña',
      nombre: 'password',
      tipo: 'password',
      autocompletar: 'current-password',
      extraEtiqueta: enlace('/recuperar', '¿La olvidaste?', 'inline-flex items-center min-h-touch text-sm font-semibold text-primario underline underline-offset-2'),
    });
    if (llegada?.tipo === 'registro-sin-login') email.input.value = llegada.email;

    const envio = botonEnvio('Ingresar', 'Ingresando…');
    const formulario = el('form', { novalidate: true }, email.contenedor, password.contenedor, envio.boton);

    contexto.contenedor.replaceChildren(
      ...encabezado('Ingresá a tu agenda', 'Con el email y la contraseña de tu cuenta.'),
      zonaLlegada,
      zonaConexion,
      zonaError,
      formulario,
      parrafoAlternativo('¿Todavía no tenés cuenta? ', enlace('/registro', 'Probá gratis 30 días')),
    );

    const dejarDeVigilar = vigilarConexion(
      contexto.conectividad,
      zonaConexion,
      envio,
      'Para ingresar necesitás internet. Cuando vuelva la conexión, podés intentar de nuevo.',
    );

    const mostrarError = (aviso: HTMLDivElement) => zonaError.replaceChildren(aviso);

    formulario.addEventListener('submit', (evento) => {
      evento.preventDefault();
      const valorEmail = email.input.value.trim();
      const valorPassword = password.input.value;
      marcarInvalido(email.input, valorEmail === '');
      marcarInvalido(password.input, valorPassword === '');
      if (valorEmail === '') {
        email.input.focus();
        return;
      }
      if (valorPassword === '') {
        password.input.focus();
        return;
      }

      envio.enviando(true);
      login(contexto.http, valorEmail, valorPassword)
        .then((token) => contexto.entrar(token, contexto.query.get('volver')))
        .catch((error: unknown) => {
          envio.enviando(false);
          password.input.value = '';
          if (!esApiError(error)) {
            mostrarError(avisoInesperado('login'));
            return;
          }
          if (error.codigo === 'CREDENCIALES_INVALIDAS') {
            mostrarError(
              avisoAuth({
                tono: 'peligro',
                icono: 'alerta',
                titulo: mensajeDeError(error.codigo, 'login').texto,
                cuerpo: ['Revisá los datos o ', enlaceEnAviso('/recuperar', 'restablecé tu contraseña'), '.'],
              }),
            );
            password.input.focus();
          } else if (error.codigo === 'CUENTA_BLOQUEADA') {
            mostrarError(
              avisoAuth({
                tono: 'advertencia',
                icono: 'candado',
                titulo: mensajeDeError(error.codigo, 'login').texto,
                cuerpo: [
                  'Hubo varios intentos fallidos seguidos. Vas a poder volver a intentar en ',
                  el('b', {}, minutosDe(error.datos)),
                  '. Si no recordás tu contraseña, ',
                  enlaceEnAviso('/recuperar', 'restablecela'),
                  '.',
                ],
              }),
            );
          } else if (error.codigo === 'DEMASIADAS_SOLICITUDES') {
            mostrarError(avisoDeLimite(error, 'login', envio));
          } else if (error.codigo !== 'SIN_CONEXION') {
            mostrarError(avisoInesperado('login'));
          }
        });
    });

    return () => {
      dejarDeVigilar();
      password.input.value = '';
    };
  },
};
