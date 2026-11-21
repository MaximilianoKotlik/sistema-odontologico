import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { crearRouter, rutaDeLogin, type Resolucion, type Router } from './router';

type Pagina = string;

function preparar(rutaInicial: string, extra: { cargarAgenda?: () => Promise<Pagina> } = {}) {
  history.replaceState(null, '', rutaInicial);
  const mostrar = vi.fn<(r: Resolucion<Pagina>) => void>();
  const router = crearRouter<Pagina>({
    rutas: [
      { patron: '/app/agenda', cargar: extra.cargarAgenda ?? (() => Promise.resolve('agenda')) },
      { patron: '/app/bloqueos', cargar: () => Promise.resolve('bloqueos') },
      { patron: '/app/turnos/:id', cargar: () => 'turno' },
    ],
    redirecciones: { '/app': '/app/agenda', '/app/': '/app/agenda' },
    noEncontrada: () => 'no-encontrada',
    mostrar,
  });
  return { router, mostrar };
}

const ultima = (mostrar: ReturnType<typeof preparar>['mostrar']) => mostrar.mock.calls.at(-1)?.[0];
const esperar = () => new Promise((r) => setTimeout(r, 0));

describe('router', () => {
  let router: Router | undefined;
  afterEach(() => router?.detener());

  it('resuelve la ruta inicial', async () => {
    const p = preparar('/app/agenda');
    router = p.router;
    await router.iniciar();
    expect(ultima(p.mostrar)).toMatchObject({ ruta: '/app/agenda', patron: '/app/agenda', pagina: 'agenda' });
  });

  it('extrae parámetros de la ruta', async () => {
    const p = preparar('/app/turnos/42');
    router = p.router;
    await router.iniciar();
    expect(ultima(p.mostrar)).toMatchObject({ pagina: 'turno', params: { id: '42' } });
  });

  it('navegar cambia la URL sin recargar y agrega una entrada al historial', async () => {
    const p = preparar('/app/agenda');
    router = p.router;
    await router.iniciar();
    const largo = history.length;

    await router.navegar('/app/bloqueos');

    expect(location.pathname).toBe('/app/bloqueos');
    expect(history.length).toBe(largo + 1);
    expect(ultima(p.mostrar)).toMatchObject({ pagina: 'bloqueos' });
  });

  it('atrás del navegador vuelve a mostrar la sección anterior', async () => {
    const p = preparar('/app/agenda');
    router = p.router;
    await router.iniciar();
    await router.navegar('/app/bloqueos');

    history.back();
    await vi.waitFor(() => expect(location.pathname).toBe('/app/agenda'));
    await vi.waitFor(() => expect(ultima(p.mostrar)).toMatchObject({ pagina: 'agenda' }));
  });

  it('/app redirige a /app/agenda reemplazando la entrada del historial', async () => {
    const p = preparar('/app');
    router = p.router;
    const largo = history.length;

    await router.iniciar();

    expect(location.pathname).toBe('/app/agenda');
    expect(history.length).toBe(largo);
    expect(ultima(p.mostrar)).toMatchObject({ pagina: 'agenda' });
  });

  it('una ruta inexistente muestra la página no encontrada', async () => {
    const p = preparar('/app/no-existe');
    router = p.router;
    await router.iniciar();
    expect(ultima(p.mostrar)).toMatchObject({ ruta: '/app/no-existe', patron: null, pagina: 'no-encontrada' });
  });

  it('intercepta los clics en enlaces data-link', async () => {
    const p = preparar('/app/agenda');
    router = p.router;
    await router.iniciar();
    document.body.innerHTML = '<a href="/app/bloqueos" data-link><span>Bloqueos</span></a>';
    const evento = new MouseEvent('click', { bubbles: true, cancelable: true, button: 0 });

    document.querySelector('span')?.dispatchEvent(evento);
    await esperar();

    expect(evento.defaultPrevented).toBe(true);
    expect(location.pathname).toBe('/app/bloqueos');
  });

  it('no intercepta clics con tecla modificadora (abrir en otra pestaña)', async () => {
    const p = preparar('/app/agenda');
    router = p.router;
    await router.iniciar();
    document.body.innerHTML = '<a href="/app/bloqueos" data-link>Bloqueos</a>';
    const evento = new MouseEvent('click', { bubbles: true, cancelable: true, button: 0, ctrlKey: true });
    // jsdom no navega: evitamos su aviso de "navigation not implemented".
    document.addEventListener('click', (e) => e.preventDefault(), { once: true });

    document.querySelector('a')?.dispatchEvent(evento);
    await esperar();

    expect(location.pathname).toBe('/app/agenda');
  });

  it('ignora una carga lenta si mientras tanto se navegó a otra ruta', async () => {
    let liberar: (p: Pagina) => void = () => undefined;
    const lenta = new Promise<Pagina>((r) => {
      liberar = r;
    });
    const p = preparar('/app/bloqueos', { cargarAgenda: () => lenta });
    router = p.router;
    await router.iniciar();

    const haciaAgenda = router.navegar('/app/agenda');
    await router.navegar('/app/bloqueos');
    liberar('agenda');
    await haciaAgenda;

    expect(ultima(p.mostrar)).toMatchObject({ pagina: 'bloqueos' });
    expect(p.mostrar.mock.calls.some(([r]) => r.pagina === 'agenda')).toBe(false);
  });

  it('recargar vuelve a resolver la ruta actual', async () => {
    const p = preparar('/app/agenda');
    router = p.router;
    await router.iniciar();
    p.mostrar.mockClear();

    await router.recargar();

    expect(p.mostrar).toHaveBeenCalledTimes(1);
  });
});

