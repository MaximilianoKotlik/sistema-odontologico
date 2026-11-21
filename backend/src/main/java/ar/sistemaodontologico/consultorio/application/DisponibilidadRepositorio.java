package ar.sistemaodontologico.consultorio.application;

import ar.sistemaodontologico.compartido.domain.ConsultorioId;
import ar.sistemaodontologico.consultorio.domain.GrillaSemanal;

/** Puerto de persistencia de la grilla semanal; todo filtra por consultorio (regla dura 6). */
public interface DisponibilidadRepositorio {

    GrillaSemanal consultar(ConsultorioId consultorio);

    /** Borra las franjas del consultorio e inserta las de la grilla, en la transacción de quien llama (design D2). */
    void reemplazar(ConsultorioId consultorio, GrillaSemanal grilla);
}
