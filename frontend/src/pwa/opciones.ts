import type { VitePWAOptions } from 'vite-plugin-pwa';

/** Caché de la última agenda leída con conexión. Se borra al cerrar sesión (C-06, design D6). */
export const CACHE_AGENDA = 'agenda-v1';

/** Lee la constante BRAND_NAME de design-system/brand.js (única fuente del nombre provisorio). */
export function leerNombreDeMarca(codigoBrand: string): string {
  const coincidencia = /BRAND_NAME\s*=\s*"([^"]+)"/.exec(codigoBrand);
  if (coincidencia?.[1] === undefined) {
    throw new Error('design-system/brand.js no define BRAND_NAME');
  }
  return coincidencia[1];
}

/**
 * Opciones de vite-plugin-pwa (spec pwa-offline, design D6).
 * Política: precache del shell; en runtime SOLO `GET /api/v1/agenda` con NetworkFirst.
 * Nada de /api/v1/auth/**, /api/v1/public/** ni /t/**; sin Background Sync (no hay escritura offline).
 */
export function crearOpcionesPwa(nombreDeMarca: string): Partial<VitePWAOptions> {
  return {
    strategies: 'generateSW',
    registerType: 'autoUpdate',
    injectRegister: 'script-defer',
    includeAssets: ['icons/*.png'],
    manifest: {
      name: nombreDeMarca,
      short_name: nombreDeMarca,
      description: 'Agenda de turnos para tu consultorio odontológico.',
      lang: 'es-AR',
      dir: 'ltr',
      display: 'standalone',
      start_url: '/app/agenda',
      scope: '/',
      // Hex permitido acá: el manifiesto no puede leer variables CSS. Igual a --color-primary y --color-bg (tokens rev. 1).
      theme_color: '#0b5e73',
      background_color: '#faf8f5',
      icons: [
        { src: '/icons/icono-192.png', sizes: '192x192', type: 'image/png' },
        { src: '/icons/icono-512.png', sizes: '512x512', type: 'image/png' },
        { src: '/icons/icono-512.png', sizes: '512x512', type: 'image/png', purpose: 'maskable' },
      ],
    },
    workbox: {
      globPatterns: ['**/*.{js,css,html,svg,png,webmanifest}'],
      navigateFallback: '/index.html',
      navigateFallbackDenylist: [/^\/api\//, /^\/c\//, /^\/t\//],
      cleanupOutdatedCaches: true,
      runtimeCaching: [
        {
          // Se serializa dentro del Service Worker: no puede usar variables de este módulo.
          urlPattern: ({ url, request }) =>
            request.method === 'GET' && (url.pathname === '/api/v1/agenda' || url.pathname.startsWith('/api/v1/agenda/')),
          method: 'GET',
          handler: 'NetworkFirst',
          options: {
            cacheName: 'agenda-v1',
            networkTimeoutSeconds: 4,
            expiration: { maxEntries: 30, maxAgeSeconds: 7 * 24 * 60 * 60 },
            cacheableResponse: { statuses: [200] },
          },
        },
      ],
    },
  };
}
