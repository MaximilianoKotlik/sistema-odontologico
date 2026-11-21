import { montarShell, type Shell } from './app/shell';
import type { AvisoLogin, ContextoAuth, PaginaAuth } from './features/auth/contexto';
import { crearCanalSesion, crearCoordinadorRefresh, type FabricaDeCanal, type LocksMinimos, type MotivoCierre } from './features/auth/coordinacion';
import { montarLayoutAuth, montarSplash } from './features/auth/layout';
import { decidirAcceso, esRutaPrivada } from './features/auth/sesion';
import { sanearVolver } from './features/auth/volver';
import { RUTA_PLANES_PUBLICOS } from './features/precios/catalogo';
import type { Pagina } from './pages/pagina';
import { borrarCacheAgenda, crearConectividad, registrarSincronizacion, type EstadoConectividad } from './shared/conectividad';
import { ApiError, crearClienteHttp, type ClienteHttp } from './shared/http';
import { el } from './shared/dom';
import { nombreDeMarca } from './shared/marca';
import { crearRouter, rutaDeLogin, type DefinicionRuta, type Router } from './shared/router';
import { crearStore, type Store } from './shared/store';

/** Datos de la sesión que usa el shell. La zona se actualiza al cargar la configuración (C-09). */
export interface EstadoSesion {
  readonly zonaHoraria: string;
}

export interface DependenciasApp {
  readonly fetch?: typeof fetch;
  /** Web Locks para serializar el refresh entre pestañas (D6). Sin dar: `navigator.locks`; `null`: sin soporte. */
  readonly locks?: LocksMinimos | null;
  /** Fábrica del canal del aviso de sesión cerrada (D11). Sin dar: `BroadcastChannel`; `null`: sin soporte. */
  readonly canalSesion?: FabricaDeCanal | null;
}

export interface Aplicacion {
  readonly router: Router;
  readonly http: ClienteHttp;
  /** El shell existe solo mientras hay sesión (D2). */
  readonly shell: Shell | null;
  readonly conectividad: Store<EstadoConectividad>;
  readonly sesion: Store<EstadoSesion>;
  /** Se resuelve cuando la primera ruta terminó de mostrarse. */
  readonly listo: Promise<void>;
  /** Guarda el access token de una sesión recién iniciada, sin datos de una sesión anterior. */
  iniciarSesion(accessToken: string): Promise<void>;
  cerrarSesion(): Promise<void>;
  detener(): void;
}

type Destino = { readonly tipo: 'privada'; readonly pagina: Pagina } | { readonly tipo: 'auth'; readonly pagina: PaginaAuth };

const privada = (patron: string, cargar: () => Promise<Pagina>): DefinicionRuta<Destino> => ({
  patron,
  cargar: async () => ({ tipo: 'privada', pagina: await cargar() }),
});
const publica = (patron: string, cargar: () => Promise<PaginaAuth>): DefinicionRuta<Destino> => ({
  patron,
  cargar: async () => ({ tipo: 'auth', pagina: await cargar() }),
});

const RUTA_ASISTENTE = '/app/bienvenida';

/** Cada ruta se carga con import() para dividir el bundle (C-06, design D1). */
const RUTAS: readonly DefinicionRuta<Destino>[] = [
  privada('/app/agenda', async () => (await import('./pages/agenda')).pagina),
  privada('/app/bloqueos', async () => (await import('./pages/bloqueos')).pagina),
  privada('/app/configuracion', async () => (await import('./pages/configuracion')).pagina),
  privada('/app/configuracion/prestaciones', async () => (await import('./pages/configuracion-prestaciones')).pagina),
  privada('/app/configuracion/horarios', async () => (await import('./pages/configuracion-horarios')).pagina),
  privada('/app/auditoria', async () => (await import('./pages/auditoria')).pagina),
  privada('/app/exportar', async () => (await import('./pages/exportar')).pagina),
  privada(RUTA_ASISTENTE, async () => (await import('./pages/bienvenida')).pagina),
  privada('/app/cuenta/contrasena', async () => (await import('./pages/cambiar-contrasena')).pagina),
  publica('/login', async () => (await import('./pages/login')).pagina),
  publica('/registro', async () => (await import('./pages/registro')).pagina),
  publica('/recuperar', async () => (await import('./pages/recuperar')).pagina),
  publica('/terminos', async () => (await import('./pages/legal')).terminos),
  publica('/privacidad', async () => (await import('./pages/legal')).privacidad),
];

const AVISO_POR_MOTIVO: Record<MotivoCierre, AvisoLogin> = {
  logout: { tipo: 'sesion-cerrada' },
  'contrasena-cambiada': { tipo: 'contrasena-cambiada' },
};

