package dev.erick.cryptokipu.ui.comun;

import dev.erick.cryptokipu.core.registro.Registro;
import java.util.concurrent.atomic.AtomicLong;
import javafx.animation.AnimationTimer;

/**
 * Modo desarrollo ({@code -Dcryptokipu.desarrollo=true}): registra cada vez que el hilo de JavaFX tarda más de
 * 100 ms entre dos pulsos de animación. Base para la medición de rendimiento de la Fase 9.
 */
public final class DetectorBloqueos extends AnimationTimer {

    public static final long UMBRAL_NANOS_MIJA = 100_000_000L;
    private static final System.Logger REGISTRO_MIJA = Registro.para(DetectorBloqueos.class);

    private long anteriorMija;
    private final AtomicLong peorMija = new AtomicLong();

    public static boolean activado() {
        return Boolean.getBoolean("cryptokipu.desarrollo");
    }

    @Override
    public void handle(long ahoraMija) {
        if (anteriorMija != 0) {
            long pausaMija = ahoraMija - anteriorMija;
            if (pausaMija > UMBRAL_NANOS_MIJA) {
                peorMija.accumulateAndGet(pausaMija, Math::max);
                REGISTRO_MIJA.log(System.Logger.Level.WARNING,
                        "Hilo de JavaFX bloqueado " + pausaMija / 1_000_000 + " ms");
            }
        }
        anteriorMija = ahoraMija;
    }

    /** Peor bloqueo observado, en milisegundos. */
    public long peorMilisegundos() {
        return peorMija.get() / 1_000_000;
    }
}
