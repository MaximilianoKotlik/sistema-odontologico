package ar.sistemaodontologico.consultorio.application;

import ar.sistemaodontologico.consultorio.domain.NuevoConsultorio;

/**
 * Puerto de persistencia del consultorio. Es la raíz del tenant: se busca por su propio id o por slug, por eso
 * está en {@code REPOSITORIOS_EXCEPTUADOS} de {@code ArchitectureTest} (C-02, design D9).
 */
public interface ConsultorioRepositorio extends SlugsEnUso {

    void guardarNuevo(NuevoConsultorio consultorio);
}
