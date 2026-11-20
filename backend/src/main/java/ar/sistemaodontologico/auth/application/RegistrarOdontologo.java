package ar.sistemaodontologico.auth.application;

import ar.sistemaodontologico.auth.domain.NuevoUsuario;
import ar.sistemaodontologico.auth.domain.PoliticaDeContrasena;
import ar.sistemaodontologico.compartido.application.PublicadorDeAuditoria;
import ar.sistemaodontologico.compartido.domain.ConsultorioId;
import ar.sistemaodontologico.compartido.domain.GeneradorDeIds;
import ar.sistemaodontologico.compartido.domain.auditoria.Actor;
import ar.sistemaodontologico.compartido.domain.auditoria.ActorTipo;
import ar.sistemaodontologico.compartido.domain.auditoria.CambioAuditado;
import ar.sistemaodontologico.compartido.domain.error.ConflictoDeNegocio;
import ar.sistemaodontologico.compartido.domain.error.ValidacionDeNegocio;
import ar.sistemaodontologico.consultorio.application.AltaDeConsultorio;
import ar.sistemaodontologico.consultorio.application.AltaDeConsultorio.ConsultorioCreado;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Registro del odontólogo con su consultorio en prueba, en una sola transacción (US-001, spec
 * registro-odontologo). Si dos registros simultáneos eligen el mismo slug, se reintenta en una transacción nueva
 * hasta {@value #INTENTOS} veces (design D6).
 */
@Service
public class RegistrarOdontologo {

    static final int INTENTOS = 3;
    private static final String CONSTRAINT_SLUG = "uk_consultorio_slug";

    public record Solicitud(String nombre, String apellido, String email, String password, String nombreConsultorio,
            Boolean aceptaTerminos) {

        @Override
        public String toString() {
            return "Solicitud[consultorio=" + nombreConsultorio + "]";
        }
    }

    public record Resultado(UUID usuarioId, ConsultorioId consultorioId, String slug) {
    }

    private final UsuarioRepositorio usuarios;
    private final AltaDeConsultorio consultorios;
    private final HasheadorDeContrasena hasheador;
    private final GeneradorDeIds ids;
    private final Clock reloj;
    private final String terminosVersionVigente;
    private final TransactionTemplate transaccion;
    private final PublicadorDeAuditoria auditoria;

    public RegistrarOdontologo(UsuarioRepositorio usuarios, AltaDeConsultorio consultorios,
            HasheadorDeContrasena hasheador, GeneradorDeIds ids, Clock reloj, TerminosVigentes terminos,
            PlatformTransactionManager transacciones, PublicadorDeAuditoria auditoria) {
        this.usuarios = usuarios;
        this.consultorios = consultorios;
        this.hasheador = hasheador;
        this.ids = ids;
        this.reloj = reloj;
        this.terminosVersionVigente = terminos.version();
        this.transaccion = new TransactionTemplate(transacciones);
        this.auditoria = auditoria;
    }

    /** @param ip IP de origen, para el evento {@code CUENTA_CREADA} (C-05, design D9) */
    public Resultado registrar(Solicitud solicitud, String ip) {
        if (!Boolean.TRUE.equals(solicitud.aceptaTerminos())) {
            throw new ValidacionDeNegocio("TERMINOS_NO_ACEPTADOS", "Hay que aceptar los términos");
        }
        PoliticaDeContrasena.validar(solicitud.password(), solicitud.email());
        // El hash (costo 12) se calcula una sola vez, fuera de la transacción y de los reintentos.
        String hash = hasheador.hashear(solicitud.password());
        for (int intento = 1; ; intento++) {
            try {
                return transaccion.execute(estado -> registrarEnTransaccion(solicitud, hash, ip));
            } catch (DataIntegrityViolationException e) {
                boolean carreraPorSlug = String.valueOf(e.getMostSpecificCause().getMessage()).contains(CONSTRAINT_SLUG);
                if (!carreraPorSlug || intento >= INTENTOS) {
                    throw e;
                }
            }
        }
    }

    private Resultado registrarEnTransaccion(Solicitud solicitud, String hash, String ip) {
        if (usuarios.buscarPorEmail(solicitud.email()).isPresent()) {
            throw new ConflictoDeNegocio("EMAIL_EN_USO", "El email ya está registrado");
        }
        ConsultorioCreado consultorio = consultorios.darDeAlta(solicitud.nombreConsultorio().trim());
        UUID usuario = ids.nuevo();
        usuarios.guardarNuevo(new NuevoUsuario(usuario, consultorio.id(), solicitud.email(), hash,
                solicitud.nombre().trim(), solicitud.apellido().trim(), Instant.now(reloj), terminosVersionVigente));
        // En la misma transacción: si el registro se revierte, el evento tampoco queda (RN-AD-03).
        Map<String, Object> cuenta = new LinkedHashMap<>();
        cuenta.put("slug", consultorio.slug());
        cuenta.put("plan", consultorio.plan());
        cuenta.put("prueba_hasta", consultorio.pruebaHasta().toString());
        auditoria.publicar(new CambioAuditado(consultorio.id(), new Actor(ActorTipo.ODONTOLOGO, usuario),
                "usuario", usuario, "CUENTA_CREADA", null, cuenta, ip));
        return new Resultado(usuario, consultorio.id(), consultorio.slug());
    }
}
