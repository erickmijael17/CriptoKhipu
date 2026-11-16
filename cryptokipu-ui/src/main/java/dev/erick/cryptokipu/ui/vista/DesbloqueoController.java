package dev.erick.cryptokipu.ui.vista;

import static dev.erick.cryptokipu.ui.Textos.t;

import dev.erick.cryptokipu.app.casos.SesionBoveda;
import dev.erick.cryptokipu.ui.ContenedorUi;
import dev.erick.cryptokipu.ui.comun.CampoSecreto;
import dev.erick.cryptokipu.ui.comun.Refrescable;
import dev.erick.cryptokipu.ui.viewmodel.DesbloqueoViewModel;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.beans.binding.Bindings;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.util.Duration;

/**
 * Desbloqueo. Los campos se leen carácter por carácter y se limpian ANTES de derivar (en el hilo virtual). Cualquier
 * error de credenciales muestra el mismo mensaje del catálogo, sin importar qué factor falló.
 */
public final class DesbloqueoController implements Refrescable {

    private final ContenedorUi uiMija;
    private final DesbloqueoViewModel vmMija;
    private final Timeline cuentaRegresivaMija;

    @FXML
    private Label nombreMija;
    @FXML
    private Label avisoIntentosMija;
    @FXML
    private VBox credencialesMija;
    @FXML
    private PasswordField f1Mija;
    @FXML
    private PasswordField f2Mija;
    @FXML
    private PasswordField f3Mija;
    @FXML
    private HBox filaLlaveMija;
    @FXML
    private Label llaveElegidaMija;
    @FXML
    private Button elegirLlaveMija;
    @FXML
    private VBox recuperacionMija;
    @FXML
    private PasswordField tokenMija;
    @FXML
    private PasswordField f1TokenMija;
    @FXML
    private Label errorMija;
    @FXML
    private Label esperaMija;
    @FXML
    private Button abrirMija;
    @FXML
    private Hyperlink cambiarModoMija;

    public DesbloqueoController(ContenedorUi uiMija) {
        this.uiMija = uiMija;
        this.vmMija = uiMija.desbloqueo();
        this.cuentaRegresivaMija = new Timeline(new KeyFrame(Duration.seconds(1), _ -> vmMija.actualizarEspera()));
    }

    @FXML
    private void initialize() {
        CampoSecreto.endurecer(f1Mija, t("desbloqueo.f1"));
        CampoSecreto.endurecer(f2Mija, t("desbloqueo.f2"));
        CampoSecreto.endurecer(f3Mija, t("desbloqueo.f3"));
        CampoSecreto.endurecer(tokenMija, t("desbloqueo.token"));
        CampoSecreto.endurecer(f1TokenMija, t("desbloqueo.f1"));
        for (var nodoMija : new javafx.scene.Node[] {credencialesMija, recuperacionMija, filaLlaveMija, errorMija,
                avisoIntentosMija, esperaMija}) {
            nodoMija.managedProperty().bind(nodoMija.visibleProperty());
        }
        credencialesMija.visibleProperty().bind(vmMija.modoTokenProperty().not());
        recuperacionMija.visibleProperty().bind(vmMija.modoTokenProperty());
        filaLlaveMija.visibleProperty().bind(vmMija.requiereLlaveProperty());
        cambiarModoMija.textProperty().bind(Bindings.when(vmMija.modoTokenProperty())
                .then(t("desbloqueo.usarCredenciales")).otherwise(t("desbloqueo.usarToken")));
        errorMija.textProperty().bind(vmMija.mensajeErrorProperty());
        errorMija.visibleProperty().bind(vmMija.mensajeErrorProperty().isNotEmpty());
        // Retardo creciente (Fase 8): "Abrir" queda deshabilitado con cuenta regresiva visible, y también si la
        // bóveda fue destruida. Una revisión por segundo mantiene la cuenta al día.
        esperaMija.textProperty().bind(vmMija.avisoEsperaProperty());
        esperaMija.visibleProperty().bind(vmMija.avisoEsperaProperty().isNotEmpty());
        abrirMija.disableProperty().bind(vmMija.segundosEsperaProperty().greaterThan(0).or(vmMija.destruidaProperty()));
        cuentaRegresivaMija.setCycleCount(Animation.INDEFINITE);
        cuentaRegresivaMija.play();
        avisoIntentosMija.textProperty().bind(vmMija.avisoIntentosProperty());
        avisoIntentosMija.visibleProperty().bind(vmMija.avisoIntentosProperty().isNotEmpty());
        llaveElegidaMija.textProperty().bind(Bindings.createStringBinding(() -> vmMija.rutaLlaveProperty().get() == null
                ? "" : vmMija.rutaLlaveProperty().get().getFileName().toString(), vmMija.rutaLlaveProperty()));
    }

