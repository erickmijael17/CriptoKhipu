package dev.erick.cryptokipu.cli.comandos;

import picocli.CommandLine.Command;

/** Comando raíz {@code kipu}. Cada subcomando corresponde a un caso de uso. */
@Command(name = "kipu", mixinStandardHelpOptions = true, version = "CryptoKipu 0.1.0 (formato .kipu v1)",
        description = "Cifrador de archivos 100%% offline. Los secretos nunca se pasan como argumentos.",
        subcommands = {CrearCmd.class, InfoCmd.class, ListarCmd.class, AgregarCmd.class, ExtraerCmd.class,
                VerificarCmd.class, CompactarCmd.class, RecuperarCmd.class, RotarCmd.class, TokensCmd.class,
                LlaveCmd.class},
        exitCodeListHeading = "%nCódigos de salida:%n",
        exitCodeList = {"0:Correcto", "1:Error general", "2:Uso incorrecto", "3:Credenciales incorrectas",
                "4:Integridad fallida o archivo inválido", "5:Bóveda en uso", "6:Bóveda destruida",
                "7:Sin espacio o límite del sistema de archivos", "8:Token mal escrito", "9:Rotación pendiente",
                "130:Cancelado (Ctrl+C)"})
public final class Kipu implements Runnable {

    @Override
    public void run() {
        throw new picocli.CommandLine.ParameterException(new picocli.CommandLine(this),
                "Falta el subcomando. Usa 'kipu --help'.");
    }
}
