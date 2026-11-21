import { describe, expect, it } from 'vitest';
import {
  crearContextoDePrueba,
  enviar,
  escribir,
  esperarPedidos,
  problemaJson,
  respuestaJson,
} from '../features/auth/prueba-contexto';
import { pagina } from './registro';

// Tarea 3.2 de C-07 (spec app-auth/registro; mockup `registro.html`). Datos ficticios evidentes.
const EMAIL = 'odontologa@example.com';

function montar(...argumentos: Parameters<typeof crearContextoDePrueba>) {
  const prueba = crearContextoDePrueba(...argumentos);
  pagina.montar(prueba.contexto);
  const campo = (nombre: string) => prueba.contenedor.querySelector<HTMLInputElement>(`input[name="${nombre}"]`);
  const completar = (password = 'ficticia-segura-01') => {
    escribir(campo('nombre'), 'Ana');
    escribir(campo('apellido'), 'Ejemplo');
    escribir(campo('email'), EMAIL);
    escribir(campo('password'), password);
    escribir(campo('nombreConsultorio'), 'Consultorio Demo');
    const terminos = campo('aceptaTerminos');
    if (terminos !== null) terminos.checked = true;
  };
  const requisito = (texto: string) =>
    [...prueba.contenedor.querySelectorAll<HTMLLIElement>('#requisitos-registro li')].find((li) => li.textContent?.startsWith(texto));
  return { ...prueba, campo, completar, requisito };
}

describe('registro', () => {
  it('muestra los campos del contrato con los links a términos y privacidad', () => {
    const { contenedor, campo } = montar();

    expect(contenedor.querySelector('h1')?.textContent).toBe('Creá tu consultorio');
    for (const nombre of ['nombre', 'apellido', 'email', 'password', 'nombreConsultorio', 'aceptaTerminos']) {
      expect(campo(nombre), nombre).not.toBeNull();
    }
    expect(campo('password')?.getAttribute('autocomplete')).toBe('new-password');
    expect(contenedor.querySelector('a[href="/terminos"]')?.textContent).toBe('términos y condiciones');
    expect(contenedor.querySelector('a[href="/privacidad"]')?.textContent).toBe('política de privacidad');
  });

  it('los requisitos cambian mientras se escribe, con ícono y texto oculto', () => {
    const { campo, requisito } = montar();

    escribir(campo('password'), 'corta1');
    expect(requisito('Al menos 10 caracteres')?.dataset['estado']).toBe('pendiente');
    expect(requisito('Al menos 10 caracteres')?.querySelector('.sr-only')?.textContent).toContain('pendiente');
    expect(requisito('Al menos 10 caracteres')?.querySelector('svg')).not.toBeNull();

    escribir(campo('password'), 'ficticia-segura-01');
    expect(requisito('Al menos 10 caracteres')?.dataset['estado']).toBe('cumplido');
    expect(requisito('Hasta 72 caracteres')).toBeUndefined();
  });

  it('con más de 72 bytes aparece el requisito en falta y el campo inválido', () => {
    const { campo, requisito } = montar();

    escribir(campo('password'), 'ñ'.repeat(37));

    expect(requisito('Hasta 72 caracteres')?.dataset['estado']).toBe('fallado');
    expect(campo('password')?.getAttribute('aria-invalid')).toBe('true');
  });

  it('con errores no envía y muestra un resumen enfocado con links a cada campo', async () => {
    const { contenedor, campo, completar, llamadas } = montar();
    completar('corta-123');
    const terminos = campo('aceptaTerminos');
    if (terminos !== null) terminos.checked = false;

    enviar(contenedor);
    await esperarPedidos();

    expect(llamadas).toHaveLength(0);
    const resumen = contenedor.querySelector<HTMLElement>('[data-resumen-errores]');
    expect(document.activeElement).toBe(resumen);
    expect(resumen?.textContent).toContain('Revisá 2 datos para seguir:');
    const destinos = [...(resumen?.querySelectorAll('a') ?? [])].map((a) => a.getAttribute('href'));
    expect(destinos).toEqual([`#${campo('password')?.id ?? ''}`, `#${campo('aceptaTerminos')?.id ?? ''}`]);
    expect(requisitoEstado(contenedor, 'Al menos 10 caracteres')).toBe('fallado');
  });

  it('con 201 entra solo: llama al login con las mismas credenciales y abre la app', async () => {
    const { contenedor, completar, llamadas, contexto } = montar({
      '/api/v1/auth/registro': [respuestaJson({ usuarioId: 'u', consultorioId: 'c', slug: 'consultorio-demo' }, 201)],
      '/api/v1/auth/login': [respuestaJson({ accessToken: 'token-ficticio' })],
    });
    completar();

    enviar(contenedor);
    await esperarPedidos();
    await esperarPedidos();

    expect(llamadas.map((l) => l.url)).toEqual(['/api/v1/auth/registro', '/api/v1/auth/login']);
    expect(llamadas[0]?.cuerpo).toEqual({
      nombre: 'Ana',
      apellido: 'Ejemplo',
      email: EMAIL,
      password: 'ficticia-segura-01',
      nombreConsultorio: 'Consultorio Demo',
      aceptaTerminos: true,
    });
    expect(llamadas[1]?.cuerpo).toEqual({ email: EMAIL, password: 'ficticia-segura-01' });
    expect(contexto.entrar).toHaveBeenCalledWith('token-ficticio', null);
  });

  it('si falla el login automático, lleva al login con el email y el aviso de cuenta lista', async () => {
    const { contenedor, completar, contexto } = montar({
      '/api/v1/auth/registro': [respuestaJson({}, 201)],
      '/api/v1/auth/login': [problemaJson(429, 'DEMASIADAS_SOLICITUDES')],
    });
    completar();

    enviar(contenedor);
    await esperarPedidos();
    await esperarPedidos();

    expect(contexto.dejarAviso).toHaveBeenCalledWith({ tipo: 'registro-sin-login', email: EMAIL });
    expect(contexto.navegar).toHaveBeenCalledWith('/login');
  });

  it('email en uso: marca el email con los links y conserva lo cargado salvo la contraseña', async () => {
    const { contenedor, completar, campo } = montar({ '/api/v1/auth/registro': [problemaJson(409, 'EMAIL_EN_USO')] });
    completar();

    enviar(contenedor);
    await esperarPedidos();

    const email = campo('email');
    expect(email?.getAttribute('aria-invalid')).toBe('true');
    const error = contenedor.querySelector(`#${email?.getAttribute('aria-describedby')?.split(' ').pop() ?? 'x'}`);
    expect(error?.textContent).toContain('Ya hay una cuenta con este email.');
    expect(error?.querySelector('a[href="/login"]')?.textContent).toBe('Ingresá');
    expect(error?.querySelector('a[href="/recuperar"]')?.textContent).toBe('restablecé tu contraseña');
    expect(email?.value).toBe(EMAIL);
    expect(campo('nombre')?.value).toBe('Ana');
    expect(campo('password')?.value).toBe('');
  });

  it('contraseña débil del backend: marca la contraseña', async () => {
    const { contenedor, completar, campo } = montar({ '/api/v1/auth/registro': [problemaJson(400, 'PASSWORD_DEBIL')] });
    completar();

    enviar(contenedor);
    await esperarPedidos();

    expect(campo('password')?.getAttribute('aria-invalid')).toBe('true');
  });
});

function requisitoEstado(contenedor: HTMLElement, texto: string): string | undefined {
  return [...contenedor.querySelectorAll<HTMLLIElement>('#requisitos-registro li')].find((li) => li.textContent?.startsWith(texto))?.dataset['estado'];
}
