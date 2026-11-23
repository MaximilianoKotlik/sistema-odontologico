import type { NombreIcono } from '../../shared/ui/iconos';
import { horaLocal } from '../agenda/tiempo';
import type { ActorAuditoria, EventoAuditoria } from './api';

/**
 * Catálogo de acciones y formato de valores (C-18, design D6). El backend acepta cualquier acción: una que no esté acá
 * se muestra con su código, y una clave desconocida, con su nombre técnico.
 */

interface DefinicionAccion {
  readonly vos: string;
  readonly paciente?: string;
  readonly icono: NombreIcono;
}

const ACCIONES: Readonly<Record<string, DefinicionAccion>> = {
  TURNO_CREADO: { vos: 'Cargaste un turno', paciente: 'Reservó por tu link', icono: 'calendario' },
  TURNO_REPROGRAMADO: { vos: 'Reprogramaste un turno', paciente: 'Reprogramó por el link', icono: 'repetir' },
  TURNO_ESTADO_CAMBIADO: { vos: 'Cambiaste el estado de un turno', paciente: 'Cambió el estado por el link', icono: 'check' },
  TURNO_DURACION_CAMBIADA: { vos: 'Ajustaste la duración de un turno', icono: 'reloj-arena' },
  TURNO_NOTA_CAMBIADA: { vos: 'Editaste la nota de un turno', icono: 'nota' },
  BLOQUEO_CREADO: { vos: 'Bloqueaste un horario', icono: 'candado' },
  BLOQUEO_ELIMINADO: { vos: 'Quitaste un bloqueo', icono: 'candado' },
  CONSULTORIO_CONFIG_CAMBIADA: { vos: 'Cambiaste la configuración', icono: 'engranaje' },
  PRESTACION_CREADA: { vos: 'Creaste una prestación', icono: 'engranaje' },
  PRESTACION_CAMBIADA: { vos: 'Cambiaste una prestación', icono: 'engranaje' },
  PRESTACION_DESACTIVADA: { vos: 'Desactivaste una prestación', icono: 'engranaje' },
  PRESTACION_ACTIVADA: { vos: 'Activaste una prestación', icono: 'engranaje' },
  DISPONIBILIDAD_CAMBIADA: { vos: 'Cambiaste tus horarios', icono: 'reloj' },
  PLAN_ACTIVADO: { vos: 'Se activó el plan', icono: 'engranaje' },
  PLAN_VENCIDO: { vos: 'Venció la prueba', icono: 'engranaje' },
  PACIENTE_CREADO: { vos: 'Cargaste un paciente', paciente: 'Se registró al reservar', icono: 'lista' },
  PACIENTE_MARCADO_PARA_REVISION: { vos: 'Un paciente quedó para revisar', paciente: 'Un paciente quedó para revisar', icono: 'alerta' },
  PACIENTE_CONSENTIMIENTO_ACTUALIZADO: { vos: 'Se actualizó un consentimiento', paciente: 'Aceptó la política de privacidad', icono: 'documento' },
  PACIENTE_REVISADO: { vos: 'Revisaste un paciente', icono: 'check' },
  CUENTA_CREADA: { vos: 'Creaste tu cuenta', icono: 'llave' },
  LOGIN_EXITOSO: { vos: 'Iniciaste sesión', icono: 'llave' },
  LOGIN_FALLIDO: { vos: 'Intento fallido de iniciar sesión con tu email', icono: 'llave' },
  PASSWORD_CAMBIADA: { vos: 'Cambiaste tu contraseña', icono: 'llave' },
  PASSWORD_RECUPERADA: { vos: 'Recuperaste tu contraseña', icono: 'llave' },
  EXPORTACION_GENERADA: { vos: 'Exportaste tus datos', icono: 'descargar' },
};

const ESTADO_POR_ACTOR: Readonly<Record<string, { readonly vos: string; readonly paciente: string }>> = {
  CONFIRMADO: { vos: 'Confirmaste un turno', paciente: 'Confirmó por el link' },
  CANCELADO: { vos: 'Cancelaste un turno', paciente: 'Canceló por el link' },
  AUSENTE: { vos: 'Marcaste ausente a un paciente', paciente: 'Quedó como ausente' },
};

export interface OpcionDeFiltro {
  readonly clave: string;
  readonly etiqueta: string;
  readonly acciones: readonly string[];
}

export interface GrupoDeFiltro {
  readonly etiqueta: string;
  readonly opciones: readonly OpcionDeFiltro[];
}

