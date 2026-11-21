import type { ApiError } from '../../shared/http';
import type { ErrorDeDia } from './validaciones';

/**
 * Códigos de error de C-08 → campo, día o aviso (C-09, design D8). Los mensajes están todos acá: si C-08 cambia un
 * código o una propiedad, se ajusta un solo archivo. Nunca se muestra el código crudo.
 */

export interface ErrorTraducido {
  readonly campos: readonly { readonly campo: string; readonly mensaje: string }[];
  readonly dias: readonly ErrorDeDia[];
  readonly sugerencias: readonly string[];
  readonly aviso: string | null;
  /** 409 `CONFLICTO_CONCURRENCIA`: ofrecer "Ver los valores actuales". */
  readonly conflicto: boolean;
  /** Red caída o error inesperado: se puede reintentar sin perder lo editado. */
  readonly recuperable: boolean;
}

const VACIO: ErrorTraducido = { campos: [], dias: [], sugerencias: [], aviso: null, conflicto: false, recuperable: false };

const MENSAJE_TELEFONO_INVALIDO = 'Este teléfono no es válido. Probá con el código de área, por ejemplo 11 0000-0001.';
const MENSAJE_DIA_SOLAPADO = 'Encontramos franjas superpuestas en este día. Revisalas y volvé a guardar.';

/** Mensaje por campo para `VALIDACION`; un campo sin mensaje propio usa el genérico. */
const MENSAJE_POR_CAMPO: Readonly<Record<string, string>> = {
  nombre: 'Revisá el nombre: es obligatorio y puede tener hasta 120 caracteres.',
  telefonoContacto: 'Poné un teléfono de contacto: es lo que ven tus pacientes cuando no pueden cancelar por el link o si pausás la reserva.',
  slug: 'Revisá la dirección de tu link.',
  zonaHoraria: 'Elegí una zona horaria de la lista.',
  anticipacionCancelacionMin: 'La anticipación para cancelar va de 0 a 168 horas (7 días).',
  anticipacionReservaMin: 'Revisá la anticipación para reservar.',
  horizonteReservaDias: 'El horizonte va de 1 a 365 días.',
  pasoHuecosMin: 'Elegí un paso de 5, 10, 15, 20 o 30 minutos.',
  duracionMin: 'Poné una duración entre 5 y 480 minutos, en múltiplos de 5.',
};

/** Los nombres del contrato (`...Min`) se muestran en los campos del formulario, que usan horas. */
const CAMPO_DEL_FORMULARIO: Readonly<Record<string, string>> = {
  anticipacionCancelacionMin: 'anticipacionCancelacion',
  anticipacionReservaMin: 'anticipacionReserva',
};

const esTexto = (valor: unknown): valor is string => typeof valor === 'string' && valor !== '';

function erroresDeValidacion(datos: Readonly<Record<string, unknown>>): { campo: string; mensaje: string }[] {
  const errores = datos['errores'];
  if (!Array.isArray(errores)) return [];
  return errores.flatMap((error: unknown) => {
    if (typeof error !== 'object' || error === null) return [];
    const campo = (error as Record<string, unknown>)['campo'];
    if (!esTexto(campo)) return [];
    return [{ campo: CAMPO_DEL_FORMULARIO[campo] ?? campo, mensaje: MENSAJE_POR_CAMPO[campo] ?? 'Revisá este dato.' }];
  });
}

export function traducirError(error: ApiError): ErrorTraducido {
  const campo = (nombre: string, mensaje: string): ErrorTraducido => ({ ...VACIO, campos: [{ campo: nombre, mensaje }] });
  switch (error.codigo) {
    case 'SLUG_EN_USO': {
      const sugerencias = Array.isArray(error.datos['sugerencias']) ? error.datos['sugerencias'].filter(esTexto) : [];
      return {
        ...campo('slug', sugerencias.length > 0 ? 'Esa dirección ya la usa otro consultorio. Elegí otra o tocá una de estas:' : 'Esa dirección ya la usa otro consultorio. Elegí otra.'),
        sugerencias,
      };
    }
    case 'SLUG_RESERVADO':
      return campo('slug', 'Esa dirección está reservada. Elegí otra.');
    case 'SLUG_INVALIDO':
      return campo('slug', 'Usá de 3 a 40 letras minúsculas, números o guiones, sin guiones al principio, al final ni dos seguidos.');
    case 'TELEFONO_INVALIDO':
      return campo('telefonoContacto', MENSAJE_TELEFONO_INVALIDO);
    case 'ZONA_HORARIA_INVALIDA':
      return campo('zonaHoraria', 'Elegí una zona horaria de la lista.');
    case 'ANTICIPACION_FUERA_DE_HORIZONTE':
      return campo('anticipacionReserva', 'Tiene que ser menor que el horizonte. Si no, no queda ningún horario para reservar.');
    case 'DURACION_INVALIDA':
      return campo('duracionMin', MENSAJE_POR_CAMPO['duracionMin'] ?? '');
    case 'PRESTACION_DUPLICADA':
      return campo('nombre', 'Ya tenés una prestación con ese nombre.');
    case 'VALIDACION': {
      const campos = erroresDeValidacion(error.datos);
      return campos.length > 0 ? { ...VACIO, campos } : { ...VACIO, aviso: 'Hay datos que no son válidos. Revisalos y volvé a guardar.' };
    }
    case 'FRANJAS_SOLAPADAS': {
      const dias = Array.isArray(error.datos['dias'])
        ? error.datos['dias'].filter((d): d is number => Number.isInteger(d)).map((diaSemana) => ({ diaSemana, mensaje: MENSAJE_DIA_SOLAPADO }))
        : [];
      return dias.length > 0 ? { ...VACIO, dias } : { ...VACIO, aviso: 'Encontramos franjas superpuestas. Revisalas y volvé a guardar.' };
    }
    case 'FRANJA_INVALIDA':
      return { ...VACIO, aviso: 'Hay una franja con horas que no son válidas. Revisá los horarios y volvé a guardar.' };
    case 'CONFLICTO_CONCURRENCIA':
      return { ...VACIO, conflicto: true, aviso: 'La configuración cambió en otra pestaña o dispositivo.' };
    case 'SIN_CONEXION':
      return { ...VACIO, recuperable: true, aviso: 'No hay conexión. Lo que editaste sigue acá: reintentá cuando vuelva la red.' };
    default:
      return { ...VACIO, recuperable: true, aviso: 'No pudimos guardar. Lo que editaste sigue acá: probá de nuevo.' };
  }
}
