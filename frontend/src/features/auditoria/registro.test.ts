import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { pagina } from '../../pages/auditoria';
import { botonConTexto, crearContextoDePagina, escribir, esperar, json, problema, type Guion } from '../configuracion/prueba-contexto';

// Tarea 5.3 y 5.4 de C-18 (spec pantalla-registro-cambios; mockup `registro.html`). Datos ficticios.
const base = (query = '') => `GET /api/v1/auditoria?tamano=50${query}`;
const texto = (e: Element | null | undefined) => (e?.textContent ?? '').replace(/\s+/g, ' ').trim();

const reprogramado = (id = 'e-1', ocurridoAt = '2026-11-20T14:42:00Z') => ({
  id,
  ocurridoAt,
  actor: { tipo: 'ODONTOLOGO', id: 'u-1', nombre: 'Dra. Ejemplo', identidadEnRevision: false },
  accion: 'TURNO_REPROGRAMADO',
  entidad: 'turno',
  entidadId: 't-1',
  valorAnterior: { inicio: '2026-11-23T13:00:00Z' },
  valorNuevo: { inicio: '2026-11-23T14:00:00Z' },
  contexto: { inicio: '2026-11-23T14:00:00Z', paciente: 'Carla Ficticia' },
});

const fallido = {
  id: 'e-3',
  ocurridoAt: '2026-11-19T11:02:00Z',
  actor: { tipo: 'ODONTOLOGO', identidadEnRevision: false },
  accion: 'LOGIN_FALLIDO',
  entidad: 'usuario',
  entidadId: 'u-1',
  valorNuevo: { intentos_fallidos: 1, bloqueado_hasta: null },
  ip: '203.0.113.24',
};

const registro = (eventos: unknown[], siguienteCursor?: string) => json({ eventos, siguienteCursor });

async function montar(guion: Guion, url = '/app/auditoria') {
  history.replaceState(null, '', url);
  const prueba = crearContextoDePagina(guion);
  pagina.montar(prueba.contexto);
  await esperar();
  await esperar();
  return prueba;
}

const campo = <T extends HTMLElement>(raiz: ParentNode, nombre: string) => raiz.querySelector<T>(`[name="${nombre}"]`);
const asentar = async () => {
  await esperar();
  await esperar();
};

beforeEach(() => {
  vi.useFakeTimers({ toFake: ['Date'] });
  vi.setSystemTime(new Date('2026-11-20T15:00:00Z'));
});

afterEach(() => {
  vi.useRealTimers();
  history.replaceState(null, '', '/');
});