describe('rutaDeLogin', () => {
  it('conserva la ruta a la que se intentaba ir', () => {
    expect(rutaDeLogin('/app/agenda')).toBe('/login?volver=/app/agenda');
  });

  it('codifica la consulta de la ruta de vuelta', () => {
    expect(rutaDeLogin('/app/agenda?dia=2026-11-24&vista=semana')).toBe('/login?volver=/app/agenda%3Fdia%3D2026-11-24%26vista%3Dsemana');
  });
});

beforeEach(() => {
  document.body.innerHTML = '';
});

describe('router: guarda de acceso (C-07)', () => {
  let router: Router | undefined;
  afterEach(() => router?.detener());

  function conGuarda(rutaInicial: string, guarda: (ruta: { pathname: string; ruta: string }) => string | null | Promise<string | null>) {
    history.replaceState(null, '', rutaInicial);
    const mostrar = vi.fn<(r: Resolucion<string>) => void>();
    const r = crearRouter<string>({
      rutas: [
        { patron: '/app/agenda', cargar: () => 'agenda' },
        { patron: '/login', cargar: () => 'login' },
      ],
      redirecciones: { '/app': '/app/agenda' },
      guarda,
      noEncontrada: () => 'no-encontrada',
      mostrar,
    });
    return { router: r, mostrar };
  }

  it('la guarda puede redirigir antes de cargar, reemplazando la entrada del historial', async () => {
    const largo = history.length;
    const p = conGuarda('/app/agenda', ({ pathname }) => (pathname === '/app/agenda' ? '/login?volver=/app/agenda' : null));
    router = p.router;

    await router.iniciar();

    expect(`${location.pathname}${location.search}`).toBe('/login?volver=/app/agenda');
    expect(p.mostrar).toHaveBeenCalledTimes(1);
    expect(p.mostrar.mock.calls[0]?.[0].pagina).toBe('login');
    expect(history.length).toBe(largo);
  });

  it('la guarda corre después de las redirecciones fijas y recibe la ruta con su query', async () => {
    const vistas: { pathname: string; ruta: string }[] = [];
    const p = conGuarda('/app?x=1', (ruta) => {
      vistas.push(ruta);
      return null;
    });
    router = p.router;

    await router.iniciar();

    expect(vistas).toEqual([{ pathname: '/app/agenda', ruta: '/app/agenda' }]);
  });

  it('una guarda asíncrona no muestra nada hasta resolver, y si se navega en el medio gana la última', async () => {
    let liberar: (valor: string | null) => void = () => undefined;
    const p = conGuarda('/app/agenda', ({ pathname }) => (pathname === '/app/agenda' ? new Promise((r) => (liberar = r)) : null));
    router = p.router;

    const inicio = router.iniciar();
    await esperar();
    expect(p.mostrar).not.toHaveBeenCalled();
    await router.navegar('/login');
    liberar(null);
    await inicio;

    expect(p.mostrar).toHaveBeenCalledTimes(1);
    expect(p.mostrar.mock.calls[0]?.[0].pagina).toBe('login');
  });

  it('corta un ciclo de redirecciones de la guarda', async () => {
    const p = conGuarda('/app/agenda', ({ pathname }) => (pathname === '/app/agenda' ? '/login' : '/app/agenda'));
    router = p.router;

    await expect(router.iniciar()).rejects.toThrow('ciclo');
  });
});
