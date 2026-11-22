import { describe, expect, it } from 'vitest';
import { botonConTexto, crearContextoDePagina, enviar, escribir, esperar, json, problema, type Guion, type Llamada } from '../features/configuracion/prueba-contexto';
import { pagina } from './configuracion-horarios';

// Tarea 3.4 de C-09 (spec app-configuracion/disponibilidad; mockup `horarios.html`). Datos ficticios.

const franja = (diaSemana: number, horaInicio: string, horaFin: string) => ({ diaSemana, horaInicio, horaFin });
const LUNES = [franja(1, '09:00', '13:00'), franja(1, '15:00', '19:00')];

async function montar(guion: Guion) {
  const prueba = crearContextoDePagina(guion);
  pagina.montar(prueba.contexto);
  await esperar();
  const dia = (n: number) => prueba.contenedor.querySelector<HTMLElement>(`[data-dia="${n}"]`);
  const horas = (n: number) => [...(dia(n)?.querySelectorAll<HTMLInputElement>('input[type="time"]') ?? [])].map((i) => i.value);
  const barraVisible = () => prueba.contenedor.querySelector<HTMLElement>('[data-barra-guardado]')?.hidden === false;
  return { ...prueba, dia, horas, barraVisible };
}

const porEtiqueta = (raiz: ParentNode, etiqueta: string) => raiz.querySelector<HTMLButtonElement>(`button[aria-label="${etiqueta}"]`);
const puts = (llamadas: readonly Llamada[]) => llamadas.filter((l) => l.metodo === 'PUT');

