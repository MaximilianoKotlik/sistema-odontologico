import { describe, expect, it } from 'vitest';
import type { BloqueoAgenda, FranjaDisponible, TurnoAgenda } from './modelo';
import { grillaSemanal, listaSemanal, type EntradaSemana } from './semana';
import { instanteLocal } from './tiempo';

// Tarea 3.3 de C-13 (design D4, D9; mockup `agenda.html`, vista Semana). Datos ficticios. TZ del proceso: Europe/Madrid.
const BA = 'America/Argentina/Buenos_Aires';
const en = (dia: string, hora: string) => instanteLocal(dia, hora, BA).toISOString();

function turno(id: string, dia: string, desde: string, hasta: string, extra: Partial<TurnoAgenda> = {}): TurnoAgenda {
  return {
    id,
    inicio: en(dia, desde),
    fin: en(dia, hasta),
    duracionMin: 30,
    estado: 'CONFIRMADO',
    esSobreturno: false,
    origen: 'MANUAL',
    prestacion: 'Consulta',
    paciente: { nombre: 'Iván', apellido: 'Ejemplo' },
    tieneNota: false,
    canceladoPor: null,
    cambioPaciente: null,
    novedadPacienteAt: null,
    ...extra,
  };
}

const HORARIO: FranjaDisponible[] = [1, 2, 3, 4, 5].flatMap((diaSemana) => [
  { diaSemana, horaInicio: '09:00', horaFin: '13:00' },
  { diaSemana, horaInicio: '15:00', horaFin: '19:00' },
]).concat([{ diaSemana: 6, horaInicio: '09:00', horaFin: '13:00' }]);

const BLOQUEO: BloqueoAgenda = { id: 'b-1', inicio: en('2026-11-20', '15:00'), fin: en('2026-11-20', '16:00'), motivo: 'Reunión con el laboratorio' };

function entrada(extra: Partial<EntradaSemana> = {}): EntradaSemana {
  return {
    lunes: '2026-11-16',
    zona: BA,
    turnos: [
      turno('a', '2026-11-16', '09:00', '09:30'),
      turno('base', '2026-11-20', '10:30', '11:30', { duracionMin: 60, paciente: { nombre: 'Carla', apellido: 'Ficticia' }, prestacion: 'Extracción' }),
      turno('sobre', '2026-11-20', '10:45', '11:05', { duracionMin: 20, esSobreturno: true, estado: 'RESERVADO', paciente: { nombre: 'Diego', apellido: 'Muestra' } }),
      turno('c', '2026-11-18', '11:00', '11:30', { estado: 'CANCELADO' }),
      turno('n', '2026-11-20', '16:00', '16:30', { estado: 'RESERVADO', cambioPaciente: 'RESERVO', paciente: { nombre: 'Federico', apellido: 'Ejemplo' } }),
    ],
    bloqueos: [BLOQUEO],
    franjas: HORARIO,
    ahora: new Date(en('2026-11-20', '12:10')),
    mostrarCancelados: true,
    ...extra,
  };
}

const texto = (e: Element | null | undefined) => (e?.textContent ?? '').replace(/\s+/g, ' ').trim();
/** Texto visible por partes (cada hijo es un bloque en pantalla). */
const partes = (e: Element | null | undefined) => [...(e?.children ?? [])].map((c) => texto(c)).filter((t) => t !== '').join(' ');

