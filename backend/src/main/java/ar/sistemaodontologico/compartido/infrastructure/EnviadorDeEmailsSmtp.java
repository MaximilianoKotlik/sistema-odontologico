package ar.sistemaodontologico.compartido.infrastructure;

import ar.sistemaodontologico.auth.domain.TokenDeRecuperacion;
import ar.sistemaodontologico.compartido.application.EnviadorDeEmails;
import ar.sistemaodontologico.compartido.application.EnvioDeEmailFallido;
import ar.sistemaodontologico.compartido.application.PropiedadesDeMail;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

/**
 * Emails de autenticación por SMTP, en texto plano y en español rioplatense (design D5). El token va en el
 * fragmento del link: nunca llega a los logs de un servidor ni de un proxy.
 */
@Component
public class EnviadorDeEmailsSmtp implements EnviadorDeEmails {

    static final String ASUNTO_RECUPERACION = "Restablecé tu contraseña";

    private final JavaMailSender smtp;
    private final PropiedadesDeMail propiedades;

    public EnviadorDeEmailsSmtp(JavaMailSender smtp, PropiedadesDeMail propiedades) {
        this.smtp = smtp;
        this.propiedades = propiedades;
    }

    @Override
    public void enviarRecuperacion(String destinatario, String token) {
        String link = propiedades.frontendOrigin() + "/recuperar#token=" + token;
        try {
            MimeMessage mensaje = smtp.createMimeMessage();
            // UTF-8 explícito: el texto tiene tildes y eñes, y no depende del encoding del JavaMailSender.
            MimeMessageHelper ayudante = new MimeMessageHelper(mensaje, false, StandardCharsets.UTF_8.name());
            ayudante.setFrom(propiedades.from());
            ayudante.setTo(destinatario);
            ayudante.setSubject(ASUNTO_RECUPERACION);
            ayudante.setText(cuerpoDeRecuperacion(link), false);
            smtp.send(mensaje);
        } catch (MessagingException | MailException e) {
            // Solo el tipo de falla: el mensaje de JavaMail puede incluir el destinatario.
            throw new EnvioDeEmailFallido("El servidor de correo rechazó el email (" + e.getClass().getSimpleName() + ")");
        }
    }

    @Override
    public void enviarConfirmacionDeTurno(String destinatario, String consultorio, String fecha, String token) {
        String link = propiedades.frontendOrigin() + "/t/" + token;
        try {
            MimeMessage mensaje = smtp.createMimeMessage();
            MimeMessageHelper ayudante = new MimeMessageHelper(mensaje, false, StandardCharsets.UTF_8.name());
            ayudante.setFrom(propiedades.from());
            ayudante.setTo(destinatario);
            ayudante.setSubject("Tu turno en " + consultorio);
            ayudante.setText("""
                    Hola:

                    Tu turno en %s quedó reservado para el %s.

                    Para verlo, cambiarlo o cancelarlo, abrí este link:

                    %s

                    Guardalo: es la única forma de gestionar tu turno sin llamar al consultorio. No lo compartas.
                    """.formatted(consultorio, fecha, link), false);
            smtp.send(mensaje);
        } catch (MessagingException | MailException e) {
            // Solo el tipo de falla: el mensaje de JavaMail puede incluir el destinatario.
            throw new EnvioDeEmailFallido("El servidor de correo rechazó el email (" + e.getClass().getSimpleName() + ")");
        }
    }

    private static String cuerpoDeRecuperacion(String link) {
        long minutos = TokenDeRecuperacion.VIGENCIA.toMinutes();
        return """
                Hola:

                Recibimos un pedido para restablecer la contraseña de tu cuenta. Para elegir una nueva, abrí este link:

                %s

                El link vence en %d minutos y sirve una sola vez. Si no lo pediste, podés ignorar este email: \
                tu contraseña sigue siendo la misma.
                """.formatted(link, minutos);
    }
}
