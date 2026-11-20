import type { EncabezadoPagina } from '../app/shell';
import type { ClienteHttp } from '../shared/http';
import type { Parametros } from '../shared/router';

/** Lo que el shell le da a cada página para montarse (C-07, C-09, C-13… solo montan contenido). */
export interface ContextoPagina {
  readonly contenedor: HTMLElement;
  /** Acciones de la barra superior (p. ej. "Nuevo turno", C-14). */
  readonly acciones: HTMLElement;
  readonly params: Parametros;
  readonly http: ClienteHttp;
  /** Zona horaria del consultorio para presentar fechas (spec fechas-zona-horaria). */
  readonly zonaHoraria: string;
}

export interface Pagina extends EncabezadoPagina {
  montar(contexto: ContextoPagina): void;
}
