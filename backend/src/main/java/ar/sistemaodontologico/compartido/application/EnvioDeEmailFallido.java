package ar.sistemaodontologico.compartido.application;

/**
 * El servidor de correo no aceptó un email. El mensaje nunca incluye el destinatario ni el token (RN-AD-05): solo
 * el tipo de falla, para el log en WARN del listener (design D4).
 */
public class EnvioDeEmailFallido extends RuntimeException {

    public EnvioDeEmailFallido(String motivo) {
        super(motivo);
    }
}
