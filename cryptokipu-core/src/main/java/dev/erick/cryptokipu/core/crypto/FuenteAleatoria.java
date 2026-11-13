package dev.erick.cryptokipu.core.crypto;

import java.security.SecureRandom;

/**
 * Puerto de aleatoriedad criptográfica. Permite sustituirla en pruebas.
 */
@FunctionalInterface
public interface FuenteAleatoria {

    /** Llena {@code destinoMija} con bytes aleatorios. */
    void llenar(byte[] destinoMija);

    default byte[] bytes(int longitudMija) {
        byte[] resultadoMija = new byte[longitudMija];
        llenar(resultadoMija);
        return resultadoMija;
    }

    /** Fuente del sistema ({@link SecureRandom} por defecto del JDK). */
    static FuenteAleatoria delSistema() {
        SecureRandom aleatorioMija = new SecureRandom();
        return aleatorioMija::nextBytes;
    }
}
