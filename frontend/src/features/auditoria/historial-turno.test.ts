import { describe, expect, it } from 'vitest';
import { botonConTexto, crearContextoDePagina, esperar, json, problema, type Guion } from '../configuracion/prueba-contexto';
import { tarjetaHistorial } from './historial-turno';

// Tareas 5.2 y 5.2b de C-18 (spec pantalla-registro-cambios; mockup `turno-historial.html`). Datos ficticios.
const RUTA = 'GET /api/v1/turnos/t-1/historial';
const BA = 'America/Argentina/Buenos_Aires';

const reserva = (actor: Record<string, unknown>) => ({
  id: 'e-1',
  ocurridoAt: '2026-11-16T13:12:00Z',
  actor,
  accion: 'TURNO_CREADO',
  entidad: 'turno',
  entidadId: 't-1',
  valorNuevo: { inicio: '2026-11-23T13:00:00Z', duracion_min: 30, paciente_id: '018f0000-0000-7000-8000-000000000002', origen: 'LINK', es_sobreturno: false },
});

const reprogramacion = {
  id: 'e-2',
  ocurridoAt: '2026-11-17T14:00:00Z',
  actor: { tipo: 'ODONTOLOGO', id: 'u-1', nombre: 'Dra. Ejemplo', identidadEnRevision: false },
  accion: 'TURNO_REPROGRAMADO',
  entidad: 'turno',
  entidadId: 't-1',
  valorAnterior: { inicio: '2026-11-23T13:00:00Z', fin: '2026-11-23T13:30:00Z' },
  valorNuevo: { inicio: '2026-11-23T14:00:00Z', fin: '2026-11-23T14:30:00Z' },
};

const PACIENTE = { tipo: 'PACIENTE', id: 'pa-1', nombre: 'Hernán Muestra', identidadEnRevision: false };
const texto = (e: Element | null | undefined) => (e?.textContent ?? '').replace(/\s+/g, ' ').trim();

async function montar(guion: Guion) {
  const prueba = crearContextoDePagina(guion);
  const tarjeta = tarjetaHistorial(prueba.contexto.http, 't-1', BA);
  prueba.contenedor.append(tarjeta);
  await esperar();
  await esperar();
  return { ...prueba, tarjeta };
}

describe('tarjetaHistorial', () => {
  it('turno reservado y reprogramado: cuenta los eventos en orden', async () => {
    const { tarjeta } = await montar({ [RUTA]: [json([reserva(PACIENTE), reprogramacion])] });
    const filas = [...tarjeta.querySelectorAll('ol > li')];

    expect(filas).toHaveLength(2);
    expect(texto(filas[0])).toContain('Reservó por tu link');
    expect(texto(filas[0])).toContain('Hernán Muestra · lun 16/11 10:12');
    expect(texto(filas[1])).toContain('Reprogramaste el turno');
    expect(texto(filas[1]?.querySelector('dl'))).toBe('Inicio: antes lun 23/11 10:00, después lun 23/11 11:00');
    expect(texto(tarjeta)).not.toContain('018f');
  });

  it('enlaza al registro filtrado por el turno', async () => {
    const { tarjeta } = await montar({ [RUTA]: [json([reprogramacion])] });
    const enlace = [...tarjeta.querySelectorAll('a')].find((a) => a.getAttribute('href') === '/app/auditoria?turnoId=t-1');

    expect(enlace?.textContent).toContain('Ver en el registro de cambios');
    expect(enlace?.hasAttribute('data-link')).toBe(true);
  });

  it('con la revisión pendiente no atribuye la reserva a la ficha', async () => {
    const { tarjeta } = await montar({ [RUTA]: [json([reserva({ tipo: 'PACIENTE', id: 'pa-1', identidadEnRevision: true })])] });

    expect(texto(tarjeta)).toContain('Reservó por tu link');
    expect(texto(tarjeta)).toContain('Reserva con este DNI · lun 16/11 10:12');
    expect(texto(tarjeta)).not.toContain('Hernán Muestra');
  });

  it('muestra que está cargando', () => {
    const prueba = crearContextoDePagina({ [RUTA]: [() => new Promise<Response>(() => undefined)] });
    const tarjeta = tarjetaHistorial(prueba.contexto.http, 't-1', BA);

    expect(tarjeta.querySelector('[aria-busy="true"]')).not.toBeNull();
  });

  it('si falla, avisa y deja reintentar', async () => {
    const { tarjeta } = await montar({ [RUTA]: [problema(500, 'ERROR_INTERNO'), json([reprogramacion])] });

    expect(texto(tarjeta)).toContain('No pudimos cargar el historial');
    expect(texto(tarjeta)).toContain('El resto del turno se ve igual.');
    botonConTexto(tarjeta, 'Reintentar')?.click();
    await esperar();
    await esperar();
    expect(texto(tarjeta)).toContain('Reprogramaste el turno');
    expect(texto(tarjeta)).not.toContain('No pudimos cargar el historial');
  });
});
