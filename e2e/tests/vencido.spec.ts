import { expect, test } from '@playwright/test';
import { DEMO, ingresar } from '../soporte/demo';

/** Consultorio de demo con el plan vencido (C-23, spec humo-e2e; tarea 5.3). */

test('la página pública del consultorio vencido no ofrece reservar y muestra el teléfono', async ({ page }) => {
  await page.goto(`/c/${DEMO.slugVencido}`);

  await expect(page.getByText('Por ahora este consultorio no toma turnos online')).toBeVisible();
  await expect(page.getByRole('link', { name: /11 0000-0020/ }).first()).toHaveAttribute('href', 'tel:+5491100000020');
  await expect(page.getByRole('button', { name: 'Reservar turno' })).toHaveCount(0);
});

test('la cuenta vencida ve la agenda en solo lectura y puede exportar', async ({ page }) => {
  await ingresar(page, DEMO.vencido);

  await expect(page.getByText('Tu prueba terminó: la agenda está en solo lectura')).toBeVisible();
  await expect(page.getByRole('link', { name: 'Exportar mis datos' }).first()).toBeVisible();
});
