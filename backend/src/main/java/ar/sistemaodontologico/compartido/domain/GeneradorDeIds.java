package ar.sistemaodontologico.compartido.domain;

import java.util.UUID;

/** Genera los identificadores de las entidades en la aplicación (UUIDv7, ordenables en el tiempo). */
public interface GeneradorDeIds {

    UUID nuevo();
}
