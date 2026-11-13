package dev.erick.cryptokipu.cli.comandos;

import dev.erick.cryptokipu.app.casos.AgregarArchivos;
import dev.erick.cryptokipu.app.casos.PoliticaColision;
import dev.erick.cryptokipu.app.casos.SesionBoveda;
import dev.erick.cryptokipu.cli.CodigosSalida;
import dev.erick.cryptokipu.cli.Entorno;
import java.nio.file.Path;
import java.util.List;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "add", description = "Agrega archivos o carpetas (recursivo; no sigue enlaces simbólicos).")
final class AgregarCmd extends ComandoBase {

    @Parameters(index = "0", paramLabel = "<bóveda>")
    Path bovedaMija;

    @Parameters(index = "1..*", arity = "1..*", paramLabel = "<archivos>")
    List<Path> archivosMija;

    @Option(names = "--keyfile", paramLabel = "<ruta>", description = "Llave de seguridad, si la bóveda la pide.")
    Path llaveMija;

    @Option(names = "--si-existe", defaultValue = "omitir",
            description = "Si el nombre ya existe en la bóveda: ${COMPLETION-CANDIDATES} (por defecto: ${DEFAULT-VALUE}).")
    SiExiste siExisteMija;

    AgregarCmd(Entorno entornoMija) {
        super(entornoMija);
    }

    @Override
    public Integer call() throws Exception {
        try (SesionBoveda sesionMija = abrir(bovedaMija, llaveMija)) {
            AgregarArchivos.Resultado resultadoMija = entornoMija.appMija().agregarArchivos().ejecutar(
                    new AgregarArchivos.Comando(sesionMija, archivosMija, "", siExisteMija.politica(),
                            entornoMija.cancelacionMija()));
            informar(entornoMija, resultadoMija);
        }
        return CodigosSalida.OK_MIJA;
    }

    static void informar(Entorno entornoMija, AgregarArchivos.Resultado resultadoMija) {
        resultadoMija.agregadosMija().forEach(entradaMija -> entornoMija.salidaMija().println("+ " + entradaMija.rutaMija()));
        resultadoMija.omitidosMija().forEach(omitidoMija -> entornoMija.erroresMija()
                .println("Omitido (" + omitidoMija.motivoMija() + "): " + omitidoMija.origenMija()));
    }

    /** Valores de {@code --si-existe}. */
    enum SiExiste {
        reemplazar(PoliticaColision.REEMPLAZAR),
        renombrar(PoliticaColision.RENOMBRAR),
        omitir(PoliticaColision.OMITIR);

        private final PoliticaColision politicaMija;

        SiExiste(PoliticaColision politicaMija) {
            this.politicaMija = politicaMija;
        }

        PoliticaColision politica() {
            return politicaMija;
        }
    }
}
