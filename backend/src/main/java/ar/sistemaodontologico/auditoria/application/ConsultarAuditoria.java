package ar.sistemaodontologico.auditoria.application;

import ar.sistemaodontologico.auditoria.application.LecturaDeAuditoria.CriteriosDeRegistro;
import ar.sistemaodontologico.auditoria.application.LecturaDeAuditoria.EventoLeido;
import ar.sistemaodontologico.auditoria.domain.CursorDeAuditoria;
import ar.sistemaodontologico.auditoria.domain.ErrorDeFiltro;
import ar.sistemaodontologico.auditoria.domain.FiltrosDeAuditoria;
import ar.sistemaodontologico.compartido.application.TenantContext;
import ar.sistemaodontologico.compartido.domain.ConsultorioId;
import ar.sistemaodontologico.compartido.domain.error.RecursoNoEncontrado;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consultas del registro de auditoría (C-18, US-040, design D1). Son de solo lectura y funcionan en cualquier estado del
 * plan. El consultorio sale siempre de la sesión.
 */
@Service
public class ConsultarAuditoria {

    /** Una página del registro; {@code siguienteCursor} es nulo en la última. */
    public record PaginaDeAuditoria(List<EventoLeido> eventos, String siguienteCursor) {
    }

    private static final int TAMANO_POR_DEFECTO = 50;
    private static final int TAMANO_MAXIMO = 100;
    private static final String ALGORITMO = "HmacSHA256";
    private static final String PROPOSITO_DE_LA_CLAVE = "cursor-auditoria-v1";

    private final LecturaDeAuditoria lectura;
    private final TenantContext tenant;
    private final byte[] claveDelCursor;

    ConsultarAuditoria(LecturaDeAuditoria lectura, TenantContext tenant,
            @Value("${app.auth.jwt-secreto}") String secreto) {
        this.lectura = lectura;
        this.tenant = tenant;
        this.claveDelCursor = derivarClave(secreto);
    }

    /** Eventos del turno en orden cronológico; 404 si no es de este consultorio (design D2). */
    @Transactional(readOnly = true)
    public List<EventoLeido> historialDeTurno(UUID turnoId) {
        ConsultorioId consultorio = tenant.actual();
        if (!lectura.existeTurno(consultorio, turnoId)) {
            throw new RecursoNoEncontrado("turno");
        }
        return lectura.listarDeTurno(consultorio, turnoId);
    }

    /** Pide un evento de más para saber si hay otra página sin hacer un {@code COUNT} (design D3). */
    @Transactional(readOnly = true)
    public PaginaDeAuditoria registro(FiltrosDeAuditoria filtros, String cursor, String tamanoPedido) {
        ConsultorioId consultorio = tenant.actual();
        int tamano = tamano(tamanoPedido);
        CursorDeAuditoria posicion =
                cursor == null ? null : CursorDeAuditoria.decodificar(cursor, claveDelCursor, consultorio);
        ZoneId zona = lectura.zonaHoraria(consultorio);
        List<EventoLeido> eventos = lectura.listarRegistro(consultorio, new CriteriosDeRegistro(filtros.turnoId(),
                filtros.inicio(zona), filtros.finExclusivo(zona), filtros.acciones(),
                posicion == null ? null : posicion.ocurridoAt(), posicion == null ? null : posicion.id(), tamano + 1));
        if (eventos.size() <= tamano) {
            return new PaginaDeAuditoria(eventos, null);
        }
        List<EventoLeido> pagina = List.copyOf(eventos.subList(0, tamano));
        EventoLeido ultimo = pagina.getLast();
        String siguiente = new CursorDeAuditoria(ultimo.ocurridoAt(), ultimo.id(), consultorio).codificar(claveDelCursor);
        return new PaginaDeAuditoria(pagina, siguiente);
    }

    private static int tamano(String pedido) {
        if (pedido == null) {
            return TAMANO_POR_DEFECTO;
        }
        try {
            int tamano = Integer.parseInt(pedido);
            if (tamano < 1 || tamano > TAMANO_MAXIMO) {
                throw ErrorDeFiltro.en("tamano");
            }
            return tamano;
        } catch (NumberFormatException e) {
            throw ErrorDeFiltro.en("tamano");
        }
    }

    /** Clave propia del cursor: un cursor firmado nunca sirve como JWT, aunque ambos salgan del mismo secreto. */
    private static byte[] derivarClave(String secreto) {
        try {
            Mac mac = Mac.getInstance(ALGORITMO);
            mac.init(new SecretKeySpec(secreto.getBytes(StandardCharsets.UTF_8), ALGORITMO));
            return mac.doFinal(PROPOSITO_DE_LA_CLAVE.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("No se pudo derivar la clave del cursor de auditoría");
        }
    }
}
