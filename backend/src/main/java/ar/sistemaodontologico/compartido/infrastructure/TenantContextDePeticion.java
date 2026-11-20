package ar.sistemaodontologico.compartido.infrastructure;

import ar.sistemaodontologico.compartido.application.TenantContext;
import ar.sistemaodontologico.compartido.application.TenantNoResueltoException;
import ar.sistemaodontologico.compartido.domain.ConsultorioId;
import java.util.Objects;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

/** {@link TenantContext} con alcance de petición HTTP. */
@Component
@RequestScope
public class TenantContextDePeticion implements TenantContext {

    private ConsultorioId consultorio;

    @Override
    public ConsultorioId actual() {
        if (consultorio == null) {
            throw new TenantNoResueltoException();
        }
        return consultorio;
    }

    @Override
    public boolean estaResuelto() {
        return consultorio != null;
    }

    @Override
    public void establecer(ConsultorioId nuevo) {
        Objects.requireNonNull(nuevo, "consultorio");
        if (consultorio != null && !consultorio.equals(nuevo)) {
            throw new IllegalStateException("El consultorio activo ya está establecido y es otro");
        }
        consultorio = nuevo;
    }
}
