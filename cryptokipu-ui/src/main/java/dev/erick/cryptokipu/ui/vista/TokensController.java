package dev.erick.cryptokipu.ui.vista;

import static dev.erick.cryptokipu.ui.Textos.t;

import dev.erick.cryptokipu.core.auth.TokenRecuperacion;
import dev.erick.cryptokipu.ui.ContenedorUi;
import dev.erick.cryptokipu.ui.viewmodel.TokensViewModel;
import java.io.File;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.util.List;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.stage.FileChooser;

/**
 * Tokens de recuperación: se muestran una sola vez. Guardar TXT (nunca sobrescribe), copiar, y "Continuar" solo con
 * la casilla marcada. Al continuar se borran de memoria, de la lista y del portapapeles (si siguen ahí); si se copian,
 * el portapapeles se limpia solo a los 30 s ({@code GuardiaPortapapeles}).
 */
public final class TokensController {

    private final ContenedorUi uiMija;
    private final TokensViewModel vmMija = new TokensViewModel();
    private String nombreBovedaMija = "";
    private Runnable despuesMija = () -> { };

    @FXML
    private ListView<String> tokensMija;
    @FXML
    private Button guardarMija;
    @FXML
    private Button copiarMija;
    @FXML
    private Label mensajeMija;
    @FXML
    private CheckBox confirmadoMija;
    @FXML
    private Button continuarMija;

    public TokensController(ContenedorUi uiMija) {
        this.uiMija = uiMija;
    }

    /** Abre el modal persistente con los tokens; {@code despuesMija} corre al continuar. */
    public static void mostrar(ContenedorUi uiMija, List<TokenRecuperacion> tokensNuevosMija, String nombreMija,
            Runnable despuesMija) {
        ContenedorUi.Cargado<TokensController> cargadoMija = uiMija.cargar("tokens");
        TokensController controladorMija = cargadoMija.controladorMija();
        controladorMija.vmMija.cargar(tokensNuevosMija);
        tokensNuevosMija.forEach(TokenRecuperacion::close);
        controladorMija.nombreBovedaMija = nombreMija;
        controladorMija.despuesMija = despuesMija;
        uiMija.tokensPendientes(true); // mientras no se guarden, el auto-bloqueo espera
        uiMija.navegador().abrirModal(cargadoMija.raizMija(), true);
    }

    @FXML
    private void initialize() {
        tokensMija.setItems(vmMija.visibles());
        confirmadoMija.selectedProperty().bindBidirectional(vmMija.confirmadoProperty());
        continuarMija.disableProperty().bind(vmMija.puedeContinuarProperty().not());
        mensajeMija.managedProperty().bind(mensajeMija.visibleProperty());
        mensajeMija.setVisible(false);
    }

    @FXML
    private void guardar() {
        FileChooser selectorMija = new FileChooser();
        selectorMija.setInitialFileName("tokens-" + nombreBovedaMija.replace(".kipu", "") + ".txt");
        selectorMija.getExtensionFilters().add(new FileChooser.ExtensionFilter("TXT", "*.txt"));
        File archivoMija = selectorMija.showSaveDialog(uiMija.ventana());
        if (archivoMija == null) {
            return;
        }
        try {
            vmMija.guardarTxt(archivoMija.toPath(), nombreBovedaMija);
            mostrarMensaje(t("tokens.guardado"));
        } catch (FileAlreadyExistsException _) {
            mostrarMensaje(t("tokens.yaExiste"));
        } catch (IOException _) {
            mostrarMensaje(t("error.general"));
        }
    }

    @FXML
    private void copiar() {
        String textoMija = vmMija.textoParaCopiar();
        uiMija.portapapeles().copiarSinHistorial(textoMija);
        uiMija.guardiaPortapapeles().copiado(textoMija); // se limpia a los 30 s si sigue ahí
        mostrarMensaje(t("tokens.copiados"));
    }

    @FXML
    private void continuar() {
        if (!vmMija.continuar()) {
            return;
        }
        uiMija.guardiaPortapapeles().alBloquearOSalir(); // borra de memoria y, si sigue igual, del portapapeles
        uiMija.tokensPendientes(false);
        uiMija.navegador().cerrarModal();
        despuesMija.run();
    }

    private void mostrarMensaje(String textoMija) {
        mensajeMija.setText(textoMija);
        mensajeMija.setVisible(true);
    }
}
