package ar.sistemaodontologico.consultorio.application;

import ar.sistemaodontologico.compartido.application.PublicadorDeAuditoria;
import ar.sistemaodontologico.compartido.application.SesionActual;
import ar.sistemaodontologico.compartido.application.TenantContext;
import ar.sistemaodontologico.compartido.domain.ConsultorioId;
import ar.sistemaodontologico.compartido.domain.auditoria.Actor;
import ar.sistemaodontologico.compartido.domain.auditoria.ActorTipo;
import ar.sistemaodontologico.compartido.domain.auditoria.CambioAuditado;
import ar.sistemaodontologico.compartido.domain.error.ConflictoDeNegocio;
import ar.sistemaodontologico.compartido.domain.error.RecursoNoEncontrado;
import ar.sistemaodontologico.compartido.domain.slug.ValidadorDeSlug;
import ar.sistemaodontologico.compartido.domain.telefono.NormalizadorDeTelefono;
import ar.sistemaodontologico.consultorio.domain.ConfiguracionDelConsultorio;
import ar.sistemaodontologico.consultorio.domain.DatosDelConsultorio;
import ar.sistemaodontologico.consultorio.domain.Diferencias;
import ar.sistemaodontologico.consultorio.domain.ReglasDeReserva;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reemplaza los datos editables del consultorio de la sesión y audita solo lo que cambió (US-004, spec
 * configuracion-consultorio y el delta de slug-consultorio; design D4, D7 y D9).
 */
@Service
public class EditarConfiguracion {

    public static final String ACCION = "CONSULTORIO_CONFIG_CAMBIADA";
    static final int SUGERENCIAS_DE_SLUG = 3;

    /** Lo que manda el odontólogo; el teléfono todavía sin normalizar. */
    public record Pedido(String nombre, String slug, String direccion, String telefonoContacto, String zonaHoraria,
            int anticipacionCancelacionMin, int anticipacionReservaMin, int horizonteReservaDias, int pasoHuecosMin,
            boolean reservaPublicaActiva) {
    }

    private final ConsultorioRepositorio consultorios;
    private final AsignadorDeSlugUnico slugs;
    private final TenantContext tenant;
    private final SesionActual sesion;
    private final PublicadorDeAuditoria auditoria;

    public EditarConfiguracion(ConsultorioRepositorio consultorios, AsignadorDeSlugUnico slugs, TenantContext tenant,
            SesionActual sesion, PublicadorDeAuditoria auditoria) {
        this.consultorios = consultorios;
        this.slugs = slugs;
        this.tenant = tenant;
        this.sesion = sesion;
        this.auditoria = auditoria;
    }

    @Transactional
    public ConfiguracionDelConsultorio editar(Pedido pedido) {
        ConsultorioId id = tenant.actual();
        ConfiguracionDelConsultorio actual = consultorios.buscarConfiguracion(id)
                .orElseThrow(() -> new RecursoNoEncontrado("consultorio"));
        DatosDelConsultorio nuevos = new DatosDelConsultorio(pedido.nombre(), pedido.slug(), pedido.direccion(),
                NormalizadorDeTelefono.normalizar(pedido.telefonoContacto()), pedido.zonaHoraria(),
                new ReglasDeReserva(pedido.anticipacionCancelacionMin(), pedido.anticipacionReservaMin(),
                        pedido.horizonteReservaDias(), pedido.pasoHuecosMin()),
                pedido.reservaPublicaActiva());
        if (!nuevos.slug().equals(actual.datos().slug())) {
            validarSlugNuevo(nuevos.slug());
        }
        Diferencias diferencias = Diferencias.entre(actual.datos().comoMapaAuditable(), nuevos.comoMapaAuditable());
        if (!diferencias.hayCambios()) {
            return actual;
        }
        consultorios.actualizarDatos(id, nuevos, actual.version());
        auditoria.publicar(new CambioAuditado(id, new Actor(ActorTipo.ODONTOLOGO, sesion.usuarioId()), "consultorio",
                id.valor(), ACCION, diferencias.anterior(), diferencias.nuevo(), sesion.ip().orElse(null)));
        return new ConfiguracionDelConsultorio(id, nuevos, actual.plan(), actual.pruebaHasta(), actual.version() + 1);
    }

    private void validarSlugNuevo(String slug) {
        ValidadorDeSlug.validar(slug);
        if (!slugs.estaLibre(slug)) {
            // Una carrera entre este chequeo y el UPDATE la frena uk_consultorio_slug, sin sugerencias (design D7).
            throw new ConflictoDeNegocio("SLUG_EN_USO", "El slug ya está en uso",
                    Map.of("sugerencias", slugs.sugerencias(slug, SUGERENCIAS_DE_SLUG)));
        }
    }
}
