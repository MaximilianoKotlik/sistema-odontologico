import { afterEach, describe, expect, it, vi } from 'vitest';
import { crearContextoDePagina, escribir, esperar, json, problema, type Guion } from '../configuracion/prueba-contexto';
import { avisos, botonEn, dialogoAbierto, en, textoDe } from '../turnos/prueba-turno';
import { montarBloqueos } from './pantalla';

// Tarea 3.6 de C-14 (design D8; spec bloqueos; mockup bloqueos.html). Hoy es viernes 20 de noviembre. Datos ficticios.

const REUNION = { id: 'b-1', inicio: en('2026-11-20', '15:00'), fin: en('2026-11-20', '16:00'), motivo: 'Reunión con el laboratorio' };
const VACACIONES = { id: 'b-2', inicio: en('2027-01-04', '00:00'), fin: en('2027-01-16', '00:00'), motivo: 'Vacaciones' };

function montar(guion: Guion) {
  const prueba = crearContextoDePagina(guion);
  montarBloqueos(prueba.contexto);
  return prueba;
}

const campo = (nombre: string) => dialogoAbierto()?.querySelector<HTMLInputElement>(`[name="${nombre}"]`) ?? null;

afterEach(() => {
  document.body.innerHTML = '';
  vi.useRealTimers();
});

describe('montarBloqueos: listado', () => {
  it('agrupa los bloqueos con su rango y motivo (escenario "Vacaciones cargadas")', async () => {
    vi.useFakeTimers({ toFake: ['Date'], now: new Date(en('2026-11-20', '12:10')) });
    const { contenedor } = montar({ 'GET /api/v1/bloqueos': [json([VACACIONES, REUNION])] });
    await esperar();
    expect([...contenedor.querySelectorAll('h3')].map((h) => h.textContent)).toEqual(['Esta semana', 'Más adelante']);
    const tarjetas = [...contenedor.querySelectorAll('[data-bloqueo]')].map((t) => textoDe(t));
    expect(tarjetas[0]).toContain('Reunión con el laboratorio Viernes 20 de noviembre · 15:00 a 16:00');
    expect(tarjetas[1]).toContain('Vacaciones Lunes 4 de enero a viernes 15 de enero · todo el día');
    expect(contenedor.querySelector('[data-bloqueo]')?.className).toContain('fondo-rayado');
    expect(textoDe(contenedor)).toContain('Nadie puede reservar en estos horarios, ni vos podés cargar turnos ahí.');
  });

  it('sin bloqueos muestra el estado vacío con "Bloquear horario"', async () => {
    const { contenedor } = montar({ 'GET /api/v1/bloqueos': [json([])] });
    await esperar();
    expect(textoDe(contenedor)).toContain('No tenés horarios bloqueados');
    expect(botonEn(contenedor, 'Bloquear horario')).not.toBeNull();
  });

  it('un error de carga se puede reintentar', async () => {
    const { contenedor } = montar({ 'GET /api/v1/bloqueos': [problema(500, 'ERROR_INTERNO'), json([REUNION])] });
    await esperar();
    botonEn(contenedor, 'Reintentar')?.click();
    await esperar();
    expect(contenedor.querySelectorAll('[data-bloqueo]')).toHaveLength(1);
  });
});

