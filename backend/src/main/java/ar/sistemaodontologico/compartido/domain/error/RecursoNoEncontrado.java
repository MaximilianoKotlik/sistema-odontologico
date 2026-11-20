package ar.sistemaodontologico.compartido.domain.error;

/**
 * El recurso no existe para el consultorio activo (HTTP 404). Se usa igual cuando existe pero es de
 * otro consultorio, para no revelar su existencia (RN-TE-04).
 */
public class RecursoNoEncontrado extends ErrorDeNegocio {

    public static final String CODIGO = "RECURSO_NO_ENCONTRADO";

    public RecursoNoEncontrado(String entidad) {
        super(CODIGO, TipoDeError.NO_ENCONTRADO, "No se encontró " + entidad);
    }
}
