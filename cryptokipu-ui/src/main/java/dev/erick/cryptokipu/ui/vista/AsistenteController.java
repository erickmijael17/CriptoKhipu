package dev.erick.cryptokipu.ui.vista;

import static dev.erick.cryptokipu.ui.Textos.t;

import dev.erick.cryptokipu.ui.ContenedorUi;
import dev.erick.cryptokipu.ui.comun.Navegador;
import dev.erick.cryptokipu.ui.comun.Refrescable;
import dev.erick.cryptokipu.ui.viewmodel.AsistenteCreacionViewModel;
import dev.erick.cryptokipu.ui.viewmodel.AsistenteCreacionViewModel.ModoLlave;
import dev.erick.cryptokipu.ui.viewmodel.AsistenteCreacionViewModel.Paso;
import dev.erick.cryptokipu.ui.viewmodel.Aviso;
import dev.erick.cryptokipu.ui.viewmodel.DesbloqueoViewModel;
import dev.erick.cryptokipu.ui.viewmodel.PerfilUi;
import dev.erick.cryptokipu.ui.viewmodel.ValidacionViva;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import javafx.collections.ListChangeListener;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;

/** Asistente de creación. Cada paso es un panel; el ViewModel decide si se puede avanzar. */
public final class AsistenteController implements Refrescable {

    private final ContenedorUi uiMija;
    private final AsistenteCreacionViewModel vmMija;
    private final Map<Paso, Node> panelesMija = new EnumMap<>(Paso.class);
    private final Map<ValidacionViva.Factor, CampoNuevoSecreto> camposMija = new EnumMap<>(ValidacionViva.Factor.class);

    @FXML
    private Label indicadorPasoMija;
    @FXML
    private StackPane pasosMija;
    @FXML
    private VBox pasoArchivosMija;
    @FXML
    private VBox pasoF1Mija;
    @FXML
    private VBox pasoF2Mija;
    @FXML
    private VBox pasoF3Mija;
    @FXML
    private VBox pasoLlaveMija;
    @FXML
    private VBox pasoPerfilMija;
    @FXML
    private VBox pasoDestinoMija;
    @FXML
    private VBox pasoResumenMija;
    @FXML
    private ListView<Path> archivosMija;
    @FXML
    private Button quitarMija;
    @FXML
    private ToggleGroup grupoLlaveMija;
    @FXML
    private RadioButton sinLlaveMija;
    @FXML
    private RadioButton llaveArchivoMija;
    @FXML
    private RadioButton llaveGenerarMija;
    @FXML
    private Button elegirLlaveMija;
    @FXML
    private Label llaveElegidaMija;
    @FXML
    private ToggleGroup grupoPerfilMija;
    @FXML
    private VBox perfilesMija;
    @FXML
    private Label ramMija;
    @FXML
    private Label avisoLigeroMija;
    @FXML
    private Label destinoElegidoMija;
    @FXML
    private VBox avisosMija;
    @FXML
    private Label resumenMija;
    @FXML
    private Button atrasMija;
    @FXML
    private Button siguienteMija;

    public AsistenteController(ContenedorUi uiMija) {
        this.uiMija = uiMija;
        this.vmMija = uiMija.asistente();
    }

