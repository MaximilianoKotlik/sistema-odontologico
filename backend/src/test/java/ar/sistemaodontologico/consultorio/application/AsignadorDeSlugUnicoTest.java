package ar.sistemaodontologico.consultorio.application;

import static org.assertj.core.api.Assertions.assertThat;

import ar.sistemaodontologico.compartido.domain.slug.ValidadorDeSlug;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Spec registro-odontologo — Requirement: Slug sugerido único (C-04, design D6). La regla de elegir el sufijo
 * libre es pura: se prueba con un conjunto en memoria de slugs tomados. La persistencia se prueba por HTTP.
 */
class AsignadorDeSlugUnicoTest {

    private final Set<String> tomados = new HashSet<>();
    private final AsignadorDeSlugUnico asignador = new AsignadorDeSlugUnico(prefijo ->
            tomados.stream().filter(slug -> slug.startsWith(prefijo)).collect(java.util.stream.Collectors.toSet()));

    @Test
    void siLaBaseEstaLibreLaUsa() {
        assertThat(asignador.asignar("Consultorio Demo Sur")).isEqualTo("consultorio-demo-sur");
    }

    @Test
    void eligeElSufijoLibreMasBajo() {
        tomados.addAll(Set.of("consultorio-demo-sur", "consultorio-demo-sur-2"));

        assertThat(asignador.asignar("Consultorio Demo Sur")).isEqualTo("consultorio-demo-sur-3");
    }

    @Test
    void reusaUnHuecoEnLaNumeracion() {
        tomados.addAll(Set.of("demo-sur", "demo-sur-3"));

        assertThat(asignador.asignar("Demo Sur")).isEqualTo("demo-sur-2");
    }

    @Test
    void noConfundeOtrosSlugsQueEmpiezanIgual() {
        tomados.addAll(Set.of("demo-sur", "demo-sur-norte"));

        assertThat(asignador.asignar("Demo Sur")).isEqualTo("demo-sur-2");
    }

    @Test
    void recortaLaBaseParaQueElSufijoEntreEnCuarentaCaracteres() {
        String nombre = "Centro Integral de Odontología Avanzada";
        tomados.add("centro-integral-de-odontologia-avanzada");

        String slug = asignador.asignar(nombre);

        assertThat(slug).hasSizeLessThanOrEqualTo(40).endsWith("-2");
        ValidadorDeSlug.validar(slug);
    }

    @Test
    void conBaseLargaVeLosCandidatosRecortadosYaTomados() {
        // La base tiene 39 caracteres: con "-2" se recorta a 38 ("...avanzad-2").
        tomados.addAll(Set.of("centro-integral-de-odontologia-avanzada", "centro-integral-de-odontologia-avanzad-2"));

        assertThat(asignador.asignar("Centro Integral de Odontología Avanzada"))
                .isEqualTo("centro-integral-de-odontologia-avanzad-3");
    }
}
