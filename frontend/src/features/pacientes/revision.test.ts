import { describe, expect, it, vi } from 'vitest';
import { botonConTexto, crearContextoDePagina, enviar, escribir, esperar, json, problema, type Guion } from '../configuracion/prueba-contexto';
import { tomarAvisoDeRevision } from './aviso-de-revision';
import type { ParaRevisar } from './para-revisar';
import { montarRevisionDePaciente } from './revision';

// C-17, tareas 4.3 a 4.5 (spec pantalla-pacientes, design D2, D7 y D9; mockup aprobado #36). Datos ficticios.

const INFORMADO_AT = '2026-11-18T13:00:00.123456Z';
const ANA = {
  id: 'p-1', nombre: 'Ana', apellido: 'Ejemplo', dni: '90000012', telefono: '+5491100000012', email: 'ana.ejemplo@example.com', requiereRevision: true,
  informado: { nombre: 'Anna', apellido: 'Ejemplo', email: 'ana.ejemplo@example.com', informadoAt: INFORMADO_AT, turno: { id: 't-1', inicio: '2026-11-23T13:30:00Z', prestacion: 'Consulta' } },
};
const REVISADA = { ...ANA, requiereRevision: false, informado: null };
const DETALLE = 'GET /api/v1/pacientes/p-1';
const CONFIRMAR = 'POST /api/v1/pacientes/p-1/confirmar-revision';
const CORREGIR = 'PATCH /api/v1/pacientes/p-1';

async function montar(guion: Guion) {
  const preparado = crearContextoDePagina(guion);
  const paraRevisar: ParaRevisar = { cantidad: () => 3, actualizar: vi.fn(async () => undefined), suscribir: () => () => undefined, olvidar: () => undefined };
  const contexto = { ...preparado.contexto, params: { id: 'p-1' }, pacientes: paraRevisar };
  montarRevisionDePaciente(contexto);
  await esperar();
  await esperar();
  return { ...preparado, contexto, paraRevisar };
}

const textoDe = (e: Element | null | undefined) => (e?.textContent ?? '').replace(/\s+/g, ' ').trim();
const filas = (raiz: ParentNode) => [...raiz.querySelectorAll('tbody tr')].map((tr) => [...tr.children].map((c) => textoDe(c)));
const enlaceConTexto = (raiz: ParentNode, texto: string) => [...raiz.querySelectorAll<HTMLAnchorElement>('a')].find((a) => textoDe(a) === texto) ?? null;
const accion = async (b: HTMLButtonElement | null) => {
  b?.click();
  await esperar();
  await esperar();
  await esperar();
};

describe('comparación con lo escrito en la reserva (4.3)', () => {
  it('solo difiere el nombre: una fila con "Distinto" y la línea de lo que coincide', async () => {
    const { contenedor } = await montar({ [DETALLE]: [json(ANA)] });

    expect(filas(contenedor)).toEqual([['Nombre', 'Ana', 'AnnaDistinto']]);
    expect(contenedor.querySelector('td[data-reserva] [data-distinto] svg')).not.toBeNull();
    expect(textoDe(contenedor.querySelector('[data-coinciden]'))).toBe('Coinciden el apellido, el email y el teléfono.');
    expect(textoDe(contenedor)).toContain('No cambiamos nada');
    const turno = enlaceConTexto(contenedor, 'lunes 23 de noviembre, 10:30 · Consulta');
    expect(turno?.getAttribute('href')).toBe('/app/agenda/turnos/t-1');
    expect(contenedor.querySelectorAll('h2')).not.toHaveLength(0);
  });

  it('una diferencia solo de mayúsculas, tildes o espacios no se marca', async () => {
    const informado = { ...ANA.informado, nombre: ' ÁNA ', apellido: 'ejemplo', email: 'otra@example.com' };
    const { contenedor } = await montar({ [DETALLE]: [json({ ...ANA, informado })] });

    expect(filas(contenedor)).toEqual([['Email', 'ana.ejemplo@example.com', 'otra@example.comDistinto']]);
    expect(textoDe(contenedor.querySelector('[data-coinciden]'))).toBe('Coinciden el nombre, el apellido y el teléfono.');
  });
});

