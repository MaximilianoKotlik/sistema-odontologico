import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { botonConTexto, crearContextoDePagina, esperar, json, problema, type Guion } from '../configuracion/prueba-contexto';
import { montarDetalle } from './detalle';
import { registrarPuntosDeEntrada, restablecerPuntosDeEntrada } from './puntos-de-entrada';
import { instanteLocal } from './tiempo';

// Tarea 3.5 de C-13 (spec detalle-turno; mockup `turno.html`). Datos ficticios. TZ del proceso: Europe/Madrid.
const BA = 'America/Argentina/Buenos_Aires';
const en = (dia: string, hora: string) => instanteLocal(dia, hora, BA).toISOString();
const RUTA = 'GET /api/v1/turnos/t-1';

const detalle = (extra: Record<string, unknown> = {}) => ({
  id: 't-1',
  inicio: en('2026-11-20', '10:30'),
  fin: en('2026-11-20', '11:30'),
  duracionMin: 60,
  estado: 'CONFIRMADO',
  esSobreturno: false,
  origen: 'MANUAL',
  prestacion: { id: 'p-1', nombre: 'Extracción' },
  paciente: { id: 'pa-3', nombre: 'Carla', apellido: 'Ficticia', dni: '30000003', telefono: '+54 9 11 0000-0003', email: 'carla.ficticia@example.com' },
  tieneNota: true,
  notaInterna: 'Trae radiografía panorámica.',
  version: 2,
  ...extra,
});

function montar(guion: Guion) {
  const pagina = crearContextoDePagina(guion);
  montarDetalle({ ...pagina.contexto, params: { id: 't-1' } });
  return pagina;
}

const texto = (e: Element | null | undefined) => (e?.textContent ?? '').replace(/\s+/g, ' ').trim();
const botones = (raiz: ParentNode) => [...raiz.querySelectorAll<HTMLButtonElement>('[data-acciones] button')].map((b) => texto(b));

beforeEach(() => {
  vi.useFakeTimers({ toFake: ['Date'] });
  vi.setSystemTime(new Date(en('2026-11-20', '10:00')));
});

afterEach(() => {
  vi.useRealTimers();
  restablecerPuntosDeEntrada();
});

