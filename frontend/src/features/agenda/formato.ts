/** Formatos solo de lectura para datos del paciente (detalle de C-13 y formularios de C-14). Los valores se envían tal como los guarda el backend. */

const formatoDni = new Intl.NumberFormat('es-AR', { useGrouping: true, maximumFractionDigits: 0 });

/** "30.000.010"; si no son solo dígitos, tal cual. */
export const dniLegible = (dni: string) => (/^\d+$/.test(dni) ? formatoDni.format(Number(dni)) : dni);

/** Celular argentino en E.164 (`+549AABBBBCCCC`) como "+54 9 11 0000-0010"; otro formato, tal cual. */
export function telefonoLegible(telefono: string): string {
  const partes = /^\+549(\d{2})(\d{4})(\d{4})$/.exec(telefono);
  return partes === null ? telefono : `+54 9 ${partes[1] ?? ''} ${partes[2] ?? ''}-${partes[3] ?? ''}`;
}
