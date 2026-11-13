package dev.erick.cryptokipu.ui.vista;

import static dev.erick.cryptokipu.ui.Textos.t;

import dev.erick.cryptokipu.app.casos.VerificarIntegridad;
import dev.erick.cryptokipu.core.auth.Credenciales;
import dev.erick.cryptokipu.ui.ContenedorUi;
import dev.erick.cryptokipu.ui.comun.Refrescable;
import dev.erick.cryptokipu.ui.viewmodel.DesbloqueoViewModel;
import dev.erick.cryptokipu.ui.viewmodel.SeguridadViewModel;
import java.nio.file.Path;
import java.util.function.Consumer;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/** Seguridad: cambiar credenciales, regenerar tokens, autodestrucción, verificar y compactar. */
public final class SeguridadController implements Refrescable {

    private final ContenedorUi uiMija;
    private final SeguridadViewModel vmMija;

    @FXML
    private Button cambiarMija;
    @FXML
    private Button regenerarMija;
    @FXML
    private Spinner<Integer> umbralMija;
    @FXML
    private Button aplicarUmbralMija;
    @FXML
    private Button verificarMija;
    @FXML
    private Button compactarMija;
    @FXML
    private Label resultadoMija;

    public SeguridadController(ContenedorUi uiMija) {
        this.uiMija = uiMija;
        this.vmMija = uiMija.seguridad();
    }

    @FXML
    private void initialize() {
        SpinnerValueFactory.IntegerSpinnerValueFactory fabricaMija =
                new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 99, vmMija.umbralProperty().get());
        umbralMija.setValueFactory(fabricaMija);
        vmMija.umbralProperty().addListener((_, _, valorMija) -> fabricaMija.setValue(valorMija.intValue()));
    }

    @Override
    public void refrescar() {
        resultadoMija.setText("");
        umbralMija.getValueFactory().setValue(vmMija.umbralProperty().get());
    }

    @FXML
    private void cambiar() {
        RotacionController.mostrarCambio(uiMija);
    }

    @FXML
    private void regenerar() {
        pedirVigentes(t("seguridad.regenerar"), vigentesMija -> uiMija.ejecutor().ejecutar(progresoMija -> {
            try (Credenciales credencialesMija = vigentesMija.construir()) {
                return vmMija.regenerarTokens(uiMija.estado().sesion(), credencialesMija, progresoMija);
            }
        }, tokensMija -> TokensController.mostrar(uiMija, tokensMija, nombreBoveda(), () -> { }), this::fallo));
    }

    @FXML
    private void aplicarUmbral() {
        int nuevoMija = umbralMija.getValue();
        pedirVigentes(t("seguridad.umbral.aplicar"), vigentesMija -> uiMija.ejecutor().ejecutar(progresoMija -> {
            try (Credenciales credencialesMija = vigentesMija.construir()) {
                vmMija.cambiarUmbral(uiMija.estado().sesion(), credencialesMija, nuevoMija, progresoMija);
                return nuevoMija;
            }
        }, aplicadoMija -> {
            vmMija.umbralProperty().set(aplicadoMija);
            uiMija.navegador().notificar(t("notificacion.umbral", aplicadoMija));
        }, this::fallo));
    }

    @FXML
    private void verificar() {
        uiMija.ejecutor().ejecutar(progresoMija -> vmMija.verificar(uiMija.estado().sesion(), progresoMija),
                (VerificarIntegridad.Resultado informeMija) -> resultadoMija.setText(informeMija.todoCorrecto()
                        ? t("seguridad.verificar.correcto")
                        : t("seguridad.verificar.corrupto", informeMija.informesMija().stream()
                                .filter(lineaMija -> lineaMija.estadoMija() != VerificarIntegridad.Estado.CORRECTO)
                                .count())),
                this::fallo);
    }

    @FXML
    private void compactar() {
        uiMija.ejecutor().ejecutar(progresoMija -> {
            vmMija.compactar(uiMija.estado().sesion(), progresoMija);
            return Boolean.TRUE;
        }, _ -> uiMija.navegador().notificar(t("notificacion.compactada")), this::fallo);
    }

    private void fallo(Throwable errorMija) {
        uiMija.navegador().notificar(DesbloqueoViewModel.mensaje(errorMija));
    }

    private String nombreBoveda() {
        Path rutaMija = uiMija.estado().rutaProperty().get();
        return rutaMija == null ? "" : rutaMija.getFileName().toString();
    }

    /** Modal con las credenciales vigentes; al aceptar entrega lo leído (los campos ya quedan vacíos). */
    private void pedirVigentes(String accionMija, Consumer<PanelVigentes.Vigentes> alAceptarMija) {
        PanelVigentes panelMija = new PanelVigentes(uiMija.ventana());
        Label tituloMija = new Label(accionMija.replace("…", ""));
        tituloMija.getStyleClass().add("title-3");
        Button cancelarMija = new Button(t("credenciales.cancelar"));
        cancelarMija.setCancelButton(true);
        Button aceptarMija = new Button(t("credenciales.aceptar"));
        aceptarMija.setDefaultButton(true);
        aceptarMija.getStyleClass().add("accent");
        Region separadorMija = new Region();
        HBox.setHgrow(separadorMija, Priority.ALWAYS);
        VBox tarjetaMija = new VBox(12, tituloMija, panelMija, new HBox(8, cancelarMija, separadorMija, aceptarMija));
        tarjetaMija.getStyleClass().add("tarjeta");
        tarjetaMija.setPadding(new Insets(24));
        tarjetaMija.setMaxSize(460, Region.USE_PREF_SIZE);
        cancelarMija.setOnAction(_ -> {
            panelMija.tomar().borrar();
            uiMija.navegador().cerrarModal();
        });
        aceptarMija.setOnAction(_ -> {
            PanelVigentes.Vigentes vigentesMija = panelMija.tomar();
            uiMija.navegador().cerrarModal();
            alAceptarMija.accept(vigentesMija);
        });
        uiMija.navegador().abrirModal(tarjetaMija, true);
        panelMija.campoF1().requestFocus();
    }
}
