package dev.erick.cryptokipu.ui.viewmodel;

import dev.erick.cryptokipu.app.casos.SesionBoveda;
import dev.erick.cryptokipu.infra.configuracion.Contenedor;
import dev.erick.cryptokipu.infra.fs.TempWorkspace;
import dev.erick.cryptokipu.ui.medios.ServidorMedios;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;

/**
 * Estado compartido de la interfaz: la sesión abierta (una a la vez), si está en rotación obligatoria y los
 * recursos que viven lo mismo que la sesión: el servidor de medios local y la carpeta temporal privada para
 * "abrir con aplicación externa". Al cerrar la sesión se cierran los dos (servidor apagado, copias borradas).
 */
public final class EstadoApp {

    private final Contenedor appMija;
    private final ObjectProperty<SesionBoveda> sesionMija = new SimpleObjectProperty<>();
    private final ObjectProperty<Path> rutaMija = new SimpleObjectProperty<>();
    private final BooleanProperty rotacionObligatoriaMija = new SimpleBooleanProperty();
    private final BooleanBinding abiertaMija = Bindings.isNotNull(sesionMija);
    private final List<Runnable> alCerrarMija = new ArrayList<>();
    private ServidorMedios mediosMija;
    private TempWorkspace externosMija;
    private boolean temporalesLimpiosMija = true;

    public EstadoApp(Contenedor appMija) {
        this.appMija = Objects.requireNonNull(appMija, "appMija");
    }

    public Contenedor app() {
        return appMija;
    }

    /** Toma una sesión recién abierta (hilo de JavaFX). Cierra la anterior, si había. */
    public void abrir(Path rutaBovedaMija, SesionBoveda nuevaMija) {
        cerrar();
        rutaMija.set(rutaBovedaMija);
        rotacionObligatoriaMija.set(nuevaMija.rotacionObligatoria());
        sesionMija.set(nuevaMija);
    }

    /**
     * Registra algo que debe hacerse cada vez que la bóveda se cierra, ANTES de apagar el servidor de medios y de
     * cerrar la sesión: detener el reproductor, cerrar los visores, limpiar el portapapeles.
     */
    public void alCerrar(Runnable accionMija) {
        alCerrarMija.add(Objects.requireNonNull(accionMija, "accionMija"));
    }

    /** Cierra la sesión: clave maestra en cero, bloqueo liberado. Idempotente. */
    public void cerrar() {
        SesionBoveda actualMija = sesionMija.get();
        sesionMija.set(null);
        rotacionObligatoriaMija.set(false);
        alCerrarMija.forEach(Runnable::run);
        cerrarRecursos();
        if (actualMija != null) {
            actualMija.close();
        }
    }

    /**
     * Bloqueo (manual, por inactividad o al minimizar): lo mismo que {@link #cerrar()} y además informa de si la
     * carpeta temporal de "abrir con aplicación externa" se pudo borrar por completo.
     *
     * @return {@code false} si una aplicación externa todavía tiene archivos abiertos en esa carpeta
     */
    public boolean bloquear() {
        cerrar();
        return temporalesLimpiosMija;
    }

    /** Ruta de la última bóveda abierta (se conserva al cerrar, para precargarla en el desbloqueo). */
    public Path ultimaRuta() {
        return rutaMija.get();
    }

    /** Servidor de medios de esta sesión (se crea al reproducir el primer audio o video). */
    public synchronized ServidorMedios servidorMedios() throws IOException {
        sesion();
        if (mediosMija == null) {
            mediosMija = ServidorMedios.iniciar();
        }
        return mediosMija;
    }

    /** Carpeta temporal privada de esta sesión para copias que abre una aplicación externa. */
    public synchronized TempWorkspace espacioExterno() throws IOException {
        sesion();
        if (externosMija == null) {
            externosMija = TempWorkspace.crear(Path.of(System.getProperty("java.io.tmpdir")));
        }
        return externosMija;
    }

    /** Deja de servir un medio (y cierra su canal). No hace nada si el servidor ya se cerró con la sesión. */
    public synchronized void retirarMedio(String tokenMija) {
        if (mediosMija != null) {
            mediosMija.retirar(tokenMija);
        }
    }

    private synchronized void cerrarRecursos() {
        temporalesLimpiosMija = true;
        if (mediosMija != null) {
            mediosMija.close();
            mediosMija = null;
        }
        if (externosMija != null) {
            temporalesLimpiosMija = externosMija.cerrarConfirmando();
            externosMija = null;
        }
    }

    public void rotacionTerminada() {
        rotacionObligatoriaMija.set(false);
    }

    public SesionBoveda sesion() {
        SesionBoveda actualMija = sesionMija.get();
        if (actualMija == null) {
            throw new IllegalStateException("No hay una bóveda abierta");
        }
        return actualMija;
    }

    public ReadOnlyObjectProperty<SesionBoveda> sesionProperty() {
        return sesionMija;
    }

    public ReadOnlyObjectProperty<Path> rutaProperty() {
        return rutaMija;
    }

    public BooleanBinding abiertaProperty() {
        return abiertaMija;
    }

    public BooleanProperty rotacionObligatoriaProperty() {
        return rotacionObligatoriaMija;
    }
}
