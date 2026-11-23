import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { ApiError } from '../../shared/http';
import { sumarDias } from './dias';
import { montarReserva, type DependenciasReserva } from './pantalla';

// Datos ficticios (regla dura 17). El entorno corre con TZ=Europe/Madrid: nada puede usar la zona del dispositivo.
const AHORA = new Date('2026-11-20T12:00:00Z');
const LIMPIEZA = '00000000-0000-4000-8000-0000000000a1';
const CONSULTA = '00000000-0000-4000-8000-0000000000a2';

const consultorio = (extra: Record<string, unknown> = {}) => ({
  nombre: 'Consultorio Demo Sur',
  direccion: 'Av. Ejemplo 1234, CABA',
  telefono: '+5491100000001',
  zonaHoraria: 'America/Argentina/Buenos_Aires',
  tomaTurnosOnline: true,
  reservableHasta: '2026-12-19',
  prestaciones: [
    { id: LIMPIEZA, nombre: 'Limpieza dental', duracionMin: 45 },
    { id: CONSULTA, nombre: 'Consulta y control', duracionMin: 30 },
  ],
  ...extra,
});

type Huecos = Readonly<Record<string, readonly string[] | false>>;

/** Días de `desde` a `hasta`: `false` = no atiende; sin entrada = atiende sin huecos. */
function huecosEntre(desde: string, hasta: string, porDia: Huecos = {}, zonaHoraria = 'America/Argentina/Buenos_Aires') {
  const dias = [];
  for (let fecha = desde; fecha <= hasta; fecha = sumarDias(fecha, 1)) {
    const valor = porDia[fecha];
    dias.push({ fecha, atiende: valor !== false, huecos: valor === false || valor === undefined ? [] : valor });
  }
  return { zonaHoraria, dias };
}

const SEMANA: Huecos = {
  '2026-11-21': ['2026-11-21T12:00:00Z'],
  '2026-11-22': false,
  '2026-11-24': ['2026-11-24T12:00:00Z', '2026-11-24T13:30:00Z', '2026-11-24T18:00:00Z'],
  '2026-11-25': ['2026-11-25T15:00:00Z'],
};

type Respuesta = unknown | ApiError | (() => unknown);
interface Pedido {
  readonly metodo: 'GET' | 'POST';
  readonly ruta: string;
  readonly cuerpo?: unknown;
}

function httpFalso(respuestas: Record<string, Respuesta[] | Respuesta>) {
  const pedidos: Pedido[] = [];
  const responder = <T,>(metodo: 'GET' | 'POST', ruta: string, cuerpo?: unknown): Promise<T> => {
    pedidos.push({ metodo, ruta, ...(cuerpo === undefined ? {} : { cuerpo }) });
    // Gana la clave más larga que coincida (`GET …/demo/huecos` antes que `GET …/demo`).
    const clave = Object.keys(respuestas)
      .filter((k) => `${metodo} ${ruta}`.startsWith(k))
      .sort((a, b) => b.length - a.length)[0];
    if (clave === undefined) return Promise.reject(new Error(`pedido inesperado: ${metodo} ${ruta}`));
    const entrada = respuestas[clave];
    // Una lista responde en orden y repite la última.
    const valor: unknown = Array.isArray(entrada) ? (entrada.length > 1 ? entrada.shift() : entrada[0]) : entrada;
    const resuelto = typeof valor === 'function' ? (valor as () => unknown)() : valor;
    return resuelto instanceof ApiError ? Promise.reject(resuelto) : Promise.resolve(resuelto as T);
  };
  return {
    pedidos,
    http: {
      get: <T,>(ruta: string) => responder<T>('GET', ruta),
      post: <T,>(ruta: string, cuerpo?: unknown) => responder<T>('POST', ruta, cuerpo),
    },
  };
}

const RUTA = '/api/v1/public/consultorios/demo';
const RUTA_HUECOS = `GET ${RUTA}/huecos`;

