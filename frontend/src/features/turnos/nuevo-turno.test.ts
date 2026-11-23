import { afterEach, describe, expect, it, vi } from 'vitest';
import { olvidarFranjas } from '../agenda/api';
import { escribir, esperar, json, problema, type Guion } from '../configuracion/prueba-contexto';
import { abrirNuevoTurno } from './nuevo-turno';
import { avisos, botonEn, detalleApi, dialogoAbierto, en, entornoDePrueba, textoDe } from './prueba-turno';

// Tarea 3.1 de C-14 (design D3, D4, D5; spec gestion-turnos; mockup nuevo-turno). Viernes 20 de noviembre a las 08:00.
// Datos ficticios.

const AGENDA_DEL_20 = `GET /api/v1/agenda?desde=${encodeURIComponent(en('2026-11-20', '00:00'))}&hasta=${encodeURIComponent(en('2026-11-21', '00:00'))}`;
const PRESTACIONES = [
  { id: 'p-consulta', nombre: 'Consulta', duracionMin: 30, activa: true },
  { id: 'p-extraccion', nombre: 'Extracción', duracionMin: 60, activa: true },
];
const CARLA = { ...detalleApi({ id: 't-carla', inicio: en('2026-11-20', '10:30'), fin: en('2026-11-20', '11:30'), estado: 'CONFIRMADO' }), paciente: { id: 'pa-3', nombre: 'Carla', apellido: 'Ficticia' } };
const BLOQUEO = { id: 'b-1', inicio: en('2026-11-20', '15:00'), fin: en('2026-11-20', '16:00'), motivo: 'Reunión con el laboratorio' };
const HORARIO = { franjas: [{ diaSemana: 5, horaInicio: '09:00', horaFin: '13:00' }, { diaSemana: 5, horaInicio: '15:00', horaFin: '19:00' }] };
const LUCIA = { id: 'pa-10', nombre: 'Lucía', apellido: 'Muestra', dni: '30000010', telefono: '+5491100000010', requiereRevision: false };

function guion(extra: Guion = {}): Guion {
  return {
    'GET /api/v1/prestaciones': [json(PRESTACIONES)],
    [AGENDA_DEL_20]: [json({ turnos: [CARLA], bloqueos: [BLOQUEO] })],
    'GET /api/v1/disponibilidad': [json(HORARIO)],
    ...extra,
  };
}

const campo = (nombre: string) => dialogoAbierto()?.querySelector<HTMLInputElement>(`[name="${nombre}"]`) ?? null;
const opcion = (texto: string) => [...(dialogoAbierto()?.querySelectorAll<HTMLButtonElement>('[data-prestacion]') ?? [])].find((b) => b.textContent?.includes(texto)) ?? null;

async function abrir(inicio: string | null, extra: Guion = {}) {
  const prueba = entornoDePrueba(guion(extra));
  abrirNuevoTurno(inicio, prueba.entorno, { esperaBusquedaMs: 0 });
  await esperar();
  await esperar();
  return prueba;
}

async function elegirALucia(): Promise<void> {
  escribir(campo('buscar'), '3000');
  await esperar();
  await esperar();
  botonEn(dialogoAbierto(), 'Lucía Muestra DNI 30.000.010 · +54 9 11 0000-0010')?.click();
}

afterEach(() => {
  document.body.innerHTML = '';
  olvidarFranjas();
  vi.useRealTimers();
});

