package ar.sistemaodontologico.agenda.application;

import ar.sistemaodontologico.agenda.domain.Consentimiento;
import ar.sistemaodontologico.agenda.domain.DatosInformados;
import ar.sistemaodontologico.agenda.domain.OrigenPaciente;
import ar.sistemaodontologico.agenda.domain.Paciente;
import ar.sistemaodontologico.agenda.domain.PacienteId;
import ar.sistemaodontologico.agenda.domain.TurnoId;
import ar.sistemaodontologico.compartido.application.PublicadorDeAuditoria;
import ar.sistemaodontologico.compartido.application.TenantContext;
import ar.sistemaodontologico.compartido.domain.ConsultorioId;
import ar.sistemaodontologico.compartido.domain.GeneradorDeIds;
import ar.sistemaodontologico.compartido.domain.auditoria.CambioAuditado;
import java.time.Clock;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Alta idempotente del paciente por DNI, desde el alta rápida del odontólogo o desde la reserva pública (spec
 * registro-de-pacientes, design D6; RN-RP-03, DD-24, DD-38).
 *
 * <p>Un DNI nuevo crea el paciente. Un DNI existente lo reutiliza sin sobrescribir la ficha; si el nombre o el
 * apellido difieren ({@link Paciente#datosDifierenDe}), lo marca para revisión y guarda lo informado, pisando lo de una
 * reserva anterior. El turno de origen se completa después de crear el turno, con {@link #vincularTurnoDeOrigen}.
 */
@Service
public class RegistrarPaciente {

    public static final String ACCION_CREADO = "PACIENTE_CREADO";
    public static final String ACCION_MARCADO = "PACIENTE_MARCADO_PARA_REVISION";
    public static final String ENTIDAD = "paciente";

    private final PacienteRepositorio pacientes;
    private final LockDeAgenda lock;
    private final TenantContext tenant;
    private final PublicadorDeAuditoria auditoria;
    private final GeneradorDeIds ids;
    private final Clock reloj;

    public RegistrarPaciente(PacienteRepositorio pacientes, LockDeAgenda lock, TenantContext tenant,
            PublicadorDeAuditoria auditoria, GeneradorDeIds ids, Clock reloj) {
        this.pacientes = pacientes;
        this.lock = lock;
        this.tenant = tenant;
        this.auditoria = auditoria;
        this.ids = ids;
        this.reloj = reloj;
    }

    @Transactional
    public PacienteRegistrado registrar(DatosDePaciente datos, OrigenPaciente origen, Consentimiento consentimiento,
            Autor autor) {
        ConsultorioId consultorio = tenant.actual();
        // Valida y normaliza antes de tocar la base: un DNI o un teléfono inválidos no registran nada.
        Paciente candidato = Paciente.nuevo(new PacienteId(ids.nuevo()), datos.nombre(), datos.apellido(), datos.dni(),
                datos.telefono(), datos.email(), origen, consentimiento);
        lock.tomar(consultorio);
        if (pacientes.insertarSiNoExiste(consultorio, candidato)) {
            auditar(consultorio, autor, candidato, ACCION_CREADO, null, Map.of("origen", origen.name()));
            return new PacienteRegistrado(candidato, false, false);
        }
        Paciente existente = pacientes.buscarPorDniParaActualizar(consultorio, candidato.dni()).orElseThrow();
        if (!existente.datosDifierenDe(candidato.nombre(), candidato.apellido())) {
            return new PacienteRegistrado(existente, true, false);
        }
        Paciente marcado = existente.marcadoParaRevision(new DatosInformados(candidato.nombre(), candidato.apellido(),
                candidato.email(), reloj.instant(), null));
        pacientes.guardarRevision(consultorio, marcado);
        // Sin lo informado (RN-AD-05). También con true → true: cambió lo informado del paciente (regla dura 7).
        auditar(consultorio, autor, marcado, ACCION_MARCADO, Map.of("requiere_revision", existente.requiereRevision()),
                Map.of("requiere_revision", true));
        return new PacienteRegistrado(marcado, true, true);
    }

    /**
     * Completa el turno de origen de lo informado, en la misma transacción que creó el turno (DD-38). Si ese
     * registro no marcó al paciente, no hace nada. Es parte de la misma marca: no emite otro evento.
     */
    @Transactional
    public void vincularTurnoDeOrigen(PacienteRegistrado registrado, TurnoId turno) {
        if (!registrado.marcadoParaRevision()) {
            return;
        }
        ConsultorioId consultorio = tenant.actual();
        lock.tomar(consultorio);
        pacientes.vincularTurnoDeOrigen(consultorio, registrado.paciente().id(), turno);
    }

    private void auditar(ConsultorioId consultorio, Autor autor, Paciente paciente, String accion,
            Map<String, Object> anterior, Map<String, Object> nuevo) {
        auditoria.publicar(new CambioAuditado(consultorio, autor.actorPara(paciente.id()), ENTIDAD,
                paciente.id().valor(), accion, anterior, nuevo, autor.ipONull()));
    }
}