const esperar = async () => {
  for (let i = 0; i < 5; i += 1) await new Promise((r) => setTimeout(r, 0));
};
const texto = (raiz: ParentNode = document.body) => (raiz.textContent ?? '').replace(/\s+/g, ' ');
const botones = () => [...document.querySelectorAll<HTMLButtonElement>('button')];
const boton = (contiene: string) => {
  const b = botones().find((x) => (x.textContent ?? '').replace(/\s+/g, ' ').includes(contiene));
  if (b === undefined) throw new Error(`No hay un botón con "${contiene}". Botones: ${botones().map((x) => x.textContent).join(' | ')}`);
  return b;
};
const tocar = async (contiene: string) => {
  boton(contiene).click();
  await esperar();
};
const campo = (etiqueta: string) => {
  const label = [...document.querySelectorAll('label')].find((l) => (l.textContent ?? '').trim().startsWith(etiqueta));
  const input = label === undefined ? null : document.getElementById(label.htmlFor);
  if (!(input instanceof HTMLInputElement)) throw new Error(`No hay un campo "${etiqueta}"`);
  return input;
};
const escribir = (etiqueta: string, valor: string) => {
  const input = campo(etiqueta);
  input.value = valor;
  input.dispatchEvent(new Event('input', { bubbles: true }));
};
const aceptar = () => {
  const casilla = document.querySelector<HTMLInputElement>('input[type="checkbox"]');
  if (casilla === null) throw new Error('sin casilla');
  casilla.checked = true;
  casilla.dispatchEvent(new Event('change', { bubbles: true }));
};
const enviar = async () => {
  document.querySelector('form')?.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
  await esperar();
};

let contenedor: HTMLElement;
let cabecera: HTMLElement;

async function montar(respuestas: Record<string, Respuesta[] | Respuesta>, extra: Partial<DependenciasReserva> = {}) {
  const falso = httpFalso(respuestas);
  await montarReserva(contenedor, { http: falso.http, slug: 'demo', cabecera, ahora: () => AHORA, ...extra });
  await esperar();
  return falso;
}

async function hastaLosDatos(respuestasPost: Respuesta[] | Respuesta = [], extra: Partial<DependenciasReserva> = {}) {
  const falso = await montar({ [`GET ${RUTA}/huecos`]: huecosEntre('2026-11-20', '2026-12-03', SEMANA), [`GET ${RUTA}`]: consultorio(), [`POST ${RUTA}/turnos`]: respuestasPost }, extra);
  await tocar('Limpieza dental');
  await tocar('24');
  await tocar('10:30');
  await tocar('Continuar con');
  return falso;
}

function completarDatos() {
  escribir('Nombre', 'Ana');
  escribir('Apellido', 'Ejemplo');
  escribir('DNI', '30.000.001');
  escribir('Celular', '11 0000-0042');
  escribir('Email', 'ana@example.com');
  aceptar();
}

const reservaCreada = {
  turno: { id: '7f1e2d3c-0000-4000-8000-000000000001', inicio: '2026-11-24T13:30:00Z', fin: '2026-11-24T14:15:00Z', prestacion: 'Limpieza dental', estado: 'RESERVADO' },
  linkGestion: '/t/ejemplo-ficticio-7Qm2yR8vLxA4',
};

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

describe('3.1 página pública del consultorio', () => {
  it('muestra nombre, dirección y teléfono del consultorio y las prestaciones con duración, sin precio', async () => {
    const { pedidos } = await montar({ [`GET ${RUTA}`]: consultorio({ email: 'odontologo@example.com', plan: 'PRUEBA' }) });

    expect(pedidos).toEqual([{ metodo: 'GET', ruta: RUTA }]);
    expect(cabecera.hidden).toBe(false);
    expect(texto(cabecera)).toContain('Consultorio Demo Sur');
    expect(texto(cabecera)).toContain('Av. Ejemplo 1234, CABA');
    expect(cabecera.querySelector('a[href="tel:+5491100000001"]')?.textContent).toContain('11 0000-0001');
    expect(boton('Limpieza dental').textContent).toContain('45 min');
    expect(boton('Limpieza dental').getAttribute('aria-pressed')).toBe('false');
    expect(texto()).not.toContain('$');
    expect(texto()).not.toContain('odontologo@example.com');
    expect(texto()).not.toContain('PRUEBA');
    expect(document.querySelectorAll('h1')).toHaveLength(1);
    expect(boton('Elegí qué necesitás').disabled).toBe(true);
  });

  it('un consultorio que no toma turnos online muestra el teléfono para llamar y ningún selector', async () => {
    await montar({ [`GET ${RUTA}`]: consultorio({ tomaTurnosOnline: false, prestaciones: [] }) });

    expect(texto(contenedor)).toContain('Por ahora este consultorio no toma turnos online');
    expect(contenedor.querySelector('a[href="tel:+5491100000001"]')?.textContent).toContain('Llamar al 11 0000-0001');
    expect(botones()).toHaveLength(0);
  });

  it('un slug inexistente muestra "No encontramos este consultorio" sin encabezado', async () => {
    await montar({ [`GET ${RUTA}`]: new ApiError(404, 'RECURSO_NO_ENCONTRADO', '') });

    expect(texto(contenedor)).toContain('No encontramos este consultorio');
    expect(cabecera.hidden).toBe(true);
    expect(document.querySelector('h1')?.textContent).toBe('No encontramos este consultorio');
  });

  it('un 429 al cargar dice cuánto esperar y permite reintentar', async () => {
    await montar({ [`GET ${RUTA}`]: [new ApiError(429, 'DEMASIADAS_SOLICITUDES', '', {}, '60')] });

    expect(texto(contenedor)).toContain('Hiciste muchas consultas seguidas.');
    expect(texto(contenedor)).toContain('Esperá un minuto y volvé a intentar.');
    expect(boton('Reintentar')).toBeDefined();
  });
});

