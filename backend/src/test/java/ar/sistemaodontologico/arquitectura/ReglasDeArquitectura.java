package ar.sistemaodontologico.arquitectura;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.util.Optional;

/** Reglas de capas de knowledge-base/08_arquitectura_propuesta.md. */
public final class ReglasDeArquitectura {

    private static final String INFRAESTRUCTURA = "infrastructure";

    private ReglasDeArquitectura() {
    }

    /** La capa {@code domain} no depende de Spring ni de la API de persistencia. */
    public static ArchRule dominioSinFramework() {
        return noClasses()
                .that().resideInAPackage("..domain..")
                .should().dependOnClassesThat().resideInAnyPackage("org.springframework..", "jakarta.persistence..")
                .as("la capa domain no depende de Spring ni de jakarta.persistence")
                .allowEmptyShould(true);
    }

    /** Ningún módulo usa clases de la capa {@code infrastructure} de otro módulo. */
    public static ArchRule infraestructuraPrivadaDelModulo(String paqueteRaiz) {
        return classes()
                .that().resideInAPackage(paqueteRaiz + "..")
                .should(noUsarInfraestructuraAjena(paqueteRaiz))
                .as("ningún módulo accede a la capa infrastructure de otro módulo")
                .allowEmptyShould(true);
    }

    private static ArchCondition<JavaClass> noUsarInfraestructuraAjena(String paqueteRaiz) {
        return new ArchCondition<>("no usar la infraestructura de otro módulo") {
            @Override
            public void check(JavaClass origen, ConditionEvents eventos) {
                Optional<String> moduloOrigen = modulo(origen, paqueteRaiz);
                for (Dependency dependencia : origen.getDirectDependenciesFromSelf()) {
                    JavaClass destino = dependencia.getTargetClass();
                    Optional<String> moduloDestino = modulo(destino, paqueteRaiz);
                    boolean esInfraestructuraAjena = moduloDestino.isPresent()
                            && esInfraestructura(destino, paqueteRaiz)
                            && !moduloDestino.equals(moduloOrigen);
                    if (esInfraestructuraAjena) {
                        eventos.add(SimpleConditionEvent.violated(dependencia, dependencia.getDescription()));
                    }
                }
            }
        };
    }

    private static Optional<String> modulo(JavaClass clase, String paqueteRaiz) {
        String paquete = clase.getPackageName();
        if (!paquete.startsWith(paqueteRaiz + ".")) {
            return Optional.empty();
        }
        String resto = paquete.substring(paqueteRaiz.length() + 1);
        int punto = resto.indexOf('.');
        return Optional.of(punto < 0 ? resto : resto.substring(0, punto));
    }

    private static boolean esInfraestructura(JavaClass clase, String paqueteRaiz) {
        String relativo = clase.getPackageName().substring(paqueteRaiz.length() + 1);
        return relativo.contains("." + INFRAESTRUCTURA);
    }
}
