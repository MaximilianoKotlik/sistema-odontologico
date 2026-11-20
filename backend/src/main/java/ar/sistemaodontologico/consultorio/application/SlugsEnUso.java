package ar.sistemaodontologico.consultorio.application;

import java.util.Set;

/** Puerto: slugs ya tomados que empiezan con un prefijo. */
@FunctionalInterface
public interface SlugsEnUso {

    Set<String> empiezanCon(String prefijo);
}
