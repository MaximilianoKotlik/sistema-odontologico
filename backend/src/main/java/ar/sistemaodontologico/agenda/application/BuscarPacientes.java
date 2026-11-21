package ar.sistemaodontologico.agenda.application;

import ar.sistemaodontologico.agenda.domain.CriterioDeBusqueda;
import ar.sistemaodontologico.compartido.application.TenantContext;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * El buscador de pacientes del turno manual (C-12, spec busqueda-de-pacientes, design D7; C-14 D4): por nombre y
 * apellido, por DNI o por teléfono, solo del consultorio de la sesión y con un tope de 20.
 */
@Service
public class BuscarPacientes {

    public static final int TOPE = 20;

    private final BusquedaDePacientesRepositorio busqueda;
    private final TenantContext tenant;

    BuscarPacientes(BusquedaDePacientesRepositorio busqueda, TenantContext tenant) {
        this.busqueda = busqueda;
        this.tenant = tenant;
    }

    @Transactional(readOnly = true)
    public List<PacienteEncontrado> buscar(String texto) {
        return busqueda.listarCoincidencias(tenant.actual(), CriterioDeBusqueda.de(texto), TOPE);
    }
}
