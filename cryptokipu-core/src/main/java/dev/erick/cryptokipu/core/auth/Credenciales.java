package dev.erick.cryptokipu.core.auth;

import java.util.Arrays;
import java.util.Objects;

/**
 * Credenciales F1–F4. <b>Toma posesión</b> de los arreglos (no los copia): al cerrar, se borran.
 *
 * @param pinMija            F1, contraseña numérica
 * @param contrasenaMija     F2, contraseña alfanumérica
 * @param fraseMija          F3, frase de paso (sin normalizar)
 * @param huellaLlaveMija    F4, SHA-256 de la llave de seguridad; {@code null} si la bóveda no usa llave
 */
public record Credenciales(char[] pinMija, char[] contrasenaMija, char[] fraseMija, byte[] huellaLlaveMija)
        implements AutoCloseable {

    public Credenciales {
        Objects.requireNonNull(pinMija, "pinMija");
        Objects.requireNonNull(contrasenaMija, "contrasenaMija");
        Objects.requireNonNull(fraseMija, "fraseMija");
        if (huellaLlaveMija != null && huellaLlaveMija.length != 32) {
            throw new IllegalArgumentException("La huella de la llave debe medir 32 B");
        }
    }

    public boolean tieneLlave() {
        return huellaLlaveMija != null;
    }

    @Override
    public void close() {
        Arrays.fill(pinMija, '\0');
        Arrays.fill(contrasenaMija, '\0');
        Arrays.fill(fraseMija, '\0');
        if (huellaLlaveMija != null) {
            Arrays.fill(huellaLlaveMija, (byte) 0);
        }
    }

    @Override
    public boolean equals(Object otroMija) {
        return this == otroMija;
    }

    @Override
    public int hashCode() {
        return System.identityHashCode(this);
    }

    @Override
    public String toString() {
        return "***";
    }
}