    /** Al entrar: limpia campos y consulta el header (perfil, llave, intentos) fuera del hilo de JavaFX. */
    @Override
    public void refrescar() {
        limpiarCampos();
        vmMija.modoTokenProperty().set(false);
        vmMija.rutaLlaveProperty().set(null);
        vmMija.mensajeErrorProperty().set("");
        Path rutaMija = vmMija.rutaProperty().get();
        nombreMija.setText(rutaMija == null ? "" : rutaMija.toString());
        vmMija.actualizarEspera();
        inspeccionar();
    }

    private void inspeccionar() {
        uiMija.ejecutor().ejecutar(_ -> vmMija.inspeccionar(), infoMija -> {
            vmMija.aplicar(infoMija);
            uiMija.seguridad().umbralProperty().set(infoMija.umbralAutodestruccionMija());
            (vmMija.modoTokenProperty().get() ? tokenMija : f1Mija).requestFocus();
        }, vmMija::mostrarError);
    }

    @FXML
    private void cambiarModo() {
        limpiarCampos();
        vmMija.mensajeErrorProperty().set("");
        vmMija.modoTokenProperty().set(!vmMija.modoTokenProperty().get());
        (vmMija.modoTokenProperty().get() ? tokenMija : f1Mija).requestFocus();
    }

    @FXML
    private void elegirLlave() {
        File archivoMija = new FileChooser().showOpenDialog(uiMija.ventana());
        if (archivoMija != null) {
            vmMija.rutaLlaveProperty().set(archivoMija.toPath());
        }
    }

    @FXML
    private void abrir() {
        Path rutaMija = vmMija.rutaProperty().get();
        vmMija.mensajeErrorProperty().set("");
        if (vmMija.modoTokenProperty().get()) {
            char[] tokenLeidoMija = CampoSecreto.leerYLimpiar(tokenMija);
            char[] f1LeidoMija = CampoSecreto.leerYLimpiar(f1TokenMija);
            uiMija.ejecutor().ejecutar(progresoMija -> {
                progresoMija.etapa("etapa.derivando", false);
                return vmMija.recuperar(tokenLeidoMija, f1LeidoMija);
            }, sesionMija -> abierta(rutaMija, sesionMija), this::fallo);
        } else {
            char[] f1LeidoMija = CampoSecreto.leerYLimpiar(f1Mija);
            char[] f2LeidoMija = CampoSecreto.leerYLimpiar(f2Mija);
            char[] f3LeidoMija = CampoSecreto.leerYLimpiar(f3Mija);
            uiMija.ejecutor().ejecutar(progresoMija -> {
                progresoMija.etapa("etapa.derivando", false);
                return vmMija.desbloquear(f1LeidoMija, f2LeidoMija, f3LeidoMija);
            }, sesionMija -> abierta(rutaMija, sesionMija), this::fallo);
        }
    }

    private void abierta(Path rutaMija, SesionBoveda sesionMija) {
        try {
            uiMija.ajustes().recordar(rutaMija);
        } catch (IOException _) {
            // Los recientes son una comodidad: si no se pueden guardar, la bóveda se abre igual.
        }
        uiMija.estado().abrir(rutaMija, sesionMija);
    }

    private void fallo(Throwable errorMija) {
        vmMija.mostrarError(errorMija);
        vmMija.actualizarEspera(); // si fue una credencial incorrecta, arranca la cuenta regresiva
        String mensajeMija = vmMija.mensajeErrorProperty().get();
        // Actualiza el aviso de intentos restantes sin borrar el mensaje de error.
        uiMija.ejecutor().ejecutar(_ -> vmMija.inspeccionar(), infoMija -> {
            vmMija.aplicar(infoMija);
            if (!infoMija.destruidaMija()) {
                vmMija.mensajeErrorProperty().set(mensajeMija);
            }
        }, _ -> vmMija.mensajeErrorProperty().set(mensajeMija));
    }

    private void limpiarCampos() {
        for (PasswordField campoMija : new PasswordField[] {f1Mija, f2Mija, f3Mija, tokenMija, f1TokenMija}) {
            campoMija.clear();
        }
    }
}
