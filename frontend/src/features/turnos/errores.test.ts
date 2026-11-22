import { describe, expect, it } from 'vitest';
import { ApiError, MENSAJE_PLAN_VENCIDO } from '../../shared/http';
import type { BloqueoAgenda, FranjaDisponible, TurnoAgenda } from '../agenda/modelo';
import { traducirErrorDeTurno, type ContextoDeError } from './errores';

// Tarea 2.3 de C-14 (design D5; textos de los mockups aprobados en #31). Datos ficticios.
const BA = 'America/Argentina/Buenos_Aires';

const turno = (extra: Partial<TurnoAgenda>): TurnoAgenda => ({
  id: 't-carla',
  inicio: '2026-11-20T13:30:00Z',
  fin: '2026-11-20T14:30:00Z',
  duracionMin: 60,
  estado: 'CONFIRMADO',
  esSobreturno: false,
  origen: 'MANUAL',
  prestacion: 'Extracción',
  paciente: { nombre: 'Carla', apellido: 'Ficticia' },
  tieneNota: false,
  canceladoPor: null,
  cambioPaciente: null,
  novedadPacienteAt: null,
  ...extra,
});

const BLOQUEO: BloqueoAgenda = { id: 'b-1', inicio: '2026-11-20T18:00:00Z', fin: '2026-11-20T19:00:00Z', motivo: 'Reunión con el laboratorio' };
// Viernes (5) de 09:00 a 13:00 y de 15:00 a 19:00.
const FRANJAS: FranjaDisponible[] = [
  { diaSemana: 5, horaInicio: '15:00', horaFin: '19:00' },
  { diaSemana: 5, horaInicio: '09:00', horaFin: '13:00' },
];

const contexto = (extra: Partial<ContextoDeError> = {}): ContextoDeError => ({
  operacion: 'crear',
  zona: BA,
  dia: '2026-11-20',
  turnos: [turno({})],
  bloqueos: [BLOQUEO],
  franjas: FRANJAS,
  ...extra,
});

const solapado = new ApiError(409, 'TURNO_SOLAPADO', 'El turno se superpone', {
  turnosEnConflicto: ['t-carla'],
  turnoEnConflicto: { id: 't-carla', inicio: '2026-11-20T13:30:00Z', fin: '2026-11-20T14:30:00Z' },
});

describe('TURNO_SOLAPADO', () => {
  it('al crear: nombra el turno con el que choca y ofrece el sobreturno', () => {
    const e = traducirErrorDeTurno(solapado, contexto());
    expect(e.titulo).toBe('Se superpone con el turno de 10:30 de Carla Ficticia');
    expect(e.texto).toBe('Elegí otro horario o crealo como sobreturno.');
    expect(e.accion).toBe('SOBRETURNO');
    expect(e.tono).toBe('advertencia');
  });

  it('sin el turno en la agenda cargada, nombra solo la hora', () => {
    expect(traducirErrorDeTurno(solapado, contexto({ turnos: [] })).titulo).toBe('Se superpone con el turno de 10:30');
  });

  it('sin datos del conflicto, queda genérico', () => {
    expect(traducirErrorDeTurno(new ApiError(409, 'TURNO_SOLAPADO', ''), contexto()).titulo).toBe('Se superpone con otro turno');
  });

  it('al reprogramar: sugiere otro hueco y no ofrece el sobreturno', () => {
    const e = traducirErrorDeTurno(solapado, contexto({ operacion: 'reprogramar' }));
    expect(e.titulo).toBe('Se superpone con el turno de 10:30 de Carla Ficticia');
    expect(e.texto).toBe('Elegí otro hueco libre. Si es una urgencia, podés crearlo como sobreturno desde la agenda.');
    expect(e.accion).toBeNull();
  });

  it('al ajustar la duración: explica que no alcanza y que la duración no cambió', () => {
    const e = traducirErrorDeTurno(solapado, contexto({ operacion: 'duracion', duracion: { nueva: 75, anterior: 30, finNuevo: '10:45' } }));
    expect(e.titulo).toBe('No alcanza: a las 10:30 empieza el turno de Carla Ficticia');
    expect(e.texto).toBe('Con 75 min terminaría a las 10:45. La duración sigue en 30 min.');
  });
});

describe('FUERA_DE_DISPONIBILIDAD', () => {
  const fuera = new ApiError(409, 'FUERA_DE_DISPONIBILIDAD', '');

  it('al crear: dice cuándo atiende ese día y ofrece el sobreturno', () => {
    const e = traducirErrorDeTurno(fuera, contexto());
    expect(e.titulo).toBe('Ese horario está fuera de tu horario de atención');
    expect(e.texto).toBe('El viernes atendés de 09:00 a 13:00 y de 15:00 a 19:00. Podés crearlo como sobreturno.');
    expect(e.accion).toBe('SOBRETURNO');
  });

  it('un día sin atención lo dice', () => {
    expect(traducirErrorDeTurno(fuera, contexto({ dia: '2026-11-22' })).texto).toBe('El domingo no atendés. Podés crearlo como sobreturno.');
  });

  it('sin el horario cargado, solo ofrece el sobreturno', () => {
    expect(traducirErrorDeTurno(fuera, contexto({ franjas: null })).texto).toBe('Podés crearlo como sobreturno.');
  });

  it('al ajustar la duración: el turno terminaría fuera de horario', () => {
    const e = traducirErrorDeTurno(fuera, contexto({ operacion: 'duracion', duracion: { nueva: 90, anterior: 60, finNuevo: '13:30' } }));
    expect(e.titulo).toBe('No alcanza: terminaría fuera de tu horario de atención');
    expect(e.texto).toBe('Con 90 min terminaría a las 13:30. La duración sigue en 60 min.');
    expect(e.accion).toBeNull();
  });
});

