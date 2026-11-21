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
    void conLosSufijosDeTresDigitosTomadosVeTambienLosDeCuatro() {
        // Revisión de Mati en #16: con "-1000" la base se recorta a 35 caracteres, más que el prefijo de la consulta.
        String base = "centro-integral-de-odontologia-avanzada";
        tomados.add(base);
        for (int n = 2; n <= 999; n++) {
            String sufijo = "-" + n;
            tomados.add(base.substring(0, Math.min(base.length(), 40 - sufijo.length())).replaceAll("-+$", "") + sufijo);
        }
        String conCuatroDigitos = base.substring(0, 35).replaceAll("-+$", "") + "-1000";
        tomados.add(conCuatroDigitos);

        String slug = asignador.asignar("Centro Integral de Odontología Avanzada");

        assertThat(slug).isNotEqualTo(conCuatroDigitos).endsWith("-1001").hasSizeLessThanOrEqualTo(40);
    }

    @Test
    void conBaseLargaVeLosCandidatosRecortadosYaTomados() {
        // La base tiene 39 caracteres: con "-2" se recorta a 38 ("...avanzad-2").
        tomados.addAll(Set.of("centro-integral-de-odontologia-avanzada", "centro-integral-de-odontologia-avanzad-2"));

        assertThat(asignador.asignar("Centro Integral de Odontología Avanzada"))
                .isEqualTo("centro-integral-de-odontologia-avanzad-3");
    }

    // --- C-08 (design D7): cambio de slug con sugerencias ---

    @Test
    void unSlugTomadoNoEstaLibre() {
        tomados.add("dra-gomez");

        assertThat(asignador.estaLibre("dra-gomez")).isFalse();
        assertThat(asignador.estaLibre("dra-gomez-sur")).isTrue();
    }

    @Test
    void sugiereLosPrimerosSufijosLibres() {
        tomados.addAll(Set.of("dra-gomez", "dra-gomez-3"));

        assertThat(asignador.sugerencias("dra-gomez", 3)).containsExactly("dra-gomez-2", "dra-gomez-4", "dra-gomez-5");
    }

    @Test
    void lasSugerenciasNoPasanDelLargoMaximo() {
        String base = "a".repeat(ValidadorDeSlug.LARGO_MAXIMO);
        tomados.add(base);

        assertThat(asignador.sugerencias(base, 3))
                .hasSize(3)
                .allSatisfy(sugerencia -> assertThat(sugerencia).hasSizeLessThanOrEqualTo(ValidadorDeSlug.LARGO_MAXIMO));
    }
}
