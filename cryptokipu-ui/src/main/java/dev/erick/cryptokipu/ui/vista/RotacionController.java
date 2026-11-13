package dev.erick.cryptokipu.ui.vista;

import static dev.erick.cryptokipu.ui.Textos.t;

import dev.erick.cryptokipu.core.auth.Credenciales;
import dev.erick.cryptokipu.ui.ContenedorUi;
import dev.erick.cryptokipu.ui.viewmodel.CambioCredencialesViewModel;
import dev.erick.cryptokipu.ui.viewmodel.CambioCredencialesViewModel.DecisionLlave;
import dev.erick.cryptokipu.ui.viewmodel.DesbloqueoViewModel;
import dev.erick.cryptokipu.ui.viewmodel.ValidacionViva;
import java.io.File;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import javafx.beans.property.BooleanProperty;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

/**
 * Cambio de credenciales. En la rotación obligatoria (tras un token) el modal es persistente: sin botón cancelar,
 * Escape y el clic fuera no lo cierran, y el menú está deshabilitado hasta terminar. En el cambio voluntario
 * (Seguridad) se eligen los factores y se confirman las credenciales vigentes.
 */
public final class RotacionController {

    private final ContenedorUi uiMija;
    private final Map<ValidacionViva.Factor, CampoNuevoSecreto> nuevosMija = new EnumMap<>(ValidacionViva.Factor.class);
    private CambioCredencialesViewModel vmMija;
    private PanelVigentes panelVigentesMija;

    @FXML
    private Label tituloMija;
    @FXML
    private Label ayudaMija;
    @FXML
    private VBox selectoresMija;
    @FXML
    private CheckBox cambiaF1Mija;
    @FXML
    private CheckBox cambiaF2Mija;
    @FXML
    private CheckBox cambiaF3Mija;
    @FXML
    private VBox camposMija;
    @FXML
    private ComboBox<DecisionLlave> llaveMija;
    @FXML
    private Button elegirLlaveMija;
    @FXML
    private Label llaveElegidaMija;
    @FXML
    private VBox vigentesMija;
    @FXML
    private Label errorMija;
    @FXML
    private Button cancelarMija;
    @FXML
    private Button guardarMija;

    public RotacionController(ContenedorUi uiMija) {
        this.uiMija = uiMija;
    }

    /** Rotación obligatoria tras usar un token. */
    public static void mostrarObligatoria(ContenedorUi uiMija) {
        mostrar(uiMija, true);
    }

    /** Cambio voluntario desde Seguridad. */
    public static void mostrarCambio(ContenedorUi uiMija) {
        mostrar(uiMija, false);
    }

    private static void mostrar(ContenedorUi uiMija, boolean obligatoriaMija) {
        ContenedorUi.Cargado<RotacionController> cargadoMija = uiMija.cargar("rotacion");
        cargadoMija.controladorMija().configurar(new CambioCredencialesViewModel(uiMija.app(), obligatoriaMija));
        uiMija.navegador().abrirModal(cargadoMija.raizMija(), true);
    }

    private void configurar(CambioCredencialesViewModel nuevoVmMija) {
        vmMija = nuevoVmMija;
        boolean obligatoriaMija = vmMija.esObligatoria();
        tituloMija.setText(obligatoriaMija ? t("rotacion.titulo") : t("seguridad.cambiar"));
        ayudaMija.setText(obligatoriaMija ? t("rotacion.ayuda") : t("rotacion.ayudaVoluntaria"));
        ayudaMija.managedProperty().bind(ayudaMija.visibleProperty());
        selectoresMija.setVisible(!obligatoriaMija);
        selectoresMija.setManaged(!obligatoriaMija);
        cancelarMija.setVisible(!obligatoriaMija);
        cambiaF1Mija.selectedProperty().bindBidirectional(vmMija.cambiaF1Property());
        cambiaF2Mija.selectedProperty().bindBidirectional(vmMija.cambiaF2Property());
        cambiaF3Mija.selectedProperty().bindBidirectional(vmMija.cambiaF3Property());
        agregarCampo(ValidacionViva.Factor.F1, "asistente.f1", vmMija.cambiaF1Property());
        agregarCampo(ValidacionViva.Factor.F2, "asistente.f2", vmMija.cambiaF2Property());
        agregarCampo(ValidacionViva.Factor.F3, "asistente.f3", vmMija.cambiaF3Property());

        if (obligatoriaMija) {
            llaveMija.getItems().setAll(DecisionLlave.QUITAR, DecisionLlave.NUEVA, DecisionLlave.GENERAR);
        } else {
            llaveMija.getItems().setAll(DecisionLlave.values());
            panelVigentesMija = new PanelVigentes(uiMija.ventana());
            vigentesMija.getChildren().setAll(panelVigentesMija);
        }
        llaveMija.setCellFactory(_ -> celdaLlave());
        llaveMija.setButtonCell(celdaLlave());
        llaveMija.valueProperty().bindBidirectional(vmMija.llaveProperty());
        elegirLlaveMija.disableProperty().bind(llaveMija.valueProperty().isEqualTo(DecisionLlave.QUITAR)
                .or(llaveMija.valueProperty().isEqualTo(DecisionLlave.SIN_CAMBIO)));
        vmMija.rutaLlaveProperty().addListener((_, _, rutaMija) -> llaveElegidaMija.setText(rutaMija == null ? ""
                : rutaMija.getFileName().toString()));
        errorMija.managedProperty().bind(errorMija.visibleProperty());
        errorMija.setVisible(false);
        nuevosMija.get(ValidacionViva.Factor.F1).campoValor().requestFocus();
    }

