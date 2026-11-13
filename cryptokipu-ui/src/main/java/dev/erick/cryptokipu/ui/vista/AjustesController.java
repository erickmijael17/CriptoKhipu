package dev.erick.cryptokipu.ui.vista;

import static dev.erick.cryptokipu.ui.Textos.t;

import dev.erick.cryptokipu.ui.ContenedorUi;
import dev.erick.cryptokipu.ui.comun.Tema;
import dev.erick.cryptokipu.ui.viewmodel.AjustesViewModel;
import java.io.IOException;
import java.util.function.Function;
import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;

/** Ajustes: tema (aplicado al instante), idioma (al reiniciar), recientes y bloqueo automático (próxima versión). */
public final class AjustesController {

    private final ContenedorUi uiMija;
    private final AjustesViewModel vmMija;

    @FXML
    private ComboBox<AjustesViewModel.Tema> temaMija;
    @FXML
    private ComboBox<String> idiomaMija;
    @FXML
    private CheckBox recientesMija;
    @FXML
    private CheckBox autobloqueoMija;
    @FXML
    private Label mensajeMija;

    public AjustesController(ContenedorUi uiMija) {
        this.uiMija = uiMija;
        this.vmMija = uiMija.ajustes();
    }

    @FXML
    private void initialize() {
        temaMija.getItems().setAll(AjustesViewModel.Tema.values());
        temaMija.setCellFactory(_ -> celda(temaElegidoMija -> t("ajustes.tema." + temaElegidoMija.name())));
        temaMija.setButtonCell(celda(temaElegidoMija -> t("ajustes.tema." + temaElegidoMija.name())));
        temaMija.setValue(vmMija.temaProperty().get());
        temaMija.valueProperty().addListener((_, _, nuevoMija) -> guardar(() -> {
            vmMija.guardarTema(nuevoMija);
            Tema.aplicar(nuevoMija);
        }));

        // Solo español: CatalogoMensajes (app) aún no tiene traducción; ofrecer inglés mezclaría idiomas.
        idiomaMija.getItems().setAll("es");
        idiomaMija.setCellFactory(_ -> celda(codigoMija -> t("ajustes.idioma." + codigoMija)));
        idiomaMija.setButtonCell(celda(codigoMija -> t("ajustes.idioma." + codigoMija)));
        idiomaMija.setValue(vmMija.idiomaProperty().get());
        idiomaMija.valueProperty().addListener((_, _, nuevoMija) -> guardar(() -> {
            vmMija.guardarIdioma(nuevoMija);
            mensajeMija.setText(t("ajustes.reiniciar"));
        }));

        recientesMija.setSelected(vmMija.recientesActivosProperty().get());
        recientesMija.selectedProperty().addListener((_, _, activoMija) -> guardar(
                () -> vmMija.activarRecientes(activoMija)));
        autobloqueoMija.setSelected(false);
    }

    private static <T> ListCell<T> celda(Function<T, String> textoMija) {
        return new ListCell<>() {
            @Override
            protected void updateItem(T valorMija, boolean vaciaMija) {
                super.updateItem(valorMija, vaciaMija);
                setText(vaciaMija || valorMija == null ? null : textoMija.apply(valorMija));
            }
        };
    }

    @FunctionalInterface
    private interface Accion {
        void ejecutar() throws IOException;
    }

    private void guardar(Accion accionMija) {
        try {
            accionMija.ejecutar();
        } catch (IOException _) {
            uiMija.navegador().notificar(t("ajustes.errorGuardar"));
        }
    }
}
