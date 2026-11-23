import type { ContextoPagina } from '../../pages/pagina';
import { el } from '../../shared/dom';
import { formatearDia, formatearHora } from '../../shared/fechas';
import { ApiError } from '../../shared/http';
import { boton, enlaceBoton } from '../../shared/ui/boton';
import { errorRecuperable } from '../../shared/ui/error-recuperable';
import { esqueleto } from '../../shared/ui/esqueleto';
import { tarjetaEstado } from '../../shared/ui/estado-vacio';
import { icono } from '../../shared/ui/iconos';
import { dniLegible, telefonoLegible } from '../agenda/formato';
import { rutaDelTurno } from '../agenda/tarjeta';
import { avisoAuth } from '../auth/ui';
import { campoConfig, resumenDeErrores } from '../configuracion/ui';
import { confirmarRevision, corregirPaciente, detallePaciente, type DatosCorregidos, type LoInformado, type Paciente } from './api';
import { dejarAvisoDeRevision } from './aviso-de-revision';
import { comparar, type CampoComparado } from './comparacion';
import { nombreCompleto, nombreDeLista } from './formato';
import { RUTA_PACIENTES, RUTA_PARA_REVISAR } from './rutas';

/**
 * `/app/pacientes/{id}` (C-17, spec pantalla-pacientes, mockup aprobado #36): compara la ficha con lo escrito en la
 * reserva y la resuelve con una de tres salidas sin confirmación (D2). Cada salida manda el `informadoAt` que se
 * mostró; si llegó otra reserva, recarga y avisa sin perder lo escrito (D9).
 */

const LARGO_MAXIMO_NOMBRE = 80;
const LARGO_MAXIMO_EMAIL = 254;
const EMAIL = /^[^@\s]+@[^@\s]+\.[^@\s]+$/;

const ETIQUETA: Record<CampoComparado, string> = { nombre: 'Nombre', apellido: 'Apellido', email: 'Email' };
const CON_ARTICULO: Record<CampoComparado | 'telefono', string> = { nombre: 'el nombre', apellido: 'el apellido', email: 'el email', telefono: 'el teléfono' };

type CampoCorregible = keyof DatosCorregidos;
interface ErrorDeCampo {
  readonly campo: CampoCorregible;
  /** Debajo del campo. */
  readonly mensaje: string;
  /** En el resumen, con link al campo. */
  readonly resumen: string;
}

/** La validación de la corrección, igual a la del dominio (nombre y apellido hasta 80, email con formato). */
export function validarCorreccion(datos: DatosCorregidos): ErrorDeCampo[] {
  const errores: ErrorDeCampo[] = [];
  for (const campo of ['nombre', 'apellido'] as const) {
    const valor = datos[campo].trim();
    if (valor === '') errores.push({ campo, mensaje: `Escribí ${CON_ARTICULO[campo]}.`, resumen: `Falta ${CON_ARTICULO[campo]}.` });
    else if (valor.length > LARGO_MAXIMO_NOMBRE) errores.push({ campo, mensaje: `Puede tener hasta ${LARGO_MAXIMO_NOMBRE} caracteres.`, resumen: `${ETIQUETA[campo]} es demasiado largo.` });
  }
  const email = datos.email.trim();
  if (email !== '' && (email.length > LARGO_MAXIMO_EMAIL || !EMAIL.test(email))) {
    errores.push({ campo: 'email', mensaje: 'Revisá el email: tiene que ser como nombre@dominio.com.', resumen: 'El email no tiene un formato válido.' });
  }
  return errores;
}

function enumerar(partes: readonly string[]): string {
  return partes.length <= 1 ? (partes[0] ?? '') : `${partes.slice(0, -1).join(', ')} y ${partes.at(-1) ?? ''}`;
}

const valorDe = (p: { readonly nombre: string; readonly apellido: string; readonly email: string | null }, campo: CampoComparado) => (campo === 'email' ? p.email : p[campo]);

