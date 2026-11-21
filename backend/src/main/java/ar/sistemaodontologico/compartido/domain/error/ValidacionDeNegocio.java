package ar.sistemaodontologico.compartido.domain.error;

import java.util.Map;

/** Datos que no cumplen una regla de negocio (HTTP 400). */
public class ValidacionDeNegocio extends ErrorDeNegocio {

    public ValidacionDeNegocio(String codigo, String mensaje) {
        super(codigo, TipoDeError.VALIDACION, mensaje);
    }

    public ValidacionDeNegocio(String codigo, String mensaje, Map<String, Object> propiedades) {
        super(codigo, TipoDeError.VALIDACION, mensaje, propiedades);
    }
}
