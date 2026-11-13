package dev.erick.cryptokipu.core.crypto;

import java.util.Arrays;
import java.util.Objects;

/**
 * Bytes sensibles (claves, credenciales) que se borran con {@link #close()}.
 *
 * <p>Uso: {@code try (SecureBytes claveMija = ...) { ... }}. Después de cerrar, cualquier acceso
 * lanza {@link IllegalStateException}. {@link #toString()} nunca muestra el contenido.
 * En la Fase 8 el almacenamiento pasa a memoria fuera del heap (FFM).
 */
public final class SecureBytes implements AutoCloseable {

    private final byte[] datosMija;
    private boolean cerradoMija;

    private SecureBytes(byte[] datosMija) {
        this.datosMija = datosMija;
    }

    /** Toma posesión del arreglo: quien llama no debe seguir usándolo. */
    public static SecureBytes envolver(byte[] datosMija) {
        return new SecureBytes(Objects.requireNonNull(datosMija, "datosMija"));
    }

    /** Copia el arreglo; el original sigue siendo responsabilidad de quien llama. */
    public static SecureBytes copiaDe(byte[] datosMija) {
        return new SecureBytes(Objects.requireNonNull(datosMija, "datosMija").clone());
    }

    /** Referencia directa a los bytes (sin copia). No guardarla más allá del bloque {@code try}. */
    public byte[] bytes() {
        verificarAbierto();
        return datosMija;
    }

    public int longitud() {
        verificarAbierto();
        return datosMija.length;
    }

    public boolean estaCerrado() {
        return cerradoMija;
    }

    @Override
    public void close() {
        Arrays.fill(datosMija, (byte) 0);
        cerradoMija = true;
    }

    @Override
    public String toString() {
        return "SecureBytes[***]";
    }

    private void verificarAbierto() {
        if (cerradoMija) {
            throw new IllegalStateException("SecureBytes ya fue cerrado");
        }
    }
}
