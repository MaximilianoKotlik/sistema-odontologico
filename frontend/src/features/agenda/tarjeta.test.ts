import { describe, expect, it } from 'vitest';
import type { BloqueoAgenda, EstadoTurno, TurnoAgenda } from './modelo';
import { chipEstadoTurno, nombreAccesibleTurno, tarjetaBloqueo, tarjetaTurno } from './tarjeta';

// Tarea 3.1 de C-13 (design D5, D9; mockup `agenda.html`). Datos ficticios.

function turno(extra: Partial<TurnoAgenda> = {}): TurnoAgenda {
  return {
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
    ...extra,
  };
}

const fila = (t: TurnoAgenda, superponeCon?: { hora: string; paciente: string }) => ({ tipo: 'turno' as const, turno: t, desde: '10:30', hasta: '11:30', ...(superponeCon === undefined ? {} : { superponeCon }) });
const visible = (e: Element) => {
  const copia = e.cloneNode(true) as Element;
  copia.querySelectorAll('[aria-hidden="true"]').forEach((n) => n.remove());
  return (copia.textContent ?? '').replace(/\s+/g, ' ').trim();
};

describe('chipEstadoTurno', () => {
  it.each<[EstadoTurno, string]>([
    ['RESERVADO', 'Reservado'],
    ['CONFIRMADO', 'Confirmado'],
    ['CANCELADO', 'Cancelado'],
    ['AUSENTE', 'Ausente'],
  ])('%s se muestra con texto e ícono, no solo con color', (estado, texto) => {
    const chip = chipEstadoTurno(estado);
    expect(visible(chip)).toBe(texto);
    expect(chip.querySelector('svg[aria-hidden="true"]')).not.toBeNull();
  });
});

describe('tarjetaTurno', () => {
  it('es un link interno al detalle con hora, paciente, prestación, duración y estado', () => {
    const tarjeta = tarjetaTurno(fila(turno({ tieneNota: true })));
    expect(tarjeta.tagName).toBe('A');
    expect(tarjeta.getAttribute('href')).toBe('/app/agenda/turnos/t-1');
    expect(tarjeta.hasAttribute('data-link')).toBe(true);
    expect(visible(tarjeta)).toContain('Carla Ficticia');
    expect(visible(tarjeta)).toContain('Extracción · 60 min');
    expect(visible(tarjeta)).toContain('Confirmado');
    expect(visible(tarjeta)).toContain('Con nota');
  });

  it('tiene nombre accesible completo (D9)', () => {
    expect(tarjetaTurno(fila(turno({ tieneNota: true }))).getAttribute('aria-label')).toBe('10:30 a 11:30, Carla Ficticia, Extracción, Confirmado, con nota');
  });

  it('un sobreturno superpuesto tiene borde punteado, su etiqueta y con quién se cruza', () => {
    const tarjeta = tarjetaTurno(fila(turno({ esSobreturno: true, estado: 'RESERVADO', paciente: { nombre: 'Diego', apellido: 'Muestra' } }), { hora: '10:30', paciente: 'Carla F.' }));
    expect(tarjeta.className).toContain('border-dashed');
    expect(visible(tarjeta)).toContain('Sobreturno');
    expect(visible(tarjeta)).toContain('Se superpone con 10:30 · Carla F.');
    expect(tarjeta.getAttribute('aria-label')).toBe('10:30 a 11:30, Diego Muestra, Extracción, Reservado, sobreturno, se superpone con 10:30 · Carla F.');
  });

  it('un cancelado se atenúa sin opacidad y con el nombre tachado', () => {
    const tarjeta = tarjetaTurno(fila(turno({ estado: 'CANCELADO' })));
    expect(tarjeta.dataset['estado']).toBe('CANCELADO');
    expect(tarjeta.className).not.toMatch(/opacity/);
    expect(tarjeta.querySelector('[data-nombre]')?.className).toContain('line-through');
  });

  it('un cambio del paciente sin ver lleva la etiqueta "Nuevo" con el tipo de cambio', () => {
    const tarjeta = tarjetaTurno(fila(turno({ estado: 'RESERVADO', origen: 'LINK', cambioPaciente: 'RESERVO', novedadPacienteAt: '2026-11-20T13:05:00Z' })));
    expect(visible(tarjeta)).toContain('Nuevo · reservó por el link');
    expect(tarjeta.getAttribute('aria-label')).toContain('nuevo: reservó por el link');
  });
});

describe('nombreAccesibleTurno', () => {
  it('nombra el cambio de cada tipo', () => {
    expect(nombreAccesibleTurno(turno({ cambioPaciente: 'REPROGRAMO' }), '10:30', '11:30')).toContain('nuevo: reprogramó por el link');
    expect(nombreAccesibleTurno(turno({ estado: 'CANCELADO', cambioPaciente: 'CANCELO' }), '10:30', '11:30')).toContain('Cancelado, nuevo: canceló por el link');
  });
});

describe('tarjetaBloqueo', () => {
  const bloqueo: BloqueoAgenda = { id: 'b-1', inicio: '2026-11-20T18:00:00Z', fin: '2026-11-20T19:00:00Z', motivo: 'Reunión con el laboratorio' };

  it('es rayada, con la etiqueta "Bloqueo" y su motivo', () => {
    const tarjeta = tarjetaBloqueo({ tipo: 'bloqueo', bloqueo, desde: '15:00', hasta: '16:00' });
    expect(tarjeta.className).toContain('fondo-rayado');
    expect(visible(tarjeta)).toContain('Reunión con el laboratorio');
    expect(visible(tarjeta)).toContain('Bloqueo');
  });

  it('sin motivo dice "Bloqueado"', () => {
    const tarjeta = tarjetaBloqueo({ tipo: 'bloqueo', bloqueo: { ...bloqueo, motivo: null }, desde: '15:00', hasta: '16:00' });
    expect(visible(tarjeta)).toContain('Bloqueado');
  });
});
