package dev.erick.cryptokipu.ui.vista;

import static dev.erick.cryptokipu.ui.Textos.t;

import dev.erick.cryptokipu.ui.ContenedorUi;
import dev.erick.cryptokipu.ui.comun.Navegador;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.input.DragEvent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

/** Inicio: crear, abrir (selector o arrastrar un .kipu) y recientes. */
public final class InicioController {

    private final ContenedorUi uiMija;

    @FXML
    private Button crearMija;
    @FXML
    private Button abrirMija;
    @FXML
    private VBox zonaSoltarMija;
    @FXML
    private ListView<Path> recientesMija;

    public InicioController(ContenedorUi uiMija) {
        this.uiMija = uiMija;
    }

    @FXML
    private void initialize() {
        recientesMija.setItems(uiMija.ajustes().recientes());
        recientesMija.setPlaceholder(new Label(t("inicio.sinRecientes")));
        recientesMija.setCellFactory(_ -> new ListCell<>() {
            @Override
            protected void updateItem(Path rutaMija, boolean vaciaMija) {
                super.updateItem(rutaMija, vaciaMija);
                setText(vaciaMija || rutaMija == null ? null : rutaMija.getFileName() + "  —  " + rutaMija.getParent());
            }
        });
        recientesMija.setOnMouseClicked(eventoMija -> {
            if (eventoMija.getButton() == MouseButton.PRIMARY && eventoMija.getClickCount() == 2) {
                abrirSeleccionado();
            }
        });
        recientesMija.setOnKeyPressed(eventoMija -> {
            if (eventoMija.getCode() == KeyCode.ENTER) {
                abrirSeleccionado();
            }
        });
        zonaSoltarMija.setOnDragOver(this::alArrastrar);
        zonaSoltarMija.setOnDragExited(_ -> zonaSoltarMija.getStyleClass().remove("zona-soltar-activa"));
        zonaSoltarMija.setOnDragDropped(eventoMija -> {
            Optional<Path> kipuMija = kipuArrastrado(eventoMija);
            kipuMija.ifPresent(this::abrirBoveda);
            eventoMija.setDropCompleted(kipuMija.isPresent());
            eventoMija.consume();
        });
    }

    private void alArrastrar(DragEvent eventoMija) {
        if (kipuArrastrado(eventoMija).isPresent()) {
            eventoMija.acceptTransferModes(TransferMode.COPY);
            if (!zonaSoltarMija.getStyleClass().contains("zona-soltar-activa")) {
                zonaSoltarMija.getStyleClass().add("zona-soltar-activa");
            }
        }
        eventoMija.consume();
    }

    private static Optional<Path> kipuArrastrado(DragEvent eventoMija) {
        if (!eventoMija.getDragboard().hasFiles() || eventoMija.getDragboard().getFiles().size() != 1) {
            return Optional.empty();
        }
        Path rutaMija = eventoMija.getDragboard().getFiles().getFirst().toPath();
        return rutaMija.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".kipu") ? Optional.of(rutaMija)
                : Optional.empty();
    }

    private void abrirSeleccionado() {
        Path rutaMija = recientesMija.getSelectionModel().getSelectedItem();
        if (rutaMija != null) {
            if (Files.exists(rutaMija)) {
                abrirBoveda(rutaMija);
            } else {
                uiMija.navegador().notificar(t("inicio.noExiste"));
            }
        }
    }

    @FXML
    private void crear() {
        uiMija.navegador().mostrar(Navegador.Vista.ASISTENTE);
    }

    @FXML
    private void abrir() {
        FileChooser selectorMija = new FileChooser();
        selectorMija.setTitle(t("inicio.abrir").replace("_", ""));
        selectorMija.getExtensionFilters().add(new FileChooser.ExtensionFilter("CriptoKhipu (*.kipu)", "*.kipu"));
        File archivoMija = selectorMija.showOpenDialog(uiMija.ventana());
        if (archivoMija != null) {
            abrirBoveda(archivoMija.toPath());
        }
    }

    /** Lleva a la pantalla de desbloqueo con esa bóveda. */
    public void abrirBoveda(Path rutaMija) {
        uiMija.estado().cerrar();
        uiMija.desbloqueo().rutaProperty().set(rutaMija);
        uiMija.navegador().mostrar(Navegador.Vista.DESBLOQUEO);
    }
}
