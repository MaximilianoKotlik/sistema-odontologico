package ar.sistemaodontologico.compartido.domain.error;

/** Datos que no cumplen una regla de negocio (HTTP 400). */
public class ValidacionDeNegocio extends ErrorDeNegocio {

    public ValidacionDeNegocio(String codigo, String mensaje) {
        super(codigo, TipoDeError.VALIDACION, mensaje);
    }
}
