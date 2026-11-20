package pruebas.arquitectura.repositorios.infrastructure;

/** Fixture válido: la infraestructura sí puede usar Spring Data. */
public class TurnoRepositorioAdaptador {

    private final TurnoSpringData springData;

    public TurnoRepositorioAdaptador(TurnoSpringData springData) {
        this.springData = springData;
    }
}
