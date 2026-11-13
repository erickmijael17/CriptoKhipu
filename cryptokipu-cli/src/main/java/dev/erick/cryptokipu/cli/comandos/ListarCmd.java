package dev.erick.cryptokipu.cli.comandos;

import dev.erick.cryptokipu.app.casos.SesionBoveda;
import dev.erick.cryptokipu.cli.CodigosSalida;
import dev.erick.cryptokipu.cli.Entorno;
import dev.erick.cryptokipu.cli.Json;
import dev.erick.cryptokipu.core.format.EntradaManifest;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "list", description = "Lista los archivos de la bóveda.")
final class ListarCmd extends ComandoBase {

    @Parameters(index = "0", paramLabel = "<bóveda>")
    Path bovedaMija;

    @Option(names = "--keyfile", paramLabel = "<ruta>", description = "Llave de seguridad, si la bóveda la pide.")
    Path llaveMija;

    @Option(names = "--json", description = "Salida en JSON.")
    boolean jsonMija;

    ListarCmd(Entorno entornoMija) {
        super(entornoMija);
    }

    @Override
    public Integer call() throws Exception {
        try (SesionBoveda sesionMija = abrir(bovedaMija, llaveMija)) {
            List<EntradaManifest> entradasMija = sesionMija.manifest().entradasMija();
            if (jsonMija) {
                StringBuilder jsonSalidaMija = new StringBuilder("[");
                for (int indiceMija = 0; indiceMija < entradasMija.size(); indiceMija++) {
                    EntradaManifest entradaMija = entradasMija.get(indiceMija);
                    jsonSalidaMija.append(indiceMija == 0 ? "" : ",").append("{\"ruta\":")
                            .append(Json.texto(entradaMija.rutaMija()))
                            .append(",\"tamano\":").append(entradaMija.tamanoMija())
                            .append(",\"modificado\":").append(Json
                                    .texto(Instant.ofEpochMilli(entradaMija.modificadoMija()).toString()))
                            .append(",\"tipo\":").append(Json.texto(entradaMija.tipoMimeMija()))
                            .append('}');
                }
                entornoMija.salidaMija().println(jsonSalidaMija.append(']'));
            } else {
                for (EntradaManifest entradaMija : entradasMija) {
                    entornoMija.salidaMija().printf("%14d  %s%n", entradaMija.tamanoMija(), entradaMija.rutaMija());
                }
                entornoMija.erroresMija().println(entradasMija.size() + " archivo(s).");
            }
        }
        return CodigosSalida.OK_MIJA;
    }
}