    private static ListCell<DecisionLlave> celdaLlave() {
        return new ListCell<>() {
            @Override
            protected void updateItem(DecisionLlave decisionMija, boolean vaciaMija) {
                super.updateItem(decisionMija, vaciaMija);
                setText(vaciaMija || decisionMija == null ? null : t("rotacion.llave." + decisionMija.name()));
            }
        };
    }

    private void agregarCampo(ValidacionViva.Factor factorMija, String prefijoMija, BooleanProperty cambiaMija) {
        CampoNuevoSecreto campoMija = new CampoNuevoSecreto(vmMija.validacion(factorMija), t(prefijoMija + ".titulo"),
                t(prefijoMija + ".ayuda"));
        campoMija.visibleProperty().bind(cambiaMija);
        campoMija.managedProperty().bind(cambiaMija);
        nuevosMija.put(factorMija, campoMija);
        camposMija.getChildren().add(campoMija);
    }

    @FXML
    private void elegirLlave() {
        FileChooser selectorMija = new FileChooser();
        File archivoMija;
        if (vmMija.llaveProperty().get() == DecisionLlave.GENERAR) {
            selectorMija.setInitialFileName("llave.kipukey");
            archivoMija = selectorMija.showSaveDialog(uiMija.ventana());
        } else {
            archivoMija = selectorMija.showOpenDialog(uiMija.ventana());
        }
        if (archivoMija != null) {
            vmMija.rutaLlaveProperty().set(archivoMija.toPath());
        }
    }

    @FXML
    private void cancelar() {
        if (vmMija.solicitarCierre()) {
            nuevosMija.values().forEach(CampoNuevoSecreto::limpiar);
            uiMija.navegador().cerrarModal();
        }
    }

    @FXML
    private void guardar() {
        errorMija.setVisible(false);
        DecisionLlave decisionMija = vmMija.llaveProperty().get();
        Path rutaLlaveMija = vmMija.rutaLlaveProperty().get();
        if ((decisionMija == DecisionLlave.NUEVA || decisionMija == DecisionLlave.GENERAR) && rutaLlaveMija == null) {
            mostrarError(t("error.faltaLlave"));
            return;
        }
        char[] f1Mija = null;
        char[] f2Mija = null;
        char[] f3Mija = null;
        try {
            f1Mija = tomar(ValidacionViva.Factor.F1, vmMija.cambiaF1Property().get());
            f2Mija = tomar(ValidacionViva.Factor.F2, vmMija.cambiaF2Property().get());
            f3Mija = tomar(ValidacionViva.Factor.F3, vmMija.cambiaF3Property().get());
        } catch (IllegalStateException _) {
            CambioCredencialesViewModel.borrar(f1Mija, f2Mija, f3Mija);
            return;
        }
        if (!vmMija.esObligatoria() && f1Mija == null && f2Mija == null && f3Mija == null
                && decisionMija == DecisionLlave.SIN_CAMBIO) {
            mostrarError(t("error.nadaQueCambiar"));
            return;
        }
        PanelVigentes.Vigentes vigentesLeidasMija = panelVigentesMija == null ? null : panelVigentesMija.tomar();
        char[] f1FinalMija = f1Mija;
        char[] f2FinalMija = f2Mija;
        char[] f3FinalMija = f3Mija;
        uiMija.ejecutor().ejecutar(progresoMija -> {
            progresoMija.etapa("etapa.derivando", false);
            Optional<Credenciales> vigentesOpcionalMija = Optional.empty();
            try {
                if (vigentesLeidasMija != null) {
                    vigentesOpcionalMija = Optional.of(vigentesLeidasMija.construir());
                }
                return vmMija.rotar(uiMija.estado().sesion(), vigentesOpcionalMija, f1FinalMija, f2FinalMija,
                        f3FinalMija);
            } finally {
                vigentesOpcionalMija.ifPresent(Credenciales::close);
                CambioCredencialesViewModel.borrar(f1FinalMija, f2FinalMija, f3FinalMija);
            }
        }, tokensMija -> {
            vmMija.rotacionTerminada();
            uiMija.estado().rotacionTerminada();
            uiMija.navegador().notificar(t("notificacion.rotado"));
            if (tokensMija.isEmpty()) {
                uiMija.navegador().cerrarModal();
            } else {
                TokensController.mostrar(uiMija, tokensMija, nombreBoveda(), () -> { });
            }
        }, errorMija -> mostrarError(DesbloqueoViewModel.mensaje(errorMija)));
    }

    private String nombreBoveda() {
        Path rutaMija = uiMija.estado().rutaProperty().get();
        return rutaMija == null ? "" : rutaMija.getFileName().toString();
    }

    /** Valor validado del factor, o null si no cambia. Lanza IllegalStateException si no es válido. */
    private char[] tomar(ValidacionViva.Factor factorMija, boolean cambiaMija) {
        if (!cambiaMija) {
            return null;
        }
        return nuevosMija.get(factorMija).tomarSiValido().orElseThrow(IllegalStateException::new);
    }

    private void mostrarError(String textoMija) {
        errorMija.setText(textoMija);
        errorMija.setVisible(true);
    }
}