describe('abrirNuevoTurno', () => {
  it('desde un hueco trae el día y la hora; con la prestación muestra "Termina 12:30" (escenario "Turno desde un hueco libre")', async () => {
    vi.useFakeTimers({ toFake: ['Date'], now: new Date(en('2026-11-20', '08:00')) });
    await abrir(en('2026-11-20', '12:00'));
    expect(textoDe(dialogoAbierto()?.querySelector('h2') ?? null)).toBe('Nuevo turno');
    expect(campo('dia')?.value).toBe('2026-11-20');
    expect(campo('hora')?.value).toBe('12:00');
    expect(textoDe(dialogoAbierto()?.querySelector('[data-fin]') ?? null)).toBe('Termina Elegí la prestación y la hora');
    opcion('Consulta')?.click();
    expect(opcion('Consulta')?.getAttribute('aria-pressed')).toBe('true');
    expect(textoDe(dialogoAbierto()?.querySelector('[data-fin]') ?? null)).toBe('Termina 12:30');
  });

  it('busca por DNI desde 3 caracteres, elige al paciente y crea el turno', async () => {
    vi.useFakeTimers({ toFake: ['Date'], now: new Date(en('2026-11-20', '08:00')) });
    const { llamadas, alTerminar } = await abrir(en('2026-11-20', '12:00'), {
      'GET /api/v1/pacientes?q=3000': [json([LUCIA])],
      'POST /api/v1/turnos': [json({ turno: detalleApi({ inicio: en('2026-11-20', '12:00'), fin: en('2026-11-20', '12:30') }), pacienteReutilizado: false }, 201)],
    });
    escribir(campo('buscar'), '30');
    await esperar();
    expect(llamadas.some((l) => l.url.startsWith('/api/v1/pacientes'))).toBe(false);
    await elegirALucia();
    expect(textoDe(dialogoAbierto()?.querySelector('[data-paciente-elegido]') ?? null)).toContain('Lucía Muestra');
    opcion('Consulta')?.click();
    botonEn(dialogoAbierto(), 'Crear turno')?.click();
    await esperar();
    expect(llamadas.find((l) => l.metodo === 'POST')?.cuerpo).toEqual({ pacienteId: 'pa-10', prestacionId: 'p-consulta', inicio: en('2026-11-20', '12:00'), esSobreturno: false });
    expect(dialogoAbierto()).toBeNull();
    expect(avisos()).toContain('Turno creado: Lucía Muestra, viernes 20 a las 12:00');
    expect(alTerminar).toHaveBeenCalledTimes(1);
  });

  it('el alta rápida valida el DNI antes de enviar (escenario "Alta rápida con DNI inválido")', async () => {
    vi.useFakeTimers({ toFake: ['Date'], now: new Date(en('2026-11-20', '08:00')) });
    const { llamadas } = await abrir(en('2026-11-20', '12:00'));
    botonEn(dialogoAbierto(), 'Cargar paciente nuevo')?.click();
    escribir(campo('nombre'), 'Iris');
    escribir(campo('apellido'), 'Ejemplo');
    escribir(campo('dni'), '30000');
    escribir(campo('telefono'), '11 0000-0009');
    opcion('Consulta')?.click();
    botonEn(dialogoAbierto(), 'Crear turno')?.click();
    await esperar();
    expect(llamadas.some((l) => l.metodo === 'POST')).toBe(false);
    expect(campo('dni')?.getAttribute('aria-invalid')).toBe('true');
    expect(textoDe(dialogoAbierto())).toContain('El DNI tiene 7 u 8 números, sin puntos.');
  });

  it('con un paciente nuevo cuyo DNI ya existía, avisa que usó sus datos', async () => {
    vi.useFakeTimers({ toFake: ['Date'], now: new Date(en('2026-11-20', '08:00')) });
    const { llamadas } = await abrir(en('2026-11-20', '12:00'), {
      'POST /api/v1/turnos': [json({ turno: detalleApi({ inicio: en('2026-11-20', '12:00'), fin: en('2026-11-20', '12:30') }), pacienteReutilizado: true }, 201)],
    });
    botonEn(dialogoAbierto(), 'Cargar paciente nuevo')?.click();
    escribir(campo('nombre'), 'Iris');
    escribir(campo('apellido'), 'Ejemplo');
    escribir(campo('dni'), '30000009');
    escribir(campo('telefono'), '11 0000-0009');
    opcion('Consulta')?.click();
    botonEn(dialogoAbierto(), 'Crear turno')?.click();
    await esperar();
    expect((llamadas.find((l) => l.metodo === 'POST')?.cuerpo as { pacienteNuevo: unknown }).pacienteNuevo).toEqual({ nombre: 'Iris', apellido: 'Ejemplo', dni: '30000009', telefono: '11 0000-0009' });
    expect(avisos().join(' ')).toContain('Ya había un paciente con ese DNI: usamos sus datos.');
  });

  it('avisa antes de enviar si la hora se superpone con la agenda cargada y sugiere el sobreturno (D3)', async () => {
    vi.useFakeTimers({ toFake: ['Date'], now: new Date(en('2026-11-20', '08:00')) });
    await abrir(en('2026-11-20', '10:15'));
    opcion('Extracción')?.click();
    const avisoPrevio = dialogoAbierto()?.querySelector('[data-aviso-superpone]');
    expect(textoDe(avisoPrevio ?? null)).toContain('Ese horario se superpone con 10:30 · Carla F.');
    expect(avisoPrevio?.getAttribute('role')).toBe('status');
    campo('sobreturno')?.click();
    expect(dialogoAbierto()?.querySelector('[data-aviso-superpone]')).toBeNull();
    expect(textoDe(dialogoAbierto())).toContain('Se va a ver marcado en tu agenda y nunca se ofrece en tu página.');
  });

  it('ante TURNO_SOLAPADO explica con quién choca y "Crear como sobreturno" reenvía marcado (escenario "Turno solapado")', async () => {
    vi.useFakeTimers({ toFake: ['Date'], now: new Date(en('2026-11-20', '08:00')) });
    const { llamadas } = await abrir(en('2026-11-20', '10:15'), {
      'GET /api/v1/pacientes?q=3000': [json([LUCIA])],
      'POST /api/v1/turnos': [
        problema(409, 'TURNO_SOLAPADO', { turnoEnConflicto: { id: 't-carla', inicio: en('2026-11-20', '10:30'), fin: en('2026-11-20', '11:30') } }),
        json({ turno: detalleApi({ esSobreturno: true }), pacienteReutilizado: false }, 201),
      ],
    });
    await elegirALucia();
    opcion('Consulta')?.click();
    botonEn(dialogoAbierto(), 'Crear turno')?.click();
    await esperar();
    const alerta = dialogoAbierto()?.querySelector('[data-error-turno]');
    expect(textoDe(alerta ?? null)).toContain('Se superpone con el turno de 10:30 de Carla Ficticia');
    expect(campo('hora')?.value).toBe('10:15');
    botonEn(dialogoAbierto(), 'Crear como sobreturno')?.click();
    await esperar();
    const posts = llamadas.filter((l) => l.metodo === 'POST');
    expect(posts).toHaveLength(2);
    expect((posts[1]?.cuerpo as { esSobreturno: boolean }).esSobreturno).toBe(true);
    expect(dialogoAbierto()).toBeNull();
  });

  it('con el rechazo a la vista no repite el aviso previo, y cambiar la hora lo saca porque ya no aplica', async () => {
    vi.useFakeTimers({ toFake: ['Date'], now: new Date(en('2026-11-20', '08:00')) });
    await abrir(en('2026-11-20', '10:15'), {
      'GET /api/v1/pacientes?q=3000': [json([LUCIA])],
      'POST /api/v1/turnos': [problema(409, 'TURNO_SOLAPADO', { turnoEnConflicto: { id: 't-carla', inicio: en('2026-11-20', '10:30'), fin: en('2026-11-20', '11:30') } })],
    });
    await elegirALucia();
    opcion('Consulta')?.click();
    botonEn(dialogoAbierto(), 'Crear turno')?.click();
    await esperar();
    expect(dialogoAbierto()?.querySelector('[data-error-turno]')).not.toBeNull();
    expect(dialogoAbierto()?.querySelector('[data-aviso-superpone]')).toBeNull();
    escribir(campo('hora'), '12:00');
    expect(dialogoAbierto()?.querySelector('[data-error-turno]')).toBeNull();
  });

  it('ante HORARIO_BLOQUEADO nombra el bloqueo y no ofrece el sobreturno (escenario "Dentro de un bloqueo")', async () => {
    vi.useFakeTimers({ toFake: ['Date'], now: new Date(en('2026-11-20', '08:00')) });
    await abrir(en('2026-11-20', '15:00'), {
      'GET /api/v1/pacientes?q=3000': [json([LUCIA])],
      'POST /api/v1/turnos': [problema(409, 'HORARIO_BLOQUEADO', { bloqueosEnConflicto: ['b-1'] })],
    });
    await elegirALucia();
    opcion('Consulta')?.click();
    botonEn(dialogoAbierto(), 'Crear turno')?.click();
    await esperar();
    expect(textoDe(dialogoAbierto()?.querySelector('[data-error-turno]') ?? null)).toContain('De 15:00 a 16:00 tenés “Reunión con el laboratorio”.');
    expect(botonEn(dialogoAbierto(), 'Crear como sobreturno')).toBeNull();
  });

  it('ante 403 PLAN_VENCIDO dice que la agenda está en solo lectura (escenario "Plan vencido")', async () => {
    vi.useFakeTimers({ toFake: ['Date'], now: new Date(en('2026-11-20', '08:00')) });
    await abrir(en('2026-11-20', '12:00'), { 'GET /api/v1/pacientes?q=3000': [json([LUCIA])], 'POST /api/v1/turnos': [problema(403, 'PLAN_VENCIDO')] });
    await elegirALucia();
    opcion('Consulta')?.click();
    botonEn(dialogoAbierto(), 'Crear turno')?.click();
    await esperar();
    expect(textoDe(dialogoAbierto()?.querySelector('[data-error-turno]') ?? null)).toContain('Tu agenda está en solo lectura');
  });

  it('desde "Nuevo turno" arranca hoy, sin hora, con el foco en el buscador', async () => {
    vi.useFakeTimers({ toFake: ['Date'], now: new Date(en('2026-11-20', '08:00')) });
    await abrir(null);
    expect(campo('dia')?.value).toBe('2026-11-20');
    expect(campo('hora')?.value).toBe('');
    expect(document.activeElement).toBe(campo('buscar'));
    expect(textoDe(dialogoAbierto())).toContain('Cargar paciente nuevo');
  });
});
