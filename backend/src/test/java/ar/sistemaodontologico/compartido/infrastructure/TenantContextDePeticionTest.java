package ar.sistemaodontologico.compartido.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.sistemaodontologico.compartido.application.TenantNoResueltoException;
import ar.sistemaodontologico.compartido.domain.ConsultorioId;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Spec aislamiento-tenant — Requirement: El consultorio activo se resuelve solo desde el contexto. */
class TenantContextDePeticionTest {

    private final TenantContextDePeticion contexto = new TenantContextDePeticion();

    @Test
    void sinTenantResueltoFalla() {
        assertThatThrownBy(contexto::actual).isInstanceOf(TenantNoResueltoException.class);
        assertThat(contexto.estaResuelto()).isFalse();
    }

    @Test
    void devuelveElTenantEstablecido() {
        ConsultorioId id = new ConsultorioId(UUID.randomUUID());

        contexto.establecer(id);

        assertThat(contexto.actual()).isEqualTo(id);
        assertThat(contexto.estaResuelto()).isTrue();
    }

    @Test
    void establecerElMismoTenantDosVecesEsIdempotente() {
        ConsultorioId id = new ConsultorioId(UUID.randomUUID());
        contexto.establecer(id);

        contexto.establecer(new ConsultorioId(id.valor()));

        assertThat(contexto.actual()).isEqualTo(id);
    }

    @Test
    void noPermiteReemplazarElTenantPorOtro() {
        contexto.establecer(new ConsultorioId(UUID.randomUUID()));

        assertThatThrownBy(() -> contexto.establecer(new ConsultorioId(UUID.randomUUID())))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void elConsultorioIdNoAceptaNulos() {
        assertThatThrownBy(() -> new ConsultorioId(null)).isInstanceOf(NullPointerException.class);
    }
}
