import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { searchForWorkspaceRoot } from 'vite';
import { VitePWA } from 'vite-plugin-pwa';
import { defineConfig } from 'vitest/config';
import { crearOpcionesPwa, leerNombreDeMarca } from './src/pwa/opciones';

// En Docker Compose el backend se llama "backend"; fuera de Docker, localhost.
const destinoApi = process.env['VITE_PROXY_TARGET'] ?? 'http://localhost:8080';

// design-system/ vive fuera de frontend/: se importa sin copiarlo (C-06, design D5).
const designSystem = fileURLToPath(new URL('../design-system', import.meta.url));
// docs/legal/ también vive fuera de frontend/: /terminos y /privacidad importan el borrador en el build (C-07, design D9).
const docsLegales = fileURLToPath(new URL('../docs/legal', import.meta.url));
const nombreDeMarca = leerNombreDeMarca(readFileSync(`${designSystem}/brand.js`, 'utf8'));

export default defineConfig({
  plugins: [VitePWA(crearOpcionesPwa(nombreDeMarca))],
  server: {
    port: 5173,
    strictPort: true,
    fs: { allow: [searchForWorkspaceRoot(process.cwd()), designSystem, docsLegales] },
    proxy: {
      '/api': { target: destinoApi, changeOrigin: true },
    },
  },
  test: {
    environment: 'jsdom',
    include: ['src/**/*.test.ts'],
    // Dispositivo en otra zona a propósito: detecta usos de la zona local (C-06, design D7).
    env: { TZ: 'Europe/Madrid' },
    // Desde Node 25, `localStorage` global es el de Node (undefined sin --localstorage-file) y tapa al de jsdom.
    // Apagarlo deja el de jsdom en cualquier versión de Node (CI usa 22, donde ya viene apagado).
    execArgv: ['--no-experimental-webstorage'],
  },
});
