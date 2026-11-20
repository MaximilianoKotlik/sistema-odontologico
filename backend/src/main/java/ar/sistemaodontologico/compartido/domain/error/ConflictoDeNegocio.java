package ar.sistemaodontologico.compartido.domain.error;

/** La operación choca con el estado actual (HTTP 409). */
public class ConflictoDeNegocio extends ErrorDeNegocio {

    public ConflictoDeNegocio(String codigo, String mensaje) {
        super(codigo, TipoDeError.CONFLICTO, mensaje);
    }
}
