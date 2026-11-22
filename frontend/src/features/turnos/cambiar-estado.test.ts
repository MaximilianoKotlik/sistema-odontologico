import { afterEach, describe, expect, it } from 'vitest';
import { escribir, esperar, json, problema } from '../configuracion/prueba-contexto';
import { abrirAusente, abrirCancelar, confirmarTurno } from './cambiar-estado';
import { avisos, botonEn, detalleApi, detalleDePrueba, dialogoAbierto, en, entornoDePrueba, textoDe } from './prueba-turno';

// Tarea 3.2 de C-14 (design D2, D5; spec gestion-turnos "Cambios de estado válidos"; mockup acciones-turno). Datos ficticios.
const RUTA = 'PATCH /api/v1/turnos/t-1/estado';

afterEach(() => {
  document.body.innerHTML = '';
});

describe('confirmarTurno', () => {
  it('confirma sin diálogo, avisa y recarga el detalle (D2)', async () => {
    const { entorno, llamadas, alTerminar } = entornoDePrueba({ [RUTA]: [json(detalleApi({ estado: 'CONFIRMADO' }))] });
    await confirmarTurno(detalleDePrueba(), entorno);
    expect(dialogoAbierto()).toBeNull();
    expect(llamadas[0]?.cuerpo).toEqual({ accion: 'CONFIRMAR', version: 3 });
    expect(avisos()).toContain('Turno confirmado: Federico Ejemplo, viernes 20 a las 16:00');
    expect(alTerminar).toHaveBeenCalledTimes(1);
  });

  it('ante TRANSICION_INVALIDA avisa que cambió y recarga sin perder nada', async () => {
    const { entorno, alTerminar } = entornoDePrueba({ [RUTA]: [problema(409, 'TRANSICION_INVALIDA')] });
    await confirmarTurno(detalleDePrueba(), entorno);
    expect(avisos().join(' ')).toContain('Este turno ya cambió de estado');
    expect(alTerminar).toHaveBeenCalledTimes(1);
  });
});

describe('abrirCancelar', () => {
  it('pide confirmación con el turno, el motivo opcional y el foco en "No, mantenerlo"', () => {
    const { entorno } = entornoDePrueba({});
    abrirCancelar(detalleDePrueba(), entorno);
    const caja = dialogoAbierto();
    expect(caja?.getAttribute('role')).toBe('alertdialog');
    expect(textoDe(caja?.querySelector('h2') ?? null)).toBe('¿Cancelar el turno de Federico Ejemplo?');
    expect(textoDe(caja)).toContain('Viernes 20 a las 16:00 · Consulta. El horario queda libre enseguida y el paciente ve el turno cancelado en su link.');
    expect(textoDe(caja)).toContain('Motivo (opcional, no lo ve el paciente)');
    expect(document.activeElement?.textContent).toContain('No, mantenerlo');
  });

  it('cancela con el motivo, cierra, avisa y recarga (escenario "Cancelar con motivo")', async () => {
    const { entorno, llamadas, alTerminar } = entornoDePrueba({ [RUTA]: [json(detalleApi({ estado: 'CANCELADO', canceladoPor: 'ODONTOLOGO', motivoCancelacion: 'Lo pidió por teléfono' }))] });
    abrirCancelar(detalleDePrueba(), entorno);
    escribir(dialogoAbierto()?.querySelector('input') ?? null, 'Lo pidió por teléfono');
    botonEn(dialogoAbierto(), 'Sí, cancelar turno')?.click();
    await esperar();
    expect(llamadas[0]?.cuerpo).toEqual({ accion: 'CANCELAR', version: 3, motivo: 'Lo pidió por teléfono' });
    expect(dialogoAbierto()).toBeNull();
    expect(avisos()).toContain('Cancelamos el turno de Federico Ejemplo');
    expect(alTerminar).toHaveBeenCalledTimes(1);
  });

  it('un motivo de más de 200 caracteres no se envía', () => {
    const { entorno, llamadas } = entornoDePrueba({});
    abrirCancelar(detalleDePrueba(), entorno);
    escribir(dialogoAbierto()?.querySelector('input') ?? null, 'a'.repeat(201));
    botonEn(dialogoAbierto(), 'Sí, cancelar turno')?.click();
    expect(llamadas).toHaveLength(0);
    expect(textoDe(dialogoAbierto())).toContain('El motivo puede tener hasta 200 caracteres.');
  });

  it('ante CONFLICTO_CONCURRENCIA no cancela y ofrece ver el turno actualizado', async () => {
    const { entorno, alTerminar } = entornoDePrueba({ [RUTA]: [problema(409, 'CONFLICTO_CONCURRENCIA')] });
    abrirCancelar(detalleDePrueba(), entorno);
    botonEn(dialogoAbierto(), 'Sí, cancelar turno')?.click();
    await esperar();
    expect(textoDe(dialogoAbierto()?.querySelector('[role="alert"]') ?? null)).toContain('El turno cambió mientras lo editabas');
    botonEn(dialogoAbierto(), 'Ver el turno actualizado')?.click();
    expect(dialogoAbierto()).toBeNull();
    expect(alTerminar).toHaveBeenCalledTimes(1);
  });

  it('sin conexión el diálogo sigue abierto con "Reintentar"', async () => {
    const { entorno, llamadas } = entornoDePrueba({ [RUTA]: [() => Promise.reject(new TypeError('Failed to fetch')), json(detalleApi({ estado: 'CANCELADO' }))] });
    abrirCancelar(detalleDePrueba(), entorno);
    botonEn(dialogoAbierto(), 'Sí, cancelar turno')?.click();
    await esperar();
    expect(textoDe(dialogoAbierto())).toContain('No hay conexión');
    botonEn(dialogoAbierto(), 'Reintentar')?.click();
    await esperar();
    expect(llamadas).toHaveLength(2);
    expect(dialogoAbierto()).toBeNull();
  });
});

describe('abrirAusente', () => {
  it('pide confirmación y marca ausente', async () => {
    const turno = detalleDePrueba({ estado: 'CONFIRMADO', inicio: en('2026-11-20', '09:30'), fin: en('2026-11-20', '10:15'), prestacion: 'Limpieza', paciente: { nombre: 'Bruno', apellido: 'Prueba', dni: null, telefono: null, email: null } });
    const { entorno, llamadas, alTerminar } = entornoDePrueba({ [RUTA]: [json(detalleApi({ estado: 'AUSENTE' }))] });
    abrirAusente(turno, entorno);
    expect(textoDe(dialogoAbierto()?.querySelector('h2') ?? null)).toBe('¿Marcar a Bruno Prueba como ausente?');
    expect(textoDe(dialogoAbierto())).toContain('Viernes 20 a las 09:30 · Limpieza. El turno queda como Ausente y ya no se puede cambiar de estado.');
    botonEn(dialogoAbierto(), 'Marcar ausente')?.click();
    await esperar();
    expect(llamadas[0]?.cuerpo).toEqual({ accion: 'MARCAR_AUSENTE', version: 3 });
    expect(avisos()).toContain('Marcamos a Bruno Prueba como ausente');
    expect(alTerminar).toHaveBeenCalledTimes(1);
  });
});
