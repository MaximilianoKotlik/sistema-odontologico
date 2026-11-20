package ar.sistemaodontologico.compartido.application;

import ar.sistemaodontologico.compartido.domain.ConsultorioId;

/**
 * Consultorio activo de la operación en curso (spec aislamiento-tenant, design D1).
 *
 * <p>Lo puebla la capa de seguridad desde la sesión (C-04) o desde el slug o token de una ruta pública;
 * nunca desde el body ni la query. Los casos de uso lo leen una vez con {@link #actual()} y pasan el
 * {@link ConsultorioId} explícito a cada repositorio. Las tareas programadas, que no tienen petición,
 * reciben el consultorio como parámetro en cada iteración.
 */
public interface TenantContext {

    /** El consultorio activo; falla con {@link TenantNoResueltoException} si no hay ninguno. */
    ConsultorioId actual();

    boolean estaResuelto();

    /** Fija el consultorio activo. Falla si ya hay otro distinto: el tenant no cambia a mitad de una operación. */
    void establecer(ConsultorioId consultorio);
}
