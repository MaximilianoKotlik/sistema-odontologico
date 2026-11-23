import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { ApiError } from '../../shared/http';
import { sumarDias } from '../reserva-publica/dias';
import { montarGestion, type DependenciasGestion } from './pantalla';

// Datos ficticios (regla dura 17). TZ del proceso: Europe/Madrid, para detectar usos de la zona del dispositivo.
const AHORA = new Date('2026-11-20T12:00:00Z');
const TOKEN = 'ejemplo-ficticio-7Qm2yR8vLxA4';
const RUTA = `/api/v1/public/turnos/${TOKEN}`;

const vista = (extra: Record<string, unknown> = {}, turno: Record<string, unknown> = {}) => ({
  turno: { inicio: '2026-11-24T13:30:00Z', fin: '2026-11-24T14:15:00Z', prestacion: 'Limpieza dental', estado: 'RESERVADO', ...turno },
  paciente: { nombre: 'Ana', apellido: 'Ejemplo', dni: '30000001', telefono: '+5491100000042', email: 'ana@example.com' },
  consultorio: { nombre: 'Consultorio Demo Sur', direccion: 'Av. Ejemplo 1234, CABA', telefono: '+5491100000001', zonaHoraria: 'America/Argentina/Buenos_Aires' },
  notaInterna: 'nota privada del consultorio',
  puedeModificar: true,
  puedeReprogramar: true,
  modificableHasta: '2026-11-23T13:30:00Z',
  ...extra,
});

function huecosEntre(desde: string, hasta: string, porDia: Readonly<Record<string, readonly string[] | false>> = {}) {
  const dias = [];
  for (let fecha = desde; fecha <= hasta; fecha = sumarDias(fecha, 1)) {
    const valor = porDia[fecha];
    dias.push({ fecha, atiende: valor !== false, huecos: valor === false || valor === undefined ? [] : valor });
  }
  return { zonaHoraria: 'America/Argentina/Buenos_Aires', dias };
}

const SEMANA = huecosEntre('2026-11-20', '2026-12-03', {
  '2026-11-24': ['2026-11-24T12:00:00Z'],
  '2026-11-26': ['2026-11-26T18:00:00Z', '2026-11-26T18:15:00Z'],
  '2026-11-29': false,
});

type Respuesta = unknown;
interface Pedido {
  readonly metodo: 'GET' | 'POST';
  readonly ruta: string;
  readonly cuerpo?: unknown;
}

function httpFalso(respuestas: Record<string, Respuesta[] | Respuesta>) {
  const pedidos: Pedido[] = [];
  const responder = <T,>(metodo: 'GET' | 'POST', ruta: string, cuerpo?: unknown): Promise<T> => {
    pedidos.push({ metodo, ruta, ...(cuerpo === undefined ? {} : { cuerpo }) });
    const clave = Object.keys(respuestas)
      .filter((k) => `${metodo} ${ruta}`.startsWith(k))
      .sort((a, b) => b.length - a.length)[0];
    if (clave === undefined) return Promise.reject(new Error(`pedido inesperado: ${metodo} ${ruta}`));
    const entrada = respuestas[clave];
    const valor: unknown = Array.isArray(entrada) ? (entrada.length > 1 ? entrada.shift() : entrada[0]) : entrada;
    return valor instanceof ApiError ? Promise.reject(valor) : Promise.resolve(valor as T);
  };
  return {
    pedidos,
    http: {
      get: <T,>(ruta: string) => responder<T>('GET', ruta),
      post: <T,>(ruta: string, cuerpo?: unknown) => responder<T>('POST', ruta, cuerpo),
    },
  };
}

const esperar = async () => {
  for (let i = 0; i < 6; i += 1) await new Promise((r) => setTimeout(r, 0));
};
const texto = (raiz: ParentNode = document.body) => (raiz.textContent ?? '').replace(/\s+/g, ' ');
const botones = (raiz: ParentNode = document) => [...raiz.querySelectorAll<HTMLButtonElement>('button')];
const hay = (contiene: string) => botones().some((b) => (b.textContent ?? '').includes(contiene));
const boton = (contiene: string) => {
  const b = botones().find((x) => (x.textContent ?? '').replace(/\s+/g, ' ').includes(contiene));
  if (b === undefined) throw new Error(`No hay un botón con "${contiene}". Botones: ${botones().map((x) => x.textContent).join(' | ')}`);
  return b;
};
const tocar = async (contiene: string) => {
  boton(contiene).click();
  await esperar();
};
const dialogoAbierto = () => document.querySelector<HTMLElement>('[role="dialog"]');

