package dev.erick.cryptokipu.ui.vista;

import static dev.erick.cryptokipu.ui.Textos.t;

import dev.erick.cryptokipu.app.casos.AgregarArchivos;
import dev.erick.cryptokipu.app.casos.ExtraerArchivo;
import dev.erick.cryptokipu.app.casos.PoliticaColision;
import dev.erick.cryptokipu.app.puertos.VisorDocumentos;
import dev.erick.cryptokipu.core.format.EntradaManifest;
import dev.erick.cryptokipu.ui.ContenedorUi;
import dev.erick.cryptokipu.ui.comun.Refrescable;
import dev.erick.cryptokipu.ui.viewmodel.DesbloqueoViewModel;
import dev.erick.cryptokipu.ui.viewmodel.ExploradorViewModel;
import dev.erick.cryptokipu.ui.viewmodel.ExploradorViewModel.Nodo;
import dev.erick.cryptokipu.ui.viewmodel.ExploradorViewModel.VistaPrevia;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeTableColumn;
import javafx.scene.control.TreeTableView;
import javafx.scene.image.Image;
import javafx.scene.control.Slider;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import javafx.scene.input.KeyCode;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;
import javafx.stage.DirectoryChooser;
import javafx.util.Duration;
import javafx.stage.FileChooser;

/** Explorador de la bóveda abierta. Las operaciones van al {@code EjecutorTareas}; la vista previa nunca toca disco. */
public final class ExploradorController implements Refrescable {

    private static final DateTimeFormatter FECHA_MIJA = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
            .withZone(ZoneId.systemDefault());

    private final ContenedorUi uiMija;
    private final ExploradorViewModel vmMija;

    @FXML
    private Button agregarMija;
    @FXML
    private Button extraerMija;
    @FXML
    private Button extraerTodoMija;
    @FXML
    private Button eliminarMija;
    @FXML
    private Button eliminarCompactarMija;
    @FXML
    private ComboBox<PoliticaColision> politicaMija;
    @FXML
    private TextField filtroMija;
    @FXML
    private TreeTableView<Nodo> tablaMija;
    @FXML
    private TreeTableColumn<Nodo, String> columnaNombreMija;
    @FXML
    private TreeTableColumn<Nodo, String> columnaTamanoMija;
    @FXML
    private TreeTableColumn<Nodo, String> columnaFechaMija;
    @FXML
    private TreeTableColumn<Nodo, String> columnaTipoMija;
    @FXML
    private Label sinPreviaMija;
    @FXML
    private ScrollPane marcoImagenMija;
    @FXML
    private ImageView imagenMija;
    @FXML
    private TextArea textoMija;
    @FXML
    private Label resumenMija;
    @FXML
    private Button abrirExternoMija;
    @FXML
    private VBox panelMedioMija;
    @FXML
    private MediaView vistaMedioMija;
    @FXML
    private Button reproducirMija;
    @FXML
    private Slider posicionMija;
    @FXML
    private Label tiempoMija;
    @FXML
    private HBox controlesPaginaMija;
    @FXML
    private Button paginaAnteriorMija;
    @FXML
    private Button paginaSiguienteMija;
    @FXML
    private Label paginaMija;

    /** Cambia con cada selección: descarta los resultados de vistas previas que llegan tarde. */
    private long generacionMija;
    private VistaPrevia.Documento documentoMija;
    private VisorDocumentos.Paginado paginasMija;
    private int paginaActualMija;
    private MediaPlayer reproductorMija;
    private VistaPrevia.Medio medioActualMija;

    public ExploradorController(ContenedorUi uiMija) {
        this.uiMija = uiMija;
        this.vmMija = uiMija.explorador();
    }

