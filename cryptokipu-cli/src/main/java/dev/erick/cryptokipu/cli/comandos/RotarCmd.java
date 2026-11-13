package dev.erick.cryptokipu.cli.comandos;

import dev.erick.cryptokipu.app.casos.RotarCredenciales;
import dev.erick.cryptokipu.app.casos.SesionBoveda;
import dev.erick.cryptokipu.cli.CodigosSalida;
import dev.erick.cryptokipu.cli.Entorno;
import dev.erick.cryptokipu.cli.ErrorUso;
import dev.erick.cryptokipu.core.auth.CambioCredenciales;
import dev.erick.cryptokipu.core.auth.Credenciales;
import dev.erick.cryptokipu.core.auth.TokenRecuperacion;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 * Cambia credenciales. {@code --f1}, {@code --f2} y {@code --f3} son interruptores sin valor (arity 0): el valor
 * nuevo se pide de forma interactiva, nunca como argumento. Con {@code --f1} se regeneran los 6 tokens.
 */
@Command(name = "rotate", description = "Cambia F1, F2, F3 o la llave (pide primero las credenciales vigentes).")
final class RotarCmd extends ComandoBase {

    static final class CambioLlave {
        @Option(names = "--llave", paramLabel = "<ruta>", required = true, description = "Usar esta llave nueva.")
        Path llaveNuevaMija;

        @Option(names = "--quitar-llave", required = true, description = "Dejar de usar llave de seguridad.")
        boolean quitarMija;
    }

    @Parameters(index = "0", paramLabel = "<bóveda>")
    Path bovedaMija;

    @Option(names = "--keyfile", paramLabel = "<ruta>", description = "Llave vigente, si la bóveda la pide.")
    Path llaveVigenteMija;

    @Option(names = "--f1", arity = "0", description = "Cambiar la contraseña numérica (regenera los tokens).")
    boolean f1Mija;

    @Option(names = "--f2", arity = "0", description = "Cambiar la contraseña alfanumérica.")
    boolean f2Mija;

    @Option(names = "--f3", arity = "0", description = "Cambiar la frase de paso.")
    boolean f3Mija;

    @ArgGroup(exclusive = true, multiplicity = "0..1")
    CambioLlave cambioLlaveMija;

    @Option(names = "--tokens-out", paramLabel = "<tokens.txt>", description = "Guardar los tokens nuevos aquí.")
    Path tokensOutMija;

    RotarCmd(Entorno entornoMija) {
        super(entornoMija);
    }

    @Override
    public Integer call() throws Exception {
        if (!f1Mija && !f2Mija && !f3Mija && cambioLlaveMija == null) {
            throw new ErrorUso("Indica qué cambiar: --f1, --f2, --f3, --llave o --quitar-llave.");
        }
        if (f1Mija) {
            exigirDestinoTokens(tokensOutMija);
        }
        confirmar("Se cambiarán las credenciales de " + bovedaMija + ". ¿Continuar?");
        try (SesionBoveda sesionMija = abrir(bovedaMija, llaveVigenteMija, true);
                Credenciales vigentesMija = tomarVigentes();
                CambioCredenciales cambioMija = new CambioCredenciales(
                        f1Mija ? Optional.of(secretoNuevo("Nueva contraseña numérica (F1)")) : Optional.empty(),
                        f2Mija ? Optional.of(secretoNuevo("Nueva contraseña alfanumérica (F2)")) : Optional.empty(),
                        f3Mija ? Optional.of(secretoNuevo("Nueva frase de paso (F3)")) : Optional.empty(),
                        cambioLlave(), OptionalInt.empty())) {
            List<TokenRecuperacion> tokensMija = entornoMija.appMija().rotarCredenciales()
                    .ejecutar(new RotarCredenciales.Comando(sesionMija, Optional.of(vigentesMija), cambioMija))
                    .tokensNuevosMija();
            if (!tokensMija.isEmpty()) {
                emitirTokens(tokensMija, tokensOutMija, bovedaMija);
            }
        }
        entornoMija.salidaMija().println("Credenciales actualizadas.");
        return CodigosSalida.OK_MIJA;
    }

    private CambioCredenciales.CambioLlave cambioLlave() throws java.io.IOException {
        if (cambioLlaveMija == null) {
            return new CambioCredenciales.CambioLlave.SinCambio();
        }
        if (cambioLlaveMija.quitarMija) {
            return new CambioCredenciales.CambioLlave.Quitar();
        }
        return new CambioCredenciales.CambioLlave.Nueva(huella(cambioLlaveMija.llaveNuevaMija));
    }
}
