import { describe, expect, it } from 'vitest';
import { escaparTexto, generarIcs, plegarLinea } from './ics';

const octetos = (linea: string) => new TextEncoder().encode(linea).length;

const base = {
  uid: '7f1e2d3c-0000-4000-8000-000000000001',
  inicio: '2026-11-24T13:30:00Z',
  fin: '2026-11-24T14:15:00Z',
  titulo: 'Limpieza dental · Consultorio Demo Sur',
  ubicacion: 'Consultorio Demo Sur, Av. Ejemplo 1234, CABA',
  descripcion: 'Para ver, cambiar o cancelar tu turno: https://agenda.example/t/ejemplo-ficticio-7Qm2',
  ahora: new Date('2026-11-21T15:00:00Z'),
};

describe('escaparTexto (RFC 5545 §3.3.11)', () => {
  it('escapa primero la barra invertida, después ; y , y los saltos de línea', () => {
    expect(escaparTexto('a\\b;c,d')).toBe('a\\\\b\\;c\\,d');
    expect(escaparTexto('uno\r\ndos\nthree\rcuatro')).toBe('uno\\ndos\\nthree\\ncuatro');
  });
});

describe('plegarLinea (RFC 5545 §3.1)', () => {
  it('no toca una línea de 75 octetos o menos', () => {
    expect(plegarLinea('a'.repeat(75))).toBe('a'.repeat(75));
  });

  it('corta en 75 octetos con CRLF y un espacio, sin partir caracteres multibyte', () => {
    const linea = `DESCRIPTION:${'á'.repeat(60)}`;
    const plegada = plegarLinea(linea);
    const partes = plegada.split('\r\n');
    expect(partes.length).toBeGreaterThan(1);
    for (const parte of partes) expect(octetos(parte)).toBeLessThanOrEqual(75);
    for (const parte of partes.slice(1)) expect(parte.startsWith(' ')).toBe(true);
    expect(partes.map((p, i) => (i === 0 ? p : p.slice(1))).join('')).toBe(linea);
    expect(plegada).not.toContain('�');
  });
});

describe('generarIcs', () => {
  it('arma un evento con inicio y fin en UTC, CRLF, el consultorio y el link en la descripción', () => {
    const ics = generarIcs(base);
    expect(ics.endsWith('\r\n')).toBe(true);
    expect(ics.replace(/\r\n/g, '')).not.toMatch(/[\r\n]/);
    const lineas = ics.split('\r\n');
    expect(lineas[0]).toBe('BEGIN:VCALENDAR');
    expect(lineas).toContain('VERSION:2.0');
    expect(lineas).toContain('BEGIN:VEVENT');
    expect(lineas).toContain('UID:7f1e2d3c-0000-4000-8000-000000000001@turnos');
    expect(lineas).toContain('DTSTAMP:20261121T150000Z');
    expect(lineas).toContain('DTSTART:20261124T133000Z');
    expect(lineas).toContain('DTEND:20261124T141500Z');
    expect(lineas).toContain('LOCATION:Consultorio Demo Sur\\, Av. Ejemplo 1234\\, CABA');
    const desplegado = ics.replace(/\r\n /g, '');
    expect(desplegado).toContain('DESCRIPTION:Para ver\\, cambiar o cancelar tu turno: https://agenda.example/t/ejemplo-ficticio-7Qm2');
    expect(lineas).toContain('END:VEVENT');
    expect(lineas.at(-2)).toBe('END:VCALENDAR');
  });

  it('una dirección con coma, punto y coma y un salto de línea no puede agregar propiedades', () => {
    const ics = generarIcs({ ...base, ubicacion: 'Av. Ejemplo 1234, piso 2; depto B\r\nATTENDEE:mailto:x@example.com' });
    const lineas = ics.split('\r\n');
    expect(lineas.some((l) => l.startsWith('ATTENDEE'))).toBe(false);
    const desplegado = ics.replace(/\r\n /g, '');
    expect(desplegado).toContain('LOCATION:Av. Ejemplo 1234\\, piso 2\\; depto B\\nATTENDEE:mailto:x@example.com');
    for (const linea of lineas) expect(octetos(linea)).toBeLessThanOrEqual(75);
  });

  it('el link largo de la descripción queda plegado y se recupera al desplegar', () => {
    const link = `https://agenda.example/t/${'x'.repeat(90)}`;
    const ics = generarIcs({ ...base, descripcion: `Tu link: ${link}` });
    for (const linea of ics.split('\r\n')) expect(octetos(linea)).toBeLessThanOrEqual(75);
    expect(ics.replace(/\r\n /g, '')).toContain(link);
  });
});