describe('montarBloqueos: crear', () => {
  it('por horas manda el rango en UTC y recarga el listado', async () => {
    vi.useFakeTimers({ toFake: ['Date'], now: new Date(en('2026-11-20', '12:10')) });
    const { contenedor, llamadas } = montar({
      'GET /api/v1/bloqueos': [json([]), json([REUNION])],
      'POST /api/v1/bloqueos': [json({ ...REUNION, id: 'b-3' }, 201)],
    });
    await esperar();
    botonEn(contenedor, 'Bloquear horario')?.click();
    expect(textoDe(dialogoAbierto()?.querySelector('h2') ?? null)).toBe('Bloquear horario');
    expect(campo('dia')?.value).toBe('2026-11-20');
    escribir(campo('desde'), '15:00');
    escribir(campo('hasta'), '19:00');
    escribir(campo('motivo'), 'Curso de actualización');
    expect(textoDe(dialogoAbierto()?.querySelector('[data-resumen-bloqueo]') ?? null)).toBe('Bloquea: Viernes 20 de noviembre · 15:00 a 19:00');
    botonEn(dialogoAbierto(), 'Bloquear')?.click();
    await esperar();
    expect(llamadas.find((l) => l.metodo === 'POST')?.cuerpo).toEqual({ inicio: en('2026-11-20', '15:00'), fin: en('2026-11-20', '19:00'), motivo: 'Curso de actualización' });
    expect(dialogoAbierto()).toBeNull();
    expect(avisos()).toContain('Bloqueamos el horario');
    expect(contenedor.querySelectorAll('[data-bloqueo]')).toHaveLength(1);
  });

  it('"Todo el día" cubre del inicio del primer día al inicio del día siguiente al último (escenario "Días completos")', async () => {
    vi.useFakeTimers({ toFake: ['Date'], now: new Date(en('2026-11-20', '12:10')) });
    const { contenedor, llamadas } = montar({ 'GET /api/v1/bloqueos': [json([]), json([VACACIONES])], 'POST /api/v1/bloqueos': [json(VACACIONES, 201)] });
    await esperar();
    botonEn(contenedor, 'Bloquear horario')?.click();
    const interruptor = dialogoAbierto()?.querySelector<HTMLButtonElement>('[role="switch"]');
    interruptor?.click();
    expect(interruptor?.getAttribute('aria-checked')).toBe('true');
    escribir(campo('diaDesde'), '2027-01-04');
    escribir(campo('diaHasta'), '2027-01-15');
    escribir(campo('motivo'), 'Vacaciones');
    botonEn(dialogoAbierto(), 'Bloquear')?.click();
    await esperar();
    expect(llamadas.find((l) => l.metodo === 'POST')?.cuerpo).toEqual({ inicio: en('2027-01-04', '00:00'), fin: en('2027-01-16', '00:00'), motivo: 'Vacaciones' });
  });

  it('un fin antes del inicio marca el campo y no se envía (escenario "Fin antes del inicio")', async () => {
    const { contenedor, llamadas } = montar({ 'GET /api/v1/bloqueos': [json([])] });
    await esperar();
    botonEn(contenedor, 'Bloquear horario')?.click();
    escribir(campo('desde'), '18:00');
    escribir(campo('hasta'), '17:00');
    botonEn(dialogoAbierto(), 'Bloquear')?.click();
    await esperar();
    expect(llamadas.some((l) => l.metodo === 'POST')).toBe(false);
    expect(campo('hasta')?.getAttribute('aria-invalid')).toBe('true');
    expect(textoDe(dialogoAbierto())).toContain('Tiene que terminar después de las 18:00.');
  });

  it('ante BLOQUEO_CON_TURNOS lista cada turno con "Ver turno" (escenario "Dos turnos en el rango")', async () => {
    vi.useFakeTimers({ toFake: ['Date'], now: new Date(en('2026-11-20', '12:10')) });
    const turno = (id: string, hora: string, nombre: string, apellido: string) => ({
      id,
      inicio: en('2026-11-20', hora),
      fin: en('2026-11-20', `${hora.slice(0, 2)}:30`),
      duracionMin: 30,
      estado: 'RESERVADO',
      esSobreturno: false,
      origen: 'MANUAL',
      prestacion: { id: 'p-1', nombre: 'Consulta' },
      paciente: { id: `pa-${id}`, nombre, apellido },
      tieneNota: false,
      version: 1,
    });
    const agenda = `GET /api/v1/agenda?desde=${encodeURIComponent(en('2026-11-20', '15:00'))}&hasta=${encodeURIComponent(en('2026-11-20', '19:00'))}`;
    const { contenedor } = montar({
      'GET /api/v1/bloqueos': [json([])],
      'POST /api/v1/bloqueos': [problema(409, 'BLOQUEO_CON_TURNOS', { turnosEnConflicto: ['t-16', 't-17'] })],
      [agenda]: [json({ turnos: [turno('t-16', '16:00', 'Federico', 'Ejemplo'), turno('t-17', '17:00', 'Gabriela', 'Prueba')], bloqueos: [] })],
    });
    await esperar();
    botonEn(contenedor, 'Bloquear horario')?.click();
    escribir(campo('desde'), '15:00');
    escribir(campo('hasta'), '19:00');
    botonEn(dialogoAbierto(), 'Bloquear')?.click();
    await esperar();
    await esperar();
    const alerta = dialogoAbierto()?.querySelector('[role="alert"]');
    expect(textoDe(alerta ?? null)).toContain('Hay 2 turnos en ese horario');
    const items = [...(dialogoAbierto()?.querySelectorAll('[data-conflicto]') ?? [])].map((li) => textoDe(li));
    expect(items).toEqual(['Vie 20 · 16:00 · Federico Ejemplo Ver turno', 'Vie 20 · 17:00 · Gabriela Prueba Ver turno']);
    expect(dialogoAbierto()?.querySelector('a[href="/app/agenda/turnos/t-16"]')).not.toBeNull();
  });
});

describe('montarBloqueos: quitar', () => {
  it('pide confirmación, lo quita, avisa y recarga (escenario "Quitar un bloqueo")', async () => {
    vi.useFakeTimers({ toFake: ['Date'], now: new Date(en('2026-11-20', '12:10')) });
    const { contenedor, llamadas } = montar({
      'GET /api/v1/bloqueos': [json([REUNION]), json([])],
      'DELETE /api/v1/bloqueos/b-1': [() => new Response(null, { status: 204 })],
    });
    await esperar();
    contenedor.querySelector<HTMLButtonElement>('button[aria-label="Quitar el bloqueo “Reunión con el laboratorio”"]')?.click();
    expect(textoDe(dialogoAbierto()?.querySelector('h2') ?? null)).toBe('¿Quitar el bloqueo “Reunión con el laboratorio”?');
    expect(textoDe(dialogoAbierto())).toContain('Ese horario vuelve a quedar disponible para reservar.');
    botonEn(dialogoAbierto(), 'Quitar bloqueo')?.click();
    await esperar();
    await esperar();
    expect(llamadas.some((l) => l.metodo === 'DELETE')).toBe(true);
    expect(avisos()).toContain('Quitamos el bloqueo “Reunión con el laboratorio”');
    expect(textoDe(contenedor)).toContain('No tenés horarios bloqueados');
  });
});
