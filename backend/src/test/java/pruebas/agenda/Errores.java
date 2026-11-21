package pruebas.agenda;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.sistemaodontologico.compartido.domain.error.ErrorDeNegocio;
import java.util.Map;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;

/** Solo de test (C-11): verifica el código estable de un {@link ErrorDeNegocio} y, si hace falta, sus propiedades. */
public final class Errores {

    private Errores() {
    }

    public static void assertCodigo(ThrowingCallable accion, String codigo) {
        assertThatThrownBy(accion).isInstanceOfSatisfying(ErrorDeNegocio.class,
                error -> assertThat(error.getCodigo()).isEqualTo(codigo));
    }

    public static Map<String, Object> propiedadesDelError(ThrowingCallable accion, String codigo) {
        try {
            accion.call();
        } catch (ErrorDeNegocio error) {
            assertThat(error.getCodigo()).isEqualTo(codigo);
            return error.getPropiedades();
        } catch (Throwable otro) {
            throw new AssertionError("Se esperaba " + codigo + " y se lanzó " + otro, otro);
        }
        throw new AssertionError("Se esperaba " + codigo + " y no se lanzó nada");
    }
}