describe('tres salidas sin confirmación (4.4)', () => {
  it('"Usar «Anna Ejemplo»" corrige con lo de la reserva y vuelve a la lista con el aviso', async () => {
    const { contenedor, llamadas, contexto, paraRevisar } = await montar({ [DETALLE]: [json(ANA)], [CORREGIR]: [json({ ...REVISADA, nombre: 'Anna' })] });

    await accion(botonConTexto(contenedor, 'Usar «Anna Ejemplo»'));

    expect(llamadas.at(-1)?.cuerpo).toEqual({ informadoAt: INFORMADO_AT, nombre: 'Anna', apellido: 'Ejemplo', email: 'ana.ejemplo@example.com' });
    expect(paraRevisar.actualizar).toHaveBeenCalled();
    expect(contexto.navegacion.navegar).toHaveBeenCalledWith('/app/pacientes?requiereRevision=true');
    expect(tomarAvisoDeRevision()).toBe('Listo: la ficha de Ejemplo, Anna quedó revisada.');
  });

  it('"Mantener «Ana Ejemplo»" confirma sin cambiar la ficha', async () => {
    const { contenedor, llamadas, contexto } = await montar({ [DETALLE]: [json(ANA)], [CONFIRMAR]: [json(REVISADA)] });

    await accion(botonConTexto(contenedor, 'Mantener «Ana Ejemplo»'));

    expect(llamadas.at(-1)).toMatchObject({ metodo: 'POST', cuerpo: { informadoAt: INFORMADO_AT } });
    expect(contexto.navegacion.navegar).toHaveBeenCalledWith('/app/pacientes?requiereRevision=true');
    expect(tomarAvisoDeRevision()).toBe('Listo: la ficha de Ejemplo, Ana quedó revisada.');
  });

  it('un 409 SIN_REVISION_PENDIENTE recarga y lo muestra revisado, sin las tres acciones', async () => {
    const { contenedor, contexto } = await montar({ [DETALLE]: [json(ANA), json(REVISADA)], [CONFIRMAR]: [problema(409, 'SIN_REVISION_PENDIENTE')] });

    await accion(botonConTexto(contenedor, 'Mantener «Ana Ejemplo»'));

    expect(textoDe(contenedor)).toContain('Datos revisados');
    expect(textoDe(contenedor)).toContain('ya no tiene una revisión pendiente');
    expect(botonConTexto(contenedor, 'Mantener «Ana Ejemplo»')).toBeNull();
    expect(contexto.navegacion.navegar).not.toHaveBeenCalled();
  });

  it('un 409 CONFLICTO_CONCURRENCIA recarga con lo nuevo y avisa que llegó otra reserva', async () => {
    const nuevo = { ...ANA, informado: { ...ANA.informado, nombre: 'Ana María', informadoAt: '2026-11-22T12:00:00Z' } };
    const { contenedor, llamadas } = await montar({ [DETALLE]: [json(ANA), json(nuevo)], [CORREGIR]: [problema(409, 'CONFLICTO_CONCURRENCIA')], [CONFIRMAR]: [json(REVISADA)] });

    await accion(botonConTexto(contenedor, 'Usar «Anna Ejemplo»'));

    expect(textoDe(contenedor.querySelector('[data-aviso-otra-reserva]'))).toContain('Llegó otra reserva');
    expect(botonConTexto(contenedor, 'Usar «Ana María Ejemplo»')).not.toBeNull();
    await accion(botonConTexto(contenedor, 'Mantener «Ana Ejemplo»'));
    expect(llamadas.at(-1)?.cuerpo).toEqual({ informadoAt: '2026-11-22T12:00:00Z' });
  });

  it('si guardar falla por otro motivo avisa que la ficha no cambió', async () => {
    const { contenedor } = await montar({ [DETALLE]: [json(ANA)], [CONFIRMAR]: [problema(500, 'ERROR_INTERNO')] });

    await accion(botonConTexto(contenedor, 'Mantener «Ana Ejemplo»'));

    const alerta = contenedor.querySelector('[role="alert"]');
    expect(textoDe(alerta)).toContain('No pudimos guardar la revisión.');
    expect(textoDe(alerta)).toContain('Tu ficha no cambió.');
    expect(botonConTexto(contenedor, 'Mantener «Ana Ejemplo»')).not.toBeNull();
  });
});