export function montarRevisionDePaciente(contexto: ContextoPagina): void {
  const { contenedor, http, zonaHoraria: zona } = contexto;
  const id = contexto.params['id'] ?? '';
  let paciente: Paciente | null = null;
  let modo: 'comparar' | 'corregir' = 'comparar';
  let borrador: DatosCorregidos | null = null;
  let otraReserva = false;
  let yaRevisado = false;
  let errorAlGuardar = false;
  let guardando = false;

  const raiz = el('div', { 'data-revision-paciente': true, class: 'pb-6 max-w-5xl' });
  contenedor.replaceChildren(raiz);

  function volver(marcado: boolean): HTMLAnchorElement {
    return el(
      'a',
      { href: marcado ? RUTA_PARA_REVISAR : RUTA_PACIENTES, 'data-link': true, class: 'inline-flex items-center gap-1 min-h-touch mb-3 font-semibold text-primario no-underline hover:underline' },
      icono('izquierda', 'w-icono-sm h-icono-sm flex-none'),
      marcado ? 'Pacientes para revisar' : 'Pacientes',
    );
  }

  function etiqueta(marcado: boolean): HTMLElement {
    return marcado
      ? el('span', { class: 'inline-flex items-center gap-1 px-2 rounded-full border text-xs font-semibold bg-sobreturno-bg border-sobreturno-bd text-sobreturno-fg' }, icono('alerta', 'w-icono-sm h-icono-sm flex-none'), 'Revisar')
      : el('span', { class: 'inline-flex items-center gap-1 px-2 rounded-full border text-xs font-semibold bg-confirmado-bg border-confirmado-bd text-confirmado-fg' }, icono('check', 'w-icono-sm h-icono-sm flex-none'), 'Datos revisados');
  }

  const tarjeta = (...hijos: (Node | null)[]) => el('section', { class: 'bg-superficie border border-borde rounded-lg shadow-sm p-4 md:p-5' }, ...hijos);

  function datosDelPaciente(p: Paciente): HTMLElement {
    const fila = (dato: string, valor: string) => el('tr', { class: 'border-t border-borde first:border-t-0' }, el('th', { scope: 'row', class: 'py-2 pr-4 text-left font-medium text-texto-tenue' }, dato), el('td', { class: 'py-2 text-texto' }, valor));
    return tarjeta(
      el('h2', { class: 'm-0 text-lg font-bold' }, 'Datos del paciente'),
      el(
        'table',
        { class: 'w-full mt-3 border-collapse text-sm' },
        el('tbody', {}, fila('Nombre', p.nombre), fila('Apellido', p.apellido), fila('Email', p.email ?? 'Sin email'), fila('Teléfono', telefonoLegible(p.telefono))),
      ),
      el('p', { class: 'mt-3 mb-0 text-sm text-texto-tenue' }, 'En esta versión solo se corrigen los datos de pacientes marcados para revisar.'),
    );
  }

  function avisoOtraReserva(): HTMLElement | null {
    if (!otraReserva) return null;
    const nodo = avisoAuth({ tono: 'advertencia', icono: 'alerta', titulo: 'Llegó otra reserva con datos distintos.', cuerpo: ['Mientras revisabas, alguien reservó con este DNI y otro nombre. Te mostramos lo nuevo: elegí de nuevo qué datos quedan.'], rol: 'status' });
    nodo.dataset['avisoOtraReserva'] = '';
    return nodo;
  }

  function avisoErrorAlGuardar(): HTMLElement | null {
    return errorAlGuardar ? avisoAuth({ tono: 'peligro', icono: 'alerta', titulo: 'No pudimos guardar la revisión.', cuerpo: ['Tu ficha no cambió. Probá de nuevo en unos segundos.'] }) : null;
  }

  function comparacion(p: Paciente, informado: LoInformado): HTMLElement {
    const { difieren, coinciden } = comparar(p, informado);
    const filas = difieren.map((campo) =>
      el(
        'tr',
        { class: 'border-t border-borde first:border-t-0 max-md:grid max-md:gap-1 max-md:py-2' },
        el('th', { scope: 'row', class: 'py-2 pr-4 text-left font-semibold text-texto' }, ETIQUETA[campo]),
        el('td', { 'data-col': 'Guardado', class: 'py-2 pr-4 text-texto max-md:before:content-[attr(data-col)] max-md:before:block max-md:before:text-xs max-md:before:text-texto-tenue' }, valorDe(p, campo) ?? 'Sin email'),
        el(
          'td',
          { 'data-col': 'En la reserva', 'data-reserva': true, class: 'py-2 px-2 rounded-md bg-sobreturno-bg text-texto max-md:before:content-[attr(data-col)] max-md:before:block max-md:before:text-xs max-md:before:text-texto-tenue' },
          valorDe(informado, campo) ?? 'Sin email',
          el('span', { 'data-distinto': true, class: 'inline-flex items-center gap-1 ml-2 text-xs font-semibold text-sobreturno-fg' }, icono('alerta', 'w-icono-sm h-icono-sm flex-none'), 'Distinto'),
        ),
      ),
    );
    const mantener = boton({ texto: `Mantener «${nombreCompleto(p)}»`, requiereConexion: true, alHacerClic: () => void guardar(() => confirmarRevision(http, p.id, informado.informadoAt)) });
    const usar = boton({
      texto: `Usar «${nombreCompleto(informado)}»`,
      variante: 'secundario',
      requiereConexion: true,
      alHacerClic: () => void guardar(() => corregirPaciente(http, p.id, informado.informadoAt, { nombre: informado.nombre, apellido: informado.apellido, email: informado.email ?? '' })),
    });
    const corregir = boton({ texto: 'Corregir a mano', icono: 'lapiz', variante: 'fantasma', requiereConexion: true, alHacerClic: () => abrirFormulario() });
    for (const b of [mantener, usar, corregir]) b.disabled = guardando;
    return tarjeta(
      avisoOtraReserva(),
      avisoAuth({ tono: 'info', icono: 'info', cuerpo: ['Reservó con un DNI que ya tenías, pero escribió el nombre distinto. ', el('b', {}, 'No cambiamos nada'), ': tu ficha sigue como estaba hasta que elijas.'], rol: null }),
      el(
        'table',
        { class: 'w-full border-collapse text-sm' },
        el('caption', { class: 'text-left font-bold text-base mb-2' }, '¿Qué datos quedan?'),
        el(
          'thead',
          { class: 'max-md:sr-only' },
          el('tr', {}, ...['Dato', 'Guardado', 'En la reserva'].map((t) => el('th', { scope: 'col', class: 'pb-2 pr-4 text-left text-xs font-semibold text-texto-tenue' }, t))),
        ),
        el('tbody', {}, ...filas),
      ),
      el('p', { 'data-coinciden': true, class: 'flex gap-2 items-center mt-3 mb-0 text-sm text-texto-tenue' }, icono('check', 'w-icono-sm h-icono-sm flex-none'), `Coinciden ${enumerar([...coinciden, 'telefono' as const].map((c) => CON_ARTICULO[c]))}.`),
      avisoErrorAlGuardar(),
      el('div', { class: 'flex flex-wrap gap-2 mt-4' }, mantener, usar, corregir),
      el('p', { class: 'mt-3 mb-0 text-sm text-texto-tenue' }, 'Cualquiera de las tres marca al paciente como revisado y queda en el Registro de cambios, solo con los datos que cambien.'),
    );
  }

  function formulario(p: Paciente, informado: LoInformado): HTMLElement {
    const inicial = borrador ?? { nombre: p.nombre, apellido: p.apellido, email: p.email ?? '' };
    const control = (nombre: CampoCorregible, valor: string, largo: number, tipo = 'text') => {
      const input = el('input', { name: nombre, type: tipo, autocomplete: 'off', maxlength: String(largo) });
      input.value = valor;
      if (tipo === 'email') input.inputMode = 'email';
      input.disabled = guardando;
      return input;
    };
    const campos = {
      nombre: campoConfig({ etiqueta: 'Nombre', control: control('nombre', inicial.nombre, LARGO_MAXIMO_NOMBRE) }),
      apellido: campoConfig({ etiqueta: 'Apellido', control: control('apellido', inicial.apellido, LARGO_MAXIMO_NOMBRE) }),
      email: campoConfig({ etiqueta: 'Email', opcional: true, control: control('email', inicial.email, LARGO_MAXIMO_EMAIL, 'email') }),
    };
    const soloLectura = (dato: string, valor: string, motivo: string) =>
      el('div', { class: 'grid gap-1 mb-4' }, el('span', { class: 'font-medium text-sm' }, dato), el('div', { class: 'min-h-touch flex items-center px-3 rounded-md bg-superficie-tenue text-texto tabular' }, valor), el('p', { class: 'm-0 text-sm text-texto-tenue' }, motivo));
    const resumen = el('div', {});
    const leer = (): DatosCorregidos => ({ nombre: campos.nombre.control.value, apellido: campos.apellido.control.value, email: campos.email.control.value });

    const enviar = boton({ texto: 'Guardar y marcar revisado', tipo: 'submit', requiereConexion: true });
    enviar.disabled = guardando;
    const cancelar = boton({ texto: 'Cancelar', variante: 'fantasma', alHacerClic: () => cerrarFormulario() });
    const form = el(
      'form',
      { novalidate: true },
      el('div', { class: 'grid md:grid-cols-2 md:gap-4' }, campos.nombre.contenedor, campos.apellido.contenedor),
      campos.email.contenedor,
      el('div', { class: 'grid md:grid-cols-2 md:gap-4' }, soloLectura('DNI', dniLegible(p.dni), 'Identifica al paciente y no se edita.'), soloLectura('Teléfono', telefonoLegible(p.telefono), 'No se edita en esta versión.')),
      el('div', { class: 'flex flex-wrap gap-2' }, enviar, cancelar),
    );
    form.addEventListener('submit', (evento) => {
      evento.preventDefault();
      const datos = leer();
      borrador = datos;
      const errores = validarCorreccion(datos);
      for (const nombre of ['nombre', 'apellido', 'email'] as const) campos[nombre].error(errores.find((e) => e.campo === nombre)?.mensaje ?? null);
      if (errores.length > 0) {
        const nodo = resumenDeErrores(`Revisá ${errores.length} ${errores.length === 1 ? 'dato' : 'datos'}`, errores.map((e) => ({ id: campos[e.campo].control.id, mensaje: e.resumen })));
        resumen.replaceChildren(nodo);
        nodo.focus();
        return;
      }
      resumen.replaceChildren();
      void guardar(() => corregirPaciente(http, p.id, informado.informadoAt, datos));
    });

    const escrito = comparar(p, informado).difieren.map((campo) => valorDe(informado, campo) ?? 'sin email').join(' ');
    return tarjeta(
      el('h2', { class: 'm-0 text-lg font-bold' }, 'Corregir datos'),
      el('p', { class: 'mt-1 mb-4 text-sm text-texto-tenue' }, `En la reserva escribió «${escrito}». Guardá cómo tiene que quedar.`),
      avisoOtraReserva(),
      avisoErrorAlGuardar(),
      resumen,
      form,
    );
  }

  function porQueAparece(informado: LoInformado): HTMLElement {
    const turno = informado.turno;
    return el(
      'aside',
      { class: 'bg-superficie border border-borde rounded-lg shadow-sm p-4 md:p-5 text-sm' },
      el('h2', { class: 'm-0 text-base font-bold' }, '¿Por qué aparece?'),
      el(
        'p',
        { class: 'mt-2 mb-0 text-texto-tenue' },
        'Tus pacientes se identifican por DNI. Si alguien reserva con un DNI que ya tenés y otro nombre, el turno queda con tu paciente y te avisamos para que confirmes. Puede ser un error de tipeo o un familiar que reservó con el DNI equivocado.',
      ),
      turno === null
        ? null
        : el(
            'p',
            { class: 'mt-2 mb-0 text-texto-tenue' },
            'Turno de esa reserva: ',
            el('a', { href: rutaDelTurno(turno.id), 'data-link': true, class: 'font-semibold text-primario underline' }, `${formatearDia(turno.inicio, zona)}, ${formatearHora(turno.inicio, zona)} · ${turno.prestacion}`),
          ),
    );
  }

  function pintar(): void {
    if (paciente === null) return;
    const p = paciente;
    const informado = p.requiereRevision ? p.informado : null;
    const encabezado: (HTMLElement | null)[] = [
      volver(informado !== null),
      el('h2', { class: 'flex flex-wrap items-center gap-2 m-0 text-xl font-bold' }, nombreDeLista(p), ' ', etiqueta(informado !== null)),
      el('p', { class: 'mt-1 mb-4 text-sm text-texto-tenue tabular' }, informado === null ? `DNI ${dniLegible(p.dni)}` : `DNI ${dniLegible(p.dni)} · reservó por tu link el ${formatearDia(informado.informadoAt, zona)}`),
      yaRevisado ? avisoAuth({ tono: 'exito', icono: 'check', cuerpo: ['Este paciente ya no tiene una revisión pendiente: la resolvieron en otra pestaña o dispositivo.'], rol: 'status' }) : null,
    ];
    const principal = informado === null ? datosDelPaciente(p) : modo === 'corregir' ? formulario(p, informado) : comparacion(p, informado);
    raiz.replaceChildren(
      ...encabezado.filter((n): n is HTMLElement => n !== null),
      el('div', { class: 'grid gap-4 lg:grid-cols-[2fr_1fr] lg:items-start' }, principal, informado === null ? null : porQueAparece(informado)),
    );
  }

  function abrirFormulario(): void {
    modo = 'corregir';
    errorAlGuardar = false;
    otraReserva = false;
    pintar();
    raiz.querySelector<HTMLInputElement>('input[name="nombre"]')?.focus();
  }

  function cerrarFormulario(): void {
    modo = 'comparar';
    borrador = null;
    errorAlGuardar = false;
    pintar();
  }

  async function guardar(enviar: () => Promise<Paciente>): Promise<void> {
    if (paciente === null || guardando) return;
    const anterior = paciente;
    guardando = true;
    errorAlGuardar = false;
    pintar();
    try {
      const revisado = await enviar();
      guardando = false;
      await contexto.pacientes?.actualizar();
      dejarAvisoDeRevision(`Listo: la ficha de ${nombreDeLista(revisado.requiereRevision ? anterior : revisado)} quedó revisada.`);
      await contexto.navegacion.navegar(RUTA_PARA_REVISAR);
    } catch (error) {
      guardando = false;
      if (error instanceof ApiError && error.codigo === 'SESION_VENCIDA') return;
      if (error instanceof ApiError && (error.codigo === 'SIN_REVISION_PENDIENTE' || error.codigo === 'CONFLICTO_CONCURRENCIA')) {
        if (error.codigo === 'SIN_REVISION_PENDIENTE') {
          yaRevisado = true;
          modo = 'comparar';
          void contexto.pacientes?.actualizar();
        } else {
          otraReserva = true;
        }
        await cargar({ conEsqueleto: false });
        return;
      }
      errorAlGuardar = true;
      pintar();
    }
  }

  async function cargar({ conEsqueleto }: { readonly conEsqueleto: boolean }): Promise<void> {
    if (conEsqueleto) raiz.replaceChildren(esqueleto({ etiqueta: 'Cargando paciente…', filas: 3 }));
    try {
      paciente = await detallePaciente(http, id);
      pintar();
    } catch (error) {
      if (error instanceof ApiError && error.codigo === 'SESION_VENCIDA') return;
      paciente = null;
      const reintentar = () => void cargar({ conEsqueleto: true });
      if (error instanceof ApiError && error.status === 404) {
        raiz.replaceChildren(
          tarjetaEstado(
            { icono: 'alerta', titulo: 'No encontramos este paciente', texto: 'Puede que el enlace esté mal o que el paciente no sea de tu consultorio.', acciones: [enlaceBoton({ texto: 'Ir a pacientes', variante: 'secundario', href: RUTA_PACIENTES })] },
            { role: 'alert' },
          ),
        );
      } else if (error instanceof ApiError && error.codigo === 'SIN_CONEXION') {
        raiz.replaceChildren(
          tarjetaEstado({ icono: 'sin-conexion', titulo: 'Sin conexión', texto: 'No podés revisar pacientes hasta que vuelva la conexión.', acciones: [boton({ texto: 'Reintentar', icono: 'reintentar', alHacerClic: reintentar })] }),
        );
      } else {
        raiz.replaceChildren(errorRecuperable({ titulo: 'No pudimos cargar el paciente', alReintentar: reintentar }));
      }
    }
  }

  void cargar({ conEsqueleto: true });
}
