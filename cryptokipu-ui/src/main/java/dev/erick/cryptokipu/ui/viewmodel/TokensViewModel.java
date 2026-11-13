package dev.erick.cryptokipu.ui.viewmodel;

import dev.erick.cryptokipu.core.auth.TokenRecuperacion;
import dev.erick.cryptokipu.infra.autenticacion.ExportadorTokensTxt;
import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

/**
 * Pantalla de tokens (al crear, al regenerar y al cambiar F1). No se puede continuar sin confirmar que se guardaron
 * en un medio distinto al .kipu. Al cerrar, los tokens se borran de memoria y de los controles. Mostrarlos exige
 * {@code String} (los controles de JavaFX no admiten {@code char[]}): limitación documentada en THREAT_MODEL.
 */
public final class TokensViewModel {

    private final List<TokenRecuperacion> tokensMija = new ArrayList<>();
    private final ObservableList<String> visiblesMija = FXCollections.observableArrayList();
    private final BooleanProperty confirmadoMija = new SimpleBooleanProperty();
    private final BooleanProperty guardadoMija = new SimpleBooleanProperty();
    private final BooleanBinding puedeContinuarMija = confirmadoMija.and(Bindings2.noVacia(visiblesMija));

    public void cargar(List<TokenRecuperacion> nuevosMija) {
        limpiar();
        for (TokenRecuperacion tokenMija : nuevosMija) {
            TokenRecuperacion copiaMija = new TokenRecuperacion(tokenMija.simbolosMija().clone());
            tokensMija.add(copiaMija);
            char[] formateadoMija = copiaMija.formatear();
            visiblesMija.add(new String(formateadoMija));
            Arrays.fill(formateadoMija, '\0');
        }
    }

    /** Guarda el TXT (nunca sobrescribe: {@code FileAlreadyExistsException}). */
    public void guardarTxt(Path destinoMija, String nombreBovedaMija) throws IOException {
        ExportadorTokensTxt.exportar(destinoMija, nombreBovedaMija, LocalDateTime.now(), tokensMija);
        guardadoMija.set(true);
    }

    public String textoParaCopiar() {
        return String.join(System.lineSeparator(), visiblesMija);
    }

    /** Devuelve {@code false} (y no hace nada) si falta la confirmación. */
    public boolean continuar() {
        if (!puedeContinuarMija.get()) {
            return false;
        }
        limpiar();
        return true;
    }

    public void limpiar() {
        tokensMija.forEach(TokenRecuperacion::close);
        tokensMija.clear();
        visiblesMija.clear();
        confirmadoMija.set(false);
        guardadoMija.set(false);
    }

    public ObservableList<String> visibles() {
        return visiblesMija;
    }

    public BooleanProperty confirmadoProperty() {
        return confirmadoMija;
    }

    public BooleanProperty guardadoProperty() {
        return guardadoMija;
    }

    public BooleanBinding puedeContinuarProperty() {
        return puedeContinuarMija;
    }
}