describe('3.2 días por ventanas hasta el horizonte', () => {
  it('elegir la prestación pide los primeros 14 días: el sábado se elige y el domingo es "sin turnos"', async () => {
    const { pedidos } = await montar({ [RUTA_HUECOS]: huecosEntre('2026-11-20', '2026-12-03', SEMANA), [`GET ${RUTA}`]: consultorio() });

    await tocar('Limpieza dental');

    expect(boton('Limpieza dental').getAttribute('aria-pressed')).toBe('true');
    expect(pedidos.at(-1)?.ruta).toBe(`${RUTA}/huecos?prestacionId=${LIMPIEZA}&desde=2026-11-20&hasta=2026-12-03`);
    const sabado = boton('sáb');
    expect(sabado.disabled).toBe(false);
    const domingo = botones().find((b) => b.getAttribute('aria-label') === 'domingo 22, sin turnos');
    expect(domingo?.disabled).toBe(true);
    expect(domingo?.textContent).toContain('sin turnos');
    expect(texto()).toContain('noviembre');
    expect(boton('Ver más días')).toBeDefined();
  });

  it('dos semanas sin huecos: avisa y "Ver más días" trae los días siguientes con huecos', async () => {
    const { pedidos } = await montar({
      [RUTA_HUECOS]: [huecosEntre('2026-11-20', '2026-12-03'), huecosEntre('2026-12-04', '2026-12-17', { '2026-12-07': ['2026-12-07T12:00:00Z'] })],
      [`GET ${RUTA}`]: consultorio({ reservableHasta: '2027-01-18' }),
    });

    await tocar('Limpieza dental');
    expect(texto()).toContain('No hay turnos en estos días');
    await tocar('Ver más días');

    expect(pedidos.at(-1)?.ruta).toBe(`${RUTA}/huecos?prestacionId=${LIMPIEZA}&desde=2026-12-04&hasta=2026-12-17`);
    const lunes7 = botones().find((b) => (b.textContent ?? '').includes('lun') && (b.textContent ?? '').includes('7') && !b.disabled);
    expect(lunes7).toBeDefined();
    expect(texto()).not.toContain('No hay turnos en estos días');
  });

  it('al llegar al horizonte "Ver más días" desaparece', async () => {
    await montar({
      [RUTA_HUECOS]: [huecosEntre('2026-11-20', '2026-12-03', SEMANA), huecosEntre('2026-12-04', '2026-12-06')],
      [`GET ${RUTA}`]: consultorio({ reservableHasta: '2026-12-06' }),
    });

    await tocar('Limpieza dental');
    await tocar('Ver más días');

    expect(botones().some((b) => (b.textContent ?? '').includes('Ver más días'))).toBe(false);
  });
});

describe('3.3 horarios en la zona del consultorio', () => {
  it('Córdoba: el hueco de las 13:30Z se ve 10:30 con el aviso de la zona, y el botón resume la elección', async () => {
    await montar({
      [RUTA_HUECOS]: huecosEntre('2026-11-20', '2026-12-03', { '2026-11-24': ['2026-11-24T13:30:00Z'] }, 'America/Argentina/Cordoba'),
      [`GET ${RUTA}`]: consultorio({ zonaHoraria: 'America/Argentina/Cordoba' }),
    });

    await tocar('Limpieza dental');
    await tocar('24');

    expect(texto()).toContain('Mañana');
    expect(boton('10:30').getAttribute('aria-pressed')).toBe('false');
    expect(texto()).toContain('Horarios en hora de Córdoba, la del consultorio.');
    await tocar('10:30');
    expect(boton('10:30').getAttribute('aria-pressed')).toBe('true');
    expect(boton('Continuar con martes 24, 10:30').disabled).toBe(false);
  });
});

