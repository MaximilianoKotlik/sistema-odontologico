import { expect, test } from '@playwright/test';
import { DEMO, dniUnico, ingresar, sufijoUnico } from '../soporte/demo';

/**
 * Recorridos del odontólogo (C-23, spec humo-e2e; tareas 5.2, 7.1, 7.2, 7.4 y 7.5). Corren contra el stack de
 * `docker compose up` con el seed de demo.
 */

test('ingresa con la cuenta de demo y ve la agenda de hoy', async ({ page }) => {
  await ingresar(page, DEMO.email);

  // En el teléfono la agenda abre en la vista del día (C-13).
  await expect(page.getByRole('heading', { level: 1, name: 'Agenda' })).toBeVisible();
  await expect(page.getByRole('heading', { level: 2, name: /Hoy$/ })).toBeVisible();
  await expect(page.getByRole('region', { name: 'Agenda del día' })).toBeVisible();
  await expect(page.getByRole('list', { name: 'Días de la semana' })).toBeVisible();
});

test('con una contraseña incorrecta no entra y ve el error', async ({ page }) => {
  // Una cuenta que no existe: el mensaje es el mismo y no suma intentos fallidos a la cuenta de demo.
  await page.goto('/login');
  await page.getByLabel('Email').fill('nadie@consultorio.test');
  await page.getByLabel('Contraseña', { exact: true }).fill('una-clave-equivocada');
  await page.getByRole('button', { name: 'Ingresar' }).click();

  await expect(page.getByText('Email o contraseña incorrectos.')).toBeVisible();
  await expect(page).toHaveURL(/\/login/);
});

test('crea un turno con un paciente nuevo, lo ve en la agenda y lo cancela', async ({ page }) => {
  const apellido = `Humo ${sufijoUnico()}`;
  const nombreCompleto = `Paciente ${apellido}`;
  const { fecha, hora } = horarioAlAzar();
  await ingresar(page, DEMO.email);

  await page.getByRole('button', { name: 'Nuevo turno' }).click();
  const formulario = page.getByRole('dialog', { name: 'Nuevo turno' });
  await formulario.getByRole('button', { name: 'Cargar paciente nuevo' }).click();
  await formulario.getByLabel('Nombre', { exact: true }).fill('Paciente');
  await formulario.getByLabel('Apellido').fill(apellido);
  await formulario.getByLabel('DNI').fill(dniUnico());
  await formulario.getByLabel('Celular').fill('+54 9 11 0000-0099');
  await formulario.getByRole('button', { name: /^Consulta/ }).click();
  await formulario.getByLabel('Día').fill(fecha);
  await formulario.getByLabel('Empieza').fill(hora);
  await formulario.getByRole('button', { name: 'Crear turno' }).click();
  await expect(formulario).toBeHidden();

  await page.goto(`/app/agenda?vista=dia&fecha=${fecha}`);
  const turno = page.getByRole('link', { name: new RegExp(`^${hora} a .*${nombreCompleto}, Consulta, Reservado`) });
  await expect(turno).toBeVisible();

  // Se cancela al final, así el horario queda libre para la próxima corrida (design D4).
  await turno.click();
  await expect(page.getByRole('heading', { name: nombreCompleto })).toBeVisible();
  await page.getByRole('button', { name: 'Cancelar turno' }).click();
  const confirmacion = page.getByRole('alertdialog', { name: `¿Cancelar el turno de ${nombreCompleto}?` });
  await confirmacion.getByRole('button', { name: 'Sí, cancelar turno' }).click();
  await expect(page.getByText('Cancelado por vos')).toBeVisible();
});

test('ve el paciente que el seed dejó para revisar', async ({ page }) => {
  await ingresar(page, DEMO.email);
  await page.goto('/app/pacientes?requiereRevision=true');

  // La lista muestra "Apellido, Nombre" y lo que escribió al reservar.
  await expect(page.getByText('Prueba, Paciente Seis')).toBeVisible();
  await expect(page.getByText(/Escribió «Paciente Seys Prueba» al reservar/)).toBeVisible();
});

test('ve el historial completo del turno que el paciente reprogramó', async ({ page }) => {
  await ingresar(page, DEMO.email);
  await page.goto(`/app/agenda/turnos/${DEMO.turnoReprogramado}`);

  await expect(page.getByRole('heading', { name: 'Historial' })).toBeVisible();
  const historial = page.getByRole('list').filter({ hasText: 'Reservó por tu link' });
  await expect(historial.getByRole('listitem')).toHaveCount(2);
  await expect(historial.getByRole('listitem').first()).toContainText('Reservó por tu link');
  await expect(historial.getByRole('listitem').last()).toContainText(/reprogram/i);
});

/**
 * Un día hábil entre 3 y 8 semanas adelante y una hora de la mañana en la grilla de 5 minutos (la consulta dura 30 y
 * la demo atiende de 9 a 13). Al azar para que una corrida que falló a mitad de camino no ocupe el horario de la
 * siguiente.
 */
function horarioAlAzar(): { fecha: string; hora: string } {
  const dia = new Date();
  dia.setDate(dia.getDate() + 21 + Math.floor(Math.random() * 35));
  while (dia.getDay() === 0 || dia.getDay() === 6) dia.setDate(dia.getDate() + 1);
  const minutos = 9 * 60 + 5 * Math.floor(Math.random() * 42);
  const dosCifras = (n: number) => String(n).padStart(2, '0');
  const fecha = `${dia.getFullYear()}-${dosCifras(dia.getMonth() + 1)}-${dosCifras(dia.getDate())}`;
  return { fecha, hora: `${dosCifras(Math.floor(minutos / 60))}:${dosCifras(minutos % 60)}` };
}