describe('Registro de cambios: lista', () => {
  it('agrupa por día y reemplaza la sección pendiente', async () => {
    const { contenedor } = await montar({ [base()]: [registro([reprogramado(), fallido])] });

    expect([...contenedor.querySelectorAll('h2')].map(texto)).toEqual(['Hoy · viernes 20 de noviembre', 'Ayer · jueves 19 de noviembre']);
    expect(texto(contenedor)).not.toContain('Esta sección todavía no está disponible');
    expect(contenedor.querySelectorAll('section ul > li')).toHaveLength(2);
  });

  it('cada fila dice quién, qué, sobre qué turno y qué cambió', async () => {
    const { contenedor } = await montar({ [base()]: [registro([reprogramado()])] });
    const fila = contenedor.querySelector('[data-evento="e-1"]');

    expect(texto(fila)).toContain('Reprogramaste un turno');
    expect(texto(fila)).toContain('11:42');
    expect(texto(fila)).toContain('Vos Dra. Ejemplo');
    expect(texto(fila)).toContain('Turno de Carla Ficticia · lun 23/11 11:00');
    expect(fila?.querySelector('a')?.getAttribute('href')).toBe('/app/agenda/turnos/t-1');
    expect(texto(fila?.querySelector('dl'))).toBe('Inicio: antes lun 23/11 10:00, después lun 23/11 11:00');
  });

  it('un login fallido muestra "Sin sesión", el intento y la IP', async () => {
    const { contenedor } = await montar({ [base()]: [registro([fallido])] });
    const fila = texto(contenedor.querySelector('[data-evento="e-3"]'));

    expect(fila).toContain('Intento fallido de iniciar sesión con tu email');
    expect(fila).toContain('Sin sesión');
    expect(fila).toContain('1 de 5 intentos');
    expect(fila).toContain('IP 203.0.113.24');
    expect(fila).not.toContain('Sistema');
  });

  it('no muestra la IP fuera de los accesos', async () => {
    const { contenedor } = await montar({ [base()]: [registro([{ ...reprogramado(), ip: '198.51.100.7' }])] });

    expect(texto(contenedor)).not.toContain('198.51.100.7');
  });

  it('con la revisión pendiente muestra "Reserva con este DNI"', async () => {
    const evento = { ...reprogramado(), actor: { tipo: 'PACIENTE', id: 'pa-1', identidadEnRevision: true } };
    const { contenedor } = await montar({ [base()]: [registro([evento])] });
    const fila = texto(contenedor.querySelector('[data-evento="e-1"]'));

    expect(fila).toContain('Reprogramó por el link');
    expect(fila).toContain('Reserva con este DNI');
  });

  it('"Cargar más" agrega al final y lleva el foco al primer evento nuevo', async () => {
    const { contenedor, llamadas } = await montar({
      [base()]: [registro([reprogramado('e-1')], 'c/1')],
      [base('&cursor=c%2F1')]: [registro([reprogramado('e-2', '2026-11-20T13:00:00Z')])],
    });

    botonConTexto(contenedor, 'Cargar más')?.click();
    await asentar();

    expect(llamadas).toHaveLength(2);
    expect(contenedor.querySelectorAll('[data-evento]')).toHaveLength(2);
    expect(document.activeElement).toBe(contenedor.querySelector('[data-evento="e-2"]'));
    expect(botonConTexto(contenedor, 'Cargar más')).toBeNull();
  });

  it('si el cursor dejó de valer, vuelve a la primera página', async () => {
    const { contenedor } = await montar({
      [base()]: [registro([reprogramado('e-1')], 'viejo'), registro([reprogramado('e-9')])],
      [base('&cursor=viejo')]: [problema(400, 'VALIDACION')],
    });

    botonConTexto(contenedor, 'Cargar más')?.click();
    await asentar();
    await asentar();

    expect(contenedor.querySelector('[data-evento="e-9"]')).not.toBeNull();
    expect(contenedor.querySelector('[data-evento="e-1"]')).toBeNull();
  });
});

describe('Registro de cambios: estados', () => {
  it('sin eventos todavía', async () => {
    const { contenedor } = await montar({ [base()]: [registro([])] });

    expect(texto(contenedor)).toContain('Todavía no hay cambios');
    expect(contenedor.querySelector('form')?.hidden).toBe(true);
  });

  it('sin resultados: ofrece quitar los filtros', async () => {
    const { contenedor } = await montar({ [base('&desde=2026-11-01')]: [registro([])], [base()]: [registro([reprogramado()])] }, '/app/auditoria?desde=2026-11-01');

    expect(texto(contenedor)).toContain('No hay cambios con estos filtros');
    const quitar = [...contenedor.querySelectorAll('button')].filter((b) => texto(b) === 'Quitar filtros').at(-1);
    quitar?.click();
    await asentar();

    expect(location.search).toBe('');
    expect(contenedor.querySelector('[data-evento="e-1"]')).not.toBeNull();
  });

  it('error con "Reintentar"', async () => {
    const { contenedor } = await montar({ [base()]: [problema(500, 'ERROR_INTERNO'), registro([reprogramado()])] });

    expect(contenedor.querySelector('[role="alert"]')?.textContent).toContain('No pudimos cargar el registro');
    botonConTexto(contenedor, 'Reintentar')?.click();
    await asentar();
    expect(contenedor.querySelector('[data-evento="e-1"]')).not.toBeNull();
  });

  it('sin conexión avisa y oculta los filtros', async () => {
    const { contenedor } = await montar({ [base()]: [() => Promise.reject(new TypeError('Failed to fetch'))] });

    expect(texto(contenedor)).toContain('No tenés conexión');
    expect(texto(contenedor)).toContain('El registro se consulta en línea.');
    expect(contenedor.querySelector('form')?.hidden).toBe(true);
  });
});

