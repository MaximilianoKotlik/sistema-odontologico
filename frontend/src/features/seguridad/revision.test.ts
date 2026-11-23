import { execFileSync } from 'node:child_process';
import { mkdtempSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { describe, expect, it } from 'vitest';
import { changesCompletados, revisarFuente, type FuenteSeguridad } from './fuente';

const CHANGES = [
  '### [C-03] `auditoria-base`',
  '- **Estado**: `[x]` completado (PR #21, 2026-11-20)',
  '',
  '### [C-18] `historial-auditoria`',
  '- **Estado**: `[ ]` pendiente',
  '',
  '### [C-19] `exportacion-datos`',
  '- **Estado**: `[x]` completado (PR #61, 2026-11-21)',
].join('\n');

const afirmacion = (texto: FuenteSeguridad['aviso']['texto'], requiere: string[] = [], respaldo = ['RN-PL-06']) => ({ texto, respaldo, requiere });

function fuente(...bloques: FuenteSeguridad['temas'][number]['bloques']): FuenteSeguridad {
  return {
    titulo: 'Seguridad y datos',
    bajada: 'Bajada.',
    actualizadoEn: '2026-11-22',
    revision: 'Se revisa.',
    aviso: afirmacion(['Aviso.']),
    temas: [{ id: 'auditoria', titulo: 'Registro', icono: 'historial', bloques }],
    enlacesDelPie: [],
  };
}

describe('changesCompletados', () => {
  it('lee los changes marcados [x] en CHANGES.md', () => {
    expect([...changesCompletados(CHANGES)].sort()).toEqual(['C-03', 'C-19']);
  });
});

describe('4.1 revisión antes de publicar (design D3)', () => {
  it('una fuente sin pendientes no tiene problemas', () => {
    expect(revisarFuente(fuente({ tipo: 'parrafo', afirmacion: afirmacion(['El registro no se borra.'], ['C-03']) }), CHANGES)).toEqual([]);
  });

  it('falla si una afirmación depende de un change sin [x], nombrando la afirmación y el change', () => {
    const problemas = revisarFuente(fuente({ tipo: 'parrafo', afirmacion: afirmacion(['Podés consultarlo desde tu cuenta, en "Registro de cambios".'], ['C-18', 'C-03']) }), CHANGES);
    expect(problemas).toEqual(['Depende de C-18, que no está completado en CHANGES.md: "Podés consultarlo desde tu cuenta, en "Registro de cambios"."']);
  });

  it('falla si una afirmación no tiene respaldo', () => {
    const problemas = revisarFuente(fuente({ tipo: 'lista', items: [afirmacion(['Frase sin respaldo.'], [], [])] }), CHANGES);
    expect(problemas).toEqual(['No tiene respaldo: "Frase sin respaldo."']);
  });

  it('falla y lista los marcadores (TBD) pendientes', () => {
    const problemas = revisarFuente(
      fuente(
        { tipo: 'parrafo', afirmacion: afirmacion(['Proveedor del servidor: ', { tbd: 'proveedor del VPS' }]) },
        { tipo: 'parrafo', afirmacion: afirmacion(['Escribinos a ', { tbd: 'email de seguridad' }, '.']) },
      ),
      CHANGES,
    );
    expect(problemas).toEqual(['Quedan marcadores (TBD): proveedor del VPS, email de seguridad']);
  });
});

describe('npm run seguridad:revisar', () => {
  const script = join(process.cwd(), 'scripts', 'seguridad-revisar.mjs');
  const correr = (archivoFuente: string, archivoChanges: string) => {
    try {
      const salida = execFileSync(process.execPath, ['--experimental-strip-types', '--no-warnings', script, '--fuente', archivoFuente, '--changes', archivoChanges], { encoding: 'utf8' });
      return { codigo: 0, salida };
    } catch (error) {
      const e = error as { status: number; stdout: string; stderr: string };
      return { codigo: e.status, salida: `${e.stdout}${e.stderr}` };
    }
  };

  it('sale con 0 y lo dice cuando no hay problemas', () => {
    const dir = mkdtempSync(join(tmpdir(), 'seguridad-'));
    writeFileSync(join(dir, 'fuente.json'), JSON.stringify(fuente({ tipo: 'parrafo', afirmacion: afirmacion(['El registro no se borra.'], ['C-03']) })));
    writeFileSync(join(dir, 'CHANGES.md'), CHANGES);

    const { codigo, salida } = correr(join(dir, 'fuente.json'), join(dir, 'CHANGES.md'));
    expect(codigo).toBe(0);
    expect(salida).toContain('lista para publicar');
  });

  it('sale con 1 y lista cada problema', () => {
    const dir = mkdtempSync(join(tmpdir(), 'seguridad-'));
    writeFileSync(join(dir, 'fuente.json'), JSON.stringify(fuente({ tipo: 'parrafo', afirmacion: afirmacion(['Escribinos a ', { tbd: 'email de seguridad' }], ['C-18']) })));
    writeFileSync(join(dir, 'CHANGES.md'), CHANGES);

    const { codigo, salida } = correr(join(dir, 'fuente.json'), join(dir, 'CHANGES.md'));
    expect(codigo).toBe(1);
    expect(salida).toContain('Depende de C-18');
    expect(salida).toContain('Quedan marcadores (TBD): email de seguridad');
  });

  it('una fuente que no cumple el contrato también falla', () => {
    const dir = mkdtempSync(join(tmpdir(), 'seguridad-'));
    writeFileSync(join(dir, 'fuente.json'), JSON.stringify({ titulo: 'sin el resto' }));
    writeFileSync(join(dir, 'CHANGES.md'), CHANGES);

    const { codigo, salida } = correr(join(dir, 'fuente.json'), join(dir, 'CHANGES.md'));
    expect(codigo).toBe(1);
    expect(salida).toContain('no cumple el contrato');
  });
});
