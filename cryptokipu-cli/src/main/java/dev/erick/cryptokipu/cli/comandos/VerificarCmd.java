package dev.erick.cryptokipu.cli.comandos;

import dev.erick.cryptokipu.app.casos.SesionBoveda;
import dev.erick.cryptokipu.app.casos.VerificarIntegridad;
import dev.erick.cryptokipu.cli.CodigosSalida;
import dev.erick.cryptokipu.cli.Entorno;
import dev.erick.cryptokipu.cli.Json;
import java.nio.file.Path;
import java.util.List;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "verify", description = "Verifica todos los bloques de todos los archivos.")
final class VerificarCmd extends ComandoBase {

    @Parameters(index = "0", paramLabel = "<bóveda>")
    Path bovedaMija;

    @Option(names = "--keyfile", paramLabel = "<ruta>", description = "Llave de seguridad, si la bóveda la pide.")
    Path llaveMija;

    @Option(names = "--json", description = "Salida en JSON.")
    boolean jsonMija;

    VerificarCmd(Entorno entornoMija) {
        super(entornoMija);
    }

    @Override
    public Integer call() throws Exception {
        try (SesionBoveda sesionMija = abrir(bovedaMija, llaveMija)) {
            VerificarIntegridad.Resultado resultadoMija = entornoMija.appMija().verificarIntegridad()
                    .ejecutar(new VerificarIntegridad.Comando(sesionMija, entornoMija.cancelacionMija()));
            List<VerificarIntegridad.Informe> informesMija = resultadoMija.informesMija();
            if (jsonMija) {
                StringBuilder salidaMija = new StringBuilder("[");
                for (int indiceMija = 0; indiceMija < informesMija.size(); indiceMija++) {
                    salidaMija.append(indiceMija == 0 ? "" : ",").append("{\"ruta\":")
                            .append(Json.texto(informesMija.get(indiceMija).rutaInternaMija())).append(",\"estado\":")
                            .append(Json.texto(informesMija.get(indiceMija).estadoMija().name())).append('}');
                }
                entornoMija.salidaMija().println(salidaMija.append(']'));
            } else {
                informesMija.forEach(informeMija -> entornoMija.salidaMija()
                        .println(informeMija.estadoMija() + "  " + informeMija.rutaInternaMija()));
            }
            return resultadoMija.todoCorrecto() ? CodigosSalida.OK_MIJA : CodigosSalida.INTEGRIDAD_MIJA;
        }
    }
}
