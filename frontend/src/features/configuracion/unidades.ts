import type { components } from '../../shared/api-types';

/**
 * Unidades legibles del formulario del consultorio (C-09, design D4): anticipaciones en horas, horizonte en días.
 * Un valor en minutos que no es una hora entera se muestra en minutos, para no redondear en silencio.
 */

export type ConfiguracionResponse = components['schemas']['ConfiguracionResponse'];
export type ConfiguracionRequest = components['schemas']['ConfiguracionRequest'];

export interface CantidadEditable {
  readonly valor: number;
  readonly unidad: 'horas' | 'minutos';
}

export interface FormularioConsultorio {
  readonly nombre: string;
  readonly slug: string;
  readonly direccion: string;
  readonly telefonoContacto: string;
  readonly zonaHoraria: string;
  readonly anticipacionCancelacion: CantidadEditable;
  readonly anticipacionReserva: CantidadEditable;
  readonly horizonteReservaDias: number;
  readonly pasoHuecosMin: number;
  readonly reservaPublicaActiva: boolean;
}

const MINUTOS_POR_HORA = 60;
const HORAS_POR_DIA = 24;

export function minutosAEditable(minutos: number): CantidadEditable {
  return minutos % MINUTOS_POR_HORA === 0 ? { valor: minutos / MINUTOS_POR_HORA, unidad: 'horas' } : { valor: minutos, unidad: 'minutos' };
}

export function editableAMinutos(cantidad: CantidadEditable): number {
  return cantidad.unidad === 'horas' ? cantidad.valor * MINUTOS_POR_HORA : cantidad.valor;
}

export function horizonteEnHoras(dias: number): number {
  return dias * HORAS_POR_DIA;
}

/** Valores por defecto de un consultorio nuevo (C-08, `ReglasDeReserva.POR_DEFECTO`), si la respuesta no los trae. */
const POR_DEFECTO = { cancelacion: 1440, reserva: 120, horizonte: 60, paso: 15, zona: 'America/Argentina/Buenos_Aires' } as const;

export function formularioDesdeRespuesta(respuesta: ConfiguracionResponse): FormularioConsultorio {
  return {
    nombre: respuesta.nombre ?? '',
    slug: respuesta.slug ?? '',
    direccion: respuesta.direccion ?? '',
    telefonoContacto: respuesta.telefonoContacto ?? '',
    zonaHoraria: respuesta.zonaHoraria ?? POR_DEFECTO.zona,
    anticipacionCancelacion: minutosAEditable(respuesta.anticipacionCancelacionMin ?? POR_DEFECTO.cancelacion),
    anticipacionReserva: minutosAEditable(respuesta.anticipacionReservaMin ?? POR_DEFECTO.reserva),
    horizonteReservaDias: respuesta.horizonteReservaDias ?? POR_DEFECTO.horizonte,
    pasoHuecosMin: respuesta.pasoHuecosMin ?? POR_DEFECTO.paso,
    reservaPublicaActiva: respuesta.reservaPublicaActiva ?? true,
  };
}

export function solicitudDesdeFormulario(formulario: FormularioConsultorio): ConfiguracionRequest {
  const direccion = formulario.direccion.trim();
  return {
    nombre: formulario.nombre.trim(),
    slug: formulario.slug,
    ...(direccion === '' ? {} : { direccion }),
    telefonoContacto: formulario.telefonoContacto.trim(),
    zonaHoraria: formulario.zonaHoraria,
    anticipacionCancelacionMin: editableAMinutos(formulario.anticipacionCancelacion),
    anticipacionReservaMin: editableAMinutos(formulario.anticipacionReserva),
    horizonteReservaDias: formulario.horizonteReservaDias,
    pasoHuecosMin: formulario.pasoHuecosMin,
    reservaPublicaActiva: formulario.reservaPublicaActiva,
  };
}
