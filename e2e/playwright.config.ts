import { defineConfig, devices } from '@playwright/test';

/**
 * Humo E2E (C-23, design D1 y D2). Corre contra el stack de `docker compose up` con el seed de demo: no levanta
 * ningún servidor. Un solo proyecto: Chromium con viewport de teléfono, en español de Argentina y en la zona del
 * consultorio, así las fechas se ven como las ve el odontólogo.
 */
const enCi = process.env['CI'] !== undefined;

export default defineConfig({
  testDir: './tests',
  fullyParallel: false,
  workers: 1,
  forbidOnly: enCi,
  retries: enCi ? 1 : 0,
  timeout: 60_000,
  expect: { timeout: 10_000 },
  reporter: enCi ? [['list'], ['html', { open: 'never' }]] : [['list']],
  use: {
    baseURL: process.env['HUMO_BASE_URL'] ?? 'http://localhost:5173',
    locale: 'es-AR',
    timezoneId: 'America/Argentina/Buenos_Aires',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
  },
  projects: [
    {
      name: 'chromium-mobile',
      use: {
        ...devices['Pixel 7'],
        browserName: 'chromium',
        viewport: { width: 390, height: 844 },
        isMobile: true,
        hasTouch: true,
      },
    },
  ],
});
