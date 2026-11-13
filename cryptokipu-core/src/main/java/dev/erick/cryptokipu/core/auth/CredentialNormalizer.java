package dev.erick.cryptokipu.core.auth;

import java.nio.CharBuffer;
import java.text.Normalizer;
import java.util.Arrays;
import java.util.Objects;

/**
 * Normalización v1 de la frase de paso (F3), fijada en la política del header ({@code normalizacionF3 = 1}).
 *
 * <p>Pasos: NFD → quitar diacríticos (marcas combinantes) → minúsculas independientes de la
 * configuración regional (mapeo simple de Unicode, equivalente a {@code Locale.ROOT}) → NFD y quitar
 * diacríticos otra vez (garantiza idempotencia) → recortar extremos → colapsar espacios a uno.
 * Así {@code "Mi Pérro  Ñato"} y {@code "mi perro nato"} producen lo mismo.
 *
 * <p>Limitación documentada en THREAT_MODEL: {@link Normalizer} solo devuelve {@code String}, así que
 * durante la normalización existe una copia inmutable de la frase que no se puede borrar.
 */
public final class CredentialNormalizer {

    private CredentialNormalizer() {
    }

    /** Devuelve un arreglo nuevo; el de entrada no se modifica. Quien llama borra ambos. */
    public static char[] normalizarFrase(char[] fraseMija) {
        Objects.requireNonNull(fraseMija, "fraseMija");
        char[] sinMarcasMija = descomponerSinMarcas(fraseMija, false);
        char[] minusculasMija = descomponerSinMarcas(sinMarcasMija, true);
        Arrays.fill(sinMarcasMija, '\0');
        char[] resultadoMija = colapsarEspacios(minusculasMija);
        Arrays.fill(minusculasMija, '\0');
        return resultadoMija;
    }

    /** Longitud en puntos de código (lo que cuenta la regla de F3). */
    public static int longitud(char[] normalizadaMija) {
        return Character.codePointCount(normalizadaMija, 0, normalizadaMija.length);
    }

    private static char[] descomponerSinMarcas(char[] textoMija, boolean minusculasMija) {
        String descompuestaMija = Normalizer.normalize(CharBuffer.wrap(textoMija), Normalizer.Form.NFD);
        StringBuilder salidaMija = new StringBuilder(descompuestaMija.length());
        descompuestaMija.codePoints().forEach(puntoMija -> {
            int tipoMija = Character.getType(puntoMija);
            if (tipoMija == Character.NON_SPACING_MARK || tipoMija == Character.COMBINING_SPACING_MARK
                    || tipoMija == Character.ENCLOSING_MARK) {
                return;
            }
            salidaMija.appendCodePoint(minusculasMija ? Character.toLowerCase(puntoMija) : puntoMija);
        });
        char[] resultadoMija = new char[salidaMija.length()];
        salidaMija.getChars(0, salidaMija.length(), resultadoMija, 0);
        salidaMija.setLength(0);
        for (int indiceMija = 0; indiceMija < resultadoMija.length; indiceMija++) {
            salidaMija.append('\0');
        }
        return resultadoMija;
    }

    private static char[] colapsarEspacios(char[] textoMija) {
        char[] salidaMija = new char[textoMija.length];
        int longitudMija = 0;
        boolean espacioPendienteMija = false;
        for (char caracterMija : textoMija) {
            if (Character.isWhitespace(caracterMija) || Character.isSpaceChar(caracterMija)) {
                espacioPendienteMija = longitudMija > 0;
                continue;
            }
            if (espacioPendienteMija) {
                salidaMija[longitudMija++] = ' ';
                espacioPendienteMija = false;
            }
            salidaMija[longitudMija++] = caracterMija;
        }
        char[] resultadoMija = Arrays.copyOf(salidaMija, longitudMija);
        Arrays.fill(salidaMija, '\0');
        return resultadoMija;
    }
}
