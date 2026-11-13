package dev.erick.cryptokipu.ui.vista;

import static dev.erick.cryptokipu.ui.Textos.t;

import dev.erick.cryptokipu.core.auth.Credenciales;
import dev.erick.cryptokipu.infra.autenticacion.LlaveSeguridad;
import dev.erick.cryptokipu.ui.comun.CampoSecreto;
import dev.erick.cryptokipu.ui.viewmodel.HiloFx;
import dev.erick.cryptokipu.ui.viewmodel.ProgresoOperacion;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Window;

/**
 * Credenciales ACTUALES (para regenerar tokens, cambiar el umbral o cambiar factores). Sin validación en vivo: al
 * confirmar no se revela nada sobre qué factor es incorrecto.
 */
public final class PanelVigentes extends VBox {

    /** Credenciales leídas de los campos; la huella de la llave se calcula fuera del hilo de JavaFX. */
    public record Vigentes(char[] f1Mija, char[] f2Mija, char[] f3Mija, Path llaveMija) {

        /** Fuera del hilo de JavaFX. Toma posesión de los arreglos. */
        public Credenciales construir() throws IOException {
            HiloFx.exigirFuera();
            byte[] huellaMija = llaveMija == null ? null
                    : LlaveSeguridad.huellaBytes(llaveMija, ProgresoOperacion.NINGUNO_MIJA);
            return new Credenciales(f1Mija, f2Mija, f3Mija, huellaMija);
        }

        public void borrar() {
            Arrays.fill(f1Mija, '\0');
            Arrays.fill(f2Mija, '\0');
            Arrays.fill(f3Mija, '\0');
        }

        @Override
        public String toString() {
            return "Vigentes[***]";
        }
    }

    private final PasswordField f1Mija = new PasswordField();
    private final PasswordField f2Mija = new PasswordField();
    private final PasswordField f3Mija = new PasswordField();
    private final Label llaveElegidaMija = new Label(t("asistente.llave.sin"));
    private Path llaveMija;

    public PanelVigentes(Window ventanaMija) {
        super(8);
        Label tituloMija = new Label(t("credenciales.vigentes.titulo"));
        tituloMija.getStyleClass().add("title-4");
        CampoSecreto.endurecer(f1Mija, t("desbloqueo.f1"));
        CampoSecreto.endurecer(f2Mija, t("desbloqueo.f2"));
        CampoSecreto.endurecer(f3Mija, t("desbloqueo.f3"));
        f1Mija.setPromptText(t("desbloqueo.f1"));
        f2Mija.setPromptText(t("desbloqueo.f2"));
        f3Mija.setPromptText(t("desbloqueo.f3"));
        Button elegirMija = new Button(t("asistente.llave.elegir"));
        elegirMija.setOnAction(_ -> {
            var archivoMija = new FileChooser().showOpenDialog(ventanaMija);
            if (archivoMija != null) {
                llaveMija = archivoMija.toPath();
                llaveElegidaMija.setText(llaveMija.getFileName().toString());
            }
        });
        HBox filaLlaveMija = new HBox(8, new Label(t("desbloqueo.llave") + ":"), llaveElegidaMija, elegirMija);
        filaLlaveMija.setAlignment(Pos.CENTER_LEFT);
        getChildren().addAll(tituloMija, f1Mija, f2Mija, f3Mija, filaLlaveMija);
    }

    /** Lee y limpia los campos. */
    public Vigentes tomar() {
        return new Vigentes(CampoSecreto.leerYLimpiar(f1Mija), CampoSecreto.leerYLimpiar(f2Mija),
                CampoSecreto.leerYLimpiar(f3Mija), llaveMija);
    }

    public PasswordField campoF1() {
        return f1Mija;
    }
}
