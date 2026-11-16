package dev.erick.cryptokipu.ui.vista;

import static dev.erick.cryptokipu.ui.Textos.t;

import dev.erick.cryptokipu.ui.ContenedorUi;
import dev.erick.cryptokipu.ui.comun.Tema;
import dev.erick.cryptokipu.ui.viewmodel.AjustesViewModel;
import java.io.IOException;
import java.util.function.Function;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;

/** Ajustes: tema (aplicado al instante), idioma (al reiniciar), recientes, bloqueo automático y «Acerca de». */
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
    private Spinner<Integer> autobloqueoMinutosMija;
    @FXML
    private CheckBox bloquearMinimizarMija;
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

        // Auto-bloqueo por inactividad (0 = desactivado) y al minimizar (Fase 8).
        SpinnerValueFactory.IntegerSpinnerValueFactory fabricaMija = new SpinnerValueFactory.IntegerSpinnerValueFactory(
                0, AjustesViewModel.MAXIMO_MINUTOS_MIJA, vmMija.autoBloqueoMinutosProperty().get());
        autobloqueoMinutosMija.setValueFactory(fabricaMija);
        autobloqueoMinutosMija.focusedProperty().addListener((_, _, enfocadoMija) -> {
            if (!enfocadoMija) {
                autobloqueoMinutosMija.increment(0); // confirma lo escrito a mano en el editor
            }
        });
        fabricaMija.valueProperty().addListener((_, _, minutosMija) -> guardar(
                () -> vmMija.guardarAutoBloqueoMinutos(minutosMija == null ? 0 : minutosMija)));
        bloquearMinimizarMija.setSelected(vmMija.bloquearAlMinimizarProperty().get());
        bloquearMinimizarMija.selectedProperty().addListener((_, _, activoMija) -> guardar(
                () -> vmMija.guardarBloquearAlMinimizar(activoMija)));
    }

    @FXML
    private void acercaDe() {
        Alert dialogoMija = new Alert(Alert.AlertType.INFORMATION, t("acercaDe.texto"), ButtonType.OK);
        dialogoMija.setTitle(t("acercaDe.titulo"));
        dialogoMija.setHeaderText(t("acercaDe.encabezado"));
        dialogoMija.initOwner(uiMija.ventana());
        dialogoMija.show();
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
