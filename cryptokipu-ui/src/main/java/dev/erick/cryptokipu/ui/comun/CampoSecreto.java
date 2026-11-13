package dev.erick.cryptokipu.ui.comun;

import javafx.animation.PauseTransition;
import javafx.event.Event;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ToggleButton;
import javafx.scene.input.ContextMenuEvent;
import javafx.util.Duration;

/**
 * Manejo de los campos secretos.
 *
 * <ul>
 *   <li>{@link #leerYLimpiar}: copia carácter por carácter desde {@code getCharacters()} a un {@code char[]} (sin
 *       {@code toString()}) y limpia el campo. El {@code String} interno de JavaFX no se puede borrar
 *       (THREAT_MODEL).</li>
 *   <li>{@link #endurecer}: sin menú contextual. Copiar y cortar ya vienen deshabilitados en {@link PasswordField}
 *       (lo verifica una prueba).</li>
 *   <li>{@link #botonMostrar}: muestra el contenido como máximo 10 segundos.</li>
 * </ul>
 */
public final class CampoSecreto {

    public static final Duration TIEMPO_VISIBLE_MIJA = Duration.seconds(10);

    private CampoSecreto() {
    }

    /** Quien llama borra el resultado con {@code Arrays.fill} cuando termina de usarlo. */
    public static char[] leerYLimpiar(PasswordField campoMija) {
        CharSequence contenidoMija = campoMija.getCharacters();
        char[] copiaMija = new char[contenidoMija.length()];
        for (int indiceMija = 0; indiceMija < copiaMija.length; indiceMija++) {
            copiaMija[indiceMija] = contenidoMija.charAt(indiceMija);
        }
        campoMija.clear();
        return copiaMija;
    }

    /** Sin menú contextual; el texto accesible describe el campo, nunca su contenido. */
    public static void endurecer(PasswordField campoMija, String descripcionAccesibleMija) {
        campoMija.setContextMenu(null);
        campoMija.addEventFilter(ContextMenuEvent.CONTEXT_MENU_REQUESTED, Event::consume);
        campoMija.setAccessibleText(descripcionAccesibleMija);
    }

    /** Botón "ojo": muestra el contenido en {@code visorMija} y lo oculta solo a los 10 segundos. */
    public static void botonMostrar(ToggleButton botonMija, PasswordField campoMija, Label visorMija) {
        PauseTransition ocultarMija = new PauseTransition(TIEMPO_VISIBLE_MIJA);
        visorMija.setVisible(false);
        visorMija.managedProperty().bind(visorMija.visibleProperty());
        ocultarMija.setOnFinished(_ -> botonMija.setSelected(false));
        botonMija.selectedProperty().addListener((_, _, visibleMija) -> {
            if (visibleMija) {
                visorMija.setText(campoMija.getText());
                visorMija.setVisible(true);
                ocultarMija.playFromStart();
            } else {
                visorMija.setText("");
                visorMija.setVisible(false);
                ocultarMija.stop();
            }
        });
        campoMija.textProperty().addListener((_, _, _) -> {
            if (botonMija.isSelected()) {
                visorMija.setText(campoMija.getText());
            }
        });
    }
}
