import { afterEach, describe, expect, it, vi } from 'vitest';
import { olvidarFranjas } from '../agenda/api';
import { escribir, esperar, json, problema, type Guion } from '../configuracion/prueba-contexto';
import { abrirReprogramar } from './reprogramar';
import { avisos, botonEn, detalleApi, detalleDePrueba, dialogoAbierto, en, entornoDePrueba, textoDe } from './prueba-turno';

// Tarea 3.3 de C-14 (design D6; spec "Reprogramar"; mockup acciones-turno?estado=reprogramar). Viernes 20 de noviembre,
// antes de que empiece el día. Datos ficticios.

const desde = encodeURIComponent(en('2026-11-20', '00:00'));
const hasta = encodeURIComponent(en('2026-11-21', '00:00'));
const AGENDA_DEL_DIA = `GET /api/v1/agenda?desde=${desde}&hasta=${hasta}`;
const HORARIO = { franjas: [{ diaSemana: 5, horaInicio: '15:00', horaFin: '19:00' }] };

const otro = (id: string, desdeHora: string, hastaHora: string, nombre: string) => ({
  ...detalleApi({ id, inicio: en('2026-11-20', desdeHora), fin: en('2026-11-20', hastaHora) }),
  paciente: { id: `pa-${id}`, nombre, apellido: 'Prueba' },
});

function guion(extra: Guion = {}): Guion {
  return {
    'GET /api/v1/consultorio': [json({ pasoHuecosMin: 30 })],
    [AGENDA_DEL_DIA]: [json({ turnos: [detalleApi(), otro('t-2', '17:00', '18:00', 'Gabriela')], bloqueos: [] })],
    'GET /api/v1/disponibilidad': [json(HORARIO)],
    ...extra,
  };
}

afterEach(() => {
  document.body.innerHTML = '';
  olvidarFranjas();
  vi.useRealTimers();
});

