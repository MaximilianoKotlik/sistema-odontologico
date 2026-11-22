import { editableAMinutos, horizonteEnHoras, type FormularioConsultorio } from './unidades';

/**
 * Validaciones del cliente de Configuración (C-09, design D4 a D7). Repiten las reglas de C-08 para avisar antes de
 * enviar; el backend sigue siendo la autoridad (por ejemplo, las palabras reservadas del slug solo las conoce él).
 * Textos de los mockups aprobados (#23).
 */

export type CampoConsultorio = keyof FormularioConsultorio;

export interface ErrorDeCampo {
  readonly campo: CampoConsultorio;
  readonly mensaje: string;
}

export interface FranjaEditable {
  readonly diaSemana: number;
  readonly horaInicio: string;
  readonly horaFin: string;
}

export interface ErrorDeDia {
  readonly diaSemana: number;
  readonly mensaje: string;
}

const SLUG_MINIMO = 3;
const SLUG_MAXIMO = 40;
// Mismo patrón que ValidadorDeSlug y ck_consultorio_slug (RN-TE-03).
const FORMATO_SLUG = /^([a-z0-9])(-?[a-z0-9])+$/;
const NOMBRE_MAXIMO = 120;
const CANCELACION_MAXIMA_HORAS = 168;
const HORIZONTE_MAXIMO_DIAS = 365;
export const PASOS_PERMITIDOS: readonly number[] = [5, 10, 15, 20, 30];
export const DURACION_MINIMA = 5;
export const DURACION_MAXIMA = 480;
const MULTIPLO = 5;

/** Mientras se escribe: minúsculas, espacios a guiones y sin tildes; descarta lo demás (D5). */
export function normalizarSlug(texto: string): string {
  return texto
    .normalize('NFD')
    .replace(/\p{M}+/gu, '')
    .toLowerCase()
    .replace(/\s+/g, '-')
    .replace(/[^a-z0-9-]/g, '');
}

export function validarSlug(slug: string): string | null {
  if (slug.length < SLUG_MINIMO || slug.length > SLUG_MAXIMO || !FORMATO_SLUG.test(slug)) {
    return 'Usá de 3 a 40 letras minúsculas, números o guiones, sin guiones al principio, al final ni dos seguidos.';
  }
  return null;
}

export function validarDuracion(minutos: number): string | null {
  if (!Number.isInteger(minutos) || minutos < DURACION_MINIMA || minutos > DURACION_MAXIMA) return 'Poné una duración entre 5 y 480 minutos.';
  if (minutos % MULTIPLO !== 0) return 'Usá múltiplos de 5 minutos (por ejemplo 45 o 50).';
  return null;
}

const esEnteroEntre = (valor: number, minimo: number, maximo: number) => Number.isInteger(valor) && valor >= minimo && valor <= maximo;

/** Errores de formato del consultorio, en el orden del formulario (para el resumen de errores). */
export function validarConsultorio(formulario: FormularioConsultorio): ErrorDeCampo[] {
  const errores: ErrorDeCampo[] = [];
  const nombre = formulario.nombre.trim();
  if (nombre === '') errores.push({ campo: 'nombre', mensaje: 'Poné el nombre del consultorio.' });
  else if (nombre.length > NOMBRE_MAXIMO) errores.push({ campo: 'nombre', mensaje: 'El nombre puede tener hasta 120 caracteres.' });

  const errorDeSlug = validarSlug(formulario.slug);
  if (errorDeSlug !== null) errores.push({ campo: 'slug', mensaje: errorDeSlug });

  if (formulario.telefonoContacto.trim() === '') {
    errores.push({
      campo: 'telefonoContacto',
      mensaje: 'Poné un teléfono de contacto: es lo que ven tus pacientes cuando no pueden cancelar por el link o si pausás la reserva.',
    });
  }

  const cancelacion = editableAMinutos(formulario.anticipacionCancelacion);
  if (!esEnteroEntre(cancelacion, 0, CANCELACION_MAXIMA_HORAS * 60)) {
    errores.push({ campo: 'anticipacionCancelacion', mensaje: 'La anticipación para cancelar va de 0 a 168 horas (7 días).' });
  }

  const horizonteValido = esEnteroEntre(formulario.horizonteReservaDias, 1, HORIZONTE_MAXIMO_DIAS);
  const reserva = editableAMinutos(formulario.anticipacionReserva);
  if (!Number.isInteger(reserva) || reserva < 0) {
    errores.push({ campo: 'anticipacionReserva', mensaje: 'La anticipación para reservar tiene que ser 0 o más horas.' });
  } else if (horizonteValido && reserva >= formulario.horizonteReservaDias * 24 * 60) {
    const dias = formulario.horizonteReservaDias;
    const horas = horizonteEnHoras(dias);
    errores.push({
      campo: 'anticipacionReserva',
      mensaje: `Tiene que ser menor que el horizonte (${dias} ${dias === 1 ? 'día' : 'días'} = ${horas} horas). Si no, no queda ningún horario para reservar.`,
    });
  }

  if (!horizonteValido) errores.push({ campo: 'horizonteReservaDias', mensaje: 'El horizonte va de 1 a 365 días.' });
  if (!PASOS_PERMITIDOS.includes(formulario.pasoHuecosMin)) {
    errores.push({ campo: 'pasoHuecosMin', mensaje: 'Elegí un paso de 5, 10, 15, 20 o 30 minutos.' });
  }

  return ordenarComoElFormulario(errores);
}

