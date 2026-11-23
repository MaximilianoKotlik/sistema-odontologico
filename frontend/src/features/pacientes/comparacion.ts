/**
 * Qué datos difieren entre la ficha del paciente y lo escrito en la reserva (C-17, design D7). Es la misma regla que
 * `Paciente.datosDifierenDe` del backend, que decidió la marca: nombre y apellido sin distinguir mayúsculas, tildes
 * (NFD sin diacríticos) ni espacios extra; el email solo sin distinguir mayúsculas, porque el dominio lo guarda así.
 */

export type CampoComparado = 'nombre' | 'apellido' | 'email';

export interface DatosComparables {
  readonly nombre: string;
  readonly apellido: string;
  readonly email?: string | null;
}

export interface Comparacion {
  readonly difieren: readonly CampoComparado[];
  readonly coinciden: readonly CampoComparado[];
}

const CAMPOS: readonly CampoComparado[] = ['nombre', 'apellido', 'email'];

function comparable(texto: string): string {
  return texto.normalize('NFD').replace(/\p{M}+/gu, '').trim().replace(/\s+/g, ' ').toLowerCase();
}

export function mismoNombre(guardado: string, informado: string): boolean {
  return comparable(guardado) === comparable(informado);
}

function mismoEmail(guardado: string | null | undefined, informado: string | null | undefined): boolean {
  return (guardado ?? '').trim().toLowerCase() === (informado ?? '').trim().toLowerCase();
}

function igual(campo: CampoComparado, guardado: DatosComparables, informado: DatosComparables): boolean {
  return campo === 'email'
    ? mismoEmail(guardado.email, informado.email)
    : mismoNombre(guardado[campo], informado[campo]);
}

export function comparar(guardado: DatosComparables, informado: DatosComparables): Comparacion {
  return {
    difieren: CAMPOS.filter((campo) => !igual(campo, guardado, informado)),
    coinciden: CAMPOS.filter((campo) => igual(campo, guardado, informado)),
  };
}
