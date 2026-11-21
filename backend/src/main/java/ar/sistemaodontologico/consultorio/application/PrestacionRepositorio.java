package ar.sistemaodontologico.consultorio.application;

import ar.sistemaodontologico.compartido.domain.ConsultorioId;
import ar.sistemaodontologico.consultorio.domain.TipoDePrestacion;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Puerto de persistencia de los tipos de prestación; todo filtra por consultorio (regla dura 6). */
public interface PrestacionRepositorio {

    /** Ordenadas por {@code orden} y nombre; solo las activas salvo que se pidan todas. */
    List<TipoDePrestacion> listar(ConsultorioId consultorio, boolean incluirInactivas);

    Optional<TipoDePrestacion> buscar(ConsultorioId consultorio, UUID id);

    /** Si otra prestación del consultorio (distinta de {@code excepto}) ya usa el nombre, sin distinguir mayúsculas. */
    boolean nombreEnUso(ConsultorioId consultorio, String nombre, UUID excepto);

    /** El orden que sigue al último del consultorio (1 si no hay ninguna). */
    int siguienteOrden(ConsultorioId consultorio);

    void guardarNueva(ConsultorioId consultorio, TipoDePrestacion prestacion);

    void actualizar(ConsultorioId consultorio, TipoDePrestacion prestacion);
}
