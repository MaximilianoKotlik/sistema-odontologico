import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { describe, expect, it } from 'vitest';

// El token de /t/{token} viaja en la ruta: el navegador no tiene que mandarlo en el Referer (C-16, design D7).
const leer = (relativo: string) => readFileSync(fileURLToPath(new URL(relativo, import.meta.url)), 'utf8');

describe('Referer bloqueado en las páginas del paciente', () => {
  it('index.html pide no-referrer con una meta (defensa aunque se sirva sin la config de nginx)', () => {
    const documento = new DOMParser().parseFromString(leer('../index.html'), 'text/html');
    expect(documento.querySelector('meta[name="referrer"]')?.getAttribute('content')).toBe('no-referrer');
  });

  it('nginx manda Referrer-Policy: no-referrer en /c/ y /t/ sin perderla por un redirect interno', () => {
    const conf = leer('../nginx.conf');
    const bloque = /location ~ \^\/\(c\|t\)\/ \{([^}]*)\}/.exec(conf)?.[1] ?? '';
    expect(bloque).toMatch(/add_header Referrer-Policy "no-referrer" always;/);
    // try_files con /index.html como último parámetro haría un redirect interno a "location /" y perdería la cabecera.
    expect(bloque).toMatch(/try_files \/index\.html =404;/);
  });
});
