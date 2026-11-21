package ar.sistemaodontologico.consultorio.application;

import ar.sistemaodontologico.compartido.domain.slug.SugeridorDeSlug;
import ar.sistemaodontologico.compartido.domain.slug.ValidadorDeSlug;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
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

    /** Si ningún consultorio usa el slug (C-08, cambio de slug). */
    public boolean estaLibre(String slug) {
        return !enUso.empiezanCon(slug).contains(slug);
    }

    /**
     * Hasta {@code cantidad} slugs libres {@code -N} a partir del pedido, que ya está tomado (C-08, design D7).
     * Se recorta la base para no pasar del largo máximo.
     */
    public List<String> sugerencias(String pedido, int cantidad) {
        return libres(pedido, cantidad, false);
    }

    String primeroLibre(String base) {
        return libres(base, 1, true).get(0);
    }

    private List<String> libres(String base, int cantidad, boolean incluirBase) {
        // Se consulta por el prefijo que comparten la base y los candidatos de la longitud de sufijo en curso. Cuando
        // el sufijo crece (de "-999" a "-1000") la base se recorta más, así que se vuelve a consultar con ese prefijo
        // más corto (revisión de Mati en #16).
        int largoConsultado = ValidadorDeSlug.LARGO_MAXIMO - 4;
        Set<String> tomados = new HashSet<>(enUso.empiezanCon(recortar(base, largoConsultado)));
        List<String> libres = new ArrayList<>();
        if (incluirBase && !tomados.contains(base)) {
            libres.add(base);
        }
        for (int numero = 2; libres.size() < cantidad; numero++) {
            String sufijo = "-" + numero;
            int largoDeBase = ValidadorDeSlug.LARGO_MAXIMO - sufijo.length();
            if (largoDeBase < largoConsultado) {
                largoConsultado = largoDeBase;
                tomados.addAll(enUso.empiezanCon(recortar(base, largoConsultado)));
            }
            String candidato = recortar(base, largoDeBase) + sufijo;
            if (!tomados.contains(candidato)) {
                libres.add(candidato);
            }
        }
        return libres;
    }

    private static String recortar(String base, int largoMaximo) {
        String recortada = base.length() > largoMaximo ? base.substring(0, largoMaximo) : base;
        return recortada.replaceAll("-+$", "");
    }
}