/** Monta la SPA del odontólogo: rutas públicas de auth, shell privado, cliente HTTP y estado de conexión. */
export function montarAplicacion(documento: Document, dependencias: DependenciasApp = {}): Aplicacion {
  const anfitrion = documento.querySelector<HTMLElement>('#app');
  if (anfitrion === null) {
    throw new Error('No se encontró el elemento #app');
  }

  const marca = nombreDeMarca();
  const sesion = crearStore<EstadoSesion>({ zonaHoraria: 'America/Argentina/Buenos_Aires' });
  const { store: conectividad, detener: dejarDeEscucharRed } = crearConectividad(window);
  const canal = dependencias.canalSesion === undefined ? crearCanalSesion() : crearCanalSesion(dependencias.canalSesion);

  let shell: Shell | null = null;
  let limpiarPaginaAuth: (() => void) | null = null;
  /** Aviso que muestra el login al llegar. Vive en memoria: puede llevar el email (D4: nunca en la URL). */
  let avisoPendiente: AvisoLogin | null = null;
  /**
   * La restauración falló por falta de red: se muestra el shell para leer la agenda guardada (pwa-offline). La
   * caché solo existe mientras hubo sesión; al volver la red, el primer pedido privado renueva o vence la sesión.
   */
  let sinRedAlRestaurar = false;

  // Las closures que usan `routerCreado` solo corren después de crearlo.
  const http = crearClienteHttp({
    ...(dependencias.fetch === undefined ? {} : { fetch: dependencias.fetch }),
    coordinarRefresh: dependencias.locks === undefined ? crearCoordinadorRefresh() : crearCoordinadorRefresh(dependencias.locks),
    alVencerSesion: () => {
      sinRedAlRestaurar = false;
      void olvidarAgendaGuardada();
      avisoPendiente = { tipo: 'sesion-vencida' };
      void routerCreado.navegar(rutaDeLogin(routerCreado.rutaActual()), { forzar: true });
    },
    alResponder: (ruta, respuesta) => registrarSincronizacion(conectividad, ruta, respuesta),
    alFallarRed: () => conectividad.set({ online: false }),
  });

  /**
   * La agenda en caché tiene datos de pacientes (Ley 25.326): no sobrevive a la sesión que la vio.
   * Se borra al cerrar sesión, al vencer y al iniciar otra. Un fallo al borrar no frena el flujo de sesión.
   */
  async function olvidarAgendaGuardada(): Promise<void> {
    await borrarCacheAgenda().catch(() => undefined);
  }

  async function iniciarSesion(accessToken: string): Promise<void> {
    await olvidarAgendaGuardada();
    sinRedAlRestaurar = false;
    http.establecerToken(accessToken);
  }

  /** Borra todo lo local de la sesión, avisa a las otras pestañas y lleva al login con el aviso del motivo. */
  async function terminarSesion(motivo: MotivoCierre): Promise<void> {
    http.borrarToken();
    sinRedAlRestaurar = false;
    await olvidarAgendaGuardada();
    canal.publicar(motivo);
    avisoPendiente = AVISO_POR_MOTIVO[motivo];
    await routerCreado.navegar('/login', { forzar: true });
  }

  async function cerrarSesion(): Promise<void> {
    // Sin conexión no se puede revocar el refresh, pero lo local se borra igual.
    await http.post('/api/v1/auth/logout').catch(() => undefined);
    await terminarSesion('logout');
  }

  // D11: otra pestaña cerró la sesión o cambió la contraseña.
  const dejarDeEscucharCanal = canal.alRecibir((motivo) => {
    http.borrarToken();
    sinRedAlRestaurar = false;
    void olvidarAgendaGuardada();
    if (!esRutaPrivada(location.pathname)) return;
    avisoPendiente = AVISO_POR_MOTIVO[motivo];
    void routerCreado.navegar('/login', { forzar: true });
  });

  function desmontarShell(): void {
    shell?.destruir();
    shell = null;
  }

  function limpiarAuth(): void {
    limpiarPaginaAuth?.();
    limpiarPaginaAuth = null;
  }

  function contextoPrivado(contenedor: HTMLElement, acciones: HTMLElement, params: Readonly<Record<string, string>>) {
    return {
      contenedor,
      acciones,
      params,
      http,
      zonaHoraria: sesion.get().zonaHoraria,
      cuenta: { terminarPorCambioDeContrasena: () => terminarSesion('contrasena-cambiada') },
      navegacion: routerCreado,
    };
  }

  function mostrarPrivada(pagina: Pagina, params: Readonly<Record<string, string>>, enfocar: boolean): void {
    limpiarAuth();
    if (pagina.sinShell === true) {
      // C-09, D2: el asistente ocupa la pantalla, sin la navegación del shell.
      desmontarShell();
      const contenedor = el('div', { 'data-sin-shell': true });
      (anfitrion as HTMLElement).replaceChildren(contenedor);
      documento.title = `${pagina.titulo} · ${marca}`;
      pagina.montar(contextoPrivado(contenedor, el('div', {}), params));
      return;
    }
    shell ??= montarShell(anfitrion as HTMLElement, {
      marca,
      conectividad,
      zonaHoraria: () => sesion.get().zonaHoraria,
      alCerrarSesion: () => void cerrarSesion(),
    });
    shell.mostrar(pagina, { enfocar });
    pagina.montar(contextoPrivado(shell.contenido, shell.acciones, params));
    shell.sincronizarConexion();
  }

  function mostrarAuth(pagina: PaginaAuth): void {
    limpiarAuth();
    desmontarShell();
    const { tarjeta } = montarLayoutAuth(anfitrion as HTMLElement, marca, pagina.conPanel === true, () => http.get<unknown>(RUTA_PLANES_PUBLICOS));
    documento.title = `${pagina.titulo} · ${marca}`;
    const contexto: ContextoAuth = {
      contenedor: tarjeta,
      http,
      marca,
      query: new URLSearchParams(location.search),
      conectividad,
      navegar: (ruta, opciones) => routerCreado.navegar(ruta, opciones),
      entrar: async (token, volver) => {
        await iniciarSesion(token);
        await routerCreado.navegar((await necesitaAsistente()) ? RUTA_ASISTENTE : sanearVolver(volver), { reemplazar: true });
      },
      tomarAviso: () => {
        const aviso = avisoPendiente;
        avisoPendiente = null;
        return aviso;
      },
      dejarAviso: (aviso) => {
        avisoPendiente = aviso;
      },
    };
    limpiarPaginaAuth = pagina.montar(contexto);
  }

  /**
   * C-09, D3: después del login y al restaurar la sesión, sin prestaciones activas se abre el asistente. No se guarda
   * una marca de "onboarding terminado": el criterio es el mismo dato que hace falta para dar turnos. Si la respuesta
   * no se puede interpretar o falla, no se interpone.
   */
  async function necesitaAsistente(): Promise<boolean> {
    try {
      const lista = await http.get<unknown>('/api/v1/prestaciones');
      return Array.isArray(lista) && !lista.some((p: unknown) => typeof p === 'object' && p !== null && (p as Record<string, unknown>)['activa'] !== false);
    } catch {
      return false;
    }
  }

  async function guarda({ pathname, ruta }: { readonly pathname: string; readonly ruta: string }): Promise<string | null> {
    const decision = decidirAcceso(pathname, http.tieneToken());
    if (decision.tipo === 'redirigir') return decision.a;
    if (decision.tipo === 'seguir' || sinRedAlRestaurar) return null;

    // D3: ruta privada sin token en memoria → restaurar con el refresh, sin aviso si no hay sesión.
    if (shell === null) montarSplash(anfitrion as HTMLElement);
    try {
      if (!(await http.renovarSesion({ avisarSiVence: false }))) return rutaDeLogin(ruta);
      return pathname !== RUTA_ASISTENTE && (await necesitaAsistente()) ? RUTA_ASISTENTE : null;
    } catch (error) {
      if (error instanceof ApiError && error.codigo === 'SIN_CONEXION') {
        sinRedAlRestaurar = true;
        return null;
      }
      return rutaDeLogin(ruta);
    }
  }

  let primeraVez = true;
  const routerCreado = crearRouter<Destino>({
    rutas: RUTAS,
    redirecciones: { '/': '/app/agenda', '/app': '/app/agenda', '/app/': '/app/agenda' },
    guarda,
    noEncontrada: async () => ({ tipo: 'privada', pagina: (await import('./pages/no-encontrada')).pagina }),
    mostrar: ({ pagina: destino, params }) => {
      if (destino.tipo === 'auth') mostrarAuth(destino.pagina);
      else mostrarPrivada(destino.pagina, params, !primeraVez);
      primeraVez = false;
    },
  });

  // Vuelve la conexión: la página actual vuelve a pedir sus datos (spec app-shell).
  const dejarDeObservarRed = conectividad.suscribir((actual, anterior) => {
    if (actual.online && !anterior.online && shell !== null) void routerCreado.recargar();
  });

  return {
    router: routerCreado,
    http,
    get shell() {
      return shell;
    },
    conectividad,
    sesion,
    listo: routerCreado.iniciar(),
    iniciarSesion,
    cerrarSesion,
    detener() {
      routerCreado.detener();
      dejarDeObservarRed();
      dejarDeEscucharRed();
      dejarDeEscucharCanal();
      canal.cerrar();
      limpiarAuth();
      desmontarShell();
    },
  };
}
