import { vi } from 'vitest';
import type { DetalleTurno } from '../agenda/api';
import type { EntornoDeAccion } from '../agenda/puntos-de-entrada';
import { crearContextoDePagina, type Guion } from '../configuracion/prueba-contexto';

/** Solo para tests de C-14: un turno y un entorno de acción con un backend falso. Datos ficticios. */

export const BA = 'America/Argentina/Buenos_Aires';

/** Instante UTC de un día y hora de Buenos Aires (UTC-3). */
export const en = (dia: string, hora: string) => new Date(`${dia}T${hora}:00-03:00`).toISOString();

export function detalleDePrueba(extra: Partial<DetalleTurno> = {}): DetalleTurno {
  return {
    id: 't-1',
    inicio: en('2026-11-20', '16:00'),
    fin: en('2026-11-20', '16:30'),
    duracionMin: 30,
    estado: 'RESERVADO',
    esSobreturno: false,
    origen: 'MANUAL',
    prestacion: 'Consulta',
    paciente: { nombre: 'Federico', apellido: 'Ejemplo', dni: '30000004', telefono: '+5491100000004', email: null },
    tieneNota: false,
    notaInterna: null,
    canceladoPor: null,
    motivoCancelacion: null,
    cambioPaciente: null,
    novedadPacienteAt: null,
    horarioAnterior: null,
    version: 3,
    ...extra,
  };
}

/** Respuesta del backend con la forma del detalle (`DetalleResponse`). */
export function detalleApi(extra: Record<string, unknown> = {}): Record<string, unknown> {
  return {
    id: 't-1',
    inicio: en('2026-11-20', '16:00'),
    fin: en('2026-11-20', '16:30'),
    duracionMin: 30,
    estado: 'RESERVADO',
    esSobreturno: false,
    origen: 'MANUAL',
    prestacion: { id: 'p-1', nombre: 'Consulta' },
    paciente: { id: 'pa-1', nombre: 'Federico', apellido: 'Ejemplo', dni: '30000004', telefono: '+5491100000004', requiereRevision: false },
    tieneNota: false,
    version: 4,
    ...extra,
  };
}

export function entornoDePrueba(guion: Guion) {
  const { contexto, llamadas } = crearContextoDePagina(guion);
  const alTerminar = vi.fn();
  const entorno: EntornoDeAccion = { http: contexto.http, zona: BA, alTerminar };
  return { entorno, llamadas, alTerminar };
}

export const dialogoAbierto = () => document.querySelector<HTMLElement>('[role="dialog"], [role="alertdialog"]');

export const botonEn = (raiz: ParentNode | null, texto: string) =>
  [...(raiz ?? document).querySelectorAll<HTMLButtonElement>('button')].find((b) => b.textContent?.replace(/\s+/g, ' ').trim() === texto) ?? null;

export const textoDe = (nodo: Element | null) => (nodo?.textContent ?? '').replace(/\s+/g, ' ').trim();

export const avisos = () => [...document.querySelectorAll('[data-region-avisos] > *')].map((n) => textoDe(n));
