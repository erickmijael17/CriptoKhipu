package dev.erick.cryptokipu.core.auth;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Entrada de Argon2id: por cada factor, longitud (4 B big-endian) ‖ bytes UTF-8.
 * El prefijo de longitud evita ambigüedades: ("ab", "c") y ("a", "bc") producen entradas distintas.
 *
 * <p>La conversión {@code char[] → byte[]} usa un {@link CharsetEncoder} sobre {@link CharBuffer}, sin
 * pasar por {@code String}, y borra los buffers intermedios.
 */
public final class CodificadorEntrada {

    private CodificadorEntrada() {
    }

    /** Quien llama debe borrar el resultado con {@code Arrays.fill} al terminar. */
    public static byte[] codificar(char[]... factoresMija) {
        Objects.requireNonNull(factoresMija, "factoresMija");
        List<byte[]> partesMija = new ArrayList<>(factoresMija.length);
        try {
            int totalMija = 0;
            for (char[] factorMija : factoresMija) {
                byte[] utf8Mija = utf8(Objects.requireNonNull(factorMija, "factorMija"));
                partesMija.add(utf8Mija);
                totalMija += Integer.BYTES + utf8Mija.length;
            }
            ByteBuffer salidaMija = ByteBuffer.allocate(totalMija);
            for (byte[] parteMija : partesMija) {
                salidaMija.putInt(parteMija.length).put(parteMija);
            }
            return salidaMija.array();
        } finally {
            partesMija.forEach(parteMija -> Arrays.fill(parteMija, (byte) 0));
        }
    }

    static byte[] utf8(char[] textoMija) {
        CharsetEncoder codificadorMija = StandardCharsets.UTF_8.newEncoder()
                .onMalformedInput(CodingErrorAction.REPLACE)
                .onUnmappableCharacter(CodingErrorAction.REPLACE);
        ByteBuffer bufferMija;
        try {
            bufferMija = codificadorMija.encode(CharBuffer.wrap(textoMija));
        } catch (CharacterCodingException errorMija) {
            throw new IllegalStateException("UTF-8 con REPLACE no debería fallar", errorMija);
        }
        try {
            byte[] resultadoMija = new byte[bufferMija.remaining()];
            bufferMija.get(resultadoMija);
            return resultadoMija;
        } finally {
            if (bufferMija.hasArray()) {
                Arrays.fill(bufferMija.array(), (byte) 0);
            }
        }
    }
}
