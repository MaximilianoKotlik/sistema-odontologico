import type { ClienteHttp } from '../../shared/http';
import { cantidadParaRevisar } from './api';

/**
 * La cantidad de pacientes con revisión pendiente (C-17, design D3). La pide el shell al iniciar la sesión y la
 * vuelve a pedir cada revisión; la leen la navegación (contador) y la agenda (aviso). Vive en memoria y se olvida
 * con la sesión: no se guarda en el dispositivo.
 */
export interface ParaRevisar {
  /** `null` mientras no se pidió o después de olvidarla. */
  cantidad(): number | null;
  /** Vuelve a pedirla; si el pedido falla, conserva la anterior. */
  actualizar(): Promise<void>;
  suscribir(fn: (cantidad: number) => void): () => void;
  olvidar(): void;
}

export function crearParaRevisar(http: ClienteHttp): ParaRevisar {
  let actual: number | null = null;
  const suscriptores = new Set<(cantidad: number) => void>();
  const avisar = (cantidad: number) => {
    for (const fn of [...suscriptores]) fn(cantidad);
  };

  return {
    cantidad: () => actual,
    async actualizar() {
      try {
        actual = await cantidadParaRevisar(http);
      } catch {
        return;
      }
      avisar(actual);
    },
    suscribir(fn) {
      suscriptores.add(fn);
      return () => {
        suscriptores.delete(fn);
      };
    },
    olvidar() {
      actual = null;
      avisar(0);
    },
  };
}
