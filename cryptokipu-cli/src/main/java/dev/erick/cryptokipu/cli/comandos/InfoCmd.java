package dev.erick.cryptokipu.cli.comandos;

import dev.erick.cryptokipu.app.casos.InspeccionarBoveda;
import dev.erick.cryptokipu.cli.CodigosSalida;
import dev.erick.cryptokipu.cli.Entorno;
import dev.erick.cryptokipu.core.crypto.ParametrosArgon2;
import java.nio.file.Path;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "info", description = "Muestra versión, perfil y si pide llave. No pide credenciales.")
final class InfoCmd extends ComandoBase {

    @Parameters(index = "0", paramLabel = "<bóveda>")
    Path bovedaMija;

    @Option(names = "--json", description = "Salida en JSON.")
    boolean jsonMija;

    InfoCmd(Entorno entornoMija) {
        super(entornoMija);
    }

    @Override
    public Integer call() throws Exception {
        InspeccionarBoveda.Resultado infoMija = entornoMija.appMija().inspeccionarBoveda()
                .ejecutar(new InspeccionarBoveda.Comando(bovedaMija));
        ParametrosArgon2 perfilMija = infoMija.perfilMija();
        if (jsonMija) {
            entornoMija.salidaMija().println("{\"versionFormato\":" + infoMija.versionFormatoMija()
                    + ",\"argon2\":{\"memoriaKib\":" + perfilMija.memoriaKibMija() + ",\"iteraciones\":"
                    + perfilMija.iteracionesMija() + ",\"paralelismo\":" + perfilMija.paralelismoMija() + "}"
                    + ",\"requiereLlave\":" + infoMija.requiereLlaveMija() + ",\"destruida\":" + infoMija.destruidaMija()
                    + ",\"soloLectura\":" + infoMija.soloLecturaMija() + "}");
        } else {
            entornoMija.salidaMija().println("Formato .kipu v" + infoMija.versionFormatoMija());
            entornoMija.salidaMija().println("Argon2id: " + perfilMija.memoriaKibMija() / 1024 + " MiB, t="
                    + perfilMija.iteracionesMija() + ", p=" + perfilMija.paralelismoMija());
            entornoMija.salidaMija().println("Pide llave de seguridad: " + (infoMija.requiereLlaveMija() ? "sí" : "no"));
            entornoMija.salidaMija().println("Destruida: " + (infoMija.destruidaMija() ? "sí" : "no"));
            entornoMija.salidaMija().println("Solo lectura: " + (infoMija.soloLecturaMija() ? "sí" : "no"));
        }
        return infoMija.destruidaMija() ? CodigosSalida.DESTRUIDA_MIJA : CodigosSalida.OK_MIJA;
    }
}
