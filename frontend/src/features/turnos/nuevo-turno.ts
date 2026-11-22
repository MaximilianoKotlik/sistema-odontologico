import { el } from '../../shared/dom';
import { hoyEn } from '../../shared/fechas';
import { ApiError } from '../../shared/http';
import { aviso } from '../../shared/ui/aviso';
import { boton, clasesBoton } from '../../shared/ui/boton';
import { icono } from '../../shared/ui/iconos';
import { nombreCompleto, nombreCorto } from '../agenda/modelo';
import type { EntornoDeAccion } from '../agenda/puntos-de-entrada';
import { diaLocal, horaLocal, instanteLocal } from '../agenda/tiempo';
import { CLASE_CONTROL, campoConfig, type CampoConfig } from '../configuracion/ui';
import { buscarPacientes, crearTurno, obtenerPrestacionesActivas, type PacienteDelTurno, type PacienteEncontrado, type PrestacionActiva } from './api';
import { traducirErrorDeTurno } from './errores';
import { abrirFormulario, type FormularioAbierto } from './formulario';
import { dniLegible, telefonoLegible } from '../agenda/formato';
import { avisoDeError, camposDiaYHora, cuandoCorto, datosDelDia, lineaDeFin, seccion, type DatosDelDia } from './ui';
import { finDerivado, validarHora, validarPacienteNuevo, type CampoPaciente } from './validaciones';

/**
 * Nuevo turno y sobreturno (C-14, FL-03; design D3 a D5; mockup `nuevo-turno.html` aprobado en #31). Se abre desde
 * "Nuevo turno" o desde un hueco libre de C-13 con el horario ya cargado. El fin se calcula con la prestación y nunca
 * se escribe (RN-AG-12). Avisar una superposición antes de enviar es solo una sugerencia: el backend decide.
 */

export interface OpcionesNuevoTurno {
  /** Espera antes de buscar mientras se escribe (los tests usan 0). */
  readonly esperaBusquedaMs?: number;
}

const MINIMO_BUSQUEDA = 3;
const CON_AGENDA = new Set(['TURNO_SOLAPADO', 'HORARIO_BLOQUEADO', 'FUERA_DE_DISPONIBILIDAD']);

type ModoPaciente = { readonly tipo: 'buscar' } | { readonly tipo: 'elegido'; readonly paciente: PacienteEncontrado } | { readonly tipo: 'nuevo' };

const activo = (estado: string) => estado === 'RESERVADO' || estado === 'CONFIRMADO';

function datosDelPaciente(p: PacienteEncontrado): string {
  return [p.dni === null ? null : `DNI ${dniLegible(p.dni)}`, p.telefono === null ? null : telefonoLegible(p.telefono)].filter((x) => x !== null).join(' · ');
}

