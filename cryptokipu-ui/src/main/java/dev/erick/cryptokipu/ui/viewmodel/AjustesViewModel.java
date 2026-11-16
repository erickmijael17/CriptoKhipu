package dev.erick.cryptokipu.ui.viewmodel;

import dev.erick.cryptokipu.infra.configuracion.PreferenciasStore;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

/**
 * Ajustes y recientes. Todo se guarda en {@link PreferenciasStore} (en modo portátil, junto a la app). Nunca se
 * guardan credenciales ni nombres de archivos internos; los recientes son rutas de {@code .kipu}.
 */
public final class AjustesViewModel {

    public enum Tema { SISTEMA, CLARO, OSCURO }

    /** Tope del ajuste de auto-bloqueo: 4 horas. */
    public static final int MAXIMO_MINUTOS_MIJA = 240;

    private final PreferenciasStore preferenciasMija;
    private final ObjectProperty<Tema> temaMija = new SimpleObjectProperty<>(Tema.SISTEMA);
    private final StringProperty idiomaMija = new SimpleStringProperty("es");
    private final BooleanProperty recientesActivosMija = new SimpleBooleanProperty(true);
    private final ObservableList<Path> recientesMija = FXCollections.observableArrayList();
    private final IntegerProperty autoBloqueoMinutosMija = new SimpleIntegerProperty(AutoBloqueo.MINUTOS_POR_DEFECTO_MIJA);
    private final BooleanProperty bloquearAlMinimizarMija = new SimpleBooleanProperty(false);

    public AjustesViewModel(PreferenciasStore preferenciasMija) {
        this.preferenciasMija = Objects.requireNonNull(preferenciasMija, "preferenciasMija");
    }

    public void cargar() throws IOException {
        temaMija.set(preferenciasMija.leer(PreferenciasStore.Clave.TEMA).map(Tema::valueOf).orElse(Tema.SISTEMA));
        idiomaMija.set(preferenciasMija.leer(PreferenciasStore.Clave.IDIOMA).orElse("es"));
        recientesActivosMija.set(preferenciasMija.recientesActivos());
        recientesMija.setAll(recientesActivosMija.get() ? preferenciasMija.recientes() : List.of());
        autoBloqueoMinutosMija.set(preferenciasMija.leer(PreferenciasStore.Clave.AUTO_BLOQUEO_MINUTOS)
                .map(AjustesViewModel::minutosValidos).orElse(AutoBloqueo.MINUTOS_POR_DEFECTO_MIJA));
        bloquearAlMinimizarMija.set(preferenciasMija.leer(PreferenciasStore.Clave.BLOQUEAR_AL_MINIMIZAR)
                .map(Boolean::parseBoolean).orElse(false));
    }

    /** Minutos guardados; un valor ilegible o fuera de rango vuelve al predeterminado (5). */
    private static int minutosValidos(String textoMija) {
        try {
            int minutosMija = Integer.parseInt(textoMija.trim());
            return minutosMija >= 0 && minutosMija <= MAXIMO_MINUTOS_MIJA ? minutosMija
                    : AutoBloqueo.MINUTOS_POR_DEFECTO_MIJA;
        } catch (NumberFormatException _) {
            return AutoBloqueo.MINUTOS_POR_DEFECTO_MIJA;
        }
    }

    /** Minutos de inactividad antes de bloquear (0 = desactivado). */
    public void guardarAutoBloqueoMinutos(int minutosMija) throws IOException {
        int validosMija = Math.max(0, Math.min(MAXIMO_MINUTOS_MIJA, minutosMija));
        autoBloqueoMinutosMija.set(validosMija);
        preferenciasMija.guardar(PreferenciasStore.Clave.AUTO_BLOQUEO_MINUTOS, Integer.toString(validosMija));
    }

    public void guardarBloquearAlMinimizar(boolean activoMija) throws IOException {
        bloquearAlMinimizarMija.set(activoMija);
        preferenciasMija.guardar(PreferenciasStore.Clave.BLOQUEAR_AL_MINIMIZAR, Boolean.toString(activoMija));
    }

    public IntegerProperty autoBloqueoMinutosProperty() {
        return autoBloqueoMinutosMija;
    }

    public BooleanProperty bloquearAlMinimizarProperty() {
        return bloquearAlMinimizarMija;
    }

    public void guardarTema(Tema nuevoMija) throws IOException {
        temaMija.set(nuevoMija);
        preferenciasMija.guardar(PreferenciasStore.Clave.TEMA, nuevoMija.name());
    }

    public void guardarIdioma(String nuevoMija) throws IOException {
        idiomaMija.set(nuevoMija);
        preferenciasMija.guardar(PreferenciasStore.Clave.IDIOMA, nuevoMija);
    }

    public void activarRecientes(boolean activoMija) throws IOException {
        preferenciasMija.activarRecientes(activoMija);
        recientesActivosMija.set(activoMija);
        recientesMija.setAll(activoMija ? preferenciasMija.recientes() : List.of());
    }

    public void recordar(Path bovedaMija) throws IOException {
        preferenciasMija.agregarReciente(bovedaMija);
        recientesMija.setAll(recientesActivosMija.get() ? preferenciasMija.recientes() : List.of());
    }

    public ObjectProperty<Tema> temaProperty() {
        return temaMija;
    }

    public StringProperty idiomaProperty() {
        return idiomaMija;
    }

    public BooleanProperty recientesActivosProperty() {
        return recientesActivosMija;
    }

    public ObservableList<Path> recientes() {
        return recientesMija;
    }
}
