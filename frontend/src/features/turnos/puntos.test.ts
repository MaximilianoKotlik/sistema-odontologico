import { afterEach, describe, expect, it } from 'vitest';
import { puntosDeEntrada, restablecerPuntosDeEntrada } from '../agenda/puntos-de-entrada';
import { json } from '../configuracion/prueba-contexto';
import { registrarAccionesDeTurnos } from './puntos';
import { detalleDePrueba, dialogoAbierto, entornoDePrueba, textoDe } from './prueba-turno';

// C-14 reemplaza los avisos provisorios de C-13 con sus diálogos (design D1; puntos de entrada de C-13).

afterEach(() => {
  document.body.innerHTML = '';
  restablecerPuntosDeEntrada();
});

describe('registrarAccionesDeTurnos', () => {
  it.each([
    ['CANCELAR', '¿Cancelar el turno de Federico Ejemplo?'],
    ['MARCAR_AUSENTE', '¿Marcar a Federico Ejemplo como ausente?'],
    ['REPROGRAMAR', 'Reprogramar turno'],
    ['AJUSTAR_DURACION', 'Ajustar duración'],
    ['EDITAR_NOTA', 'Nota interna'],
  ] as const)('%s abre su diálogo', (accion, titulo) => {
    registrarAccionesDeTurnos();
    const { entorno } = entornoDePrueba({ 'GET /api/v1/consultorio': [json({ pasoHuecosMin: 15 })] });
    puntosDeEntrada().accion(accion, detalleDePrueba(), entorno);
    expect(textoDe(dialogoAbierto()?.querySelector('h2') ?? null)).toBe(titulo);
  });

  it('CONFIRMAR no abre diálogo', () => {
    registrarAccionesDeTurnos();
    const { entorno, llamadas } = entornoDePrueba({ 'PATCH /api/v1/turnos/t-1/estado': [json({})] });
    puntosDeEntrada().accion('CONFIRMAR', detalleDePrueba(), entorno);
    expect(dialogoAbierto()).toBeNull();
    expect(llamadas[0]?.metodo).toBe('PATCH');
  });

  it('"Nuevo turno" abre el formulario', () => {
    registrarAccionesDeTurnos();
    const { entorno } = entornoDePrueba({ 'GET /api/v1/prestaciones': [json([])] });
    puntosDeEntrada().nuevoTurno(null, entorno);
    expect(textoDe(dialogoAbierto()?.querySelector('h2') ?? null)).toBe('Nuevo turno');
  });
});
