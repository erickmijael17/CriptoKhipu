package dev.erick.cryptokipu.cli.comandos;

import dev.erick.cryptokipu.app.casos.RegenerarTokens;
import dev.erick.cryptokipu.app.casos.SesionBoveda;
import dev.erick.cryptokipu.cli.CodigosSalida;
import dev.erick.cryptokipu.cli.Entorno;
import dev.erick.cryptokipu.core.auth.Credenciales;
import java.nio.file.Path;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "tokens", description = "Reemplaza los 6 tokens de recuperación por otros nuevos.")
final class TokensCmd extends ComandoBase {

    @Parameters(index = "0", paramLabel = "<bóveda>")
    Path bovedaMija;

    @Option(names = "--regenerar", required = true, description = "Generar 6 tokens nuevos (los anteriores dejan de servir).")
    boolean regenerarMija;

    @Option(names = "--tokens-out", paramLabel = "<archivo>", description = "Guardar los tokens nuevos aquí.")
    Path tokensOutMija;

    @Option(names = "--keyfile", paramLabel = "<ruta>", description = "Llave de seguridad, si la bóveda la pide.")
    Path llaveMija;

    TokensCmd(Entorno entornoMija) {
        super(entornoMija);
    }

    @Override
    public Integer call() throws Exception {
        exigirDestinoTokens(tokensOutMija);
        confirmar("Los tokens actuales de " + bovedaMija + " dejarán de servir. ¿Continuar?");
        try (SesionBoveda sesionMija = abrir(bovedaMija, llaveMija, true);
                Credenciales vigentesMija = tomarVigentes()) {
            emitirTokens(entornoMija.appMija().regenerarTokens()
                    .ejecutar(new RegenerarTokens.Comando(sesionMija, vigentesMija)).tokensMija(), tokensOutMija, bovedaMija);
        }
        return CodigosSalida.OK_MIJA;
    }
}
