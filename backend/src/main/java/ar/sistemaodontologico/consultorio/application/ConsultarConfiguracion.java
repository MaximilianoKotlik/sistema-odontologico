package ar.sistemaodontologico.consultorio.application;

import ar.sistemaodontologico.compartido.application.TenantContext;
import ar.sistemaodontologico.compartido.domain.error.RecursoNoEncontrado;
import ar.sistemaodontologico.consultorio.domain.ConfiguracionDelConsultorio;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Configuración del consultorio de la sesión (US-004, spec configuracion-consultorio). */
@Service
public class ConsultarConfiguracion {

    private final ConsultorioRepositorio consultorios;
    private final TenantContext tenant;

    public ConsultarConfiguracion(ConsultorioRepositorio consultorios, TenantContext tenant) {
        this.consultorios = consultorios;
        this.tenant = tenant;
    }

    @Transactional(readOnly = true)
    public ConfiguracionDelConsultorio consultar() {
        return consultorios.buscarConfiguracion(tenant.actual())
                .orElseThrow(() -> new RecursoNoEncontrado("consultorio"));
    }
}
