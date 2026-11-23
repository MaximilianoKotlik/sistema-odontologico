import { formatearDia } from '../../shared/fechas';
import type { ApiError } from '../../shared/http';
import { franjasDelDia, nombreCompleto, type BloqueoAgenda, type FranjaDisponible, type TurnoAgenda } from '../agenda/modelo';
import { diaLocal, horaDeMinutos, horaLocal } from '../agenda/tiempo';
import type { ErrorDeCampo } from './validaciones';

/**
 * Códigos de error de C-12 → mensaje, campo y salida (C-14, design D5; textos de los mockups aprobados en #31). Todos
 * los mensajes están acá: si C-12 cambia un código, se ajusta un solo archivo. Nunca se muestra el código crudo.
 *
 * C-12 no manda datos del paciente en los 409 (D6): el nombre sale de la agenda que ya tiene cargada el formulario.
 */

export type Operacion = 'crear' | 'reprogramar' | 'duracion' | 'estado' | 'nota';

/** Salida que ofrece el aviso: crear como sobreturno, ver la versión actual, recargar o reintentar. */
export type SalidaDeError = 'SOBRETURNO' | 'VER_ACTUALIZADO' | 'RECARGAR' | 'REINTENTAR';

export interface ContextoDeError {
  readonly operacion: Operacion;
  readonly zona: string;
  /** Día local del horario pedido (`YYYY-MM-DD`). */
  readonly dia: string;
  readonly turnos: readonly TurnoAgenda[];
  readonly bloqueos: readonly BloqueoAgenda[];
  /** Horario de atención; null si no se pudo cargar. */
  readonly franjas: readonly FranjaDisponible[] | null;
  readonly duracion?: { readonly nueva: number; readonly anterior: number; readonly finNuevo: string };
}

export interface ErrorDeTurno {
  readonly tono: 'advertencia' | 'peligro';
  readonly titulo: string;
  readonly texto: string;
  readonly accion: SalidaDeError | null;
  readonly campos: readonly ErrorDeCampo[];
}

const aviso = (tono: ErrorDeTurno['tono'], titulo: string, texto: string, accion: SalidaDeError | null = null): ErrorDeTurno => ({ tono, titulo, texto, accion, campos: [] });

const enCampos = (campos: readonly ErrorDeCampo[]): ErrorDeTurno => ({ tono: 'peligro', titulo: 'Revisá los datos marcados', texto: '', accion: null, campos });
const enCampo = (campo: string, mensaje: string) => enCampos([{ campo, mensaje }]);

const esObjeto = (valor: unknown): valor is Record<string, unknown> => typeof valor === 'object' && valor !== null;
const esTexto = (valor: unknown): valor is string => typeof valor === 'string' && valor !== '';
const textos = (valor: unknown): string[] => (Array.isArray(valor) ? valor.filter(esTexto) : []);

const nombreDelDia = (dia: string, zona: string) => formatearDia(dia, zona).split(' ')[0] ?? '';

function textoDeAtencion(contexto: ContextoDeError): string | null {
  if (contexto.franjas === null) return null;
  const tramos = franjasDelDia(contexto.dia, contexto.franjas);
  const dia = nombreDelDia(contexto.dia, contexto.zona);
  if (tramos.length === 0) return `El ${dia} no atendés.`;
  const rangos = tramos.map((t) => `de ${horaDeMinutos(t.desde)} a ${horaDeMinutos(t.hasta)}`);
  const ultimo = rangos.pop() ?? '';
  return `El ${dia} atendés ${rangos.length === 0 ? ultimo : `${rangos.join(', ')} y ${ultimo}`}.`;
}

const juntar = (...partes: (string | null)[]) => partes.filter((p): p is string => p !== null && p !== '').join(' ');

const mantieneDuracion = (contexto: ContextoDeError) =>
  contexto.duracion === undefined ? 'La duración no cambió.' : `Con ${contexto.duracion.nueva} min terminaría a las ${contexto.duracion.finNuevo}. La duración sigue en ${contexto.duracion.anterior} min.`;

