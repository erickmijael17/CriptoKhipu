package dev.erick.cryptokipu.cli.comandos;

import dev.erick.cryptokipu.app.casos.AgregarArchivos;
import dev.erick.cryptokipu.app.casos.CrearBoveda;
import dev.erick.cryptokipu.app.casos.PoliticaColision;
import dev.erick.cryptokipu.cli.CodigosSalida;
import dev.erick.cryptokipu.cli.Entorno;
import dev.erick.cryptokipu.core.auth.Credenciales;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "create", description = "Crea una bóveda nueva (pide F1, F2 y F3 dos veces) y muestra los 6 tokens.")
final class CrearCmd extends ComandoBase {

    static final class Llave {
        @Option(names = "--keyfile", paramLabel = "<ruta>", required = true,
                description = "Archivo que actúa como llave de seguridad (F4).")
        Path llaveMija;

        @Option(names = "--sin-llave", required = true, description = "Crear la bóveda sin llave de seguridad.")
        boolean sinLlaveMija;
    }

    @Option(names = "--out", required = true, paramLabel = "<bóveda.kipu>", description = "Archivo a crear.")
    Path salidaMija;

    @ArgGroup(exclusive = true, multiplicity = "1")
    Llave llaveMija;

    @Option(names = "--perfil", defaultValue = "estandar",
            description = "Costo de Argon2id: ${COMPLETION-CANDIDATES} (por defecto: ${DEFAULT-VALUE}).")
    Perfil perfilMija;

    @Option(names = "--umbral", defaultValue = "0", paramLabel = "<n>",
            description = "Autodestrucción tras n intentos fallidos (0 = desactivada).")
    int umbralMija;

    @Option(names = "--tokens-out", paramLabel = "<tokens.txt>", description = "Guardar los tokens en este archivo.")
    Path tokensOutMija;

    @Parameters(paramLabel = "<archivos>", arity = "0..*", description = "Archivos o carpetas a agregar.")
    List<Path> archivosMija = new ArrayList<>();

    CrearCmd(Entorno entornoMija) {
        super(entornoMija);
    }

    @Override
    public Integer call() throws Exception {
        exigirDestinoTokens(tokensOutMija);
        try (Credenciales credencialesMija = credencialesNuevas(llaveMija.llaveMija);
                CrearBoveda.Resultado creadaMija = entornoMija.appMija().crearBoveda().ejecutar(
                        new CrearBoveda.Comando(salidaMija, credencialesMija, perfilMija.parametros(), umbralMija))) {
            if (!archivosMija.isEmpty()) {
                AgregarArchivos.Resultado agregadoMija = entornoMija.appMija().agregarArchivos().ejecutar(
                        new AgregarArchivos.Comando(creadaMija.sesionMija(), archivosMija, "", PoliticaColision.OMITIR,
                                entornoMija.cancelacionMija()));
                AgregarCmd.informar(entornoMija, agregadoMija);
            }
            emitirTokens(creadaMija.tokensMija(), tokensOutMija, salidaMija);
            entornoMija.salidaMija().println("Bóveda creada: " + salidaMija);
        }
        return CodigosSalida.OK_MIJA;
    }
}