describe('HORARIO_BLOQUEADO', () => {
  const bloqueado = new ApiError(409, 'HORARIO_BLOQUEADO', '', { bloqueosEnConflicto: ['b-1'] });

  it('nombra el bloqueo y no ofrece el sobreturno (RN-AG-04)', () => {
    const e = traducirErrorDeTurno(bloqueado, contexto());
    expect(e.titulo).toBe('Ese horario está bloqueado');
    expect(e.texto).toBe('De 15:00 a 16:00 tenés “Reunión con el laboratorio”. Los bloqueos no admiten turnos ni sobreturnos.');
    expect(e.accion).toBeNull();
    expect(e.tono).toBe('peligro');
  });

  it('sin motivo ni bloqueo cargado, explica la regla', () => {
    expect(traducirErrorDeTurno(new ApiError(409, 'HORARIO_BLOQUEADO', ''), contexto()).texto).toBe('Los bloqueos no admiten turnos ni sobreturnos.');
    expect(traducirErrorDeTurno(bloqueado, contexto({ bloqueos: [{ ...BLOQUEO, motivo: null }] })).texto).toBe('De 15:00 a 16:00 tenés un bloqueo. Los bloqueos no admiten turnos ni sobreturnos.');
  });
});

describe('estado y concurrencia', () => {
  it('TRANSICION_INVALIDA recarga el detalle', () => {
    const e = traducirErrorDeTurno(new ApiError(409, 'TRANSICION_INVALIDA', ''), contexto({ operacion: 'estado' }));
    expect(e.titulo).toBe('Este turno ya cambió de estado');
    expect(e.texto).toBe('Mientras lo mirabas, el turno cambió. Actualizamos el detalle.');
    expect(e.accion).toBe('RECARGAR');
  });

  it('CONFLICTO_CONCURRENCIA ofrece ver la versión actual', () => {
    const e = traducirErrorDeTurno(new ApiError(409, 'CONFLICTO_CONCURRENCIA', ''), contexto({ operacion: 'nota' }));
    expect(e.titulo).toBe('El turno cambió mientras lo editabas');
    expect(e.texto).toBe('No guardamos tu cambio para no pisar el otro. Mirá la versión actual y volvé a intentar.');
    expect(e.accion).toBe('VER_ACTUALIZADO');
  });
});

describe('plan, campos y red', () => {
  it('PLAN_VENCIDO dice que la agenda está en solo lectura', () => {
    const e = traducirErrorDeTurno(new ApiError(403, 'PLAN_VENCIDO', MENSAJE_PLAN_VENCIDO), contexto());
    expect(e.titulo).toBe('Tu agenda está en solo lectura');
    expect(e.texto).toBe('El período de prueba terminó. Activá el plan para volver a dar turnos; tus datos siguen guardados.');
    expect(e.accion).toBeNull();
  });

  it('los errores de dato van al campo', () => {
    expect(traducirErrorDeTurno(new ApiError(400, 'DNI_INVALIDO', ''), contexto()).campos).toEqual([{ campo: 'dni', mensaje: 'El DNI tiene 7 u 8 números, sin puntos.' }]);
    expect(traducirErrorDeTurno(new ApiError(400, 'TELEFONO_INVALIDO', ''), contexto()).campos[0]?.campo).toBe('telefono');
    expect(traducirErrorDeTurno(new ApiError(400, 'DURACION_INVALIDA', ''), contexto({ operacion: 'duracion' })).campos[0]?.campo).toBe('duracion');
    expect(traducirErrorDeTurno(new ApiError(409, 'PRESTACION_INACTIVA', ''), contexto()).campos[0]?.campo).toBe('prestacion');
  });

  it('VALIDACION reparte cada error del backend en su campo', () => {
    const error = new ApiError(400, 'VALIDACION', '', { errores: [{ campo: 'pacienteNuevo.apellido' }, { campo: 'nota' }] });
    expect(traducirErrorDeTurno(error, contexto()).campos.map((c) => c.campo)).toEqual(['apellido', 'nota']);
  });

  it('sin conexión o con un 5xx se puede reintentar sin perder lo cargado', () => {
    expect(traducirErrorDeTurno(new ApiError(0, 'SIN_CONEXION', ''), contexto())).toMatchObject({ titulo: 'No hay conexión', accion: 'REINTENTAR' });
    expect(traducirErrorDeTurno(new ApiError(500, 'ERROR_INTERNO', ''), contexto())).toMatchObject({ titulo: 'No pudimos guardar', accion: 'REINTENTAR' });
  });

  it('un 404 dice que el turno ya no existe', () => {
    expect(traducirErrorDeTurno(new ApiError(404, 'RECURSO_NO_ENCONTRADO', ''), contexto({ operacion: 'estado' })).titulo).toBe('No encontramos este turno');
  });
});
