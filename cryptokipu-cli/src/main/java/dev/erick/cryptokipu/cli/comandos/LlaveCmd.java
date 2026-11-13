package dev.erick.cryptokipu.cli.comandos;

import dev.erick.cryptokipu.cli.CodigosSalida;
import dev.erick.cryptokipu.cli.Entorno;
import dev.erick.cryptokipu.infra.autenticacion.LlaveSeguridad;
import java.nio.file.Path;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "keyfile", description = "Genera una llave de seguridad .kipukey de 64 bytes aleatorios (nunca sobrescribe).")
final class LlaveCmd extends ComandoBase {

    @Option(names = "--generar", required = true, paramLabel = "<ruta>", description = "Archivo a crear.")
    Path destinoMija;

    LlaveCmd(Entorno entornoMija) {
        super(entornoMija);
    }

    @Override
    public Integer call() throws Exception {
        LlaveSeguridad.generar(destinoMija);
        entornoMija.salidaMija().println("Llave creada: " + destinoMija);
        entornoMija.erroresMija().println("Guárdala en un medio distinto al .kipu y no la envíes como foto: "
                + "si se recomprime, deja de servir.");
        return CodigosSalida.OK_MIJA;
    }
}
