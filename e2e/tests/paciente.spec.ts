import { expect, test } from '@playwright/test';
import { DEMO, dniUnico, sufijoUnico } from '../soporte/demo';

/**
 * Recorrido del paciente sin cuenta (C-23, spec humo-e2e; tarea 7.3): página pública, prestación, horario, datos y
 * política, confirmación, link de gestión y cancelación. Cancela al final para liberar el horario (design D4).
 */
test('reserva un turno desde la página pública y lo cancela con su link', async ({ page }) => {
  const apellido = `Humo ${sufijoUnico()}`;
  await page.goto(`/c/${DEMO.slug}`);

  await page.getByRole('group', { name: 'Prestaciones' }).getByRole('button', { name: /^Control/ }).click();
  // El último día con turnos de los que muestra la página (unas dos semanas adelante) y su primer horario libre:
  // así el turno queda fuera de las 24 horas de anticipación y el paciente lo puede cancelar desde el link.
  await page.getByRole('group', { name: 'Días' }).getByRole('button', { name: /^(lun|mar|mié|jue|vie) \d+$/ }).last().click();
  await page.getByRole('group', { name: /^Horarios de la / }).first().getByRole('button').first().click();
  await page.getByRole('button', { name: /^Continuar con / }).click();

  await page.getByLabel('Nombre', { exact: true }).fill('Paciente');
  await page.getByLabel('Apellido', { exact: true }).fill(apellido);
  await page.getByLabel('DNI', { exact: true }).fill(dniUnico());
  await page.getByLabel('Celular', { exact: true }).fill('+54 9 11 0000-0098');
  await page.getByRole('checkbox', { name: /^Acepto que/ }).check();
  await page.getByRole('button', { name: 'Reservar turno' }).click();

  await expect(page.getByRole('heading', { name: '¡Listo, reservaste tu turno!' })).toBeVisible();
  const link = await page.getByRole('region', { name: 'Guardá este link' }).getByText(/\/t\//).textContent();
  expect(link).toMatch(/\/t\/[A-Za-z0-9_-]{43}$/);

  await page.goto(new URL(link ?? '').pathname);
  await expect(page.getByRole('heading', { name: 'Tu turno' })).toBeVisible();
  await expect(page.getByText(`Paciente ${apellido}`, { exact: false })).toBeVisible();
  await page.getByRole('button', { name: 'Cancelar turno' }).click();
  await page.getByRole('button', { name: 'Sí, cancelar turno' }).click();
  await expect(page.getByText('Cancelaste tu turno').first()).toBeVisible();
});
