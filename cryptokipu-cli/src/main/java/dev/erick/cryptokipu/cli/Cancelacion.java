package dev.erick.cryptokipu.cli;

import dev.erick.cryptokipu.core.progreso.ReporteProgreso;
import java.io.PrintStream;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Progreso en stderr y cancelación por Ctrl+C. El shutdown hook llama a {@link #cancelar()}; las operaciones
 * largas lo ven en su siguiente bloque, lanzan {@code OperacionCancelada} y la bóveda queda sin footer nuevo.
 */
public class Cancelacion implements ReporteProgreso {

    private final AtomicBoolean canceladaMija = new AtomicBoolean();
    private final PrintStream erroresMija;
    private int ultimoPorcentajeMija = -1;

    public Cancelacion(PrintStream erroresMija) {
        this.erroresMija = Objects.requireNonNull(erroresMija, "erroresMija");
    }

    public void cancelar() {
        canceladaMija.set(true);
    }

    @Override
    public boolean cancelado() {
        return canceladaMija.get();
    }

    @Override
    public synchronized void avanzar(long hechoMija, long totalMija) {
        if (totalMija <= 0) {
            return;
        }
        int porcentajeMija = (int) Math.min(100, hechoMija * 100 / totalMija);
        if (porcentajeMija / 10 != ultimoPorcentajeMija / 10) {
            ultimoPorcentajeMija = porcentajeMija;
            erroresMija.println("Progreso: " + porcentajeMija + "%");
        }
    }
}