/** "el turno de 10:30 de Carla Ficticia", "el turno de 10:30" o null, con la agenda cargada. */
function turnoQueChoca(error: ApiError, contexto: ContextoDeError): { readonly hora: string; readonly paciente: string | null } | null {
  const conflicto = error.datos['turnoEnConflicto'];
  if (!esObjeto(conflicto) || !esTexto(conflicto['inicio'])) return null;
  const encontrado = contexto.turnos.find((t) => t.id === conflicto['id']);
  return { hora: horaLocal(conflicto['inicio'], contexto.zona), paciente: encontrado === undefined ? null : nombreCompleto(encontrado.paciente) };
}

function solapado(error: ApiError, contexto: ContextoDeError): ErrorDeTurno {
  const choca = turnoQueChoca(error, contexto);
  if (contexto.operacion === 'duracion') {
    const titulo = choca === null ? 'No alcanza: choca con otro turno' : `No alcanza: a las ${choca.hora} empieza ${choca.paciente === null ? 'otro turno' : `el turno de ${choca.paciente}`}`;
    return aviso('advertencia', titulo, mantieneDuracion(contexto));
  }
  const titulo = choca === null ? 'Se superpone con otro turno' : `Se superpone con el turno de ${choca.hora}${choca.paciente === null ? '' : ` de ${choca.paciente}`}`;
  return contexto.operacion === 'crear'
    ? aviso('advertencia', titulo, 'Elegí otro horario o crealo como sobreturno.', 'SOBRETURNO')
    : aviso('advertencia', titulo, 'Elegí otro hueco libre. Si es una urgencia, podés crearlo como sobreturno desde la agenda.');
}

function fueraDeDisponibilidad(contexto: ContextoDeError): ErrorDeTurno {
  if (contexto.operacion === 'duracion') return aviso('advertencia', 'No alcanza: terminaría fuera de tu horario de atención', mantieneDuracion(contexto));
  const titulo = 'Ese horario está fuera de tu horario de atención';
  return contexto.operacion === 'crear'
    ? aviso('advertencia', titulo, juntar(textoDeAtencion(contexto), 'Podés crearlo como sobreturno.'), 'SOBRETURNO')
    : aviso('advertencia', titulo, juntar(textoDeAtencion(contexto), 'Elegí otro hueco libre.'));
}

function textoDelBloqueo(bloqueo: BloqueoAgenda, zona: string): string {
  const que = bloqueo.motivo === null ? 'un bloqueo' : `“${bloqueo.motivo}”`;
  if (diaLocal(bloqueo.inicio, zona) === diaLocal(bloqueo.fin, zona)) return `De ${horaLocal(bloqueo.inicio, zona)} a ${horaLocal(bloqueo.fin, zona)} tenés ${que}.`;
  return `Hasta el ${formatearDia(bloqueo.fin, zona)} a las ${horaLocal(bloqueo.fin, zona)} tenés ${que}.`;
}

function bloqueado(error: ApiError, contexto: ContextoDeError): ErrorDeTurno {
  const ids = textos(error.datos['bloqueosEnConflicto']);
  const bloqueo = contexto.bloqueos.find((b) => ids.includes(b.id));
  const delBloqueo = bloqueo === undefined ? null : textoDelBloqueo(bloqueo, contexto.zona);
  if (contexto.operacion === 'duracion') return aviso('peligro', 'No alcanza: choca con un bloqueo', juntar(delBloqueo, mantieneDuracion(contexto)));
  return aviso('peligro', 'Ese horario está bloqueado', juntar(delBloqueo, 'Los bloqueos no admiten turnos ni sobreturnos.'));
}

/** Campos del contrato (`pacienteNuevo.dni`, `inicio`…) → campos del formulario. */
const CAMPO_DEL_FORMULARIO: Readonly<Record<string, string>> = {
  'pacienteNuevo.nombre': 'nombre',
  'pacienteNuevo.apellido': 'apellido',
  'pacienteNuevo.dni': 'dni',
  'pacienteNuevo.telefono': 'telefono',
  'pacienteNuevo.email': 'email',
  pacienteNuevo: 'paciente',
  pacienteId: 'paciente',
  prestacionId: 'prestacion',
  inicio: 'hora',
  duracionMin: 'duracion',
};