    @FXML
    private void initialize() {
        panelesMija.put(Paso.ARCHIVOS, pasoArchivosMija);
        panelesMija.put(Paso.F1, pasoF1Mija);
        panelesMija.put(Paso.F2, pasoF2Mija);
        panelesMija.put(Paso.F3, pasoF3Mija);
        panelesMija.put(Paso.LLAVE, pasoLlaveMija);
        panelesMija.put(Paso.PERFIL, pasoPerfilMija);
        panelesMija.put(Paso.DESTINO, pasoDestinoMija);
        panelesMija.put(Paso.RESUMEN, pasoResumenMija);
        agregarCampo(ValidacionViva.Factor.F1, pasoF1Mija, "asistente.f1");
        agregarCampo(ValidacionViva.Factor.F2, pasoF2Mija, "asistente.f2");
        agregarCampo(ValidacionViva.Factor.F3, pasoF3Mija, "asistente.f3");

        archivosMija.setItems(vmMija.archivos());
        archivosMija.setPlaceholder(new Label(t("asistente.archivos.ayuda")));
        archivosMija.getSelectionModel().setSelectionMode(javafx.scene.control.SelectionMode.MULTIPLE);
        quitarMija.disableProperty().bind(archivosMija.getSelectionModel().selectedItemProperty().isNull());
        archivosMija.setOnDragOver(eventoMija -> {
            if (eventoMija.getDragboard().hasFiles()) {
                eventoMija.acceptTransferModes(TransferMode.COPY);
            }
            eventoMija.consume();
        });
        archivosMija.setOnDragDropped(eventoMija -> {
            boolean hayMija = eventoMija.getDragboard().hasFiles();
            if (hayMija) {
                eventoMija.getDragboard().getFiles().forEach(archivoMija -> agregarSiNoEsta(archivoMija.toPath()));
            }
            eventoMija.setDropCompleted(hayMija);
            eventoMija.consume();
        });

        sinLlaveMija.setUserData(ModoLlave.SIN_LLAVE);
        llaveArchivoMija.setUserData(ModoLlave.ARCHIVO);
        llaveGenerarMija.setUserData(ModoLlave.GENERAR);
        grupoLlaveMija.selectedToggleProperty().addListener((_, _, elegidoMija) -> {
            if (elegidoMija != null) {
                vmMija.modoLlaveProperty().set((ModoLlave) elegidoMija.getUserData());
                vmMija.rutaLlaveProperty().set(null);
                actualizarBotones();
            }
        });
        elegirLlaveMija.disableProperty().bind(sinLlaveMija.selectedProperty());
        vmMija.rutaLlaveProperty().addListener((_, _, rutaMija) -> llaveElegidaMija.setText(rutaMija == null ? ""
                : rutaMija.toString()));

        for (PerfilUi perfilMija : PerfilUi.values()) {
            RadioButton opcionMija = new RadioButton(t("perfil." + perfilMija.name()));
            opcionMija.setUserData(perfilMija);
            opcionMija.setToggleGroup(grupoPerfilMija);
            opcionMija.setSelected(perfilMija == vmMija.perfilProperty().get());
            perfilesMija.getChildren().add(opcionMija);
        }
        grupoPerfilMija.selectedToggleProperty().addListener((_, _, elegidoMija) -> {
            if (elegidoMija != null) {
                vmMija.perfilProperty().set((PerfilUi) elegidoMija.getUserData());
            }
        });
        vmMija.perfilProperty().addListener((_, _, _) -> actualizarPerfil());
        avisoLigeroMija.managedProperty().bind(avisoLigeroMija.visibleProperty());
        actualizarPerfil();

        destinoElegidoMija.textProperty().bind(vmMija.destinoProperty().asString().map(textoMija ->
                "null".equals(textoMija) ? "" : textoMija));
        vmMija.avisosDestino().addListener((ListChangeListener<Aviso>) _ -> dibujarAvisos());
        vmMija.pasoProperty().addListener((_, _, _) -> mostrarPaso());
        vmMija.archivos().addListener((ListChangeListener<Path>) _ -> actualizarBotones());
        mostrarPaso();
    }

    private void agregarCampo(ValidacionViva.Factor factorMija, VBox panelMija, String prefijoMija) {
        CampoNuevoSecreto campoMija = new CampoNuevoSecreto(vmMija.validacion(factorMija), t(prefijoMija + ".titulo"),
                t(prefijoMija + ".ayuda"));
        camposMija.put(factorMija, campoMija);
        panelMija.getChildren().add(campoMija);
    }

    /** Al entrar desde Inicio: asistente desde cero. */
    @Override
    public void refrescar() {
        reiniciar();
    }

