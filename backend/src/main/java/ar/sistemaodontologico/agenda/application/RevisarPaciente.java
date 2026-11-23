package ar.sistemaodontologico.agenda.application;

import ar.sistemaodontologico.agenda.domain.Paciente;
import ar.sistemaodontologico.agenda.domain.PacienteId;
import ar.sistemaodontologico.compartido.application.PublicadorDeAuditoria;
import ar.sistemaodontologico.compartido.application.TenantContext;
import ar.sistemaodontologico.compartido.domain.ConsultorioId;
import ar.sistemaodontologico.compartido.domain.auditoria.CambioAuditado;
import ar.sistemaodontologico.compartido.domain.error.RecursoNoEncontrado;
import ar.sistemaodontologico.compartido.domain.error.ValidacionDeNegocio;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resuelve la revisión de un paciente marcado por la deduplicación (C-17, spec revision-pacientes): confirmar mantiene
 * sus datos y corregir cambia nombre, apellido o email. Las dos sacan la marca y borran lo informado (DD-38).
 *
 * <p>Toma el {@link LockDeAgenda} antes de leer al paciente, en la misma cola que {@link RegistrarPaciente}, y aplica
 * la revisión solo sobre lo que vio el odontólogo: si el {@code informadoAt} no es el guardado, llegó otra reserva y
 * responde {@code CONFLICTO_CONCURRENCIA} sin cambiar nada (design D9). Sin revisión pendiente es
 * {@code SIN_REVISION_PENDIENTE}, que se evalúa primero (design D5). {@code PACIENTE_REVISADO} lleva solo lo que
 * cambió más {@code requiere_revision}, nunca lo informado (design D6, RN-AD-05).
 */
@Service
public class RevisarPaciente {

    public static final String ACCION_REVISADO = "PACIENTE_REVISADO";

    private final PacienteRepositorio pacientes;
    private final LockDeAgenda lock;
    private final TenantContext tenant;
    private final PublicadorDeAuditoria auditoria;

    RevisarPaciente(PacienteRepositorio pacientes, LockDeAgenda lock, TenantContext tenant,
            PublicadorDeAuditoria auditoria) {
        this.pacientes = pacientes;
        this.lock = lock;
        this.tenant = tenant;
        this.auditoria = auditoria;
    }

    @Transactional
    public void confirmar(PacienteId id, Instant informadoAt, Autor autor) {
        aplicar(id, informadoAt, autor, Paciente::revisado);
    }

    @Transactional
    public void corregir(PacienteId id, Instant informadoAt, DatosCorregidos datos, Autor autor) {
        Objects.requireNonNull(datos, "datos");
        aplicar(id, informadoAt, autor,
                paciente -> paciente.corregido(datos.nombre(), datos.apellido(), datos.email()));
    }

    private void aplicar(PacienteId id, Instant informadoAt, Autor autor,
            java.util.function.UnaryOperator<Paciente> revision) {
        autor.exigirOdontologo();
        if (informadoAt == null) {
            throw new ValidacionDeNegocio("VALIDACION", "Falta el informadoAt de la revisión");
        }
        ConsultorioId consultorio = tenant.actual();
        lock.tomar(consultorio);
        Paciente antes = pacientes.buscar(consultorio, id).orElseThrow(() -> new RecursoNoEncontrado("paciente"));
        // Primero SIN_REVISION_PENDIENTE y los datos inválidos; después, la variante vista (D5 y D9).
        Paciente despues = revision.apply(antes);
        if (!antes.informados().informadoAt().equals(informadoAt)) {
            throw new OptimisticLockingFailureException("Llegó otra reserva para el paciente");
        }
        pacientes.guardarFichaRevisada(consultorio, despues);
        auditar(consultorio, autor, antes, despues);
    }

    private void auditar(ConsultorioId consultorio, Autor autor, Paciente antes, Paciente despues) {
        Map<String, Object> anterior = new LinkedHashMap<>();
        Map<String, Object> nuevo = new LinkedHashMap<>();
        cambio("nombre", antes.nombre(), despues.nombre(), anterior, nuevo);
        cambio("apellido", antes.apellido(), despues.apellido(), anterior, nuevo);
        cambio("email", antes.email(), despues.email(), anterior, nuevo);
        anterior.put("requiere_revision", true);
        nuevo.put("requiere_revision", false);
        auditoria.publicar(new CambioAuditado(consultorio, autor.actorPara(antes.id()), RegistrarPaciente.ENTIDAD,
                antes.id().valor(), ACCION_REVISADO, anterior, nuevo, autor.ipONull()));
    }

    private static void cambio(String campo, String antes, String despues, Map<String, Object> anterior,
            Map<String, Object> nuevo) {
        if (!Objects.equals(antes, despues)) {
            anterior.put(campo, antes);
            nuevo.put(campo, despues);
        }
    }
}