export function abrirNuevoTurno(inicio: string | null, entorno: EntornoDeAccion, opciones: OpcionesNuevoTurno = {}): void {
  const { http, zona } = entorno;
  const esperaBusqueda = opciones.esperaBusquedaMs ?? 250;
  const diaInicial = inicio === null ? hoyEn(zona) : diaLocal(inicio, zona);
  const horaInicial = inicio === null ? '' : horaLocal(inicio, zona);

  let modo: ModoPaciente = { tipo: 'buscar' };
  let prestaciones: PrestacionActiva[] = [];
  let prestacion: PrestacionActiva | null = null;
  let datos: DatosDelDia = { turnos: [], bloqueos: [], franjas: null };
  let formulario: FormularioAbierto | null = null;

  // Paciente: buscador, paciente elegido o alta rápida (D4).
  const buscador = el('input', {
    name: 'buscar',
    type: 'search',
    autocomplete: 'off',
    placeholder: 'Nombre, DNI o teléfono',
    'aria-label': 'Buscar paciente por nombre, DNI o teléfono',
    'aria-describedby': 'buscar-ayuda',
    class: `${CLASE_CONTROL} pl-[calc(var(--space-3)+var(--space-5)+var(--space-1))]`,
  });
  const ayudaBusqueda = el('p', { id: 'buscar-ayuda', class: 'm-0 mt-1 text-sm text-texto-tenue' }, 'Buscá entre tus pacientes o cargá uno nuevo.');
  const errorPaciente = el('p', { hidden: true, role: 'alert', class: 'm-0 mt-1 flex gap-1 items-start text-sm font-medium text-peligro' });
  const resultados = el('ul', { 'aria-label': 'Pacientes encontrados', class: 'list-none m-0 mt-2 p-0 border border-borde rounded-md overflow-hidden bg-superficie divide-y divide-borde' });
  const anuncioBusqueda = el('p', { 'aria-live': 'polite', class: 'sr-only' });
  const zonaPaciente = el('div', {});

  const alta: Record<CampoPaciente, CampoConfig<HTMLInputElement>> = {
    nombre: campoConfig({ etiqueta: 'Nombre', control: el('input', { name: 'nombre', type: 'text', autocomplete: 'off' }) }),
    apellido: campoConfig({ etiqueta: 'Apellido', control: el('input', { name: 'apellido', type: 'text', autocomplete: 'off' }) }),
    dni: campoConfig({ etiqueta: 'DNI', ayuda: 'Solo números, sin puntos.', control: el('input', { name: 'dni', type: 'text', inputmode: 'numeric', autocomplete: 'off' }) }),
    telefono: campoConfig({ etiqueta: 'Celular', control: el('input', { name: 'telefono', type: 'tel', autocomplete: 'off' }) }),
    email: campoConfig({ etiqueta: 'Email', opcional: true, control: el('input', { name: 'email', type: 'email', autocomplete: 'off', placeholder: 'paciente@example.com' }) }),
  };

  function mostrarErrorPaciente(mensaje: string | null): void {
    errorPaciente.hidden = mensaje === null;
    errorPaciente.replaceChildren(...(mensaje === null ? [] : [icono('alerta', 'w-icono-sm h-icono-sm flex-none mt-1'), el('span', {}, mensaje)]));
  }

  const botonResultado = (contenido: (Node | string)[], alElegir: () => void, clase = '') => {
    const b = el(
      'button',
      { type: 'button', class: `flex items-center justify-between gap-2 w-full min-h-[calc(var(--touch-min)+var(--space-2))] px-3 py-2 border-0 bg-transparent text-left text-texto font-sans text-base cursor-pointer hover:bg-superficie-tenue ${clase}` },
      ...contenido,
    );
    b.addEventListener('click', alElegir);
    return el('li', {}, b);
  };

  const itemNuevo = () => botonResultado([el('span', { class: 'flex items-center gap-2' }, icono('persona-mas', 'w-icono-sm h-icono-sm flex-none'), 'Cargar paciente nuevo')], () => cambiarModo({ tipo: 'nuevo' }), '!justify-start text-primario font-semibold');

  function pintarResultados(encontrados: readonly PacienteEncontrado[] | null): void {
    const items = (encontrados ?? []).map((p) =>
      botonResultado(
        [
          el('span', { class: 'min-w-0' }, el('span', { class: 'block font-semibold' }, nombreCompleto(p)), ' ', el('small', { class: 'block text-sm text-texto-tenue [overflow-wrap:anywhere]' }, datosDelPaciente(p))),
          icono('derecha', 'w-icono-sm h-icono-sm flex-none text-texto-tenue'),
        ],
        () => cambiarModo({ tipo: 'elegido', paciente: p }),
      ),
    );
    resultados.replaceChildren(...items, itemNuevo());
  }

  let busquedaActual = 0;
  let temporizador: ReturnType<typeof setTimeout> | null = null;
  buscador.addEventListener('input', () => {
    mostrarErrorPaciente(null);
    const texto = buscador.value.trim();
    if (temporizador !== null) clearTimeout(temporizador);
    busquedaActual += 1;
    if (texto.length < MINIMO_BUSQUEDA) {
      pintarResultados(null);
      anuncioBusqueda.textContent = '';
      return;
    }
    const numero = busquedaActual;
    temporizador = setTimeout(() => {
      buscarPacientes(http, texto)
        .then((encontrados) => {
          if (numero !== busquedaActual) return;
          pintarResultados(encontrados);
          anuncioBusqueda.textContent = encontrados.length === 1 ? '1 paciente encontrado' : `${encontrados.length} pacientes encontrados`;
        })
        .catch((error: unknown) => {
          if (numero !== busquedaActual || (error instanceof ApiError && error.codigo === 'SESION_VENCIDA')) return;
          pintarResultados([]);
          anuncioBusqueda.textContent = 'No pudimos buscar. Probá de nuevo o cargá un paciente nuevo.';
        });
    }, esperaBusqueda);
  });

  function cambiarModo(nuevo: ModoPaciente): void {
    modo = nuevo;
    mostrarErrorPaciente(null);
    if (modo.tipo === 'buscar') {
      zonaPaciente.replaceChildren(
        el('div', { class: 'relative' }, icono('buscar', 'absolute left-3 top-1/2 -translate-y-1/2 w-icono-sm h-icono-sm text-texto-tenue pointer-events-none'), buscador),
        ayudaBusqueda,
        errorPaciente,
        resultados,
        anuncioBusqueda,
      );
      pintarResultados(null);
      buscador.focus();
    } else if (modo.tipo === 'elegido') {
      const cambiar = boton({ texto: 'Cambiar', variante: 'fantasma', alHacerClic: () => cambiarModo({ tipo: 'buscar' }) });
      zonaPaciente.replaceChildren(
        el(
          'div',
          { 'data-paciente-elegido': true, class: 'flex items-center gap-3 px-3 py-2 rounded-md border-control border-primario bg-primario-tinte' },
          el('div', { class: 'flex-1 min-w-0' }, el('span', { class: 'block font-semibold' }, nombreCompleto(modo.paciente)), ' ', el('small', { class: 'text-sm text-texto-tenue' }, datosDelPaciente(modo.paciente))),
          cambiar,
        ),
      );
      cambiar.focus();
    } else {
      const buscarOtro = boton({ texto: 'Buscar otro', variante: 'fantasma', alHacerClic: () => cambiarModo({ tipo: 'buscar' }) });
      zonaPaciente.replaceChildren(
        el(
          'div',
          { 'data-alta': true, class: 'px-3 pt-3 pb-1 mt-2 rounded-md border border-dashed border-borde-fuerte' },
          el('div', { class: 'flex items-center justify-between gap-2 mb-3 font-semibold' }, el('span', {}, 'Paciente nuevo'), buscarOtro),
          ...Object.values(alta).map((c) => c.contenedor),
        ),
      );
      alta.nombre.control.focus();
    }
    actualizar();
  }

  // Prestación (opciones de un toque) y día y hora con el fin derivado.
  const zonaPrestaciones = el('div', { class: 'grid gap-2 sm:grid-cols-2' });
  const errorPrestacion = el('p', { hidden: true, role: 'alert', class: 'm-0 mt-1 text-sm font-medium text-peligro' });
  const campos = camposDiaYHora(diaInicial, horaInicial);
  const fin = lineaDeFin();
  const zonaSuperpone = el('div', {});

  function pintarPrestaciones(): void {
    if (prestaciones.length === 0) {
      const enlace = el('a', { href: '/app/configuracion/prestaciones', 'data-link': true, class: 'font-semibold text-primario underline' }, 'Cargar prestaciones');
      enlace.addEventListener('click', () => formulario?.ventana.cerrar());
      zonaPrestaciones.replaceChildren(el('p', { class: 'm-0 text-sm text-texto-tenue sm:col-span-2' }, 'Todavía no tenés prestaciones activas. ', enlace));
      return;
    }
    zonaPrestaciones.replaceChildren(
      ...prestaciones.map((p) => {
        const elegida = prestacion?.id === p.id;
        const b = el(
          'button',
          {
            type: 'button',
            'data-prestacion': p.id,
            'aria-pressed': String(elegida),
            class:
              'flex items-center justify-between gap-2 min-h-touch px-3 py-2 rounded-md border-control border-borde-fuerte bg-superficie text-texto text-left font-sans text-base cursor-pointer ' +
              'aria-pressed:border-primario aria-pressed:bg-primario-tinte aria-pressed:shadow-[inset_0_0_0_1px_var(--color-primary)] aria-pressed:font-semibold',
          },
          el('span', {}, p.nombre),
          ' ',
          el('small', { class: 'text-sm text-texto-tenue tabular' }, `${p.duracionMin} min`),
        );
        b.addEventListener('click', () => {
          prestacion = p;
          errorPrestacion.hidden = true;
          formulario?.zonaDeAviso.replaceChildren();
          pintarPrestaciones();
          [...zonaPrestaciones.querySelectorAll<HTMLButtonElement>('[data-prestacion]')].find((otro) => otro.dataset['prestacion'] === p.id)?.focus();
          actualizar();
        });
        return b;
      }),
    );
  }

  // Sobreturno (RN-AG-04).
  const casilla = el('input', { name: 'sobreturno', type: 'checkbox', class: 'flex-none w-icono h-icono mt-1 accent-primario' });
  const textoCasilla = el('span', { class: 'block text-sm' });
  const cajaSobreturno = el(
    'label',
    { class: 'flex gap-3 items-start px-3 py-2 rounded-md border-2 border-dashed border-sobreturno-bd bg-superficie cursor-pointer has-[:checked]:bg-sobreturno-bg' },
    casilla,
    el('span', {}, el('strong', { class: 'block' }, 'Sobreturno'), textoCasilla),
  );
  casilla.addEventListener('change', () => actualizar());

  function horarioElegido(): { readonly inicio: string; readonly fin: string; readonly horaFin: string } | null {
    const dia = campos.dia.control.value;
    const hora = campos.hora.control.value;
    if (dia === '' || prestacion === null || validarHora(hora) !== null) return null;
    const calculado = finDerivado(dia, hora, prestacion.duracionMin, zona);
    return { inicio: instanteLocal(dia, hora, zona).toISOString(), fin: calculado.instante, horaFin: calculado.hora };
  }

  function actualizar(): void {
    const horario = horarioElegido();
    fin.mostrar(horario === null ? 'Elegí la prestación y la hora' : horario.horaFin);
    textoCasilla.textContent = casilla.checked
      ? 'Se va a ver marcado en tu agenda y nunca se ofrece en tu página. No puede caer en un horario bloqueado.'
      : 'Para una urgencia: puede superponerse con otro turno o quedar fuera de tu horario.';

    const conRechazo = formulario?.zonaDeAviso.querySelector('[data-error-turno]') != null;
    const choca =
      horario === null || casilla.checked || conRechazo
        ? undefined
        : datos.turnos.find((t) => activo(t.estado) && !t.esSobreturno && Date.parse(t.inicio) < Date.parse(horario.fin) && Date.parse(horario.inicio) < Date.parse(t.fin));
    zonaSuperpone.replaceChildren(
      ...(choca === undefined
        ? []
        : [
            el(
              'div',
              { 'data-aviso-superpone': true, role: 'status', class: 'flex gap-3 items-start px-4 py-3 mt-3 rounded-md border bg-primario-tinte border-reservado-bd text-reservado-fg text-sm' },
              icono('info', 'w-icono h-icono flex-none'),
              el('div', {}, el('strong', { class: 'block text-base' }, `Ese horario se superpone con ${horaLocal(choca.inicio, zona)} · ${nombreCorto(choca.paciente)}`), el('p', { class: 'm-0' }, 'Si es una urgencia, marcalo como sobreturno. Si no, elegí otro horario.')),
            ),
          ]),
    );

    if (formulario !== null && formulario.guardar.getAttribute('aria-busy') !== 'true') {
      formulario.guardar.disabled = horario === null || modo.tipo === 'buscar';
    }
  }

  async function cargarDia(): Promise<void> {
    const dia = campos.dia.control.value;
    if (dia === '') return;
    const delDia = await datosDelDia(http, dia, zona);
    if (campos.dia.control.value !== dia) return;
    datos = delDia;
    actualizar();
  }

  /** Un rechazo habla del horario que se envió: si cambia lo cargado, ya no aplica. */
  const olvidarRechazo = () => formulario?.zonaDeAviso.replaceChildren();

  campos.dia.control.addEventListener('change', () => {
    campos.dia.error(null);
    olvidarRechazo();
    void cargarDia();
    actualizar();
  });
  campos.hora.control.addEventListener('input', () => {
    campos.hora.error(null);
    olvidarRechazo();
    actualizar();
  });

  function validar(): boolean {
    const errores: HTMLElement[] = [];
    if (modo.tipo === 'buscar') {
      mostrarErrorPaciente('Elegí un paciente o cargá uno nuevo.');
      errores.push(buscador);
    }
    if (modo.tipo === 'nuevo') {
      for (const c of Object.values(alta)) c.error(null);
      for (const { campo, mensaje } of validarPacienteNuevo(valoresDelAlta())) {
        alta[campo].error(mensaje);
        errores.push(alta[campo].control);
      }
    }
    if (prestacion === null) {
      errorPrestacion.textContent = 'Elegí una prestación.';
      errorPrestacion.hidden = false;
      const primera = zonaPrestaciones.querySelector<HTMLElement>('button');
      if (primera !== null) errores.push(primera);
    }
    if (campos.dia.control.value === '') {
      campos.dia.error('Elegí el día.');
      errores.push(campos.dia.control);
    }
    const errorHora = validarHora(campos.hora.control.value);
    campos.hora.error(errorHora);
    if (errorHora !== null) errores.push(campos.hora.control);
    errores[0]?.focus();
    return errores.length === 0;
  }

  const valoresDelAlta = () => ({
    nombre: alta.nombre.control.value,
    apellido: alta.apellido.control.value,
    dni: alta.dni.control.value,
    telefono: alta.telefono.control.value,
    email: alta.email.control.value,
  });

  function pacienteDelTurno(): PacienteDelTurno {
    return modo.tipo === 'elegido' ? { tipo: 'existente', id: modo.paciente.id } : { tipo: 'nuevo', datos: valoresDelAlta() };
  }

  const nombreDelPaciente = () => (modo.tipo === 'elegido' ? nombreCompleto(modo.paciente) : `${alta.nombre.control.value.trim()} ${alta.apellido.control.value.trim()}`);

  formulario = abrirFormulario({
    titulo: 'Nuevo turno',
    contenido: el(
      'div',
      {},
      seccion('Paciente', zonaPaciente),
      seccion('Prestación', zonaPrestaciones, errorPrestacion),
      seccion('Día y hora', campos.elemento, fin.elemento, zonaSuperpone),
      cajaSobreturno,
    ),
    textoGuardar: 'Crear turno',
    textoCargando: 'Creando…',
    conCambios: () => modo.tipo !== 'buscar' || buscador.value.trim() !== '' || prestacion !== null || casilla.checked || campos.hora.control.value !== horaInicial || campos.dia.control.value !== diaInicial,
    validar,
    ejecutar: async () => {
      const horario = horarioElegido();
      if (horario === null || prestacion === null) return;
      const { pacienteReutilizado } = await crearTurno(http, { paciente: pacienteDelTurno(), prestacionId: prestacion.id, inicio: horario.inicio, esSobreturno: casilla.checked });
      const nombre = nombreDelPaciente();
      aviso(
        `${casilla.checked ? 'Sobreturno creado' : 'Turno creado'}: ${nombre}, ${cuandoCorto(horario.inicio, zona)}${pacienteReutilizado ? '. Ya había un paciente con ese DNI: usamos sus datos.' : ''}`,
        { tono: 'exito' },
      );
      entorno.alTerminar();
    },
    alFallar: async (error, { zonaDeAviso, reintentar }) => {
      const dia = campos.dia.control.value;
      if (CON_AGENDA.has(error.codigo) && datos.turnos.length === 0 && datos.bloqueos.length === 0) datos = await datosDelDia(http, dia, zona);
      const traducido = traducirErrorDeTurno(error, { operacion: 'crear', zona, dia, ...datos });
      const sinCampo: string[] = [];
      for (const { campo, mensaje } of traducido.campos) {
        if (campo === 'hora') campos.hora.error(mensaje);
        else if (campo === 'prestacion') {
          errorPrestacion.textContent = mensaje;
          errorPrestacion.hidden = false;
        } else if (modo.tipo === 'nuevo' && campo in alta) alta[campo as CampoPaciente].error(mensaje);
        else sinCampo.push(mensaje);
      }
      const aMostrar = traducido.campos.length === 0 ? traducido : sinCampo.length > 0 ? { ...traducido, titulo: 'Revisá los datos', texto: sinCampo.join(' ') } : null;
      zonaDeAviso.replaceChildren(
        ...(aMostrar === null
          ? []
          : [
              avisoDeError(aMostrar, {
                alReintentar: reintentar,
                alSobreturno: () => {
                  casilla.checked = true;
                  actualizar();
                  void formulario?.enviar();
                },
              }),
            ]),
      );
      actualizar();
    },
  });

  cambiarModo({ tipo: 'buscar' });
  pintarPrestaciones();
  actualizar();
  void obtenerPrestacionesActivas(http)
    .then((lista) => {
      prestaciones = lista;
      pintarPrestaciones();
    })
    .catch((error: unknown) => {
      if (error instanceof ApiError && error.codigo === 'SESION_VENCIDA') return;
      const reintentar = el('button', { type: 'button', class: `${clasesBoton('secundario')} sm:col-span-2` }, 'Reintentar');
      reintentar.addEventListener('click', () => {
        void obtenerPrestacionesActivas(http).then((lista) => {
          prestaciones = lista;
          pintarPrestaciones();
        });
      });
      zonaPrestaciones.replaceChildren(el('p', { role: 'alert', class: 'm-0 text-sm text-peligro sm:col-span-2' }, 'No pudimos cargar tus prestaciones.'), reintentar);
    });
  void cargarDia();
}