describe('abrirReprogramar', () => {
  it('muestra el turno actual y los huecos del día donde entra su duración, con el paso del consultorio', async () => {
    vi.useFakeTimers({ toFake: ['Date'], now: new Date(en('2026-11-20', '08:00')) });
    const { entorno } = entornoDePrueba(guion());
    abrirReprogramar(detalleDePrueba(), entorno);
    await esperar();
    await esperar();
    const caja = dialogoAbierto();
    expect(textoDe(caja?.querySelector('h2') ?? null)).toBe('Reprogramar turno');
    expect(textoDe(caja?.querySelector('[data-referencia]') ?? null)).toContain('Viernes 20 de noviembre · 16:00 a 16:30');
    expect(caja?.querySelector<HTMLInputElement>('input[type="date"]')?.value).toBe('2026-11-20');
    expect(textoDe(caja?.querySelector('[data-titulo-huecos]') ?? null)).toBe('Huecos libres el viernes para 30 min');
    // 15:00 a 19:00 cada 30 min; el turno que se mueve no ocupa y el de 17:00 a 18:00 sí.
    expect([...(caja?.querySelectorAll('[data-hueco]') ?? [])].map((b) => b.textContent)).toEqual(['15:00', '15:30', '16:00', '16:30', '18:00', '18:30']);
    expect(textoDe(caja)).toContain('cada 30 minutos (el paso de huecos de tu configuración)');
  });

  it('tocar un hueco carga la hora y muestra el horario nuevo (escenario "Elegir un hueco")', async () => {
    vi.useFakeTimers({ toFake: ['Date'], now: new Date(en('2026-11-20', '08:00')) });
    const { entorno } = entornoDePrueba(guion());
    abrirReprogramar(detalleDePrueba(), entorno);
    await esperar();
    await esperar();
    botonEn(dialogoAbierto(), '16:30')?.click();
    expect(dialogoAbierto()?.querySelector<HTMLInputElement>('input[type="time"]')?.value).toBe('16:30');
    expect(dialogoAbierto()?.querySelector('[data-hueco][aria-pressed="true"]')?.textContent).toBe('16:30');
    expect(textoDe(dialogoAbierto()?.querySelector('[data-nuevo-horario]') ?? null)).toBe('Nuevo horario: viernes 20 · 16:30 a 17:00');
    expect(textoDe(dialogoAbierto())).toContain('El paciente conserva su link: ahí va a ver el horario nuevo.');
  });

  it('"Mover turno" manda el inicio nuevo con la versión leída, avisa y recarga', async () => {
    vi.useFakeTimers({ toFake: ['Date'], now: new Date(en('2026-11-20', '08:00')) });
    const { entorno, llamadas, alTerminar } = entornoDePrueba(guion({ 'POST /api/v1/turnos/t-1/reprogramar': [json(detalleApi({ inicio: en('2026-11-20', '16:30'), fin: en('2026-11-20', '17:00') }))] }));
    abrirReprogramar(detalleDePrueba(), entorno);
    await esperar();
    await esperar();
    botonEn(dialogoAbierto(), '16:30')?.click();
    botonEn(dialogoAbierto(), 'Mover turno')?.click();
    await esperar();
    expect(llamadas.find((l) => l.metodo === 'POST')?.cuerpo).toEqual({ inicio: en('2026-11-20', '16:30'), version: 3 });
    expect(dialogoAbierto()).toBeNull();
    expect(avisos()).toContain('Movimos el turno de Federico Ejemplo al viernes 20 a las 16:30');
    expect(alTerminar).toHaveBeenCalledTimes(1);
  });

  it('una hora escrita a mano también vale, y el mismo horario no se envía', async () => {
    vi.useFakeTimers({ toFake: ['Date'], now: new Date(en('2026-11-20', '08:00')) });
    const { entorno, llamadas } = entornoDePrueba(guion());
    abrirReprogramar(detalleDePrueba(), entorno);
    await esperar();
    await esperar();
    escribir(dialogoAbierto()?.querySelector<HTMLInputElement>('input[type="time"]') ?? null, '16:00');
    botonEn(dialogoAbierto(), 'Mover turno')?.click();
    expect(llamadas.some((l) => l.metodo === 'POST')).toBe(false);
    expect(textoDe(dialogoAbierto())).toContain('Elegí un horario distinto al actual.');
    escribir(dialogoAbierto()?.querySelector<HTMLInputElement>('input[type="time"]') ?? null, '18:45');
    expect(textoDe(dialogoAbierto()?.querySelector('[data-nuevo-horario]') ?? null)).toBe('Nuevo horario: viernes 20 · 18:45 a 19:15');
  });

  it('ante TURNO_SOLAPADO nombra el turno con el que choca y no ofrece el sobreturno', async () => {
    vi.useFakeTimers({ toFake: ['Date'], now: new Date(en('2026-11-20', '08:00')) });
    const { entorno } = entornoDePrueba(
      guion({ 'POST /api/v1/turnos/t-1/reprogramar': [problema(409, 'TURNO_SOLAPADO', { turnoEnConflicto: { id: 't-2', inicio: en('2026-11-20', '17:00'), fin: en('2026-11-20', '18:00') } })] }),
    );
    abrirReprogramar(detalleDePrueba(), entorno);
    await esperar();
    await esperar();
    escribir(dialogoAbierto()?.querySelector<HTMLInputElement>('input[type="time"]') ?? null, '16:45');
    botonEn(dialogoAbierto(), 'Mover turno')?.click();
    await esperar();
    const alerta = textoDe(dialogoAbierto()?.querySelector('[role="alert"]') ?? null);
    expect(alerta).toContain('Se superpone con el turno de 17:00 de Gabriela Prueba');
    expect(botonEn(dialogoAbierto(), 'Crear como sobreturno')).toBeNull();
  });

  it('un día sin huecos lo dice', async () => {
    vi.useFakeTimers({ toFake: ['Date'], now: new Date(en('2026-11-20', '08:00')) });
    const sabado = `GET /api/v1/agenda?desde=${encodeURIComponent(en('2026-11-21', '00:00'))}&hasta=${encodeURIComponent(en('2026-11-22', '00:00'))}`;
    const { entorno } = entornoDePrueba(guion({ [sabado]: [json({ turnos: [], bloqueos: [] })] }));
    abrirReprogramar(detalleDePrueba(), entorno);
    await esperar();
    await esperar();
    escribir(dialogoAbierto()?.querySelector<HTMLInputElement>('input[type="date"]') ?? null, '2026-11-21');
    await esperar();
    await esperar();
    expect(textoDe(dialogoAbierto())).toContain('No hay huecos libres el sábado para 30 min. Probá otro día o escribí una hora.');
  });
});
