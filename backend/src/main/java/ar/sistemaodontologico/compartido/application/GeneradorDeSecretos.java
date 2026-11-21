package ar.sistemaodontologico.compartido.application;

/** Puerto: valores opacos de 256 bits de {@code SecureRandom}, en base64url (refresh tokens, design D4). */
@FunctionalInterface
public interface GeneradorDeSecretos {

    String nuevo();
}