describe('corrección a mano (4.5)', () => {
  const abrirFormulario = async (guion: Guion) => {
    const montado = await montar(guion);
    await accion(botonConTexto(montado.contenedor, 'Corregir a mano'));
    return montado;
  };
  const campo = (raiz: ParentNode, etiqueta: string) => {
    const label = [...raiz.querySelectorAll('label')].find((l) => textoDe(l).startsWith(etiqueta));
    return raiz.querySelector<HTMLInputElement>(`#${label?.getAttribute('for') ?? 'nada'}`);
  };

  it('precarga nombre, apellido y email, y muestra el DNI y el teléfono de solo lectura con su motivo', async () => {
    const { contenedor } = await abrirFormulario({ [DETALLE]: [json(ANA)] });

    expect(campo(contenedor, 'Nombre')?.value).toBe('Ana');
    expect(campo(contenedor, 'Apellido')?.value).toBe('Ejemplo');
    expect(campo(contenedor, 'Email')?.value).toBe('ana.ejemplo@example.com');
    expect(textoDe(contenedor)).toContain('90.000.012');
    expect(textoDe(contenedor)).toContain('Identifica al paciente y no se edita.');
    expect(textoDe(contenedor)).toContain('+54 9 11 0000-0012');
    expect(textoDe(contenedor)).toContain('No se edita en esta versión.');
  });

  it('valida antes de enviar: "Revisá 2 datos" con links a Nombre y Email y el error debajo de cada uno', async () => {
    const { contenedor, llamadas } = await abrirFormulario({ [DETALLE]: [json(ANA)] });
    const antes = llamadas.length;

    escribir(campo(contenedor, 'Nombre'), ' ');
    escribir(campo(contenedor, 'Email'), 'ana.ejemplo@example');
    enviar(contenedor);
    await esperar();

    expect(llamadas).toHaveLength(antes);
    const resumen = contenedor.querySelector('[data-resumen-errores]');
    expect(textoDe(resumen)).toContain('Revisá 2 datos');
    const nombre = campo(contenedor, 'Nombre');
    const email = campo(contenedor, 'Email');
    expect([...(resumen?.querySelectorAll('a') ?? [])].map((a) => a.getAttribute('href'))).toEqual([`#${nombre?.id ?? ''}`, `#${email?.id ?? ''}`]);
    expect(nombre?.getAttribute('aria-invalid')).toBe('true');
    expect(email?.getAttribute('aria-invalid')).toBe('true');
    expect(textoDe(contenedor)).toContain('Escribí el nombre.');
    expect(textoDe(contenedor)).toContain('Revisá el email: tiene que ser como nombre@dominio.com.');
  });

  it('guarda lo escrito con el informadoAt y vuelve a la lista', async () => {
    const { contenedor, llamadas, contexto } = await abrirFormulario({ [DETALLE]: [json(ANA)], [CORREGIR]: [json({ ...REVISADA, nombre: 'Anabel' })] });

    escribir(campo(contenedor, 'Nombre'), 'Anabel');
    escribir(campo(contenedor, 'Email'), '');
    enviar(contenedor);
    await esperar();
    await esperar();
    await esperar();

    expect(llamadas.at(-1)?.cuerpo).toEqual({ informadoAt: INFORMADO_AT, nombre: 'Anabel', apellido: 'Ejemplo' });
    expect(contexto.navegacion.navegar).toHaveBeenCalledWith('/app/pacientes?requiereRevision=true');
    tomarAvisoDeRevision();
  });

  it('con otra reserva en el medio recarga sin perder lo escrito', async () => {
    const nuevo = { ...ANA, informado: { ...ANA.informado, nombre: 'Ana María', informadoAt: '2026-11-22T12:00:00Z' } };
    const { contenedor } = await abrirFormulario({ [DETALLE]: [json(ANA), json(nuevo)], [CORREGIR]: [problema(409, 'CONFLICTO_CONCURRENCIA')] });

    escribir(campo(contenedor, 'Nombre'), 'Anabel');
    enviar(contenedor);
    await esperar();
    await esperar();
    await esperar();

    expect(campo(contenedor, 'Nombre')?.value).toBe('Anabel');
    expect(textoDe(contenedor.querySelector('[data-aviso-otra-reserva]'))).toContain('Llegó otra reserva');
    expect(textoDe(contenedor)).toContain('En la reserva escribió «Ana María»');
  });

  it('si guardar falla no pierde lo escrito', async () => {
    const { contenedor } = await abrirFormulario({ [DETALLE]: [json(ANA)], [CORREGIR]: [problema(500, 'ERROR_INTERNO')] });

    escribir(campo(contenedor, 'Nombre'), 'Anabel');
    enviar(contenedor);
    await esperar();
    await esperar();

    expect(campo(contenedor, 'Nombre')?.value).toBe('Anabel');
    expect(textoDe(contenedor.querySelector('[role="alert"]'))).toContain('Tu ficha no cambió.');
  });
});

describe('estados de la revisión', () => {
  it('404: "No encontramos este paciente" con "Ir a pacientes"', async () => {
    const { contenedor } = await montar({ [DETALLE]: [problema(404, 'RECURSO_NO_ENCONTRADO')] });
    expect(textoDe(contenedor.querySelector('h2'))).toBe('No encontramos este paciente');
    expect(enlaceConTexto(contenedor, 'Ir a pacientes')?.getAttribute('href')).toBe('/app/pacientes');
  });

  it('un paciente sin revisión pendiente muestra sus datos sin acciones', async () => {
    const { contenedor } = await montar({ [DETALLE]: [json(REVISADA)] });
    expect(textoDe(contenedor)).toContain('Datos del paciente');
    expect(textoDe(contenedor)).toContain('Datos revisados');
    expect(botonConTexto(contenedor, 'Corregir a mano')).toBeNull();
  });

  it('sin conexión no deja revisar', async () => {
    const sinRed = () => {
      throw new TypeError('Failed to fetch');
    };
    const { contenedor } = await montar({ [DETALLE]: [sinRed] });
    expect(textoDe(contenedor)).toContain('No podés revisar pacientes hasta que vuelva la conexión.');
  });

  it('las acciones de escritura se deshabilitan sin conexión (data-requiere-conexion)', async () => {
    const { contenedor } = await montar({ [DETALLE]: [json(ANA)] });
    for (const texto of ['Mantener «Ana Ejemplo»', 'Usar «Anna Ejemplo»', 'Corregir a mano']) {
      expect(botonConTexto(contenedor, texto)?.hasAttribute('data-requiere-conexion')).toBe(true);
    }
  });
});
