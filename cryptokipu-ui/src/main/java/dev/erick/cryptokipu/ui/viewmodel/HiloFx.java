package dev.erick.cryptokipu.ui.viewmodel;

import javafx.application.Platform;

/**
 * Guardia de hilos: las operaciones de los ViewModels que derivan claves, cifran o leen la bóveda llaman a
 * {@link #exigirFuera()}. Si alguien las ejecuta en el hilo de JavaFX (la interfaz se congelaría segundos),
 * fallan de inmediato en lugar de bloquear.
 *
 * <ul>
 *   <li>Con el toolkit iniciado (la aplicación, el generador de capturas, las pruebas con pantalla) se usa
 *       {@link Platform#isFxApplicationThread()}, la comprobación oficial.</li>
 *   <li>Sin toolkit (pruebas de ViewModels sin pantalla) se compara el nombre del hilo: llamar a
 *       {@code Platform} cargaría el toolkit gráfico. Es solo un respaldo para pruebas.</li>
 * </ul>
 */
public final class HiloFx {

    /** Nombre que JavaFX da a su hilo de aplicación. */
    public static final String NOMBRE_HILO_FX_MIJA = "JavaFX Application Thread";

    private static volatile boolean toolkitIniciadoMija;

    private HiloFx() {
    }

    /** Lo llama quien inicia el toolkit ({@code Application.start}, {@code Platform.startup}). */
    public static void toolkitIniciado() {
        toolkitIniciadoMija = true;
    }

    public static void exigirFuera() {
        boolean enHiloFxMija = toolkitIniciadoMija ? Platform.isFxApplicationThread()
                : NOMBRE_HILO_FX_MIJA.equals(Thread.currentThread().getName());
        if (enHiloFxMija) {
            throw new IllegalStateException("Operación larga en el hilo de JavaFX: debe ir en una tarea");
        }
    }
}
