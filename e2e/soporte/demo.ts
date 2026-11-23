import { expect, type Page } from '@playwright/test';

/**
 * Datos del seed local (backend/src/main/resources/db/seed-local/README.md). Todo es ficticio (regla dura 17) y la
 * contraseña vale solo en local.
 */
export const DEMO = {
  email: 'demo@consultorio.test',
  vencido: 'vencido@consultorio.test',
  contrasena: 'demo-local-2026',
  slug: 'demo',
  slugVencido: 'demo-vencido',
  /** Turno del seed reprogramado por el paciente desde el link (miércoles 11:00). */
  turnoReprogramado: '01936b2e-0000-7000-8000-000000000314',
} as const;

const LETRAS = 'abcdefghijklmnopqrstuvwxyz';

/**
 * Sufijo de letras único por corrida (design D4): los nombres no aceptan números y así dos corridas sobre el mismo
 * stack no chocan.
 */
export function sufijoUnico(): string {
  let n = Date.now() * 1000 + Math.floor(Math.random() * 1000);
  let sufijo = '';
  for (let i = 0; i < 8; i++) {
    sufijo = (LETRAS[n % LETRAS.length] ?? 'a') + sufijo;
    n = Math.floor(n / LETRAS.length);
  }
  return sufijo.charAt(0).toUpperCase() + sufijo.slice(1);
}

/** DNI ficticio de 8 dígitos que empieza con 9, como los del seed (90000001…), distinto en cada corrida. */
export function dniUnico(): string {
  return `9${String(Math.floor(Math.random() * 10_000_000)).padStart(7, '0')}`;
}

/** Inicia sesión desde la pantalla de ingreso y espera la agenda. */
export async function ingresar(page: Page, email: string, contrasena: string = DEMO.contrasena): Promise<void> {
  await page.goto('/login');
  await page.getByLabel('Email').fill(email);
  await page.getByLabel('Contraseña', { exact: true }).fill(contrasena);
  await page.getByRole('button', { name: 'Ingresar' }).click();
  await expect(page).toHaveURL(/\/app\/agenda/);
}
