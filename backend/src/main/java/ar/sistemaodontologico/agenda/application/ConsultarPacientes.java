package ar.sistemaodontologico.agenda.application;

import ar.sistemaodontologico.compartido.application.TenantContext;
import ar.sistemaodontologico.compartido.domain.error.RecursoNoEncontrado;
import ar.sistemaodontologico.compartido.domain.error.ValidacionDeNegocio;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * La lista paginada y el detalle de los pacientes del consultorio de la sesión (C-17, spec revision-pacientes,
 * design D4 y D10). Un paciente de otro consultorio no existe para esta sesión (RN-TE-04).
 */
@Service
public class ConsultarPacientes {

    public static final int TAMANO_POR_DEFECTO = 20;
    public static final int TAMANO_MAXIMO = 50;

    private final ListadoDePacientesRepositorio listado;
    private final TenantContext tenant;

    ConsultarPacientes(ListadoDePacientesRepositorio listado, TenantContext tenant) {
        this.listado = listado;
        this.tenant = tenant;
    }

    @Transactional(readOnly = true)
    public PaginaDePacientes listar(boolean soloConRevision, int pagina, int tamano) {
        if (pagina < 0 || tamano < 1 || tamano > TAMANO_MAXIMO) {
            throw new ValidacionDeNegocio("VALIDACION",
                    "La página empieza en 0 y el tamaño va de 1 a " + TAMANO_MAXIMO);
        }
        return listado.pagina(tenant.actual(), soloConRevision, Math.multiplyExact(pagina, tamano), tamano);
    }

    @Transactional(readOnly = true)
    public PacienteDeLaLista detalle(UUID id) {
        return listado.buscar(tenant.actual(), id).orElseThrow(() -> new RecursoNoEncontrado("paciente"));
    }
}
