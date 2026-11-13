package dev.erick.cryptokipu.cli.comandos;

import dev.erick.cryptokipu.app.casos.Compactar;
import dev.erick.cryptokipu.app.casos.SesionBoveda;
import dev.erick.cryptokipu.cli.CodigosSalida;
import dev.erick.cryptokipu.cli.Entorno;
import java.nio.file.Files;
import java.nio.file.Path;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "compact", description = "Reescribe la bóveda sin los bloques de archivos eliminados.")
final class CompactarCmd extends ComandoBase {

    @Parameters(index = "0", paramLabel = "<bóveda>")
    Path bovedaMija;

    @Option(names = "--keyfile", paramLabel = "<ruta>", description = "Llave de seguridad, si la bóveda la pide.")
    Path llaveMija;

    CompactarCmd(Entorno entornoMija) {
        super(entornoMija);
    }

    @Override
    public Integer call() throws Exception {
        confirmar("Se reescribirá " + bovedaMija + " por completo. ¿Continuar?");
        try (SesionBoveda sesionMija = abrir(bovedaMija, llaveMija)) {
            long antesMija = Files.size(bovedaMija);
            entornoMija.appMija().compactar().ejecutar(new Compactar.Comando(sesionMija));
            entornoMija.salidaMija().println("Compactada: " + antesMija + " → " + Files.size(bovedaMija) + " bytes");
        }
        return CodigosSalida.OK_MIJA;
    }
}
