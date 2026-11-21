package ar.sistemaodontologico.consultorio.application;

import ar.sistemaodontologico.compartido.domain.ConsultorioId;
import ar.sistemaodontologico.consultorio.domain.GrillaSemanal;

/** Puerto de persistencia de la grilla semanal; todo filtra por consultorio (regla dura 6). */
public interface DisponibilidadRepositorio {

    GrillaSemanal consultar(ConsultorioId consultorio);

    /**
     * Bloquea la fila del consultorio hasta el fin de la transacción, para serializar los reemplazos de su grilla
     * (revisión de Maxi en #15). Se llama antes de leer la grilla anterior, así el evento de auditoría compara contra
     * lo que realmente estaba guardado.
     */
    void bloquear(ConsultorioId consultorio);

    /**
     * Borra las franjas del consultorio e inserta las de la grilla, en la transacción de quien llama (design D2).
     * Bloquea primero la fila del consultorio: sin eso, el {@code DELETE} de una transacción no ve las filas que otra
     * todavía no confirmó, y dos grillas que no se solapan quedarían sumadas.
     */
    void reemplazar(ConsultorioId consultorio, GrillaSemanal grilla);
}
