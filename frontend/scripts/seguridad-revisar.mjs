// Revisión de /seguridad antes de publicar (C-22, design D3; RN-PL-06). Está en el criterio de salida de C-23.
// Falla si una afirmación depende de un change sin [x] en CHANGES.md, si alguna no tiene respaldo o si quedan (TBD).
// Uso:
//   npm run seguridad:revisar                                   → src/contenido/seguridad.json y ../CHANGES.md
//   npm run seguridad:revisar -- --fuente <json> --changes <md> → otros archivos (los usan los tests)
// Importa la lógica desde TypeScript: necesita Node con --experimental-strip-types (22.6 o posterior).
import { readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { esFuenteSeguridad, revisarFuente } from '../src/features/seguridad/fuente.ts';

const raizFrontend = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const argumento = (nombre, porDefecto) => {
  const i = process.argv.indexOf(nombre);
  return i === -1 ? porDefecto : resolve(process.cwd(), process.argv[i + 1] ?? '');
};
const archivoFuente = argumento('--fuente', resolve(raizFrontend, 'src/contenido/seguridad.json'));
const archivoChanges = argumento('--changes', resolve(raizFrontend, '../CHANGES.md'));

const fuente = JSON.parse(readFileSync(archivoFuente, 'utf8'));
if (!esFuenteSeguridad(fuente)) {
  console.error(`${archivoFuente} no cumple el contrato de la fuente de /seguridad.`);
  process.exit(1);
}

const problemas = revisarFuente(fuente, readFileSync(archivoChanges, 'utf8'));
if (problemas.length === 0) {
  console.log('/seguridad está lista para publicar: todas las afirmaciones tienen respaldo y changes completados, sin (TBD).');
} else {
  console.error(`/seguridad NO se puede publicar todavía (${problemas.length} problema${problemas.length === 1 ? '' : 's'}):`);
  for (const problema of problemas) console.error(`- ${problema}`);
  process.exitCode = 1;
}