let contenedor: HTMLElement;
let cabecera: HTMLElement;

async function montar(respuestas: Record<string, Respuesta[] | Respuesta>, extra: Partial<DependenciasGestion> = {}) {
  const falso = httpFalso(respuestas);
  await montarGestion(contenedor, { http: falso.http, token: TOKEN, cabecera, ahora: () => AHORA, ...extra });
  await esperar();
  return falso;
}

beforeEach(() => {
  document.body.innerHTML = '<header id="cab" hidden></header><main id="raiz"></main>';
  contenedor = document.querySelector<HTMLElement>('#raiz') as HTMLElement;
  cabecera = document.querySelector<HTMLElement>('#cab') as HTMLElement;
  localStorage.clear();
  sessionStorage.clear();
});
afterEach(() => {
  document.body.innerHTML = '';
});

describe('4.1 vista del turno por link', () => {
  it('muestra día, hora, prestación, estado, paciente, consultorio y el plazo, sin datos privados', async () => {
    const { pedidos } = await montar({ [`GET ${RUTA}`]: vista() });

    expect(pedidos).toEqual([{ metodo: 'GET', ruta: RUTA }]);
    expect(document.querySelector('h1')?.textContent).toBe('Tu turno');
    expect(texto(contenedor)).toContain('Martes 24 de noviembre, 10:30');
    expect(texto(contenedor)).toContain('Limpieza dental · 45 min · hasta las 11:15');
    expect(contenedor.querySelector('[data-estado="reservado"]')?.textContent).toContain('Reservado');
    expect(texto(contenedor)).toContain('"Reservado" quiere decir que el consultorio todavía no lo confirmó.');
    expect(texto(contenedor)).toContain('Ana Ejemplo');
    expect(texto(cabecera)).toContain('Consultorio Demo Sur');
    expect(cabecera.querySelector('a[href="tel:+5491100000001"]')).not.toBeNull();
    expect(texto(contenedor)).toContain('Podés cambiarlo o cancelarlo por acá hasta el lunes 23 a las 10:30 (24 horas antes).');
    expect(hay('Cambiar día u horario')).toBe(true);
    expect(hay('Cancelar turno')).toBe(true);
    const todo = texto();
    for (const privado of ['30000001', '30.000.001', '0000-0042', '5491100000042', 'ana@example.com', 'nota privada']) expect(todo).not.toContain(privado);
  });

  it('no guarda el token en el navegador', async () => {
    await montar({ [`GET ${RUTA}`]: vista() });
    expect(JSON.stringify({ ...localStorage }) + JSON.stringify({ ...sessionStorage }) + document.cookie).not.toContain(TOKEN);
  });
});

describe('4.2 acciones según el plazo y la reserva online', () => {
  it('fuera de plazo: sin cambiar ni cancelar, con el mensaje y el botón para llamar', async () => {
    await montar({ [`GET ${RUTA}`]: vista({ puedeModificar: false, puedeReprogramar: false, modificableHasta: '2026-11-19T16:00:00Z' }, { inicio: '2026-11-20T16:00:00Z', fin: '2026-11-20T16:45:00Z' }) });

    expect(hay('Cambiar día u horario')).toBe(false);
    expect(hay('Cancelar turno')).toBe(false);
    expect(texto(contenedor)).toContain('Ya no podés cambiarlo ni cancelarlo por acá.');
    expect(texto(contenedor)).toContain('Faltan menos de 24 horas para el turno.');
    expect(contenedor.querySelector('a[href="tel:+5491100000001"]')?.textContent).toContain('Llamar al 11 0000-0001');
  });

  it('reserva online desactivada: solo cancelar, con el teléfono para cambiarlo', async () => {
    await montar({ [`GET ${RUTA}`]: vista({ puedeReprogramar: false }) });

    expect(hay('Cambiar día u horario')).toBe(false);
    expect(hay('Cancelar turno')).toBe(true);
    expect(texto(contenedor)).toContain('El consultorio no está tomando turnos online.');
    expect(contenedor.querySelector('a[href="tel:+5491100000001"]')).not.toBeNull();
  });

  it('cancelado por el consultorio: lo dice, ofrece llamar y no tiene acciones', async () => {
    await montar({ [`GET ${RUTA}`]: vista({ puedeModificar: false, puedeReprogramar: false }, { estado: 'CANCELADO', canceladoPor: 'ODONTOLOGO' }) });

    expect(texto(contenedor)).toContain('El consultorio canceló este turno.');
    expect(contenedor.querySelector('[data-estado="cancelado"]')).not.toBeNull();
    expect(contenedor.querySelector('a[href="tel:+5491100000001"]')).not.toBeNull();
    expect(botones(contenedor)).toHaveLength(0);
  });

  it('cancelado por el paciente: "Cancelaste este turno"', async () => {
    await montar({ [`GET ${RUTA}`]: vista({ puedeModificar: false, puedeReprogramar: false }, { estado: 'CANCELADO', canceladoPor: 'PACIENTE' }) });
    expect(texto(contenedor)).toContain('Cancelaste este turno.');
  });
});

