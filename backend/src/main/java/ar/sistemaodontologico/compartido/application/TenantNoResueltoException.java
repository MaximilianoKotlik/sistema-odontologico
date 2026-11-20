package ar.sistemaodontologico.compartido.application;

/** Se pidió el consultorio activo en una operación que no lo tiene: es un error de programación (HTTP 500). */
public class TenantNoResueltoException extends IllegalStateException {

    public TenantNoResueltoException() {
        super("No hay un consultorio activo en esta operación");
    }
}
