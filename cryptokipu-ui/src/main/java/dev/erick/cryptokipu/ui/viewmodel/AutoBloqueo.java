package dev.erick.cryptokipu.ui.viewmodel;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/**
 * Auto-bloqueo (Fase 8): por inactividad (teclado y ratón; 5 min por defecto, 0 = desactivado) y, si se pidió, al
 * minimizar. Bloquear es {@link EstadoApp#bloquear()}: detiene el reproductor y el servidor de medios (los
 * {@code alCerrar} del estado), cierra la sesión (clave maestra en cero) y limpia la carpeta temporal; después la
 * interfaz vuelve al desbloqueo con la misma ruta ({@code alBloquear}).
 *
 * <p><b>Espera, no interrumpe:</b> mientras haya una operación en curso (agregar, compactar…), unos tokens recién
 * generados sin guardar o una rotación obligatoria (cerrar en ese punto gastaría el token sin dejar credenciales
 * nuevas), el bloqueo queda pendiente y se aplica en cuanto termina. Sin dependencias de JavaFX: la vista llama a
 * {@link #actividad()} desde un filtro de eventos y a {@link #revisar()} desde un temporizador de 1 s.
 */
public final class AutoBloqueo {

    public static final int MINUTOS_POR_DEFECTO_MIJA = 5;
    private static final long MS_POR_MINUTO_MIJA = 60_000L;

    private final EstadoApp estadoMija;
    private final LongSupplier relojMsMija;
    private final BooleanSupplier ocupadoMija;
    private int minutosMija = MINUTOS_POR_DEFECTO_MIJA;
    private boolean alMinimizarMija;
    private boolean pendientePorMinimizarMija;
    private long ultimaActividadMsMija;
    private Consumer<Boolean> alBloquearMija = _ -> { };

    /**
     * @param ocupadoMija {@code true} mientras algo impide cerrar la sesión (operación en curso, tokens sin guardar)
     * @param relojMsMija reloj monótono en milisegundos (se sustituye en pruebas)
     */
    public AutoBloqueo(EstadoApp estadoMija, BooleanSupplier ocupadoMija, LongSupplier relojMsMija) {
        this.estadoMija = Objects.requireNonNull(estadoMija, "estadoMija");
        this.ocupadoMija = Objects.requireNonNull(ocupadoMija, "ocupadoMija");
        this.relojMsMija = Objects.requireNonNull(relojMsMija, "relojMsMija");
        this.ultimaActividadMsMija = relojMsMija.getAsLong();
        // Abrir una bóveda cuenta como actividad: el plazo empieza desde ese momento.
        estadoMija.abiertaProperty().addListener((_, _, _) -> {
            ultimaActividadMsMija = this.relojMsMija.getAsLong();
            pendientePorMinimizarMija = false;
        });
    }

    /** 0 = desactivado. Los valores negativos se tratan como 0. */
    public void usarMinutos(int nuevosMija) {
        this.minutosMija = Math.max(0, nuevosMija);
    }

    public void bloquearAlMinimizar(boolean activoMija) {
        this.alMinimizarMija = activoMija;
    }

    /** Qué hace la interfaz después de bloquear; recibe {@code false} si quedaron temporales sin borrar. */
    public void alBloquear(Consumer<Boolean> accionMija) {
        this.alBloquearMija = Objects.requireNonNull(accionMija, "accionMija");
    }

    /** Teclado o ratón: reinicia el plazo. */
    public void actividad() {
        ultimaActividadMsMija = relojMsMija.getAsLong();
    }

    /** La ventana se minimizó: si está activado, bloquea (esperando a que termine lo que esté en curso). */
    public void alMinimizar() {
        if (alMinimizarMija && estadoMija.abiertaProperty().get()) {
            pendientePorMinimizarMija = true;
            revisar();
        }
    }

    /** Se llama una vez por segundo (hilo de JavaFX). Bloquea si toca y nada lo impide. */
    public void revisar() {
        if (!estadoMija.abiertaProperty().get()) {
            pendientePorMinimizarMija = false;
            return;
        }
        boolean inactivoMija = minutosMija > 0
                && relojMsMija.getAsLong() - ultimaActividadMsMija >= minutosMija * MS_POR_MINUTO_MIJA;
        if (!inactivoMija && !pendientePorMinimizarMija) {
            return;
        }
        if (ocupadoMija.getAsBoolean() || estadoMija.rotacionObligatoriaProperty().get()) {
            return; // espera: se vuelve a revisar en el próximo segundo
        }
        bloquearAhora();
    }

    /** Bloqueo inmediato: cierra la sesión y avisa a la interfaz. */
    public void bloquearAhora() {
        pendientePorMinimizarMija = false;
        boolean temporalesLimpiosMija = estadoMija.bloquear();
        alBloquearMija.accept(temporalesLimpiosMija);
    }
}