describe('4.3 reprogramación', () => {
  async function hastaElegir(respuestasPost: Respuesta[] | Respuesta = [], vistas: Respuesta[] | Respuesta = vista()) {
    const falso = await montar({ [`GET ${RUTA}/huecos`]: SEMANA, [`GET ${RUTA}`]: vistas, [`POST ${RUTA}/reprogramacion`]: respuestasPost });
    await tocar('Cambiar día u horario');
    await tocar('26');
    await tocar('15:00');
    return falso;
  }

  it('elige jueves 26 a las 15:00, confirma en el diálogo y envía ese instante como nuevoInicio', async () => {
    const nueva = vista({}, { inicio: '2026-11-26T18:00:00Z', fin: '2026-11-26T18:45:00Z' });
    const { pedidos } = await hastaElegir([nueva]);

    expect(pedidos.find((p) => p.ruta.startsWith(`${RUTA}/huecos`))?.ruta).toBe(`${RUTA}/huecos?desde=2026-11-20&hasta=2026-12-03`);
    expect(texto(contenedor)).toContain('Horarios en hora de Buenos Aires, la del consultorio.');
    await tocar('Mover al jueves 26, 15:00');
    expect(texto(dialogoAbierto() ?? document.body)).toContain('¿Mover tu turno al jueves 26 a las 15:00?');
    expect(texto(dialogoAbierto() ?? document.body)).toContain('Dejás libre el martes 24 a las 10:30.');
    await tocar('Sí, mover mi turno');

    expect(pedidos.find((p) => p.metodo === 'POST')).toEqual({ metodo: 'POST', ruta: `${RUTA}/reprogramacion`, cuerpo: { nuevoInicio: '2026-11-26T18:00:00Z' } });
    expect(document.querySelector('h1')?.textContent).toBe('Listo, cambiaste tu turno');
    expect(texto(contenedor)).toContain('Tu link sigue sirviendo para verlo, cambiarlo o cancelarlo.');
    expect(texto(contenedor)).toContain('Jueves 26 de noviembre, 15:00');
  });

  it('HUECO_NO_DISPONIBLE avisa que el turno sigue en su horario y tacha el elegido', async () => {
    await hastaElegir([new ApiError(409, 'HUECO_NO_DISPONIBLE', '', { huecos: ['2026-11-26T18:15:00Z'] })]);
    await tocar('Mover al');
    await tocar('Sí, mover mi turno');

    expect(texto(contenedor)).toContain('Ese horario se ocupó hace un instante.');
    expect(texto(contenedor)).toContain('Tu turno sigue el martes 24 a las 10:30.');
    expect(botones().find((b) => b.getAttribute('aria-label') === '15:00, ya no está disponible')?.disabled).toBe(true);
    expect(boton('Elegí un horario').disabled).toBe(true);
  });

  it('FUERA_DE_PLAZO lleva al mensaje de fuera de plazo con el teléfono que trae el error', async () => {
    await hastaElegir([new ApiError(409, 'FUERA_DE_PLAZO', '', { telefonoContacto: '+5491100000099' })]);
    await tocar('Mover al');
    await tocar('Sí, mover mi turno');

    expect(texto(contenedor)).toContain('Ya no podés cambiarlo ni cancelarlo por acá.');
    expect(contenedor.querySelector('a[href="tel:+5491100000099"]')).not.toBeNull();
    expect(hay('Cancelar turno')).toBe(false);
  });

  it('RESERVA_NO_DISPONIBLE deja solo cancelar', async () => {
    await hastaElegir([new ApiError(409, 'RESERVA_NO_DISPONIBLE', '', { telefonoContacto: '+5491100000001' })]);
    await tocar('Mover al');
    await tocar('Sí, mover mi turno');

    expect(texto(contenedor)).toContain('El consultorio no está tomando turnos online.');
    expect(hay('Cambiar día u horario')).toBe(false);
    expect(hay('Cancelar turno')).toBe(true);
  });

  it('CONFLICTO_CONCURRENCIA recarga el turno y avisa que cambió', async () => {
    const { pedidos } = await hastaElegir([new ApiError(409, 'CONFLICTO_CONCURRENCIA', '')], [vista(), vista({}, { estado: 'CONFIRMADO' })]);
    await tocar('Mover al');
    await tocar('Sí, mover mi turno');

    expect(pedidos.filter((p) => p.metodo === 'GET' && p.ruta === RUTA)).toHaveLength(2);
    expect(texto(contenedor)).toContain('Tu turno cambió mientras lo mirabas.');
    expect(contenedor.querySelector('[data-estado="confirmado"]')).not.toBeNull();
  });
});

