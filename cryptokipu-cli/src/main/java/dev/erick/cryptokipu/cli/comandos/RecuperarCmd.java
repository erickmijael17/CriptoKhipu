package dev.erick.cryptokipu.cli.comandos;

import dev.erick.cryptokipu.app.casos.RecuperarConToken;
import dev.erick.cryptokipu.app.casos.RotarCredenciales;
import dev.erick.cryptokipu.app.casos.SesionBoveda;
import dev.erick.cryptokipu.cli.CodigosSalida;
import dev.erick.cryptokipu.cli.Entorno;
import dev.erick.cryptokipu.core.auth.CambioCredenciales;
import dev.erick.cryptokipu.core.auth.Credenciales;
import dev.erick.cryptokipu.core.auth.ExcepcionValidacion;
import dev.erick.cryptokipu.core.auth.ReglaIncumplida;
import dev.erick.cryptokipu.core.auth.TokenRecuperacion;
import dev.erick.cryptokipu.core.auth.ValidadorFactores;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 * Recupera con token + F1 y obliga a rotar en la misma ejecución. Todo lo que puede fallar por uso (destino de
 * los tokens, token mal escrito, credenciales nuevas débiles) se comprueba <b>antes</b> de consumir el token.
 */
@Command(name = "recover", description = "Abre con un token + F1 y define credenciales nuevas (6 tokens nuevos).")
final class RecuperarCmd extends ComandoBase {

    static final class LlaveNueva {
        @Option(names = "--llave", paramLabel = "<ruta>", required = true,
                description = "Llave de seguridad de las credenciales nuevas.")
        Path llaveMija;

        @Option(names = "--quitar-llave", required = true, description = "Las credenciales nuevas no usan llave.")
        boolean quitarMija;
    }

    @Parameters(index = "0", paramLabel = "<bóveda>")
    Path bovedaMija;

    @ArgGroup(exclusive = true, multiplicity = "1")
    LlaveNueva llaveNuevaMija;

    @Option(names = "--tokens-out", paramLabel = "<tokens.txt>", description = "Guardar los tokens nuevos aquí.")
    Path tokensOutMija;

    RecuperarCmd(Entorno entornoMija) {
        super(entornoMija);
    }

    @Override
    public Integer call() throws Exception {
        exigirDestinoTokens(tokensOutMija);
        char[] escritoMija = lector().leerSecreto("Token de recuperación");
        TokenRecuperacion tokenMija;
        try {
            tokenMija = TokenRecuperacion.leer(escritoMija);
        } finally {
            Arrays.fill(escritoMija, '\0');
        }
        char[] pinMija = lector().leerSecreto("Contraseña numérica actual (F1)");
        try (TokenRecuperacion tokenUsadoMija = tokenMija;
                Credenciales nuevasMija = credencialesNuevas(llaveNuevaMija.llaveMija)) {
            List<ReglaIncumplida> reglasMija = ValidadorFactores.validar(nuevasMija.pinMija(),
                    nuevasMija.contrasenaMija(), nuevasMija.fraseMija());
            if (!reglasMija.isEmpty()) {
                throw new ExcepcionValidacion(reglasMija);
            }
            try (SesionBoveda sesionMija = entornoMija.appMija().recuperarConToken()
                    .ejecutar(new RecuperarConToken.Comando(bovedaMija, tokenUsadoMija, pinMija))) {
                CambioCredenciales cambioMija = new CambioCredenciales(Optional.of(nuevasMija.pinMija().clone()),
                        Optional.of(nuevasMija.contrasenaMija().clone()), Optional.of(nuevasMija.fraseMija().clone()),
                        nuevasMija.tieneLlave() ? new CambioCredenciales.CambioLlave.Nueva(nuevasMija.huellaLlaveMija().clone())
                                : new CambioCredenciales.CambioLlave.Quitar(), OptionalInt.empty());
                try (cambioMija) {
                    List<TokenRecuperacion> nuevosTokensMija = entornoMija.appMija().rotarCredenciales()
                            .ejecutar(new RotarCredenciales.Comando(sesionMija, Optional.empty(), cambioMija))
                            .tokensNuevosMija();
                    emitirTokens(nuevosTokensMija, tokensOutMija, bovedaMija);
                }
            }
        } finally {
            Arrays.fill(pinMija, '\0');
        }
        entornoMija.salidaMija().println("Credenciales nuevas definidas. El token usado ya no sirve.");
        return CodigosSalida.OK_MIJA;
    }
}
