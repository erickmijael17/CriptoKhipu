package dev.erick.cryptokipu.cli;

import dev.erick.cryptokipu.infra.configuracion.Contenedor;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.Objects;
import java.util.Optional;

/**
 * Todo lo que la CLI toma del mundo exterior. En producción lo arma {@link Main}; las pruebas lo sustituyen.
 *
 * @param appMija            casos de uso
 * @param salidaMija         resultados (stdout)
 * @param erroresMija        progreso y mensajes (stderr)
 * @param entradaMija        stdin (solo con {@code --secretos-stdin})
 * @param lectorConsolaMija  secretos desde la consola interactiva, si existe
 * @param terminalMija       si stdout es una terminal
 * @param cancelacionMija    progreso y cancelación (Ctrl+C)
 * @param pausaMija          espera entre intentos fallidos de la consola interactiva (se sustituye en pruebas)
 */
public record Entorno(Contenedor appMija, PrintStream salidaMija, PrintStream erroresMija, InputStream entradaMija,
        Optional<LectorSecretos> lectorConsolaMija, Terminal terminalMija, Cancelacion cancelacionMija,
        Pausa pausaMija) {

    /** Entorno con la pausa real (dormir el hilo). */
    public Entorno(Contenedor appMija, PrintStream salidaMija, PrintStream erroresMija, InputStream entradaMija,
            Optional<LectorSecretos> lectorConsolaMija, Terminal terminalMija, Cancelacion cancelacionMija) {
        this(appMija, salidaMija, erroresMija, entradaMija, lectorConsolaMija, terminalMija, cancelacionMija,
                Pausa.REAL_MIJA);
    }

    public Entorno {
        Objects.requireNonNull(appMija, "appMija");
        Objects.requireNonNull(salidaMija, "salidaMija");
        Objects.requireNonNull(erroresMija, "erroresMija");
        Objects.requireNonNull(entradaMija, "entradaMija");
        Objects.requireNonNull(lectorConsolaMija, "lectorConsolaMija");
        Objects.requireNonNull(terminalMija, "terminalMija");
        Objects.requireNonNull(cancelacionMija, "cancelacionMija");
        Objects.requireNonNull(pausaMija, "pausaMija");
    }
}