    private void reiniciar() {
        vmMija.limpiar();
        vmMija.archivos().clear();
        vmMija.destinoProperty().set(null);
        vmMija.avisosDestino().clear();
        vmMija.rutaLlaveProperty().set(null);
        sinLlaveMija.setSelected(true);
        camposMija.values().forEach(CampoNuevoSecreto::limpiar);
        mostrarPaso();
    }

    // ------------------------------------------------------------------ pasos

    private void mostrarPaso() {
        Paso pasoMija = vmMija.pasoProperty().get();
        panelesMija.forEach((claveMija, panelMija) -> panelMija.setVisible(claveMija == pasoMija));
        indicadorPasoMija.setText(t("asistente.paso", pasoMija.ordinal() + 1, Paso.values().length));
        if (pasoMija == Paso.RESUMEN) {
            resumenMija.setText(String.join(System.lineSeparator(),
                    t("asistente.resumen.archivos", vmMija.archivos().size()),
                    t("asistente.resumen.llave", textoLlave()),
                    t("asistente.resumen.perfil", t("perfil." + vmMija.perfilProperty().get().name())),
                    t("asistente.resumen.destino", vmMija.destinoProperty().get())));
        }
        actualizarBotones();
        Optional.ofNullable(camposMija.get(factor(pasoMija))).ifPresent(campoMija -> campoMija.campoValor()
                .requestFocus());
    }

    private String textoLlave() {
        return switch (vmMija.modoLlaveProperty().get()) {
            case SIN_LLAVE -> t("asistente.llave.sin");
            case ARCHIVO, GENERAR -> String.valueOf(vmMija.rutaLlaveProperty().get());
        };
    }

    private void actualizarBotones() {
        Paso pasoMija = vmMija.pasoProperty().get();
        atrasMija.setDisable(pasoMija == Paso.ARCHIVOS);
        siguienteMija.setText(pasoMija == Paso.RESUMEN ? t("asistente.crear") : t("asistente.siguiente"));
        boolean esFactorMija = factor(pasoMija) != null;
        siguienteMija.setDisable(!esFactorMija && !vmMija.puedeAvanzar());
    }

    private static ValidacionViva.Factor factor(Paso pasoMija) {
        return switch (pasoMija) {
            case F1 -> ValidacionViva.Factor.F1;
            case F2 -> ValidacionViva.Factor.F2;
            case F3 -> ValidacionViva.Factor.F3;
            default -> null;
        };
    }

    @FXML
    private void siguiente() {
        Paso pasoMija = vmMija.pasoProperty().get();
        ValidacionViva.Factor factorMija = factor(pasoMija);
        if (factorMija != null) {
            CampoNuevoSecreto campoMija = camposMija.get(factorMija);
            boolean escribioMija = !campoMija.campoValor().getCharacters().isEmpty();
            if (escribioMija || !vmMija.tieneSecreto(factorMija)) {
                Optional<String> errorMija = vmMija.fijarSecreto(factorMija, campoMija.tomarValor(),
                        campoMija.tomarRepetido());
                if (errorMija.isPresent()) {
                    campoMija.mostrarError(errorMija.get());
                    return;
                }
            }
        }
        if (pasoMija == Paso.RESUMEN) {
            crear();
            return;
        }
        vmMija.siguiente();
    }

    @FXML
    private void atras() {
        vmMija.atras();
    }

    @FXML
    private void cancelar() {
        reiniciar();
        uiMija.navegador().mostrar(Navegador.Vista.INICIO);
    }

    // ------------------------------------------------------------------ archivos, llave y destino

    @FXML
    private void agregarArchivos() {
        List<File> elegidosMija = new FileChooser().showOpenMultipleDialog(uiMija.ventana());
        if (elegidosMija != null) {
            elegidosMija.forEach(archivoMija -> agregarSiNoEsta(archivoMija.toPath()));
        }
    }

    @FXML
    private void agregarCarpeta() {
        File carpetaMija = new DirectoryChooser().showDialog(uiMija.ventana());
        if (carpetaMija != null) {
            agregarSiNoEsta(carpetaMija.toPath());
        }
    }