/** Opciones del mockup `registro.html`. Plan y consentimiento van en la opción más cercana, sin sumar filas al selector. */
export const GRUPOS_DE_FILTRO: readonly GrupoDeFiltro[] = [
  {
    etiqueta: 'Turnos',
    opciones: [
      { clave: 'altas', etiqueta: 'Reservas y altas', acciones: ['TURNO_CREADO'] },
      { clave: 'reprogramaciones', etiqueta: 'Reprogramaciones', acciones: ['TURNO_REPROGRAMADO'] },
      { clave: 'estados', etiqueta: 'Cambios de estado', acciones: ['TURNO_ESTADO_CAMBIADO'] },
      { clave: 'duracion', etiqueta: 'Duración', acciones: ['TURNO_DURACION_CAMBIADA'] },
      { clave: 'notas', etiqueta: 'Notas', acciones: ['TURNO_NOTA_CAMBIADA'] },
    ],
  },
  {
    etiqueta: 'Bloqueos',
    opciones: [
      { clave: 'bloqueos-creados', etiqueta: 'Bloqueos creados', acciones: ['BLOQUEO_CREADO'] },
      { clave: 'bloqueos-eliminados', etiqueta: 'Bloqueos eliminados', acciones: ['BLOQUEO_ELIMINADO'] },
    ],
  },
  {
    etiqueta: 'Configuración',
    opciones: [
      { clave: 'consultorio', etiqueta: 'Datos del consultorio', acciones: ['CONSULTORIO_CONFIG_CAMBIADA', 'PLAN_ACTIVADO', 'PLAN_VENCIDO'] },
      { clave: 'prestaciones', etiqueta: 'Prestaciones', acciones: ['PRESTACION_CREADA', 'PRESTACION_CAMBIADA', 'PRESTACION_DESACTIVADA', 'PRESTACION_ACTIVADA'] },
      { clave: 'horarios', etiqueta: 'Horarios', acciones: ['DISPONIBILIDAD_CAMBIADA'] },
    ],
  },
  {
    etiqueta: 'Pacientes',
    opciones: [
      { clave: 'pacientes-nuevos', etiqueta: 'Pacientes nuevos', acciones: ['PACIENTE_CREADO', 'PACIENTE_CONSENTIMIENTO_ACTUALIZADO'] },
      { clave: 'pacientes-revisar', etiqueta: 'Pacientes a revisar', acciones: ['PACIENTE_MARCADO_PARA_REVISION', 'PACIENTE_REVISADO'] },
    ],
  },
  {
    etiqueta: 'Accesos',
    opciones: [{ clave: 'accesos', etiqueta: 'Inicios de sesión y contraseña', acciones: ['CUENTA_CREADA', 'LOGIN_EXITOSO', 'LOGIN_FALLIDO', 'PASSWORD_CAMBIADA', 'PASSWORD_RECUPERADA'] }],
  },
  {
    etiqueta: 'Exportaciones',
    opciones: [{ clave: 'exportaciones', etiqueta: 'Exportaciones', acciones: ['EXPORTACION_GENERADA'] }],
  },
];

export const opcionDeFiltro = (clave: string | null): OpcionDeFiltro | null =>
  GRUPOS_DE_FILTRO.flatMap((g) => g.opciones).find((o) => o.clave === clave) ?? null;

export function textoDelEvento(evento: EventoAuditoria): string {
  const definicion = ACCIONES[evento.accion];
  if (definicion === undefined) return evento.accion;
  const esPaciente = evento.actor.tipo === 'PACIENTE';
  if (evento.accion === 'TURNO_ESTADO_CAMBIADO') {
    const estado = evento.valorNuevo?.['estado'];
    const porEstado = typeof estado === 'string' ? ESTADO_POR_ACTOR[estado] : undefined;
    if (porEstado !== undefined) return esPaciente ? porEstado.paciente : porEstado.vos;
  }
  return esPaciente ? (definicion.paciente ?? definicion.vos) : definicion.vos;
}

/** Dentro del detalle ya se sabe de qué turno se habla ("Reprogramaste el turno"). */
export const textoEnElTurno = (evento: EventoAuditoria) => textoDelEvento(evento).replace(/ un turno$/, ' el turno');

/** Lo que hizo el paciente desde su link lleva el ícono de link, como en los mockups. */
export const iconoDelEvento = (evento: EventoAuditoria): NombreIcono =>
  evento.actor.tipo === 'PACIENTE' && evento.accion.startsWith('TURNO_') ? 'link' : (ACCIONES[evento.accion]?.icono ?? 'historial');