describe('3.4 paso de datos', () => {
  it('muestra el resumen del turno y la casilla de consentimiento sin marcar con el texto del anexo legal', async () => {
    await hastaLosDatos();

    expect(document.querySelector('h1')?.textContent).toBe('Completá tus datos');
    expect(texto(contenedor)).toContain('Martes 24 de noviembre, 10:30');
    expect(texto(contenedor)).toContain('Limpieza dental · 45 min · hasta las 11:15');
    const casilla = document.querySelector<HTMLInputElement>('input[type="checkbox"]');
    expect(casilla?.checked).toBe(false);
    const etiqueta = casilla === null ? null : document.querySelector(`label[for="${casilla.id}"]`);
    expect(texto(etiqueta ?? document.body)).toBe(
      'Acepto que Consultorio Demo Sur use mis datos (nombre, apellido, DNI, teléfono y, si lo dejo, mi email) para gestionar este turno, según la política de privacidad.',
    );
    expect(etiqueta?.querySelector('a')?.getAttribute('href')).toBe('/privacidad');
  });

  it('con DNI 12.34, celular de 3 dígitos y sin aceptar no envía nada y muestra el resumen enlazado', async () => {
    const { pedidos } = await hastaLosDatos();
    completarDatos();
    escribir('DNI', '12.34');
    escribir('Celular', '123');
    const casilla = document.querySelector<HTMLInputElement>('input[type="checkbox"]') as HTMLInputElement;
    casilla.checked = false;
    casilla.dispatchEvent(new Event('change', { bubbles: true }));

    await enviar();

    expect(pedidos.some((p) => p.metodo === 'POST')).toBe(false);
    const resumen = document.querySelector<HTMLElement>('[data-resumen-errores]');
    expect(resumen).not.toBeNull();
    expect(document.activeElement).toBe(resumen);
    const enlaces = [...(resumen?.querySelectorAll('a') ?? [])];
    expect(enlaces.map((a) => a.textContent)).toEqual(['El DNI tiene que tener 7 u 8 números.', 'Falta la característica en el celular.', 'Tenés que aceptar el uso de tus datos.']);
    expect(enlaces[0]?.getAttribute('href')).toBe(`#${campo('DNI').id}`);
    expect(campo('DNI').getAttribute('aria-invalid')).toBe('true');
    expect(campo('Celular').getAttribute('aria-invalid')).toBe('true');
    expect(texto(contenedor)).toContain('El DNI tiene que tener 7 u 8 números, por ejemplo 30.000.001.');
    expect(texto(contenedor)).toContain('Para reservar tenés que aceptarlo.');
  });

  it('envía el DNI solo con dígitos y el instante UTC tal como vino de la API', async () => {
    const { pedidos } = await hastaLosDatos([reservaCreada]);
    completarDatos();

    await enviar();

    const post = pedidos.find((p) => p.metodo === 'POST');
    expect(post?.ruta).toBe(`${RUTA}/turnos`);
    expect(post?.cuerpo).toEqual({
      prestacionId: LIMPIEZA,
      inicio: '2026-11-24T13:30:00Z',
      nombre: 'Ana',
      apellido: 'Ejemplo',
      dni: '30000001',
      telefono: '11 0000-0042',
      email: 'ana@example.com',
      aceptaPrivacidad: true,
    });
  });

  it('"Cambiar día u horario" vuelve a elegir conservando la elección', async () => {
    await hastaLosDatos();
    await tocar('Cambiar día u horario');

    expect(boton('Limpieza dental').getAttribute('aria-pressed')).toBe('true');
    expect(boton('10:30').getAttribute('aria-pressed')).toBe('true');
  });
});

