// Genera src/shared/api-types.ts desde el contrato OpenAPI del backend (spec plataforma/contrato-api).
// Uso:
//   npm run api:types                         → lee http://localhost:8080/v3/api-docs (backend corriendo)
//   npm run api:types -- <ruta-o-url>         → lee un archivo (p. ej. backend/build/openapi/openapi.json) o una URL
import { mkdir, writeFile } from 'node:fs/promises';
import { dirname, resolve } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';
import openapiTS, { astToString } from 'openapi-typescript';

const ORIGEN_POR_DEFECTO = 'http://localhost:8080/v3/api-docs';
const raizFrontend = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const destino = resolve(raizFrontend, 'src/shared/api-types.ts');

const argumento = process.argv[2] ?? ORIGEN_POR_DEFECTO;
const origen = /^https?:\/\//.test(argumento) ? new URL(argumento) : pathToFileURL(resolve(process.cwd(), argumento));

const encabezado = [
  '/**',
  ' * ARCHIVO GENERADO: no editar a mano.',
  ' * Fuente: contrato OpenAPI del backend (/v3/api-docs). Regenerar con `npm run api:types`.',
  ' */',
  '',
].join('\n');

try {
  const ast = await openapiTS(origen);
  await mkdir(dirname(destino), { recursive: true });
  await writeFile(destino, encabezado + astToString(ast), 'utf8');
  console.log(`api-types.ts generado desde ${origen.href}`);
} catch (error) {
  console.error(`No se pudo generar api-types.ts desde ${origen.href}:`, error instanceof Error ? error.message : error);
  process.exitCode = 1;
}
