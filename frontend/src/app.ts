import { montarShell, type Shell } from './app/shell';
import type { Pagina } from './pages/pagina';
import { borrarCacheAgenda, crearConectividad, registrarSincronizacion, type EstadoConectividad } from './shared/conectividad';
import { crearClienteHttp, type ClienteHttp } from './shared/http';
import { nombreDeMarca } from './shared/marca';
import { crearRouter, rutaDeLogin, type DefinicionRuta, type Router } from './shared/router';
import { crearStore, type Store } from './shared/store';

/** Datos de la sesión que usa el shell. La zona se actualiza al cargar la configuración (C-07/C-09). */
export interface EstadoSesion {
  readonly zonaHoraria: string;
}

export interface DependenciasApp {
  readonly fetch?: typeof fetch;
}

export interface Aplicacion {
  readonly router: Router;
  readonly http: ClienteHttp;
  readonly shell: Shell;
  readonly conectividad: Store<EstadoConectividad>;
  readonly sesion: Store<EstadoSesion>;
  /** Se resuelve cuando la primera ruta terminó de mostrarse. */
  readonly listo: Promise<void>;
  /** Guarda el access token de una sesión recién iniciada (login de C-07), sin datos de una sesión anterior. */
  iniciarSesion(accessToken: string): Promise<void>;
  cerrarSesion(): Promise<void>;
  detener(): void;
}

/** Cada ruta se carga con import() para dividir el bundle (design D1). */
const RUTAS: readonly DefinicionRuta<Pagina>[] = [
  { patron: '/app/agenda', cargar: async () => (await import('./pages/agenda')).pagina },
  { patron: '/app/bloqueos', cargar: async () => (await import('./pages/bloqueos')).pagina },
  { patron: '/app/configuracion', cargar: async () => (await import('./pages/configuracion')).pagina },
  { patron: '/app/auditoria', cargar: async () => (await import('./pages/auditoria')).pagina },
  { patron: '/app/exportar', cargar: async () => (await import('./pages/exportar')).pagina },
];

/** Monta la SPA del odontólogo: shell, router, cliente HTTP y estado de conexión. */
export function montarAplicacion(documento: Document, dependencias: DependenciasApp = {}): Aplicacion {
  const anfitrion = documento.querySelector<HTMLElement>('#app');
  if (anfitrion === null) {
    throw new Error('No se encontró el elemento #app');
  }

  const sesion = crearStore<EstadoSesion>({ zonaHoraria: 'America/Argentina/Buenos_Aires' });
  const { store: conectividad, detener: dejarDeEscucharRed } = crearConectividad(window);

  // Las closures que usan `routerCreado` solo corren después de crearlo (al vencer la sesión o al cerrarla).
  const http = crearClienteHttp({
    ...(dependencias.fetch === undefined ? {} : { fetch: dependencias.fetch }),
    alVencerSesion: () => {
      void olvidarAgendaGuardada();
      void routerCreado.navegar(rutaDeLogin(routerCreado.rutaActual()));
    },
    alResponder: (ruta, respuesta) => registrarSincronizacion(conectividad, ruta, respuesta),
    alFallarRed: () => conectividad.set({ online: false }),
  });

  /**
   * La agenda en caché tiene datos de pacientes (Ley 25.326): no sobrevive a la sesión que la vio.
   * Se borra al cerrar sesión, al vencer y al iniciar otra (la clave de caché es solo la URL, así que
   * sin esto otro consultorio en el mismo navegador podría ver la agenda anterior sin conexión).
   * Un fallo al borrar no frena el flujo de sesión.
   */
  async function olvidarAgendaGuardada(): Promise<void> {
    await borrarCacheAgenda().catch(() => undefined);
  }

  async function iniciarSesion(accessToken: string): Promise<void> {
    await olvidarAgendaGuardada();
    http.establecerToken(accessToken);
  }

  async function cerrarSesion(): Promise<void> {
    // Sin conexión no se puede revocar el refresh, pero lo local se borra igual.
    await http.post('/api/v1/auth/logout').catch(() => undefined);
    http.borrarToken();
    await olvidarAgendaGuardada();
    await routerCreado.navegar('/login');
  }

  const shell = montarShell(anfitrion, {
    marca: nombreDeMarca(),
    conectividad,
    zonaHoraria: () => sesion.get().zonaHoraria,
    alCerrarSesion: () => void cerrarSesion(),
  });

  let primeraVez = true;
  const routerCreado = crearRouter<Pagina>({
    rutas: RUTAS,
    redirecciones: { '/': '/app/agenda', '/app': '/app/agenda', '/app/': '/app/agenda' },
    noEncontrada: async () => (await import('./pages/no-encontrada')).pagina,
    mostrar: ({ pagina, params }) => {
      shell.mostrar(pagina, { enfocar: !primeraVez });
      primeraVez = false;
      pagina.montar({ contenedor: shell.contenido, acciones: shell.acciones, params, http, zonaHoraria: sesion.get().zonaHoraria });
      shell.sincronizarConexion();
    },
  });

  // Vuelve la conexión: la página actual vuelve a pedir sus datos (spec app-shell).
  const dejarDeObservarRed = conectividad.suscribir((actual, anterior) => {
    if (actual.online && !anterior.online) void routerCreado.recargar();
  });

  return {
    router: routerCreado,
    http,
    shell,
    conectividad,
    sesion,
    listo: routerCreado.iniciar(),
    iniciarSesion,
    cerrarSesion,
    detener() {
      routerCreado.detener();
      dejarDeObservarRed();
      dejarDeEscucharRed();
      shell.destruir();
    },
  };
}
