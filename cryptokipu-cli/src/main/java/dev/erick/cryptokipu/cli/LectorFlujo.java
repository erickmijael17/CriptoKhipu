package dev.erick.cryptokipu.cli;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.util.Arrays;
import java.util.Objects;

/**
 * Secretos desde un flujo (tuberías, scripts, pruebas): solo con {@code --secretos-stdin}. Lee línea a línea
 * byte a byte y decodifica con el charset indicado (por defecto UTF-8; con consola, el de la consola) directamente
 * a {@code char[]}, sin pasar por {@code String}. Los buffers intermedios se borran.
 */
public final class LectorFlujo implements LectorSecretos {

    private static final int MAXIMO_LINEA_MIJA = 4096;

    private final InputStream entradaMija;
    private final Charset charsetMija;
    private final PrintStream avisosMija;

    public LectorFlujo(InputStream entradaMija, Charset charsetMija, PrintStream avisosMija) {
        this.entradaMija = Objects.requireNonNull(entradaMija, "entradaMija");
        this.charsetMija = Objects.requireNonNull(charsetMija, "charsetMija");
        this.avisosMija = Objects.requireNonNull(avisosMija, "avisosMija");
    }

    @Override
    public char[] leerSecreto(String etiquetaMija) {
        avisosMija.print(etiquetaMija + ": ");
        avisosMija.flush();
        return leer();
    }

    @Override
    public char[] leerLinea(String etiquetaMija) {
        return leerSecreto(etiquetaMija);
    }

    private char[] leer() {
        byte[] bufferMija = new byte[MAXIMO_LINEA_MIJA];
        int longitudMija = 0;
        try {
            int byteMija;
            while ((byteMija = entradaMija.read()) != -1 && byteMija != '\n') {
                if (longitudMija == bufferMija.length) {
                    throw new ErrorUso("Línea de entrada demasiado larga.");
                }
                bufferMija[longitudMija++] = (byte) byteMija;
            }
            if (byteMija == -1 && longitudMija == 0) {
                throw new ErrorUso("Entrada terminada antes de tiempo: faltan secretos en stdin.");
            }
            if (longitudMija > 0 && bufferMija[longitudMija - 1] == '\r') {
                longitudMija--;
            }
            return decodificar(ByteBuffer.wrap(bufferMija, 0, longitudMija));
        } catch (IOException errorMija) {
            throw new UncheckedIOException(errorMija);
        } finally {
            Arrays.fill(bufferMija, (byte) 0);
        }
    }

    private char[] decodificar(ByteBuffer bytesMija) {
        CharsetDecoder decodificadorMija = charsetMija.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        CharBuffer caracteresMija;
        try {
            caracteresMija = decodificadorMija.decode(bytesMija);
        } catch (CharacterCodingException _) {
            throw new ErrorUso("La entrada no es texto válido en " + charsetMija.name() + ".");
        }
        try {
            char[] resultadoMija = new char[caracteresMija.remaining()];
            caracteresMija.get(resultadoMija);
            return resultadoMija;
        } finally {
            if (caracteresMija.hasArray()) {
                Arrays.fill(caracteresMija.array(), '\0');
            }
        }
    }
}