describe('Registro de cambios: filtros en la URL', () => {
  it('llega desde el detalle con el chip del turno', async () => {
    const { contenedor, llamadas } = await montar({ [base('&turnoId=t-1')]: [registro([reprogramado()])] }, '/app/auditoria?turnoId=t-1');

    expect(llamadas[0]?.url).toContain('turnoId=t-1');
    expect(texto(contenedor.querySelector('[data-chip-turno]'))).toContain('Turno de Carla Ficticia · lun 23/11 11:00');
    expect(texto(contenedor)).toContain('1 cambio de este turno');
  });

  it('quitar el chip saca turnoId de la URL y muestra todo', async () => {
    const { contenedor } = await montar(
      { [base('&turnoId=t-1')]: [registro([reprogramado()])], [base()]: [registro([reprogramado('e-5')])] },
      '/app/auditoria?turnoId=t-1',
    );

    contenedor.querySelector<HTMLButtonElement>('[aria-label="Quitar el filtro del turno"]')?.click();
    await asentar();

    expect(location.search).toBe('');
    expect(contenedor.querySelector('[data-chip-turno]')).toBeNull();
    expect(contenedor.querySelector('[data-evento="e-5"]')).not.toBeNull();
  });

  it('el tipo de cambio pide todas sus acciones y queda en la URL', async () => {
    const { contenedor, llamadas } = await montar({
      [base()]: [registro([reprogramado()])],
      [base('&accion=CUENTA_CREADA&accion=LOGIN_EXITOSO&accion=LOGIN_FALLIDO&accion=PASSWORD_CAMBIADA&accion=PASSWORD_RECUPERADA')]: [registro([fallido])],
    });

    escribir(campo<HTMLSelectElement>(contenedor, 'accion'), 'accesos');
    await asentar();

    expect(llamadas).toHaveLength(2);
    expect(location.search).toBe('?accion=accesos');
    expect(contenedor.querySelector('[data-evento="e-3"]')).not.toBeNull();
  });

  it('recargar conserva los filtros', async () => {
    const { contenedor } = await montar(
      { [base('&desde=2026-11-01&hasta=2026-11-20&accion=EXPORTACION_GENERADA')]: [registro([reprogramado()])] },
      '/app/auditoria?desde=2026-11-01&hasta=2026-11-20&accion=exportaciones',
    );

    expect(campo<HTMLInputElement>(contenedor, 'desde')?.value).toBe('2026-11-01');
    expect(campo<HTMLInputElement>(contenedor, 'hasta')?.value).toBe('2026-11-20');
    expect(campo<HTMLSelectElement>(contenedor, 'accion')?.value).toBe('exportaciones');
  });

  it('rango invertido: avisa debajo de "Hasta" y no pide nada', async () => {
    const { contenedor, llamadas } = await montar({ [base()]: [registro([reprogramado()])], [base('&desde=2026-11-20')]: [registro([reprogramado()])] });

    escribir(campo<HTMLInputElement>(contenedor, 'desde'), '2026-11-20');
    await asentar();
    const pedidos = llamadas.length;
    const hasta = campo<HTMLInputElement>(contenedor, 'hasta');
    escribir(hasta, '2026-11-10');
    await asentar();

    expect(llamadas).toHaveLength(pedidos);
    expect(hasta?.getAttribute('aria-invalid')).toBe('true');
    const error = document.getElementById(hasta?.getAttribute('aria-describedby') ?? '');
    expect(texto(error)).toBe('Tiene que ser igual o posterior a "Desde".');
  });
});
