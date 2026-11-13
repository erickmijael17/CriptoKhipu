/** Línea de comandos de CryptoKipu ({@code kipu}). */
module dev.erick.cryptokipu.cli {
    requires dev.erick.cryptokipu.infra;
    requires info.picocli;

    // picocli lee y asigna las opciones por reflexión.
    opens dev.erick.cryptokipu.cli.comandos to info.picocli;
}
