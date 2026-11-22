import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { botonConTexto, crearContextoDePagina, esperar, json, problema, type Guion } from '../configuracion/prueba-contexto';
import { olvidarFranjas } from './api';
import { montarAgenda } from './pantalla';
import { registrarPuntosDeEntrada, restablecerPuntosDeEntrada } from './puntos-de-entrada';
import { instanteLocal } from './tiempo';

// Tareas 3.2 a 3.4 de C-13 (spec vista-agenda; mockup `agenda.html`). Datos ficticios. TZ del proceso: Europe/Madrid.
const BA = 'America/Argentina/Buenos_Aires';
const en = (dia: string, hora: string) => instanteLocal(dia, hora, BA).toISOString();
const rutaAgenda = (lunes: string, siguiente: string) =>
  `/api/v1/agenda?desde=${encodeURIComponent(instanteLocal(lunes, '00:00', BA).toISOString())}&hasta=${encodeURIComponent(instanteLocal(siguiente, '00:00', BA).toISOString())}`;
const SEMANA = rutaAgenda('2026-11-16', '2026-11-23');

const HORARIO = {
  franjas: [1, 2, 3, 4, 5]
    .flatMap((diaSemana) => [
      { diaSemana, horaInicio: '09:00', horaFin: '13:00' },
      { diaSemana, horaInicio: '15:00', horaFin: '19:00' },
    ])
    .concat([{ diaSemana: 6, horaInicio: '09:00', horaFin: '13:00' }]),
};

const turnoApi = (id: string, desde: string, hasta: string, extra: Record<string, unknown> = {}) => ({
  id,
  inicio: en('2026-11-20', desde),
  fin: en('2026-11-20', hasta),
  duracionMin: 30,
  estado: 'CONFIRMADO',
  esSobreturno: false,
  origen: 'MANUAL',
  prestacion: { id: 'p-1', nombre: 'Consulta' },
  paciente: { id: `pa-${id}`, nombre: 'Ana', apellido: 'Ejemplo' },
  tieneNota: false,
  version: 0,
  ...extra,
});

const AGENDA = {
  turnos: [
    turnoApi('a', '09:00', '09:30'),
    turnoApi('c', '11:30', '12:00', { estado: 'CANCELADO', canceladoPor: 'PACIENTE', cambioPaciente: 'CANCELO', novedadPacienteAt: en('2026-11-20', '08:12'), paciente: { nombre: 'Elena', apellido: 'Demo' } }),
    turnoApi('f', '16:00', '16:30', { estado: 'RESERVADO', origen: 'LINK', cambioPaciente: 'RESERVO', novedadPacienteAt: en('2026-11-20', '10:05'), paciente: { nombre: 'Federico', apellido: 'Ejemplo' } }),
  ],
  bloqueos: [],
};

function montar(query: string, guion: Guion) {
  history.replaceState(null, '', `/app/agenda${query}`);
  const pagina = crearContextoDePagina(guion);
  montarAgenda(pagina.contexto);
  return pagina;
}

const texto = (e: Element | null | undefined) => (e?.textContent ?? '').replace(/\s+/g, ' ').trim();

beforeEach(() => {
  vi.useFakeTimers({ toFake: ['Date'] });
  vi.setSystemTime(new Date(en('2026-11-20', '12:10')));
  localStorage.clear();
  olvidarFranjas();
});

afterEach(() => {
  vi.useRealTimers();
  restablecerPuntosDeEntrada();
});

