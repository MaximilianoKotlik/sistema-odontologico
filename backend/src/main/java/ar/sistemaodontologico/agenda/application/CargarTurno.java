package ar.sistemaodontologico.agenda.application;

import ar.sistemaodontologico.agenda.domain.OrigenPaciente;
import ar.sistemaodontologico.agenda.domain.PacienteId;
import ar.sistemaodontologico.compartido.domain.error.ValidacionDeNegocio;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * El odontólogo carga un turno para un paciente existente o para uno nuevo del alta rápida, en una sola transacción
 * (C-12, design D10; US-021, FL-03). Si el DNI ya existe no hay error: se reutiliza el paciente con sus datos
 * guardados (C-11 D6, decisión de Maxi en #38); si el nombre difiere, queda marcado y el turno creado es el turno de
 * origen de lo informado (DD-38). Si el turno falla, el paciente nuevo tampoco queda.
 */
@Service
public class CargarTurno {

    /** Exactamente uno de {@code paciente} o {@code pacienteNuevo}. */
    public record Pedido(Optional<PacienteId> paciente, Optional<DatosDePaciente> pacienteNuevo, UUID prestacion,
            Instant inicio, Optional<Integer> duracionMin, boolean sobreturno) {

        public Pedido {
            Objects.requireNonNull(paciente, "paciente");
            Objects.requireNonNull(pacienteNuevo, "pacienteNuevo");
            Objects.requireNonNull(duracionMin, "duracionMin");
        }
    }

    public record Resultado(TurnoGuardado turno, boolean pacienteReutilizado) {
    }

    private final RegistrarPaciente registrar;
    private final CrearTurno crear;

    CargarTurno(RegistrarPaciente registrar, CrearTurno crear) {
        this.registrar = registrar;
        this.crear = crear;
    }

    @Transactional
    public Resultado cargar(Pedido pedido, Autor autor) {
        if (pedido.paciente().isPresent() == pedido.pacienteNuevo().isPresent()) {
            throw new ValidacionDeNegocio("VALIDACION", "Indicá un paciente existente o uno nuevo");
        }
        Optional<PacienteRegistrado> registrado = pedido.pacienteNuevo()
                .map(datos -> registrar.registrar(datos, OrigenPaciente.MANUAL, null, autor));
        PacienteId paciente = registrado.map(r -> r.paciente().id()).orElseGet(() -> pedido.paciente().orElseThrow());
        TurnoGuardado turno = crear.crear(new NuevoTurno(paciente, pedido.prestacion(), pedido.inicio(),
                pedido.duracionMin(), pedido.sobreturno()), autor);
        registrado.ifPresent(r -> registrar.vincularTurnoDeOrigen(r, turno.turno().id()));
        return new Resultado(turno, registrado.map(PacienteRegistrado::reutilizado).orElse(false));
    }
}
