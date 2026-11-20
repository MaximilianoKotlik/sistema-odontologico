package ar.sistemaodontologico.compartido.domain.error;

/** No se pudo autenticar (HTTP 401): credenciales inválidas o sesión vencida. El mensaje nunca dice por qué. */
public class AutenticacionRechazada extends ErrorDeNegocio {

    public AutenticacionRechazada(String codigo, String mensaje) {
        super(codigo, TipoDeError.NO_AUTENTICADO, mensaje);
    }
}
