import { ApiError, type ClienteHttp } from '../../shared/http';

/**
 * Cliente de pacientes de C-17 (spec revision-pacientes, design D3, D4, D9 y D10). Las respuestas se leen como
 * `unknown` y se validan con type guards: una entrada incompleta se descarta (regla dura 9).
 */

export interface TurnoDeOrigen {
  readonly id: string;
  readonly inicio: string;
  readonly prestacion: string;
}

export interface LoInformado {
  readonly nombre: string;
  readonly apellido: string;
  readonly email: string | null;
  readonly informadoAt: string;
  readonly turno: TurnoDeOrigen | null;
}

export interface Paciente {
  readonly id: string;
  readonly nombre: string;
  readonly apellido: string;
  readonly dni: string;
  readonly telefono: string;
  readonly email: string | null;
  readonly requiereRevision: boolean;
  /** Solo en un paciente con revisión pendiente (DD-38). */
  readonly informado: LoInformado | null;
}

export interface PaginaDePacientes {
  readonly items: readonly Paciente[];
  readonly total: number;
}

export interface DatosCorregidos {
  readonly nombre: string;
  readonly apellido: string;
  readonly email: string;
}

export const TAMANO_DE_PAGINA = 20;
const RUTA = '/api/v1/pacientes';

type Registro = Record<string, unknown>;

const esRegistro = (valor: unknown): valor is Registro => typeof valor === 'object' && valor !== null && !Array.isArray(valor);
const texto = (valor: unknown): string | null => (typeof valor === 'string' ? valor : null);
const textoOpcional = (valor: unknown): string | null => (typeof valor === 'string' && valor !== '' ? valor : null);

function aTurno(valor: unknown): TurnoDeOrigen | null {
  if (!esRegistro(valor)) return null;
  const id = texto(valor['id']);
  const inicio = texto(valor['inicio']);
  const prestacion = texto(valor['prestacion']);
  return id === null || inicio === null || prestacion === null ? null : { id, inicio, prestacion };
}

function aInformado(valor: unknown): LoInformado | null {
  if (!esRegistro(valor)) return null;
  const nombre = texto(valor['nombre']);
  const apellido = texto(valor['apellido']);
  const informadoAt = texto(valor['informadoAt']);
  if (nombre === null || apellido === null || informadoAt === null) return null;
  return { nombre, apellido, email: textoOpcional(valor['email']), informadoAt, turno: aTurno(valor['turno']) };
}

export function aPaciente(valor: unknown): Paciente | null {
  if (!esRegistro(valor)) return null;
  const id = texto(valor['id']);
  const nombre = texto(valor['nombre']);
  const apellido = texto(valor['apellido']);
  const dni = texto(valor['dni']);
  const telefono = texto(valor['telefono']);
  if (id === null || nombre === null || apellido === null || dni === null || telefono === null) return null;
  const requiereRevision = valor['requiereRevision'] === true;
  return {
    id,
    nombre,
    apellido,
    dni,
    telefono,
    email: textoOpcional(valor['email']),
    requiereRevision,
    informado: requiereRevision ? aInformado(valor['informado']) : null,
  };
}

function aPagina(valor: unknown): PaginaDePacientes {
  if (!esRegistro(valor)) return { items: [], total: 0 };
  const items = Array.isArray(valor['items']) ? valor['items'].map(aPaciente).filter((p): p is Paciente => p !== null) : [];
  const total = typeof valor['total'] === 'number' && Number.isInteger(valor['total']) && valor['total'] >= 0 ? valor['total'] : 0;
  return { items, total };
}

function exigirPaciente(valor: unknown): Paciente {
  const paciente = aPaciente(valor);
  if (paciente === null) throw new ApiError(200, 'ERROR_INESPERADO', 'La respuesta del servidor no es válida.');
  return paciente;
}

const rutaListado = (soloConRevision: boolean, pagina: number, tamano: number) =>
  `${RUTA}/listado?requiereRevision=${soloConRevision}&page=${pagina}&size=${tamano}`;

export async function listarPacientes(http: ClienteHttp, pedido: { readonly soloConRevision: boolean; readonly pagina: number }): Promise<PaginaDePacientes> {
  return aPagina(await http.get<unknown>(rutaListado(pedido.soloConRevision, pedido.pagina, TAMANO_DE_PAGINA)));
}

/** El contador de la navegación y del aviso de la agenda (D3): el total de la lista filtrada, sin endpoint nuevo. */
export async function cantidadParaRevisar(http: ClienteHttp): Promise<number> {
  return aPagina(await http.get<unknown>(rutaListado(true, 0, 1))).total;
}

export async function detallePaciente(http: ClienteHttp, id: string): Promise<Paciente> {
  return exigirPaciente(await http.get<unknown>(`${RUTA}/${encodeURIComponent(id)}`));
}

/** Mantiene los datos guardados. `informadoAt` es el del detalle que se mostró (D9). */
export async function confirmarRevision(http: ClienteHttp, id: string, informadoAt: string): Promise<Paciente> {
  return exigirPaciente(await http.post<unknown>(`${RUTA}/${encodeURIComponent(id)}/confirmar-revision`, { informadoAt }));
}

/** Corrige nombre, apellido y email; un email vacío se quita. El DNI y el teléfono no se mandan. */
export async function corregirPaciente(http: ClienteHttp, id: string, informadoAt: string, datos: DatosCorregidos): Promise<Paciente> {
  const email = datos.email.trim();
  const cuerpo = { informadoAt, nombre: datos.nombre.trim(), apellido: datos.apellido.trim(), ...(email === '' ? {} : { email }) };
  return exigirPaciente(await http.patch<unknown>(`${RUTA}/${encodeURIComponent(id)}`, cuerpo));
}
