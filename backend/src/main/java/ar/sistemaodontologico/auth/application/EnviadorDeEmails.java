package ar.sistemaodontologico.auth.application;

/**
 * Puerto de envío de emails de autenticación (design D4/D5). Lo usa el listener que corre después del commit de la
 * solicitud de recuperación, así la respuesta 202 no espera al servidor de correo.
 */
public interface EnviadorDeEmails {

    /**
     * Envía el link para restablecer la contraseña.
     *
     * @throws EnvioDeEmailFallido si el servidor de correo no acepta el mensaje
     */
    void enviarRecuperacion(String destinatario, String token);
}