describe('montarAgenda: vista diaria', () => {
  it('muestra un esqueleto mientras carga y después la línea de tiempo del día', async () => {
    const { contenedor } = montar('?vista=dia&fecha=2026-11-20', { [`GET ${SEMANA}`]: [json(AGENDA)], 'GET /api/v1/disponibilidad': [json(HORARIO)] });
    expect(contenedor.querySelector('[aria-busy="true"]')).not.toBeNull();
    await esperar();
    expect(contenedor.querySelector('[aria-busy="true"]')).toBeNull();
    expect(texto(contenedor.querySelector('h2'))).toBe('Viernes 20 de noviembre Hoy');
    expect(contenedor.querySelector('[data-linea-de-tiempo]')).not.toBeNull();
    expect(texto(contenedor.querySelector('[data-ahora]'))).toBe('Ahora · 12:10');
  });

  it('resume el día y avisa los cambios de los pacientes en un status', async () => {
    const { contenedor } = montar('?vista=dia&fecha=2026-11-20', { [`GET ${SEMANA}`]: [json(AGENDA)], 'GET /api/v1/disponibilidad': [json(HORARIO)] });
    await esperar();
    expect(texto(contenedor.querySelector('[data-resumen]'))).toBe('2 turnos · 1 cancelado');
    const aviso = contenedor.querySelector('[data-aviso-nuevos]');
    expect(aviso?.getAttribute('role')).toBe('status');
    expect(texto(aviso)).toContain('2 cambios de tus pacientes');
    expect(texto(aviso)).toContain('Elena D. canceló y Federico E. reservó, por el link.');
  });

  it('la tira de días marca el día elegido y lleva a cada día de la semana', async () => {
    const { contenedor } = montar('?vista=dia&fecha=2026-11-20', { [`GET ${SEMANA}`]: [json(AGENDA)], 'GET /api/v1/disponibilidad': [json(HORARIO)] });
    await esperar();
    const dias = [...contenedor.querySelectorAll<HTMLAnchorElement>('[data-tira-dias] a')];
    expect(dias).toHaveLength(7);
    expect(dias[4]?.getAttribute('aria-current')).toBe('date');
    expect(dias[0]?.getAttribute('href')).toBe('/app/agenda?vista=dia&fecha=2026-11-16');
    expect(texto(dias[4])).toContain('3');
    expect(texto(dias[6])).toContain('cerrado');
  });

  it('"Día siguiente" navega a la fecha siguiente y lo refleja en la URL', async () => {
    const { contenedor, contexto } = montar('?vista=dia&fecha=2026-11-20', { [`GET ${SEMANA}`]: [json(AGENDA)], 'GET /api/v1/disponibilidad': [json(HORARIO)] });
    await esperar();
    contenedor.querySelector<HTMLButtonElement>('button[aria-label="Día siguiente"]')?.click();
    expect(contexto.navegacion.navegar).toHaveBeenCalledWith('/app/agenda?vista=dia&fecha=2026-11-21');
  });

  it('apagar "Mostrar cancelados" los oculta, no los cuenta y se recuerda en el dispositivo', async () => {
    const { contenedor } = montar('?vista=dia&fecha=2026-11-20', { [`GET ${SEMANA}`]: [json(AGENDA)], 'GET /api/v1/disponibilidad': [json(HORARIO)] });
    await esperar();
    const interruptor = contenedor.querySelector<HTMLButtonElement>('button[role="switch"]');
    expect(interruptor?.getAttribute('aria-checked')).toBe('true');
    interruptor?.click();
    expect(contenedor.querySelector('a[data-turno="c"]')).toBeNull();
    expect(texto(contenedor.querySelector('[data-resumen]'))).toBe('2 turnos');
    expect(contenedor.querySelector<HTMLButtonElement>('button[role="switch"]')?.getAttribute('aria-checked')).toBe('false');
    expect(localStorage.getItem('agenda.mostrarCancelados')).toBe('false');
  });

  it('el sábado sin turnos dice "No tenés turnos" y muestra sus huecos libres', async () => {
    const { contenedor } = montar('?vista=dia&fecha=2026-11-21', { [`GET ${SEMANA}`]: [json({ turnos: [], bloqueos: [] })], 'GET /api/v1/disponibilidad': [json(HORARIO)] });
    await esperar();
    expect(texto(contenedor)).toContain('No tenés turnos');
    expect(texto(contenedor.querySelector('button[data-libre]'))).toBe('Libre · 09:00 a 13:00');
  });

  it('el domingo sin horario dice "No atendés los domingos" con el link a los horarios', async () => {
    const { contenedor } = montar('?vista=dia&fecha=2026-11-22', { [`GET ${SEMANA}`]: [json({ turnos: [], bloqueos: [] })], 'GET /api/v1/disponibilidad': [json(HORARIO)] });
    await esperar();
    expect(texto(contenedor)).toContain('No atendés los domingos');
    expect(contenedor.querySelector('a[href="/app/configuracion/horarios"]')?.textContent).toContain('Cambiar mis horarios');
  });

  it('"Nuevo turno" y los huecos libres son puntos de entrada de C-14 que piden conexión', async () => {
    const nuevoTurno = vi.fn();
    registrarPuntosDeEntrada({ nuevoTurno, accion: vi.fn() });
    const { contenedor, contexto } = montar('?vista=dia&fecha=2026-11-21', { [`GET ${SEMANA}`]: [json({ turnos: [], bloqueos: [] })], 'GET /api/v1/disponibilidad': [json(HORARIO)] });
    await esperar();
    const nuevo = botonConTexto(contexto.acciones, 'Nuevo turno');
    expect(nuevo?.hasAttribute('data-requiere-conexion')).toBe(true);
    nuevo?.click();
    expect(nuevoTurno).toHaveBeenCalledWith(null);
    contenedor.querySelector<HTMLButtonElement>('button[data-libre]')?.click();
    expect(nuevoTurno).toHaveBeenLastCalledWith(en('2026-11-21', '09:00'));
  });
});