describe('montarDetalle', () => {
  it('muestra un esqueleto y después los datos del paciente y del turno', async () => {
    const { contenedor } = montar({ [RUTA]: [json(detalle())] });
    expect(contenedor.querySelector('[aria-busy="true"]')).not.toBeNull();
    await esperar();
    expect(texto(contenedor.querySelector('h2'))).toBe('Carla Ficticia');
    expect(texto(contenedor)).toContain('Viernes 20 de noviembre · 10:30 a 11:30');
    expect(texto(contenedor)).toContain('Confirmado');
    expect(texto(contenedor)).toContain('30.000.003');
    expect(contenedor.querySelector('a[href="tel:+5491100000003"]')?.textContent).toContain('+54 9 11 0000-0003');
    expect(texto(contenedor)).toContain('carla.ficticia@example.com');
    expect(texto(contenedor)).toContain('Extracción · 60 min');
    expect(texto(contenedor)).toContain('Lo cargaste vos');
  });

  it('muestra el historial debajo de la tarjeta Turno, y si falla el resto se ve igual (C-18)', async () => {
    const { contenedor } = montar({ [RUTA]: [json(detalle())], [`${RUTA}/historial`]: [problema(500, 'ERROR_INTERNO')] });
    await esperar();
    await esperar();
    const historial = contenedor.querySelector('[data-historial]');
    expect(texto(historial?.previousElementSibling?.querySelector('h3'))).toBe('Turno');
    expect(texto(historial)).toContain('No pudimos cargar el historial');
    expect(texto(contenedor.querySelector('h2'))).toBe('Carla Ficticia');
  });

  it('el teléfono en E.164 que guarda el backend se lee agrupado (C-14)', async () => {
    const { contenedor } = montar({ [RUTA]: [json(detalle({ paciente: { nombre: 'Ana', apellido: 'Ejemplo', dni: '30000001', telefono: '+5491100000001' } }))] });
    await esperar();
    const enlace = contenedor.querySelector('a[href="tel:+5491100000001"]');
    expect(enlace?.textContent).toBe('+54 9 11 0000-0001');
  });

  it('tiene "Volver a la agenda" al día del turno', async () => {
    const { contenedor } = montar({ [RUTA]: [json(detalle())] });
    await esperar();
    // El selector de atributo de jsdom no tolera `?` y `&` en el valor: se busca por getAttribute.
    const volver = [...contenedor.querySelectorAll('a')].find((a) => a.getAttribute('href') === '/app/agenda?vista=dia&fecha=2026-11-20');
    expect(volver?.textContent).toContain('Volver a la agenda');
  });

  it('muestra la nota interna, o que no hay', async () => {
    const con = montar({ [RUTA]: [json(detalle())] });
    await esperar();
    expect(texto(con.contenedor.querySelector('[data-nota]'))).toBe('Trae radiografía panorámica.');
    const sin = montar({ [RUTA]: [json(detalle({ tieneNota: false, notaInterna: undefined }))] });
    await esperar();
    expect(texto(sin.contenedor)).toContain('Sin nota. Solo la ves vos.');
  });

  it('sin email lo dice', async () => {
    const { contenedor } = montar({ [RUTA]: [json(detalle({ paciente: { nombre: 'Ana', apellido: 'Ejemplo', dni: '30000001', telefono: '+54 9 11 0000-0001' } }))] });
    await esperar();
    expect(texto(contenedor)).toContain('No dejó email');
  });

  it('confirmado futuro: acciones sin confirmar ni marcar ausente, y la aclaración de ausente', async () => {
    const { contenedor } = montar({ [RUTA]: [json(detalle())] });
    await esperar();
    expect(botones(contenedor)).toEqual(['Reprogramar', 'Ajustar duración', 'Editar nota', 'Cancelar turno']);
    expect(texto(contenedor)).toContain('“Marcar ausente” aparece cuando el turno ya empezó.');
    expect(contenedor.querySelector('[data-acciones] button')?.hasAttribute('data-requiere-conexion')).toBe(true);
  });

  it('reservado que ya empezó: confirmar y marcar ausente', async () => {
    vi.setSystemTime(new Date(en('2026-11-20', '10:40')));
    const { contenedor } = montar({ [RUTA]: [json(detalle({ estado: 'RESERVADO' }))] });
    await esperar();
    expect(botones(contenedor)).toEqual(['Confirmar', 'Reprogramar', 'Ajustar duración', 'Marcar ausente', 'Editar nota', 'Cancelar turno']);
  });

  it('cancelado por el paciente: quién y por qué, y solo "Editar nota"', async () => {
    const { contenedor } = montar({ [RUTA]: [json(detalle({ estado: 'CANCELADO', canceladoPor: 'PACIENTE', motivoCancelacion: 'Me surgió un viaje.' }))] });
    await esperar();
    expect(texto(contenedor)).toContain('Cancelado por el paciente');
    expect(texto(contenedor)).toContain('“Me surgió un viaje.”');
    expect(botones(contenedor)).toEqual(['Editar nota']);
  });

  it('las acciones son puntos de entrada de C-14', async () => {
    const accion = vi.fn();
    registrarPuntosDeEntrada({ nuevoTurno: vi.fn(), accion });
    const { contenedor } = montar({ [RUTA]: [json(detalle())] });
    await esperar();
    botonConTexto(contenedor, 'Reprogramar')?.click();
    expect(accion).toHaveBeenCalledWith('REPROGRAMAR', expect.objectContaining({ id: 't-1', version: 2 }), expect.objectContaining({ zona: 'America/Argentina/Buenos_Aires' }));
  });

  it('al terminar una acción de C-14, el detalle se vuelve a pedir', async () => {
    const accion = vi.fn();
    registrarPuntosDeEntrada({ nuevoTurno: vi.fn(), accion });
    const { contenedor, llamadas } = montar({ [RUTA]: [json(detalle()), json(detalle({ estado: 'CANCELADO', canceladoPor: 'ODONTOLOGO' }))] });
    await esperar();
    botonConTexto(contenedor, 'Reprogramar')?.click();
    (accion.mock.calls[0]?.[2] as { alTerminar: () => void }).alTerminar();
    await esperar();
    expect(llamadas.filter((l) => l.url === '/api/v1/turnos/t-1')).toHaveLength(2);
    expect(botones(contenedor)).toEqual(['Editar nota']);
  });

  it('un cambio del paciente se explica con el horario anterior y se marca como visto al abrirse', async () => {
    const { contenedor, llamadas } = montar({
      [RUTA]: [
        json(
          detalle({
            estado: 'RESERVADO',
            origen: 'LINK',
            cambioPaciente: 'REPROGRAMO',
            novedadPacienteAt: en('2026-11-19', '21:40'),
            horarioAnterior: { inicio: en('2026-11-19', '16:30'), fin: en('2026-11-19', '17:30') },
          }),
        ),
      ],
      'POST /api/v1/turnos/t-1/visto': [() => new Response(null, { status: 204 })],
    });
    await esperar();
    await esperar();
    const aviso = contenedor.querySelector('[data-cambio-paciente]');
    expect(texto(aviso)).toContain('El paciente lo reprogramó desde tu link ayer a las 21:40');
    expect(texto(aviso)).toContain('Antes era el jueves 19 de noviembre a las 16:30');
    expect(texto(aviso)).toContain('Ya lo marcamos como visto.');
    expect(llamadas.find((l) => l.metodo === 'POST')?.cuerpo).toEqual({ novedadAt: en('2026-11-19', '21:40') });
  });

  it('un sobreturno dice con qué turno se superpone', async () => {
    const desde = en('2026-11-20', '10:45');
    const hasta = en('2026-11-20', '11:05');
    const { contenedor } = montar({
      [RUTA]: [json(detalle({ esSobreturno: true, estado: 'RESERVADO', inicio: desde, fin: hasta, duracionMin: 20, tieneNota: false, notaInterna: undefined, paciente: { nombre: 'Diego', apellido: 'Muestra', dni: '30000004', telefono: '+54 9 11 0000-0004' } }))],
      [`GET /api/v1/agenda?desde=${encodeURIComponent(desde)}&hasta=${encodeURIComponent(hasta)}`]: [
        json({
          turnos: [
            { id: 'base', inicio: en('2026-11-20', '10:30'), fin: en('2026-11-20', '11:30'), estado: 'CONFIRMADO', esSobreturno: false, prestacion: { nombre: 'Extracción' }, paciente: { nombre: 'Carla', apellido: 'Ficticia' } },
            { id: 't-1', inicio: desde, fin: hasta, estado: 'RESERVADO', esSobreturno: true, prestacion: { nombre: 'Control' }, paciente: { nombre: 'Diego', apellido: 'Muestra' } },
          ],
          bloqueos: [],
        }),
      ],
    });
    await esperar();
    await esperar();
    expect(texto(contenedor)).toContain('Sobreturno');
    expect(texto(contenedor.querySelector('[data-aviso-sobreturno]'))).toContain('Se superpone con el turno de 10:30 de Carla Ficticia.');
  });

  it('un 404 muestra "No encontramos este turno" con el acceso a la agenda', async () => {
    const { contenedor } = montar({ [RUTA]: [problema(404, 'TURNO_NO_ENCONTRADO')] });
    await esperar();
    expect(texto(contenedor.querySelector('[role="alert"]'))).toContain('No encontramos este turno');
    expect(contenedor.querySelector('a[href="/app/agenda"]')?.textContent).toContain('Volver a la agenda');
  });

  it('sin conexión explica que el detalle la necesita y ofrece reintentar', async () => {
    const { contenedor } = montar({ [RUTA]: [() => Promise.reject(new TypeError('Failed to fetch'))] });
    await esperar();
    expect(texto(contenedor)).toContain('Necesitás conexión para ver este turno');
    expect(botonConTexto(contenedor, 'Reintentar')).not.toBeNull();
  });

  it('un error del servidor es recuperable', async () => {
    const { contenedor } = montar({ [RUTA]: [problema(500, 'ERROR_INTERNO'), json(detalle())] });
    await esperar();
    expect(texto(contenedor.querySelector('[role="alert"]'))).toContain('No pudimos cargar el turno');
    botonConTexto(contenedor, 'Reintentar')?.click();
    await esperar();
    expect(texto(contenedor.querySelector('h2'))).toBe('Carla Ficticia');
  });
});
