/** Store observable mínimo (design D3). */
export type Suscriptor<S> = (actual: Readonly<S>, anterior: Readonly<S>) => void;

export interface Store<S extends object> {
  get(): Readonly<S>;
  set(cambios: Partial<S>): void;
  suscribir(fn: Suscriptor<S>): () => void;
}

export function crearStore<S extends object>(inicial: S): Store<S> {
  let estado: Readonly<S> = Object.freeze({ ...inicial });
  const suscriptores = new Set<Suscriptor<S>>();

  return {
    get: () => estado,
    set(cambios) {
      const claves = Object.keys(cambios) as (keyof S)[];
      if (claves.every((clave) => Object.is(estado[clave], cambios[clave]))) return;
      const anterior = estado;
      estado = Object.freeze({ ...estado, ...cambios });
      for (const fn of [...suscriptores]) fn(estado, anterior);
    },
    suscribir(fn) {
      suscriptores.add(fn);
      return () => {
        suscriptores.delete(fn);
      };
    },
  };
}