export function actorDelEvento(actor: ActorAuditoria): { readonly etiqueta: string; readonly nombre: string | null } {
  if (actor.identidadEnRevision) return { etiqueta: 'Reserva con este DNI', nombre: null };
  if (actor.tipo === 'SISTEMA') return { etiqueta: 'Sistema', nombre: null };
  if (actor.id === null) return { etiqueta: 'Sin sesión', nombre: null };
  return { etiqueta: actor.tipo === 'PACIENTE' ? 'Paciente' : 'Vos', nombre: actor.nombre };
}

export const muestraIp = (accion: string) => /^(LOGIN|PASSWORD)_/.test(accion);

const INTENTOS_MAXIMOS = 5;

export function detalleDeAcceso(evento: EventoAuditoria): string | null {
  if (evento.accion !== 'LOGIN_FALLIDO') return null;
  const intentos = evento.valorNuevo?.['intentos_fallidos'];
  return typeof intentos === 'number' ? `Contraseña incorrecta · ${intentos} de ${INTENTOS_MAXIMOS} intentos` : 'Contraseña incorrecta';
}

const NOMBRES: Readonly<Record<string, string>> = {
  RESERVADO: 'Reservado',
  CONFIRMADO: 'Confirmado',
  CANCELADO: 'Cancelado',
  AUSENTE: 'Ausente',
  LINK: 'Por tu link',
  MANUAL: 'Cargado por vos',
  PRUEBA: 'Prueba',
  ACTIVO: 'Activo',
  VENCIDO: 'Vencido',
};

type Formato = 'instante' | 'nombre' | 'minutos' | 'horas' | 'dias' | 'booleano' | 'texto' | 'franjas' | 'total';

const CLAVES: Readonly<Record<string, { readonly etiqueta: string; readonly formato: Formato }>> = {
  inicio: { etiqueta: 'Inicio', formato: 'instante' },
  fin: { etiqueta: 'Fin', formato: 'instante' },
  duracion_min: { etiqueta: 'Duración', formato: 'minutos' },
  estado: { etiqueta: 'Estado', formato: 'nombre' },
  origen: { etiqueta: 'Origen', formato: 'nombre' },
  es_sobreturno: { etiqueta: 'Sobreturno', formato: 'booleano' },
  nota_interna: { etiqueta: 'Nota', formato: 'texto' },
  motivo: { etiqueta: 'Motivo', formato: 'texto' },
  requiere_revision: { etiqueta: 'Para revisar', formato: 'booleano' },
  consentimiento_version: { etiqueta: 'Versión aceptada', formato: 'texto' },
  consentimiento_at: { etiqueta: 'Aceptada el', formato: 'instante' },
  plan: { etiqueta: 'Plan', formato: 'nombre' },
  prueba_hasta: { etiqueta: 'Prueba hasta', formato: 'instante' },
  nombre: { etiqueta: 'Nombre', formato: 'texto' },
  // C-17: los datos que corrige la revisión del paciente (PACIENTE_REVISADO).
  apellido: { etiqueta: 'Apellido', formato: 'texto' },
  email: { etiqueta: 'Email', formato: 'texto' },
  slug: { etiqueta: 'Link público', formato: 'texto' },
  direccion: { etiqueta: 'Dirección', formato: 'texto' },
  telefono_contacto: { etiqueta: 'Teléfono de contacto', formato: 'texto' },
  zona_horaria: { etiqueta: 'Zona horaria', formato: 'texto' },
  anticipacion_cancelacion_min: { etiqueta: 'Anticipación para cancelar', formato: 'horas' },
  anticipacion_reserva_min: { etiqueta: 'Anticipación para reservar', formato: 'horas' },
  horizonte_reserva_dias: { etiqueta: 'Reservas hasta', formato: 'dias' },
  paso_huecos_min: { etiqueta: 'Intervalo de horarios', formato: 'minutos' },
  reserva_publica_activa: { etiqueta: 'Reserva pública', formato: 'booleano' },
  activa: { etiqueta: 'Activa', formato: 'booleano' },
  visible_publica: { etiqueta: 'Visible al reservar', formato: 'booleano' },
  orden: { etiqueta: 'Orden', formato: 'texto' },
  franjas: { etiqueta: 'Horarios', formato: 'franjas' },
  entidades: { etiqueta: 'Registros exportados', formato: 'total' },
};

