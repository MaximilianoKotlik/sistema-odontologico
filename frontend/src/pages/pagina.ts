import type { EncabezadoPagina } from '../app/shell';
import type { ClienteHttp } from '../shared/http';
import type { Parametros, Router } from '../shared/router';

/** Acciones de la sesión que puede pedir una página privada (C-07). */
export interface AccionesCuenta {
  /** Después de un cambio de contraseña: borra lo local, avisa a las otras pestañas y lleva al login (D11). */
  terminarPorCambioDeContrasena(): Promise<void>;
}

/** Lo que el shell le da a cada página para montarse (C-07, C-09, C-13… solo montan contenido). */
export interface ContextoPagina {
  readonly contenedor: HTMLElement;
  /** Acciones de la barra superior (p. ej. "Nuevo turno", C-14). */
  readonly acciones: HTMLElement;
  readonly params: Parametros;
  readonly http: ClienteHttp;
  /** Zona horaria del consultorio para presentar fechas (spec fechas-zona-horaria). */
  readonly zonaHoraria: string;
  readonly cuenta: AccionesCuenta;
  /** Navegar dentro de la app y frenar la salida con cambios sin guardar (C-09, D9). */
  readonly navegacion: Pick<Router, 'navegar' | 'bloquearSalida'>;
}

export interface Pagina extends EncabezadoPagina {
  montar(contexto: ContextoPagina): void;
}
