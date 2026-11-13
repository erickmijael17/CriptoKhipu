package dev.erick.cryptokipu.cli.comandos;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import picocli.CommandLine.Option;

/** Opciones que comparten todos los subcomandos. Ninguna acepta un secreto como valor. */
public final class OpcionesComunes {

    @Option(names = "--secretos-stdin",
            description = "Leer los secretos desde stdin, una línea cada uno (solo para scripts; sin consola).")
    boolean secretosStdinMija;

    @Option(names = "--secretos-charset", paramLabel = "<charset>", defaultValue = "UTF-8",
            description = "Codificación de stdin con --secretos-stdin (por defecto: ${DEFAULT-VALUE}).")
    Charset charsetMija = StandardCharsets.UTF_8;

    @Option(names = {"-y", "--yes"}, description = "No pedir confirmación en acciones destructivas.")
    boolean siMija;
}
