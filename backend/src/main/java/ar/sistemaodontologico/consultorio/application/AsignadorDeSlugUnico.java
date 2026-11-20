package ar.sistemaodontologico.consultorio.application;

import ar.sistemaodontologico.compartido.domain.slug.SugeridorDeSlug;
import ar.sistemaodontologico.compartido.domain.slug.ValidadorDeSlug;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * Asigna un slug libre a partir del nombre del consultorio: la sugerencia de C-02 o, si está tomada, el sufijo
 * {@code -N} libre más bajo (C-04, design D6). Lo usan el registro y el cambio de slug de C-08.
 */
@Service
public class AsignadorDeSlugUnico {

    private final SlugsEnUso enUso;

    public AsignadorDeSlugUnico(SlugsEnUso enUso) {
        this.enUso = enUso;
    }

    public String asignar(String nombreConsultorio) {
        return primeroLibre(SugeridorDeSlug.sugerir(nombreConsultorio));
    }

    String primeroLibre(String base) {
        // El prefijo ya recortado cubre la base completa y los candidatos con sufijo recortados (hasta "-999").
        Set<String> tomados = enUso.empiezanCon(recortar(base, ValidadorDeSlug.LARGO_MAXIMO - 4));
        if (!tomados.contains(base)) {
            return base;
        }
        for (int numero = 2; ; numero++) {
            String sufijo = "-" + numero;
            String candidato = recortar(base, ValidadorDeSlug.LARGO_MAXIMO - sufijo.length()) + sufijo;
            if (!tomados.contains(candidato)) {
                return candidato;
            }
        }
    }

    private static String recortar(String base, int largoMaximo) {
        String recortada = base.length() > largoMaximo ? base.substring(0, largoMaximo) : base;
        return recortada.replaceAll("-+$", "");
    }
}
