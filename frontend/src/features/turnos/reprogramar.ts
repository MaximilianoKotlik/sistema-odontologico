import { el } from '../../shared/dom';
import { formatearDia } from '../../shared/fechas';
import { aviso } from '../../shared/ui/aviso';
import { icono } from '../../shared/ui/iconos';
import type { DetalleTurno } from '../agenda/api';
import { nombreCompleto } from '../agenda/modelo';
import type { EntornoDeAccion } from '../agenda/puntos-de-entrada';
import { instanteLocal } from '../agenda/tiempo';
import { CLASE_CHIP } from '../configuracion/ui';
import { obtenerPasoHuecos, reprogramarTurno } from './api';
import { traducirErrorDeTurno } from './errores';
import { abrirFormulario } from './formulario';
import { iniciosLibres } from './huecos';
import { avisoDeError, camposDiaYHora, cuandoCorto, datosDelDia, diaDelTurno, referenciaDelTurno, type DatosDelDia } from './ui';
import { finDerivado, validarHora } from './validaciones';

/**
 * Reprogramar (C-14, design D6; RN-AG-11, RN-AG-12): día y hora, con los huecos del día donde entra la duración del
 * turno, cada `paso_huecos_min` del consultorio. Conserva la duración y el link de gestión del paciente.
 */
export function abrirReprogramar(turno: DetalleTurno, entorno: EntornoDeAccion): void {
  const { zona, http } = entorno;
  const diaActual = diaDelTurno(turno, zona);
  const campos = camposDiaYHora(diaActual, '');
  const tituloHuecos = el('p', { 'data-titulo-huecos': true, class: 'm-0 font-medium text-sm' });
  const listaHuecos = el('ul', { 'aria-labelledby': 'titulo-huecos', class: 'flex flex-wrap gap-2 list-none m-0 mt-2 p-0' });
  tituloHuecos.id = 'titulo-huecos';
  const ayudaHuecos = el('p', { class: 'm-0 mt-2 text-sm text-texto-tenue' });
  const nuevoHorario = el('p', {
    'data-nuevo-horario': true,
    hidden: true,
    'aria-live': 'polite',
    class: 'flex items-center gap-2 m-0 mt-3 p-3 rounded-md border border-confirmado-bd bg-confirmado-bg text-confirmado-fg font-semibold tabular',
  });
  let paso = 15;
  let datos: DatosDelDia = { turnos: [], bloqueos: [], franjas: null };

  const nombreDelDia = (dia: string) => formatearDia(dia, zona).split(' ')[0] ?? '';

  function pintarHuecos(): void {
    const dia = campos.dia.control.value;
    if (dia === '') return;
    tituloHuecos.textContent = `Huecos libres el ${nombreDelDia(dia)} para ${turno.duracionMin} min`;
    const inicios = iniciosLibres({ dia, zona, ...datos, duracionMin: turno.duracionMin, paso, ahora: new Date(), excluir: turno.id });
    const elegida = campos.hora.control.value;
    listaHuecos.replaceChildren(
      ...inicios.map((hora) => {
        const b = el('button', { type: 'button', 'data-hueco': true, 'aria-pressed': String(hora === elegida), class: `${CLASE_CHIP} justify-center min-w-[calc(var(--space-7)+var(--space-5))] tabular` }, hora);
        b.addEventListener('click', () => {
          campos.hora.control.value = hora;
          alCambiarHora();
        });
        return el('li', {}, b);
      }),
    );
    ayudaHuecos.textContent =
      inicios.length === 0
        ? `No hay huecos libres el ${nombreDelDia(dia)} para ${turno.duracionMin} min. Probá otro día o escribí una hora.`
        : `Mostramos los inicios donde entra el turno completo, cada ${paso} minutos (el paso de huecos de tu configuración). También podés escribir otra hora.`;
  }

  function alCambiarHora(): void {
    campos.hora.error(null);
    const dia = campos.dia.control.value;
    const hora = campos.hora.control.value;
    for (const b of listaHuecos.querySelectorAll('[data-hueco]')) b.setAttribute('aria-pressed', String(b.textContent === hora));
    if (dia === '' || validarHora(hora) !== null) {
      nuevoHorario.hidden = true;
      return;
    }
    const [diaSemana = '', numero = ''] = formatearDia(dia, zona).split(' ');
    nuevoHorario.replaceChildren(icono('flecha', 'w-icono-sm h-icono-sm flex-none'), `Nuevo horario: ${diaSemana} ${numero} · ${hora} a ${finDerivado(dia, hora, turno.duracionMin, zona).hora}`);
    nuevoHorario.hidden = false;
  }

  async function cargarDia(): Promise<void> {
    const dia = campos.dia.control.value;
    if (dia === '') return;
    datos = await datosDelDia(http, dia, zona);
    if (campos.dia.control.value === dia) pintarHuecos();
  }

  campos.dia.control.addEventListener('change', () => {
    campos.dia.error(null);
    void cargarDia();
    alCambiarHora();
  });
  campos.hora.control.addEventListener('input', alCambiarHora);

  const inicioElegido = () => instanteLocal(campos.dia.control.value, campos.hora.control.value, zona).toISOString();

  abrirFormulario({
    titulo: 'Reprogramar turno',
    contenido: el(
      'div',
      {},
      referenciaDelTurno(turno, zona),
      campos.elemento,
      tituloHuecos,
      listaHuecos,
      ayudaHuecos,
      nuevoHorario,
      el('p', { class: 'm-0 mt-3 text-sm text-texto-tenue' }, 'El paciente conserva su link: ahí va a ver el horario nuevo.'),
    ),
    textoGuardar: 'Mover turno',
    textoCargando: 'Moviendo…',
    conCambios: () => campos.hora.control.value !== '' || campos.dia.control.value !== diaActual,
    validar: () => {
      if (campos.dia.control.value === '') {
        campos.dia.error('Elegí el día.');
        campos.dia.control.focus();
        return false;
      }
      const error = validarHora(campos.hora.control.value) ?? (inicioElegido() === new Date(turno.inicio).toISOString() ? 'Elegí un horario distinto al actual.' : null);
      campos.hora.error(error);
      if (error !== null) campos.hora.control.focus();
      return error === null;
    },
    ejecutar: async () => {
      const inicio = inicioElegido();
      await reprogramarTurno(http, turno.id, { inicio, version: turno.version ?? 0 });
      aviso(`Movimos el turno de ${nombreCompleto(turno.paciente)} al ${cuandoCorto(inicio, zona)}`, { tono: 'exito' });
      entorno.alTerminar();
    },
    alFallar: (error, { zonaDeAviso, ventana, reintentar }) => {
      const traducido = traducirErrorDeTurno(error, { operacion: 'reprogramar', zona, dia: campos.dia.control.value, ...datos });
      for (const { campo, mensaje } of traducido.campos) if (campo === 'hora') campos.hora.error(mensaje);
      zonaDeAviso.replaceChildren(
        avisoDeError(traducido, {
          alReintentar: reintentar,
          alVerActualizado: () => {
            ventana.cerrar();
            entorno.alTerminar();
          },
        }),
      );
    },
  });

  void Promise.all([obtenerPasoHuecos(http), datosDelDia(http, diaActual, zona)]).then(([pasoDelConsultorio, delDia]) => {
    paso = pasoDelConsultorio;
    datos = delDia;
    pintarHuecos();
  });
}
