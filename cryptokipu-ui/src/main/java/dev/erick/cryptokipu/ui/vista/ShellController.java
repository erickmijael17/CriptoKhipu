package dev.erick.cryptokipu.ui.vista;

import static dev.erick.cryptokipu.ui.Textos.t;

import atlantafx.base.controls.ModalPane;
import dev.erick.cryptokipu.ui.ContenedorUi;
import dev.erick.cryptokipu.ui.comun.EjecutorTareas;
import dev.erick.cryptokipu.ui.comun.Navegador;
import dev.erick.cryptokipu.ui.comun.Refrescable;
import java.util.EnumMap;
import java.util.Map;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.beans.binding.Bindings;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.input.InputEvent;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;

/**
 * Ventana principal: menú lateral, vistas cacheadas (se cargan la primera vez que se usan, salvo Inicio), modales
 * de AtlantaFX y capa de progreso. Solo enlaza; la lógica está en los ViewModels.
 */
public final class ShellController implements Navegador, EjecutorTareas.VistaProgreso {

    private final ContenedorUi uiMija;
    private final Map<Vista, ContenedorUi.Cargado<?>> vistasMija = new EnumMap<>(Vista.class);
    private final ModalPane modalMija = new ModalPane();
    private final PauseTransition ocultarNotificacionMija = new PauseTransition(Duration.seconds(5));
    private final Timeline revisionAutoBloqueoMija;
    private Runnable alCancelarMija = () -> { };

    @FXML
    private StackPane raizMija;
    @FXML
    private StackPane contenidoMija;
    @FXML
    private Button botonInicioMija;
    @FXML
    private Button botonExploradorMija;
    @FXML
    private Button botonSeguridadMija;
    @FXML
    private Button botonAjustesMija;
    @FXML
    private Button botonCerrarBovedaMija;
    @FXML
    private Label rutaBovedaMija;
    @FXML
    private Label notificacionMija;
    @FXML
    private StackPane capaProgresoMija;
    @FXML
    private Label etapaMija;
    @FXML
    private ProgressBar barraProgresoMija;
    @FXML
    private Button cancelarProgresoMija;

    public ShellController(ContenedorUi uiMija) {
        this.uiMija = uiMija;
        this.revisionAutoBloqueoMija = new Timeline(new KeyFrame(Duration.seconds(1),
                _ -> uiMija.autoBloqueo().revisar()));
    }

    @FXML
    private void initialize() {
        uiMija.usarNavegador(this);
        uiMija.usarEjecutor(new EjecutorTareas(this));
        modalMija.setAlignment(Pos.CENTER);
        // Sin animaciones: un modal puede reemplazar a otro (rotación → tokens) sin carreras con la salida.
        modalMija.setInTransitionFactory(null);
        modalMija.setOutTransitionFactory(null);
        raizMija.getChildren().add(modalMija);

        var estadoMija = uiMija.estado();
        var bloqueadoMija = estadoMija.abiertaProperty().not().or(estadoMija.rotacionObligatoriaProperty());
        botonExploradorMija.disableProperty().bind(bloqueadoMija);
        botonSeguridadMija.disableProperty().bind(bloqueadoMija);
        botonInicioMija.disableProperty().bind(estadoMija.rotacionObligatoriaProperty());
        botonAjustesMija.disableProperty().bind(estadoMija.rotacionObligatoriaProperty());
        botonCerrarBovedaMija.visibleProperty().bind(estadoMija.abiertaProperty());
        botonCerrarBovedaMija.managedProperty().bind(botonCerrarBovedaMija.visibleProperty());
        rutaBovedaMija.textProperty().bind(Bindings.createStringBinding(() -> estadoMija.rutaProperty().get() == null
                || !estadoMija.abiertaProperty().get() ? "" : estadoMija.rutaProperty().get().getFileName().toString(),
                estadoMija.rutaProperty(), estadoMija.abiertaProperty()));

        estadoMija.abiertaProperty().addListener((_, _, abiertaMija) -> mostrar(abiertaMija ? Vista.EXPLORADOR : Vista.INICIO));
        estadoMija.rotacionObligatoriaProperty().addListener((_, _, obligatoriaMija) -> {
            if (obligatoriaMija) {
                RotacionController.mostrarObligatoria(uiMija);
            } else if (estadoMija.abiertaProperty().get()) {
                mostrar(Vista.EXPLORADOR);
            }
        });
        ocultarNotificacionMija.setOnFinished(_ -> notificacionMija.setVisible(false));
        notificacionMija.managedProperty().bind(notificacionMija.visibleProperty());
        mostrar(Vista.INICIO);
        iniciarAutoBloqueo();
    }

