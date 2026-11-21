package ar.sistemaodontologico.exportacion.application;

import ar.sistemaodontologico.compartido.domain.ConsultorioId;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Quién pidió la exportación, armado en el hilo de la petición (C-19, design D2): el streaming corre en otro hilo, donde
 * {@code TenantContext} y {@code SesionActual} no tienen datos.
 *
 * @param ip IP de origen, si se conoce
 */
public record PedidoDeExportacion(ConsultorioId consultorio, UUID usuarioId, String ip, Instant solicitadoAt) {

    public PedidoDeExportacion {
        Objects.requireNonNull(consultorio, "consultorio");
        Objects.requireNonNull(usuarioId, "usuarioId");
        Objects.requireNonNull(solicitadoAt, "solicitadoAt");
    }
}
