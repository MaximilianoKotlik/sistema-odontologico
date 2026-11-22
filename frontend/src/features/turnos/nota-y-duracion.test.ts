import { afterEach, describe, expect, it } from 'vitest';
import { escribir, esperar, json, problema } from '../configuracion/prueba-contexto';
import { abrirDuracion } from './duracion';
import { abrirNota } from './nota';
import { BA, avisos, botonEn, detalleApi, detalleDePrueba, dialogoAbierto, en, entornoDePrueba, textoDe } from './prueba-turno';

// Tareas 3.4 y 3.5 de C-14 (design D7, D9; spec "Ajustar duración" y "Nota interna"; mockup acciones-turno). Datos ficticios.

afterEach(() => {
  document.body.innerHTML = '';
});

describe('abrirNota', () => {
  const RUTA = 'PATCH /api/v1/turnos/t-1/nota';

  it('abre con la nota actual, el contador y el recordatorio de que el paciente no la ve', () => {
    const { entorno } = entornoDePrueba({});
    abrirNota(detalleDePrueba({ notaInterna: 'Trae radiografía' }), entorno);
    const caja = dialogoAbierto();
    expect(textoDe(caja?.querySelector('h2') ?? null)).toBe('Nota interna');
    expect(caja?.querySelector('textarea')?.value).toBe('Trae radiografía');
    expect(textoDe(caja)).toContain('16 / 500');
    expect(textoDe(caja)).toContain('El paciente no la ve.');
    expect(textoDe(caja?.querySelector('[data-referencia]') ?? null)).toBe('Federico Ejemplo · Consulta Viernes 20 de noviembre · 16:00 a 16:30');
  });

  it('con 501 caracteres marca "1 carácter de más" y "Guardar nota" no envía (escenario "Nota demasiado larga")', () => {
    const { entorno, llamadas } = entornoDePrueba({});
    abrirNota(detalleDePrueba(), entorno);
    escribir(dialogoAbierto()?.querySelector('textarea') as unknown as HTMLInputElement, 'a'.repeat(501));
    expect(textoDe(dialogoAbierto())).toContain('501 / 500');
    expect(textoDe(dialogoAbierto())).toContain('1 carácter de más');
    const guardar = botonEn(dialogoAbierto(), 'Guardar nota');
    expect(guardar?.disabled).toBe(true);
    guardar?.click();
    expect(llamadas).toHaveLength(0);
  });

  it('guarda con la versión leída, cierra, avisa y recarga', async () => {
    const { entorno, llamadas, alTerminar } = entornoDePrueba({ [RUTA]: [json(detalleApi({ tieneNota: true, notaInterna: 'Alergia a la penicilina' }))] });
    abrirNota(detalleDePrueba(), entorno);
    escribir(dialogoAbierto()?.querySelector('textarea') as unknown as HTMLInputElement, 'Alergia a la penicilina');
    botonEn(dialogoAbierto(), 'Guardar nota')?.click();
    await esperar();
    expect(llamadas[0]?.cuerpo).toEqual({ nota: 'Alergia a la penicilina', version: 3 });
    expect(dialogoAbierto()).toBeNull();
    expect(avisos()).toContain('Guardamos la nota');
    expect(alTerminar).toHaveBeenCalledTimes(1);
  });

  it('ante CONFLICTO_CONCURRENCIA no pisa el cambio y ofrece la versión actual', async () => {
    const { entorno, alTerminar } = entornoDePrueba({ [RUTA]: [problema(409, 'CONFLICTO_CONCURRENCIA')] });
    abrirNota(detalleDePrueba(), entorno);
    escribir(dialogoAbierto()?.querySelector('textarea') as unknown as HTMLInputElement, 'Otra cosa');
    botonEn(dialogoAbierto(), 'Guardar nota')?.click();
    await esperar();
    expect(textoDe(dialogoAbierto())).toContain('El turno cambió mientras lo editabas');
    botonEn(dialogoAbierto(), 'Ver el turno actualizado')?.click();
    expect(dialogoAbierto()).toBeNull();
    expect(alTerminar).toHaveBeenCalledTimes(1);
  });
});