const ORDEN_DE_CLAVES = Object.keys(CLAVES);

/** Ids internos y datos técnicos que la fila no muestra (spec "Cambios legibles"; el intento va en `detalleDeAcceso`). */
const OCULTAS = new Set(['paciente_id', 'tipo_prestacion_id', 'cancelado_por', 'intentos_fallidos', 'bloqueado_hasta', 'credenciales_version', 'versionEsquema']);

const formatoDiaCorto = new Map<string, Intl.DateTimeFormat>();

export function instanteCorto(valor: string, zona: string): string {
  const fecha = new Date(valor);
  if (Number.isNaN(fecha.getTime())) return valor;
  let formato = formatoDiaCorto.get(zona);
  if (formato === undefined) {
    formato = new Intl.DateTimeFormat('es-AR', { timeZone: zona, weekday: 'short', day: '2-digit', month: '2-digit' });
    formatoDiaCorto.set(zona, formato);
  }
  const partes = new Map(formato.formatToParts(fecha).map((p) => [p.type, p.value]));
  return `${(partes.get('weekday') ?? '').replace('.', '')} ${partes.get('day') ?? ''}/${partes.get('month') ?? ''} ${horaLocal(fecha, zona)}`;
}

const crudo = (valor: unknown) => (typeof valor === 'string' ? valor : JSON.stringify(valor));

function formatearValor(formato: Formato, valor: unknown, zona: string): string {
  if (valor === null || valor === undefined || valor === '') return 'Sin dato';
  switch (formato) {
    case 'instante':
      return typeof valor === 'string' ? instanteCorto(valor, zona) : crudo(valor);
    case 'nombre':
      return typeof valor === 'string' ? (NOMBRES[valor] ?? valor) : crudo(valor);
    case 'minutos':
      return `${crudo(valor)} min`;
    case 'horas':
      return typeof valor === 'number' && valor % 60 === 0 ? `${valor / 60} h` : `${crudo(valor)} min`;
    case 'dias':
      return `${crudo(valor)} días`;
    case 'booleano':
      return valor === true ? 'Sí' : valor === false ? 'No' : crudo(valor);
    case 'franjas':
      return Array.isArray(valor) ? `${valor.length} ${valor.length === 1 ? 'franja' : 'franjas'}` : crudo(valor);
    case 'total':
      return typeof valor === 'object' ? String(Object.values(valor).reduce<number>((suma, n) => suma + (typeof n === 'number' ? n : 0), 0)) : crudo(valor);
    case 'texto':
      return crudo(valor);
  }
}

export interface CambioLegible {
  readonly etiqueta: string;
  readonly antes: string | null;
  readonly despues: string | null;
  /** Clave sin etiqueta conocida: se muestra en tipografía de código. */
  readonly codigo: boolean;
}

export function cambiosDelEvento(evento: EventoAuditoria, zona: string): CambioLegible[] {
  const anterior = evento.valorAnterior ?? {};
  const nuevo = evento.valorNuevo ?? {};
  // JSONB no conserva el orden de las claves: se usa el del catálogo, y las desconocidas van al final.
  const posicion = (clave: string) => (clave in CLAVES ? ORDEN_DE_CLAVES.indexOf(clave) : ORDEN_DE_CLAVES.length);
  const claves = [...new Set([...Object.keys(nuevo), ...Object.keys(anterior)])].sort((x, y) => posicion(x) - posicion(y));
  const cambios: CambioLegible[] = [];
  for (const clave of claves) {
    if (OCULTAS.has(clave)) continue;
    // En un turno, el fin sale del inicio y la duración.
    if (clave === 'fin' && evento.entidad === 'turno') continue;
    const hayAntes = evento.valorAnterior !== null && clave in anterior;
    const hayDespues = evento.valorNuevo !== null && clave in nuevo;
    if (hayAntes && hayDespues && JSON.stringify(anterior[clave]) === JSON.stringify(nuevo[clave])) continue;
    const definicion = CLAVES[clave];
    const formato = definicion?.formato ?? 'texto';
    cambios.push({
      etiqueta: definicion?.etiqueta ?? clave,
      antes: hayAntes ? formatearValor(formato, anterior[clave], zona) : null,
      despues: hayDespues ? formatearValor(formato, nuevo[clave], zona) : null,
      codigo: definicion === undefined,
    });
  }
  return cambios;
}
