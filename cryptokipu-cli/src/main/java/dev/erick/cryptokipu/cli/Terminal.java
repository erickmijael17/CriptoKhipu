package dev.erick.cryptokipu.cli;

/** Puerto: ¿la salida estándar es una terminal? Decide si los tokens se pueden mostrar en pantalla. */
@FunctionalInterface
public interface Terminal {

    boolean salidaEsTty();

    /** Terminal real: {@code System.console()} existe y es interactiva (stdin y stdout son terminal). */
    static Terminal delSistema() {
        return () -> System.console() != null && System.console().isTerminal();
    }
}