describe('grillaSemanal', () => {
  it('tiene un encabezado por día con su conteo, que lleva a la vista diaria', () => {
    const grilla = grillaSemanal(entrada());
    const dias = [...grilla.querySelectorAll<HTMLAnchorElement>('[data-encabezado-dia]')];
    expect(dias.map((d) => partes(d))).toEqual(['LUN 16 1 turno', 'MAR 17 Sin turnos', 'MIÉ 18 1 turno', 'JUE 19 Sin turnos', 'VIE 20 3 turnos', 'SÁB 21 Sin turnos', 'DOM 22 No atendés']);
    expect(dias[4]?.getAttribute('href')).toBe('/app/agenda?vista=dia&fecha=2026-11-20');
    expect(dias[4]?.getAttribute('aria-current')).toBe('date');
    expect(dias[4]?.getAttribute('aria-label')).toBe('viernes 20 de noviembre, 3 turnos');
  });

  it('expone por día una lista en orden de hora con nombres accesibles completos (D9)', () => {
    const viernes = grillaSemanal(entrada()).querySelector('[data-columna="2026-11-20"] ol');
    expect(viernes?.getAttribute('aria-label')).toBe('viernes 20 de noviembre');
    const links = [...(viernes?.querySelectorAll('a') ?? [])].map((a) => a.getAttribute('aria-label'));
    expect(links[0]).toBe('10:30 a 11:30, Carla Ficticia, Extracción, Confirmado');
    expect(links[1]).toContain('Diego Muestra');
  });

  it('ubica cada bloque según su hora, a 1,2 px por minuto desde el inicio del rango', () => {
    const grilla = grillaSemanal(entrada());
    const base = grilla.querySelector<HTMLElement>('a[data-turno="base"]');
    // Rango de 08:00 a 20:00: 10:30 está 150 minutos después del inicio, y dura 60.
    expect(base?.style.top).toBe('calc(var(--space-1) * 0.3 * 150)');
    expect(base?.style.height).toBe('calc(var(--space-1) * 0.3 * 60)');
  });

  it('el sobreturno superpuesto va a la derecha, con borde punteado, alto mínimo y el resto en el title', () => {
    const sobre = grillaSemanal(entrada()).querySelector<HTMLElement>('a[data-turno="sobre"]');
    expect(sobre?.dataset['superpuesto']).toBe('');
    expect(sobre?.className).toContain('border-dashed');
    expect(sobre?.style.minHeight).not.toBe('');
    expect(texto(sobre)).toContain('10:45');
    expect(texto(sobre)).toContain('Muestra');
    expect(sobre?.title).toContain('Diego Muestra');
  });

  it('los bloques sin lugar para dos líneas muestran una sola, con elipsis', () => {
    const grilla = grillaSemanal(entrada({ turnos: [turno('corto', '2026-11-16', '09:00', '09:20', { duracionMin: 20 })] }));
    const corto = grilla.querySelector<HTMLElement>('a[data-turno="corto"]');
    expect(corto?.className).toContain('truncate');
    expect(corto?.querySelector('[data-prestacion]')).toBeNull();
    expect(corto?.title).toContain('Consulta');
  });

  it('marca los cambios sin ver con un punto, los bloqueos rayados y la hora actual en hoy', () => {
    const grilla = grillaSemanal(entrada());
    expect(grilla.querySelector('a[data-turno="n"] [data-punto-nuevo]')).not.toBeNull();
    expect(grilla.querySelector('[data-bloqueo="b-1"]')?.className).toContain('fondo-rayado');
    expect(grilla.querySelector('[data-columna="2026-11-20"] [data-ahora]')).not.toBeNull();
    expect(grilla.querySelector('[data-columna="2026-11-19"] [data-ahora]')).toBeNull();
  });

  it('sin cancelados, no los dibuja ni los cuenta', () => {
    const grilla = grillaSemanal(entrada({ mostrarCancelados: false }));
    expect(grilla.querySelector('a[data-turno="c"]')).toBeNull();
    expect(partes(grilla.querySelector('[data-encabezado-dia="2026-11-18"]'))).toBe('MIÉ 18 Sin turnos');
  });

  it('las horas de la grilla son decorativas', () => {
    expect(grillaSemanal(entrada()).querySelector('[data-horas]')?.getAttribute('aria-hidden')).toBe('true');
  });
});

describe('listaSemanal (mobile)', () => {
  it('agrupa por día, con el conteo y un link a la vista diaria', () => {
    const lista = listaSemanal(entrada());
    const titulos = [...lista.querySelectorAll('h3')].map((h) => texto(h));
    expect(titulos[0]).toBe('Lunes 16 · 1 turno');
    expect(titulos[4]).toBe('Viernes 20 · 3 turnos');
    expect(lista.querySelector('h3 a')?.getAttribute('href')).toBe('/app/agenda?vista=dia&fecha=2026-11-16');
  });

  it('cada turno es una fila tocable con hora, paciente y estado, y los días vacíos lo dicen', () => {
    const lista = listaSemanal(entrada());
    const viernes = lista.querySelector('[data-dia="2026-11-20"]');
    expect(partes(viernes?.querySelector('a[data-turno="base"]'))).toBe('10:30 Carla Ficticia Confirmado');
    expect(texto(lista.querySelector('[data-dia="2026-11-17"] [data-vacio]'))).toBe('Sin turnos');
    expect(texto(lista.querySelector('[data-dia="2026-11-22"] [data-vacio]'))).toBe('No atendés');
    expect(texto(viernes?.querySelector('[data-bloqueo="b-1"]'))).toContain('Reunión con el laboratorio');
  });
});
