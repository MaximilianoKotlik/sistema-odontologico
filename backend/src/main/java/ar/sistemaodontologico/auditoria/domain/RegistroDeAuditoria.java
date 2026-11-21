package ar.sistemaodontologico.auditoria.domain;

import ar.sistemaodontologico.compartido.domain.auditoria.ActorTipo;
import ar.sistemaodontologico.compartido.domain.auditoria.CambioAuditado;
import ar.sistemaodontologico.compartido.domain.auditoria.EventoDeAuditoria;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Fila de {@code evento_auditoria} tal como se persiste (C-03, design D-4). Se arma a partir del evento que
 * publica el puerto de C-02; el contenido ya viene validado por {@link CambioAuditado}.
 */
public record RegistroDeAuditoria(
        UUID id,
        UUID consultorioId,
        Instant ocurridoAt,
        ActorTipo actorTipo,
        UUID actorId,
        String entidad,
        UUID entidadId,
        String accion,
        Map<String, Object> valorAnterior,
        Map<String, Object> valorNuevo,
        String ip) {

    private static final Pattern IPV4 =
            Pattern.compile("^((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)\\.){3}(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)$");
    // Solo caracteres de un literal IPv6 (con IPv4 embebida opcional): así InetAddress no consulta DNS.
    private static final Pattern CARACTERES_IPV6 = Pattern.compile("^[0-9A-Fa-f:.]+$");

    public RegistroDeAuditoria {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(consultorioId, "consultorioId");
        Objects.requireNonNull(ocurridoAt, "ocurridoAt");
        Objects.requireNonNull(actorTipo, "actorTipo");
        Objects.requireNonNull(entidad, "entidad");
        Objects.requireNonNull(entidadId, "entidadId");
        Objects.requireNonNull(accion, "accion");
    }

    /** Arma el registro con el id generado por la aplicación y el instante que trae el evento. */
    public static RegistroDeAuditoria desde(EventoDeAuditoria evento, UUID id) {
        CambioAuditado cambio = evento.cambio();
        return new RegistroDeAuditoria(id, cambio.consultorio().valor(), evento.ocurridoAt(), cambio.actor().tipo(),
                cambio.actor().id(), cambio.entidad(), cambio.entidadId(), cambio.accion(), cambio.valorAnterior(),
                cambio.valorNuevo(), ipValidaONula(cambio.ip()));
    }

    /**
     * Una IP que no es un literal IPv4 o IPv6 se guarda vacía: si llegara al {@code CAST(... AS inet)}, el error
     * revertiría el cambio auditado (design D-4). Nunca resuelve nombres por DNS.
     */
    static String ipValidaONula(String ip) {
        if (ip == null) {
            return null;
        }
        if (IPV4.matcher(ip).matches()) {
            return ip;
        }
        if (!ip.contains(":") || !CARACTERES_IPV6.matcher(ip).matches()) {
            return null;
        }
        try {
            InetAddress.getByName(ip);
            return ip;
        } catch (UnknownHostException | IllegalArgumentException e) {
            return null;
        }
    }
}