    @FXML
    private void initialize() {
        columna(columnaNombreMija, Nodo::nombreMija);
        columna(columnaTamanoMija, nodoMija -> nodoMija.esCarpeta() ? "" : tamano(nodoMija.tamanoMija()));
        columna(columnaFechaMija, nodoMija -> nodoMija.esCarpeta() || nodoMija.modificadoMija() <= 0 ? ""
                : FECHA_MIJA.format(Instant.ofEpochMilli(nodoMija.modificadoMija())));
        columna(columnaTipoMija, Nodo::tipoMija);
        tablaMija.setColumnResizePolicy(TreeTableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        tablaMija.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        tablaMija.setPlaceholder(new Label(t("explorador.vacia")));
        tablaMija.getSelectionModel().selectedItemProperty().addListener((_, _, _) -> previsualizar());
        tablaMija.setOnKeyPressed(eventoMija -> {
            if (eventoMija.getCode() == KeyCode.DELETE) {
                eliminar();
            }
        });
        var sinSeleccionMija = tablaMija.getSelectionModel().selectedItemProperty().isNull();
        extraerMija.disableProperty().bind(sinSeleccionMija);
        abrirExternoMija.disableProperty().bind(sinSeleccionMija);
        eliminarMija.disableProperty().bind(sinSeleccionMija);
        eliminarCompactarMija.disableProperty().bind(sinSeleccionMija);

        politicaMija.getItems().setAll(PoliticaColision.values());
        politicaMija.setValue(PoliticaColision.RENOMBRAR);
        politicaMija.setCellFactory(_ -> celdaPolitica());
        politicaMija.setButtonCell(celdaPolitica());
        filtroMija.textProperty().bindBidirectional(vmMija.filtroProperty());
        vmMija.filtroProperty().addListener((_, _, _) -> refrescar());

        tablaMija.setOnDragOver(eventoMija -> {
            if (eventoMija.getDragboard().hasFiles()) {
                eventoMija.acceptTransferModes(TransferMode.COPY);
            }
            eventoMija.consume();
        });
        tablaMija.setOnDragDropped(eventoMija -> {
            boolean hayMija = eventoMija.getDragboard().hasFiles();
            if (hayMija) {
                agregarRutas(eventoMija.getDragboard().getFiles().stream().map(File::toPath).toList());
            }
            eventoMija.setDropCompleted(hayMija);
            eventoMija.consume();
        });
    }

    private static void columna(TreeTableColumn<Nodo, String> columnaMija, Function<Nodo, String> valorMija) {
        columnaMija.setCellValueFactory(celdaMija -> new ReadOnlyStringWrapper(
                valorMija.apply(celdaMija.getValue().getValue())));
    }

    private static ListCell<PoliticaColision> celdaPolitica() {
        return new ListCell<>() {
            @Override
            protected void updateItem(PoliticaColision politicaElegidaMija, boolean vaciaMija) {
                super.updateItem(politicaElegidaMija, vaciaMija);
                setText(vaciaMija || politicaElegidaMija == null ? null : t("politica." + politicaElegidaMija.name()));
            }
        };
    }

    static String tamano(long bytesMija) {
        if (bytesMija < 1024) {
            return bytesMija + " B";
        }
        String[] unidadesMija = {"KiB", "MiB", "GiB", "TiB"};
        double valorMija = bytesMija;
        int indiceMija = -1;
        while (valorMija >= 1024 && indiceMija < unidadesMija.length - 1) {
            valorMija /= 1024;
            indiceMija++;
        }
        return String.format(Locale.ROOT, "%.1f %s", valorMija, unidadesMija[indiceMija]);
    }

    /** Reconstruye el árbol desde el manifest de la sesión (en rotación obligatoria, vacío). */
    @Override
    public void refrescar() {
        var estadoMija = uiMija.estado();
        if (!estadoMija.abiertaProperty().get() || estadoMija.rotacionObligatoriaProperty().get()) {
            tablaMija.setRoot(null);
            resumenMija.setText("");
            limpiarPrevia();
            return;
        }
        List<EntradaManifest> entradasMija = estadoMija.sesion().manifest().entradasMija();
        tablaMija.setRoot(vmMija.arbol(entradasMija));
        resumenMija.setText(t("explorador.resumen", entradasMija.size(),
                tamano(entradasMija.stream().mapToLong(EntradaManifest::tamanoMija).sum())));
        limpiarPrevia();
    }

    private List<EntradaManifest> seleccionadas() {
        Set<EntradaManifest> unicasMija = new LinkedHashSet<>();
        for (TreeItem<Nodo> itemMija : new ArrayList<>(tablaMija.getSelectionModel().getSelectedItems())) {
            if (itemMija != null) {
                unicasMija.addAll(ExploradorViewModel.entradasBajo(itemMija));
            }
        }
        return List.copyOf(unicasMija);
    }

    // ------------------------------------------------------------------ vista previa

    private void previsualizar() {
        limpiarPrevia();
        TreeItem<Nodo> itemMija = tablaMija.getSelectionModel().getSelectedItem();
        if (itemMija == null || itemMija.getValue().esCarpeta()
                || tablaMija.getSelectionModel().getSelectedItems().size() != 1) {
            return;
        }
        EntradaManifest entradaMija = itemMija.getValue().entradaMija();
        long generacionPedidaMija = generacionMija;
        uiMija.ejecutor().ejecutar(_ -> vmMija.vistaPrevia(uiMija.estado().sesion(), entradaMija,
                () -> uiMija.estado().servidorMedios()),
                previaMija -> {
                    if (generacionPedidaMija != generacionMija) {
                        liberar(previaMija); // el usuario ya eligió otro archivo
                        return;
                    }
                    mostrarPrevia(previaMija);
                }, errorMija -> uiMija.navegador().notificar(DesbloqueoViewModel.mensaje(errorMija)));
    }

    private void mostrarPrevia(VistaPrevia previaMija) {
        switch (previaMija) {
            case VistaPrevia.Imagen imagenLeidaMija -> mostrarImagen(new Image(
                    new ByteArrayInputStream(imagenLeidaMija.bytesMija())));
            case VistaPrevia.Texto textoLeidoMija -> mostrarTexto(textoLeidoMija.contenidoMija());
            case VistaPrevia.Documento documentoNuevoMija -> mostrarDocumento(documentoNuevoMija);
            case VistaPrevia.Medio medioNuevoMija -> mostrarMedio(medioNuevoMija);
            case VistaPrevia.Externa motivoMija -> mostrarMensaje(t(motivoMija.claveMotivoMija()));
        }
    }

    private void mostrarImagen(Image imagenNuevaMija) {
        imagenMija.setImage(imagenNuevaMija);
        imagenMija.fitWidthProperty().bind(marcoImagenMija.widthProperty().subtract(16));
        marcoImagenMija.setVisible(true);
        sinPreviaMija.setVisible(false);
    }

    private void mostrarTexto(String contenidoMija) {
        textoMija.setText(contenidoMija);
        textoMija.setVisible(true);
        sinPreviaMija.setVisible(false);
    }

    private void mostrarMensaje(String mensajeMija) {
        sinPreviaMija.setText(mensajeMija);
        sinPreviaMija.setVisible(true);
    }

    private void mostrarDocumento(VistaPrevia.Documento documentoNuevoMija) {
        documentoMija = documentoNuevoMija;
        switch (documentoNuevoMija.documentoMija()) {
            case VisorDocumentos.Textual textualMija -> mostrarTexto(textualMija.contenidoMija());
            case VisorDocumentos.Paginado paginadoMija -> {
                paginasMija = paginadoMija;
                paginaActualMija = 0;
                controlesPaginaMija.setVisible(true);
                controlesPaginaMija.setManaged(true);
                renderizarPagina();
            }
        }
    }

    /** Renderiza la página actual bajo demanda (fuera del hilo de JavaFX) y la muestra. */
    private void renderizarPagina() {
        VisorDocumentos.Paginado visorMija = paginasMija;
        int indiceMija = paginaActualMija;
        long generacionPedidaMija = generacionMija;
        paginaMija.setText(t("pagina.indicador", indiceMija + 1, visorMija.paginas()));
        paginaAnteriorMija.setDisable(indiceMija == 0);
        paginaSiguienteMija.setDisable(indiceMija >= visorMija.paginas() - 1);
        int anchoMija = (int) Math.max(400, marcoImagenMija.getWidth() * 1.5);
        uiMija.ejecutor().ejecutar(_ -> visorMija.renderizar(indiceMija, anchoMija), paginaImagenMija -> {
            if (generacionPedidaMija == generacionMija) {
                mostrarImagen(aImagenFx(paginaImagenMija));
            }
        }, errorMija -> uiMija.navegador().notificar(DesbloqueoViewModel.mensaje(errorMija)));
    }

    /** ARGB de Java2D a una imagen de JavaFX sin {@code javafx.swing}. */
    static WritableImage aImagenFx(VisorDocumentos.Imagen origenMija) {
        WritableImage destinoMija = new WritableImage(origenMija.anchoMija(), origenMija.altoMija());
        destinoMija.getPixelWriter().setPixels(0, 0, origenMija.anchoMija(), origenMija.altoMija(),
                PixelFormat.getIntArgbInstance(), origenMija.argbMija(), 0, origenMija.anchoMija());
        return destinoMija;
    }

    @FXML
    private void paginaAnterior() {
        if (paginasMija != null && paginaActualMija > 0) {
            paginaActualMija--;
            renderizarPagina();
        }
    }

    @FXML
    private void paginaSiguiente() {
        if (paginasMija != null && paginaActualMija < paginasMija.paginas() - 1) {
            paginaActualMija++;
            renderizarPagina();
        }
    }

    private void mostrarMedio(VistaPrevia.Medio medioNuevoMija) {
        medioActualMija = medioNuevoMija;
        MediaPlayer reproductorNuevoMija = new MediaPlayer(new Media(medioNuevoMija.urlMija()));
        reproductorMija = reproductorNuevoMija;
        vistaMedioMija.setMediaPlayer(reproductorNuevoMija);
        vistaMedioMija.fitWidthProperty().bind(panelMedioMija.widthProperty());
        vistaMedioMija.fitHeightProperty().bind(panelMedioMija.heightProperty().subtract(48));
        reproducirMija.setText(t("medio.reproducir"));
        posicionMija.setValue(0);
        posicionMija.setMax(1);
        reproductorNuevoMija.setOnReady(() -> posicionMija.setMax(
                Math.max(1, reproductorNuevoMija.getTotalDuration().toSeconds())));
        reproductorNuevoMija.currentTimeProperty().addListener((_, _, tiempoActualMija) -> {
            if (!posicionMija.isValueChanging()) {
                posicionMija.setValue(tiempoActualMija.toSeconds());
            }
            actualizarTiempo(tiempoActualMija);
        });
        // Adelantar y retroceder: cada seek del reproductor genera una petición Range al servidor local.
        posicionMija.valueChangingProperty().addListener((_, _, cambiandoMija) -> {
            if (!cambiandoMija) {
                reproductorNuevoMija.seek(Duration.seconds(posicionMija.getValue()));
            }
        });
        posicionMija.setOnMouseReleased(_ -> reproductorNuevoMija.seek(Duration.seconds(posicionMija.getValue())));
        reproductorNuevoMija.setOnEndOfMedia(() -> {
            reproductorNuevoMija.stop();
            reproducirMija.setText(t("medio.reproducir"));
        });
        reproductorNuevoMija.setOnError(() -> {
            // Formato que la plataforma no decodifica (HEVC sin códec, etc.): se ofrece la aplicación externa.
            liberarMedio();
            mostrarMensaje(t("previa.medioNoReproducible"));
        });
        panelMedioMija.setVisible(true);
        sinPreviaMija.setVisible(false);
    }

    private void actualizarTiempo(Duration actualMija) {
        Duration totalMija = reproductorMija == null ? Duration.ZERO : reproductorMija.getTotalDuration();
        tiempoMija.setText(formatoTiempo(actualMija) + " / " + formatoTiempo(totalMija));
    }

    static String formatoTiempo(Duration duracionMija) {
        if (duracionMija == null || duracionMija.isUnknown() || duracionMija.isIndefinite()) {
            return "--:--";
        }
        long segundosMija = (long) duracionMija.toSeconds();
        return String.format(Locale.ROOT, "%d:%02d", segundosMija / 60, segundosMija % 60);
    }

    @FXML
    private void alternarReproduccion() {
        if (reproductorMija == null) {
            return;
        }
        if (reproductorMija.getStatus() == MediaPlayer.Status.PLAYING) {
            reproductorMija.pause();
            reproducirMija.setText(t("medio.reproducir"));
        } else {
            reproductorMija.play();
            reproducirMija.setText(t("medio.pausar"));
        }
    }

    private void liberarMedio() {
        if (reproductorMija != null) {
            reproductorMija.dispose();
            reproductorMija = null;
        }
        vistaMedioMija.setMediaPlayer(null);
        panelMedioMija.setVisible(false);
        if (medioActualMija != null) {
            uiMija.estado().retirarMedio(medioActualMija.tokenMija());
            medioActualMija = null;
        }
    }

    private static void liberar(VistaPrevia previaMija) {
        if (previaMija instanceof VistaPrevia.Documento documentoCerrarMija) {
            documentoCerrarMija.close();
        }
    }

    /** Detiene y cierra todo lo de la vista previa anterior: reproductor, token del servidor y documento. */
    private void limpiarPrevia() {
        generacionMija++;
        liberarMedio();
        if (documentoMija != null) {
            documentoMija.close();
            documentoMija = null;
        }
        paginasMija = null;
        controlesPaginaMija.setVisible(false);
        controlesPaginaMija.setManaged(false);
        imagenMija.fitWidthProperty().unbind();
        imagenMija.setImage(null);
        textoMija.clear();
        marcoImagenMija.setVisible(false);
        textoMija.setVisible(false);
        sinPreviaMija.setText(t("explorador.sinSeleccion"));
        sinPreviaMija.setVisible(true);
    }

    /** "Abrir con aplicación externa": copia en la carpeta temporal privada, con advertencia explícita. */
    @FXML
    private void abrirExterno() {
        TreeItem<Nodo> itemMija = tablaMija.getSelectionModel().getSelectedItem();
        if (itemMija == null || itemMija.getValue().esCarpeta()) {
            return;
        }
        EntradaManifest entradaMija = itemMija.getValue().entradaMija();
        Alert advertenciaMija = new Alert(Alert.AlertType.WARNING,
                t("externa.aviso", itemMija.getValue().nombreMija()), ButtonType.YES, ButtonType.NO);
        advertenciaMija.initOwner(uiMija.ventana());
        advertenciaMija.setTitle(t("externa.titulo"));
        advertenciaMija.setHeaderText(t("externa.titulo"));
        if (advertenciaMija.showAndWait().orElse(ButtonType.NO) != ButtonType.YES) {
            return;
        }
        uiMija.ejecutor().ejecutar(progresoMija -> vmMija.copiaParaAplicacionExterna(uiMija.estado().sesion(),
                entradaMija, uiMija.estado().espacioExterno().carpeta(), progresoMija), copiaMija -> {
                    try {
                        uiMija.abrirExterno(copiaMija);
                        uiMija.navegador().notificar(t("externa.abierta"));
                    } catch (RuntimeException errorMija) {
                        uiMija.navegador().notificar(t("externa.fallo", errorMija.getMessage()));
                    }
                }, errorMija -> uiMija.navegador().notificar(DesbloqueoViewModel.mensaje(errorMija)));
    }

    // ------------------------------------------------------------------ operaciones

    @FXML
    private void agregar() {
        List<File> elegidosMija = new FileChooser().showOpenMultipleDialog(uiMija.ventana());
        if (elegidosMija != null) {
            agregarRutas(elegidosMija.stream().map(File::toPath).toList());
        }
    }

    private void agregarRutas(List<Path> rutasMija) {
        PoliticaColision politicaElegidaMija = politicaMija.getValue();
        uiMija.ejecutor().ejecutar(progresoMija -> vmMija.agregar(uiMija.estado().sesion(), rutasMija,
                politicaElegidaMija, progresoMija), this::agregados,
                errorMija -> {
                    refrescar();
                    uiMija.navegador().notificar(DesbloqueoViewModel.mensaje(errorMija));
                });
    }

    private void agregados(AgregarArchivos.Resultado resultadoMija) {
        refrescar();
        String textoMija = t("notificacion.agregados", resultadoMija.agregadosMija().size());
        if (!resultadoMija.omitidosMija().isEmpty()) {
            textoMija += " " + t("notificacion.omitidos", resultadoMija.omitidosMija().size(),
                    resultadoMija.omitidosMija().stream().map(omitidoMija -> omitidoMija.origenMija().getFileName()
                            + " (" + omitidoMija.motivoMija() + ")").limit(5).toList());
        }
        uiMija.navegador().notificar(textoMija);
    }

    @FXML
    private void extraer() {
        extraerEntradas(seleccionadas());
    }

    @FXML
    private void extraerTodo() {
        if (uiMija.estado().abiertaProperty().get()) {
            extraerEntradas(uiMija.estado().sesion().manifest().entradasMija());
        }
    }

    private void extraerEntradas(List<EntradaManifest> entradasMija) {
        if (entradasMija.isEmpty()) {
            return;
        }
        File carpetaMija = new DirectoryChooser().showDialog(uiMija.ventana());
        if (carpetaMija == null) {
            return;
        }
        PoliticaColision politicaElegidaMija = politicaMija.getValue();
        uiMija.ejecutor().ejecutar(progresoMija -> vmMija.extraer(uiMija.estado().sesion(), entradasMija,
                carpetaMija.toPath(), politicaElegidaMija, progresoMija), this::extraidos,
                errorMija -> uiMija.navegador().notificar(DesbloqueoViewModel.mensaje(errorMija)));
    }

    private void extraidos(ExtraerArchivo.Resultado resultadoMija) {
        String textoMija = t("notificacion.extraidos", resultadoMija.extraidosMija().size());
        if (!resultadoMija.rechazadosMija().isEmpty()) {
            textoMija += " " + t("notificacion.omitidos", resultadoMija.rechazadosMija().size(),
                    resultadoMija.rechazadosMija().stream().map(rechazoMija -> rechazoMija.rutaInternaMija()
                            + " (" + rechazoMija.motivoMija() + ")").limit(5).toList());
        }
        uiMija.navegador().notificar(textoMija);
    }

    @FXML
    private void eliminar() {
        eliminarEntradas(false);
    }

    @FXML
    private void eliminarCompactar() {
        eliminarEntradas(true);
    }

    private void eliminarEntradas(boolean compactarMija) {
        List<EntradaManifest> entradasMija = seleccionadas();
        if (entradasMija.isEmpty()) {
            return;
        }
        Alert confirmacionMija = new Alert(Alert.AlertType.CONFIRMATION,
                compactarMija ? t("explorador.eliminarCompactarPregunta", entradasMija.size())
                        : t("explorador.eliminarPregunta", entradasMija.size()) + "\n\n" + t("explorador.eliminarAviso"),
                ButtonType.YES, ButtonType.NO);
        confirmacionMija.initOwner(uiMija.ventana());
        confirmacionMija.setHeaderText(null);
        if (confirmacionMija.showAndWait().orElse(ButtonType.NO) != ButtonType.YES) {
            return;
        }
        uiMija.ejecutor().ejecutar(progresoMija -> {
            vmMija.eliminar(uiMija.estado().sesion(), entradasMija, compactarMija, progresoMija);
            return entradasMija.size();
        }, cantidadMija -> {
            refrescar();
            uiMija.navegador().notificar(t("notificacion.eliminados", cantidadMija));
        }, errorMija -> {
            refrescar();
            uiMija.navegador().notificar(DesbloqueoViewModel.mensaje(errorMija));
        });
    }
}
