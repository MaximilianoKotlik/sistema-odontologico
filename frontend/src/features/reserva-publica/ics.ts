/**
 * Archivo de calendario (.ics) generado en el cliente, sin librerías (C-16, design D6; DD-26).
 * Todo texto que viene del consultorio o de la API se escapa (RFC 5545 §3.3.11) y las líneas se pliegan a 75 octetos
 * (§3.1), para que ningún dato pueda agregar propiedades al evento.
 */

export interface EventoDeCalendario {
  /** Id del turno: se usa para el UID, así volver a descargarlo actualiza el mismo evento. */
  readonly uid: string;
  readonly inicio: string;
  readonly fin: string;
  readonly titulo: string;
  readonly ubicacion: string;
  readonly descripcion: string;
  readonly ahora?: Date;
}

const CRLF = '\r\n';
const MAXIMO_OCTETOS = 75;
const codificador = new TextEncoder();

/** `\` primero (si no, se escaparían las barras que agregan los otros reemplazos), después `;`, `,` y saltos de línea. */
export function escaparTexto(texto: string): string {
  return texto.replace(/\\/g, '\\\\').replace(/;/g, '\\;').replace(/,/g, '\\,').replace(/\r\n|\n|\r/g, '\\n');
}

/** Corta en 75 octetos UTF-8 sin partir un carácter; cada continuación empieza con un espacio, que también cuenta. */
export function plegarLinea(linea: string): string {
  if (codificador.encode(linea).length <= MAXIMO_OCTETOS) return linea;
  const partes: string[] = [];
  let actual = '';
  let octetos = 0;
  for (const caracter of linea) {
    const largo = codificador.encode(caracter).length;
    const limite = partes.length === 0 ? MAXIMO_OCTETOS : MAXIMO_OCTETOS - 1;
    if (octetos + largo > limite) {
      partes.push(actual);
      actual = '';
      octetos = 0;
    }
    actual += caracter;
    octetos += largo;
  }
  partes.push(actual);
  return partes.join(`${CRLF} `);
}

/** `2026-11-24T13:30:00Z` → `20261124T133000Z`. */
function fechaUtc(instante: string | Date): string {
  const fecha = instante instanceof Date ? instante : new Date(instante);
  return fecha.toISOString().replace(/[-:]/g, '').replace(/\.\d{3}/, '');
}

export function generarIcs(evento: EventoDeCalendario): string {
  const lineas = [
    'BEGIN:VCALENDAR',
    'VERSION:2.0',
    'PRODID:-//Turnos online//Reserva publica//ES',
    'CALSCALE:GREGORIAN',
    'METHOD:PUBLISH',
    'BEGIN:VEVENT',
    `UID:${escaparTexto(evento.uid)}@turnos`,
    `DTSTAMP:${fechaUtc(evento.ahora ?? new Date())}`,
    `DTSTART:${fechaUtc(evento.inicio)}`,
    `DTEND:${fechaUtc(evento.fin)}`,
    `SUMMARY:${escaparTexto(evento.titulo)}`,
    `LOCATION:${escaparTexto(evento.ubicacion)}`,
    `DESCRIPTION:${escaparTexto(evento.descripcion)}`,
    'END:VEVENT',
    'END:VCALENDAR',
  ];
  return lineas.map(plegarLinea).join(CRLF) + CRLF;
}