const ORDEN_DEL_FORMULARIO: readonly CampoConsultorio[] = [
  'nombre',
  'slug',
  'direccion',
  'telefonoContacto',
  'anticipacionCancelacion',
  'anticipacionReserva',
  'horizonteReservaDias',
  'pasoHuecosMin',
  'reservaPublicaActiva',
  'zonaHoraria',
];

function ordenarComoElFormulario(errores: ErrorDeCampo[]): ErrorDeCampo[] {
  return [...errores].sort((a, b) => ORDEN_DEL_FORMULARIO.indexOf(a.campo) - ORDEN_DEL_FORMULARIO.indexOf(b.campo));
}

const HORA = /^([01]\d|2[0-3]):([0-5]\d)$/;

function aMinutosDelDia(hora: string): number | null {
  const partes = HORA.exec(hora);
  if (partes === null) return null;
  return Number(partes[1]) * 60 + Number(partes[2]);
}

/**
 * Grilla semanal (D7): intervalos semiabiertos [inicio, fin), así que las franjas contiguas son válidas. Un error por
 * día como máximo, con el primer problema que se encuentra; el número de franja es su posición en el editor.
 */
export function validarGrilla(franjas: readonly FranjaEditable[]): ErrorDeDia[] {
  const errores: ErrorDeDia[] = [];
  const dias = [...new Set(franjas.map((f) => f.diaSemana))].sort((a, b) => a - b);

  for (const dia of dias) {
    const delDia = franjas.filter((f) => f.diaSemana === dia);
    const mensaje = problemaDelDia(delDia);
    if (mensaje !== null) errores.push({ diaSemana: dia, mensaje });
  }
  return errores;
}

function problemaDelDia(franjas: readonly FranjaEditable[]): string | null {
  const intervalos: { inicio: number; fin: number; franja: FranjaEditable }[] = [];
  for (const [indice, franja] of franjas.entries()) {
    const numero = indice + 1;
    const inicio = aMinutosDelDia(franja.horaInicio);
    const fin = aMinutosDelDia(franja.horaFin);
    if (inicio === null || fin === null) return `Completá las dos horas de la franja ${numero}.`;
    if (inicio % MULTIPLO !== 0 || fin % MULTIPLO !== 0) return `Usá horas en múltiplos de 5 minutos en la franja ${numero}.`;
    if (fin <= inicio) return `La franja ${numero} termina antes de empezar. Poné una hora de fin posterior al inicio.`;
    intervalos.push({ inicio, fin, franja });
  }

  const ordenados = [...intervalos].sort((a, b) => a.inicio - b.inicio);
  for (let i = 1; i < ordenados.length; i += 1) {
    const anterior = ordenados[i - 1];
    const actual = ordenados[i];
    if (anterior !== undefined && actual !== undefined && actual.inicio < anterior.fin) {
      return `Las franjas ${rango(anterior.franja)} y ${rango(actual.franja)} se superponen. Cambiá una de las dos.`;
    }
  }
  return null;
}

const rango = (franja: FranjaEditable) => `${franja.horaInicio}–${franja.horaFin}`;
