package ar.sistemaodontologico.consultorio.application;

import ar.sistemaodontologico.compartido.application.PublicadorDeAuditoria;
import ar.sistemaodontologico.compartido.application.SesionActual;
import ar.sistemaodontologico.compartido.application.TenantContext;
import ar.sistemaodontologico.compartido.domain.ConsultorioId;
import ar.sistemaodontologico.compartido.domain.GeneradorDeIds;
import ar.sistemaodontologico.compartido.domain.auditoria.Actor;
import ar.sistemaodontologico.compartido.domain.auditoria.ActorTipo;
import ar.sistemaodontologico.compartido.domain.auditoria.CambioAuditado;
import ar.sistemaodontologico.compartido.domain.error.ConflictoDeNegocio;
import ar.sistemaodontologico.compartido.domain.error.RecursoNoEncontrado;
import ar.sistemaodontologico.consultorio.domain.Diferencias;
import ar.sistemaodontologico.consultorio.domain.TipoDePrestacion;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Alta, edición, desactivación y activación de los tipos de prestación del consultorio de la sesión, cada una
 * auditada en su transacción (US-010, spec prestaciones; design D4 y D8).
 */
@Service
public class GestionarPrestaciones {

    public static final String ENTIDAD = "tipo_prestacion";
    public static final String CODIGO_DUPLICADA = "PRESTACION_DUPLICADA";

    /** Datos del alta o la edición; {@code visiblePublica} y {@code orden} son opcionales. */
    public record Pedido(String nombre, int duracionMin, Boolean visiblePublica, Integer orden) {
    }

    private final PrestacionRepositorio prestaciones;
    private final TenantContext tenant;
    private final SesionActual sesion;
    private final PublicadorDeAuditoria auditoria;
    private final GeneradorDeIds ids;

    public GestionarPrestaciones(PrestacionRepositorio prestaciones, TenantContext tenant, SesionActual sesion,
            PublicadorDeAuditoria auditoria, GeneradorDeIds ids) {
        this.prestaciones = prestaciones;
        this.tenant = tenant;
        this.sesion = sesion;
        this.auditoria = auditoria;
        this.ids = ids;
    }

    @Transactional(readOnly = true)
    public List<TipoDePrestacion> listar(boolean incluirInactivas) {
        return prestaciones.listar(tenant.actual(), incluirInactivas);
    }

    @Transactional(readOnly = true)
    public TipoDePrestacion consultar(UUID id) {
        return existente(tenant.actual(), id);
    }

    @Transactional
    public TipoDePrestacion crear(Pedido pedido) {
        ConsultorioId consultorio = tenant.actual();
        int orden = pedido.orden() != null ? pedido.orden() : prestaciones.siguienteOrden(consultorio);
        TipoDePrestacion nueva = TipoDePrestacion.nueva(ids.nuevo(), pedido.nombre(), pedido.duracionMin(),
                pedido.visiblePublica() == null || pedido.visiblePublica(), orden);
        validarNombreLibre(consultorio, nueva);
        prestaciones.guardarNueva(consultorio, nueva);
        auditar(consultorio, nueva.id(), "PRESTACION_CREADA", null, nueva.comoMapaAuditable());
        return nueva;
    }

    @Transactional
    public TipoDePrestacion editar(UUID id, Pedido pedido) {
        ConsultorioId consultorio = tenant.actual();
        TipoDePrestacion actual = existente(consultorio, id);
        TipoDePrestacion editada = actual.editada(pedido.nombre(), pedido.duracionMin(),
                pedido.visiblePublica() == null ? actual.visiblePublica() : pedido.visiblePublica(),
                pedido.orden() == null ? actual.orden() : pedido.orden());
        validarNombreLibre(consultorio, editada);
        return guardarSiCambio(consultorio, actual, editada, "PRESTACION_CAMBIADA");
    }

    @Transactional
    public TipoDePrestacion desactivar(UUID id) {
        ConsultorioId consultorio = tenant.actual();
        TipoDePrestacion actual = existente(consultorio, id);
        return guardarSiCambio(consultorio, actual, actual.desactivada(), "PRESTACION_DESACTIVADA");
    }

    @Transactional
    public TipoDePrestacion activar(UUID id) {
        ConsultorioId consultorio = tenant.actual();
        TipoDePrestacion actual = existente(consultorio, id);
        return guardarSiCambio(consultorio, actual, actual.activada(), "PRESTACION_ACTIVADA");
    }

    private TipoDePrestacion guardarSiCambio(ConsultorioId consultorio, TipoDePrestacion actual,
            TipoDePrestacion nueva, String accion) {
        Diferencias diferencias = Diferencias.entre(actual.comoMapaAuditable(), nueva.comoMapaAuditable());
        if (!diferencias.hayCambios()) {
            return actual;
        }
        prestaciones.actualizar(consultorio, nueva);
        auditar(consultorio, nueva.id(), accion, diferencias.anterior(), diferencias.nuevo());
        return nueva;
    }

    private TipoDePrestacion existente(ConsultorioId consultorio, UUID id) {
        // La de otro consultorio responde igual que una inexistente (RN-TE-04).
        return prestaciones.buscar(consultorio, id).orElseThrow(() -> new RecursoNoEncontrado("prestación"));
    }

    private void validarNombreLibre(ConsultorioId consultorio, TipoDePrestacion prestacion) {
        // Una carrera la frena uk_tipo_prestacion_nombre, mapeada al mismo código (design D8).
        if (prestaciones.nombreEnUso(consultorio, prestacion.nombre(), prestacion.id())) {
            throw new ConflictoDeNegocio(CODIGO_DUPLICADA, "Ya hay una prestación con ese nombre");
        }
    }

    private void auditar(ConsultorioId consultorio, UUID prestacion, String accion, Map<String, Object> anterior,
            Map<String, Object> nuevo) {
        auditoria.publicar(new CambioAuditado(consultorio, new Actor(ActorTipo.ODONTOLOGO, sesion.usuarioId()),
                ENTIDAD, prestacion, accion, anterior, nuevo, sesion.ip().orElse(null)));
    }
}
