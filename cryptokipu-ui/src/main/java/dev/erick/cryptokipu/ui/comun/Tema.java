package dev.erick.cryptokipu.ui.comun;

import atlantafx.base.theme.PrimerDark;
import atlantafx.base.theme.PrimerLight;
import dev.erick.cryptokipu.ui.viewmodel.AjustesViewModel;
import javafx.application.Application;
import javafx.application.ColorScheme;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;

/** Tema claro/oscuro de AtlantaFX. "Seguir al sistema" escucha los cambios del esquema de color del sistema. */
public final class Tema {

    private static AjustesViewModel.Tema elegidoMija = AjustesViewModel.Tema.SISTEMA;
    private static final ChangeListener<ColorScheme> OYENTE_SISTEMA_MIJA = (_, _, _) -> {
        if (elegidoMija == AjustesViewModel.Tema.SISTEMA) {
            aplicarEfectivo();
        }
    };
    private static boolean escuchandoMija;

    private Tema() {
    }

    public static void aplicar(AjustesViewModel.Tema temaMija) {
        elegidoMija = temaMija;
        if (!escuchandoMija) {
            Platform.getPreferences().colorSchemeProperty().addListener(OYENTE_SISTEMA_MIJA);
            escuchandoMija = true;
        }
        aplicarEfectivo();
    }

    public static boolean esOscuro() {
        return switch (elegidoMija) {
            case OSCURO -> true;
            case CLARO -> false;
            case SISTEMA -> Platform.getPreferences().getColorScheme() == ColorScheme.DARK;
        };
    }

    private static void aplicarEfectivo() {
        Application.setUserAgentStylesheet(esOscuro() ? new PrimerDark().getUserAgentStylesheet()
                : new PrimerLight().getUserAgentStylesheet());
    }
}