const MENSAJE_POR_CAMPO: Readonly<Record<string, string>> = {
  nombre: 'Revisá el nombre: es obligatorio y puede tener hasta 80 caracteres.',
  apellido: 'Revisá el apellido: es obligatorio y puede tener hasta 80 caracteres.',
  dni: 'El DNI tiene 7 u 8 números, sin puntos.',
  telefono: 'Revisá el celular: poné el código de área, por ejemplo 11 0000-0001.',
  email: 'Revisá el email, por ejemplo paciente@example.com.',
  paciente: 'Elegí un paciente o cargá uno nuevo.',
  prestacion: 'Elegí una prestación.',
  hora: 'Revisá el día y la hora.',
  duracion: 'La duración va de 5 a 480 minutos, en pasos de 5.',
  nota: 'La nota puede tener hasta 500 caracteres.',
  motivo: 'El motivo puede tener hasta 200 caracteres.',
};

function erroresDeValidacion(error: ApiError): ErrorDeTurno {
  const lista = error.datos['errores'];
  const campos = (Array.isArray(lista) ? lista : []).flatMap((item: unknown) => {
    if (!esObjeto(item) || !esTexto(item['campo'])) return [];
    const campo = CAMPO_DEL_FORMULARIO[item['campo']] ?? item['campo'];
    return [{ campo, mensaje: MENSAJE_POR_CAMPO[campo] ?? 'Revisá este dato.' }];
  });
  return campos.length === 0 ? aviso('peligro', 'Revisá los datos', 'Hay algún dato que no pudimos guardar. Revisalo y volvé a intentar.') : enCampos(campos);
}

export function traducirErrorDeTurno(error: ApiError, contexto: ContextoDeError): ErrorDeTurno {
  switch (error.codigo) {
    case 'TURNO_SOLAPADO':
      return solapado(error, contexto);
    case 'FUERA_DE_DISPONIBILIDAD':
      return fueraDeDisponibilidad(contexto);
    case 'HORARIO_BLOQUEADO':
      return bloqueado(error, contexto);
    case 'TRANSICION_INVALIDA':
      return aviso('advertencia', 'Este turno ya cambió de estado', 'Mientras lo mirabas, el turno cambió. Actualizamos el detalle.', 'RECARGAR');
    case 'CONFLICTO_CONCURRENCIA':
      return aviso('advertencia', 'El turno cambió mientras lo editabas', 'No guardamos tu cambio para no pisar el otro. Mirá la versión actual y volvé a intentar.', 'VER_ACTUALIZADO');
    case 'PLAN_VENCIDO':
      return aviso('peligro', 'Tu agenda está en solo lectura', 'El período de prueba terminó. Activá el plan para volver a dar turnos; tus datos siguen guardados.');
    case 'DNI_INVALIDO':
      return enCampo('dni', MENSAJE_POR_CAMPO['dni'] ?? '');
    case 'TELEFONO_INVALIDO':
      return enCampo('telefono', MENSAJE_POR_CAMPO['telefono'] ?? '');
    case 'DURACION_INVALIDA':
      return enCampo('duracion', MENSAJE_POR_CAMPO['duracion'] ?? '');
    case 'HORARIO_INVALIDO':
      return enCampo('hora', MENSAJE_POR_CAMPO['hora'] ?? '');
    case 'PRESTACION_INACTIVA':
      return enCampo('prestacion', 'Esa prestación está desactivada. Elegí otra o activala en Configuración.');
    case 'VALIDACION':
      return erroresDeValidacion(error);
    case 'SIN_CONEXION':
      return aviso('peligro', 'No hay conexión', 'Revisá tu conexión y volvé a intentar. No perdiste lo que cargaste.', 'REINTENTAR');
    default:
      if (error.status === 404) return aviso('peligro', 'No encontramos este turno', 'Puede que ya no exista o que sea de otro consultorio.');
      return aviso('peligro', 'No pudimos guardar', 'Puede ser un problema momentáneo del servidor. Probá de nuevo en unos segundos.', 'REINTENTAR');
  }
}
