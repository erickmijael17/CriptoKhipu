package dev.erick.cryptokipu.ui.viewmodel;

import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.collections.ObservableList;

/** Atajos de enlaces que JavaFX no trae. */
final class Bindings2 {

    private Bindings2() {
    }

    static BooleanBinding noVacia(ObservableList<?> listaMija) {
        return Bindings.isNotEmpty(listaMija);
    }
}