describe('3.5 respuestas de la reserva', () => {
  it('HUECO_NO_DISPONIBLE vuelve a los horarios con las 10:30 tachadas y conserva los datos', async () => {
    await hastaLosDatos([new ApiError(409, 'HUECO_NO_DISPONIBLE', '', { huecos: ['2026-11-24T12:00:00Z', '2026-11-24T18:00:00Z'] })]);
    completarDatos();

    await enviar();

    expect(texto(contenedor)).toContain('Alguien reservó las 10:30 hace un instante.');
    expect(texto(contenedor)).toContain('Actualizamos los horarios: elegí otro.');
    const ocupado = botones().find((b) => b.getAttribute('aria-label') === '10:30, ya no está disponible');
    expect(ocupado?.disabled).toBe(true);
    expect(boton('Elegí un horario').disabled).toBe(true);
    await tocar('15:00');
    await tocar('Continuar con');
    expect(campo('Nombre').value).toBe('Ana');
    expect(campo('DNI').value).toBe('30.000.001');
    expect(document.querySelector<HTMLInputElement>('input[type="checkbox"]')?.checked).toBe(true);
  });

  it('si el día elegido se quedó sin horarios, ofrece ir al próximo día con horarios', async () => {
    await hastaLosDatos([new ApiError(409, 'HUECO_NO_DISPONIBLE', '', { huecos: [] })]);
    completarDatos();
    await enviar();

    expect(texto(contenedor)).toContain('No quedan horarios para Limpieza dental el martes 24.');
    await tocar('Ver el miércoles 25');
    expect(boton('12:00').getAttribute('aria-pressed')).toBe('false');
  });

  it('LIMITE_TURNOS avisa en el paso de datos con el teléfono y sin datos de esos turnos', async () => {
    await hastaLosDatos([new ApiError(409, 'LIMITE_TURNOS', '')]);
    completarDatos();
    await enviar();

    expect(texto(contenedor)).toContain('Ya tenés 2 turnos próximos en este consultorio.');
    expect(contenedor.querySelector('[role="alert"] a[href="tel:+5491100000001"]')).not.toBeNull();
    expect(boton('Reservar turno').disabled).toBe(true);
  });

  it('RESERVA_NO_DISPONIBLE pasa al estado "no toma turnos online"', async () => {
    await hastaLosDatos([new ApiError(409, 'RESERVA_NO_DISPONIBLE', '', { telefonoContacto: '+5491100000099' })]);
    completarDatos();
    await enviar();

    expect(texto(contenedor)).toContain('Por ahora este consultorio no toma turnos online');
    expect(contenedor.querySelector('a[href="tel:+5491100000099"]')).not.toBeNull();
  });

  it('DNI_INVALIDO marca el DNI y conserva el resto', async () => {
    await hastaLosDatos([new ApiError(400, 'DNI_INVALIDO', '')]);
    completarDatos();
    await enviar();

    expect(campo('DNI').getAttribute('aria-invalid')).toBe('true');
    expect(campo('Nombre').value).toBe('Ana');
    expect(campo('Celular').getAttribute('aria-invalid')).toBeNull();
  });

  it('un 429 al reservar dice cuánto esperar', async () => {
    await hastaLosDatos([new ApiError(429, 'DEMASIADAS_SOLICITUDES', '', {}, '60')]);
    completarDatos();
    await enviar();

    expect(texto(contenedor)).toContain('Esperá un minuto y volvé a intentar.');
    expect(boton('Reservar turno').disabled).toBe(false);
  });
});

describe('3.6 confirmación', () => {
  it('muestra el link una sola vez, lo copia, descarga el .ics y no guarda el token', async () => {
    const copiados: string[] = [];
    const descargas: { nombre: string; contenido: string }[] = [];
    await hastaLosDatos([reservaCreada], {
      copiar: (t) => {
        copiados.push(t);
        return Promise.resolve();
      },
      descargar: (nombre, contenido) => descargas.push({ nombre, contenido }),
    });
    completarDatos();
    await enviar();

    const link = `${location.origin}/t/ejemplo-ficticio-7Qm2yR8vLxA4`;
    expect(document.querySelector('h1')?.textContent).toBe('¡Listo, reservaste tu turno!');
    expect(texto(contenedor)).toContain('Guardá este link');
    expect(texto(contenedor)).toContain('No lo vamos a volver a mostrar.');
    expect(texto(contenedor)).toContain('El consultorio lo va a confirmar.');
    expect(texto(contenedor)).toContain(link);
    expect(texto(contenedor)).toContain('Martes 24 de noviembre, 10:30');

    await tocar('Copiar link');
    expect(copiados).toEqual([link]);
    expect(texto()).toContain('Copiamos el link.');

    await tocar('Agregar al calendario');
    expect(descargas).toHaveLength(1);
    expect(descargas[0]?.nombre).toBe('turno.ics');
    expect(descargas[0]?.contenido).toContain('DTSTART:20261124T133000Z');
    expect(descargas[0]?.contenido).toContain('DTEND:20261124T141500Z');
    expect(descargas[0]?.contenido.replace(/\r\n /g, '')).toContain(link);

    const guardado = JSON.stringify({ ...localStorage }) + JSON.stringify({ ...sessionStorage }) + document.cookie;
    expect(guardado).not.toContain('ejemplo-ficticio-7Qm2yR8vLxA4');
  });

  it('si no se puede copiar, avisa y deja el link seleccionable', async () => {
    await hastaLosDatos([reservaCreada], { copiar: () => Promise.reject(new Error('sin permiso')) });
    completarDatos();
    await enviar();

    await tocar('Copiar link');
    expect(texto()).toContain('No pudimos copiarlo: mantené apretado el link para copiarlo a mano.');
  });
});
