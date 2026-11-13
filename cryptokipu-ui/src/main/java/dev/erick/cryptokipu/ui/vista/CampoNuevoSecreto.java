package dev.erick.cryptokipu.ui.vista;

import static dev.erick.cryptokipu.ui.Textos.t;

import dev.erick.cryptokipu.ui.comun.CampoSecreto;
import dev.erick.cryptokipu.ui.viewmodel.ValidacionViva;
import java.util.Arrays;
import java.util.Optional;
import javafx.collections.ListChangeListener;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

/**
 * Campo para un factor NUEVO (crear o rotar): valor + repetición, botón "ojo" de 10 s, lista ✓/✗ por regla y
 * medidor de fortaleza. La validación en vivo copia el contenido a un {@code char[]}, evalúa y lo borra.
 */
public final class CampoNuevoSecreto extends VBox {

    private final ValidacionViva validacionMija;
    private final PasswordField valorMija = new PasswordField();
    private final PasswordField repetidoMija = new PasswordField();
    private final VBox reglasMija = new VBox(2);
    private final ProgressBar fortalezaMija = new ProgressBar(0);
    private final Label errorMija = new Label();

    public CampoNuevoSecreto(ValidacionViva validacionMija, String tituloMija, String ayudaMija) {
        super(8);
        this.validacionMija = validacionMija;
        Label tituloEtiquetaMija = new Label(tituloMija);
        tituloEtiquetaMija.getStyleClass().add("title-4");
        tituloEtiquetaMija.setLabelFor(valorMija);
        Label ayudaEtiquetaMija = new Label(ayudaMija);
        ayudaEtiquetaMija.setWrapText(true);
        ayudaEtiquetaMija.getStyleClass().add("text-muted");

        CampoSecreto.endurecer(valorMija, tituloMija);
        CampoSecreto.endurecer(repetidoMija, t("asistente.repetir"));
        valorMija.setPromptText(tituloMija);
        repetidoMija.setPromptText(t("asistente.repetir"));
        ToggleButton ojoMija = new ToggleButton(null, new FontIcon(Feather.EYE));
        ojoMija.setAccessibleText(t("campo.mostrar"));
        ojoMija.setFocusTraversable(false);
        Label visorMija = new Label();
        visorMija.getStyleClass().add("token");
        CampoSecreto.botonMostrar(ojoMija, valorMija, visorMija);
        HBox filaMija = new HBox(6, valorMija, ojoMija);
        filaMija.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(valorMija, Priority.ALWAYS);

        fortalezaMija.setMaxWidth(Double.MAX_VALUE);
        fortalezaMija.setAccessibleText(t("campo.fortaleza"));
        errorMija.getStyleClass().add("regla-falla");
        errorMija.managedProperty().bind(errorMija.visibleProperty());
        errorMija.setVisible(false);

        validacionMija.reglas().addListener((ListChangeListener<ValidacionViva.EstadoRegla>) _ -> dibujarReglas());
        validacionMija.fortalezaProperty().addListener((_, _, valorNuevoMija) -> fortalezaMija.setProgress(
                valorNuevoMija.doubleValue()));
        valorMija.textProperty().addListener((_, _, _) -> evaluar());
        dibujarReglas();
        getChildren().addAll(tituloEtiquetaMija, ayudaEtiquetaMija, filaMija, visorMija, repetidoMija, reglasMija,
                fortalezaMija, errorMija);
    }

    private void evaluar() {
        CharSequence contenidoMija = valorMija.getCharacters();
        char[] copiaMija = new char[contenidoMija.length()];
        for (int indiceMija = 0; indiceMija < copiaMija.length; indiceMija++) {
            copiaMija[indiceMija] = contenidoMija.charAt(indiceMija);
        }
        try {
            validacionMija.evaluar(copiaMija);
        } finally {
            Arrays.fill(copiaMija, '\0');
        }
        errorMija.setVisible(false);
    }

    private void dibujarReglas() {
        reglasMija.getChildren().setAll(validacionMija.reglas().stream().map(reglaMija -> {
            Label etiquetaMija = new Label((reglaMija.cumplidaMija() ? "✓ " : "✗ ") + reglaMija.textoMija());
            etiquetaMija.getStyleClass().add(reglaMija.cumplidaMija() ? "regla-ok" : "regla-falla");
            etiquetaMija.setWrapText(true);
            return etiquetaMija;
        }).toList());
    }

    /** Lee y limpia el valor (quien llama lo borra). */
    public char[] tomarValor() {
        return CampoSecreto.leerYLimpiar(valorMija);
    }

    /** Lee y limpia la repetición (quien llama lo borra). */
    public char[] tomarRepetido() {
        return CampoSecreto.leerYLimpiar(repetidoMija);
    }

    /**
     * Valida valor y repetición; si todo está bien, devuelve el valor (quien llama lo borra). Si no, borra ambos,
     * muestra el motivo y devuelve vacío.
     */
    public Optional<char[]> tomarSiValido() {
        char[] valorLeidoMija = tomarValor();
        char[] repetidoLeidoMija = tomarRepetido();
        try {
            if (!Arrays.equals(valorLeidoMija, repetidoLeidoMija)) {
                mostrarError(t("error.noCoinciden"));
            } else {
                validacionMija.evaluar(valorLeidoMija);
                if (validacionMija.esValido()) {
                    char[] resultadoMija = valorLeidoMija;
                    valorLeidoMija = null;
                    return Optional.of(resultadoMija);
                }
                mostrarError(t("error.politica"));
            }
            return Optional.empty();
        } finally {
            if (valorLeidoMija != null) {
                Arrays.fill(valorLeidoMija, '\0');
            }
            Arrays.fill(repetidoLeidoMija, '\0');
        }
    }

    public void mostrarError(String textoMija) {
        errorMija.setText(textoMija);
        errorMija.setVisible(true);
    }

    public void limpiar() {
        valorMija.clear();
        repetidoMija.clear();
        errorMija.setVisible(false);
    }

    public PasswordField campoValor() {
        return valorMija;
    }

    public PasswordField campoRepetido() {
        return repetidoMija;
    }
}