    private void agregarSiNoEsta(Path rutaMija) {
        if (!vmMija.archivos().contains(rutaMija)) {
            vmMija.archivos().add(rutaMija);
        }
    }

    @FXML
    private void quitarArchivo() {
        vmMija.archivos().removeAll(List.copyOf(archivosMija.getSelectionModel().getSelectedItems()));
    }

    @FXML
    private void elegirLlave() {
        FileChooser selectorMija = new FileChooser();
        File archivoMija;
        if (vmMija.modoLlaveProperty().get() == ModoLlave.GENERAR) {
            selectorMija.setInitialFileName("llave.kipukey");
            archivoMija = selectorMija.showSaveDialog(uiMija.ventana());
        } else {
            archivoMija = selectorMija.showOpenDialog(uiMija.ventana());
        }
        if (archivoMija != null) {
            vmMija.rutaLlaveProperty().set(archivoMija.toPath());
            actualizarBotones();
        }
    }

    @FXML
    private void elegirDestino() {
        FileChooser selectorMija = new FileChooser();
        selectorMija.setInitialFileName("boveda.kipu");
        selectorMija.getExtensionFilters().add(new FileChooser.ExtensionFilter("CryptoKipu (*.kipu)", "*.kipu"));
        File archivoMija = selectorMija.showSaveDialog(uiMija.ventana());
        if (archivoMija == null) {
            return;
        }
        Path rutaMija = archivoMija.toPath();
        if (!rutaMija.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".kipu")) {
            rutaMija = rutaMija.resolveSibling(rutaMija.getFileName() + ".kipu");
        }
        vmMija.destinoProperty().set(rutaMija);
        vmMija.evaluarDestino();
        actualizarBotones();
    }

    private void actualizarPerfil() {
        PerfilUi perfilMija = vmMija.perfilProperty().get();
        ramMija.setText(t("asistente.perfil.ram", perfilMija.ramRecomendadaMib()));
        avisoLigeroMija.setVisible(perfilMija == PerfilUi.LIGERO);
    }

    private void dibujarAvisos() {
        avisosMija.getChildren().setAll(vmMija.avisosDestino().stream().map(avisoMija -> {
            Label etiquetaMija = new Label((avisoMija.bloqueanteMija() ? "✗ " : "⚠ ") + avisoMija.textoMija());
            etiquetaMija.setWrapText(true);
            etiquetaMija.getStyleClass().add(avisoMija.bloqueanteMija() ? "aviso-bloqueante" : "aviso");
            return etiquetaMija;
        }).toList());
    }

    // ------------------------------------------------------------------ crear

    private void crear() {
        uiMija.ejecutor().ejecutar(vmMija::crear, creadaMija -> {
            try {
                uiMija.ajustes().recordar(creadaMija.rutaMija());
            } catch (IOException _) {
                // Los recientes son una comodidad.
            }
            reiniciar();
            uiMija.estado().abrir(creadaMija.rutaMija(), creadaMija.sesionMija());
            int omitidosMija = creadaMija.archivosMija().omitidosMija().size();
            TokensController.mostrar(uiMija, creadaMija.tokensMija(), creadaMija.rutaMija().getFileName().toString(),
                    () -> uiMija.navegador().notificar(omitidosMija == 0 ? t("notificacion.creada")
                            : t("notificacion.creada") + " " + t("notificacion.omitidos", omitidosMija,
                                    creadaMija.archivosMija().omitidosMija().stream()
                                            .map(omitidoMija -> omitidoMija.origenMija().getFileName().toString())
                                            .limit(5).toList())));
        }, errorMija -> {
            // Los secretos ya se consumieron: hay que volver a escribirlos (archivos y destino se conservan).
            vmMija.limpiar();
            camposMija.values().forEach(CampoNuevoSecreto::limpiar);
            vmMija.evaluarDestino();
            uiMija.navegador().notificar(DesbloqueoViewModel.mensaje(errorMija));
        });
    }
}