describe('montarAgenda: vista semanal', () => {
  it('muestra la grilla y la lista de la semana con su título', async () => {
    const { contenedor } = montar('?vista=semana&fecha=2026-11-18', { [`GET ${SEMANA}`]: [json(AGENDA)], 'GET /api/v1/disponibilidad': [json(HORARIO)] });
    await esperar();
    expect(texto(contenedor.querySelector('h2'))).toBe('16 al 22 de noviembre Esta semana');
    expect(contenedor.querySelector('[data-grilla-semanal]')).not.toBeNull();
    expect(contenedor.querySelector('[data-lista-semanal]')).not.toBeNull();
    expect(contenedor.querySelector('a[aria-current="page"]')?.textContent).toBe('Semana');
  });

  it('"Semana anterior" navega al lunes anterior', async () => {
    const { contenedor, contexto } = montar('?vista=semana&fecha=2026-11-16', { [`GET ${SEMANA}`]: [json(AGENDA)], 'GET /api/v1/disponibilidad': [json(HORARIO)] });
    await esperar();
    contenedor.querySelector<HTMLButtonElement>('button[aria-label="Semana anterior"]')?.click();
    expect(contexto.navegacion.navegar).toHaveBeenCalledWith('/app/agenda?vista=semana&fecha=2026-11-09');
  });

  it('una semana que no es la actual ofrece volver a hoy', async () => {
    const otra = rutaAgenda('2026-11-30', '2026-12-07');
    const { contenedor } = montar('?vista=semana&fecha=2026-11-30', { [`GET ${otra}`]: [json({ turnos: [], bloqueos: [] })], 'GET /api/v1/disponibilidad': [json(HORARIO)] });
    await esperar();
    expect(texto(contenedor.querySelector('h2'))).toBe('30 de noviembre al 6 de diciembre');
    expect(botonConTexto(contenedor, 'Hoy')).not.toBeNull();
  });
});

describe('montarAgenda: errores y sin conexión', () => {
  it('un 500 muestra "No pudimos cargar tu agenda" y "Reintentar" vuelve a pedirla', async () => {
    const { contenedor } = montar('?vista=dia&fecha=2026-11-20', {
      [`GET ${SEMANA}`]: [problema(500, 'ERROR_INTERNO'), json(AGENDA)],
      'GET /api/v1/disponibilidad': [json(HORARIO)],
    });
    await esperar();
    expect(texto(contenedor.querySelector('[role="alert"]'))).toContain('No pudimos cargar tu agenda');
    botonConTexto(contenedor, 'Reintentar')?.click();
    await esperar();
    expect(contenedor.querySelector('[data-linea-de-tiempo]')).not.toBeNull();
    expect(texto(contenedor.querySelector('h2'))).toBe('Viernes 20 de noviembre Hoy');
  });

  it('sin conexión y sin agenda guardada lo dice y ofrece reintentar', async () => {
    const { contenedor } = montar('?vista=dia&fecha=2026-11-20', {
      [`GET ${SEMANA}`]: [() => Promise.reject(new TypeError('Failed to fetch'))],
      'GET /api/v1/disponibilidad': [() => Promise.reject(new TypeError('Failed to fetch'))],
    });
    await esperar();
    expect(texto(contenedor)).toContain('No tenés guardada la agenda de esta fecha');
    expect(botonConTexto(contenedor, 'Reintentar')).not.toBeNull();
  });

  it('sin conexión pero con la agenda guardada la muestra aunque no haya horario en memoria', async () => {
    const { contenedor } = montar('?vista=dia&fecha=2026-11-20', {
      [`GET ${SEMANA}`]: [json(AGENDA)],
      'GET /api/v1/disponibilidad': [() => Promise.reject(new TypeError('Failed to fetch'))],
    });
    await esperar();
    expect(contenedor.querySelector('a[data-turno="a"]')).not.toBeNull();
    expect(contenedor.querySelector('button[data-libre]')).toBeNull();
  });
});
