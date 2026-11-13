package dev.erick.cryptokipu.ui;

import javafx.application.Application;

/**
 * Punto de entrada real. Separado de {@link CryptoKipuApp} (como en la referencia crud-persona-spring) para que el
 * JAR arranque aunque JavaFX esté en el classpath y no en el module path.
 */
public final class Launcher {

    private Launcher() {
    }

    public static void main(String[] argumentosMija) {
        Application.launch(CryptoKipuApp.class, argumentosMija);
    }
}
