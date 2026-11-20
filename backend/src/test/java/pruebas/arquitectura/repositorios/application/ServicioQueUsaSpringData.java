package pruebas.arquitectura.repositorios.application;

import pruebas.arquitectura.repositorios.infrastructure.TurnoSpringData;

/** Fixture que viola la regla: un caso de uso usa Spring Data directo. */
public class ServicioQueUsaSpringData {

    private final TurnoSpringData springData;

    public ServicioQueUsaSpringData(TurnoSpringData springData) {
        this.springData = springData;
    }
}
