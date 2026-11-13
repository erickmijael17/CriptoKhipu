package dev.erick.cryptokipu.ui.comun;

import dev.erick.cryptokipu.ui.viewmodel.ProgresoOperacion;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import javafx.application.Platform;
import javafx.concurrent.Task;

/**
 * Ejecuta las operaciones largas (Argon2id, cifrado, lectura de la bóveda, hash de la llave) en una {@link Task}
 * sobre un hilo virtual, nunca en el hilo de JavaFX. Muestra el progreso con botón cancelar; durante Argon2id el
 * botón indica que no se puede cancelar y, si el usuario cancela, el resultado se descarta (y se cierra si es
 * {@link AutoCloseable}, por ejemplo una sesión ya abierta).
 */
public final class EjecutorTareas {

    /** Trabajo a ejecutar fuera del hilo de JavaFX. */
    @FunctionalInterface
    public interface Operacion<T> {
        T ejecutar(ProgresoOperacion progresoMija) throws Exception;
    }

    /** Dónde se dibuja el progreso (el shell). Todos los métodos se llaman en el hilo de JavaFX. */
    public interface VistaProgreso {
        void mostrar(Runnable alCancelarMija);

        void etapa(String claveTextoMija, boolean cancelableMija);

        void progreso(double fraccionMija);

        void ocultar();
    }

    private final VistaProgreso vistaMija;
    private volatile Thread hiloActualMija;
    private volatile AtomicBoolean canceladaActualMija;

    public EjecutorTareas(VistaProgreso vistaMija) {
        this.vistaMija = Objects.requireNonNull(vistaMija, "vistaMija");
    }

    /** Llamar en el hilo de JavaFX. {@code alTerminarMija} y {@code alFallarMija} también corren en él. */
    public <T> void ejecutar(Operacion<T> operacionMija, Consumer<T> alTerminarMija, Consumer<Throwable> alFallarMija) {
        AtomicBoolean canceladaMija = new AtomicBoolean();
        ProgresoOperacion progresoMija = new ProgresoOperacion() {
            @Override
            public void etapa(String claveTextoMija, boolean cancelableMija) {
                Platform.runLater(() -> vistaMija.etapa(claveTextoMija, cancelableMija));
            }

            @Override
            public void avanzar(long hechoMija, long totalMija) {
                if (totalMija > 0) {
                    double fraccionMija = (double) hechoMija / totalMija;
                    Platform.runLater(() -> vistaMija.progreso(fraccionMija));
                }
            }

            @Override
            public boolean cancelado() {
                return canceladaMija.get();
            }
        };
        Task<T> tareaMija = new Task<>() {
            @Override
            protected T call() throws Exception {
                return operacionMija.ejecutar(progresoMija);
            }
        };
        tareaMija.setOnSucceeded(_ -> {
            terminar();
            T resultadoMija = tareaMija.getValue();
            if (canceladaMija.get()) {
                descartar(resultadoMija);
                return;
            }
            alTerminarMija.accept(resultadoMija);
        });
        tareaMija.setOnFailed(_ -> {
            terminar();
            alFallarMija.accept(tareaMija.getException());
        });
        canceladaActualMija = canceladaMija;
        vistaMija.mostrar(() -> canceladaMija.set(true));
        hiloActualMija = Thread.ofVirtual().name("cryptokipu-tarea").start(tareaMija);
    }

    public boolean enCurso() {
        Thread hiloMija = hiloActualMija;
        return hiloMija != null && hiloMija.isAlive();
    }

    /** Para cerrar la ventana: pide cancelar y espera a que la tarea termine (el resultado se descarta). */
    public void cancelarYEsperar(Duration esperaMija) throws InterruptedException {
        AtomicBoolean canceladaMija = canceladaActualMija;
        if (canceladaMija != null) {
            canceladaMija.set(true);
        }
        Thread hiloMija = hiloActualMija;
        if (hiloMija != null) {
            hiloMija.join(esperaMija);
        }
    }

    private void terminar() {
        hiloActualMija = null;
        vistaMija.ocultar();
    }

    private static void descartar(Object resultadoMija) {
        if (resultadoMija instanceof AutoCloseable cerrableMija) {
            try {
                cerrableMija.close();
            } catch (Exception _) {
                // Se descarta igual: el usuario canceló.
            }
        }
    }
}
