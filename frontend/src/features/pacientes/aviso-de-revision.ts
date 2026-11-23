/**
 * El aviso "Listo: … quedó revisada" que la revisión deja para la lista al volver (C-17). Vive en memoria y se toma
 * una sola vez: nunca va en la URL, porque lleva el nombre del paciente (RN-GL-01).
 */
let pendiente: string | null = null;

export function dejarAvisoDeRevision(texto: string): void {
  pendiente = texto;
}

export function tomarAvisoDeRevision(): string | null {
  const texto = pendiente;
  pendiente = null;
  return texto;
}
