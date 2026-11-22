import { horaLocal, instanteLocal } from '../agenda/tiempo';

/**
 * Validaciones de los formularios de turnos (C-14, design D3, D4 y D7): funciones puras que evitan mandar al backend lo
 * que ya se sabe inválido. El backend sigue siendo la autoridad (solapamientos, disponibilidad, bloqueos).
 */

export const DURACION_MINIMA = 5;
export const DURACION_MAXIMA = 480;
export const PASO_MINUTOS = 5;
export const LARGO_NOTA = 500;
export const LARGO_MOTIVO_CANCELACION = 200;
export const LARGO_NOMBRE = 80;

/** Fin derivado (RN-AG-12): nunca se escribe, se calcula con la duración. */
export function finDerivado(dia: string, hora: string, duracionMin: number, zona: string): { readonly instante: string; readonly hora: string } {
  const fin = new Date(instanteLocal(dia, hora, zona).getTime() + duracionMin * 60000);
  return { instante: fin.toISOString(), hora: horaLocal(fin, zona) };
}

const HORA = /^(\d{2}):(\d{2})$/;

export function validarHora(hora: string): string | null {
  const partes = HORA.exec(hora);
  if (partes === null) return 'Elegí la hora de inicio.';
  return Number(partes[2]) % PASO_MINUTOS === 0 ? null : 'Elegí una hora en pasos de 5 minutos, por ejemplo 10:10 o 10:15.';
}

export function validarDuracion(minutos: number): string | null {
  return Number.isInteger(minutos) && minutos >= DURACION_MINIMA && minutos <= DURACION_MAXIMA && minutos % PASO_MINUTOS === 0
    ? null
    : 'La duración va de 5 a 480 minutos, en pasos de 5.';
}

export const ajustarDuracion = (minutos: number, delta: number) => Math.min(DURACION_MAXIMA, Math.max(DURACION_MINIMA, minutos + delta));

export const normalizarDni = (dni: string) => dni.replace(/[.\s]/g, '');

export function validarDni(dni: string): string | null {
  const normalizado = normalizarDni(dni);
  if (normalizado === '') return 'Escribí el DNI del paciente.';
  return /^\d{7,8}$/.test(normalizado) ? null : 'El DNI tiene 7 u 8 números, sin puntos.';
}

/** Chequeo de plausibilidad; el backend normaliza a E.164 y responde `TELEFONO_INVALIDO` si no es posible. */
export function validarCelular(telefono: string): string | null {
  if (telefono.trim() === '') return 'Escribí el celular del paciente.';
  const digitos = telefono.replace(/\D/g, '');
  return digitos.length >= 8 && digitos.length <= 15 ? null : 'Revisá el celular: poné el código de área, por ejemplo 11 0000-0001.';
}

export interface PacienteNuevo {
  readonly nombre: string;
  readonly apellido: string;
  readonly dni: string;
  readonly telefono: string;
  readonly email: string;
}

export type CampoPaciente = keyof PacienteNuevo;

export interface ErrorDeCampo<C extends string = string> {
  readonly campo: C;
  readonly mensaje: string;
}

function validarNombre(texto: string, vacio: string): string | null {
  const recortado = texto.trim();
  if (recortado === '') return vacio;
  return recortado.length > LARGO_NOMBRE ? 'Puede tener hasta 80 caracteres.' : null;
}

const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

export function validarPacienteNuevo(paciente: PacienteNuevo): ErrorDeCampo<CampoPaciente>[] {
  const errores: [CampoPaciente, string | null][] = [
    ['nombre', validarNombre(paciente.nombre, 'Escribí el nombre del paciente.')],
    ['apellido', validarNombre(paciente.apellido, 'Escribí el apellido del paciente.')],
    ['dni', validarDni(paciente.dni)],
    ['telefono', validarCelular(paciente.telefono)],
    ['email', paciente.email.trim() === '' || EMAIL.test(paciente.email.trim()) ? null : 'Revisá el email, por ejemplo paciente@example.com.'],
  ];
  return errores.flatMap(([campo, mensaje]) => (mensaje === null ? [] : [{ campo, mensaje }]));
}

const deMas = (sobra: number) => (sobra === 1 ? '1 carácter de más' : `${sobra} caracteres de más`);

export const validarNota = (nota: string) => (nota.length > LARGO_NOTA ? deMas(nota.length - LARGO_NOTA) : null);

export const validarMotivoCancelacion = (motivo: string) =>
  motivo.trim().length > LARGO_MOTIVO_CANCELACION ? 'El motivo puede tener hasta 200 caracteres.' : null;

/** Contador de caracteres (D9): se anuncia cada 50 y al pasarse, no en cada tecla. */
export function estadoDelContador(largo: number, maximo: number): { readonly texto: string; readonly excedido: boolean; readonly anunciar: string | null } {
  const excedido = largo > maximo;
  const anunciar = excedido ? deMas(largo - maximo) : largo > 0 && largo % 50 === 0 ? `Quedan ${maximo - largo} caracteres` : null;
  return { texto: `${largo} / ${maximo}`, excedido, anunciar };
}
