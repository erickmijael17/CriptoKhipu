package dev.erick.cryptokipu.ui.viewmodel;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/**
 * Limpia el portapapeles después de copiar un token (Fase 8): a los 30 s, al bloquear y al salir, pero
 * <b>solo si todavía contiene ese mismo texto</b>. Si el usuario copió otra cosa después, no se toca.
 *
 * <p>No guarda el texto copiado: solo su huella SHA-256, para poder compararlo sin tener otra copia del token en
 * memoria. Sin dependencias de JavaFX: el portapapeles y el temporizador se inyectan.
 */
public final class GuardiaPortapapeles {

    public static final Duration RETARDO_MIJA = Duration.ofSeconds(30);

    /** Acceso al portapapeles del sistema. */
    public interface Portapapeles {
        /** Texto actual, vacío si no hay o no es texto. */
        Optional<String> texto();

        void limpiar();
    }

    /** Ejecuta una tarea más tarde en el hilo de JavaFX; devuelve cómo cancelarla. */
    @FunctionalInterface
    public interface Programador {
        Runnable programar(Duration retardoMija, Runnable tareaMija);
    }

    private final Portapapeles portapapelesMija;
    private final Programador programadorMija;
    private byte[] huellaMija;
    private Runnable cancelarMija = () -> { };

    public GuardiaPortapapeles(Portapapeles portapapelesMija, Programador programadorMija) {
        this.portapapelesMija = Objects.requireNonNull(portapapelesMija, "portapapelesMija");
        this.programadorMija = Objects.requireNonNull(programadorMija, "programadorMija");
    }

    /** Llamar justo después de poner {@code textoMija} en el portapapeles: arranca la cuenta de 30 s. */
    public void copiado(String textoMija) {
        cancelarMija.run();
        huellaMija = huella(Objects.requireNonNull(textoMija, "textoMija"));
        cancelarMija = programadorMija.programar(RETARDO_MIJA, this::limpiarSiSigueIgual);
    }

    /**
     * Limpia el portapapeles solo si todavía tiene el texto que se copió.
     *
     * @return {@code true} si lo limpió
     */
    public boolean limpiarSiSigueIgual() {
        byte[] esperadaMija = huellaMija;
        cancelarMija.run();
        cancelarMija = () -> { };
        huellaMija = null;
        if (esperadaMija == null) {
            return false;
        }
        boolean igualMija = portapapelesMija.texto()
                .map(actualMija -> MessageDigest.isEqual(esperadaMija, huella(actualMija))).orElse(false);
        if (igualMija) {
            portapapelesMija.limpiar();
        }
        return igualMija;
    }

    /** Al bloquear la bóveda o salir de la aplicación. */
    public void alBloquearOSalir() {
        limpiarSiSigueIgual();
    }

    /** Hay una copia pendiente de limpiar. */
    public boolean pendiente() {
        return huellaMija != null;
    }

    private static byte[] huella(String textoMija) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(textoMija.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException errorMija) {
            throw new IllegalStateException("SHA-256 no disponible", errorMija);
        }
    }
}
