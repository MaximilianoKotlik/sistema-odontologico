package ar.sistemaodontologico.compartido.domain.error;

/** Categoría de un error de negocio; la capa web la traduce a un estado HTTP. */
public enum TipoDeError {
    VALIDACION,
    NO_AUTENTICADO,
    NO_ENCONTRADO,
    CONFLICTO
}