describe('Configuración: horarios', () => {
  it('muestra los siete días con sus franjas y "No atendés" en los días vacíos', async () => {
    const { contenedor, dia, horas } = await montar({ 'GET /api/v1/disponibilidad': [json({ franjas: LUNES })] });

    expect([...contenedor.querySelectorAll('[data-dia] h3')].map((h) => h.firstChild?.textContent)).toEqual(['Lunes', 'Martes', 'Miércoles', 'Jueves', 'Viernes', 'Sábado', 'Domingo']);
    expect(horas(1)).toEqual(['09:00', '13:00', '15:00', '19:00']);
    expect(dia(2)?.textContent).toContain('No atendés');
    expect(contenedor.querySelector('nav [aria-current="page"]')?.textContent).toBe('Horarios');
  });

  it('agregar una franja suma horas editables en pasos de 5 minutos y marca cambios', async () => {
    const { dia, barraVisible } = await montar({ 'GET /api/v1/disponibilidad': [json({ franjas: LUNES })] });

    porEtiqueta(dia(2) ?? document, 'Agregar una franja al martes')?.click();

    const entradas = [...(dia(2)?.querySelectorAll<HTMLInputElement>('input[type="time"]') ?? [])];
    expect(entradas).toHaveLength(2);
    expect(entradas.every((i) => i.step === '300')).toBe(true);
    expect(entradas[0]?.getAttribute('aria-label')).toBe('Martes, franja 1: desde');
    expect(barraVisible()).toBe(true);
  });

  it('quitar una franja deja "Tenés cambios sin guardar"', async () => {
    const { contenedor, dia, barraVisible } = await montar({ 'GET /api/v1/disponibilidad': [json({ franjas: LUNES })] });
    expect(barraVisible()).toBe(false);

    porEtiqueta(dia(1) ?? document, 'Quitar la franja 2 del lunes')?.click();

    expect(barraVisible()).toBe(true);
    expect(contenedor.querySelector('[data-barra-guardado]')?.textContent).toContain('Tenés cambios sin guardar');
  });

  it('copia el lunes a los días elegidos, todavía sin guardar', async () => {
    const { dia, horas, llamadas } = await montar({ 'GET /api/v1/disponibilidad': [json({ franjas: LUNES })] });

    porEtiqueta(dia(1) ?? document, 'Copiar los horarios del lunes a otros días')?.click();
    const caja = document.querySelector<HTMLElement>('[role="dialog"]');
    expect(caja?.textContent).toContain('Copiar los horarios del lunes');
    for (const nombre of ['Martes', 'Miércoles', 'Jueves', 'Viernes']) {
      const casilla = [...(caja?.querySelectorAll('label') ?? [])].find((l) => l.textContent === nombre)?.control;
      if (casilla instanceof HTMLInputElement) casilla.checked = true;
    }
    botonConTexto(caja ?? document, 'Copiar')?.click();

    for (const n of [2, 3, 4, 5]) expect(horas(n), String(n)).toEqual(['09:00', '13:00', '15:00', '19:00']);
    expect(horas(6)).toEqual([]);
    expect(puts(llamadas)).toHaveLength(0);
  });

  it('franjas solapadas: no envía, marca el día con el par y lleva el foco al error', async () => {
    const { contenedor, dia, llamadas } = await montar({ 'GET /api/v1/disponibilidad': [json({ franjas: [franja(1, '09:00', '13:00'), franja(1, '15:00', '19:00')] })] });
    escribir(dia(1)?.querySelectorAll<HTMLInputElement>('input[type="time"]')[2] ?? null, '12:00');

    enviar(contenedor);
    await esperar();

    expect(puts(llamadas)).toHaveLength(0);
    expect(dia(1)?.textContent).toContain('Las franjas 09:00–13:00 y 12:00–19:00 se superponen');
    const resumen = contenedor.querySelector<HTMLElement>('[data-resumen-errores]');
    expect(document.activeElement).toBe(resumen);
    expect(resumen?.querySelector('a')?.textContent).toBe('Lunes');
  });

  it('franjas contiguas: guarda la semana completa y lo anuncia', async () => {
    const contiguas = [franja(1, '09:00', '13:00'), franja(1, '13:00', '15:00')];
    const { contenedor, dia, llamadas, barraVisible } = await montar({
      'GET /api/v1/disponibilidad': [json({ franjas: LUNES })],
      'PUT /api/v1/disponibilidad': [json({ franjas: [...contiguas, franja(5, '09:00', '13:00')] })],
    });
    const entradas = dia(1)?.querySelectorAll<HTMLInputElement>('input[type="time"]');
    escribir(entradas?.[2] ?? null, '13:00');
    escribir(entradas?.[3] ?? null, '15:00');
    porEtiqueta(dia(5) ?? document, 'Agregar una franja al viernes')?.click();

    enviar(contenedor);
    await esperar();

    expect(puts(llamadas)[0]?.cuerpo).toEqual({ franjas: [...contiguas, franja(5, '09:00', '13:00')] });
    expect(contenedor.querySelector('[aria-live="polite"]')?.textContent).toContain('Guardamos tus horarios');
    expect(barraVisible()).toBe(false);
  });

  it('solapamiento detectado por la API: marca los días que informa', async () => {
    const { contenedor, dia } = await montar({
      'GET /api/v1/disponibilidad': [json({ franjas: [franja(3, '09:00', '13:00')] })],
      'PUT /api/v1/disponibilidad': [problema(400, 'FRANJAS_SOLAPADAS', { dias: [3] })],
    });
    porEtiqueta(dia(1) ?? document, 'Agregar una franja al lunes')?.click();

    enviar(contenedor);
    await esperar();

    expect(dia(3)?.textContent).toContain('Encontramos franjas superpuestas en este día');
  });

  it('guardar la semana sin franjas pide confirmación antes de enviar', async () => {
    const { contenedor, dia, llamadas } = await montar({
      'GET /api/v1/disponibilidad': [json({ franjas: [franja(1, '09:00', '13:00')] })],
      'PUT /api/v1/disponibilidad': [json({ franjas: [] })],
    });
    porEtiqueta(dia(1) ?? document, 'Quitar la franja 1 del lunes')?.click();

    enviar(contenedor);
    await esperar();
    const confirmacion = document.querySelector<HTMLElement>('[role="dialog"]');
    expect(confirmacion?.textContent).toContain('¿Guardar la semana sin horarios?');
    expect(puts(llamadas)).toHaveLength(0);
    botonConTexto(confirmacion ?? document, 'Guardar sin horarios')?.click();
    await esperar();

    expect(puts(llamadas)[0]?.cuerpo).toEqual({ franjas: [] });
  });

  it('sin horarios ofrece el atajo de lunes a viernes, 9 a 13 y 15 a 19', async () => {
    const { contenedor, horas } = await montar({ 'GET /api/v1/disponibilidad': [json({ franjas: [] })] });

    botonConTexto(contenedor, 'Cargar lunes a viernes, 9 a 13 y 15 a 19')?.click();

    for (const n of [1, 2, 3, 4, 5]) expect(horas(n)).toEqual(['09:00', '13:00', '15:00', '19:00']);
    expect(horas(6)).toEqual([]);
  });
});
