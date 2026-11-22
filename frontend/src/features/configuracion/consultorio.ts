import type { ContextoPagina } from '../../pages/pagina';
import { el, type Hijo } from '../../shared/dom';
import { ApiError } from '../../shared/http';
import { boton } from '../../shared/ui/boton';
import { errorRecuperable } from '../../shared/ui/error-recuperable';
import { esqueleto } from '../../shared/ui/esqueleto';
import { avisoAuth } from '../auth/ui';
import { guardarConfiguracion, obtenerConfiguracion } from './api';
import { vigilarCambios } from './cambios';
import { traducirError } from './errores';
import { barraDeGuardado, campoConfig, cantidadDeDatos, chip, regionAnuncios, resumenDeErrores, tarjeta, type CampoConfig } from './ui';
import { formularioDesdeRespuesta, solicitudDesdeFormulario, type CantidadEditable, type FormularioConsultorio } from './unidades';
import { normalizarSlug, PASOS_PERMITIDOS, validarConsultorio } from './validaciones';

/**
 * Pestaña Consultorio (C-09, spec app-configuracion/consultorio; mockup `consultorio.html`): datos, link público,
 * reglas de reserva, reserva pública y zona horaria, con un solo botón de guardar.
 */

const ZONAS: readonly { readonly id: string; readonly texto: string }[] = [
  { id: 'America/Argentina/Buenos_Aires', texto: 'Buenos Aires, CABA y la mayoría de las provincias' },
  { id: 'America/Argentina/Cordoba', texto: 'Córdoba' },
  { id: 'America/Argentina/Mendoza', texto: 'Mendoza' },
  { id: 'America/Argentina/San_Luis', texto: 'San Luis' },
  { id: 'America/Argentina/Salta', texto: 'Salta' },
  { id: 'America/Argentina/Ushuaia', texto: 'Ushuaia' },
];

export const linkPublico = (slug: string) => `${location.origin}/c/${slug}`;

function ejemploDePaso(paso: number): string {
  const horas = [0, 1, 2].map((i) => {
    const minutos = 9 * 60 + i * paso;
    return `${Math.floor(minutos / 60)}:${String(minutos % 60).padStart(2, '0')}`;
  });
  return `Con ${paso} minutos, tu página ofrece ${horas.join(', ')}…`;
}

const input = (atributos: Record<string, string | number | boolean | null>) => el('input', atributos);

interface Controles {
  readonly nombre: CampoConfig<HTMLInputElement>;
  readonly slug: CampoConfig<HTMLInputElement>;
  readonly direccion: CampoConfig<HTMLInputElement>;
  readonly telefonoContacto: CampoConfig<HTMLInputElement>;
  readonly anticipacionCancelacion: CampoConfig<HTMLInputElement>;
  readonly anticipacionReserva: CampoConfig<HTMLInputElement>;
  readonly horizonteReservaDias: CampoConfig<HTMLInputElement>;
  readonly pasoHuecosMin: CampoConfig<HTMLSelectElement>;
  readonly zonaHoraria: CampoConfig<HTMLSelectElement>;
}

type CampoConsultorio = keyof Controles;

/** Un campo por clave: si `Controles` suma o pierde uno, esto no compila hasta actualizarlo. */
const CAMPOS_CONSULTORIO: Readonly<Record<CampoConsultorio, true>> = {
  nombre: true,
  slug: true,
  direccion: true,
  telefonoContacto: true,
  anticipacionCancelacion: true,
  anticipacionReserva: true,
  horizonteReservaDias: true,
  pasoHuecosMin: true,
  zonaHoraria: true,
};

/** Los errores traen el campo como texto (issue #60): solo las claves propias cuentan, no las heredadas de Object. */
export function esCampoConsultorio(nombre: string): nombre is CampoConsultorio {
  return Object.hasOwn(CAMPOS_CONSULTORIO, nombre);
}

const sufijoDeAnticipacion = (cantidad: CantidadEditable) => (cantidad.unidad === 'horas' ? 'horas antes' : 'minutos antes');