    // ------------------------------------------------------------------ auto-bloqueo (Fase 8)

    /** Actividad = teclado y ratón (filtro de eventos en la escena); una revisión por segundo. */
    private void iniciarAutoBloqueo() {
        var autoBloqueoMija = uiMija.autoBloqueo();
        autoBloqueoMija.alBloquear(this::alBloquear);
        if (raizMija.getScene() != null) {
            vigilarActividad(raizMija.getScene());
        }
        raizMija.sceneProperty().addListener((_, _, escenaMija) -> {
            if (escenaMija != null) {
                vigilarActividad(escenaMija);
            }
        });
        revisionAutoBloqueoMija.setCycleCount(Animation.INDEFINITE);
        revisionAutoBloqueoMija.play();
    }

    private void vigilarActividad(Scene escenaMija) {
        escenaMija.addEventFilter(InputEvent.ANY, _ -> uiMija.autoBloqueo().actividad());
    }

    /** Después de bloquear: vuelve al desbloqueo con la misma ruta y avisa si una app externa retiene archivos. */
    private void alBloquear(boolean temporalesLimpiosMija) {
        cerrarModal();
        uiMija.desbloqueo().rutaProperty().set(uiMija.estado().ultimaRuta());
        mostrar(Vista.DESBLOQUEO);
        notificar(t("bloqueo.notificacion"));
        if (!temporalesLimpiosMija) {
            Alert avisoMija = new Alert(Alert.AlertType.WARNING, t("bloqueo.externoAbierto"), ButtonType.OK);
            avisoMija.setHeaderText(t("bloqueo.externoTitulo"));
            if (uiMija.ventana() != null) {
                avisoMija.initOwner(uiMija.ventana());
            }
            avisoMija.show(); // show, no showAndWait: esto corre dentro de un pulso del temporizador
        }
    }

    // ------------------------------------------------------------------ Navegador

    @Override
    public void mostrar(Vista vistaMija) {
        ContenedorUi.Cargado<?> cargadoMija = vistasMija.computeIfAbsent(vistaMija,
                claveMija -> uiMija.cargar(claveMija.name().toLowerCase(java.util.Locale.ROOT)));
        contenidoMija.getChildren().setAll(cargadoMija.raizMija());
        if (cargadoMija.controladorMija() instanceof Refrescable refrescableMija) {
            refrescableMija.refrescar();
        }
    }

    @Override
    public void abrirModal(Node contenidoNuevoMija, boolean persistenteMija) {
        modalMija.setPersistent(persistenteMija);
        modalMija.show(contenidoNuevoMija);
    }

    @Override
    public void cerrarModal() {
        modalMija.hide(true);
    }

    @Override
    public void notificar(String textoMija) {
        notificacionMija.setText(textoMija);
        notificacionMija.setVisible(true);
        ocultarNotificacionMija.playFromStart();
    }

    // ------------------------------------------------------------------ progreso

    @Override
    public void mostrar(Runnable alCancelarNuevoMija) {
        alCancelarMija = alCancelarNuevoMija;
        etapaMija.setText(t("etapa.abriendo"));
        barraProgresoMija.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
        cancelarProgresoMija.setDisable(false);
        cancelarProgresoMija.setText(t("progreso.cancelar"));
        capaProgresoMija.setVisible(true);
    }

    @Override
    public void etapa(String claveTextoMija, boolean cancelableMija) {
        etapaMija.setText(t(claveTextoMija));
        barraProgresoMija.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
        cancelarProgresoMija.setText(cancelableMija ? t("progreso.cancelar") : t("progreso.noCancelable"));
    }

    @Override
    public void progreso(double fraccionMija) {
        barraProgresoMija.setProgress(fraccionMija);
    }

    @Override
    public void ocultar() {
        capaProgresoMija.setVisible(false);
    }

    @FXML
    private void cancelarTarea() {
        alCancelarMija.run();
        cancelarProgresoMija.setDisable(true);
    }

    // ------------------------------------------------------------------ menú

    @FXML
    private void irInicio() {
        mostrar(Vista.INICIO);
    }

    @FXML
    private void irExplorador() {
        mostrar(Vista.EXPLORADOR);
    }

    @FXML
    private void irSeguridad() {
        mostrar(Vista.SEGURIDAD);
    }

    @FXML
    private void irAjustes() {
        mostrar(Vista.AJUSTES);
    }

    @FXML
    private void cerrarBoveda() {
        uiMija.estado().cerrar();
    }
}