describe('abrirDuracion', () => {
  const RUTA = 'PATCH /api/v1/turnos/t-1/duracion';
  const turno = detalleDePrueba({ inicio: en('2026-11-20', '10:00'), fin: en('2026-11-20', '10:30') });

  it('+5 tres veces muestra "45 min · termina 10:45" (escenario "Alargar a 45 minutos")', () => {
    const { entorno } = entornoDePrueba({});
    abrirDuracion(turno, entorno);
    const mas = dialogoAbierto()?.querySelector<HTMLButtonElement>('button[aria-label="Sumar 5 minutos"]');
    mas?.click();
    mas?.click();
    mas?.click();
    expect(textoDe(dialogoAbierto()?.querySelector('output') ?? null)).toBe('45 min Termina 10:45');
    expect(dialogoAbierto()?.querySelector('[aria-pressed="true"]')?.textContent).toBe('45');
    expect(textoDe(dialogoAbierto())).toContain('Antes: 30 min (10:00 a 10:30). En pasos de 5 minutos, de 5 a 480.');
  });

  it('los atajos eligen la duración y −5 se deshabilita en el mínimo', () => {
    const { entorno } = entornoDePrueba({});
    abrirDuracion(detalleDePrueba({ duracionMin: 10, inicio: en('2026-11-20', '10:00'), fin: en('2026-11-20', '10:10') }), entorno);
    const menos = dialogoAbierto()?.querySelector<HTMLButtonElement>('button[aria-label="Restar 5 minutos"]');
    menos?.click();
    expect(menos?.disabled).toBe(true);
    botonEn(dialogoAbierto(), '60')?.click();
    expect(textoDe(dialogoAbierto()?.querySelector('output') ?? null)).toBe('60 min Termina 11:00');
  });

  it('guarda la duración nueva con la versión leída', async () => {
    const { entorno, llamadas, alTerminar } = entornoDePrueba({ [RUTA]: [json(detalleApi({ duracionMin: 45 }))] });
    abrirDuracion(turno, entorno);
    botonEn(dialogoAbierto(), '45')?.click();
    botonEn(dialogoAbierto(), 'Guardar duración')?.click();
    await esperar();
    expect(llamadas[0]?.cuerpo).toEqual({ duracionMin: 45, version: 3 });
    expect(avisos()).toContain('Ahora el turno dura 45 min y termina a las 10:45');
    expect(alTerminar).toHaveBeenCalledTimes(1);
  });

  it('ante TURNO_SOLAPADO mantiene la duración y nombra el turno que empieza (con la agenda del día)', async () => {
    const siguiente = { ...detalleApi({ id: 't-2', inicio: en('2026-11-20', '10:30'), fin: en('2026-11-20', '11:00') }), paciente: { id: 'pa-2', nombre: 'Gabriela', apellido: 'Prueba' } };
    const desde = encodeURIComponent(en('2026-11-20', '00:00'));
    const hasta = encodeURIComponent(en('2026-11-21', '00:00'));
    const { entorno, alTerminar } = entornoDePrueba({
      [RUTA]: [problema(409, 'TURNO_SOLAPADO', { turnosEnConflicto: ['t-2'], turnoEnConflicto: { id: 't-2', inicio: en('2026-11-20', '10:30'), fin: en('2026-11-20', '11:00') } })],
      [`GET /api/v1/agenda?desde=${desde}&hasta=${hasta}`]: [json({ turnos: [siguiente], bloqueos: [] })],
      'GET /api/v1/disponibilidad': [json({ franjas: [] })],
    });
    abrirDuracion(turno, entorno);
    botonEn(dialogoAbierto(), '45')?.click();
    botonEn(dialogoAbierto(), 'Guardar duración')?.click();
    await esperar();
    await esperar();
    const alerta = textoDe(dialogoAbierto()?.querySelector('[role="alert"]') ?? null);
    expect(alerta).toContain('No alcanza: a las 10:30 empieza el turno de Gabriela Prueba');
    expect(alerta).toContain('Con 45 min terminaría a las 10:45. La duración sigue en 30 min.');
    expect(alTerminar).not.toHaveBeenCalled();
    expect(BA).toBeTruthy();
  });
});
