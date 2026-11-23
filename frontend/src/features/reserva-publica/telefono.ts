/**
 * El teléfono del consultorio llega en E.164 (C-08 lo normaliza). Un número de CABA se muestra como se escribe acá
 * ("11 0000-0001"); para el resto no se adivina el largo de la característica y queda en E.164.
 */
export function telefonoLegible(e164: string): string {
  const caba = /^\+549?11(\d{4})(\d{4})$/.exec(e164);
  return caba === null ? e164 : `11 ${caba[1] ?? ''}-${caba[2] ?? ''}`;
}

export function hrefTelefono(telefono: string): string {
  return `tel:${telefono.replace(/[^\d+]/g, '')}`;
}
