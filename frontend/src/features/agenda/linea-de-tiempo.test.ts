import { describe, expect, it, vi } from 'vitest';
import { lineaDeTiempo } from './linea-de-tiempo';
import type { Fila, TurnoAgenda } from './modelo';

// Tarea 3.2 de C-13 (design D3, D9; mockup `agenda.html`, vista Día). Datos ficticios.

const turno: TurnoAgenda = {
  id: 't-1',
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
};

const FILAS: Fila[] = [
  { tipo: 'turno', turno, desde: '10:30', hasta: '11:30' },
  { tipo: 'libre', desde: '11:30', hasta: '13:00' },
  { tipo: 'ahora', hora: '12:10' },
  { tipo: 'fuera', desde: '13:00', hasta: '15:00' },
  { tipo: 'bloqueo', bloqueo: { id: 'b-1', inicio: '', fin: '', motivo: 'Reunión' }, desde: '15:00', hasta: '16:00' },
];

const texto = (e: Element | null | undefined) => (e?.textContent ?? '').replace(/\s+/g, ' ').trim();

describe('lineaDeTiempo', () => {
  it('es una lista ordenada con una fila por elemento del día', () => {
    const lista = lineaDeTiempo(FILAS, { alElegirLibre: vi.fn() });
    expect(lista.tagName).toBe('OL');
    expect(lista.children).toHaveLength(5);
  });

  it('cada turno muestra su hora de inicio y fin junto a la tarjeta', () => {
    const fila = lineaDeTiempo(FILAS, { alElegirLibre: vi.fn() }).children[0];
    expect(texto(fila?.querySelector('[data-hora]'))).toBe('10:30 a 11:30');
    expect(fila?.querySelector('a[href="/app/agenda/turnos/t-1"]')).not.toBeNull();
  });

  it('un hueco libre es un botón que pide conexión y avisa qué hora se eligió', () => {
    const alElegirLibre = vi.fn();
    const libre = lineaDeTiempo(FILAS, { alElegirLibre }).querySelector<HTMLButtonElement>('button[data-libre]');
    expect(texto(libre)).toBe('Libre · 11:30 a 13:00');
    expect(libre?.hasAttribute('data-requiere-conexion')).toBe(true);
    libre?.click();
    expect(alElegirLibre).toHaveBeenCalledWith('11:30');
  });

  it('muestra los tramos sin atención y la marca de ahora', () => {
    const lista = lineaDeTiempo(FILAS, { alElegirLibre: vi.fn() });
    expect(texto(lista.querySelector('[data-fuera]'))).toBe('No atendés · 13:00 a 15:00');
    expect(texto(lista.querySelector('[data-ahora]'))).toBe('Ahora · 12:10');
  });

  it('los bloqueos van con su rango de horas', () => {
    const fila = lineaDeTiempo(FILAS, { alElegirLibre: vi.fn() }).children[4];
    expect(texto(fila?.querySelector('[data-hora]'))).toBe('15:00 a 16:00');
    expect(texto(fila)).toContain('Reunión');
  });
});