describe('4.4 cancelación', () => {
  it('el diálogo atrapa el foco, Escape lo cierra y el foco vuelve a "Cancelar turno"', async () => {
    await montar({ [`GET ${RUTA}`]: vista() });
    const abrir = boton('Cancelar turno');
    abrir.focus();
    abrir.click();
    await esperar();

    const caja = dialogoAbierto();
    expect(caja).not.toBeNull();
    expect(texto(caja ?? document.body)).toContain('¿Cancelar tu turno del martes 24 a las 10:30?');
    expect(texto(caja ?? document.body)).toContain('No se puede deshacer.');
    expect(caja?.contains(document.activeElement)).toBe(true);
    const enDialogo = botones(caja ?? document);
    enDialogo.at(-1)?.focus();
    caja?.dispatchEvent(new KeyboardEvent('keydown', { key: 'Tab', bubbles: true }));
    expect(document.activeElement).toBe(enDialogo[0]);

    caja?.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
    expect(dialogoAbierto()).toBeNull();
    expect(document.activeElement).toBe(abrir);
  });

  it('al confirmar muestra "Cancelaste tu turno" con el turno tachado y sin acciones', async () => {
    const { pedidos } = await montar({
      [`GET ${RUTA}`]: vista(),
      [`POST ${RUTA}/cancelacion`]: vista({ puedeModificar: false, puedeReprogramar: false }, { estado: 'CANCELADO', canceladoPor: 'PACIENTE' }),
    });
    await tocar('Cancelar turno');
    await tocar('Sí, cancelar turno');

    expect(pedidos.at(-1)).toEqual({ metodo: 'POST', ruta: `${RUTA}/cancelacion` });
    expect(document.querySelector('h1')?.textContent).toBe('Cancelaste tu turno');
    expect(texto(contenedor)).toContain('Este link ya no funciona');
    expect(contenedor.querySelector('.line-through')?.textContent).toContain('Martes 24 de noviembre, 10:30');
    expect(botones(contenedor)).toHaveLength(0);
  });

  it('FUERA_DE_PLAZO al cancelar muestra el mensaje con ese teléfono y el turno sigue igual', async () => {
    await montar({ [`GET ${RUTA}`]: vista(), [`POST ${RUTA}/cancelacion`]: new ApiError(409, 'FUERA_DE_PLAZO', '', { telefonoContacto: '+5491100000099' }) });
    await tocar('Cancelar turno');
    await tocar('Sí, cancelar turno');

    expect(texto(contenedor)).toContain('Ya no podés cambiarlo ni cancelarlo por acá.');
    expect(contenedor.querySelector('a[href="tel:+5491100000099"]')).not.toBeNull();
    expect(contenedor.querySelector('[data-estado="reservado"]')).not.toBeNull();
  });
});

describe('4.5 link inválido', () => {
  it('un 404 al cargar muestra "Este link no funciona" sin datos del consultorio', async () => {
    await montar({ [`GET ${RUTA}`]: new ApiError(404, 'RECURSO_NO_ENCONTRADO', '') });

    expect(document.querySelector('h1')?.textContent).toBe('Este link no funciona');
    expect(cabecera.hidden).toBe(true);
    expect(texto()).not.toContain('Consultorio Demo Sur');
    expect(document.querySelector('a[href^="tel:"]')).toBeNull();
  });

  it('un 404 durante una acción pasa al mismo estado', async () => {
    await montar({ [`GET ${RUTA}`]: vista(), [`POST ${RUTA}/cancelacion`]: new ApiError(404, 'RECURSO_NO_ENCONTRADO', '') });
    await tocar('Cancelar turno');
    await tocar('Sí, cancelar turno');

    expect(document.querySelector('h1')?.textContent).toBe('Este link no funciona');
    expect(cabecera.hidden).toBe(true);
    expect(texto()).not.toContain('Consultorio Demo Sur');
  });
});