export function montarConsultorio(contexto: ContextoPagina, destino: HTMLElement): void {
  const anuncios = regionAnuncios();
  const vigia = vigilarCambios(contexto.navegacion, window);
  let guardado: FormularioConsultorio | null = null;

  async function cargar(): Promise<void> {
    vigia.marcar(false);
    destino.replaceChildren(esqueleto({ etiqueta: 'Cargando la configuración…' }), anuncios.elemento);
    try {
      guardado = formularioDesdeRespuesta(await obtenerConfiguracion(contexto.http));
      dibujar(guardado);
    } catch {
      destino.replaceChildren(errorRecuperable({ titulo: 'No pudimos cargar la configuración', alReintentar: () => void cargar() }), anuncios.elemento);
    }
  }

  function dibujar(valores: FormularioConsultorio): void {
    const zonaDeAvisos = el('div', {});
    let reservaActiva = valores.reservaPublicaActiva;
    const unidades = { cancelacion: valores.anticipacionCancelacion.unidad, reserva: valores.anticipacionReserva.unidad };

    const c: Controles = {
      nombre: campoConfig({ etiqueta: 'Nombre del consultorio', control: input({ name: 'nombre', autocomplete: 'organization', maxlength: 120 }) }),
      slug: campoConfig({
        etiqueta: 'Dirección de tu link',
        control: input({ name: 'slug', autocapitalize: 'none', autocomplete: 'off', spellcheck: 'false', maxlength: 40 }),
        prefijo: '…/c/',
        ayuda: 'De 3 a 40 letras minúsculas, números o guiones.',
      }),
      direccion: campoConfig({ etiqueta: 'Dirección', opcional: true, control: input({ name: 'direccion', autocomplete: 'street-address' }) }),
      telefonoContacto: campoConfig({
        etiqueta: 'Teléfono de contacto',
        control: input({ name: 'telefonoContacto', type: 'tel', inputmode: 'tel', autocomplete: 'tel', required: true }),
        ayuda: 'Se muestra en tu página y cuando los pacientes ya no pueden cancelar por el link o pausás la reserva.',
      }),
      anticipacionCancelacion: campoConfig({
        etiqueta: 'Hasta cuándo pueden cancelar o reprogramar',
        control: input({ name: 'anticipacionCancelacion', type: 'number', inputmode: 'numeric', min: 0, step: 1 }),
        sufijo: sufijoDeAnticipacion(valores.anticipacionCancelacion),
        corto: true,
        ayuda: 'Pasado ese plazo, el link les muestra tu teléfono. De 0 a 168 horas (7 días).',
      }),
      anticipacionReserva: campoConfig({
        etiqueta: 'Con cuánta anticipación pueden reservar',
        control: input({ name: 'anticipacionReserva', type: 'number', inputmode: 'numeric', min: 0, step: 1 }),
        sufijo: sufijoDeAnticipacion(valores.anticipacionReserva),
        corto: true,
        ayuda: 'Evita que te reserven un turno para dentro de 10 minutos. Tiene que ser menor que el horizonte.',
      }),
      horizonteReservaDias: campoConfig({
        etiqueta: 'Hasta cuántos días adelante',
        control: input({ name: 'horizonteReservaDias', type: 'number', inputmode: 'numeric', min: 1, max: 365, step: 1 }),
        sufijo: 'días',
        corto: true,
        ayuda: 'De 1 a 365 días.',
      }),
      pasoHuecosMin: campoConfig({
        etiqueta: 'Cada cuánto empiezan los turnos',
        control: el('select', { name: 'pasoHuecosMin' }, ...PASOS_PERMITIDOS.map((p) => el('option', { value: String(p) }, `Cada ${p} minutos`))),
        corto: true,
        ayuda: ejemploDePaso(valores.pasoHuecosMin),
      }),
      zonaHoraria: campoConfig({
        etiqueta: 'Zona horaria',
        control: el(
          'select',
          { name: 'zonaHoraria' },
          ...[...ZONAS, ...(ZONAS.some((z) => z.id === valores.zonaHoraria) ? [] : [{ id: valores.zonaHoraria, texto: valores.zonaHoraria }])].map((z) =>
            el('option', { value: z.id }, z.texto),
          ),
        ),
        ayuda: 'Tus horarios y turnos se muestran en esta zona.',
      }),
    };

    const textoLink = el('span', { 'data-link-publico': true }, linkPublico(valores.slug));
    const avisoPausada = avisoAuth({ tono: 'advertencia', icono: 'info', rol: null, cuerpo: [el('b', {}, 'La reserva pública está pausada.'), ' El link muestra tu teléfono, pero no permite reservar.'] });
    avisoPausada.classList.replace('mb-5', 'mt-3');
    avisoPausada.classList.add('mb-0');
    const copiar = boton({
      texto: 'Copiar link',
      variante: 'secundario',
      icono: 'copiar',
      alHacerClic: () => {
        const url = linkPublico(guardado?.slug ?? valores.slug);
        void (navigator.clipboard?.writeText(url) ?? Promise.reject(new Error('sin portapapeles')))
          .then(() => anuncios.anunciar('¡Copiado!'))
          .catch(() => anuncios.anunciar('No pudimos copiar el link. Seleccionalo y copialo a mano.'));
      },
    });
    const tarjetaLink = tarjeta(
      'Tu link para pacientes',
      'Compartilo para que te reserven turnos sin crear una cuenta.',
      el(
        'div',
        { class: 'flex flex-wrap items-center gap-x-3 gap-y-2 p-3 bg-primario-tinte border border-reservado-bd rounded-md' },
        el('p', { class: 'flex-[1_1_14rem] min-w-0 m-0 font-mono font-semibold text-reservado-fg break-all' }, el('small', { class: 'block font-sans font-normal text-xs text-texto-tenue' }, 'Link público'), textoLink),
        copiar,
      ),
      avisoPausada,
    );

    const avisoSlug = el('div', { 'data-aviso-slug': true, hidden: true });
    const sugerencias = el('ul', { 'aria-label': 'Direcciones libres sugeridas', hidden: true, class: 'flex flex-wrap gap-2 mt-1 mb-0 p-0 list-none' });
    c.slug.extra.append(sugerencias, avisoSlug);

    const textoEstado = el('span', {});
    const pista = el('p', { id: 'reserva-publica-ayuda', class: 'm-0 text-sm text-texto-tenue' });
    const interruptor = el(
      'button',
      { type: 'button', role: 'switch', 'aria-labelledby': 'reserva-publica-etiqueta', 'aria-describedby': 'reserva-publica-ayuda', class: 'group flex-none inline-flex items-center gap-2 min-h-touch p-0 border-0 bg-transparent font-sans text-sm font-semibold text-texto cursor-pointer' },
      el(
        'span',
        { 'aria-hidden': 'true', class: 'relative w-6 h-5 rounded-full bg-borde-fuerte group-aria-checked:bg-exito transition-colors duration-rapida' },
        el('span', { class: 'absolute top-1 left-1 w-4 h-4 rounded-full bg-superficie shadow-sm transition-transform duration-rapida group-aria-checked:translate-x-2' }),
      ),
      textoEstado,
    );

    const reglas = tarjeta(
      'Reservas de pacientes',
      'Reglas que se aplican en tu link público.',
      el('div', { class: 'flex items-start justify-between gap-4 mb-4' }, el('div', { class: 'flex-1' }, el('strong', { id: 'reserva-publica-etiqueta', class: 'block' }, 'Reserva pública'), pista), interruptor),
      c.anticipacionCancelacion.contenedor,
      c.anticipacionReserva.contenedor,
      c.horizonteReservaDias.contenedor,
      c.pasoHuecosMin.contenedor,
      c.zonaHoraria.contenedor,
    );

    const barra = barraDeGuardado('Guardar cambios', () => {
      if (guardado !== null) dibujar(guardado);
    });
    const formulario = el(
      'form',
      { novalidate: true, class: 'grid gap-5 config-2col:grid-cols-2 config-2col:items-start' },
      el('div', { class: 'grid gap-5 min-w-0' }, tarjetaLink, tarjeta('Datos del consultorio', 'Así te ven tus pacientes en tu página.', c.nombre.contenedor, c.slug.contenedor, c.direccion.contenedor, c.telefonoContacto.contenedor)),
      reglas,
      barra.elemento,
    );

    destino.replaceChildren(zonaDeAvisos, formulario, anuncios.elemento);

    // Valores iniciales.
    c.nombre.control.value = valores.nombre;
    c.slug.control.value = valores.slug;
    c.direccion.control.value = valores.direccion;
    c.telefonoContacto.control.value = valores.telefonoContacto;
    c.anticipacionCancelacion.control.value = String(valores.anticipacionCancelacion.valor);
    c.anticipacionReserva.control.value = String(valores.anticipacionReserva.valor);
    c.horizonteReservaDias.control.value = String(valores.horizonteReservaDias);
    c.pasoHuecosMin.control.value = String(valores.pasoHuecosMin);
    c.zonaHoraria.control.value = valores.zonaHoraria;

    const numero = (control: HTMLInputElement) => (control.value.trim() === '' ? Number.NaN : Number(control.value));
    const leer = (): FormularioConsultorio => ({
      nombre: c.nombre.control.value,
      slug: c.slug.control.value,
      direccion: c.direccion.control.value,
      telefonoContacto: c.telefonoContacto.control.value,
      zonaHoraria: c.zonaHoraria.control.value,
      anticipacionCancelacion: { valor: numero(c.anticipacionCancelacion.control), unidad: unidades.cancelacion },
      anticipacionReserva: { valor: numero(c.anticipacionReserva.control), unidad: unidades.reserva },
      horizonteReservaDias: numero(c.horizonteReservaDias.control),
      pasoHuecosMin: Number(c.pasoHuecosMin.control.value),
      reservaPublicaActiva: reservaActiva,
      // La versión no es un campo: es la del último valor leído o guardado (issue #59).
      version: guardado?.version ?? valores.version,
    });

    function actualizarReserva(): void {
      interruptor.setAttribute('aria-checked', String(reservaActiva));
      textoEstado.textContent = reservaActiva ? 'Activa' : 'Pausada';
      pista.textContent = reservaActiva
        ? 'Los pacientes pueden reservar, reprogramar y cancelar desde tu link.'
        : 'Pausada: los pacientes no pueden reservar desde el link hasta que la actives. Tus turnos no cambian.';
      avisoPausada.hidden = guardado?.reservaPublicaActiva !== false;
    }

    function actualizarSlug(): void {
      const slug = c.slug.control.value;
      const cambio = guardado !== null && slug !== guardado.slug && slug !== '';
      avisoSlug.hidden = !cambio;
      if (!cambio) return avisoSlug.replaceChildren();
      const aviso = avisoAuth({
        tono: 'advertencia',
        icono: 'info',
        rol: null,
        cuerpo: ['Tu link va a pasar a ser ', el('b', {}, linkPublico(slug)), '. Al guardar, ', el('b', {}, 'el link anterior deja de funcionar'), ': si ya lo compartiste, avisales a tus pacientes.'],
      });
      aviso.classList.replace('mb-5', 'mt-2');
      avisoSlug.replaceChildren(aviso);
    }

    function actualizarCambios(): void {
      const conCambios = guardado !== null && JSON.stringify(leer()) !== JSON.stringify(guardado);
      barra.mostrar(conCambios);
      vigia.marcar(conCambios);
    }

    function mostrarSugerencias(lista: readonly string[]): void {
      sugerencias.hidden = lista.length === 0;
      sugerencias.replaceChildren(
        ...lista.map((s) =>
          el(
            'li',
            {},
            chip(s, () => {
              c.slug.control.value = s;
              c.slug.error(null);
              mostrarSugerencias([]);
              actualizarSlug();
              actualizarCambios();
            }),
          ),
        ),
      );
    }

    interruptor.addEventListener('click', () => {
      reservaActiva = !reservaActiva;
      actualizarReserva();
      actualizarCambios();
    });
    c.slug.control.addEventListener('input', () => {
      const normalizado = normalizarSlug(c.slug.control.value);
      if (normalizado !== c.slug.control.value) c.slug.control.value = normalizado;
      actualizarSlug();
    });
    c.pasoHuecosMin.control.addEventListener('change', () => {
      const ayuda = c.pasoHuecosMin.contenedor.querySelector(`#${c.pasoHuecosMin.control.id}-ayuda`);
      if (ayuda !== null) ayuda.textContent = ejemploDePaso(Number(c.pasoHuecosMin.control.value));
    });
    for (const campo of Object.values(c) as CampoConfig<HTMLInputElement | HTMLSelectElement>[]) {
      const limpiar = () => {
        campo.error(null);
        actualizarCambios();
      };
      campo.control.addEventListener('input', limpiar);
      campo.control.addEventListener('change', limpiar);
    }

    function mostrarErrores(errores: readonly { readonly campo: string; readonly mensaje: string | readonly Hijo[] }[]): void {
      for (const error of errores) if (esCampoConsultorio(error.campo)) c[error.campo].error(error.mensaje);
    }

    const idDelCampo = (nombre: string) => (esCampoConsultorio(nombre) ? c[nombre].control.id : '');

    formulario.addEventListener('submit', (evento) => {
      evento.preventDefault();
      const datos = leer();
      const errores = validarConsultorio(datos);
      zonaDeAvisos.replaceChildren();
      if (errores.length > 0) {
        mostrarErrores(errores);
        const resumen = resumenDeErrores(
          cantidadDeDatos(errores.length),
          errores.map((e) => ({ id: idDelCampo(e.campo), mensaje: e.mensaje })),
        );
        zonaDeAvisos.replaceChildren(resumen);
        resumen.focus();
        return;
      }
      barra.guardando(true);
      guardarConfiguracion(contexto.http, solicitudDesdeFormulario(datos))
        .then((respuesta) => {
          guardado = formularioDesdeRespuesta(respuesta);
          barra.guardando(false);
          actualizarSlug();
          actualizarReserva();
          actualizarCambios();
          textoLink.textContent = linkPublico(guardado.slug);
          anuncios.anunciar('Guardamos los cambios');
        })
        .catch((error: unknown) => {
          barra.guardando(false);
          if (!(error instanceof ApiError)) {
            zonaDeAvisos.replaceChildren(avisoAuth({ tono: 'peligro', icono: 'alerta', titulo: 'No pudimos guardar. Lo que editaste sigue acá: probá de nuevo.' }));
            return;
          }
          const traducido = traducirError(error);
          mostrarErrores(traducido.campos);
          if (traducido.sugerencias.length > 0) {
            c.slug.error([`${c.slug.control.value} ya lo usa otro consultorio. Elegí una de estas o escribí otra:`]);
            mostrarSugerencias(traducido.sugerencias);
          }
          if (traducido.conflicto) {
            const aviso = avisoAuth({
              tono: 'advertencia',
              icono: 'reintentar',
              titulo: 'La configuración cambió en otra pestaña o dispositivo',
              cuerpo: ['Para no pisar esos cambios, no guardamos los tuyos. Mirá los valores actuales y volvé a hacer tu cambio.'],
            });
            aviso.lastElementChild?.append(el('div', { class: 'mt-2' }, boton({ texto: 'Ver los valores actuales', variante: 'secundario', alHacerClic: () => void cargar() })));
            zonaDeAvisos.replaceChildren(aviso);
          } else if (traducido.aviso !== null && traducido.campos.length === 0) {
            zonaDeAvisos.replaceChildren(avisoAuth({ tono: 'peligro', icono: 'alerta', titulo: traducido.aviso }));
          }
        });
    });

    actualizarReserva();
    actualizarSlug();
    actualizarCambios();
  }

  void cargar();
}
