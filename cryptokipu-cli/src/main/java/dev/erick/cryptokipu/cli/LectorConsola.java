package dev.erick.cryptokipu.cli;

import java.io.Console;
import java.util.Objects;

/**
 * Secretos desde la consola interactiva, sin eco. {@link Console#readPassword} decodifica con
 * {@link Console#charset()} (en Windows puede ser cp850/cp1252), no con el charset por defecto de la JVM:
 * así una frase con ñ y tildes produce los mismos caracteres que en la interfaz gráfica.
 */
public final class LectorConsola implements LectorSecretos {

    private final Console consolaMija;

    public LectorConsola(Console consolaMija) {
        this.consolaMija = Objects.requireNonNull(consolaMija, "consolaMija");
    }

    @Override
    public char[] leerSecreto(String etiquetaMija) {
        char[] leidoMija = consolaMija.readPassword("%s: ", etiquetaMija);
        if (leidoMija == null) {
            throw new ErrorUso("Entrada terminada antes de tiempo.");
        }
        return leidoMija;
    }

    @Override
    public char[] leerLinea(String etiquetaMija) {
        String lineaMija = consolaMija.readLine("%s ", etiquetaMija);
        if (lineaMija == null) {
            throw new ErrorUso("Entrada terminada antes de tiempo.");
        }
        return lineaMija.toCharArray();
    }
}
