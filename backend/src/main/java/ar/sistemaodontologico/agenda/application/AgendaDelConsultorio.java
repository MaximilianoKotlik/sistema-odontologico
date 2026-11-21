package ar.sistemaodontologico.agenda.application;

import ar.sistemaodontologico.agenda.domain.Bloqueo;
import ar.sistemaodontologico.agenda.domain.Turno;
import ar.sistemaodontologico.agenda.domain.TurnoId;
import ar.sistemaodontologico.agenda.domain.ValidadorDeBloqueo;
import ar.sistemaodontologico.agenda.domain.ValidadorDeSolapamiento;
import ar.sistemaodontologico.compartido.domain.ConsultorioId;
import ar.sistemaodontologico.compartido.domain.error.ConflictoDeNegocio;
import ar.sistemaodontologico.compartido.domain.error.RecursoNoEncontrado;
import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;

/**
 * Lo que comparten los casos de uso de agenda: cargar un turno con la versión que leyó el cliente y validar un turno
 * o un bloqueo contra la agenda del consultorio (design D5 y D7). Quien llama ya tomó el {@link LockDeAgenda}.
 */
@Component
class AgendaDelConsultorio {

    static final String PROPIEDAD_TURNO_EN_CONFLICTO = "turnoEnConflicto";

    private final TurnoRepositorio turnos;
    private final BloqueoRepositorio bloqueos;
    private final ConsultaDeDisponibilidad disponibilidad;

    AgendaDelConsultorio(TurnoRepositorio turnos, BloqueoRepositorio bloqueos, ConsultaDeDisponibilidad disponibilidad) {
        this.turnos = turnos;
        this.bloqueos = bloqueos;
        this.disponibilidad = disponibilidad;
    }

    /**
     * El turno del consultorio, si la versión es la que leyó el cliente. Uno ajeno o inexistente es 404 (RN-TE-04);
     * una versión vieja es {@code CONFLICTO_CONCURRENCIA} antes de aplicar nada (RN-GL-02).
     */
    Turno turnoEnSuVersion(ConsultorioId consultorio, TurnoId id, long versionEsperada) {
        TurnoGuardado guardado = turnos.buscar(consultorio, id).orElseThrow(() -> new RecursoNoEncontrado("turno"));
        if (guardado.version() != versionEsperada) {
            throw new OptimisticLockingFailureException("El turno cambió desde que se leyó");
        }
        return guardado.turno();
    }

    /**
     * Bloqueo → disponibilidad → solapamiento, con los turnos y bloqueos que tocan su horario (design D5). Un
     * {@code TURNO_SOLAPADO} suma {@code turnoEnConflicto} con el id, el inicio y el fin del primero por inicio, sin
     * datos del paciente (C-12 D6): C-14 lo resuelve con la agenda que ya tiene cargada.
     */
    void validarTurno(ConsultorioId consultorio, Turno turno) {
        List<Turno> activos = turnos.listarActivosQueIntersectan(consultorio, turno.intervalo());
        try {
            ValidadorDeSolapamiento.validar(turno, activos, bloqueos.listarQueIntersectan(consultorio,
                    turno.intervalo()), disponibilidad.de(consultorio));
        } catch (ConflictoDeNegocio error) {
            if (!ValidadorDeSolapamiento.CODIGO_SOLAPADO.equals(error.getCodigo())) {
                throw error;
            }
            throw conTurnoEnConflicto(error, activos);
        }
    }

    private static ConflictoDeNegocio conTurnoEnConflicto(ConflictoDeNegocio error, List<Turno> activos) {
        Collection<?> ids = (Collection<?>) error.getPropiedades().get(ValidadorDeSolapamiento.PROPIEDAD_TURNOS);
        Turno primero = activos.stream()
                .filter(otro -> ids.contains(otro.id().valor().toString()))
                .min(Comparator.comparing(Turno::inicio))
                .orElseThrow();
        Map<String, Object> propiedades = new LinkedHashMap<>(error.getPropiedades());
        propiedades.put(PROPIEDAD_TURNO_EN_CONFLICTO, campos("id", primero.id().valor().toString(),
                "inicio", primero.inicio(), "fin", primero.fin()));
        return new ConflictoDeNegocio(error.getCodigo(), error.getMessage(), propiedades);
    }

    /** Un bloqueo no pisa turnos activos, sobreturnos incluidos (RN-AG-05). */
    void validarBloqueo(ConsultorioId consultorio, Bloqueo bloqueo) {
        ValidadorDeBloqueo.validar(bloqueo, turnos.listarActivosQueIntersectan(consultorio, bloqueo.intervalo()));
    }

    /** Los campos auditables, con los nombres de las columnas y los instantes en ISO-8601 UTC (design D8). */
    static Map<String, Object> campos(Object... claveYValor) {
        Map<String, Object> campos = new LinkedHashMap<>();
        for (int i = 0; i < claveYValor.length; i += 2) {
            Object valor = claveYValor[i + 1];
            campos.put((String) claveYValor[i], valor instanceof Instant instante ? instante.toString() : valor);
        }
        return campos;
    }
}
