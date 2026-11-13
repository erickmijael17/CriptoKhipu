package dev.erick.cryptokipu.cli;

import dev.erick.cryptokipu.cli.comandos.Cli;
import dev.erick.cryptokipu.infra.configuracion.Contenedor;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import picocli.AutoComplete;
import picocli.CommandLine;

/** Genera el autocompletado de bash y la ayuda de cada subcomando (tarea Gradle {@code generarDocsCli}). */
public final class GeneradorDocs {

    private GeneradorDocs() {
    }

    public static void main(String[] argumentosMija) throws IOException {
        Path destinoMija = Path.of(argumentosMija[0]);
        Path ayudaMija = Files.createDirectories(destinoMija.resolve("ayuda"));
        PrintStream nuloMija = new PrintStream(PrintStream.nullOutputStream(), true, StandardCharsets.UTF_8);
        Entorno entornoMija = new Entorno(Contenedor.porDefecto(), nuloMija, nuloMija,
                new ByteArrayInputStream(new byte[0]), Optional.empty(), () -> false, new Cancelacion(nuloMija));
        CommandLine lineaMija = Cli.lineaDeComandos(entornoMija);

        Files.writeString(destinoMija.resolve("kipu_completion.bash"), AutoComplete.bash("kipu", lineaMija));
        Files.writeString(ayudaMija.resolve("kipu.txt"), lineaMija.getUsageMessage(CommandLine.Help.Ansi.OFF));
        for (var subcomandoMija : lineaMija.getSubcommands().entrySet()) {
            Files.writeString(ayudaMija.resolve("kipu-" + subcomandoMija.getKey() + ".txt"),
                    subcomandoMija.getValue().getUsageMessage(CommandLine.Help.Ansi.OFF));
        }
        System.out.println("Documentación generada en " + destinoMija);
    }
}
