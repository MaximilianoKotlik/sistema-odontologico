import { crearStore } from '../../shared/store';

/**
 * Estado del plan del consultorio en memoria (C-20, design D7): lo pide el shell al montarse y otra vez tras un 403
 * `PLAN_VENCIDO`. Los días y el "solo lectura" los calcula el backend (RN-GL-04); el frontend solo los muestra.
 */

export const RUTA_PLAN = '/api/v1/plan';

export type Plan = 'PRUEBA' | 'ACTIVO' | 'VENCIDO';

export interface EstadoPlan {
  readonly plan: Plan;
  /** Último día de prueba (`YYYY-MM-DD`), inclusive. */
  readonly pruebaHasta: string | null;
  readonly diasDePruebaRestantes: number | null;
  readonly soloLectura: boolean;
}

export interface EstadoDelPlan {
  /** Pide el plan; si falla (sin red, error), deja el estado como estaba. */
  cargar(): Promise<void>;
  get(): EstadoPlan | null;
  soloLectura(): boolean;
  suscribir(fn: (estado: EstadoPlan | null) => void): () => void;
  /** Al cerrar o vencer la sesión. */
  olvidar(): void;
}

const PLANES: readonly string[] = ['PRUEBA', 'ACTIVO', 'VENCIDO'];

const esTextoONulo = (valor: unknown): valor is string | null => valor === null || typeof valor === 'string';
const esEnteroONulo = (valor: unknown): valor is number | null => valor === null || Number.isInteger(valor);

export function esEstadoPlan(valor: unknown): valor is EstadoPlan {
  if (typeof valor !== 'object' || valor === null) return false;
  const v = valor as Record<string, unknown>;
  return (
    typeof v['plan'] === 'string' &&
    PLANES.includes(v['plan']) &&
    esTextoONulo(v['pruebaHasta'] ?? null) &&
    esEnteroONulo(v['diasDePruebaRestantes'] ?? null) &&
    typeof v['soloLectura'] === 'boolean'
  );
}

function normalizar(valor: EstadoPlan): EstadoPlan {
  return {
    plan: valor.plan,
    pruebaHasta: valor.pruebaHasta ?? null,
    diasDePruebaRestantes: valor.diasDePruebaRestantes ?? null,
    soloLectura: valor.soloLectura,
  };
}

/** Lo único que necesita del cliente HTTP; la respuesta se valida con {@link esEstadoPlan}. */
export interface LectorDelPlan {
  get(ruta: string): Promise<unknown>;
}

export function crearEstadoDelPlan(http: LectorDelPlan): EstadoDelPlan {
  const store = crearStore<{ estado: EstadoPlan | null }>({ estado: null });

  return {
    async cargar() {
      const respuesta = await http.get(RUTA_PLAN).catch(() => undefined);
      if (esEstadoPlan(respuesta)) store.set({ estado: normalizar(respuesta) });
    },
    get: () => store.get().estado,
    soloLectura: () => store.get().estado?.soloLectura === true,
    suscribir: (fn) => store.suscribir(({ estado }) => fn(estado)),
    olvidar: () => store.set({ estado: null }),
  };
}
